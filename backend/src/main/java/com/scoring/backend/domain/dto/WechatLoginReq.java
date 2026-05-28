package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;

public class WechatLoginReq {

    @NotBlank(message = "code不能为空")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
