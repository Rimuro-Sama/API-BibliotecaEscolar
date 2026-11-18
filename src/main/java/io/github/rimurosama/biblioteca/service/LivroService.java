package io.github.rimurosama.biblioteca.service;

import io.github.rimurosama.biblioteca.api.Dtos.ExemplarRequest;
import io.github.rimurosama.biblioteca.api.Dtos.ExemplarResponse;
import io.github.rimurosama.biblioteca.api.Dtos.LivroRequest;
import io.github.rimurosama.biblioteca.api.Dtos.LivroResponse;
import io.github.rimurosama.biblioteca.api.Dtos.Pagina;
import io.github.rimurosama.biblioteca.api.erro.Erros.Conflito;
import io.github.rimurosama.biblioteca.api.erro.Erros.NaoEncontrado;
import io.github.rimurosama.biblioteca.domain.Exemplar;
import io.github.rimurosama.biblioteca.domain.Livro;
import io.github.rimurosama.biblioteca.repository.ExemplarRepository;
import io.github.rimurosama.biblioteca.repository.LivroRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LivroService {
    private final LivroRepository livros;
    private final ExemplarRepository exemplares;

    public LivroService(LivroRepository livros, ExemplarRepository exemplares) {
        this.livros = livros;
        this.exemplares = exemplares;
    }

    @Transactional
    public LivroResponse criar(LivroRequest r) {
        String isbn = normalizaIsbn(r.isbn());
        if (isbn != null && livros.existsByIsbn(isbn)) {
            throw new Conflito("Já existe um livro com este ISBN.");
        }
        try {
            Livro l = livros.saveAndFlush(new Livro(r.titulo(), r.autor(), r.anoPublicacao(), isbn));
            return paraResposta(l, 0, 0);
        } catch (DataIntegrityViolationException e) {
            throw new Conflito("Já existe um livro com este ISBN.");
        }
    }

    @Transactional(readOnly = true)
    public LivroResponse buscar(Long id) {
        Livro l = carregar(id);
        Map<Long, long[]> contagens = contagens(List.of(id));
        long[] c = contagens.getOrDefault(id, new long[2]);
        return paraResposta(l, c[0], c[1]);
    }

    /** Catálogo com busca opcional por título ou autor (sem diferenciar maiúsculas/minúsculas). */
    @Transactional(readOnly = true)
    public Pagina<LivroResponse> listar(String busca, int pagina, int tamanho) {
        var pageable = PageRequest.of(pagina, tamanho, Sort.by("titulo").and(Sort.by("id")));
        Page<Livro> page = busca == null || busca.isBlank()
                ? livros.findAll(pageable)
                : livros.buscar("%" + escapaLike(busca.strip().toLowerCase(Locale.ROOT)) + "%", pageable);
        Map<Long, long[]> contagens = contagens(page.getContent().stream().map(Livro::getId).toList());
        return Pagina.de(page, l -> {
            long[] c = contagens.getOrDefault(l.getId(), new long[2]);
            return paraResposta(l, c[0], c[1]);
        });
    }

    @Transactional
    public LivroResponse atualizar(Long id, LivroRequest r) {
        Livro l = carregar(id);
        String isbn = normalizaIsbn(r.isbn());
        if (isbn != null && livros.existsByIsbnAndIdNot(isbn, id)) {
            throw new Conflito("Já existe outro livro com este ISBN.");
        }
        l.atualizar(r.titulo(), r.autor(), r.anoPublicacao(), isbn);
        try {
            livros.saveAndFlush(l);
        } catch (DataIntegrityViolationException e) {
            throw new Conflito("Já existe outro livro com este ISBN.");
        }
        return buscar(id);
    }

    /** Só remove livros sem exemplares: os exemplares (e seus empréstimos) fazem parte do histórico. */
    @Transactional
    public void remover(Long id) {
        Livro l = carregar(id);
        if (exemplares.existsByLivroId(id)) {
            throw new Conflito("O livro tem exemplares cadastrados e não pode ser removido.");
        }
        livros.delete(l);
    }

    @Transactional
    public ExemplarResponse adicionarExemplar(Long livroId, ExemplarRequest r) {
        Livro livro = carregar(livroId);
        String tombo = r.tombo().strip();
        if (exemplares.existsByTombo(tombo)) {
            throw new Conflito("Já existe um exemplar com o tombo " + tombo + ".");
        }
        try {
            Exemplar e = exemplares.saveAndFlush(new Exemplar(livro, tombo));
            return new ExemplarResponse(e.getId(), livroId, e.getTombo(), true);
        } catch (DataIntegrityViolationException e) {
            throw new Conflito("Já existe um exemplar com o tombo " + tombo + ".");
        }
    }

    @Transactional(readOnly = true)
    public List<ExemplarResponse> listarExemplares(Long livroId) {
        carregar(livroId);
        return exemplares.findByLivroIdOrderByTomboAsc(livroId).stream()
                .map(e -> new ExemplarResponse(e.getId(), livroId, e.getTombo(), exemplares.estaDisponivel(e.getId())))
                .toList();
    }

    private Livro carregar(Long id) {
        return livros.findById(id).orElseThrow(() -> new NaoEncontrado("Livro", id));
    }

    /** livroId -> [total, disponíveis], em duas consultas agrupadas (evita uma consulta por livro). */
    private Map<Long, long[]> contagens(List<Long> ids) {
        Map<Long, long[]> mapa = new HashMap<>();
        if (ids.isEmpty()) return mapa;
        for (Object[] linha : exemplares.contarPorLivro(ids)) {
            mapa.computeIfAbsent((Long) linha[0], k -> new long[2])[0] = (Long) linha[1];
        }
        for (Object[] linha : exemplares.contarDisponiveisPorLivro(ids)) {
            mapa.computeIfAbsent((Long) linha[0], k -> new long[2])[1] = (Long) linha[1];
        }
        return mapa;
    }

    private static String normalizaIsbn(String isbn) {
        return isbn == null || isbn.isBlank() ? null : isbn.strip().toUpperCase(Locale.ROOT);
    }

    /** Impede que % e _ digitados pelo usuário virem curingas do LIKE (o caractere de escape é '!', declarado na consulta). */
    private static String escapaLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private LivroResponse paraResposta(Livro l, long total, long disponiveis) {
        return new LivroResponse(l.getId(), l.getTitulo(), l.getAutor(), l.getAnoPublicacao(), l.getIsbn(), total, disponiveis);
    }
}
