package br.com.giovanni.budget.application;

public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException() {
        super("IA desativada. Configure OPENAI_API_KEY e ative o perfil ai.");
    }
}
