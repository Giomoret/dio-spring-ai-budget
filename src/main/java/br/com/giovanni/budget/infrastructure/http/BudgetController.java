package br.com.giovanni.budget.infrastructure.http;
import br.com.giovanni.budget.application.*;
import br.com.giovanni.budget.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
@RestController
@RequestMapping("/api/transactions")
public class BudgetController {
    private final BudgetService budget;
    public BudgetController(BudgetService budget) { this.budget = budget; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Transaction create(@Valid @RequestBody CreateTransaction request) {
        return budget.create(request.description(), request.amount(), request.type(), request.category(), request.date());
    }
    @GetMapping
    public List<Transaction> list(@RequestParam LocalDate start, @RequestParam LocalDate end,
                                  @RequestParam(required = false) Category category) { return budget.list(start, end, category); }
    @GetMapping("/summary")
    public BudgetService.Summary summary(@RequestParam LocalDate start, @RequestParam LocalDate end) { return budget.summary(start, end); }
    public record CreateTransaction(@NotBlank @Size(max = 200) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull TransactionType type, @NotNull Category category, @NotNull LocalDate date) {}
}
