package com.nep.tsuki.media.image.processing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.nep.tsuki.common.api.ApiCode;
import com.nep.tsuki.common.exceptions.BusinessException;
import com.nep.tsuki.media.config.MediaImageProperties;
import com.nep.tsuki.media.constants.MediaApiCode;
import com.nep.tsuki.media.image.model.enums.ImageType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageProcessor {
    private final MediaImageProperties mediaImageProperties;

    /**
     * 校验并重编码上传的图片文件
     *
     * @param file 上传的图片文件
     * @return 处理后的图片字节、媒体类型、扩展名及实际宽高
     */
    public ProcessedImage process(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST);
        }

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            log.warn("读取上传图片字节失败: filename={}, size={}", file.getOriginalFilename(), file.getSize());
            throw new BusinessException(MediaApiCode.INVALID_IMAGE_FILE);
        }

        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            if (iis == null) {
                throw new BusinessException(ApiCode.UNSUPPORTED_MEDIA_TYPE);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new BusinessException(ApiCode.UNSUPPORTED_MEDIA_TYPE);
            }
            ImageReader reader = readers.next();

            try {
                // 通过文件头识别真实格式，校验是否在白名单内
                ImageType imageType = ImageType.fromLowerName(reader.getFormatName().toLowerCase(Locale.ROOT));

                reader.setInput(iis, false, true);

                // 拦截多帧动态 WebP
                if (imageType == ImageType.WEBP && reader.getNumImages(true) > 1) {
                    throw new BusinessException(ApiCode.UNSUPPORTED_MEDIA_TYPE, "不支持动态 Webp 图片");
                }

                // 仅读元数据校验宽高与总像素，防范解压缩炸弹（OOM）
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > mediaImageProperties.getMaxDimension()
                        || height > mediaImageProperties.getMaxDimension()
                        || (long) width * height > mediaImageProperties.getMaxPixels()) {
                    throw new BusinessException(MediaApiCode.INVALID_IMAGE_FILE);
                }

                // JPEG：按 EXIF 方向扶正并按配置质量重编码
                if (imageType == ImageType.JPG || imageType == ImageType.JPEG) {
                    BufferedImage rotated = Thumbnails.of(new ByteArrayInputStream(rawBytes))
                            .scale(1.0)
                            .useExifOrientation(true)
                            .asBufferedImage();

                    if (rotated == null) {
                        throw new BusinessException(MediaApiCode.INVALID_IMAGE_FILE);
                    }
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    Thumbnails.of(rotated)
                            .scale(1.0)
                            .outputFormat(ImageType.JPG.getLowerName())
                            .outputQuality(mediaImageProperties.getJpegQuality())
                            .toOutputStream(outputStream);

                    byte[] encodedBytes = outputStream.toByteArray();
                    if (encodedBytes.length == 0) {
                        throw new BusinessException(MediaApiCode.INVALID_IMAGE_FILE);
                    }
                    return ProcessedImage.builder()
                            .bytes(encodedBytes)
                            .contentType(imageType.getMediaType())
                            .extension(ImageType.JPG.getLowerName())
                            .width(rotated.getWidth())
                            .height(rotated.getHeight())
                            .build();
                }

                // PNG / WebP：完整解码后重编码
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new BusinessException(MediaApiCode.INVALID_IMAGE_FILE);
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                boolean written = ImageIO.write(decoded, ImageType.PNG.getLowerName(), outputStream);
                byte[] encodedBytes = outputStream.toByteArray();
                if (!written || encodedBytes.length == 0) {
                    throw new BusinessException(MediaApiCode.INVALID_IMAGE_FILE);
                }
                return ProcessedImage.builder()
                        .bytes(encodedBytes)
                        .contentType(ImageType.PNG.getMediaType())
                        .extension(ImageType.PNG.getLowerName())
                        .width(decoded.getWidth())
                        .height(decoded.getHeight())
                        .build();

            } finally {
                reader.dispose();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.warn("图片检查或重编码失败: filename={}, size={}", file.getOriginalFilename(), rawBytes.length, ex);
            throw new BusinessException(MediaApiCode.INVALID_IMAGE_FILE);
        }
    }
}
