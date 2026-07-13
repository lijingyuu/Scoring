package com.scoring.backend.common;

import com.scoring.backend.security.TooManyRequestsException;
import com.scoring.backend.security.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_ERROR_MESSAGE = "系统异常，请稍后再试";

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return new ApiResponse<>(400, e.getMessage(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ApiResponse<Void> handleIllegalState(IllegalStateException e) {
        log.error("illegal_state", e);
        return new ApiResponse<>(500, INTERNAL_ERROR_MESSAGE, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException e) {
        return new ApiResponse<>(401, e.getMessage(), null);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ApiResponse<Void> handleTooManyRequests(TooManyRequestsException e) {
        return new ApiResponse<>(429, e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() == null
                ? "参数校验失败"
                : e.getBindingResult().getFieldError().getDefaultMessage();
        return new ApiResponse<>(400, msg, null);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("unhandled_exception", e);
        return new ApiResponse<>(500, INTERNAL_ERROR_MESSAGE, null);
    }
}
