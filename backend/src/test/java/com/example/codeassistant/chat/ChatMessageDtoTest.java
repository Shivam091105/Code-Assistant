package com.example.codeassistant.chat;

import com.example.codeassistant.repository.RepositoryEntity;
import com.example.codeassistant.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageDtoTest {

    @Test
    void splitsNewlineJoinedSourcesIntoAList() {
        User user = new User(1L, "alice", "alice@example.com", null);
        RepositoryEntity repo = new RepositoryEntity(user, 1L, "repo", "alice/repo", "alice", "main", "d", "Java", "url");
        ChatMessage message = new ChatMessage(user, repo, ChatRole.ASSISTANT, "answer",
                "SecurityConfig.java:20-65\nAuthService.java:30-72");

        ChatMessageDto dto = ChatMessageDto.from(message);

        assertThat(dto.sources()).containsExactly("SecurityConfig.java:20-65", "AuthService.java:30-72");
    }

    @Test
    void returnsEmptyListWhenNoSources() {
        User user = new User(1L, "alice", "alice@example.com", null);
        RepositoryEntity repo = new RepositoryEntity(user, 1L, "repo", "alice/repo", "alice", "main", "d", "Java", "url");
        ChatMessage message = new ChatMessage(user, repo, ChatRole.USER, "question", null);

        ChatMessageDto dto = ChatMessageDto.from(message);

        assertThat(dto.sources()).isEmpty();
    }
}
