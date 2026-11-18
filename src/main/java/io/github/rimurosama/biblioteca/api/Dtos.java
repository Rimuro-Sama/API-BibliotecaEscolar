package io.github.rimurosama.biblioteca.api;

import io.github.rimurosama.biblioteca.domain.StatusEmprestimo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/** Objetos de entrada e saída da API. As entidades do banco nunca são expostas diretamente. */
public final class Dtos {
    private Dtos() {
    }

    public record LeitorRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @Email @Size(max = 160) String email,
            @Size(max = 40) String turma,
            @Size(max = 20) String telefone) {
    }

    public record LeitorResponse(Long id, String nome, String email, String turma, String telefone) {
    }

    public record LivroRequest(
            @NotBlank @Size(max = 200) String titulo,
            @NotBlank @Size(max = 150) String autor,
            @Min(1000) @Max(2100) Integer anoPublicacao,
            @Size(max = 20) @Pattern(regexp = "^[0-9Xx-]*$", message = "o ISBN só pode ter dígitos, X e hífen") String isbn) {
    }

    /** {@code exemplares} = cópias físicas; {@code disponiveis} = as que não estão emprestadas agora. */
    public record LivroResponse(Long id, String titulo, String autor, Integer anoPublicacao, String isbn,
                                long exemplares, long disponiveis) {
    }

    public record ExemplarRequest(@NotBlank @Size(max = 30) String tombo) {
    }

    public record ExemplarResponse(Long id, Long livroId, String tombo, boolean disponivel) {
    }

    public record EmprestimoRequest(@NotNull Long leitorId, @NotNull Long exemplarId) {
    }

    /** Valores em dinheiro sempre em centavos (inteiro). */
    public record EmprestimoResponse(Long id, Long leitorId, String leitorNome, Long exemplarId, String tombo,
                                     Long livroId, String titulo, LocalDate dataEmprestimo, LocalDate dataPrevista,
                                     LocalDate dataDevolucao, int renovacoes, StatusEmprestimo status,
                                     int diasDeAtraso, int multaCentavos, boolean multaPaga) {
    }

    public record LivroMaisEmprestadoResponse(Long livroId, String titulo, String autor, long emprestimos) {
    }

    /** Página de resultados (formato próprio e estável, em vez de serializar o Page do Spring). */
    public record Pagina<T>(List<T> conteudo, int pagina, int tamanho, long total, int totalPaginas) {
        public static <E, T> Pagina<T> de(org.springframework.data.domain.Page<E> page, Function<E, T> mapa) {
            return new Pagina<>(page.getContent().stream().map(mapa).toList(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
