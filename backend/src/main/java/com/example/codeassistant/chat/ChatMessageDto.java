package com.example.codeassistant.chat;

import java.time.Instant;
import java.util.List;

public record ChatMessageDto(Long id, String role, String content, List<String> sources, Instant createdAt) {
    public static ChatMessageDto from(ChatMessage message) {
        List<String> sources = (message.getSources() == null || message.getSources().isBlank())
                ? List.of()
                : List.of(message.getSources().split("\n"));
        return new ChatMessageDto(message.getId(), message.getRole().name(), message.getContent(), sources, message.getCreatedAt());
    }
}
