package com.queuedockyard.ecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * SQS client configuration — pointed at LocalStack.
 * Same pattern as phase-5-sqs/localstack.
 *
 * In production: remove endpointOverride, use DefaultCredentialsProvider.
 */
@Slf4j
@Configuration
public class SqsConfig {

    @Value("${aws.endpoint}")
    private String endpoint;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.credentials.access-key}")
    private String accessKey;

    @Value("${aws.credentials.secret-key}")
    private String secretKey;

    @Bean
    public SqsClient sqsClient() {
        log.info("Configuring SQS client | endpoint: {} | region: {}", endpoint, region);

        return SqsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
    }

}