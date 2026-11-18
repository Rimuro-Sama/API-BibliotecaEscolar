package io.github.rimurosama.biblioteca;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LivroApiTest extends IntegracaoBase {

    @Test
    void catalogoEPublicoMasCadastroExigeAutenticacao() throws Exception {
        criaLivro("Dom Casmurro", "Machado de Assis");
        mvc.perform(get("/api/livros")).andExpect(status().isOk()).andExpect(jsonPath("$.conteudo", hasSize(1)));
        mvc.perform(get("/api/livros/1")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/livros")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"X\",\"autor\":\"Y\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/livros/1")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/livros/1").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void buscaPorTituloOuAutorSemDiferenciarMaiusculas() throws Exception {
        criaLivro("Dom Casmurro", "Machado de Assis");
        criaLivro("Memórias Póstumas de Brás Cubas", "Machado de Assis");
        criaLivro("Vidas Secas", "Graciliano Ramos");

        mvc.perform(get("/api/livros?busca=machado")).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/livros?busca=CASMURRO")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/livros?busca=vidas")).andExpect(jsonPath("$.conteudo[0].autor").value("Graciliano Ramos"));
        mvc.perform(get("/api/livros?busca=inexistente")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/livros")).andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void caracteresCoringaNaBuscaSaoTratadosComoTextoLiteral() throws Exception {
        criaLivro("100% Java", "Fulano");
        criaLivro("Dom Casmurro", "Machado");
        // "%" sozinho NÃO pode casar com tudo, e "_" não pode ser "qualquer caractere"
        // .param() evita a dupla codificação que get("...?busca=%25") faria (o servidor receberia "%25" em vez de "%")
        mvc.perform(get("/api/livros").param("busca", "%")).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.conteudo[0].titulo").value("100% Java"));
        mvc.perform(get("/api/livros").param("busca", "D_m")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/livros").param("busca", "100!")).andExpect(jsonPath("$.total").value(0)); // o próprio caractere de escape também é literal
    }

    @Test
    void catalogoMostraExemplaresEDisponiveis() throws Exception {
        long livro = criaLivro("Dom Casmurro", "Machado de Assis");
        long e1 = criaExemplar(livro, "T-001");
        criaExemplar(livro, "T-002");
        criaExemplar(livro, "T-003");
        long leitor = criaLeitor("Ana", "ana@escola.exemplo");

        mvc.perform(get("/api/livros/" + livro))
                .andExpect(jsonPath("$.exemplares").value(3)).andExpect(jsonPath("$.disponiveis").value(3));

        emprestaOk(leitor, e1);
        mvc.perform(get("/api/livros/" + livro))
                .andExpect(jsonPath("$.exemplares").value(3)).andExpect(jsonPath("$.disponiveis").value(2));
        mvc.perform(get("/api/livros?busca=casmurro"))
                .andExpect(jsonPath("$.conteudo[0].exemplares").value(3)).andExpect(jsonPath("$.conteudo[0].disponiveis").value(2));
        mvc.perform(get("/api/livros/" + livro + "/exemplares"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].tombo").value("T-001")).andExpect(jsonPath("$[0].disponivel").value(false))
                .andExpect(jsonPath("$[1].disponivel").value(true));
    }

    @Test
    void livroSemExemplaresTemContagemZero() throws Exception {
        long livro = criaLivro("Sem Exemplares", "Autor");
        mvc.perform(get("/api/livros/" + livro))
                .andExpect(jsonPath("$.exemplares").value(0)).andExpect(jsonPath("$.disponiveis").value(0));
    }

    @Test
    void isbnDuplicadoDaConflitEIsbnInvalidoDa400() throws Exception {
        postJson("/api/livros", "{\"titulo\":\"A\",\"autor\":\"B\",\"isbn\":\"978-85-359-0277-5\"}").andExpect(status().isCreated());
        postJson("/api/livros", "{\"titulo\":\"Outro\",\"autor\":\"C\",\"isbn\":\"978-85-359-0277-5\"}").andExpect(status().isConflict());
        postJson("/api/livros", "{\"titulo\":\"Outro\",\"autor\":\"C\",\"isbn\":\"abc; drop table livro\"}")
                .andExpect(status().isBadRequest()).andExpect(campoErro("isbn"));
        // vários livros sem ISBN são permitidos
        postJson("/api/livros", "{\"titulo\":\"S1\",\"autor\":\"C\"}").andExpect(status().isCreated());
        postJson("/api/livros", "{\"titulo\":\"S2\",\"autor\":\"C\"}").andExpect(status().isCreated());
    }

    @Test
    void validaCamposObrigatoriosEAnoDePublicacao() throws Exception {
        postJson("/api/livros", "{\"titulo\":\"\",\"autor\":\"\",\"anoPublicacao\":50}")
                .andExpect(status().isBadRequest()).andExpect(campoErro("titulo")).andExpect(campoErro("autor")).andExpect(campoErro("anoPublicacao"));
    }

    @Test
    void atualizaLivro() throws Exception {
        long id = criaLivro("Titulo Velho", "Autor");
        mvc.perform(put("/api/livros/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Titulo Novo\",\"autor\":\"Autor\",\"anoPublicacao\":1899}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Titulo Novo")).andExpect(jsonPath("$.anoPublicacao").value(1899));
    }

    @Test
    void tomboDuplicadoDaConflitEExemplarDeLivroInexistenteDa404() throws Exception {
        long livro = criaLivro("Dom Casmurro", "Machado");
        criaExemplar(livro, "T-001");
        postJson("/api/livros/" + livro + "/exemplares", "{\"tombo\":\"T-001\"}").andExpect(status().isConflict());
        postJson("/api/livros/999/exemplares", "{\"tombo\":\"T-999\"}").andExpect(status().isNotFound());
        postJson("/api/livros/" + livro + "/exemplares", "{\"tombo\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void livroComExemplaresNaoPodeSerRemovido() throws Exception {
        long comExemplar = criaLivro("Com Exemplar", "A");
        criaExemplar(comExemplar, "T-1");
        mvc.perform(delete("/api/livros/" + comExemplar).with(admin())).andExpect(status().isConflict());

        long semExemplar = criaLivro("Sem Exemplar", "A");
        mvc.perform(delete("/api/livros/" + semExemplar).with(admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/livros/" + semExemplar)).andExpect(status().isNotFound());
    }

    @Test
    void rotaInexistenteDa404EMetodoNaoPermitidoDa405NaoDa500() throws Exception {
        mvc.perform(get("/api/nao-existe").with(admin())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/livros/1").with(admin())).andExpect(status().isMethodNotAllowed());
    }
}
