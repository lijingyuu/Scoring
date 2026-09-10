package com.scoring.backend.service.wechat;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.scoring.backend.config.WechatProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/**
 * 微信小程序码客户端（POST /wxa/getwxacodeunlimit）。
 *
 * 接口特性（踩坑记录，改动前先核对官方文档）：
 * - scene 最大 32 个可见字符，仅支持数字/大小写字母及部分符号（不含 %），
 *   本项目 ticket 固定为 32 位十六进制，天然合规；
 * - page 不能携带参数（参数只能放 scene）；
 * - check_path=true 时 page 必须是已发布正式版存在的页面（否则 41030），
 *   灰度期可配置 app.wechat.qr-check-path=false + qr-env-version=trial 用体验版验证；
 * - 成功返回图片二进制（Content-Type: image/*），失败返回 JSON（含 errcode）；
 * - access_token 受小程序后台「API IP 白名单」限制（40164）。
 */
@Component
public class WechatQrCodeClient {

    public static final String PC_LOGIN_CONFIRM_PAGE = "pages/auth/pc-confirm";

    private static final String QR_CODE_URL = "https://api.weixin.qq.com/wxa/getwxacodeunlimit";
    private static final int HTTP_TIMEOUT_MILLIS = 10_000;
    private static final int QR_WIDTH = 430;
    /** token 类错误：失效后强制刷新重试一次 */
    private static final Set<Integer> TOKEN_ERROR_CODES = Set.of(40001, 40014, 42001);

    /**
     * 1x1 透明 PNG，dev 未配置微信时作为占位二维码返回，
     * 登录链路可继续走（ticket 真实存在，可通过接口直接 confirm）。
     */
    private static final byte[] DEV_PLACEHOLDER_PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private final WechatAccessTokenManager accessTokenManager;
    private final WechatProperties wechatProperties;
    private final Environment environment;

    public WechatQrCodeClient(WechatAccessTokenManager accessTokenManager,
                              WechatProperties wechatProperties,
                              Environment environment) {
        this.accessTokenManager = accessTokenManager;
        this.wechatProperties = wechatProperties;
        this.environment = environment;
    }

    /**
     * 生成 PC 扫码登录小程序码，scene 即 32 位 ticket。
     */
    public byte[] fetchLoginQrCode(String scene) {
        if (!wechatProperties.isConfigured()) {
            if (!environment.acceptsProfiles(Profiles.of("dev"))) {
                throw new IllegalStateException("微信接口未配置");
            }
            return DEV_PLACEHOLDER_PNG;
        }

        JSONObject req = JSONUtil.createObj()
                .set("scene", scene)
                .set("page", PC_LOGIN_CONFIRM_PAGE)
                .set("check_path", Boolean.TRUE.equals(wechatProperties.getQrCheckPath()))
                .set("env_version", StrUtil.blankToDefault(wechatProperties.getQrEnvVersion(), "release"))
                .set("width", QR_WIDTH);

        IllegalStateException lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String accessToken = accessTokenManager.getToken();
            try (HttpResponse response = HttpRequest.post(QR_CODE_URL + "?access_token=" + accessToken)
                    .body(req.toString())
                    .timeout(HTTP_TIMEOUT_MILLIS)
                    .execute()) {
                String contentType = StrUtil.blankToDefault(response.header("Content-Type"), "");
                byte[] bytes = response.bodyBytes();
                if (contentType.startsWith("image/")) {
                    return bytes;
                }
                JSONObject json = JSONUtil.parseObj(new String(bytes, StandardCharsets.UTF_8));
                Integer errcode = json.getInt("errcode");
                if (errcode != null && TOKEN_ERROR_CODES.contains(errcode) && attempt == 0) {
                    accessTokenManager.invalidate();
                    continue;
                }
                lastError = new IllegalStateException("获取小程序码失败：" + describeQrError(errcode, json.getStr("errmsg")));
            }
        }
        // 循环每轮要么 return 要么置 lastError，走到这里 lastError 必非空
        throw lastError;
    }

    private static String describeQrError(Integer errcode, String errmsg) {
        if (errcode == null) {
            return StrUtil.blankToDefault(errmsg, "未知错误");
        }
        return switch (errcode) {
            case 40164 -> WechatAccessTokenManager.ipWhitelistHint();
            case 41030 -> "errcode=41030：page 页面不存在，请确认小程序已发布包含登录确认页的版本（或临时将 qr-check-path 置为 false）";
            default -> "errcode=" + errcode + (StrUtil.isBlank(errmsg) ? "" : "：" + errmsg);
        };
    }
}
