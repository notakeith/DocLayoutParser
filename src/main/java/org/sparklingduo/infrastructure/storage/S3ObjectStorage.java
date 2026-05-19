package org.sparklingduo.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sparklingduo.domain.exception.StorageException;
import org.sparklingduo.domain.port.ObjectStorage;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Override
    public void put(String bucket, String key, byte[] data, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) data.length)
                            .build(),
                    RequestBody.fromBytes(data)
            );
            log.debug("Uploaded s3://{}/{} ({} bytes)", bucket, key, data.length);
        } catch (S3Exception e) {
            throw new StorageException("Failed to upload s3://%s/%s".formatted(bucket, key), e);
        }
    }

    @Override
    public byte[] get(String bucket, String key) {
        try {
            return s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            ).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new StorageException("Object not found: s3://%s/%s".formatted(bucket, key), e);
        } catch (S3Exception e) {
            throw new StorageException("Failed to get s3://%s/%s".formatted(bucket, key), e);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            );
        } catch (S3Exception e) {
            log.warn("Failed to delete s3://{}/{}: {}", bucket, key, e.getMessage());
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String key, int expiresInSeconds) {
        var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                .getObjectRequest(r -> r.bucket(bucket).key(key))
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}