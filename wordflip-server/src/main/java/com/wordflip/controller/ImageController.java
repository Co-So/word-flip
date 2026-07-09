package com.wordflip.controller;

import com.wordflip.dto.media.ImageTransform;
import com.wordflip.dto.media.WordImageResponse;
import com.wordflip.security.SecurityUtils;
import com.wordflip.service.ImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 卡片图片 API：GET/POST/PATCH/DELETE /words/{wordKey}/image（P3-B02/B03）。
 * <p>主要错误码：NOT_FOUND（无图）、VALIDATION_ERROR（MIME/大小/transform）。
 */
@RestController
@RequestMapping("/api/v1/words/{wordKey}/image")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping
    public WordImageResponse get(@PathVariable String wordKey) {
        return imageService.get(SecurityUtils.getCurrentUserId(), wordKey);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WordImageResponse upload(
            @PathVariable String wordKey,
            @RequestPart("file") MultipartFile file,
            @RequestPart("transform") String transform
    ) {
        return imageService.uploadOrReplace(SecurityUtils.getCurrentUserId(), wordKey, file, transform);
    }

    @PatchMapping
    public WordImageResponse patchTransform(
            @PathVariable String wordKey,
            @RequestBody ImageTransform transform
    ) {
        return imageService.patchTransform(SecurityUtils.getCurrentUserId(), wordKey, transform);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String wordKey) {
        imageService.delete(SecurityUtils.getCurrentUserId(), wordKey);
    }
}
