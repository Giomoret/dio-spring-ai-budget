package br.com.giovanni.budget.application;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
@Service
public class AssistantService {
    private final AiGateway ai;
    public AssistantService(AiGateway ai) { this.ai = ai; }
    public String text(String message) {
        if (message == null || message.isBlank() || message.length() > 4000)
            throw new IllegalArgumentException("Comando deve conter de 1 a 4000 caracteres.");
        String reply = ai.answer(message);
        if (reply == null || reply.isBlank()) throw new AiProviderException("Resposta de texto vazia.");
        return reply;
    }
    public AudioReply audio(Resource resource) {
        String transcript = ai.transcribe(resource);
        String reply = text(transcript);
        byte[] audio = ai.speak(reply);
        if (audio == null || audio.length == 0) throw new AiProviderException("Resposta de audio vazia.");
        return new AudioReply(transcript, reply, audio);
    }
    public record AudioReply(String transcript, String reply, byte[] audio) {}
}
