package br.com.giovanni.budget.application;

/** Falha externa; detalhes do provedor ficam na causa, nunca na resposta HTTP. */
public class AiProviderException extends RuntimeException {
    public AiProviderException(String message) { super(message); }
    public AiProviderException(String message, Throwable cause) { super(message, cause); }
}
