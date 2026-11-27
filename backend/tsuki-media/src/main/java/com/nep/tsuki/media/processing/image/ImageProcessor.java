package com.nep.tsuki.media.processing.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nep.tsuki.media.model.enums.ImageContentType;
import com.nep.tsuki.media.processing.MediaJobCode;
import com.nep.tsuki.media.processing.MediaProcessResult;
import com.nep.tsuki.media.utils.MediaUtils;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

@Slf4j
@Component
public class ImageProcessor {
    private static final int MAX_DIMENSION_PX = 8192;
    private static final long MAX_PIXELS = 25_000_000L;

    private record ImageHeader(int width, int height) {
    }

    /**
     * 校验本地图像文件头格式、尺寸上限及完整像素可解码性（不做缩放与重编码）
     *
     * @param sourceFile       待校验的本地图像文件
     * @param expectedMimeType 预期的 MIME 类型，用于与文件头实际格式比对
     * @return 校验通过返回 {@link MediaJobCode#SUCCESS}，否则返回对应的业务错误码
     */
    public MediaJobCode validate(Path sourceFile, String expectedMimeType) {
        Objects.requireNonNull(sourceFile, "sourceFile 不能为空");
        if (!StringUtils.hasText(expectedMimeType)) {
            throw new IllegalArgumentException("expectedMimeType 不能为空");
        }
        return inspectImage(sourceFile, expectedMimeType, true).code();
    }

    /**
     * 对本地图像文件进行校验、EXIF 方向校正、等比缩放及重编码
     * 若缩放后的图像包含可见透明像素，则编码为 PNG；否则转换为 RGB 并按指定质量编码为 JPEG
     *
     * @param sourceFile       待处理的本地图像文件
     * @param expectedMimeType 预期的 MIME 类型，用于与文件头实际格式比对
     * @param maxEdgePx        输出图像长边的最大像素值（原图更小时不放大）
     * @param jpegQuality      JPEG 压缩质量，范围 (0, 1]
     * @return 处理成功返回编码后的图像数据与实际尺寸，失败返回对应的业务错误码
     */
    public MediaProcessResult<ImageProcessingResult> process(
            Path sourceFile,
            String expectedMimeType,
            int maxEdgePx,
            float jpegQuality) {
        // 基础入参校验
        Objects.requireNonNull(sourceFile, "sourceFile 不能为空");
        if (!StringUtils.hasText(expectedMimeType)) {
            throw new IllegalArgumentException("expectedMimeType 不能为空");
        }
        if (maxEdgePx <= 0) {
            throw new IllegalArgumentException("maxEdgePx 必须大于 0");
        }
        if (jpegQuality <= 0.0f || jpegQuality > 1.0f) {
            throw new IllegalArgumentException("jpegQuality 必须在 (0, 1] 范围内");
        }

        // 仅读取文件头校验格式与尺寸，避免在解码超大/非法图像时消耗内存
        MediaProcessResult<ImageHeader> headerResult = inspectImage(sourceFile, expectedMimeType, false);
        if (!headerResult.isSuccess()) {
            return MediaProcessResult.failure(headerResult.code());
        }
        ImageHeader header = headerResult.data();

        // 计算等比缩放系数，上限为 1.0（只缩小不放大）
        double scale = Math.min(1.0d, (double) maxEdgePx / Math.max(header.width, header.height));

        // 一次性完成图像解码、EXIF 方向自动校正与缩放
        BufferedImage processedImage;
        try {
            processedImage = Thumbnails
                    .of(sourceFile.toFile())
                    .scale(scale)
                    .useExifOrientation(true)
                    .asBufferedImage();
        } catch (IOException | RuntimeException ex) {
            log.warn("解码或缩放图像失败: expectedMimeType={}", expectedMimeType, ex);
            return MediaProcessResult.failure(MediaJobCode.INVALID_IMAGE);
        }

        if (processedImage == null || processedImage.getWidth() <= 0 || processedImage.getHeight() <= 0) {
            log.warn("解码结果图像尺寸无效: expectedMimeType={}", expectedMimeType);
            return MediaProcessResult.failure(MediaJobCode.INVALID_IMAGE);
        }

        // 根据是否存在可见透明像素选择输出格式：有透明保留 PNG，无透明转 JPEG 以减小体积
        if (hasVisibleAlpha(processedImage)) {
            byte[] pngBytes = encodePng(processedImage);
            return MediaProcessResult.success(new ImageProcessingResult(
                    ImageContentType.PNG,
                    pngBytes,
                    processedImage.getWidth(),
                    processedImage.getHeight()));
        }

        BufferedImage rgbImage = toRgbImage(processedImage);
        byte[] jpegBytes = encodeJpeg(rgbImage, jpegQuality);
        return MediaProcessResult.success(new ImageProcessingResult(
                ImageContentType.JPEG,
                jpegBytes,
                processedImage.getWidth(),
                processedImage.getHeight()));
    }

