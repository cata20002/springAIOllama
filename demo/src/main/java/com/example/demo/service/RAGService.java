package com.example.demo.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;

@Service
public class RAGService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ChatService chatService;

    @Autowired
    public RAGService(VectorStore vectorStore, EmbeddingModel embeddingModel, ChatService chatService) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.chatService = chatService;
    }

    public Mono<String> uploadDocument(MultipartFile file) {
        return Mono.fromCallable(() -> {
            try {
                List<Document> documents = readDocuments(file);
                TokenTextSplitter splitter = new TokenTextSplitter(128, 100, 10, 5000, false);
                List<Document> splitDocuments = splitter.split(documents);
                vectorStore.add(splitDocuments);
                return "Document uploaded successfully. Processed " + splitDocuments.size() + " chunks.";
            } catch (IOException e) {
                throw new RuntimeException("Failed to process document", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> queryWithContext(String query) {
        return Mono.fromCallable(() -> {
                    List<Document> similarDocuments = vectorStore.similaritySearch(
                            SearchRequest.query(query)
                                    .withTopK(5)
                    );

                    StringBuilder context = new StringBuilder();
                    for (Document doc : similarDocuments) {
                        context.append(doc.getContent()).append("\n\n");
                    }

                    return String.format(
                            "Use the following context to answer the question. If you cannot answer based on the context, say so.\n\n" +
                                    "Context:\n%s\n\n" +
                                    "Question: %s",
                            context.toString(),
                            query
                    );
                })
                .flatMap(chatService::ask)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<Document> readDocuments(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        ByteArrayResource resource = new ByteArrayResource(file.getBytes());

        return new TikaDocumentReader(resource).read();
    }
}
