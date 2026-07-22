package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.exception.WordflipException;
import com.wordflip.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 学习卡图片上传边界测试。
 */
@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private MinioStorageService storage;

    @Test
    void rejectsNonImageMimeType() {
        var service = new ImageService(jdbc, storage, new ObjectMapper());
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> service.validateUpload(file))
                .isInstanceOf(WordflipException.class)
                .hasMessageContaining("image/jpeg");
    }
}
