package com.example.codeassistant.indexing;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Remembers the GitHub blob SHA we last indexed for a given file in a given
 * repository. GitHub already computes a content hash (the blob SHA) for
 * every file in its tree API, so we don't need to hash file contents
 * ourselves - we just compare the SHA we saw last time against the SHA we
 * see now. If they match, the file's content hasn't changed and we can
 * skip re-downloading, re-chunking, and re-embedding it.
 */
@Entity
@Table(name = "indexed_files", uniqueConstraints = @UniqueConstraint(columnNames = {"repository_id", "file_path"}))
public class IndexedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "blob_sha", nullable = false)
    private String blobSha;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    protected IndexedFile() {
    }

    public IndexedFile(Long repositoryId, String filePath, String blobSha) {
        this.repositoryId = repositoryId;
        this.filePath = filePath;
        this.blobSha = blobSha;
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

    public String getBlobSha() {
        return blobSha;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}