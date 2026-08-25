package com.scoring.backend.controller;

import com.scoring.backend.common.ApiResponse;
import com.scoring.backend.domain.dto.PasswordLoginReq;
import com.scoring.backend.domain.dto.RegisterReq;
import com.scoring.backend.domain.dto.UpdateProfileReq;
import com.scoring.backend.domain.dto.WechatLoginReq;
import com.scoring.backend.domain.vo.AuthLoginVO;
import com.scoring.backend.domain.vo.FileUploadVO;
import com.scoring.backend.domain.vo.UserProfileVO;
import com.scoring.backend.security.AuthGuard;
import com.scoring.backend.service.AuthService;
import com.scoring.backend.service.FileService;
import com.scoring.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final FileService fileService;
    private final AuthGuard authGuard;

    public AuthController(AuthService authService,
                          UserService userService,
                          FileService fileService,
                          AuthGuard authGuard) {
        this.authService = authService;
        this.userService = userService;
        this.fileService = fileService;
        this.authGuard = authGuard;
    }

    @PostMapping("/auth/wechat-login")
    public ApiResponse<AuthLoginVO> wechatLogin(@Valid @RequestBody WechatLoginReq req) {
        return ApiResponse.ok(authService.loginWithCode(req.getCode()));
    }

    @PostMapping("/auth/register")
    public ApiResponse<AuthLoginVO> register(@RequestBody RegisterReq req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/auth/password-login")
    public ApiResponse<AuthLoginVO> passwordLogin(@RequestBody PasswordLoginReq req) {
        return ApiResponse.ok(authService.loginWithPassword(req));
    }

    @PostMapping("/auth/profile")
    public ApiResponse<UserProfileVO> updateProfile(@Valid @RequestBody UpdateProfileReq req) {
        return ApiResponse.ok(userService.updateProfile(authGuard.requireUserId(), req));
    }

    @PostMapping("/files/avatars")
    public ApiResponse<FileUploadVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        authGuard.requireUserId();
        return ApiResponse.ok(new FileUploadVO(fileService.uploadAvatar(file)));
    }

    @GetMapping("/users/me")
    public ApiResponse<UserProfileVO> getCurrentProfile() {
        return ApiResponse.ok(userService.getCurrentProfile(authGuard.requireUserId()));
    }
}
