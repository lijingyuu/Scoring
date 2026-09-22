package com.scoring.backend.security;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * 解析客户端真实 IP。
     * trust=true 时只采信反代注入的 X-Real-IP（由本仓 nginx 模板以 $remote_addr 设置），
     * 刻意不读取 X-Forwarded-For：XFF 首段可被客户端伪造，取首段等于允许伪造来源绕过限流；
     * X-Real-IP 缺失时回退 remoteAddr（直连场景真实，反代未配置时退化为共享桶，不劣于 trust=false）。
     */
    public static String resolve(HttpServletRequest request, boolean trustProxyHeaders) {
        if (trustProxyHeaders) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
