package com.scoring.backend.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scoring.backend.config.AuthProperties;
import com.scoring.backend.config.WechatProperties;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.AuthLoginVO;
import com.scoring.backend.mapper.UserMapper;
import com.scoring.backend.service.AuthService;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final AuthProperties authProperties;
    private final WechatProperties wechatProperties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public AuthServiceImpl(UserMapper userMapper, AuthProperties authProperties, WechatProperties wechatProperties) {
        this.userMapper = userMapper;
        this.authProperties = authProperties;
        this.wechatProperties = wechatProperties;
        this.algorithm = Algorithm.HMAC256(resolveJwtSecret(authProperties));
        this.verifier = JWT.require(algorithm).build();
    }

    private String resolveJwtSecret(AuthProperties authProperties) {
        String jwtSecret = authProperties == null ? null : authProperties.getJwtSecret();
        if (StrUtil.isBlank(jwtSecret)) {
            throw new IllegalStateException("JWT secret must be configured");
        }
        return jwtSecret;
    }

    @Override
    public AuthLoginVO loginWithCode(String code) {
        if (StrUtil.isBlank(code)) {
            throw new IllegalArgumentException("code不能为空");
        }
        String openid = fetchOpenid(code);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname(null);
            user.setAvatarUrl(null);
            user.setProfileCompleted(false);
            userMapper.insert(user);
        }

        AuthLoginVO vo = new AuthLoginVO();
        vo.setToken(signToken(user.getId()));
        vo.setProfileCompleted(Boolean.TRUE.equals(user.getProfileCompleted()));
        return vo;
    }

    @Override
    public String verifyToken(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            String userId = jwt.getClaim("userId").asString();
            if (StrUtil.isBlank(userId)) {
                throw new IllegalArgumentException("无效token");
            }
            return userId;
        } catch (JWTVerificationException e) {
            throw new IllegalArgumentException("登录态已失效");
        }
    }

    private String signToken(String userId) {
        long expireSeconds = authProperties.getJwtExpireSeconds() == null ? 2592000L : authProperties.getJwtExpireSeconds();
        Date expireAt = new Date(System.currentTimeMillis() + expireSeconds * 1000);
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(expireAt)
                .sign(algorithm);
    }

    private String fetchOpenid(String code) {
        if (StrUtil.isBlank(wechatProperties.getAppId()) || StrUtil.isBlank(wechatProperties.getAppSecret())) {
            return "mock_" + code;
        }
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + wechatProperties.getAppId()
                + "&secret=" + wechatProperties.getAppSecret()
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        String body = HttpUtil.get(url);
        JSONObject json = JSONUtil.parseObj(body);
        String openid = json.getStr("openid");
        if (StrUtil.isBlank(openid)) {
            String error = json.getStr("errmsg");
            throw new IllegalArgumentException(StrUtil.isBlank(error) ? "微信登录失败" : error);
        }
        return openid;
    }
}
