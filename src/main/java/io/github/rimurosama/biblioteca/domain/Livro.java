package io.github.rimurosama.biblioteca.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "livro")
public class Livro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, length = 150)
    private String autor;

    @Column(name = "ano_publicacao")
    private Integer anoPublicacao;

    @Column(length = 20)
    private String isbn;

    protected Livro() {
    }

    public Livro(String titulo, String autor, Integer anoPublicacao, String isbn) {
        atualizar(titulo, autor, anoPublicacao, isbn);
    }

    public void atualizar(String titulo, String autor, Integer anoPublicacao, String isbn) {
        this.titulo = titulo.strip();
        this.autor = autor.strip();
        this.anoPublicacao = anoPublicacao;
        this.isbn = isbn == null || isbn.isBlank() ? null : isbn.strip();
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getAutor() { return autor; }
    public Integer getAnoPublicacao() { return anoPublicacao; }
    public String getIsbn() { return isbn; }
}
