package org.sparklingduo.domain.port;

public interface ObjectStorage {
    void put(String bucket, String key, byte[] data, String contentType);

    byte[] get(String bucket, String key);

    void delete(String bucket, String key);

    String presignedGetUrl(String bucket, String key, int expiresInSeconds);
}