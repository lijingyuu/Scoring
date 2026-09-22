package com.scoring.backend.service.impl;

import com.scoring.backend.config.UploadProperties;
import com.scoring.backend.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    /** 魔数校验读取的字节数：足够覆盖 WEBP 的 RIFF....WEBP */
    private static final int MAGIC_PREFIX_LENGTH = 12;
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] RIFF_MAGIC = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MAGIC = {0x57, 0x45, 0x42, 0x50};

    private final UploadProperties uploadProperties;

    public FileServiceImpl(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像图片");
        }

        String extension = IMAGE_EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new IllegalArgumentException("头像仅支持 JPG、PNG 或 WEBP 图片");
        }
        // Content-Type 由客户端声明、可伪造，再按文件头魔数复核真实格式
        if (!hasImageMagicBytes(file)) {
            throw new IllegalArgumentException("不支持的图片格式");
        }

        try {
            Path avatarDirectory = Path.of(uploadProperties.getDirectory())
                    .toAbsolutePath()
                    .normalize()
                    .resolve("avatars");
            Files.createDirectories(avatarDirectory);

            String filename = UUID.randomUUID() + "." + extension;
            file.transferTo(avatarDirectory.resolve(filename));
            return trimTrailingSlash(uploadProperties.getPublicBaseUrl()) + "/uploads/avatars/" + filename;
        } catch (IOException e) {
            throw new IllegalStateException("头像上传失败", e);
        }
    }

    /** 读前 12 字节校验魔数：JPEG(FF D8 FF)、PNG(89 50 4E 47)、WEBP(RIFF....WEBP) */
    private boolean hasImageMagicBytes(MultipartFile file) {
        byte[] prefix = new byte[MAGIC_PREFIX_LENGTH];
        try (InputStream input = file.getInputStream()) {
            input.readNBytes(prefix, 0, MAGIC_PREFIX_LENGTH);
        } catch (IOException e) {
            throw new IllegalStateException("头像上传失败", e);
        }
        return startsWith(prefix, 0, JPEG_MAGIC)
                || startsWith(prefix, 0, PNG_MAGIC)
                || (startsWith(prefix, 0, RIFF_MAGIC) && startsWith(prefix, 8, WEBP_MAGIC));
    }

    private boolean startsWith(byte[] data, int offset, byte[] magic) {
        if (data.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceFirst("/+$", "");
    }
}
