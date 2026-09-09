package com.scoring.backend.common;

import com.scoring.backend.security.TooManyRequestsException;
import com.scoring.backend.security.UnauthorizedException;
import com.scoring.backend.security.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常 → HTTP 状态码映射。
 *
 * 各分支必须用 @ResponseStatus 声明真实 HTTP 状态码：
 * 之前所有异常都以 HTTP 200 返回（错误码只在 body 里），访问日志无法区分成败，
 * 排查线上问题（例如执裁心跳/写接口被 403 拒绝）时完全不可见。
 * 前端（小程序 request / admin-web apiRequest）已兼容非 2xx：优先读取 body 里的业务 message。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_ERROR_MESSAGE = "系统异常，请稍后再试";

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return new ApiResponse<>(400, e.getMessage(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleIllegalState(IllegalStateException e) {
        log.error("illegal_state", e);
        return new ApiResponse<>(500, INTERNAL_ERROR_MESSAGE, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException e) {
        return new ApiResponse<>(401, e.getMessage(), null);
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(ForbiddenException e) {
        return new ApiResponse<>(403, e.getMessage(), null);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Void> handleTooManyRequests(TooManyRequestsException e) {
        return new ApiResponse<>(429, e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() == null
                ? "参数校验失败"
                : e.getBindingResult().getFieldError().getDefaultMessage();
        return new ApiResponse<>(400, msg, null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("unhandled_exception", e);
        return new ApiResponse<>(500, INTERNAL_ERROR_MESSAGE, null);
    }
}
