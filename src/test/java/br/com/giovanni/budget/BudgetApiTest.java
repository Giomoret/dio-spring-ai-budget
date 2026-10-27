package br.com.giovanni.budget;
import br.com.giovanni.budget.application.BudgetService;
import br.com.giovanni.budget.domain.*;
import br.com.giovanni.budget.infrastructure.ai.BudgetTools;
import br.com.giovanni.budget.infrastructure.persistence.SpringDataTransactions;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:budgettest;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BudgetApiTest {
    @LocalServerPort int port;
    @Autowired SpringDataTransactions data;
    @Autowired BudgetTools tools;
    @Autowired BudgetService budget;
    private final HttpClient http = HttpClient.newHttpClient();
    private final LocalDate date = LocalDate.of(2026, 10, 27);
    @BeforeEach void clear() { data.deleteAll(); }
    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String transaction(String amount) {
        return "{\"description\":\"Mercado\",\"amount\":" + amount + ",\"type\":\"EXPENSE\",\"category\":\"FOOD\",\"date\":\"2026-10-27\"}";
    }
    @Test void createAndQueryThroughHttpAndDatabase() throws Exception {
        var created = request("POST", "/api/transactions", transaction("25.90"));
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"amount\":25.90", "\"createdAt\":");
        assertThat(data.count()).isEqualTo(1);
        var listed = request("GET", "/api/transactions?start=2026-10-01&end=2026-10-31&category=FOOD", null);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).contains("Mercado");
    }
    @Test void rejectInvalidAmountsWithoutPersisting() throws Exception {
        for (String amount : new String[]{"0", "-1", "1.001", "1.000", "1000000000000", "null"}) {
            assertThat(request("POST", "/api/transactions", transaction(amount)).statusCode()).isEqualTo(400);
        }
        assertThat(data.count()).isZero();
    }
    @Test void rejectMissingDescriptionAndMalformedJson() throws Exception {
        assertThat(request("POST", "/api/transactions", transaction("10").replace("Mercado", " ")).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/transactions", "{").statusCode()).isEqualTo(400);
        assertThat(data.count()).isZero();
    }
    @Test void inclusivePeriodAndCategoryFilter() {
        tools.createTransaction("Mercado", new BigDecimal("10"), TransactionType.EXPENSE, Category.FOOD, date.toString());
        tools.createTransaction("Onibus", new BigDecimal("5"), TransactionType.EXPENSE, Category.TRANSPORT, date.plusDays(1).toString());
        assertThat(tools.listTransactions(date.toString(), date.toString(), null)).hasSize(1);
        assertThat(tools.listTransactions(date.toString(), date.plusDays(1).toString(), Category.TRANSPORT)).hasSize(1);
        assertThat(tools.listTransactions(date.minusDays(2).toString(), date.minusDays(1).toString(), null)).isEmpty();
    }
    @Test void toolsRespectDomainValidation() {
        assertThatThrownBy(() -> tools.createTransaction("Gasto", new BigDecimal("-10"), TransactionType.EXPENSE, Category.OTHER, date.toString()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(data.count()).isZero();
    }
    @Test void calculatesSummaryUsingExactDecimals() throws Exception {
        tools.createTransaction("Salario", new BigDecimal("100.00"), TransactionType.INCOME, Category.SALARY, date.toString());
        tools.createTransaction("Compra A", new BigDecimal("0.10"), TransactionType.EXPENSE, Category.FOOD, date.toString());
        tools.createTransaction("Compra B", new BigDecimal("0.20"), TransactionType.EXPENSE, Category.FOOD, date.toString());
        var summary = tools.summarizeBudget(date.toString(), date.toString());
        assertThat(summary.income()).isEqualByComparingTo("100.00");
        assertThat(summary.expense()).isEqualByComparingTo("0.30");
        assertThat(summary.balance()).isEqualByComparingTo("99.70");
        assertThat(summary.count()).isEqualTo(3);
        assertThat(request("GET", "/api/transactions/summary?start=2026-10-27&end=2026-10-27", null).body()).contains("99.70");
    }
    @Test void emptySummaryIsZero() {
        assertThat(budget.summary(date, date).balance()).isEqualByComparingTo("0");
    }
    @Test void rejectsReversedPeriodAndInvalidEnum() throws Exception {
        assertThat(request("GET", "/api/transactions?start=2026-10-31&end=2026-10-01", null).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/transactions?start=2026-10-01&end=2026-10-31&category=INVALID", null).statusCode()).isEqualTo(400);
    }
    @Test void disabledAiReturnsServiceUnavailable() throws Exception {
        var reply = request("POST", "/api/assistant/text", "{\"message\":\"Qual meu saldo?\"}");
        assertThat(reply.statusCode()).isEqualTo(503);
        assertThat(reply.body()).contains("IA desativada");
    }
    @Test void emptyTextIsBadRequest() throws Exception {
        assertThat(request("POST", "/api/assistant/text", "{\"message\":\" \"}").statusCode()).isEqualTo(400);
    }
    @Test void toolsAndRestRejectSamePrecisionAndDescriptionLength() throws Exception {
        assertThatThrownBy(() -> tools.createTransaction("Compra", new BigDecimal("1.000"), TransactionType.EXPENSE, Category.FOOD, date.toString()))
            .isInstanceOf(IllegalArgumentException.class);
        String padded = " ".repeat(200) + "a";
        assertThatThrownBy(() -> tools.createTransaction(padded, BigDecimal.ONE, TransactionType.EXPENSE, Category.FOOD, date.toString()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(request("POST", "/api/transactions", transaction("1").replace("Mercado", padded)).statusCode()).isEqualTo(400);
        assertThat(data.count()).isZero();
    }
    @Test void rejectsInvalidDatesMissingFieldsAndNullJson() throws Exception {
        assertThat(request("POST", "/api/transactions", transaction("1").replace("2026-10-27", "2026-02-30")).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/transactions", "{}").statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/transactions", "null").statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/transactions", null).statusCode()).isEqualTo(400);
        assertThatThrownBy(() -> tools.summarizeBudget("2026-02-30", "2026-03-01")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.summarizeBudget(null, "2026-03-01")).isInstanceOf(IllegalArgumentException.class);
        assertThat(data.count()).isZero();
    }
    @Test void numericEnumsCannotSilentlySelectIncomeOrCategory() throws Exception {
        assertThat(request("POST", "/api/transactions", transaction("25.90").replace("\"EXPENSE\"", "0")).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/transactions", transaction("25.90").replace("\"FOOD\"", "0")).statusCode()).isEqualTo(400);
        assertThat(data.count()).isZero();
    }
}
