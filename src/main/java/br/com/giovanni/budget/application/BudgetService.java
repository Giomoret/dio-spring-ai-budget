package br.com.giovanni.budget.application;
import br.com.giovanni.budget.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
@Service
@Transactional(readOnly = true)
public class BudgetService {
    private final TransactionRepository repository;
    public BudgetService(TransactionRepository repository) { this.repository = repository; }
    @Transactional
    public Transaction create(String description, BigDecimal amount, TransactionType type, Category category, LocalDate date) {
        return repository.save(Transaction.create(description, amount, type, category, date));
    }
    public List<Transaction> list(LocalDate start, LocalDate end, Category category) {
        if (start == null || end == null || start.isAfter(end)) throw new IllegalArgumentException("Periodo invalido: inicio deve ser anterior ou igual ao fim.");
        return repository.findBetween(start, end).stream().filter(t -> category == null || t.category() == category).toList();
    }
    public Summary summary(LocalDate start, LocalDate end) {
        var transactions = list(start, end, null);
        BigDecimal income = new BigDecimal("0.00"), expense = new BigDecimal("0.00");
        for (var transaction : transactions) {
            if (transaction.type() == TransactionType.INCOME) income = income.add(transaction.amount());
            else expense = expense.add(transaction.amount());
        }
        return new Summary(start, end, income, expense, income.subtract(expense), transactions.size());
    }
    public record Summary(LocalDate start, LocalDate end, BigDecimal income, BigDecimal expense, BigDecimal balance, int count) {}
}
