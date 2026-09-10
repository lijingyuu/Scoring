package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PC 扫码登录票据提交（小程序端 scan / confirm 共用）。
 * ticket 即小程序码 scene：32 位十六进制。
 */
public class PcTicketReq {

    @NotBlank(message = "ticket不能为空")
    @Pattern(regexp = "^[0-9a-f]{32}$", message = "ticket格式不正确")
    private String ticket;

    public String getTicket() { return ticket; }
    public void setTicket(String ticket) { this.ticket = ticket; }
}
