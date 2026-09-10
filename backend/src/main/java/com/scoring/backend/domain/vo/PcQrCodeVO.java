package com.scoring.backend.domain.vo;

/**
 * PC 扫码登录：小程序码生成结果。
 */
public class PcQrCodeVO {

    /** 32 位十六进制票据，即小程序码 scene，同时作为轮询凭证 */
    private String ticket;

    /** 小程序码图片，data:image/png;base64,... 可直接放入 img src */
    private String qrImage;

    /** 票据有效期（秒） */
    private Integer expireSeconds;

    public PcQrCodeVO() {
    }

    public PcQrCodeVO(String ticket, String qrImage, Integer expireSeconds) {
        this.ticket = ticket;
        this.qrImage = qrImage;
        this.expireSeconds = expireSeconds;
    }

    public String getTicket() { return ticket; }
    public void setTicket(String ticket) { this.ticket = ticket; }
    public String getQrImage() { return qrImage; }
    public void setQrImage(String qrImage) { this.qrImage = qrImage; }
    public Integer getExpireSeconds() { return expireSeconds; }
    public void setExpireSeconds(Integer expireSeconds) { this.expireSeconds = expireSeconds; }
}
