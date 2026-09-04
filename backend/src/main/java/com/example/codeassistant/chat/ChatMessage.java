package com.example.codeassistant.chat;

import com.example.codeassistant.repository.RepositoryEntity;
import com.example.codeassistant.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryEntity repository;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Comma/newline-joined "path:startLine-endLine" strings; empty for user messages. */
    @Column(name = "sources")
    private String sources;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    protected ChatMessage() {
    }

    public ChatMessage(User user, RepositoryEntity repository, ChatRole role, String content, String sources) {
        this.user = user;
        this.repository = repository;
        this.role = role;
        this.content = content;
        this.sources = sources;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public RepositoryEntity getRepository() {
        return repository;
    }

    public ChatRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSources() {
        return sources;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
