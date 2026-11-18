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

import java.time.LocalDate;

@Entity
@Table(name = "emprestimo")
public class Emprestimo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leitor_id", nullable = false)
    private Leitor leitor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exemplar_id", nullable = false)
    private Exemplar exemplar;

    @Column(name = "data_emprestimo", nullable = false)
    private LocalDate dataEmprestimo;

    @Column(name = "data_prevista", nullable = false)
    private LocalDate dataPrevista;

    @Column(name = "data_devolucao")
    private LocalDate dataDevolucao;

    @Column(nullable = false)
    private int renovacoes;

    @Column(name = "multa_centavos", nullable = false)
    private int multaCentavos;

    @Column(name = "multa_paga", nullable = false)
    private boolean multaPaga;

    protected Emprestimo() {
    }

    /** Abre um empréstimo em {@code hoje}, com o prazo padrão. */
    public Emprestimo(Leitor leitor, Exemplar exemplar, LocalDate hoje) {
        this.leitor = leitor;
        this.exemplar = exemplar;
        this.dataEmprestimo = hoje;
        this.dataPrevista = PoliticaDeEmprestimo.dataPrevista(hoje);
    }

    public boolean estaAtivo() {
        return dataDevolucao == null;
    }

    public boolean estaAtrasadoEm(LocalDate referencia) {
        return estaAtivo() && referencia.isAfter(dataPrevista);
    }

    public StatusEmprestimo statusEm(LocalDate referencia) {
        if (!estaAtivo()) return StatusEmprestimo.DEVOLVIDO;
        return estaAtrasadoEm(referencia) ? StatusEmprestimo.ATRASADO : StatusEmprestimo.ATIVO;
    }

    public void renovar() {
        this.dataPrevista = PoliticaDeEmprestimo.novaDataPrevista(dataPrevista);
        this.renovacoes++;
    }

    /** Registra a devolução e calcula a multa pelo atraso, se houver. */
    public void devolver(LocalDate hoje) {
        this.dataDevolucao = hoje;
        this.multaCentavos = PoliticaDeEmprestimo.multaCentavos(dataPrevista, hoje);
        this.multaPaga = false;
    }

    public void pagarMulta() {
        this.multaPaga = true;
    }

    public boolean temMultaPendente() {
        return multaCentavos > 0 && !multaPaga;
    }

    public Long getId() { return id; }
    public Leitor getLeitor() { return leitor; }
    public Exemplar getExemplar() { return exemplar; }
    public LocalDate getDataEmprestimo() { return dataEmprestimo; }
    public LocalDate getDataPrevista() { return dataPrevista; }
    public LocalDate getDataDevolucao() { return dataDevolucao; }
    public int getRenovacoes() { return renovacoes; }
    public int getMultaCentavos() { return multaCentavos; }
    public boolean isMultaPaga() { return multaPaga; }
}
