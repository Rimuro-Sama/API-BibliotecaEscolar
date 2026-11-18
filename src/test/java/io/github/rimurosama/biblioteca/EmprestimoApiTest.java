package io.github.rimurosama.biblioteca;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regras de empréstimo, de ponta a ponta (HTTP + PostgreSQL). O "hoje" vem do relógio de teste: segunda-feira
 * 2026-03-02, então o prazo padrão vence em 2026-03-16.
 */
class EmprestimoApiTest extends IntegracaoBase {
    private long ana;

    @BeforeEach
    void cenario() throws Exception {
        ana = criaLeitor("Ana Souza", "ana@escola.exemplo");
    }

    @Test
    void emprestimoTemPrazoDeCatorzeDias() throws Exception {
        long ex = criaExemplarNovo("Dom Casmurro", "T-1");
        emprestar(ana, ex)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/emprestimos/")))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.dataEmprestimo").value("2026-03-02"))
                .andExpect(jsonPath("$.dataPrevista").value("2026-03-16"))
                .andExpect(jsonPath("$.dataDevolucao").doesNotExist())
                .andExpect(jsonPath("$.titulo").value("Dom Casmurro"))
                .andExpect(jsonPath("$.leitorNome").value("Ana Souza"))
                .andExpect(jsonPath("$.multaCentavos").value(0));
    }

    @Test
    void exemplarJaEmprestadoDaConflict() throws Exception {
        long ex = criaExemplarNovo("Dom Casmurro", "T-1");
        long bia = criaLeitor("Bia", "bia@escola.exemplo");
        emprestaOk(ana, ex);
        emprestar(bia, ex).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("T-1")));
    }

    @Test
    void leitorOuExemplarInexistentesDao404() throws Exception {
        long ex = criaExemplarNovo("Dom Casmurro", "T-1");
        emprestar(999, ex).andExpect(status().isNotFound());
        emprestar(ana, 999).andExpect(status().isNotFound());
        postJson("/api/emprestimos", "{\"leitorId\":null}").andExpect(status().isBadRequest())
                .andExpect(campoErro("leitorId")).andExpect(campoErro("exemplarId"));
    }

    @Test
    void limiteDeTresEmprestimosAtivos() throws Exception {
        for (int i = 1; i <= 3; i++) emprestaOk(ana, criaExemplarNovo("Livro " + i, "T-" + i));
        long quarto = criaExemplarNovo("Livro 4", "T-4");
        emprestar(ana, quarto).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("3")));

        // devolver um libera de novo
        long primeiro = idDoPrimeiroEmprestimo();
        postVazio("/api/emprestimos/" + primeiro + "/devolucao").andExpect(status().isOk());
        emprestar(ana, quarto).andExpect(status().isCreated());
    }

    @Test
    void renovaUmaVezSomandoSeteDiasAoPrazoAtual() throws Exception {
        long id = emprestaOk(ana, criaExemplarNovo("Dom Casmurro", "T-1"));
        relogio.avancaDias(5); // 2026-03-07, ainda no prazo
        postVazio("/api/emprestimos/" + id + "/renovacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataPrevista").value("2026-03-23")) // 16 + 7 (e não "hoje" + 7)
                .andExpect(jsonPath("$.renovacoes").value(1))
                .andExpect(jsonPath("$.status").value("ATIVO"));
        // segunda renovação: não pode
        postVazio("/api/emprestimos/" + id + "/renovacao").andExpect(status().isConflict());
    }

    @Test
    void podeRenovarNoProprioDiaDoPrazo() throws Exception {
        long id = emprestaOk(ana, criaExemplarNovo("Dom Casmurro", "T-1"));
        relogio.avancaDias(14); // 2026-03-16 = dia do prazo
        postVazio("/api/emprestimos/" + id + "/renovacao").andExpect(status().isOk()).andExpect(jsonPath("$.dataPrevista").value("2026-03-23"));
    }

    @Test
    void emprestimoAtrasadoNaoPodeSerRenovado() throws Exception {
        long id = emprestaOk(ana, criaExemplarNovo("Dom Casmurro", "T-1"));
        relogio.avancaDias(15); // 2026-03-17, 1 dia de atraso
        postVazio("/api/emprestimos/" + id + "/renovacao").andExpect(status().isConflict());
        getAutenticado("/api/emprestimos/" + id)
                .andExpect(jsonPath("$.status").value("ATRASADO")).andExpect(jsonPath("$.diasDeAtraso").value(1));
    }

    @Test
    void devolucaoNoPrazoNaoGeraMulta() throws Exception {
        long id = emprestaOk(ana, criaExemplarNovo("Dom Casmurro", "T-1"));
        relogio.avancaDias(14); // no dia do prazo
        postVazio("/api/emprestimos/" + id + "/devolucao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEVOLVIDO"))
                .andExpect(jsonPath("$.dataDevolucao").value("2026-03-16"))
                .andExpect(jsonPath("$.multaCentavos").value(0))
                .andExpect(jsonPath("$.diasDeAtraso").value(0));
    }

    @Test
    void devolucaoComSeisDiasDeAtrasoGeraMultaDeTrezentosCentavos() throws Exception {
        long id = emprestaOk(ana, criaExemplarNovo("Dom Casmurro", "T-1"));
        relogio.avancaDias(20); // 2026-03-22: prazo 16/03, 6 dias de atraso
        postVazio("/api/emprestimos/" + id + "/devolucao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeAtraso").value(6))
                .andExpect(jsonPath("$.multaCentavos").value(300))
                .andExpect(jsonPath("$.multaPaga").value(false));
    }

    @Test
    void multaDepoisDeRenovarUsaOPrazoRenovado() throws Exception {
        long id = emprestaOk(ana, criaExemplarNovo("Dom Casmurro", "T-1"));
        postVazio("/api/emprestimos/" + id + "/renovacao").andExpect(status().isOk()); // prazo vira 23/03
        relogio.avancaDias(23); // 2026-03-25: 2 dias depois do prazo renovado
        postVazio("/api/emprestimos/" + id + "/devolucao").andExpect(jsonPath("$.diasDeAtraso").value(2)).andExpect(jsonPath("$.multaCentavos").value(100));
    }

    @Test
    void devolverDuasVezesDaConflict() throws Exception {
        long id = emprestaOk(ana, criaExemplarNovo("Dom Casmurro", "T-1"));
        postVazio("/api/emprestimos/" + id + "/devolucao").andExpect(status().isOk());
        postVazio("/api/emprestimos/" + id + "/devolucao").andExpect(status().isConflict());
        postVazio("/api/emprestimos/" + id + "/renovacao").andExpect(status().isConflict()); // devolvido não renova
    }

    @Test
    void multaPendenteBloqueiaNovosEmprestimosAtePagar() throws Exception {
        long id = emprestaOk(ana, criaExemplarNovo("Dom Casmurro", "T-1"));
        long outro = criaExemplarNovo("Vidas Secas", "T-2");
        relogio.avancaDias(20);
        postVazio("/api/emprestimos/" + id + "/devolucao").andExpect(jsonPath("$.multaCentavos").value(300));

        emprestar(ana, outro).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("multa")));
        getAutenticado("/api/multas").andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.conteudo[0].multaCentavos").value(300));

        postVazio("/api/emprestimos/" + id + "/pagamento-multa").andExpect(status().isOk()).andExpect(jsonPath("$.multaPaga").value(true));
        getAutenticado("/api/multas").andExpect(jsonPath("$.total").value(0));
        emprestar(ana, outro).andExpect(status().isCreated()); // liberado
    }

    @Test
    void pagamentoDeMultaInexistenteOuRepetidoDaConflict() throws Exception {
        long id = emprestaOk(ana, criaExemplarNovo("Dom Casmurro", "T-1"));
        postVazio("/api/emprestimos/" + id + "/devolucao").andExpect(status().isOk()); // sem multa
        postVazio("/api/emprestimos/" + id + "/pagamento-multa").andExpect(status().isConflict()); // não há multa

        long id2 = emprestaOk(ana, criaExemplarNovo("Vidas Secas", "T-2"));
        relogio.avancaDias(20);
        postVazio("/api/emprestimos/" + id2 + "/devolucao").andExpect(jsonPath("$.multaCentavos").value(300));
        postVazio("/api/emprestimos/" + id2 + "/pagamento-multa").andExpect(status().isOk());
        postVazio("/api/emprestimos/" + id2 + "/pagamento-multa").andExpect(status().isConflict()); // já paga
        postVazio("/api/emprestimos/999/pagamento-multa").andExpect(status().isNotFound());
    }

    @Test
    void emprestimoAtrasadoBloqueiaNovoEmprestimo() throws Exception {
        emprestaOk(ana, criaExemplarNovo("Dom Casmurro", "T-1"));
        long outro = criaExemplarNovo("Vidas Secas", "T-2");
        relogio.avancaDias(15); // atrasado, ainda não devolvido
        emprestar(ana, outro).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("atrasado")));
    }

    @Test
    void listaAtrasadosAtivosETodos() throws Exception {
        long bia = criaLeitor("Bia", "bia@escola.exemplo");
        long noPrazo = emprestaOk(bia, criaExemplarNovo("Livro A", "T-A"));
        long atrasado = emprestaOk(ana, criaExemplarNovo("Livro B", "T-B"));
        relogio.avancaDias(3);
        // Bia pega mais um, 3 dias depois: terá prazo maior que o de Ana
        long recente = emprestaOk(bia, criaExemplarNovo("Livro C", "T-C"));
        relogio.avancaDias(12); // 2026-03-17: o de Ana e o primeiro da Bia (16/03) estão atrasados; "recente" vence em 19/03

        getAutenticado("/api/emprestimos?status=ativos").andExpect(jsonPath("$.total").value(3));
        getAutenticado("/api/emprestimos?status=atrasados").andExpect(jsonPath("$.total").value(2));
        postVazio("/api/emprestimos/" + atrasado + "/devolucao").andExpect(status().isOk());
        getAutenticado("/api/emprestimos?status=atrasados").andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.conteudo[0].id").value(noPrazo));
        getAutenticado("/api/emprestimos?status=todos").andExpect(jsonPath("$.total").value(3));
        getAutenticado("/api/emprestimos").andExpect(jsonPath("$.total").value(3));
        getAutenticado("/api/emprestimos?status=qualquer").andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertNotEquals(recente, noPrazo);
    }

    @Test
    void historicoDoLeitorVemDoMaisRecenteParaOMaisAntigo() throws Exception {
        long primeiro = emprestaOk(ana, criaExemplarNovo("Livro 1", "T-1"));
        long segundo = emprestaOk(ana, criaExemplarNovo("Livro 2", "T-2"));
        getAutenticado("/api/leitores/" + ana + "/emprestimos")
                .andExpect(jsonPath("$.conteudo", hasSize(2)))
                .andExpect(jsonPath("$.conteudo[0].id").value(segundo))
                .andExpect(jsonPath("$.conteudo[1].id").value(primeiro));
        getAutenticado("/api/leitores/999/emprestimos").andExpect(status().isNotFound());
    }

    @Test
    void relatorioDeMaisEmprestadosOrdenaPorQuantidadeEDepoisPorTitulo() throws Exception {
        long bia = criaLeitor("Bia", "bia@escola.exemplo");
        long livroA = criaLivro("Alfa", "X");
        long livroB = criaLivro("Beta", "X");
        long livroC = criaLivro("Gama", "X");
        long a1 = criaExemplar(livroA, "A-1");
        long b1 = criaExemplar(livroB, "B-1");
        long c1 = criaExemplar(livroC, "C-1");

        // Gama: 3 empréstimos; Alfa e Beta: 1 cada (empate resolvido por título)
        for (int i = 0; i < 3; i++) {
            long id = emprestaOk(i % 2 == 0 ? ana : bia, c1);
            postVazio("/api/emprestimos/" + id + "/devolucao").andExpect(status().isOk());
        }
        postVazio("/api/emprestimos/" + emprestaOk(ana, b1) + "/devolucao").andExpect(status().isOk());
        postVazio("/api/emprestimos/" + emprestaOk(ana, a1) + "/devolucao").andExpect(status().isOk());

        getAutenticado("/api/relatorios/mais-emprestados?limite=10")
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].titulo").value("Gama")).andExpect(jsonPath("$[0].emprestimos").value(3))
                .andExpect(jsonPath("$[1].titulo").value("Alfa"))
                .andExpect(jsonPath("$[2].titulo").value("Beta"));
        getAutenticado("/api/relatorios/mais-emprestados?limite=1").andExpect(jsonPath("$", hasSize(1)));
        getAutenticado("/api/relatorios/mais-emprestados?limite=0").andExpect(status().isBadRequest());
    }

    @Test
    void operacoesDeEmprestimoExigemAutenticacao() throws Exception {
        long ex = criaExemplarNovo("Dom Casmurro", "T-1");
        long id = emprestaOk(ana, ex);
        mvc.perform(post("/api/emprestimos").contentType("application/json").content("{\"leitorId\":1,\"exemplarId\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/emprestimos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/emprestimos/" + id + "/devolucao")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/emprestimos/" + id + "/renovacao")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/multas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/relatorios/mais-emprestados")).andExpect(status().isUnauthorized());
    }

    private long idDoPrimeiroEmprestimo() throws Exception {
        String json = getAutenticado("/api/emprestimos?status=ativos&tamanho=1").andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.conteudo[0].id")).longValue();
    }
}
