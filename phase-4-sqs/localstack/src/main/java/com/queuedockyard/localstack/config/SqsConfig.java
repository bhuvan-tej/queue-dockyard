package com.queuedockyard.localstack.config;

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
 * Configures the AWS SQS client pointed at LocalStack.
 *
 * Three things that make this work with LocalStack instead of real AWS:
 *
 * 1. endpointOverride — redirects all API calls to localhost:4566
 *    instead of the real AWS endpoint (sqs.us-east-1.amazonaws.com)
 *
 * 2. StaticCredentialsProvider — LocalStack accepts any credentials.
 *    Real AWS would validate these against IAM.
 *
 * 3. UrlConnectionHttpClient — lightweight HTTP client for the SDK.
 *    No extra dependencies like Apache HttpClient needed.
 *
 * In production:
 *   - Remove endpointOverride entirely
 *   - Use DefaultCredentialsProvider (reads from IAM role / env vars)
 *   - Never hardcode credentials
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

    /**
     * Creates and configures the SQS client.
     *
     * This single client instance is thread-safe and reused
     * across the entire application — do not create new clients
     * per request, it is expensive.
     */
    @Bean
    public SqsClient sqsClient() {

        log.info("Configuring SQS client | endpoint: {} | region: {}", endpoint, region);

        return SqsClient.builder()
                // redirect to LocalStack instead of real AWS
                .endpointOverride(URI.create(endpoint))
                // any region works with LocalStack
                .region(Region.of(region))
                // static credentials — LocalStack doesn't validate these
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                // lightweight HTTP client — no extra dependencies needed
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
    }

}