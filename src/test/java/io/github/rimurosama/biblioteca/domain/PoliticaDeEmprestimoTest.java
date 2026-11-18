package io.github.rimurosama.biblioteca.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticaDeEmprestimoTest {
    private static final LocalDate EMPRESTIMO = LocalDate.of(2026, 3, 2);
    private static final LocalDate PREVISTA = LocalDate.of(2026, 3, 16); // 14 dias depois

    @Test
    void prazoEDeCatorzeDias() {
        assertEquals(PREVISTA, PoliticaDeEmprestimo.dataPrevista(EMPRESTIMO));
    }

    @Test
    void prazoAtravessaFimDeMesEDeAno() {
        assertEquals(LocalDate.of(2026, 1, 8), PoliticaDeEmprestimo.dataPrevista(LocalDate.of(2025, 12, 25)));
        assertEquals(LocalDate.of(2024, 3, 8), PoliticaDeEmprestimo.dataPrevista(LocalDate.of(2024, 2, 23))); // 2024 é bissexto
    }

    @Test
    void renovacaoSomaSeteDiasAoPrazoAtualENaoAHoje() {
        assertEquals(LocalDate.of(2026, 3, 23), PoliticaDeEmprestimo.novaDataPrevista(PREVISTA));
    }

    @ParameterizedTest(name = "devolvido em {0} -> {1} dia(s) de atraso, multa de {2} centavos")
    @CsvSource({
            "2026-03-02, 0, 0",    // devolvido no mesmo dia
            "2026-03-15, 0, 0",    // um dia antes do prazo
            "2026-03-16, 0, 0",    // NO dia do prazo: ainda não é atraso
            "2026-03-17, 1, 50",   // um dia depois: primeira multa
            "2026-03-18, 2, 100",
            "2026-03-22, 6, 300",
            "2026-04-15, 30, 1500",
    })
    void multaDeCinquentaCentavosPorDiaDeAtraso(LocalDate devolucao, int dias, int centavos) {
        assertEquals(dias, PoliticaDeEmprestimo.diasDeAtraso(PREVISTA, devolucao));
        assertEquals(centavos, PoliticaDeEmprestimo.multaCentavos(PREVISTA, devolucao));
    }

    @Test
    void multaUsaSomenteInteirosSemArredondamento() {
        // 3 dias de atraso = exatamente 150 centavos, sem erro de ponto flutuante
        assertEquals(150, PoliticaDeEmprestimo.multaCentavos(PREVISTA, PREVISTA.plusDays(3)));
        assertEquals(0, PoliticaDeEmprestimo.multaCentavos(PREVISTA, PREVISTA.minusDays(10)));
    }

    @Test
    void podeRenovarAteODiaDoPrazoInclusive() {
        assertTrue(PoliticaDeEmprestimo.podeRenovar(0, PREVISTA, PREVISTA.minusDays(5)));
        assertTrue(PoliticaDeEmprestimo.podeRenovar(0, PREVISTA, PREVISTA)); // no dia do prazo ainda vale
        assertFalse(PoliticaDeEmprestimo.podeRenovar(0, PREVISTA, PREVISTA.plusDays(1))); // atrasado
    }

    @Test
    void naoPodeRenovarAlemDoLimite() {
        assertFalse(PoliticaDeEmprestimo.podeRenovar(PoliticaDeEmprestimo.MAX_RENOVACOES, PREVISTA, EMPRESTIMO));
        assertFalse(PoliticaDeEmprestimo.podeRenovar(5, PREVISTA, EMPRESTIMO));
    }

    @Test
    void constantesDaPolitica() {
        assertEquals(14, PoliticaDeEmprestimo.PRAZO_DIAS);
        assertEquals(7, PoliticaDeEmprestimo.RENOVACAO_DIAS);
        assertEquals(1, PoliticaDeEmprestimo.MAX_RENOVACOES);
        assertEquals(3, PoliticaDeEmprestimo.MAX_EMPRESTIMOS_ATIVOS);
        assertEquals(50, PoliticaDeEmprestimo.MULTA_POR_DIA_CENTAVOS);
    }
}
