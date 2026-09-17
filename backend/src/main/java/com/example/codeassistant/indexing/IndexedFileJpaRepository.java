package com.example.codeassistant.indexing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndexedFileJpaRepository extends JpaRepository<IndexedFile, Long> {

    List<IndexedFile> findByRepositoryId(Long repositoryId);
}