package br.com.giovanni.budget.infrastructure.persistence;
import br.com.giovanni.budget.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
@Entity
@Table(name = "budget_transactions")
public class TransactionEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 200) private String description;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TransactionType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Category category;
    @Column(nullable = false) private LocalDate date;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    protected TransactionEntity() {}
    public static TransactionEntity from(Transaction t) {
        var entity = new TransactionEntity();
        entity.id = t.id(); entity.description = t.description(); entity.amount = t.amount();
        entity.type = t.type(); entity.category = t.category(); entity.date = t.date(); entity.createdAt = t.createdAt();
        return entity;
    }
    public Transaction toDomain() { return new Transaction(id, description, amount, type, category, date, createdAt); }
}
