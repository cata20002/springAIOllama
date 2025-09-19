package accesa.springaigemini.service;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SimpleRAGService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentSplitter documentSplitter;
    private final DocumentParser pdfParser;
    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.location}")
    private String location;

    public SimpleRAGService() {
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        this.documentSplitter = DocumentSplitters.recursive(1000, 200);
        this.pdfParser = new ApachePdfBoxDocumentParser();
    }

    public void indexDocument(MultipartFile file) throws IOException {
        Document document;

        if (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            try (InputStream inputStream = file.getInputStream()) {
                document = pdfParser.parse(inputStream);
            }
        } else {
            // For other file types, read as text
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            document = Document.from(content);
        }

        List<TextSegment> segments = documentSplitter.split(document);

        for (TextSegment segment : segments) {
            segment.metadata().put("filename", file.getOriginalFilename());
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
        }
    }

    public List<String> findRelevantDocuments(String query) {
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(query).content())
                        .maxResults(5)
                        .build())
                .matches();

        return matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.toList());
    }

    public String queryWithRAG(String query) throws Exception {
        // Find relevant documents
        List<String> relevantDocs = findRelevantDocuments(query);
        String context = String.join("\n\n", relevantDocs);

        String augmentedPrompt = String.format(
                "Based on the following context, answer the question.\n\nContext:\n%s\n\nQuestion: %s",
                context,
                query
        );

        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
            GenerationConfig config = GenerationConfig.newBuilder()
                    .setMaxOutputTokens(8192)
                    .setTemperature(0.7F)
                    .setTopP(0.95F)
                    .build();

            GenerativeModel model = new GenerativeModel.Builder()
                    .setModelName("gemini-2.5-flash-lite")
                    .setVertexAi(vertexAI)
                    .setGenerationConfig(config)
                    .build();

            GenerateContentResponse response = model.generateContent(augmentedPrompt);
            return response.getCandidates(0).getContent().getParts(0).getText();
        }
    }

    public void indexDocuments(List<MultipartFile> files) throws IOException {
        for (MultipartFile file : files) {
            indexDocument(file);
        }
    }
}
