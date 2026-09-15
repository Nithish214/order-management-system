package com.learn.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// Backs OutboxEventCreatedListener's @Async -- without @EnableAsync, that annotation is
// silently ignored and the listener runs synchronously on the same thread that committed
// the transaction (here, the Kafka consumer thread handling inventory.reserved, blocking
// it on the outbound Kafka send instead of letting it move on to the next message).
// Identical setup and reasoning to Order Service's AsyncConfig.
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
