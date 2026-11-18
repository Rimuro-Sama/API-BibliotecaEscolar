package io.github.rimurosama.biblioteca.repository;

import io.github.rimurosama.biblioteca.domain.Emprestimo;
import io.github.rimurosama.biblioteca.domain.Exemplar;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExemplarRepository extends JpaRepository<Exemplar, Long> {
    boolean existsByTombo(String tombo);

    boolean existsByLivroId(Long livroId);

    @EntityGraph(attributePaths = "livro")
    List<Exemplar> findByLivroIdOrderByTomboAsc(Long livroId);

    @EntityGraph(attributePaths = "livro")
    Optional<Exemplar> findWithLivroById(Long id);

    /** Quantidade de exemplares por livro, para os livros informados. Linhas: [livroId, total]. */
    @Query("select e.livro.id, count(e) from Exemplar e where e.livro.id in :ids group by e.livro.id")
    List<Object[]> contarPorLivro(@Param("ids") Collection<Long> ids);

    /** Exemplares sem empréstimo ativo, por livro. Linhas: [livroId, disponiveis]. */
    @Query("""
            select e.livro.id, count(e) from Exemplar e
            where e.livro.id in :ids
              and not exists (select 1 from Emprestimo m where m.exemplar = e and m.dataDevolucao is null)
            group by e.livro.id""")
    List<Object[]> contarDisponiveisPorLivro(@Param("ids") Collection<Long> ids);

    @Query("""
            select count(m) = 0 from Emprestimo m where m.exemplar.id = :exemplarId and m.dataDevolucao is null""")
    boolean estaDisponivel(@Param("exemplarId") Long exemplarId);
}
