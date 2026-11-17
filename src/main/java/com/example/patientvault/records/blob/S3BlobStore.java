package com.example.patientvault.records.blob;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/**
 * Amazon S3 adapter (spec 5.3.6). Objects are immutable ciphertext under UUID-only keys; no tags and no personal
 * data in keys or headers. Uploads never buffer a whole file: one reusable buffer of {@code part-size} bytes, a
 * single {@code PutObject} when the content fits in it and a multipart upload otherwise. The bucket is versioned,
 * so erase deletes every version and delete marker by {@code versionId}; a plain delete would only add a marker.
 */
public class S3BlobStore implements BlobStore {

    static final int MAX_PARTS = 10_000;
    private static final Logger LOG = LoggerFactory.getLogger(S3BlobStore.class);
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final int DELETE_BATCH = 1000;

    private final S3Client s3;
    private final S3Properties properties;
    private final String bucket;
    private final int partSize;

    public S3BlobStore(S3Client s3, S3Properties properties) {
        this.s3 = s3;
        this.properties = properties;
        this.bucket = properties.bucket();
        this.partSize = Math.toIntExact(properties.partSize().toBytes());
    }

    @Override
    public BlobRef put(UUID fileId, UUID patientId, InputStream encryptedContent, BlobMeta meta) {
        String key = StorageKeys.content(patientId, fileId);
        byte[] buffer = new byte[partSize];
        try {
            int filled = encryptedContent.readNBytes(buffer, 0, partSize);
            if (filled < partSize) {
                putObject(key, buffer, filled, meta);
                return new BlobRef(key, null, filled);
            }
            return new BlobRef(key, null, putMultipart(key, buffer, encryptedContent, meta));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SdkException e) {
            throw new BlobStoreException("S3 could not store the object (" + describe(e) + ")", e);
        }
    }

    private void putObject(String key, byte[] data, int length, BlobMeta meta) {
        PutObjectRequest.Builder request = PutObjectRequest.builder().bucket(bucket).key(key)
                .contentType(OCTET_STREAM).metadata(userMetadata(meta))
                .checksumAlgorithm(ChecksumAlgorithm.SHA256);
        if (properties.conditionalWrites()) {
            request.ifNoneMatch("*");
        }
        applySse(request);
        s3.putObject(request.build(), RequestBody.fromContentProvider(
                () -> new ByteArrayInputStream(data, 0, length), length, OCTET_STREAM));
    }

