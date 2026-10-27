package br.com.giovanni.budget;

import br.com.giovanni.budget.infrastructure.persistence.SpringDataTransactions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Usa os adaptadores reais Spring AI contra um servidor HTTP local simulado, sem chamar OpenAI. */
@ActiveProfiles("ai")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:voicetest;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class VoiceFlowTest {
    private static final AtomicInteger chatCalls = new AtomicInteger();
    private static final AtomicInteger transcriptions = new AtomicInteger();
    private static final AtomicInteger speeches = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicReference<String> failedStage = new java.util.concurrent.atomic.AtomicReference<>("");
    private static final AtomicInteger providerStatus = new AtomicInteger(503);
    private static volatile boolean emptySpeech;
    private static final java.util.concurrent.atomic.AtomicReference<String> lastChatRequest = new java.util.concurrent.atomic.AtomicReference<>();
    private static final byte[] MP3 = new byte[]{'I', 'D', '3', 4, 0, 0};
    private static final HttpServer PROVIDER = startProvider();
    @LocalServerPort int port;
    @Autowired SpringDataTransactions data;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.api-key", () -> "local-test-not-a-real-key");
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
    }
    private static HttpServer startProvider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/audio/transcriptions", exchange -> {
                exchange.getRequestBody().readAllBytes();
                transcriptions.incrementAndGet();
                if (failIfRequested(exchange, "transcription")) return;
                byte[] body = "Registre 25.90 reais de almoco em FOOD, EXPENSE, no dia 2026-10-27".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/v1/chat/completions", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                lastChatRequest.set(request);
                chatCalls.incrementAndGet();
                if (failIfRequested(exchange, "chat")) return;
                // Uma mensagem tool so aparece apos a aplicacao executar a ferramenta.
                boolean afterTool = request.matches("(?s).*\\\"role\\\"\\s*:\\s*\\\"tool\\\".*");
                String message = afterTool
                    ? "{\"role\":\"assistant\",\"content\":\"Despesa de R$ 25,90 registrada.\"}"
                    : """
                    {"role":"assistant","content":null,"tool_calls":[{"id":"call_local_1","type":"function","function":{"name":"createTransaction","arguments":"{\\\"description\\\":\\\"Almoco\\\",\\\"amount\\\":25.90,\\\"type\\\":\\\"EXPENSE\\\",\\\"category\\\":\\\"FOOD\\\",\\\"date\\\":\\\"2026-10-27\\\"}"}}]}
                    """;
                String finishReason = afterTool ? "stop" : "tool_calls";
                String json = "{\"id\":\"local-chat\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"local-model\",\"choices\":[{\"index\":0,\"message\":" + message + ",\"finish_reason\":\"" + finishReason + "\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/v1/audio/speech", exchange -> {
                exchange.getRequestBody().readAllBytes(); speeches.incrementAndGet();
                if (failIfRequested(exchange, "speech")) return;
                if (emptySpeech) { exchange.sendResponseHeaders(200, -1); exchange.close(); return; }
                exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
                exchange.sendResponseHeaders(200, MP3.length);
                exchange.getResponseBody().write(MP3); exchange.close();
            });
            server.start(); return server;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private static boolean failIfRequested(com.sun.net.httpserver.HttpExchange exchange, String stage) throws IOException {
        if (!failedStage.get().equals(stage)) return false;
        byte[] body = "{\"error\":{\"message\":\"INTERNAL_PROVIDER_DETAIL\",\"type\":\"server_error\"}}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(providerStatus.get(), body.length);
        exchange.getResponseBody().write(body); exchange.close(); return true;
    }
    @BeforeEach void clear() { data.deleteAll(); chatCalls.set(0); transcriptions.set(0); speeches.set(0); failedStage.set(""); providerStatus.set(503); emptySpeech = false; }
    @AfterAll static void stop() { PROVIDER.stop(0); }
    private HttpResponse<byte[]> audio(String filename, String contents) throws Exception {
        String body = "--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\nContent-Type: audio/wav\r\n\r\n" + contents + "\r\n--boundary--\r\n";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/assistant/audio"))
            .header("Content-Type", "multipart/form-data; boundary=boundary")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void multipartToTranscriptionToRealToolToDatabaseToSpeech() throws Exception {
        var response = audio("comando.wav", "audio-simulado");
        assertThat(response.statusCode()).withFailMessage(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("audio/mpeg");
        assertThat(response.body()).isEqualTo(MP3);
        assertThat(transcriptions.get()).isEqualTo(1);
        assertThat(chatCalls.get()).isEqualTo(2);
        assertThat(speeches.get()).isEqualTo(1);
        assertThat(lastChatRequest.get()).contains("createdAt", "25.90").doesNotContain("Unexpected character", "not supported by default");
        assertThat(data.findAll()).withFailMessage(lastChatRequest.get()).singleElement().satisfies(entity -> {
            assertThat(entity.toDomain().description()).isEqualTo("Almoco");
            assertThat(entity.toDomain().amount()).isEqualByComparingTo("25.90");
        });
    }
    @Test void invalidExtensionAndEmptyAudioDoNotReachProvider() throws Exception {
        assertThat(audio("comando.exe", "invalido").statusCode()).isEqualTo(400);
        assertThat(audio("comando.wav", "").statusCode()).isEqualTo(400);
        assertThat(transcriptions.get()).isZero();
        assertThat(data.count()).isZero();
    }
    @Test void transcriptionOutageIsBadGatewayWithoutInternalDetailsOrWrites() throws Exception {
        failedStage.set("transcription");
        var response = audio("comando.wav", "audio-simulado");
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("Falha no provedor").doesNotContain("INTERNAL_PROVIDER_DETAIL");
        assertThat(chatCalls.get()).isZero();
        assertThat(data.count()).isZero();
    }
    @Test void invalidProviderCredentialsAreBadGatewayWithoutInternalDetails() throws Exception {
        failedStage.set("chat"); providerStatus.set(401);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/assistant/text"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"Registre meu almoco\"}")).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).doesNotContain("INTERNAL_PROVIDER_DETAIL");
        assertThat(data.count()).isZero();
    }
    @Test void speechFailureAfterWriteWarnsAboutRepeatingCommand() throws Exception {
        failedStage.set("speech");
        var response = audio("comando.wav", "audio-simulado");
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("consulte antes de repetir").doesNotContain("INTERNAL_PROVIDER_DETAIL");
        assertThat(data.count()).isEqualTo(1);
    }
    @Test void emptyProviderAudioIsNeverReturnedAsSuccess() throws Exception {
        emptySpeech = true;
        assertThat(audio("comando.wav", "audio-simulado").statusCode()).isEqualTo(502);
        assertThat(data.count()).isEqualTo(1);
    }
    @Test void oversizedAudioIsRejectedBeforeProviderCall() throws Exception {
        assertThat(audio("comando.wav", "a".repeat(10 * 1024 * 1024 + 1)).statusCode()).isEqualTo(413);
        assertThat(transcriptions.get()).isZero();
        assertThat(data.count()).isZero();
    }
}
