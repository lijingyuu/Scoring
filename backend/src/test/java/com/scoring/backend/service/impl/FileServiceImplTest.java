package com.scoring.backend.service.impl;

import com.scoring.backend.config.UploadProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileServiceImplTest {

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
                new byte[]{1, 2, 3}
        ));

        assertTrue(url.startsWith("https://api.example.com/uploads/avatars/"));
        try (var files = Files.list(tempDirectory.resolve("avatars"))) {
            assertEquals(1, files.count());
        }
    }
}
