package com.example.codeassistant.indexing;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Thin @Async trampoline. Kept as a separate bean from IndexingService so
 * that the @Async proxy is actually invoked (see the note in
 * IndexingService.startIndexing about self-invocation).
 */
@Component
public class IndexingWorker {

    @Async("indexingExecutor")
    public void runIndexingJob(Long repositoryId, String accessToken, IndexingService indexingService) {
        indexingService.runIndexingJob(repositoryId, accessToken);
    }
}
