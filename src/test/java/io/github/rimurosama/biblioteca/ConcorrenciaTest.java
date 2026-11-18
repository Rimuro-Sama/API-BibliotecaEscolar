package io.github.rimurosama.biblioteca;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Requisições simultâneas de verdade (várias threads, transações independentes, PostgreSQL real).
 * Estes testes existem porque as regras "um exemplar por vez" e "no máximo 3 empréstimos" são fáceis de
 * furar quando duas requisições passam pela checagem ao mesmo tempo.
 */
class ConcorrenciaTest extends IntegracaoBase {

    /** Dispara as tarefas ao mesmo tempo (barreira) e devolve o status HTTP de cada uma. */
    private List<Integer> emParalelo(List<Callable<Integer>> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        CyclicBarrier largada = new CyclicBarrier(tarefas.size());
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (Callable<Integer> t : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return t.call();
                }));
            }
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> f : futuros) status.add(f.get());
            return status;
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(5)
    void oitoLeitoresDisputandoOMesmoExemplarSoUmConsegue() throws Exception {
        long exemplar = criaExemplarNovo("Dom Casmurro", "T-1");
        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            long leitor = criaLeitor("Leitor " + i, "leitor" + i + "@escola.exemplo");
            tarefas.add(() -> emprestar(leitor, exemplar).andReturn().getResponse().getStatus());
        }

        List<Integer> status = emParalelo(tarefas);

        assertEquals(1, status.stream().filter(s -> s == 201).count(), "exatamente um empréstimo criado: " + status);
        assertEquals(7, status.stream().filter(s -> s == 409).count(), "os outros sete recebem 409 (nunca 500): " + status);
        assertEquals(1, jdbc.queryForObject("select count(*) from emprestimo where exemplar_id = ? and data_devolucao is null", Long.class, exemplar));
    }

    @RepeatedTest(5)
    void mesmoLeitorNaoConsegueFurarOLimiteComRequisicoesSimultaneas() throws Exception {
        long leitor = criaLeitor("Ana", "ana@escola.exemplo");
        emprestaOk(leitor, criaExemplarNovo("Livro 1", "T-1"));
        emprestaOk(leitor, criaExemplarNovo("Livro 2", "T-2"));
        // já tem 2 de 3: duas requisições simultâneas para exemplares DIFERENTES, só uma pode passar
        long x = criaExemplarNovo("Livro 3", "T-3");
        long y = criaExemplarNovo("Livro 4", "T-4");

        List<Integer> status = emParalelo(List.of(
                () -> emprestar(leitor, x).andReturn().getResponse().getStatus(),
                () -> emprestar(leitor, y).andReturn().getResponse().getStatus()));

        assertEquals(1, status.stream().filter(s -> s == 201).count(), "só um passa: " + status);
        assertEquals(1, status.stream().filter(s -> s == 409).count(), "o outro bate no limite: " + status);
        assertEquals(3, jdbc.queryForObject("select count(*) from emprestimo where leitor_id = ? and data_devolucao is null", Long.class, leitor));
    }

    @Test
    void renovacoesSimultaneasDoMesmoEmprestimoContamosUmaVez() throws Exception {
        long leitor = criaLeitor("Ana", "ana@escola.exemplo");
        long id = emprestaOk(leitor, criaExemplarNovo("Dom Casmurro", "T-1"));

        List<Integer> status = emParalelo(List.of(
                () -> postVazio("/api/emprestimos/" + id + "/renovacao").andReturn().getResponse().getStatus(),
                () -> postVazio("/api/emprestimos/" + id + "/renovacao").andReturn().getResponse().getStatus(),
                () -> postVazio("/api/emprestimos/" + id + "/renovacao").andReturn().getResponse().getStatus()));

        assertEquals(1, status.stream().filter(s -> s == 200).count(), "uma renovação: " + status);
        assertEquals(2, status.stream().filter(s -> s == 409).count(), "as outras recusadas: " + status);
        assertEquals(1, jdbc.queryForObject("select renovacoes from emprestimo where id = ?", Integer.class, id));
        assertEquals("2026-03-23", jdbc.queryForObject("select data_prevista::text from emprestimo where id = ?", String.class, id));
    }

    @Test
    void devolucoesSimultaneasRegistramUmaSoVez() throws Exception {
        long leitor = criaLeitor("Ana", "ana@escola.exemplo");
        long id = emprestaOk(leitor, criaExemplarNovo("Dom Casmurro", "T-1"));
        relogio.avancaDias(20); // atrasado: a multa não pode ser aplicada em dobro

        List<Integer> status = emParalelo(List.of(
                () -> postVazio("/api/emprestimos/" + id + "/devolucao").andReturn().getResponse().getStatus(),
                () -> postVazio("/api/emprestimos/" + id + "/devolucao").andReturn().getResponse().getStatus()));

        assertEquals(1, status.stream().filter(s -> s == 200).count(), status.toString());
        assertEquals(1, status.stream().filter(s -> s == 409).count(), status.toString());
        assertEquals(300, jdbc.queryForObject("select multa_centavos from emprestimo where id = ?", Integer.class, id));
    }
}
