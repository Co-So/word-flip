package com.wordflip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.dto.media.ImageTransform;
import com.wordflip.dto.media.WordImageResponse;
import com.wordflip.exception.WordflipException;
import com.wordflip.storage.MinioStorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户学习卡图片服务；相同词形在不同词书中的图片完全隔离。
 */
@Service
public class ImageService {

    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final String WEBP_CONTENT_TYPE = "image/webp";

    private final JdbcTemplate jdbc;
    private final MinioStorageService storage;
    private final ObjectMapper objectMapper;

    public ImageService(JdbcTemplate jdbc, MinioStorageService storage, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WordImageResponse get(Long userId, Long cardId) {
        Long lexemeId = requireCurrentCard(userId, cardId);
        List<WordImageResponse> rows = jdbc.query(
                "SELECT storage_key, transform_json, updated_at FROM card_images WHERE user_id=? AND card_id=?",
                (rs, row) -> new WordImageResponse(
                        cardId, lexemeId, true, mediaProxyUrl(userId, cardId),
                        rs.getString("storage_key"), parseTransform(rs.getString("transform_json")),
                        rs.getTimestamp("updated_at").toInstant()
                ), userId, cardId
        );
        if (rows.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "该学习卡尚无图片");
        }
        return rows.getFirst();
    }

    @Transactional
    public WordImageResponse uploadOrReplace(
            Long userId, Long cardId, MultipartFile file, String transformJson
    ) {
        requireCurrentCard(userId, cardId);
        validateUpload(file);
        ImageTransform transform = parseTransform(transformJson).withDefaults();
        validateTransform(transform);
        String storageKey = storageKey(userId, cardId);
        storage.putObject(encodeToWebp(readBytes(file)), storageKey, WEBP_CONTENT_TYPE);
        jdbc.update(
                """
                INSERT INTO card_images(user_id, card_id, storage_key, transform_json)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE storage_key=VALUES(storage_key),
                                        transform_json=VALUES(transform_json), updated_at=NOW(3)
                """,
                userId, cardId, storageKey, writeJson(transform)
        );
        return get(userId, cardId);
    }

    @Transactional
    public WordImageResponse patchTransform(Long userId, Long cardId, ImageTransform transform) {
        requireCurrentCard(userId, cardId);
        ImageTransform normalized = (transform == null
                ? new ImageTransform(null, null, null, null, null, null) : transform).withDefaults();
        validateTransform(normalized);
        int updated = jdbc.update(
                "UPDATE card_images SET transform_json=?, updated_at=NOW(3) WHERE user_id=? AND card_id=?",
                writeJson(normalized), userId, cardId
        );
        if (updated == 0) {
            throw new WordflipException("NOT_FOUND", "该学习卡尚无图片");
        }
        return get(userId, cardId);
    }

    @Transactional
    public void delete(Long userId, Long cardId) {
        requireCurrentCard(userId, cardId);
        List<String> keys = jdbc.queryForList(
                "SELECT storage_key FROM card_images WHERE user_id=? AND card_id=?",
                String.class, userId, cardId
        );
        if (!keys.isEmpty()) {
            storage.removeObject(keys.getFirst());
            jdbc.update("DELETE FROM card_images WHERE user_id=? AND card_id=?", userId, cardId);
        }
    }

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

    private Long requireCurrentCard(Long userId, Long cardId) {
        List<Long> values = jdbc.queryForList(
                """
                SELECT bi.lexeme_id FROM learning_cards c
                JOIN book_items bi ON bi.id=c.book_item_id
                JOIN user_learning_plans p ON p.book_id=bi.book_id AND p.user_id=?
                JOIN user_settings us ON us.user_id=p.user_id AND us.active_plan_id=p.id
                WHERE c.id=? AND c.status='published'
                """,
                Long.class, userId, cardId
        );
        if (values.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "当前计划中没有该学习卡");
        }
        return values.getFirst();
    }

    static String mediaProxyUrl(Long userId, Long cardId) {
        return "/api/v1/media/card-images/" + userId + "/" + cardId + ".webp";
    }

    private static String storageKey(Long userId, Long cardId) {
        return "card-images/" + userId + "/" + cardId + ".webp";
    }

    private ImageTransform parseTransform(String json) {
        try {
            return objectMapper.readValue(json, ImageTransform.class);
        } catch (JsonProcessingException error) {
            throw new WordflipException("VALIDATION_ERROR", "transform 不是合法 JSON");
        }
    }

    private String writeJson(ImageTransform transform) {
        try {
            return objectMapper.writeValueAsString(transform);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("图片变换序列化失败", error);
        }
    }

    private void validateTransform(ImageTransform transform) {
        if (transform.scale() != null && (transform.scale() < 0.2 || transform.scale() > 3.0)) {
            throw new WordflipException("VALIDATION_ERROR", "scale 须在 0.2～3 之间");
        }
    }

    private byte[] encodeToWebp(byte[] source) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                throw new WordflipException("VALIDATION_ERROR", "无法解析图片内容");
            }
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = rgb.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
            if (!writers.hasNext()) {
                throw new WordflipException("INTERNAL_ERROR", "服务端缺少 WebP 编码器");
            }
            ImageWriter writer = writers.next();
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                 ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(imageOutput);
                ImageWriteParam params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(0.85f);
                }
                writer.write(null, new IIOImage(rgb, null, null), params);
                imageOutput.flush();
                return output.toByteArray();
            } finally {
                writer.dispose();
            }
        } catch (WordflipException error) {
            throw error;
        } catch (IOException error) {
            throw new WordflipException("VALIDATION_ERROR", "图片转码 WebP 失败");
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException error) {
            throw new WordflipException("VALIDATION_ERROR", "读取上传文件失败");
        }
    }

    private static String normalizeContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int separator = value.indexOf(';');
        return separator < 0 ? value : value.substring(0, separator).trim();
    }
}
