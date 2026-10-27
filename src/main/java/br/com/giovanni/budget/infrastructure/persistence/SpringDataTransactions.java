package br.com.giovanni.budget.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public interface SpringDataTransactions extends JpaRepository<TransactionEntity, UUID> {
    List<TransactionEntity> findByDateBetweenOrderByDateAscCreatedAtAsc(LocalDate start, LocalDate end);
}
