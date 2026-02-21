package com.ftgo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Pattern 6: @Asynchronous (MicroProfile) → @Async (Spring)
 *
 * Enables asynchronous method execution for fire-and-forget operations.
 *
 * Why a dedicated executor?
 * - Default Spring @Async uses SimpleAsyncTaskExecutor (creates unbounded threads!)
 * - A bounded pool prevents thread exhaustion under load
 * - Named threads ("event-publisher-1", "event-publisher-2") make debugging easy
 *
 * Applied to: OrderEventPublisher.publishOrderCreated()
 * - Kafka publishing is fire-and-forget — the order response shouldn't wait for it
 * - Without @Async: the HTTP response blocks until Kafka.send() completes
 * - With @Async: the HTTP response returns immediately; Kafka publish runs in background
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "eventPublisherExecutor")
    public Executor eventPublisherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("event-publisher-");
        executor.initialize();
        return executor;
    }
}
