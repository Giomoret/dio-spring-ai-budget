package br.com.giovanni.budget.infrastructure.ai;
import br.com.giovanni.budget.application.BudgetService;
import br.com.giovanni.budget.domain.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
@Component
public class BudgetTools {
    private final BudgetService budget;
    public BudgetTools(BudgetService budget) { this.budget = budget; }
    @Tool(description = "Registra receita ou despesa real no orcamento, apenas se solicitado explicitamente. Valor em REAIS, nunca centavos.")
    public Transaction createTransaction(String description,
        @ToolParam(description = "Valor positivo em reais com no maximo duas casas decimais, ex: 25.90") BigDecimal amount,
        TransactionType type, Category category, @ToolParam(description = "Data ISO yyyy-MM-dd") String date) {
        return budget.create(description, amount, type, category, parseDate(date));
    }
    @Tool(description = "Consulta transacoes no periodo inclusivo; datas ISO yyyy-MM-dd.")
    public List<Transaction> listTransactions(String start, String end,
        @ToolParam(required = false, description = "Categoria opcional; nulo lista todas") Category category) {
        return budget.list(parseDate(start), parseDate(end), category);
    }
    @Tool(description = "Calcula receitas, despesas e saldo real do periodo inclusivo; datas ISO yyyy-MM-dd.")
    public BudgetService.Summary summarizeBudget(String start, String end) { return budget.summary(parseDate(start), parseDate(end)); }
    private LocalDate parseDate(String value) {
        if (value == null) throw new IllegalArgumentException("Data obrigatoria no formato yyyy-MM-dd.");
        try { return LocalDate.parse(value); }
        catch (java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("Data invalida: use yyyy-MM-dd."); }
    }
}
