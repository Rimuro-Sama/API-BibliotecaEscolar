package io.github.rimurosama.biblioteca.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "leitor")
public class Leitor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(length = 40)
    private String turma;

    @Column(length = 20)
    private String telefone;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected Leitor() {
    }

    public Leitor(String nome, String email, String turma, String telefone) {
        atualizar(nome, email, turma, telefone);
    }

    public void atualizar(String nome, String email, String turma, String telefone) {
        this.nome = nome.strip();
        this.email = email.strip().toLowerCase();
        this.turma = turma == null || turma.isBlank() ? null : turma.strip();
        this.telefone = telefone == null || telefone.isBlank() ? null : telefone.strip();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getTurma() { return turma; }
    public String getTelefone() { return telefone; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
}
