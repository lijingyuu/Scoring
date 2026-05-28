package com.scoring.backend.service;

import com.scoring.backend.domain.vo.AuthLoginVO;

public interface AuthService {

    AuthLoginVO loginWithCode(String code);

    String verifyToken(String token);
}
