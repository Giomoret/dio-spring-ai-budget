package br.com.giovanni.budget.infrastructure.ai;
import br.com.giovanni.budget.application.AiGateway;
import br.com.giovanni.budget.application.AiUnavailableException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
@Component
@Profile("!ai")
public class DisabledAiGateway implements AiGateway {
    private AiUnavailableException disabled() { return new AiUnavailableException(); }
    public String answer(String message) { throw disabled(); }
    public String transcribe(Resource audio) { throw disabled(); }
    public byte[] speak(String message) { throw disabled(); }
}
