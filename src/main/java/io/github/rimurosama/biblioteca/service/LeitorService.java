package io.github.rimurosama.biblioteca.service;

import io.github.rimurosama.biblioteca.api.Dtos.LeitorRequest;
import io.github.rimurosama.biblioteca.api.Dtos.LeitorResponse;
import io.github.rimurosama.biblioteca.api.Dtos.Pagina;
import io.github.rimurosama.biblioteca.api.erro.Erros.Conflito;
import io.github.rimurosama.biblioteca.api.erro.Erros.NaoEncontrado;
import io.github.rimurosama.biblioteca.domain.Leitor;
import io.github.rimurosama.biblioteca.repository.EmprestimoRepository;
import io.github.rimurosama.biblioteca.repository.LeitorRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeitorService {
    private final LeitorRepository leitores;
    private final EmprestimoRepository emprestimos;

    public LeitorService(LeitorRepository leitores, EmprestimoRepository emprestimos) {
        this.leitores = leitores;
        this.emprestimos = emprestimos;
    }

    @Transactional
    public LeitorResponse criar(LeitorRequest r) {
        if (leitores.existsByEmailIgnoreCase(r.email().strip())) {
            throw new Conflito("Já existe um leitor com este e-mail.");
        }
        try {
            return paraResposta(leitores.saveAndFlush(new Leitor(r.nome(), r.email(), r.turma(), r.telefone())));
        } catch (DataIntegrityViolationException e) {
            throw new Conflito("Já existe um leitor com este e-mail."); // corrida entre duas criações simultâneas
        }
    }

    @Transactional(readOnly = true)
    public LeitorResponse buscar(Long id) {
        return paraResposta(carregar(id));
    }

    @Transactional(readOnly = true)
    public Pagina<LeitorResponse> listar(int pagina, int tamanho) {
        return Pagina.de(leitores.findAll(PageRequest.of(pagina, tamanho, Sort.by("nome").and(Sort.by("id")))), this::paraResposta);
    }

    @Transactional
    public LeitorResponse atualizar(Long id, LeitorRequest r) {
        Leitor l = carregar(id);
        if (leitores.existsByEmailIgnoreCaseAndIdNot(r.email().strip(), id)) {
            throw new Conflito("Já existe outro leitor com este e-mail.");
        }
        l.atualizar(r.nome(), r.email(), r.turma(), r.telefone());
        try {
            return paraResposta(leitores.saveAndFlush(l));
        } catch (DataIntegrityViolationException e) {
            throw new Conflito("Já existe outro leitor com este e-mail.");
        }
    }

    /** Leitor com histórico de empréstimos não pode ser removido (preserva o histórico e as multas). */
    @Transactional
    public void remover(Long id) {
        Leitor l = carregar(id);
        if (emprestimos.existsByLeitorId(id)) {
            throw new Conflito("O leitor tem histórico de empréstimos e não pode ser removido.");
        }
        leitores.delete(l);
    }

    private Leitor carregar(Long id) {
        return leitores.findById(id).orElseThrow(() -> new NaoEncontrado("Leitor", id));
    }

    private LeitorResponse paraResposta(Leitor l) {
        return new LeitorResponse(l.getId(), l.getNome(), l.getEmail(), l.getTurma(), l.getTelefone());
    }
}
