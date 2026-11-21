package com.nep.tsuki.media.image.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nep.tsuki.common.api.ApiResponse;
import com.nep.tsuki.media.image.model.vo.MediaImageUploadResponse;
import com.nep.tsuki.media.image.service.MediaImageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "管理端图片接口")
@RestController
@RequestMapping("/api/v1/admin/media/images")
@RequiredArgsConstructor
public class AdminMediaImageController {

    private final MediaImageService mediaImageService;

    /**
     * 上传图片
     * 
     * @param file 上传图片
     * @return 上传图片响应
     */
    @Operation(summary = "上传图片")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaImageUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(mediaImageService.upload(file));
    }
}
