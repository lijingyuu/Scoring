package com.scoring.backend.service;

import com.scoring.backend.domain.vo.AuthLoginVO;
import com.scoring.backend.domain.dto.PasswordLoginReq;
import com.scoring.backend.domain.dto.RegisterReq;

public interface AuthService {

    AuthLoginVO loginWithCode(String code);

    AuthLoginVO register(RegisterReq req);

     /**
      * 密码登录。clientIp 参与失败锁定的 key 维度（username+IP 复合）：
      * 防止攻击者用纯账号锁把合法用户挡在门外（DoS），由 Controller 解析后传入。
      */
     AuthLoginVO loginWithPassword(PasswordLoginReq req, String clientIp);

    /** 按已有 userId 签发登录态（PC 扫码登录取走票据时用，与常规登录返回结构一致） */
    AuthLoginVO issueLogin(String userId);

    String verifyToken(String token);
}
