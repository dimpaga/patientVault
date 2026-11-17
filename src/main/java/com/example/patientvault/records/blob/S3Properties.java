package com.example.patientvault.records.blob;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Amazon S3 (or S3-compatible) connection for the encrypted file store. Without an access key the AWS default
 * credential chain is used. No {@code toString()} output of the secrets.
 *
 * @param bucket            private bucket that holds only this application's objects
 * @param endpoint          endpoint override for an S3-compatible emulator (local/test only); empty for AWS
 * @param pathStyle         path-style addressing (needed by most emulators)
 * @param partSize          multipart part size, also the upload buffer (minimum 5 MiB)
 * @param sse               {@code S3} (SSE-S3), {@code KMS} (with {@code kmsKeyId}) or {@code NONE} (emulators only)
 * @param conditionalWrites send {@code If-None-Match: *} on put so an object is never overwritten
 */
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(
        String bucket,
        @DefaultValue("eu-central-1") String region,
        String accessKeyId,
        String secretAccessKey,
        String endpoint,
        @DefaultValue("false") boolean pathStyle,
        @DefaultValue("8MB") DataSize partSize,
        @DefaultValue("S3") Sse sse,
        String kmsKeyId,
        @DefaultValue("true") boolean conditionalWrites) {

    public enum Sse {
        S3, KMS, NONE
    }

    @Override
    public String toString() {
        return "S3Properties[bucket=" + bucket + ", region=" + region + "]";
    }
}
