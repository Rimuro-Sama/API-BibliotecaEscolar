package io.github.rimurosama.biblioteca.service;

import io.github.rimurosama.biblioteca.api.Dtos.EmprestimoResponse;
import io.github.rimurosama.biblioteca.api.Dtos.LivroMaisEmprestadoResponse;
import io.github.rimurosama.biblioteca.api.Dtos.Pagina;
import io.github.rimurosama.biblioteca.api.erro.Erros.Conflito;
import io.github.rimurosama.biblioteca.api.erro.Erros.NaoEncontrado;
import io.github.rimurosama.biblioteca.domain.Emprestimo;
import io.github.rimurosama.biblioteca.domain.Exemplar;
import io.github.rimurosama.biblioteca.domain.Leitor;
import io.github.rimurosama.biblioteca.domain.PoliticaDeEmprestimo;
import io.github.rimurosama.biblioteca.repository.EmprestimoRepository;
import io.github.rimurosama.biblioteca.repository.ExemplarRepository;
import io.github.rimurosama.biblioteca.repository.LeitorRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class EmprestimoService {
    private final EmprestimoRepository emprestimos;
    private final LeitorRepository leitores;
    private final ExemplarRepository exemplares;
    private final Clock relogio;

    public EmprestimoService(EmprestimoRepository emprestimos, LeitorRepository leitores,
                             ExemplarRepository exemplares, Clock relogio) {
        this.emprestimos = emprestimos;
        this.leitores = leitores;
        this.exemplares = exemplares;
        this.relogio = relogio;
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    /**
     * Abre um empréstimo. Recusa (409) se o leitor tem multa pendente, empréstimo atrasado ou já atingiu o limite,
     * ou se o exemplar já está emprestado.
     *
     * A linha do leitor fica travada durante a transação, então duas requisições simultâneas do mesmo leitor não
     * conseguem furar o limite. Para o exemplar, a garantia final é o índice único parcial do banco (V1): se duas
     * requisições passarem pela checagem ao mesmo tempo, uma delas falha e vira 409.
     */
    @Transactional
    public EmprestimoResponse emprestar(Long leitorId, Long exemplarId) {
        Leitor leitor = leitores.findByIdParaAtualizar(leitorId)
                .orElseThrow(() -> new NaoEncontrado("Leitor", leitorId));
        Exemplar exemplar = exemplares.findWithLivroById(exemplarId)
                .orElseThrow(() -> new NaoEncontrado("Exemplar", exemplarId));
        LocalDate hoje = hoje();

        if (emprestimos.existeMultaPendente(leitorId)) {
            throw new Conflito("O leitor tem multa pendente. Registre o pagamento antes de novos empréstimos.");
        }
        if (emprestimos.existsByLeitorIdAndDataDevolucaoIsNullAndDataPrevistaBefore(leitorId, hoje)) {
            throw new Conflito("O leitor tem empréstimo atrasado. Devolva o livro antes de pegar outro.");
        }
        if (emprestimos.countByLeitorIdAndDataDevolucaoIsNull(leitorId) >= PoliticaDeEmprestimo.MAX_EMPRESTIMOS_ATIVOS) {
            throw new Conflito("O leitor já tem " + PoliticaDeEmprestimo.MAX_EMPRESTIMOS_ATIVOS + " empréstimos ativos (limite).");
        }
        if (emprestimos.existsByExemplarIdAndDataDevolucaoIsNull(exemplarId)) {
            throw new Conflito("O exemplar " + exemplar.getTombo() + " já está emprestado.");
        }

        try {
            Emprestimo novo = emprestimos.saveAndFlush(new Emprestimo(leitor, exemplar, hoje));
            return paraResposta(novo, hoje);
        } catch (DataIntegrityViolationException e) {
            // perdeu a corrida para outra requisição que emprestou o mesmo exemplar
            throw new Conflito("O exemplar " + exemplar.getTombo() + " já está emprestado.");
        }
    }

    /** Renova uma vez (+7 dias a partir do prazo atual), desde que não esteja atrasado. */
    @Transactional
    public EmprestimoResponse renovar(Long id) {
        Emprestimo e = emprestimos.findByIdParaAtualizar(id).orElseThrow(() -> new NaoEncontrado("Empréstimo", id));
        LocalDate hoje = hoje();
        if (!e.estaAtivo()) {
            throw new Conflito("Empréstimo já devolvido.");
        }
        if (e.getRenovacoes() >= PoliticaDeEmprestimo.MAX_RENOVACOES) {
            throw new Conflito("Este empréstimo já foi renovado o máximo de vezes (" + PoliticaDeEmprestimo.MAX_RENOVACOES + ").");
        }
        if (!PoliticaDeEmprestimo.podeRenovar(e.getRenovacoes(), e.getDataPrevista(), hoje)) {
            throw new Conflito("Empréstimo atrasado não pode ser renovado. Devolva o livro.");
        }
        e.renovar();
        return paraResposta(carregar(id), hoje);
    }

    /** Registra a devolução e calcula a multa. Se houver multa, o leitor fica bloqueado até pagá-la. */
    @Transactional
    public EmprestimoResponse devolver(Long id) {
        Emprestimo e = emprestimos.findByIdParaAtualizar(id).orElseThrow(() -> new NaoEncontrado("Empréstimo", id));
        if (!e.estaAtivo()) {
            throw new Conflito("Empréstimo já devolvido.");
        }
        LocalDate hoje = hoje();
        e.devolver(hoje);
        return paraResposta(carregar(id), hoje);
    }

    @Transactional
    public EmprestimoResponse pagarMulta(Long id) {
        Emprestimo e = emprestimos.findByIdParaAtualizar(id).orElseThrow(() -> new NaoEncontrado("Empréstimo", id));
        if (e.getMultaCentavos() == 0) {
            throw new Conflito("Este empréstimo não tem multa.");
        }
        if (e.isMultaPaga()) {
            throw new Conflito("A multa deste empréstimo já foi paga.");
        }
        e.pagarMulta();
        return paraResposta(carregar(id), hoje());
    }

    @Transactional(readOnly = true)
    public EmprestimoResponse buscar(Long id) {
        return paraResposta(carregar(id), hoje());
    }

    /** Filtro de status: todos | ativos (em andamento) | atrasados. */
    @Transactional(readOnly = true)
    public Pagina<EmprestimoResponse> listar(String status, int pagina, int tamanho) {
        Pageable p = PageRequest.of(pagina, tamanho);
        LocalDate hoje = hoje();
        var resultado = switch (status) {
            case "ativos" -> emprestimos.findByDataDevolucaoIsNullOrderByDataPrevistaAsc(p);
            case "atrasados" -> emprestimos.findByDataDevolucaoIsNullAndDataPrevistaBeforeOrderByDataPrevistaAsc(hoje, p);
            default -> emprestimos.findAllByOrderByIdDesc(p);
        };
        return Pagina.de(resultado, e -> paraResposta(e, hoje));
    }

    @Transactional(readOnly = true)
    public Pagina<EmprestimoResponse> listarDoLeitor(Long leitorId, int pagina, int tamanho) {
        if (!leitores.existsById(leitorId)) throw new NaoEncontrado("Leitor", leitorId);
        LocalDate hoje = hoje();
        return Pagina.de(emprestimos.findByLeitorIdOrderByIdDesc(leitorId, PageRequest.of(pagina, tamanho)),
                e -> paraResposta(e, hoje));
    }

    /** Multas ainda não pagas. */
    @Transactional(readOnly = true)
    public Pagina<EmprestimoResponse> listarMultasPendentes(int pagina, int tamanho) {
        LocalDate hoje = hoje();
        return Pagina.de(emprestimos.findByMultaCentavosGreaterThanAndMultaPagaFalseOrderByIdAsc(0, PageRequest.of(pagina, tamanho)),
                e -> paraResposta(e, hoje));
    }

    @Transactional(readOnly = true)
    public List<LivroMaisEmprestadoResponse> maisEmprestados(int limite) {
        return emprestimos.maisEmprestados(PageRequest.of(0, limite)).stream()
                .map(l -> new LivroMaisEmprestadoResponse(l.livroId(), l.titulo(), l.autor(), l.emprestimos()))
                .toList();
    }

    private Emprestimo carregar(Long id) {
        return emprestimos.findWithDetalhesById(id).orElseThrow(() -> new NaoEncontrado("Empréstimo", id));
    }

    private EmprestimoResponse paraResposta(Emprestimo e, LocalDate hoje) {
        var exemplar = e.getExemplar();
        var livro = exemplar.getLivro();
        // atraso "em andamento" para quem ainda não devolveu; para devolvidos, o atraso que gerou a multa
        int atraso = e.estaAtivo()
                ? PoliticaDeEmprestimo.diasDeAtraso(e.getDataPrevista(), hoje)
                : PoliticaDeEmprestimo.diasDeAtraso(e.getDataPrevista(), e.getDataDevolucao());
        return new EmprestimoResponse(e.getId(), e.getLeitor().getId(), e.getLeitor().getNome(),
                exemplar.getId(), exemplar.getTombo(), livro.getId(), livro.getTitulo(),
                e.getDataEmprestimo(), e.getDataPrevista(), e.getDataDevolucao(), e.getRenovacoes(),
                e.statusEm(hoje), atraso, e.getMultaCentavos(), e.isMultaPaga());
    }
}
