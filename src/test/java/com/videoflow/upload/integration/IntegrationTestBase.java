package com.videoflow.upload.integration;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/**
 * Base class for tests that exercise the real API against Testcontainers
 * PostgreSQL and MinIO. Skipped automatically when Docker is not available
 * (see {@link Testcontainers#disabledWithoutDocker}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestBase {

    public static final UUID USER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID USER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final String BUCKET = "video-uploads";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("uploads")
            .withUsername("test")
            .withPassword("test");

    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withUserName("minioadmin")
            .withPassword("minioadmin");

    @Container
    static final PostgreSQLContainer<?> POSTGRES_CONTAINER = POSTGRES;

    @Container
    static final MinIOContainer MINIO_CONTAINER = MINIO;

    static S3Client storageClient;

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate rest;

    @DynamicPropertySource
    static void datasourceAndStorage(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("storage.s3.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("storage.s3.access-key", MINIO::getUserName);
        registry.add("storage.s3.secret-key", MINIO::getPassword);
        registry.add("storage.s3.bucket", () -> BUCKET);
        registry.add("storage.s3.region", () -> "us-east-1");
    }

    static void ensureBucket() {
        if (storageClient == null) {
            storageClient = S3Client.builder()
                    .region(Region.US_EAST_1)
                    .endpointOverride(java.net.URI.create(
                            "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
        }
        try {
            storageClient.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build());
        } catch (NoSuchBucketException ex) {
            storageClient.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    protected String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    protected HttpHeaders authHeaders(UUID user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", user.toString());
        return headers;
    }

    protected HttpEntity<String> jsonRequest(UUID user, String body) {
        return new HttpEntity<>(body, authHeaders(user));
    }

    protected HttpEntity<String> jsonRequestNoAuth(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}