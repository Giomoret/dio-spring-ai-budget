package br.com.giovanni.budget;
import br.com.giovanni.budget.application.BudgetService;
import br.com.giovanni.budget.infrastructure.ai.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.core.io.ByteArrayResource;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class SpringAiGatewayTest {
    @Test void adapterUsesSpringAiChatAndAudioModels() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("Saldo consultado.")))));
        var transcription = mock(TranscriptionModel.class);
        var speech = mock(TextToSpeechModel.class);
        var resource = new ByteArrayResource(new byte[]{1});
        when(transcription.transcribe(resource)).thenReturn("Qual meu saldo?");
        when(speech.call("Saldo consultado.")).thenReturn(new byte[]{2, 3});
        var gateway = new SpringAiGateway(ChatClient.builder(model), new BudgetTools(mock(BudgetService.class)), transcription, speech);
        assertThat(gateway.answer(gateway.transcribe(resource))).isEqualTo("Saldo consultado.");
        assertThat(gateway.speak("Saldo consultado.")).containsExactly(2, 3);
        verify(model).call(argThat((Prompt prompt) -> prompt.getContents().contains("Qual meu saldo?") && prompt.getContents().contains("REAIS")));
    }
}
