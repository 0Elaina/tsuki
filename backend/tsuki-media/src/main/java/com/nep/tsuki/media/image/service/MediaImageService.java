package com.nep.tsuki.media.image.service;

import org.springframework.web.multipart.MultipartFile;

import com.nep.tsuki.media.image.model.vo.BoundImage;
import com.nep.tsuki.media.image.model.vo.MediaImageUploadResponse;

public interface MediaImageService {
    /**
     * 上传媒体图片文件
     *
     * @param file 待上传的图片文件
     * @return 图片上传结果响应
     */
    MediaImageUploadResponse upload(MultipartFile file);

    /**
     * 绑定可用的图片媒体资产
     *
     * @param assetId 媒体资产 ID
     * @return 绑定后的图片信息
     */
    BoundImage bindImage(long assetId);

    /**
     * 构建图片的公开访问 URL
     * 
     * @param relativePath 图片的相对路径
     * @return 图片的公开访问 URL
     */
    String buildPublicUrl(String relativePath);
}
