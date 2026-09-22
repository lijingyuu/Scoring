package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateProfileReq {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称最长64个字符")
    private String nickname;

    @NotBlank(message = "头像不能为空")
    @Size(max = 512, message = "头像地址过长")
    private String avatarUrl;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
