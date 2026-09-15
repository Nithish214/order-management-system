package com.learn.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Spring Boot auto-enables @KafkaListener processing once spring-kafka is on the
// classpath (via KafkaAnnotationDrivenConfiguration) -- no @EnableKafka needed here.
// @EnableScheduling backs OutboxPoller's @Scheduled safety net.
@SpringBootApplication
@EnableScheduling
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
