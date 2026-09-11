package com.learn.inventoryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

// Spring Boot's autoconfigured @KafkaListener container factory automatically picks up
// any DefaultErrorHandler bean found in the context (it's a CommonErrorHandler), so
// declaring this bean is enough to wire it in -- no need to build the factory by hand.
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // On a listener exception: retry the same record 3 times, 1 second apart. If it still
        // fails, publish the original record to "<topic>.DLT" (order.created.DLT here) instead
        // of blocking this consumer on it forever, then move on to the next message.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        FixedBackOff backOff = new FixedBackOff(1000L, 3);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
