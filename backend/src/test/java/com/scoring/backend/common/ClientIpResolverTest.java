package com.scoring.backend.common;

import com.scoring.backend.security.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    @Test
    void resolve_defaultShouldIgnoreProxyHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        request.addHeader("X-Real-IP", "203.0.113.11");

        assertEquals("10.0.0.8", ClientIpResolver.resolve(request, false));
    }

    @Test
    void resolve_trustedProxyShouldPreferRealIpOverForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        // 客户端伪造的 XFF 与反代注入的 X-Real-IP 同时存在时，只采信反代可控的 X-Real-IP
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.11");

        assertEquals("203.0.113.11", ClientIpResolver.resolve(request, true));
    }

    @Test
    void resolve_trustedProxyShouldIgnoreForwardedForAlone() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        // 没有反代注入的 X-Real-IP 时，XFF（可能整条由客户端伪造）不可采信，回退 remoteAddr
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        assertEquals("10.0.0.8", ClientIpResolver.resolve(request, true));
    }

    @Test
    void resolve_trustedProxyShouldTrimRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Real-IP", "  203.0.113.11 ");

        assertEquals("203.0.113.11", ClientIpResolver.resolve(request, true));
    }

    @Test
    void resolve_trustedProxyShouldFallBackWhenRealIpBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Real-IP", "   ");

        assertEquals("10.0.0.8", ClientIpResolver.resolve(request, true));
    }
}
