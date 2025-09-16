package com.example.demo.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ChatService {

    private final ChatModel chatModel;

    @Autowired
    public ChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * @param userMessage
     * @return Ollama call is blocking, so use mono and then boundedElastic scheduler to offload it
     */
    public Mono<String> ask(String userMessage) {
        return Mono.fromCallable(() -> {
                    Prompt prompt = new Prompt(userMessage);
                    ChatResponse response = chatModel.call(prompt);
                    return response.getResult().getOutput().getContent();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> Mono.just("Error: " + ex.getMessage()));
    }
}