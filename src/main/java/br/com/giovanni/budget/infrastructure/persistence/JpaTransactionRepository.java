package br.com.giovanni.budget.infrastructure.persistence;
import br.com.giovanni.budget.domain.*;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
@Repository
public class JpaTransactionRepository implements TransactionRepository {
    private final SpringDataTransactions data;
    public JpaTransactionRepository(SpringDataTransactions data) { this.data = data; }
    public Transaction save(Transaction t) { return data.save(TransactionEntity.from(t)).toDomain(); }
    public List<Transaction> findBetween(LocalDate start, LocalDate end) {
        return data.findByDateBetweenOrderByDateAscCreatedAtAsc(start, end).stream().map(TransactionEntity::toDomain).toList();
    }
}
