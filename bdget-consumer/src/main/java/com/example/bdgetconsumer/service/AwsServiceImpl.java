package com.example.bdgetconsumer.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.bdgetconsumer.repository.S3Repository;

@Service
public class AwsServiceImpl implements AwsService {

    @Autowired
    private S3Repository s3Repository;

    @Override
    public List<String> listS3Files(String bucketName, String prefix) {
        return s3Repository.listObjects(bucketName, prefix);
    }

    @Override
    public byte[] downloadS3File(String bucketName, String key) {
        return s3Repository.downloadObject(bucketName, key);
    }

    @Override
    public void uploadFile(String bucketName, String key, byte[] content, String contentType) {
        s3Repository.uploadObject(bucketName, key, content, contentType);
    }

    @Override
    public void deleteObject(String bucketName, String key) {
        s3Repository.deleteObject(bucketName, key);
    }

    @Override
    public boolean objectExists(String bucketName, String key) {
        return s3Repository.objectExists(bucketName, key);
    }
}
