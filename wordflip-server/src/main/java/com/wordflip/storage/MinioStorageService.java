package com.wordflip.storage;

import com.wordflip.config.MinioProperties;
import com.wordflip.exception.WordflipException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MinIO 对象存储封装：上传、删除与预签名 GET（P3 Images）。
 */
@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public MinioStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 首次写操作前确保 bucket 存在（懒初始化，避免测试/无 MinIO 时启动失败）。
     */
    private void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            try {
                String bucket = properties.getBucket();
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                bucketReady.set(true);
            } catch (Exception e) {
                // 鉴权失败等应立即暴露，避免仅 WARN 后 put 再失败且客户端难排查
                log.error("确保 MinIO bucket 失败: {}", e.getMessage());
                throw new WordflipException(
                        "INTERNAL_ERROR",
                        "对象存储不可用，请检查 MinIO 是否启动且 access-key/secret 与 docker/.env 一致"
                );
            }
        }
    }

    public String bucket() {
        return properties.getBucket();
    }

    public MinioClient client() {
        return minioClient;
    }

    /**
     * 上传对象字节到指定 key。
     *
     * @param bytes       文件内容
     * @param key         对象路径，如 card-images/{userId}/{wordKey}.webp
     * @param contentType MIME，如 image/webp
     */
    public void putObject(byte[] bytes, String key, String contentType) {
        ensureBucket();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new WordflipException("INTERNAL_ERROR", "上传图片到对象存储失败");
        }
    }

    /**
     * 删除对象；对象不存在时忽略（幂等）。
     */
    public void removeObject(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new WordflipException("INTERNAL_ERROR", "删除对象存储中的图片失败");
        }
    }

    /**
     * 生成预签名 GET URL（直连 MinIO；真机调试不可达 localhost:9000，优先用 {@link #getObjectBytes} + 后端代理）。
     */
    public String presignedGetUrl(String key, Duration duration) {
        try {
            long seconds = Math.max(1, duration.getSeconds());
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(key)
                    .expiry((int) seconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new WordflipException("INTERNAL_ERROR", "生成图片访问链接失败");
        }
    }

    /**
     * 读取对象字节，供后端媒体代理返回给客户端（绕过手机无法访问 MinIO localhost）。
     */
    public byte[] getObjectBytes(String key) {
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(key)
                .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new WordflipException("NOT_FOUND", "图片对象不存在或读取失败");
        }
    }
}
