package com.learn.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// No credentials configured here at all -- both clients (like every AWS SDK v2 client)
// use the default credential provider chain, which on EC2 means the instance's own IAM
// role (ec2-app-cloudwatch-role, now also carrying ProductImageUploadPolicy). Same
// no-static-keys approach as everything else in this project.
@Configuration
public class S3Config {

    @Value("${aws.region}")
    private String region;

    // Only for minting presigned upload URLs -- see ProductImageUploadService#createUploadUrl.
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }

    // A real client, not just the presigner, for the one operation that actually needs to
    // execute immediately rather than being handed to a browser to use later: deleting the
    // file a product used to point at once it's been replaced (see
    // ProductImageUploadService#deleteIfManaged).
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
