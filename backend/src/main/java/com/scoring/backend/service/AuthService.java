package com.scoring.backend.service;

import com.scoring.backend.domain.vo.AuthLoginVO;
import com.scoring.backend.domain.dto.PasswordLoginReq;
import com.scoring.backend.domain.dto.RegisterReq;

public interface AuthService {

    AuthLoginVO loginWithCode(String code);

    AuthLoginVO register(RegisterReq req);

    AuthLoginVO loginWithPassword(PasswordLoginReq req);

    /** 按已有 userId 签发登录态（PC 扫码登录取走票据时用，与常规登录返回结构一致） */
    AuthLoginVO issueLogin(String userId);

    String verifyToken(String token);
}
