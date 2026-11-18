package io.github.rimurosama.biblioteca.domain;

public enum StatusEmprestimo {
    /** Em andamento e dentro do prazo. */
    ATIVO,
    /** Em andamento e com o prazo vencido. */
    ATRASADO,
    /** Já devolvido. */
    DEVOLVIDO
}
