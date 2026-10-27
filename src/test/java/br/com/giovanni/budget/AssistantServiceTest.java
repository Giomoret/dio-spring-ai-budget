package br.com.giovanni.budget;
import br.com.giovanni.budget.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AssistantServiceTest {
    @Test void audioRunsTranscriptionThenChatThenSpeech() {
        var ai = mock(AiGateway.class);
        var resource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(ai.transcribe(resource)).thenReturn("Registre 25 reais de almoco");
        when(ai.answer("Registre 25 reais de almoco")).thenReturn("Despesa registrada.");
        when(ai.speak("Despesa registrada.")).thenReturn(new byte[]{4, 5});
        var reply = new AssistantService(ai).audio(resource);
        assertThat(reply.audio()).containsExactly(4, 5);
        assertThat(reply.transcript()).isEqualTo("Registre 25 reais de almoco");
        var order = inOrder(ai);
        order.verify(ai).transcribe(resource);
        order.verify(ai).answer(reply.transcript());
        order.verify(ai).speak(reply.reply());
    }
    @Test void emptyTranscriptionDoesNotExecuteToolsOrSpeech() {
        var ai = mock(AiGateway.class);
        var resource = new ByteArrayResource(new byte[]{1});
        when(ai.transcribe(resource)).thenReturn(" ");
        assertThatThrownBy(() -> new AssistantService(ai).audio(resource)).isInstanceOf(IllegalArgumentException.class);
        verify(ai, never()).answer(anyString());
        verify(ai, never()).speak(anyString());
    }
    @Test void oversizedCommandIsRejected() {
        var ai = mock(AiGateway.class);
        assertThatThrownBy(() -> new AssistantService(ai).text("x".repeat(4001))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ai);
    }
    @Test void emptyTextResponseIsProviderFailure() {
        var ai = mock(AiGateway.class);
        var assistant = new AssistantService(ai);
        when(ai.answer("saldo")).thenReturn(" ");
        assertThatThrownBy(() -> assistant.text("saldo")).isInstanceOf(AiProviderException.class);
        when(ai.answer("saldo")).thenReturn(null);
        assertThatThrownBy(() -> assistant.text("saldo")).isInstanceOf(AiProviderException.class);
    }
    @Test void emptySpeechResponseIsProviderFailure() {
        var ai = mock(AiGateway.class);
        var resource = new ByteArrayResource(new byte[]{1});
        when(ai.transcribe(resource)).thenReturn("saldo");
        when(ai.answer("saldo")).thenReturn("Saldo zero.");
        var assistant = new AssistantService(ai);
        when(ai.speak("Saldo zero.")).thenReturn(new byte[0]);
        assertThatThrownBy(() -> assistant.audio(resource)).isInstanceOf(AiProviderException.class);
        when(ai.speak("Saldo zero.")).thenReturn(null);
        assertThatThrownBy(() -> assistant.audio(resource)).isInstanceOf(AiProviderException.class);
    }
}
