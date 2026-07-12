package com.example.bdgetproducer.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EfsStorageService {

    @Value("${storage.efs.base-path:/app/efs}")
    private String basePath;

    public Path resolvePath(String relativePath) {
        return Paths.get(basePath, relativePath).normalize();
    }

    public void writeFile(String relativePath, byte[] content) {
        try {
            Path target = resolvePath(relativePath);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new RuntimeException("Error al escribir archivo en EFS: " + ex.getMessage(), ex);
        }
    }

    public byte[] readFile(String relativePath) {
        try {
            Path target = resolvePath(relativePath);
            if (!Files.exists(target)) {
                throw new RuntimeException("Archivo no encontrado en EFS: " + relativePath);
            }
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new RuntimeException("Error al leer archivo desde EFS: " + ex.getMessage(), ex);
        }
    }

    public void deleteFile(String relativePath) {
        try {
            Path target = resolvePath(relativePath);
            if (Files.exists(target)) {
                Files.delete(target);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error al eliminar archivo en EFS: " + ex.getMessage(), ex);
        }
    }

    public boolean fileExists(String relativePath) {
        return Files.exists(resolvePath(relativePath));
    }
}
