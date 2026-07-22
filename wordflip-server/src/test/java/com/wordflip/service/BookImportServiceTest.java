package com.wordflip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 私有词书预览解析测试。
 */
@ExtendWith(MockitoExtension.class)
class BookImportServiceTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    @Test
    void acceptsChineseDefinitionsAndEnglishOnlyRows() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        var file = new MockMultipartFile(
                "file", "my-book.txt", "text/plain", "apple,苹果\nbanana\n".getBytes()
        );

        var response = new BookImportService(jdbc, redis, new ObjectMapper()).preview(7L, file);

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.previewWords().get(0).cn()).isEqualTo("苹果");
        assertThat(response.previewWords().get(1).cn()).isEmpty();
        verify(values).set(anyString(), anyString(), any());
    }
}
