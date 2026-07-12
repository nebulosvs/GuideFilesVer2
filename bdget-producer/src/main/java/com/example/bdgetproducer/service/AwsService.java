package com.example.bdgetproducer.service;

import java.util.List;

public interface AwsService {

    List<String> listS3Files(String bucketName, String prefix);

    byte[] downloadS3File(String bucketName, String key);

    void uploadFile(String bucketName, String key, byte[] content, String contentType);

    void deleteObject(String bucketName, String key);

    boolean objectExists(String bucketName, String key);
}
