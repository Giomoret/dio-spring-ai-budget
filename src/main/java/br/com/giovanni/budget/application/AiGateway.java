package br.com.giovanni.budget.application;
import org.springframework.core.io.Resource;
public interface AiGateway {
    String answer(String message);
    String transcribe(Resource audio);
    byte[] speak(String message);
}
