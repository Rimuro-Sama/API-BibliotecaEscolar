package io.github.rimurosama.biblioteca.repository;

import io.github.rimurosama.biblioteca.domain.Leitor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface LeitorRepository extends JpaRepository<Leitor, Long> {
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /**
     * Trava a linha do leitor até o fim da transação. Serializa empréstimos simultâneos do mesmo leitor,
     * para que o limite de empréstimos ativos não seja ultrapassado por duas requisições ao mesmo tempo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Leitor l where l.id = :id")
    Optional<Leitor> findByIdParaAtualizar(@Param("id") Long id);
}
