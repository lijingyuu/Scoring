package com.scoring.backend.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleException_shouldHideInternalMessage() {
        ApiResponse<Void> response = handler.handleException(new RuntimeException("sql syntax near secret_table"));

        assertEquals(500, response.getCode());
        assertEquals("系统异常，请稍后再试", response.getMessage());
    }

    @Test
    void handleIllegalState_shouldHideInternalMessage() {
        ApiResponse<Void> response = handler.handleIllegalState(new IllegalStateException("internal state detail"));

        assertEquals(500, response.getCode());
        assertEquals("系统异常，请稍后再试", response.getMessage());
    }

    @Test
    void handleIllegalArgument_shouldKeepBusinessMessage() {
        ApiResponse<Void> response = handler.handleIllegalArgument(new IllegalArgumentException("裁判密码错误"));

        assertEquals(400, response.getCode());
        assertEquals("裁判密码错误", response.getMessage());
    }

    @Test
    void handleConflict_shouldReturn409WithBusinessMessage() {
        ApiResponse<Void> response = handler.handleConflict(new ConflictException("事件序号与已有记录冲突，请刷新后重试（服务端最大序号 7）"));

        assertEquals(409, response.getCode());
        assertEquals("事件序号与已有记录冲突，请刷新后重试（服务端最大序号 7）", response.getMessage());
    }
}