    private long putMultipart(String key, byte[] buffer, InputStream in, BlobMeta meta) throws IOException {
        var create = CreateMultipartUploadRequest.builder()
                .bucket(bucket).key(key).contentType(OCTET_STREAM).metadata(userMetadata(meta))
                .checksumAlgorithm(ChecksumAlgorithm.SHA256);
        applySse(create);
        String uploadId = s3.createMultipartUpload(create.build()).uploadId();
        try {
            List<CompletedPart> parts = new ArrayList<>();
            long total = 0;
            int filled = buffer.length;
            for (int number = 1; filled > 0; number++) {
                if (number > MAX_PARTS) {
                    throw new BlobStoreException("S3 upload exceeds " + MAX_PARTS + " parts");
                }
                int length = filled;
                var uploaded = s3.uploadPart(UploadPartRequest.builder().bucket(bucket).key(key).uploadId(uploadId)
                                .partNumber(number).checksumAlgorithm(ChecksumAlgorithm.SHA256).build(),
                        RequestBody.fromContentProvider(() -> new ByteArrayInputStream(buffer, 0, length), length, OCTET_STREAM));
                parts.add(CompletedPart.builder().partNumber(number).eTag(uploaded.eTag())
                        .checksumSHA256(uploaded.checksumSHA256()).build());
                total += filled;
                filled = in.readNBytes(buffer, 0, partSize);
            }
            var complete = CompleteMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build());
            if (properties.conditionalWrites()) {
                complete.ifNoneMatch("*");
            }
            s3.completeMultipartUpload(complete.build());
            return total;
        } catch (IOException | RuntimeException e) {
            try {
                s3.abortMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(uploadId));
            } catch (RuntimeException abort) {
                LOG.warn("Could not abort multipart upload; the bucket lifecycle rule removes the parts");
            }
            throw e;
        }
    }

    @Override
    public String putThumbnail(UUID fileId, UUID patientId, byte[] encryptedThumbnail) {
        String key = StorageKeys.thumbnail(patientId, fileId);
        try {
            putObject(key, encryptedThumbnail, encryptedThumbnail.length, new BlobMeta(null));
        } catch (SdkException e) {
            throw new BlobStoreException("S3 could not store the thumbnail (" + describe(e) + ")", e);
        }
        return key;
    }

    @Override
    public InputStream openContent(BlobRef ref) {
        return get(ref.key());
    }

    @Override
    public Optional<InputStream> openThumbnail(BlobRef ref) {
        if (ref.thumbnailKey() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(get(ref.thumbnailKey()));
        } catch (BlobNotFoundException e) {
            return Optional.empty();
        }
    }

    private InputStream get(String key) {
        try {
            return s3.getObject(b -> b.bucket(bucket).key(key));
        } catch (NoSuchKeyException e) {
            throw new BlobNotFoundException();
        } catch (SdkException e) {
            throw new BlobStoreException("S3 could not read the object (" + describe(e) + ")", e);
        }
    }

    @Override
    public void erase(BlobRef ref) {
        try {
            eraseKey(ref.key());
            if (ref.thumbnailKey() != null) {
                eraseKey(ref.thumbnailKey());
            }
        } catch (SdkException e) {
            throw new BlobStoreException("S3 could not erase the object (" + describe(e) + ")", e);
        }
    }

    /** Lists every version and delete marker of exactly this key and removes each by version id. */
    private void eraseKey(String key) {
        List<ObjectIdentifier> targets = new ArrayList<>();
        String keyMarker = null;
        String versionMarker = null;
        ListObjectVersionsResponse page;
        do {
            String km = keyMarker;
            String vm = versionMarker;
            page = s3.listObjectVersions(b -> b.bucket(bucket).prefix(key).keyMarker(km).versionIdMarker(vm));
            page.versions().stream().filter(v -> v.key().equals(key))
                    .forEach(v -> targets.add(ObjectIdentifier.builder().key(key).versionId(v.versionId()).build()));
            page.deleteMarkers().stream().filter(m -> m.key().equals(key))
                    .forEach(m -> targets.add(ObjectIdentifier.builder().key(key).versionId(m.versionId()).build()));
            keyMarker = page.nextKeyMarker();
            versionMarker = page.nextVersionIdMarker();
        } while (Boolean.TRUE.equals(page.isTruncated()));

        for (int from = 0; from < targets.size(); from += DELETE_BATCH) {
            List<ObjectIdentifier> batch = targets.subList(from, Math.min(from + DELETE_BATCH, targets.size()));
            DeleteObjectsResponse result = s3.deleteObjects(b -> b.bucket(bucket)
                    .delete(Delete.builder().objects(batch).quiet(true).build()));
            if (result.hasErrors() && !result.errors().isEmpty()) {
                throw new BlobStoreException("S3 could not delete " + result.errors().size() + " object version(s)");
            }
        }
    }

    /** Reachability and permission check used by the health indicator. */
    boolean reachable() {
        try {
            s3.headBucket(b -> b.bucket(bucket));
            return true;
        } catch (SdkException e) {
            return false;
        }
    }

    private Map<String, String> userMetadata(BlobMeta meta) {
        return meta.cipher() == null ? Map.of("schema", "1") : Map.of("cipher", meta.cipher(), "schema", "1");
    }

    private void applySse(PutObjectRequest.Builder request) {
        switch (properties.sse()) {
            case S3 -> request.serverSideEncryption(ServerSideEncryption.AES256);
            case KMS -> request.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(properties.kmsKeyId());
            case NONE -> { }
        }
    }

    private void applySse(CreateMultipartUploadRequest.Builder request) {
        switch (properties.sse()) {
            case S3 -> request.serverSideEncryption(ServerSideEncryption.AES256);
            case KMS -> request.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(properties.kmsKeyId());
            case NONE -> { }
        }
    }

    /** Error text for exceptions: the S3 error code and status only, which never contain object content. */
    private static String describe(SdkException e) {
        if (e instanceof AwsServiceException service) {
            return service.statusCode() + " " + service.awsErrorDetails().errorCode();
        }
        return e.getClass().getSimpleName();
    }
}
