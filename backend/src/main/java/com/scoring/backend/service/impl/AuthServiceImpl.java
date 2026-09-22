package com.scoring.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scoring.backend.common.DuplicateKeySupport;
import com.scoring.backend.config.AuthProperties;
import com.scoring.backend.config.WechatProperties;
import com.scoring.backend.domain.dto.PasswordLoginReq;
import com.scoring.backend.domain.dto.RegisterReq;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.AuthLoginVO;
import com.scoring.backend.mapper.UserMapper;
import com.scoring.backend.security.UnauthorizedException;
import com.scoring.backend.service.AuthService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String USERNAME_PATTERN = "^[a-z0-9_]{3,32}$";
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final String WELL_KNOWN_DEFAULT_JWT_SECRET = "change-me-jwt-secret";

    private final UserMapper userMapper;
    private final AuthProperties authProperties;
    private final WechatProperties wechatProperties;
    private final Environment environment;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public AuthServiceImpl(UserMapper userMapper,
                           AuthProperties authProperties,
                           WechatProperties wechatProperties,
                           Environment environment) {
        this.userMapper = userMapper;
        this.authProperties = authProperties;
        this.wechatProperties = wechatProperties;
        this.environment = environment;
        this.algorithm = Algorithm.HMAC256(resolveJwtSecret(authProperties));
        this.verifier = JWT.require(algorithm).build();
    }

    private String resolveJwtSecret(AuthProperties authProperties) {
        String jwtSecret = authProperties == null ? null : authProperties.getJwtSecret();
        if (StrUtil.isBlank(jwtSecret)) {
            throw new IllegalStateException("JWT secret must be configured");
        }
        if (WELL_KNOWN_DEFAULT_JWT_SECRET.equals(jwtSecret) && !environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException(
                    "JWT secret is still the well-known default; tokens signed with it can be forged by anyone. "
                            + "Set a real JWT_SECRET (only --spring.profiles.active=dev may keep the default).");
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
            User created = new User();
            created.setOpenid(openid);
            created.setNickname(null);
            created.setAvatarUrl(null);
            created.setProfileCompleted(false);
            try {
                userMapper.insert(created);
                user = created;
            } catch (RuntimeException ex) {
                if (!DuplicateKeySupport.isDuplicateKey(ex)) {
                    throw ex;
                }
                // 并发首登：另一请求已建号（唯一键兜底），回查该用户走正常登录
                user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
                if (user == null) {
                    throw ex;
                }
            }
        }
        return buildLoginVO(user);
    }

    @Override
    public AuthLoginVO register(RegisterReq req) {
        if (!Boolean.TRUE.equals(authProperties.getRegistrationEnabled())) {
            throw new IllegalArgumentException("注册暂未开放");
        }
        String username = normalizeUsername(req == null ? null : req.getUsername());
        String password = req == null ? null : req.getPassword();
        String nickname = StrUtil.trim(req == null ? null : req.getNickname());
        validatePassword(password);
        if (StrUtil.isBlank(nickname) || nickname.length() > 64) {
            throw new IllegalArgumentException("昵称不能为空且不能超过64个字符");
        }

        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user = new User();
        user.setOpenid(null);
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setNickname(nickname);
        user.setAvatarUrl(null);
        user.setProfileCompleted(true);
        try {
            userMapper.insert(user);
        } catch (RuntimeException ex) {
            // 并发注册同名：唯一键兜底，转成幂等的业务拒绝语义（400）而不是 500
            if (DuplicateKeySupport.isDuplicateKey(ex)) {
                throw new IllegalArgumentException("用户名已被占用");
            }
            throw ex;
        }
        return buildLoginVO(user);
    }

    @Override
    public AuthLoginVO loginWithPassword(PasswordLoginReq req) {
        String username = normalizeUsername(req == null ? null : req.getUsername());
        String password = req == null ? null : req.getPassword();
        if (StrUtil.isBlank(password)) {
            throw new IllegalArgumentException("密码不能为空");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || StrUtil.isBlank(user.getPasswordHash()) || !BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return buildLoginVO(user);
    }

    @Override
    public AuthLoginVO issueLogin(String userId) {
        if (StrUtil.isBlank(userId)) {
            throw new IllegalArgumentException("用户不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return buildLoginVO(user);
    }

    @Override
    public String verifyToken(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            String userId = jwt.getClaim("userId").asString();
            if (StrUtil.isBlank(userId)) {
                // token 结构合法但缺少 userId：属于登录态失效，抛 401 语义异常
                throw new UnauthorizedException("登录态已失效，请重新登录");
            }
            return userId;
        } catch (JWTVerificationException e) {
            // 过期/伪造/签名不符：统一抛 401 语义异常（GlobalExceptionHandler 映射为 HTTP 401）
            throw new UnauthorizedException("登录态已失效，请重新登录");
        }
    }

    private AuthLoginVO buildLoginVO(User user) {
        AuthLoginVO vo = new AuthLoginVO();
        vo.setToken(signToken(user.getId()));
        vo.setProfileCompleted(Boolean.TRUE.equals(user.getProfileCompleted()));
        return vo;
    }

    private String signToken(String userId) {
        long expireSeconds = authProperties.getJwtExpireSeconds() == null ? 2592000L : authProperties.getJwtExpireSeconds();
        Date expireAt = new Date(System.currentTimeMillis() + expireSeconds * 1000);
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(expireAt)
                .sign(algorithm);
    }

    private String normalizeUsername(String username) {
        String normalized = StrUtil.trim(username);
        if (StrUtil.isBlank(normalized)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!normalized.matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException("用户名只能包含3-32位小写字母、数字或下划线");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (StrUtil.isBlank(password)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.length() < 6 || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码长度必须为6-72位");
        }
    }

    private String fetchOpenid(String code) {
        if (StrUtil.isBlank(wechatProperties.getAppId()) || StrUtil.isBlank(wechatProperties.getAppSecret())) {
            if (!environment.acceptsProfiles(Profiles.of("dev"))) {
                throw new IllegalStateException("微信登录未配置");
            }
            return "mock_" + code;
        }
        // code 由微信下发，可能含 +/&/= 等字符，不编码会截断查询串
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + wechatProperties.getAppId()
                + "&secret=" + wechatProperties.getAppSecret()
                + "&js_code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code";
        // 微信接口超时：5 秒（避免登录请求被网络问题长期挂住）
        String body = HttpUtil.get(url, 5000);
        JSONObject json = JSONUtil.parseObj(body);
        String openid = json.getStr("openid");
        if (StrUtil.isBlank(openid)) {
            String error = json.getStr("errmsg");
            throw new IllegalArgumentException(StrUtil.isBlank(error) ? "微信登录失败" : error);
        }
        return openid;
    }
}
