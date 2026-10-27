package br.com.giovanni.budget.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record Transaction(UUID id, String description, BigDecimal amount, TransactionType type,
                          Category category, LocalDate date, Instant createdAt) {
    public Transaction {
        if (id == null || type == null || category == null || date == null || createdAt == null)
            throw new IllegalArgumentException("Tipo, categoria e data sao obrigatorios.");
        if (description == null || description.isBlank() || description.length() > 200)
            throw new IllegalArgumentException("Descricao deve conter de 1 a 200 caracteres.");
        description = description.strip();
        if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new IllegalArgumentException("Valor deve ser positivo e ter no maximo 12 digitos inteiros.");
        if (amount.scale() > 2) throw new IllegalArgumentException("Valor deve ter no maximo duas casas decimais.");
        try { amount = amount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Valor deve ter no maximo duas casas decimais."); }
    }
    public static Transaction create(String description, BigDecimal amount, TransactionType type, Category category, LocalDate date) {
        return new Transaction(UUID.randomUUID(), description, amount, type, category, date, Instant.now());
    }
}
