package io.github.rimurosama.biblioteca.repository;

import io.github.rimurosama.biblioteca.domain.Emprestimo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmprestimoRepository extends JpaRepository<Emprestimo, Long> {
    boolean existsByExemplarIdAndDataDevolucaoIsNull(Long exemplarId);

    long countByLeitorIdAndDataDevolucaoIsNull(Long leitorId);

    boolean existsByLeitorId(Long leitorId);

    boolean existsByLeitorIdAndDataDevolucaoIsNullAndDataPrevistaBefore(Long leitorId, LocalDate hoje);

    @Query("select count(m) > 0 from Emprestimo m where m.leitor.id = :leitorId and m.multaCentavos > 0 and m.multaPaga = false")
    boolean existeMultaPendente(@Param("leitorId") Long leitorId);

    @EntityGraph(attributePaths = {"leitor", "exemplar", "exemplar.livro"})
    Optional<Emprestimo> findWithDetalhesById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Emprestimo m where m.id = :id")
    Optional<Emprestimo> findByIdParaAtualizar(@Param("id") Long id);

    @EntityGraph(attributePaths = {"leitor", "exemplar", "exemplar.livro"})
    Page<Emprestimo> findAllByOrderByIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"leitor", "exemplar", "exemplar.livro"})
    Page<Emprestimo> findByDataDevolucaoIsNullOrderByDataPrevistaAsc(Pageable pageable);

    @EntityGraph(attributePaths = {"leitor", "exemplar", "exemplar.livro"})
    Page<Emprestimo> findByDataDevolucaoIsNullAndDataPrevistaBeforeOrderByDataPrevistaAsc(LocalDate hoje, Pageable pageable);

    @EntityGraph(attributePaths = {"leitor", "exemplar", "exemplar.livro"})
    Page<Emprestimo> findByLeitorIdOrderByIdDesc(Long leitorId, Pageable pageable);

    @EntityGraph(attributePaths = {"leitor", "exemplar", "exemplar.livro"})
    Page<Emprestimo> findByMultaCentavosGreaterThanAndMultaPagaFalseOrderByIdAsc(int minimo, Pageable pageable);

    @Query("""
            select new io.github.rimurosama.biblioteca.repository.LivroMaisEmprestado(
                l.id, l.titulo, l.autor, count(m))
            from Emprestimo m join m.exemplar e join e.livro l
            group by l.id, l.titulo, l.autor
            order by count(m) desc, l.titulo asc""")
    List<LivroMaisEmprestado> maisEmprestados(Pageable limite);
}
