package io.github.rimurosama.biblioteca;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeitorApiTest extends IntegracaoBase {

    @Test
    void cadastraLeitorEDevolveLocationENormalizaOEmail() throws Exception {
        postJson("/api/leitores", "{\"nome\":\"  Ana Souza \",\"email\":\"Ana.Souza@Escola.EXEMPLO\",\"turma\":\"2º B\",\"telefone\":\"11 90000-0000\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/leitores/")))
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana.souza@escola.exemplo"))
                .andExpect(jsonPath("$.turma").value("2º B"));
    }

    @Test
    void emailComEspacosNasPontasEhRejeitadoNaValidacao() throws Exception {
        postJson("/api/leitores", "{\"nome\":\"Ana\",\"email\":\" ana@escola.exemplo \"}")
                .andExpect(status().isBadRequest())
                .andExpect(campoErro("email"));
    }

    @Test
    void emailDuplicadoDaConflitSemDiferenciarMaiusculas() throws Exception {
        criaLeitor("Ana", "ana@escola.exemplo");
        postJson("/api/leitores", "{\"nome\":\"Outra Ana\",\"email\":\"ANA@escola.exemplo\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void validacaoDevolve400ComOsCamposInvalidos() throws Exception {
        postJson("/api/leitores", "{\"nome\":\"\",\"email\":\"isto-nao-e-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(campoErro("nome"))
                .andExpect(campoErro("email"));
    }

    @Test
    void jsonMalformadoDevolve400SemVazarDetalhesInternos() throws Exception {
        postJson("/api/leitores", "{nome: isso nao e json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(containsString("Exception"))));
    }

    @Test
    void leitorInexistenteDevolve404() throws Exception {
        getAutenticado("/api/leitores/999").andExpect(status().isNotFound());
        getAutenticado("/api/leitores/abc").andExpect(status().isBadRequest());
    }

    @Test
    void atualizaLeitorEPermiteManterOProprioEmail() throws Exception {
        long id = criaLeitor("Ana", "ana@escola.exemplo");
        mvc.perform(put("/api/leitores/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana Maria\",\"email\":\"ana@escola.exemplo\",\"turma\":\"3º A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Maria"))
                .andExpect(jsonPath("$.turma").value("3º A"));
    }

    @Test
    void naoPermiteAtualizarParaOEmailDeOutroLeitor() throws Exception {
        criaLeitor("Ana", "ana@escola.exemplo");
        long bia = criaLeitor("Bia", "bia@escola.exemplo");
        mvc.perform(put("/api/leitores/" + bia).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Bia\",\"email\":\"ana@escola.exemplo\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void removeLeitorSemHistoricoEMantemOhistoricoDeQuemJaEmprestou() throws Exception {
        long livre = criaLeitor("Sem Historico", "livre@escola.exemplo");
        mvc.perform(delete("/api/leitores/" + livre).with(admin())).andExpect(status().isNoContent());
        getAutenticado("/api/leitores/" + livre).andExpect(status().isNotFound());

        long comHistorico = criaLeitor("Com Historico", "historico@escola.exemplo");
        emprestaOk(comHistorico, criaExemplarNovo("Dom Casmurro", "T-1"));
        mvc.perform(delete("/api/leitores/" + comHistorico).with(admin())).andExpect(status().isConflict());
    }

    @Test
    void listaPaginadaEOrdenadaPorNome() throws Exception {
        for (String n : new String[]{"Eva", "Ana", "Dani", "Bia", "Caio"}) {
            criaLeitor(n, n.toLowerCase() + "@escola.exemplo");
        }
        getAutenticado("/api/leitores?pagina=0&tamanho=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(2)))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Ana"))
                .andExpect(jsonPath("$.conteudo[1].nome").value("Bia"))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3));
        getAutenticado("/api/leitores?pagina=2&tamanho=2")
                .andExpect(jsonPath("$.conteudo", hasSize(1)))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Eva"));
    }

    @Test
    void tamanhoDePaginaForaDoLimiteDevolve400() throws Exception {
        getAutenticado("/api/leitores?tamanho=0").andExpect(status().isBadRequest());
        getAutenticado("/api/leitores?tamanho=101").andExpect(status().isBadRequest());
        getAutenticado("/api/leitores?pagina=-1").andExpect(status().isBadRequest());
    }

    @Test
    void semCredenciaisOsDadosPessoaisNaoSaem() throws Exception {
        criaLeitor("Ana", "ana@escola.exemplo");
        mvc.perform(get("/api/leitores")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leitores/1")).andExpect(status().isUnauthorized());
    }
}
