package com.example.patientvault.records.blob;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/** Wires the S3 client and the {@link BlobStore} adapter. Fails fast on missing or inconsistent settings. */
@Configuration(proxyBeanMethods = false)
class S3ClientConfig {

    private static final DataSize MIN_PART_SIZE = DataSize.ofMegabytes(5);

    @Bean(destroyMethod = "close")
    S3Client s3Client(S3Properties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .forcePathStyle(properties.pathStyle())
                .credentialsProvider(credentials(properties))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(60)));
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    @Bean
    S3BlobStore blobStore(S3Client s3, S3Properties properties, @Value("${app.uploads.max-file-size}") DataSize maxFileSize) {
        require(properties.bucket(), "S3_BUCKET");
        if (properties.partSize().compareTo(MIN_PART_SIZE) < 0) {
            throw new IllegalStateException("app.storage.s3.part-size must be at least 5MiB");
        }
        if (properties.partSize().toBytes() * S3BlobStore.MAX_PARTS < maxFileSize.toBytes()) {
            throw new IllegalStateException("app.storage.s3.part-size x 10,000 parts is smaller than app.uploads.max-file-size");
        }
        if (properties.sse() == S3Properties.Sse.KMS) {
            require(properties.kmsKeyId(), "app.storage.s3.kms-key-id");
        }
        return new S3BlobStore(s3, properties);
    }

    private static AwsCredentialsProvider credentials(S3Properties properties) {
        boolean hasKey = properties.accessKeyId() != null && !properties.accessKeyId().isBlank();
        boolean hasSecret = properties.secretAccessKey() != null && !properties.secretAccessKey().isBlank();
        if (hasKey != hasSecret) {
            throw new IllegalStateException("S3_ACCESS_KEY_ID and S3_SECRET_ACCESS_KEY must be set together");
        }
        if (!hasKey) {
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
    }
}
