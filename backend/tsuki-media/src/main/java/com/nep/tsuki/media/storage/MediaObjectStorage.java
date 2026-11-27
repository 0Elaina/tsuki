package com.nep.tsuki.media.storage;

import java.nio.file.Path;
import java.util.Collection;

import com.nep.tsuki.media.processing.MediaJobCode;

public interface MediaObjectStorage {
    /**
     * 从对象存储下载媒体文件到本地指定路径，并在下载过程中校验对象的一致性
     *
     * @param objectKey        对象存储中的文件 Key
     * @param expectedEtag     期望的对象 ETag
     * @param expectedByteSize 期望的文件字节大小
     * @param targetFile       本地目标文件路径
     * @return 下载结果状态码
     */
    MediaJobCode downloadToFile(String objectKey, String expectedEtag, long expectedByteSize, Path targetFile);

    /**
     * 将本地已校验的源文件归档上传到目标路径
     *
     * @param sourceKey   源对象存储 Key
     * @param sourceEtag  源对象 ETag
     * @param targetKey   归档目标对象存储 Key
     * @param localFile   已下载并校验通过的本地临时文件路径
     * @param contentType 媒体文件 MIME 类型
     * @param byteSize    本地文件精确字节大小
     * @return 上传成功后由服务端生成的目标对象 ETag
     */
    String archiveObject(
            String sourceKey,
            String sourceEtag,
            String targetKey,
            Path localFile,
            String contentType,
            long byteSize);

    /**
     * 将内存中的字节数组原子写入对象存储
     *
     * @param objectKey   目标对象存储 Key
     * @param content     待上传的非空字节数组
     * @param contentType 媒体文件 MIME 类型
     * @return 上传成功后由服务端生成的对象 ETag
     */
    String putObjectIfAbsent(String objectKey, byte[] content, String contentType);

    /**
     * 静默批量删除对象存储中的指定 Key 集合
     * 
     * @param objectKeys 待清理的对象存储 Key 集合
     */
    void deleteQuietly(Collection<String> objectKeys);
}
