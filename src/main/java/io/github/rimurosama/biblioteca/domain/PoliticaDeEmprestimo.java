package io.github.rimurosama.biblioteca.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Regras de empréstimo da biblioteca, em funções puras (sem banco, sem relógio do sistema).
 *
 * <ul>
 *   <li>Prazo de 14 dias a partir do empréstimo.</li>
 *   <li>Uma renovação por empréstimo, de +7 dias contados a partir do prazo atual (não de "hoje"),
 *       e só enquanto o empréstimo não estiver atrasado.</li>
 *   <li>No máximo 3 empréstimos ativos por leitor.</li>
 *   <li>Multa de R$ 0,50 por dia corrido de atraso, calculada na devolução. Valores em centavos (inteiros),
 *       para nunca usar ponto flutuante com dinheiro.</li>
 * </ul>
 */
public final class PoliticaDeEmprestimo {
    public static final int PRAZO_DIAS = 14;
    public static final int RENOVACAO_DIAS = 7;
    public static final int MAX_RENOVACOES = 1;
    public static final int MAX_EMPRESTIMOS_ATIVOS = 3;
    public static final int MULTA_POR_DIA_CENTAVOS = 50;

    private PoliticaDeEmprestimo() {
    }

    public static LocalDate dataPrevista(LocalDate dataEmprestimo) {
        return dataEmprestimo.plusDays(PRAZO_DIAS);
    }

    public static LocalDate novaDataPrevista(LocalDate dataPrevistaAtual) {
        return dataPrevistaAtual.plusDays(RENOVACAO_DIAS);
    }

    /** Dias corridos de atraso em {@code referencia}; 0 se ainda está no prazo (o próprio dia do prazo não é atraso). */
    public static int diasDeAtraso(LocalDate dataPrevista, LocalDate referencia) {
        long dias = ChronoUnit.DAYS.between(dataPrevista, referencia);
        return dias > 0 ? (int) dias : 0;
    }

    public static int multaCentavos(LocalDate dataPrevista, LocalDate dataDevolucao) {
        return diasDeAtraso(dataPrevista, dataDevolucao) * MULTA_POR_DIA_CENTAVOS;
    }

    /** Pode renovar se ainda há renovações disponíveis e o prazo não venceu (o dia do prazo ainda vale). */
    public static boolean podeRenovar(int renovacoesFeitas, LocalDate dataPrevista, LocalDate hoje) {
        return renovacoesFeitas < MAX_RENOVACOES && !hoje.isAfter(dataPrevista);
    }
}
