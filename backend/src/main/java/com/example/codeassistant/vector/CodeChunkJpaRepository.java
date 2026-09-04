package com.example.codeassistant.vector;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodeChunkJpaRepository extends JpaRepository<CodeChunk, Long> {

    @Modifying
    @Query(value = "DELETE FROM code_chunks WHERE repository_id = :repositoryId", nativeQuery = true)
    void deleteAllByRepositoryId(@Param("repositoryId") Long repositoryId);

    long countByRepositoryId(Long repositoryId);
}
