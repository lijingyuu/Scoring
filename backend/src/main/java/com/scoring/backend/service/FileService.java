package com.scoring.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    String uploadAvatar(MultipartFile file);
}
