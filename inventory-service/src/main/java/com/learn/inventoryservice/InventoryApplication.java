package com.learn.inventoryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring Boot auto-enables @KafkaListener processing once spring-kafka is on the
// classpath (via KafkaAnnotationDrivenConfiguration) -- no @EnableKafka needed here.
@SpringBootApplication
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
