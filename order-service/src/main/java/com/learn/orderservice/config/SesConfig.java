package com.learn.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

// No credentials configured here at all -- same default-credential-provider-chain story
// as S3Config: on EC2 this resolves via the instance's own IAM role
// (ec2-app-cloudwatch-role, now also carrying OrderNotificationEmailPolicy, scoped to
// exactly the one verified SES sender identity this service is allowed to send from).
@Configuration
public class SesConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    public SesClient sesClient() {
        return SesClient.builder()
                .region(Region.of(region))
                .build();
    }
}
