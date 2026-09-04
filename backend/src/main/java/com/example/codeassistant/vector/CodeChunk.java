package com.example.codeassistant.vector;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "code_chunks")
public class CodeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "start_line", nullable = false)
    private int startLine;

    @Column(name = "end_line", nullable = false)
    private int endLine;

    /**
     * Stored as pgvector's `vector` type. Hibernate maps it as a plain
     * String here (pgvector's text I/O format, e.g. "[0.12,0.98,...]") via
     * @JdbcTypeCode(SqlTypes.OTHER) so we don't need a custom Hibernate
     * UserType - see PgVectorConverter.
     */
    @Column(nullable = false, columnDefinition = "vector")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.OTHER)
    private String embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    protected CodeChunk() {
    }

    public CodeChunk(Long repositoryId, String filePath, String content, int startLine, int endLine, String embedding) {
        this.repositoryId = repositoryId;
        this.filePath = filePath;
        this.content = content;
        this.startLine = startLine;
        this.endLine = endLine;
        this.embedding = embedding;
    }

    public Long getId() {
        return id;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getContent() {
        return content;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }
}
