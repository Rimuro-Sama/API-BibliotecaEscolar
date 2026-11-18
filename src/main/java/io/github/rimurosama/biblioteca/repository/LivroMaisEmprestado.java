package io.github.rimurosama.biblioteca.repository;

/** Linha do relatório de livros mais emprestados. */
public record LivroMaisEmprestado(Long livroId, String titulo, String autor, long emprestimos) {
}
