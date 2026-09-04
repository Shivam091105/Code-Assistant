package com.example.codeassistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Backs @Async indexing jobs. A single, small, in-JVM thread pool is enough
 * for this project - no external queue/broker needed (see README, section
 * "Why no Kafka/RabbitMQ").
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "indexingExecutor")
    public ThreadPoolTaskExecutor indexingExecutor(@Value("${app.indexing.thread-pool-size:4}") int poolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("indexing-");
        executor.initialize();
        return executor;
    }
}
