package com.wordflip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.WordImageRepository;
import com.wordflip.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ImageService 上传校验单测：非法 MIME / 超大文件拒绝（mock MinIO）。
 */
@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private WordImageRepository wordImageRepository;
    @Mock
    private MinioStorageService minioStorageService;

    private ImageService imageService;

    @BeforeEach
    void setUp() {
        imageService = new ImageService(wordImageRepository, minioStorageService, new ObjectMapper());
    }

    @Test
    void validateUpload_rejectsInvalidMime() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> imageService.validateUpload(file))
                .isInstanceOf(WordflipException.class)
                .satisfies(ex -> {
                    WordflipException we = (WordflipException) ex;
                    assert we.getCode().equals("VALIDATION_ERROR");
                });
    }

    @Test
    void validateUpload_rejectsOversizedFile() {
        byte[] oversized = new byte[(int) ImageService.MAX_UPLOAD_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "big.jpg",
                "image/jpeg",
                oversized
        );

        assertThatThrownBy(() -> imageService.validateUpload(file))
                .isInstanceOf(WordflipException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void uploadOrReplace_rejectsInvalidMime_withoutCallingMinio() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "x.gif",
                "image/gif",
                new byte[]{1, 2, 3, 4}
        );

        assertThatThrownBy(() ->
                imageService.uploadOrReplace(1L, "Apple", file, "{\"rotate\":0,\"scale\":1}")
        ).isInstanceOf(WordflipException.class);

        verify(minioStorageService, never()).putObject(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(wordImageRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void uploadOrReplace_rejectsOversized_withoutCallingMinio() {
        byte[] oversized = new byte[(int) ImageService.MAX_UPLOAD_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "big.png",
                "image/png",
                oversized
        );

        assertThatThrownBy(() ->
                imageService.uploadOrReplace(1L, "banana", file, "{\"scale\":1}")
        ).isInstanceOf(WordflipException.class);

        verify(minioStorageService, never()).putObject(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
