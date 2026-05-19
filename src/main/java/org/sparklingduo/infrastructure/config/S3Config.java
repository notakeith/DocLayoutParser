package org.sparklingduo.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final AppProperties appProperties;

    private StaticCredentialsProvider credentials() {
        AppProperties.Storage cfg = appProperties.getStorage();
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey())
        );
    }

    @Bean
    public S3Client s3Client() {
        AppProperties.Storage cfg = appProperties.getStorage();
        return S3Client.builder()
                .endpointOverride(URI.create(cfg.getEndpoint()))
                .credentialsProvider(credentials())
                .region(Region.of(cfg.getRegion()))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        AppProperties.Storage cfg = appProperties.getStorage();
        return S3Presigner.builder()
                .endpointOverride(URI.create(cfg.getEndpoint()))
                .credentialsProvider(credentials())
                .region(Region.of(cfg.getRegion()))
                .build();
    }
}