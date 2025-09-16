package com.example.demo.controller;

import com.example.demo.service.RAGService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final RAGService ragService;

    public DocumentController(RAGService ragService) {
        this.ragService = ragService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<String> uploadDocument(@RequestPart("file") MultipartFile file) {
        return ragService.uploadDocument(file);
    }

    @GetMapping("/query")
    public Mono<String> queryDocuments(@RequestParam("q") String query) {
        return ragService.queryWithContext(query);
    }
}
