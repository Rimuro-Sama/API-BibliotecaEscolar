package io.github.rimurosama.biblioteca;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SegurancaTest extends IntegracaoBase {

    @Test
    void semCredenciaisRecebe401ComDesafioBasic() throws Exception {
        mvc.perform(get("/api/leitores"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")));
    }

    @Test
    void senhaOuUsuarioErradosRecebem401() throws Exception {
        mvc.perform(get("/api/leitores").with(httpBasic(USUARIO, "senha-errada"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leitores").with(httpBasic("outro-usuario", SENHA))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leitores").with(httpBasic("", ""))).andExpect(status().isUnauthorized());
    }

    @Test
    void credenciaisCorretasPermitemAcesso() throws Exception {
        mvc.perform(get("/api/leitores").with(admin())).andExpect(status().isOk());
    }

    @Test
    void respostaDe401NaoVazaDetalhesInternos() throws Exception {
        mvc.perform(get("/api/leitores"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("springframework"))));
    }

    @Test
    void catalogoHealthEDocumentacaoSaoPublicos() throws Exception {
        mvc.perform(get("/api/livros")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.info.title").value("API Biblioteca Escolar"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void apenasOHealthEExpostoPeloActuator() throws Exception {
        // mesmo autenticado, endpoints sensíveis do Actuator (env, beans, heapdump...) não existem
        for (String caminho : new String[]{"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/configprops", "/actuator/mappings"}) {
            mvc.perform(get(caminho).with(admin())).andExpect(status().isNotFound());
        }
    }

    @Test
    void escritaNoCatalogoExigeAutenticacaoMesmoSendoAConsultaPublica() throws Exception {
        mvc.perform(post("/api/livros").contentType("application/json").content("{\"titulo\":\"X\",\"autor\":\"Y\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/livros/1/exemplares").contentType("application/json").content("{\"tombo\":\"T\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void respostasNaoExpoemOHashDaSenhaNemDadosDeConfiguracao() throws Exception {
        criaLeitor("Ana", "ana@escola.exemplo");
        String corpo = getAutenticado("/api/leitores").andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(corpo.contains("$2a$"), "hash de senha vazou");
        org.junit.jupiter.api.Assertions.assertFalse(corpo.contains(SENHA), "senha vazou");
    }

    @Test
    void erroDeNegocioNaoRevelaEstruturaDoBanco() throws Exception {
        criaLeitor("Ana", "ana@escola.exemplo");
        postJson("/api/leitores", "{\"nome\":\"Outra\",\"email\":\"ana@escola.exemplo\"}")
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("uk_leitor_email"))))
                .andExpect(content().string(not(containsString("constraint"))));
    }
}
