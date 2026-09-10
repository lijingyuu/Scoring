package com.scoring.backend.service.wechat;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.scoring.backend.config.WechatProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 微信服务端 API access_token 管理。
 *
 * 使用官方推荐的稳定版接口（POST /cgi-bin/stable_token，普通模式）：
 * 有效期内重复调用返回同一 token，不会像旧版 /cgi-bin/token 那样互相顶掉。
 * 单实例部署，内存缓存 + synchronized 即可，无需中控。
 *
 * 注意：该接口受小程序后台「API IP 白名单」限制（错误码 40164），
 * 上线前必须把服务器出口 IP 加入公众平台-开发管理-开发设置-IP 白名单。
 */
@Component
public class WechatAccessTokenManager {

    private static final String STABLE_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";
    /** 提前刷新余量：官方有效期 7200s，提前 5 分钟刷新 */
    private static final long REFRESH_AHEAD_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final int HTTP_TIMEOUT_MILLIS = 10_000;

    private final WechatProperties wechatProperties;
    private final Environment environment;

    private volatile String cachedToken;
    private volatile long expireAtMillis;

    public WechatAccessTokenManager(WechatProperties wechatProperties, Environment environment) {
        this.wechatProperties = wechatProperties;
        this.environment = environment;
    }

    /**
     * 获取有效 access_token；未配置微信且处于 dev profile 时返回 mock 值（本地联调用）。
     */
    public synchronized String getToken() {
        if (StrUtil.isBlank(wechatProperties.getAppId()) || StrUtil.isBlank(wechatProperties.getAppSecret())) {
            if (!environment.acceptsProfiles(Profiles.of("dev"))) {
                throw new IllegalStateException("微信接口未配置");
            }
            return "mock_access_token";
        }

        long now = System.currentTimeMillis();
        if (StrUtil.isNotBlank(cachedToken) && now < expireAtMillis - REFRESH_AHEAD_MILLIS) {
            return cachedToken;
        }

        JSONObject req = JSONUtil.createObj()
                .set("grant_type", "client_credential")
                .set("appid", wechatProperties.getAppId())
                .set("secret", wechatProperties.getAppSecret())
                .set("force_refresh", false);
        String body = HttpRequest.post(STABLE_TOKEN_URL)
                .body(req.toString())
                .timeout(HTTP_TIMEOUT_MILLIS)
                .execute()
                .body();
        JSONObject json = JSONUtil.parseObj(body);
        String token = json.getStr("access_token");
        if (StrUtil.isBlank(token)) {
            throw new IllegalStateException("获取access_token失败：" + describeError(json));
        }
        Integer expiresIn = json.getInt("expires_in");
        cachedToken = token;
        expireAtMillis = now + (expiresIn == null ? TimeUnit.HOURS.toMillis(2) : expiresIn * 1000L);
        return cachedToken;
    }

    /**
     * 丢弃缓存 token（调用业务接口收到 40001/40014/42001 后强制刷新重试用）。
     */
    public synchronized void invalidate() {
        cachedToken = null;
        expireAtMillis = 0;
    }

    static String describeError(JSONObject json) {
        Integer errcode = json.getInt("errcode");
        String errmsg = json.getStr("errmsg");
        if (errcode == null) {
            return StrUtil.blankToDefault(errmsg, "未知错误");
        }
        if (errcode == 40164) {
            return "errcode=40164：服务器IP不在小程序后台IP白名单，请在公众平台-开发管理-开发设置中添加";
        }
        return "errcode=" + errcode + (StrUtil.isBlank(errmsg) ? "" : "：" + errmsg);
    }
}
