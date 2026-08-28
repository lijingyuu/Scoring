package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;

public class MatchLockReq {

    @NotBlank(message = "lockToken cannot be blank")
    private String lockToken;

    public String getLockToken() { return lockToken; }
    public void setLockToken(String lockToken) { this.lockToken = lockToken; }
}
