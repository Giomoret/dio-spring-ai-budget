package br.com.giovanni.budget.domain;
import java.time.LocalDate;
import java.util.List;
public interface TransactionRepository {
    Transaction save(Transaction transaction);
    List<Transaction> findBetween(LocalDate start, LocalDate end);
}
