package br.com.giovanni.budget.infrastructure.http;
import br.com.giovanni.budget.application.AiProviderException;
import br.com.giovanni.budget.application.AiUnavailableException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ProblemDetail invalid(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            e instanceof IllegalArgumentException ? e.getMessage() : "JSON, data, categoria ou tipo invalido.");
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revise os campos enviados.");
        problem.setProperty("errors", e.getBindingResult().getFieldErrors().stream().map(error -> error.getField() + ": " + error.getDefaultMessage()).toList());
        return problem;
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail largeFile(Exception e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Audio excede o limite de 10 MB."); }
    @ExceptionHandler(AiUnavailableException.class)
    public ProblemDetail unavailable(AiUnavailableException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()); }
    @ExceptionHandler(AiProviderException.class)
    public ProblemDetail provider(Exception e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Falha no provedor de IA. Uma transacao pode ja ter sido registrada; consulte antes de repetir."); }
}
