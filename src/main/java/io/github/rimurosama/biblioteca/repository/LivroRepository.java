package io.github.rimurosama.biblioteca.repository;

import io.github.rimurosama.biblioteca.domain.Livro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LivroRepository extends JpaRepository<Livro, Long> {
    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    // escape '!' declarado de propósito: sem isso o Hibernate gera "escape ''" e o escape do % e do _ não funciona
    @Query("select l from Livro l where lower(l.titulo) like :padrao escape '!' or lower(l.autor) like :padrao escape '!'")
    Page<Livro> buscar(@Param("padrao") String padrao, Pageable pageable);
}