    /**
     * 读取图像文件头并校验格式与尺寸，按需执行完整帧解码以拦截损坏图像
     *
     * @param sourceFile       待校验的本地图像文件
     * @param expectedMimeType 预期的 MIME 类型，用于与文件实际格式比对
     * @param verifyFullDecode 是否进一步解码第 0 帧完整像素以确认文件未损坏
     * @return 校验通过返回图像宽高信息，失败返回对应的业务错误码
     */
    private MediaProcessResult<ImageHeader> inspectImage(
            Path sourceFile,
            String expectedMimeType,
            boolean verifyFullDecode) {
        try (ImageInputStream inputStream = ImageIO.createImageInputStream(sourceFile.toFile())) {
            if (inputStream == null) {
                log.warn("无法打开图像输入流: expectedMimeType={}", expectedMimeType);
                return MediaProcessResult.failure(MediaJobCode.INVALID_IMAGE);
            }

            // 根据文件头魔数匹配对应的图像解码器
            Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);
            if (!readers.hasNext()) {
                log.warn("无法识别的图像格式: expectedMimeType={}", expectedMimeType);
                return MediaProcessResult.failure(MediaJobCode.INVALID_IMAGE);
            }

            ImageReader reader = readers.next();
            try {
                // seekForwardOnly=true, ignoreMetadata=true：仅顺序读取且跳过元数据以减少开销
                reader.setInput(inputStream, true, true);

                // 校验文件实际格式是否在支持列表中，且与声明的 MIME 类型一致
                String formatName = reader.getFormatName();
                ImageContentType actualContentType = ImageContentType.fromFormatName(formatName);
                if (actualContentType == null
                        || !MediaUtils.isSameMediaType(expectedMimeType, actualContentType.getMimeType())) {
                    log.warn("图像实际格式与资产 MIME 不符: expected={}, formatName={}", expectedMimeType, formatName);
                    return MediaProcessResult.failure(MediaJobCode.INVALID_IMAGE);
                }

                // 读取第一帧宽高，拦截非法尺寸或超出阈值的“图片炸弹”
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    log.warn("图像宽高非法: width={}, height={}", width, height);
                    return MediaProcessResult.failure(MediaJobCode.INVALID_IMAGE);
                }

                if (width > MAX_DIMENSION_PX || height > MAX_DIMENSION_PX || (long) width * height > MAX_PIXELS) {
                    log.warn("图像尺寸超出限制: width={}, height={}", width, height);
                    return MediaProcessResult.failure(MediaJobCode.IMAGE_TOO_LARGE);
                }

                if (verifyFullDecode) {
                    BufferedImage decodedImage = reader.read(0);
                    if (decodedImage == null || decodedImage.getWidth() <= 0 || decodedImage.getHeight() <= 0) {
                        log.warn("图像完整解码结果无效: expectedMimeType={}", expectedMimeType);
                        return MediaProcessResult.failure(MediaJobCode.INVALID_IMAGE);
                    }
                }

                return MediaProcessResult.success(new ImageHeader(width, height));
            } finally {
                // 释放解码器底层资源
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            log.warn("读取或校验图像失败: expectedMimeType={}", expectedMimeType, ex);
            return MediaProcessResult.failure(MediaJobCode.INVALID_IMAGE);
        }
    }

    /**
     * 检查图片是否包含实际可见的透明像素
     * 即使图片带有 Alpha 通道，若所有像素均为完全不透明（alpha == 255），也返回 false
     */
    private boolean hasVisibleAlpha(BufferedImage image) {
        // 色彩模型本身不支持 Alpha 通道，直接返回 false
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        // 复用单行像素缓冲区，避免一次性分配整图数组占用过多内存
        int[] rowPixels = new int[width];

        for (int y = 0; y < height; y++) {
            // 逐行读取 ARGB 像素数据（每像素 32 位：高 8 位为 Alpha）
            image.getRGB(0, y, width, 1, rowPixels, 0, width);
            for (int argb : rowPixels) {
                // 无符号右移 24 位取出 Alpha 值，小于 0xFF (255) 表示存在半透明或全透明像素
                if ((argb >>> 24) < 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 将图片转换为不含 Alpha 通道的 RGB 格式（TYPE_INT_RGB）
     * 若原图包含透明区域，将以白色填充背景（常用于转存 JPEG 等不支持透明度的格式）
     */
    private BufferedImage toRgbImage(BufferedImage source) {
        // 已经是标准 RGB 格式，直接复用原图避免额外拷贝
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }

        // 创建同等尺寸、不含 Alpha 通道的目标画布
        BufferedImage rgbImage = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();

        try {
            // 绘制原图并用白色填充透明像素，避免透明区域变黑
            graphics.drawImage(source, 0, 0, Color.WHITE, null);
        } finally {
            // 及时释放绘图上下文资源
            graphics.dispose();
        }

        return rgbImage;
    }

    /**
     * 将图像编码为 PNG 格式字节数组（无损压缩，保留 Alpha 透明通道）
     */
    private byte[] encodePng(BufferedImage image) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // 写入内存流；若当前运行环境缺少对应编码器，ImageIO.write 会返回 false
            boolean written = ImageIO.write(image, ImageContentType.PNG.getExtension(), outputStream);
            if (!written) {
                throw new IllegalStateException("未找到可用的 PNG 图像编码器");
            }
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("编码 PNG 图像失败", ex);
        }
    }

    /**
     * 将不含 Alpha 通道的 RGB 图像按指定压缩质量编码为 JPEG 字节数组
     *
     * @param rgbImage 已转换为 TYPE_INT_RGB 的图像
     * @param quality  压缩质量，范围 (0, 1]，值越大画质越高、体积越大
     */
    private byte[] encodeJpeg(BufferedImage rgbImage, float quality) {
        // 获取 JPEG 编码器
        Iterator<ImageWriter> writers = ImageIO
                .getImageWritersByFormatName(ImageContentType.JPEG.name().toLowerCase(Locale.ROOT));
        if (!writers.hasNext()) {
            throw new IllegalStateException("未找到可用的 JPEG 图像编码器");
        }

        ImageWriter writer = writers.next();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // 将内存流包装为 ImageOutputStream，配合 try-with-resources 确保流关闭
            try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
                if (imageOutputStream == null) {
                    throw new IllegalStateException("无法创建 JPEG 图像输出流");
                }
                writer.setOutput(imageOutputStream);

                // 显式开启压缩模式并设置质量参数
                ImageWriteParam writeParam = writer.getDefaultWriteParam();
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(quality);

                // 执行编码写入并刷新缓冲，确保所有字节落入 ByteArrayOutputStream
                writer.write(null, new IIOImage(rgbImage, null, null), writeParam);
                imageOutputStream.flush();
            } catch (IOException ex) {
                throw new IllegalStateException("编码 JPEG 图像失败", ex);
            }
        } finally {
            // 释放编码器持有的底层资源，防止内存泄漏
            writer.dispose();
        }

        return outputStream.toByteArray();
    }

}
