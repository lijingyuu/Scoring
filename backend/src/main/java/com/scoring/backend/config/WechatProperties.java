package com.scoring.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.wechat")
public class WechatProperties {

    private String appId;
    private String appSecret;
    /** 生成小程序码时是否校验 page 已发布（check_path）；灰度期可置 false 配合体验版 */
    private Boolean qrCheckPath = true;
    /** 小程序码目标版本：release 正式版 / trial 体验版 / develop 开发版 */
    private String qrEnvVersion = "release";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public Boolean getQrCheckPath() {
        return qrCheckPath;
    }

    public void setQrCheckPath(Boolean qrCheckPath) {
        this.qrCheckPath = qrCheckPath;
    }

    public String getQrEnvVersion() {
        return qrEnvVersion;
    }

    public void setQrEnvVersion(String qrEnvVersion) {
        this.qrEnvVersion = qrEnvVersion;
    }

    /** appId/appSecret 是否已配置（未配置时微信相关接口在 dev 下走 mock，生产下报错） */
    public boolean isConfigured() {
        return appId != null && !appId.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }
}
