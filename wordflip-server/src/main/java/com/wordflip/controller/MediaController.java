package com.wordflip.controller;

import com.wordflip.exception.WordflipException;
import com.wordflip.security.SecurityUtils;
import com.wordflip.storage.MinioStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 卡片图片媒体代理：GET /media/card-images/{userId}/{fileName}
 * <p>
 * 真机经 adb reverse 只能访问后端 8080，无法直连 MinIO localhost:9000；
 * 因此 imageUrl 指向本接口，由服务端从 MinIO 读出再返回。
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MinioStorageService minioStorageService;

    public MediaController(MinioStorageService minioStorageService) {
        this.minioStorageService = minioStorageService;
    }

    /**
     * 代理读取 MinIO 对象；仅允许访问当前登录用户自己的目录。
     */
    @GetMapping("/card-images/{userId}/{fileName}")
    public ResponseEntity<byte[]> getCardImage(
            @PathVariable Long userId,
            @PathVariable String fileName
    ) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        // 资源按 userId 隔离，禁止越权读他人卡片图
        if (!currentUserId.equals(userId)) {
            throw new WordflipException("FORBIDDEN", "无权访问该图片");
        }
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/")) {
            throw new WordflipException("VALIDATION_ERROR", "非法文件名");
        }
        String key = "card-images/" + userId + "/" + fileName;
        byte[] bytes = minioStorageService.getObjectBytes(key);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(MediaType.parseMediaType("image/webp"))
                .body(bytes);
    }
}
