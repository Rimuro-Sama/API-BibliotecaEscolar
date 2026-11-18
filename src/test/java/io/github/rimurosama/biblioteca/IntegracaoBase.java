package io.github.rimurosama.biblioteca;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base dos testes de integração: sobe a aplicação inteira contra um PostgreSQL real e limpa os dados a cada teste. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegracaoBase.RelogioConfig.class)
public abstract class IntegracaoBase {
    protected static final String USUARIO = "bibliotecario-teste";
    protected static final String SENHA = "senha-de-teste-apenas";
    /** Segunda-feira; o "hoje" de todos os testes, salvo quando o teste avança o relógio. */
    protected static final LocalDate HOJE = LocalDate.of(2026, 3, 2);

    /** Relógio que os testes controlam, para simular atrasos sem esperar dias de verdade. */
    public static class RelogioDeTeste extends Clock {
        private final AtomicReference<Instant> agora = new AtomicReference<>();
        private final ZoneId zona = ZoneId.of("America/Sao_Paulo");

        public void fixa(LocalDate dia) {
            agora.set(dia.atTime(12, 0).atZone(zona).toInstant());
        }

        public void avancaDias(int dias) {
            agora.updateAndGet(i -> i.plusSeconds(dias * 86_400L));
        }

        @Override public ZoneId getZone() { return zona; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return agora.get(); }
    }

    @TestConfiguration
    static class RelogioConfig {
        @Bean
        @Primary
        RelogioDeTeste relogioDeTeste() {
            RelogioDeTeste r = new RelogioDeTeste();
            r.fixa(HOJE);
            return r;
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected RelogioDeTeste relogio;

    @BeforeEach
    void limpaBancoEFixaRelogio() {
        jdbc.execute("TRUNCATE TABLE emprestimo, exemplar, livro, leitor RESTART IDENTITY CASCADE");
        relogio.fixa(HOJE);
    }

    protected RequestPostProcessor admin() {
        return httpBasic(USUARIO, SENHA);
    }

    // ---- atalhos para montar cenários ----

    protected ResultActions postJson(String url, String corpo) throws Exception {
        return mvc.perform(post(url).with(admin()).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    protected ResultActions postVazio(String url) throws Exception {
        return mvc.perform(post(url).with(admin()));
    }

    protected ResultActions getAutenticado(String url) throws Exception {
        return mvc.perform(get(url).with(admin()));
    }

    protected long criaLeitor(String nome, String email) throws Exception {
        String corpo = "{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\"}";
        String json = postJson("/api/leitores", corpo).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return idDe(json);
    }

    protected long criaLivro(String titulo, String autor) throws Exception {
        String corpo = "{\"titulo\":\"" + titulo + "\",\"autor\":\"" + autor + "\"}";
        String json = postJson("/api/livros", corpo).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return idDe(json);
    }

    protected long criaExemplar(long livroId, String tombo) throws Exception {
        String json = postJson("/api/livros/" + livroId + "/exemplares", "{\"tombo\":\"" + tombo + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return idDe(json);
    }

    /** Cria um livro com um exemplar e devolve o id do exemplar. */
    protected long criaExemplarNovo(String titulo, String tombo) throws Exception {
        return criaExemplar(criaLivro(titulo, "Autor Teste"), tombo);
    }

    protected ResultActions emprestar(long leitorId, long exemplarId) throws Exception {
        return postJson("/api/emprestimos", "{\"leitorId\":" + leitorId + ",\"exemplarId\":" + exemplarId + "}");
    }

    protected long emprestaOk(long leitorId, long exemplarId) throws Exception {
        String json = emprestar(leitorId, exemplarId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return idDe(json);
    }

    private static long idDe(String json) {
        return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.id")).longValue();
    }

    protected static org.springframework.test.web.servlet.ResultMatcher campoErro(String campo) {
        return jsonPath("$.campos." + campo).exists();
    }
}
