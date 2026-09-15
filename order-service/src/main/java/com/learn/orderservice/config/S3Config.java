package com.learn.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// No credentials configured here at all -- S3Presigner (like every AWS SDK v2 client) uses
// the default credential provider chain, which on EC2 means the instance's own IAM role
// (ec2-app-cloudwatch-role, now also carrying ProductImageUploadPolicy). Same
// no-static-keys approach as everything else in this project.
@Configuration
public class S3Config {

    @Value("${aws.region}")
    private String region;

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
