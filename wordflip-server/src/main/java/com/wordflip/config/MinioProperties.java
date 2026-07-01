package com.wordflip.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioProperties {

    @Value("${wordflip.minio.endpoint}")
    private String endpoint;

    @Value("${wordflip.minio.access-key}")
    private String accessKey;

    @Value("${wordflip.minio.secret-key}")
    private String secretKey;

    @Value("${wordflip.minio.bucket}")
    private String bucket;

    public String getEndpoint() {
        return endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getBucket() {
        return bucket;
    }
}
