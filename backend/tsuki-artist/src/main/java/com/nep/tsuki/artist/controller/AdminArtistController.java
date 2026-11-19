package com.nep.tsuki.artist.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nep.tsuki.artist.model.dto.ArtistCreateRequest;
import com.nep.tsuki.artist.model.vo.ArtistResponse;
import com.nep.tsuki.artist.service.ArtistService;
import com.nep.tsuki.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "管理端艺人接口")
@RestController
@RequestMapping("/api/v1/admin/artists")
@RequiredArgsConstructor
public class AdminArtistController {
    private final ArtistService artistService;

    /**
     * 新增艺人
     * 
     * @param request 新增艺人请求参数
     * @return 艺人响应
     */
    @Operation(summary = "新增艺人")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<ArtistResponse> create(@Valid @RequestBody ArtistCreateRequest request) {
        return ApiResponse.success(artistService.create(request));
    }
}
