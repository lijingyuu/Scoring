package com.scoring.backend.service;

import com.scoring.backend.domain.vo.AuthLoginVO;
import com.scoring.backend.domain.dto.PasswordLoginReq;
import com.scoring.backend.domain.dto.RegisterReq;

public interface AuthService {

    AuthLoginVO loginWithCode(String code);

    AuthLoginVO register(RegisterReq req);

    AuthLoginVO loginWithPassword(PasswordLoginReq req);

    String verifyToken(String token);
}
