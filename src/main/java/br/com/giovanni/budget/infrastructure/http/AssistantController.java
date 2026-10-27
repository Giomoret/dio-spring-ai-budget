package br.com.giovanni.budget.infrastructure.http;
import br.com.giovanni.budget.application.AssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Locale;
import java.util.Set;
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private static final Set<String> EXTENSIONS = Set.of("mp3", "wav", "m4a", "webm", "mp4", "mpeg", "mpga");
    private final AssistantService assistant;
    public AssistantController(AssistantService assistant) { this.assistant = assistant; }
    @PostMapping("/text")
    public TextReply text(@Valid @RequestBody TextCommand command) { return new TextReply(assistant.text(command.message())); }
    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "audio/mpeg")
    public ResponseEntity<byte[]> audio(@RequestParam("file") MultipartFile file) {
        String name = file.getOriginalFilename();
        String extension = name == null || !name.contains(".") ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (file.isEmpty() || !EXTENSIONS.contains(extension)) throw new IllegalArgumentException("Envie audio nao vazio: mp3, wav, m4a, webm, mp4, mpeg ou mpga.");
        var reply = assistant.audio(file.getResource());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resposta.mp3\"")
            .header("X-Audio-Generated-By", "AI").body(reply.audio());
    }
    public record TextCommand(@NotBlank @Size(max = 4000) String message) {}
    public record TextReply(String reply) {}
}
