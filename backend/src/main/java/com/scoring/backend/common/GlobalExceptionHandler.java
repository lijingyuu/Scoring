package com.scoring.backend.common;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return new ApiResponse<>(400, e.getMessage(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ApiResponse<Void> handleIllegalState(IllegalStateException e) {
        return new ApiResponse<>(500, e.getMessage(), null);
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
        return new ApiResponse<>(500, e.getMessage(), null);
    }
}
