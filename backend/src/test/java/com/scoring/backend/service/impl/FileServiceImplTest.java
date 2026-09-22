package com.scoring.backend.service.impl;

import com.scoring.backend.config.UploadProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileServiceImplTest {

    /** PNG 文件头魔数 89 50 4E 47 ... */
    private static final byte[] PNG_MAGIC_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @TempDir
    Path tempDirectory;

    @Test
    void uploadAvatar_shouldStoreImageAndReturnPublicUrl() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setDirectory(tempDirectory.toString());
        properties.setPublicBaseUrl("https://api.example.com/");
        FileServiceImpl service = new FileServiceImpl(properties);

        String url = service.uploadAvatar(new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                PNG_MAGIC_BYTES
        ));

        assertTrue(url.startsWith("https://api.example.com/uploads/avatars/"));
        try (var files = Files.list(tempDirectory.resolve("avatars"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void uploadAvatar_contentTypeMismatchesMagicBytes_shouldReject() {
        UploadProperties properties = new UploadProperties();
        properties.setDirectory(tempDirectory.toString());
        properties.setPublicBaseUrl("https://api.example.com/");
        FileServiceImpl service = new FileServiceImpl(properties);

        // 声明 image/png 但内容不是图片（伪造后缀 / 改名绕过 Content-Type）
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.uploadAvatar(
                new MockMultipartFile("file", "avatar.png", "image/png", "not-an-image".getBytes())));
        assertEquals("不支持的图片格式", ex.getMessage());
    }
}
