package io.github.rimurosama.biblioteca.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Cópia física de um livro, identificada pelo número de tombo. */
@Entity
@Table(name = "exemplar")
public class Exemplar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "livro_id", nullable = false)
    private Livro livro;

    @Column(nullable = false, length = 30)
    private String tombo;

    protected Exemplar() {
    }

    public Exemplar(Livro livro, String tombo) {
        this.livro = livro;
        this.tombo = tombo.strip();
    }

    public Long getId() { return id; }
    public Livro getLivro() { return livro; }
    public String getTombo() { return tombo; }
}
