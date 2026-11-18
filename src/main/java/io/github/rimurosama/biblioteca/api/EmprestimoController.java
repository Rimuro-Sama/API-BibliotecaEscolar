package io.github.rimurosama.biblioteca.api;

import io.github.rimurosama.biblioteca.api.Dtos.EmprestimoRequest;
import io.github.rimurosama.biblioteca.api.Dtos.EmprestimoResponse;
import io.github.rimurosama.biblioteca.api.Dtos.LivroMaisEmprestadoResponse;
import io.github.rimurosama.biblioteca.api.Dtos.Pagina;
import io.github.rimurosama.biblioteca.service.EmprestimoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "Empréstimos", description = "Empréstimos, renovações, devoluções, multas e relatórios (exigem autenticação)")
public class EmprestimoController {
    private final EmprestimoService emprestimos;

    public EmprestimoController(EmprestimoService emprestimos) {
        this.emprestimos = emprestimos;
    }

    @PostMapping("/emprestimos")
    @Operation(summary = "Registra um empréstimo",
            description = "Prazo de 14 dias. 409 se o exemplar já estiver emprestado, se o leitor tiver 3 empréstimos ativos, "
                    + "um empréstimo atrasado ou uma multa pendente.")
    public ResponseEntity<EmprestimoResponse> emprestar(@Valid @RequestBody EmprestimoRequest r) {
        EmprestimoResponse criado = emprestimos.emprestar(r.leitorId(), r.exemplarId());
        return ResponseEntity.created(URI.create("/api/emprestimos/" + criado.id())).body(criado);
    }

    @GetMapping("/emprestimos")
    @Operation(summary = "Lista empréstimos", description = "status = todos (padrão) | ativos | atrasados")
    public Pagina<EmprestimoResponse> listar(
            @RequestParam(defaultValue = "todos") @Pattern(regexp = "todos|ativos|atrasados", message = "use todos, ativos ou atrasados") String status,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return emprestimos.listar(status, pagina, tamanho);
    }

    @GetMapping("/emprestimos/{id}")
    public EmprestimoResponse buscar(@PathVariable Long id) {
        return emprestimos.buscar(id);
    }

    @PostMapping("/emprestimos/{id}/renovacao")
    @Operation(summary = "Renova o empréstimo", description = "Uma renovação de +7 dias, só se não estiver atrasado. 409 caso contrário.")
    public EmprestimoResponse renovar(@PathVariable Long id) {
        return emprestimos.renovar(id);
    }

    @PostMapping("/emprestimos/{id}/devolucao")
    @Operation(summary = "Registra a devolução", description = "Calcula a multa (R$ 0,50 por dia de atraso, em centavos). Com multa, o leitor fica bloqueado até pagá-la.")
    public EmprestimoResponse devolver(@PathVariable Long id) {
        return emprestimos.devolver(id);
    }

    @GetMapping("/multas")
    @Operation(summary = "Multas ainda não pagas")
    public Pagina<EmprestimoResponse> multasPendentes(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return emprestimos.listarMultasPendentes(pagina, tamanho);
    }

    @PostMapping("/emprestimos/{id}/pagamento-multa")
    @Operation(summary = "Registra o pagamento da multa de um empréstimo", description = "Libera o leitor para novos empréstimos.")
    public EmprestimoResponse pagarMulta(@PathVariable Long id) {
        return emprestimos.pagarMulta(id);
    }

    @GetMapping("/relatorios/mais-emprestados")
    @Operation(summary = "Livros mais emprestados", description = "Ordenado por número de empréstimos (desempate por título).")
    public List<LivroMaisEmprestadoResponse> maisEmprestados(@RequestParam(defaultValue = "10") @Min(1) @Max(100) int limite) {
        return emprestimos.maisEmprestados(limite);
    }
}
