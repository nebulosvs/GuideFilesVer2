package com.example.bdgetproducer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class StorageConfig {

    @Value("${cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key:}")
    private String secretKey;

    @Value("${cloud.aws.credentials.session-token:}")
    private String sessionToken;

    @Value("${cloud.aws.region.static:us-east-1}")
    private String region;

    @Bean
    public S3Client s3Client() {

        if (accessKey == null || accessKey.isBlank() ||
            secretKey == null || secretKey.isBlank()) {

            return S3Client.builder()
                    .region(Region.of(region))
                    .build();
        }

        AwsCredentials credentials;

        if (sessionToken != null && !sessionToken.isBlank()) {
            credentials = AwsSessionCredentials.create(accessKey, secretKey, sessionToken);
        } else {
            credentials = AwsBasicCredentials.create(accessKey, secretKey);
        }

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}