package com.scoring.backend.service.impl;

import com.scoring.backend.config.UploadProperties;
import com.scoring.backend.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceFirst("/+$", "");
    }
}
