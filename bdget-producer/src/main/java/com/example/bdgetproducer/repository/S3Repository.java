package com.example.bdgetproducer.repository;

import java.util.List;

public interface S3Repository {

    List<String> listObjects(String bucketName, String prefix);

    byte[] downloadObject(String bucketName, String key);

    void uploadObject(String bucketName, String key, byte[] content, String contentType);

    void deleteObject(String bucketName, String key);

    boolean objectExists(String bucketName, String key);
}
