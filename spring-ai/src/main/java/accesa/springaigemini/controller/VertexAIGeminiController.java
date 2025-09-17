package accesa.springaigemini.controller;

import accesa.springaigemini.service.SimpleRAGService;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/gemini")
@CrossOrigin("*")
@RequiredArgsConstructor
public class VertexAIGeminiController {

    private final SimpleRAGService ragService;

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.location}")
    private String location;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(@RequestPart("file") MultipartFile file) {
        try {
            ragService.indexDocument(file);
            return ResponseEntity.ok("Document uploaded successfully");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/query")
    public ResponseEntity<String> queryWithRAG(@RequestBody String query) {
        try {
            String response = ragService.queryWithRAG(query);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping()
    public ResponseEntity<String> getAnswer() throws IOException {
        Path filePath = Path.of("src\\main\\resources\\HPI.json");
        String json = Files.readString(filePath);

        String req = "You are an expert in Hogan Assessments for workplace. " +
                "Summarize the following values of a candidate on the HPI scale, in one paragraph. " +
                "Infer a list of strengths and one of challenges" + json;

        GenerateContentResponse response = null;

        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
            GenerationConfig config = GenerationConfig.newBuilder()
                    .setMaxOutputTokens(8192)
                    .setTemperature(1F)
                    .setTopP(0.95F)
                    .build();

            GenerativeModel model = new GenerativeModel.Builder()
                    .setModelName("gemini-2.5-flash-lite")
                    .setVertexAi(vertexAI)
                    .setGenerationConfig(config)
                    .build();

            response = model.generateContent(req);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok(response.toString());
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<String> uploadMultipleDocuments(@RequestParam("files") List<MultipartFile> files) {
        try {
            ragService.indexDocuments(files);
            return ResponseEntity.ok(String.format("%d documents uploaded successfully", files.size()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
