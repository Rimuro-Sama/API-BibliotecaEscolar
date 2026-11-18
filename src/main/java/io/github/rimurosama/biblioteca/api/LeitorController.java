package io.github.rimurosama.biblioteca.api;

import io.github.rimurosama.biblioteca.api.Dtos.EmprestimoResponse;
import io.github.rimurosama.biblioteca.api.Dtos.LeitorRequest;
import io.github.rimurosama.biblioteca.api.Dtos.LeitorResponse;
import io.github.rimurosama.biblioteca.api.Dtos.Pagina;
import io.github.rimurosama.biblioteca.service.EmprestimoService;
import io.github.rimurosama.biblioteca.service.LeitorService;
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

@RestController
@RequestMapping("/api/leitores")
@Validated
@Tag(name = "Leitores", description = "Cadastro de leitores (exige autenticação: contém dados pessoais)")
public class LeitorController {
    private final LeitorService leitores;
    private final EmprestimoService emprestimos;

    public LeitorController(LeitorService leitores, EmprestimoService emprestimos) {
        this.leitores = leitores;
        this.emprestimos = emprestimos;
    }

    @PostMapping
    @Operation(summary = "Cadastra um leitor", description = "409 se o e-mail já estiver cadastrado.")
    public ResponseEntity<LeitorResponse> criar(@Valid @RequestBody LeitorRequest r) {
        LeitorResponse criado = leitores.criar(r);
        return ResponseEntity.created(URI.create("/api/leitores/" + criado.id())).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista os leitores (paginado, ordenado por nome)")
    public Pagina<LeitorResponse> listar(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return leitores.listar(pagina, tamanho);
    }

    @GetMapping("/{id}")
    public LeitorResponse buscar(@PathVariable Long id) {
        return leitores.buscar(id);
    }

    @PutMapping("/{id}")
    public LeitorResponse atualizar(@PathVariable Long id, @Valid @RequestBody LeitorRequest r) {
        return leitores.atualizar(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um leitor", description = "409 se o leitor tiver histórico de empréstimos.")
    public void remover(@PathVariable Long id) {
        leitores.remover(id);
    }

    @GetMapping("/{id}/emprestimos")
    @Operation(summary = "Histórico de empréstimos do leitor (do mais recente ao mais antigo)")
    public Pagina<EmprestimoResponse> emprestimosDoLeitor(@PathVariable Long id,
                                                          @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return emprestimos.listarDoLeitor(id, pagina, tamanho);
    }
}
