package io.github.rimurosama.biblioteca;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Garantias do próprio banco, testadas SEM passar pela aplicação (SQL direto). A aplicação checa as regras, mas
 * o banco é a última linha de defesa: se algum dia um bug ou uma corrida pular a checagem, o dado inválido não entra.
 */
class BancoDeDadosTest extends IntegracaoBase {
    private long leitorA;
    private long leitorB;
    private long exemplar;

    @BeforeEach
    void dados() {
        leitorA = insere("insert into leitor (nome, email) values ('A', 'a@x.exemplo') returning id");
        leitorB = insere("insert into leitor (nome, email) values ('B', 'b@x.exemplo') returning id");
        long livro = insere("insert into livro (titulo, autor) values ('L', 'Z') returning id");
        exemplar = insere("insert into exemplar (livro_id, tombo) values (" + livro + ", 'T-1') returning id");
    }

    private long insere(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private void emprestimo(long leitor, String emprestimo, String prevista, String devolucao) {
        jdbc.update("insert into emprestimo (leitor_id, exemplar_id, data_emprestimo, data_prevista, data_devolucao) values (?, ?, ?::date, ?::date, ?::date)",
                leitor, exemplar, emprestimo, prevista, devolucao);
    }

    @Test
    void umExemplarNaoPodeTerDoisEmprestimosAtivos() {
        emprestimo(leitorA, "2026-03-02", "2026-03-16", null);
        assertThrows(DataIntegrityViolationException.class, () -> emprestimo(leitorB, "2026-03-02", "2026-03-16", null));
    }

    @Test
    void depoisDeDevolvidoOExemplarPodeSerEmprestadoDeNovo() {
        emprestimo(leitorA, "2026-03-02", "2026-03-16", "2026-03-10"); // devolvido
        assertDoesNotThrow(() -> emprestimo(leitorB, "2026-03-11", "2026-03-25", null));
        // e o histórico pode ter vários empréstimos devolvidos do mesmo exemplar
        assertDoesNotThrow(() -> emprestimo(leitorA, "2026-01-01", "2026-01-15", "2026-01-05"));
    }

    @Test
    void emailDeLeitorEUnicoSemDiferenciarMaiusculas() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into leitor (nome, email) values ('C', 'A@X.EXEMPLO')"));
    }

    @Test
    void isbnEUnicoMasVariosLivrosSemIsbnSaoPermitidos() {
        jdbc.update("insert into livro (titulo, autor, isbn) values ('1', 'a', '9788535902775')");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into livro (titulo, autor, isbn) values ('2', 'b', '9788535902775')"));
        assertDoesNotThrow(() -> jdbc.update("insert into livro (titulo, autor) values ('3', 'c')"));
        assertDoesNotThrow(() -> jdbc.update("insert into livro (titulo, autor) values ('4', 'd')"));
    }

    @Test
    void tomboEUnico() {
        long livro = insere("insert into livro (titulo, autor) values ('M', 'Y') returning id");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into exemplar (livro_id, tombo) values (?, 'T-1')", livro));
    }

    @Test
    void datasIncoerentesSaoRecusadas() {
        // prazo antes do empréstimo
        assertThrows(DataIntegrityViolationException.class, () -> emprestimo(leitorA, "2026-03-10", "2026-03-01", null));
        // devolução antes do empréstimo
        assertThrows(DataIntegrityViolationException.class, () -> emprestimo(leitorA, "2026-03-10", "2026-03-24", "2026-03-01"));
    }

    @Test
    void renovacoesEMultaForaDosLimitesSaoRecusadas() {
        emprestimo(leitorA, "2026-03-02", "2026-03-16", null);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update emprestimo set renovacoes = 2"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update emprestimo set renovacoes = -1"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update emprestimo set multa_centavos = -50"));
    }

    @Test
    void chavesEstrangeirasImpedemApagarDadosComHistorico() {
        emprestimo(leitorA, "2026-03-02", "2026-03-16", null);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("delete from leitor where id = ?", leitorA));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("delete from exemplar where id = ?", exemplar));
    }

    @Test
    void anoDePublicacaoForaDaFaixaERecusado() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into livro (titulo, autor, ano_publicacao) values ('x','y', 50)"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into livro (titulo, autor, ano_publicacao) values ('x','y', 3000)"));
    }
}
