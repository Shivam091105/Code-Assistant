package com.example.codeassistant.repository;

import com.example.codeassistant.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "repositories", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "github_repository_id"}))
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "github_repository_id", nullable = false)
    private Long githubRepositoryId;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String owner;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    private String description;

    private String language;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "indexing_status", nullable = false)
    private IndexingStatus indexingStatus = IndexingStatus.NOT_INDEXED;

    @Column(name = "indexing_error")
    private String indexingError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    protected RepositoryEntity() {
    }

    public RepositoryEntity(User user, Long githubRepositoryId, String name, String fullName, String owner,
                             String defaultBranch, String description, String language, String url) {
        this.user = user;
        this.githubRepositoryId = githubRepositoryId;
        this.name = name;
        this.fullName = fullName;
        this.owner = owner;
        this.defaultBranch = defaultBranch;
        this.description = description;
        this.language = language;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Long getGithubRepositoryId() {
        return githubRepositoryId;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    public String getOwner() {
        return owner;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public String getDescription() {
        return description;
    }

    public String getLanguage() {
        return language;
    }

    public String getUrl() {
        return url;
    }

    public IndexingStatus getIndexingStatus() {
        return indexingStatus;
    }

    public void setIndexingStatus(IndexingStatus indexingStatus) {
        this.indexingStatus = indexingStatus;
    }

    public String getIndexingError() {
        return indexingError;
    }

    public void setIndexingError(String indexingError) {
        this.indexingError = indexingError;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
