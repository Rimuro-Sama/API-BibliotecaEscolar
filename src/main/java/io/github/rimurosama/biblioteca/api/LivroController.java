package io.github.rimurosama.biblioteca.api;

import io.github.rimurosama.biblioteca.api.Dtos.ExemplarRequest;
import io.github.rimurosama.biblioteca.api.Dtos.ExemplarResponse;
import io.github.rimurosama.biblioteca.api.Dtos.LivroRequest;
import io.github.rimurosama.biblioteca.api.Dtos.LivroResponse;
import io.github.rimurosama.biblioteca.api.Dtos.Pagina;
import io.github.rimurosama.biblioteca.service.LivroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/livros")
@Validated
@Tag(name = "Acervo", description = "Livros e exemplares. A consulta (GET) é pública; alterações exigem autenticação.")
public class LivroController {
    private final LivroService livros;

    public LivroController(LivroService livros) {
        this.livros = livros;
    }

    @GetMapping
    @Operation(summary = "Catálogo (público)", description = "Busca opcional por título ou autor. Mostra quantos exemplares existem e quantos estão disponíveis.")
    public Pagina<LivroResponse> listar(@RequestParam(required = false) String busca,
                                        @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return livros.listar(busca, pagina, tamanho);
    }

    @GetMapping("/{id}")
    public LivroResponse buscar(@PathVariable Long id) {
        return livros.buscar(id);
    }

    @PostMapping
    @Operation(summary = "Cadastra um livro", description = "409 se o ISBN já estiver cadastrado.")
    public ResponseEntity<LivroResponse> criar(@Valid @RequestBody LivroRequest r) {
        LivroResponse criado = livros.criar(r);
        return ResponseEntity.created(URI.create("/api/livros/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public LivroResponse atualizar(@PathVariable Long id, @Valid @RequestBody LivroRequest r) {
        return livros.atualizar(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um livro", description = "409 se o livro tiver exemplares cadastrados.")
    public void remover(@PathVariable Long id) {
        livros.remover(id);
    }

    @GetMapping("/{id}/exemplares")
    @Operation(summary = "Exemplares do livro e se cada um está disponível (público)")
    public List<ExemplarResponse> exemplares(@PathVariable Long id) {
        return livros.listarExemplares(id);
    }

    @PostMapping("/{id}/exemplares")
    @Operation(summary = "Cadastra um exemplar (cópia física) do livro", description = "409 se o tombo já existir.")
    public ResponseEntity<ExemplarResponse> adicionarExemplar(@PathVariable Long id, @Valid @RequestBody ExemplarRequest r) {
        ExemplarResponse criado = livros.adicionarExemplar(id, r);
        return ResponseEntity.created(URI.create("/api/livros/" + id + "/exemplares")).body(criado);
    }
}
