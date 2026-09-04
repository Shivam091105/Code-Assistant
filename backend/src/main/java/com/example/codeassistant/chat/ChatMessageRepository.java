package com.example.codeassistant.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRepositoryIdOrderByCreatedAtAsc(Long repositoryId);

    List<ChatMessage> findTop6ByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);
}
