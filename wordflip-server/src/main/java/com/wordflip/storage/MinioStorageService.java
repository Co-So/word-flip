package com.wordflip.storage;

import com.wordflip.config.MinioProperties;
import io.minio.MinioClient;
import org.springframework.stereotype.Service;

/**
 * MinIO storage wrapper placeholder — full implementation in P3 image tasks.
 */
@Service
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public String bucket() {
        return properties.getBucket();
    }

    public MinioClient client() {
        return minioClient;
    }
}
