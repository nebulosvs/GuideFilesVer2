package com.example.bdgetconsumer.repository;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

@Repository
public class S3RepositoryImpl implements S3Repository {

    @Autowired
    private S3Client s3Client;

    @Override
    public List<String> listObjects(String bucketName, String prefix) {
        try {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName);
            if (prefix != null && !prefix.isBlank()) {
                requestBuilder.prefix(prefix);
            }
            return s3Client.listObjectsV2(requestBuilder.build())
                    .contents()
                    .stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
        } catch (S3Exception ex) {
            throw new RuntimeException("Error al listar objetos en S3: " + ex.getMessage(), ex);
        }
    }

    @Override
    public byte[] downloadObject(String bucketName, String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            try (ResponseInputStream<?> response = s3Client.getObject(request)) {
                return response.readAllBytes();
            }
        } catch (NoSuchKeyException ex) {
            throw new RuntimeException("Archivo no encontrado en S3: " + key, ex);
        } catch (IOException | S3Exception ex) {
            throw new RuntimeException("Error al descargar archivo de S3: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void uploadObject(String bucketName, String key, byte[] content, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(content));
        } catch (S3Exception ex) {
            throw new RuntimeException("Error al subir archivo a S3: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void deleteObject(String bucketName, String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
        } catch (S3Exception ex) {
            throw new RuntimeException("Error al eliminar archivo en S3: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean objectExists(String bucketName, String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            throw new RuntimeException("Error al verificar archivo en S3: " + ex.getMessage(), ex);
        }
    }
}
