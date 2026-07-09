package com.wordflip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.domain.WordImage;
import com.wordflip.dto.media.ImageTransform;
import com.wordflip.dto.media.WordImageResponse;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.WordImageRepository;
import com.wordflip.storage.MinioStorageService;
import com.wordflip.util.WordKeyUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 卡片图片业务：上传/替换、查询、仅更新 transform、删除（REQ-IMAGE / REQ-SNAPSHOT）。
 * <p>存储路径：{@code card-images/{userId}/{wordKey}.webp}；MIME 白名单 jpeg/png/webp，上限约 5MB。
 */
@Service
public class ImageService {

    /** 允许的上传 MIME（REQ / architecture 白名单） */
    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    /** 上传体积上限约 5MB */
    static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    private static final String WEBP_CONTENT_TYPE = "image/webp";

    private final WordImageRepository wordImageRepository;
    private final MinioStorageService minioStorageService;
    private final ObjectMapper objectMapper;

    public ImageService(
            WordImageRepository wordImageRepository,
            MinioStorageService minioStorageService,
            ObjectMapper objectMapper
    ) {
        this.wordImageRepository = wordImageRepository;
        this.minioStorageService = minioStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /words/{wordKey}/image：无行时 404。
     */
    @Transactional(readOnly = true)
    public WordImageResponse get(Long userId, String rawWordKey) {
        String wordKey = normalizeWordKey(rawWordKey);
        WordImage image = wordImageRepository.findByUserIdAndWordKey(userId, wordKey)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "该单词尚无卡片图片"));
        return toResponse(image);
    }

    /**
     * POST multipart：校验 MIME/大小 → 转 WebP → 上传 MinIO → upsert DB。
     */
    @Transactional
    public WordImageResponse uploadOrReplace(Long userId, String rawWordKey, MultipartFile file, String transformJson) {
        String wordKey = normalizeWordKey(rawWordKey);
        validateUpload(file);
        ImageTransform transform = parseTransform(transformJson).withDefaults();
        validateTransform(transform);

        byte[] webpBytes = encodeToWebp(readBytes(file));
        String storageKey = storageKey(userId, wordKey);

        // 先写对象存储，再落库；替换时覆盖同 key
        minioStorageService.putObject(webpBytes, storageKey, WEBP_CONTENT_TYPE);

        Instant now = Instant.now();
        WordImage entity = wordImageRepository.findByUserIdAndWordKey(userId, wordKey)
                .orElseGet(WordImage::new);
        if (entity.getId() == null) {
            entity.setUserId(userId);
            entity.setWordKey(wordKey);
            entity.setCreatedAt(now);
        }
        entity.setStorageKey(storageKey);
        entity.setTransformJson(writeTransformJson(transform));
        entity.setUpdatedAt(now);
        wordImageRepository.save(entity);

        return toResponse(entity);
    }

    /**
     * PATCH：仅更新 transform_json，不重传文件。
     */
    @Transactional
    public WordImageResponse patchTransform(Long userId, String rawWordKey, ImageTransform transform) {
        String wordKey = normalizeWordKey(rawWordKey);
        WordImage image = wordImageRepository.findByUserIdAndWordKey(userId, wordKey)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "该单词尚无卡片图片"));
        ImageTransform normalized = (transform != null ? transform : new ImageTransform(null, null, null, null, null, null))
                .withDefaults();
        validateTransform(normalized);
        image.setTransformJson(writeTransformJson(normalized));
        image.setUpdatedAt(Instant.now());
        wordImageRepository.save(image);
        return toResponse(image);
    }

    /**
     * DELETE：删 MinIO 对象 + DB 行；无图时幂等 204。
     */
    @Transactional
    public void delete(Long userId, String rawWordKey) {
        String wordKey = normalizeWordKey(rawWordKey);
        Optional<WordImage> existing = wordImageRepository.findByUserIdAndWordKey(userId, wordKey);
        if (existing.isEmpty()) {
            return;
        }
        WordImage image = existing.get();
        minioStorageService.removeObject(image.getStorageKey());
        wordImageRepository.delete(image);
    }

    /**
     * 校验 MIME 与大小；供单测直接调用。
     */
    void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", "请上传图片文件");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new WordflipException("VALIDATION_ERROR", "图片大小不能超过 5MB");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new WordflipException("VALIDATION_ERROR", "仅支持 image/jpeg、image/png、image/webp");
        }
    }

    private WordImageResponse toResponse(WordImage image) {
        // 真机无法访问 MinIO localhost:9000，返回后端媒体代理路径（相对 /api/v1）
        return new WordImageResponse(
                image.getWordKey(),
                true,
                mediaProxyUrl(image.getUserId(), image.getWordKey()),
                image.getStorageKey(),
                readTransform(image.getTransformJson()),
                image.getUpdatedAt()
        );
    }

    /** 客户端经 API 拉图：GET /api/v1/media/card-images/{userId}/{wordKey}.webp */
    static String mediaProxyUrl(Long userId, String wordKey) {
        return "/api/v1/media/card-images/" + userId + "/" + wordKey + ".webp";
    }

    private static String storageKey(Long userId, String wordKey) {
        return "card-images/" + userId + "/" + wordKey + ".webp";
    }

    private static String normalizeWordKey(String raw) {
        String wordKey = WordKeyUtil.normalize(raw);
        if (wordKey.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", "wordKey 不能为空");
        }
        return wordKey;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String lower = contentType.trim().toLowerCase(Locale.ROOT);
        int semi = lower.indexOf(';');
        return semi >= 0 ? lower.substring(0, semi).trim() : lower;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new WordflipException("VALIDATION_ERROR", "读取上传文件失败");
        }
    }

    /**
     * 解码后以 WebP 重新编码；无 WebP writer 时回退为原字节但仍以 image/webp 存储名约定上传。
     */
    private byte[] encodeToWebp(byte[] sourceBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (image == null) {
                throw new WordflipException("VALIDATION_ERROR", "无法解析图片内容");
            }
            // 统一为 RGB，避免部分 PNG 带 alpha 导致 writer 失败
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g = rgb.createGraphics();
            try {
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(WEBP_CONTENT_TYPE);
            if (!writers.hasNext()) {
                writers = ImageIO.getImageWritersByFormatName("webp");
            }
            if (!writers.hasNext()) {
                // 库不可用时：仍按 .webp key 存原字节（content-type image/webp），避免阻断上传
                return sourceBytes;
            }
            ImageWriter writer = writers.next();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    String[] types = param.getCompressionTypes();
                    if (types != null && types.length > 0) {
                        param.setCompressionType(types[0]);
                    }
                    try {
                        param.setCompressionQuality(0.85f);
                    } catch (UnsupportedOperationException ignored) {
                        // 部分 WebP writer 不支持 quality
                    }
                }
                writer.write(null, new IIOImage(rgb, null, null), param);
                ios.flush();
                return baos.toByteArray();
            } finally {
                writer.dispose();
            }
        } catch (WordflipException e) {
            throw e;
        } catch (IOException e) {
            throw new WordflipException("VALIDATION_ERROR", "图片转码 WebP 失败");
        }
    }

    private ImageTransform parseTransform(String transformJson) {
        if (transformJson == null || transformJson.isBlank()) {
            throw new WordflipException("VALIDATION_ERROR", "transform 不能为空");
        }
        try {
            return objectMapper.readValue(transformJson, ImageTransform.class);
        } catch (JsonProcessingException e) {
            throw new WordflipException("VALIDATION_ERROR", "transform 不是合法 JSON");
        }
    }

    private ImageTransform readTransform(String json) {
        try {
            return objectMapper.readValue(json, ImageTransform.class).withDefaults();
        } catch (JsonProcessingException e) {
            throw new WordflipException("INTERNAL_ERROR", "读取 transform 失败");
        }
    }

    private String writeTransformJson(ImageTransform transform) {
        try {
            return objectMapper.writeValueAsString(transform);
        } catch (JsonProcessingException e) {
            throw new WordflipException("INTERNAL_ERROR", "序列化 transform 失败");
        }
    }

    /**
     * 校验 scale 范围（openapi：0.2–3）。
     */
    private void validateTransform(ImageTransform transform) {
        if (transform.scale() != null && (transform.scale() < 0.2 || transform.scale() > 3.0)) {
            throw new WordflipException("VALIDATION_ERROR", "scale 须在 0.2～3 之间");
        }
    }
}
