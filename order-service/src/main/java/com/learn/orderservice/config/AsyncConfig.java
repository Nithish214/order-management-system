package com.learn.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// Backs OutboxEventCreatedListener's @Async -- without @EnableAsync, that annotation is
// silently ignored and the listener just runs synchronously (which is exactly the bug
// this fixes: @TransactionalEventListener's default behavior runs on the SAME thread that
// committed the transaction, meaning the customer's own HTTP request was blocking on the
// Kafka round-trip completing before it could return a response).
//
// A named, bounded pool rather than Spring's @Async default (SimpleAsyncTaskExecutor,
// which spins up an unbounded new thread per task with no queue or ceiling at all) --
// outbox publishing is infrequent and quick, so a couple of threads is already generous,
// and "bounded" matters specifically on a memory-constrained t3.micro where an unbounded
// executor spawning threads under any kind of burst would be a real risk, not a
// theoretical one.
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "outboxPublisherExecutor")
    public Executor outboxPublisherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("outbox-publish-");
        executor.initialize();
        return executor;
    }
}
