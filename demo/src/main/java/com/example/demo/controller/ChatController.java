package com.example.demo.controller;

import com.example.demo.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class ChatController {


    private final ChatService chatService;


    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }


    @GetMapping("/ask")
    public Mono<String> ask(@RequestParam("q") String q) {
        return chatService.ask(q);
    }
}
