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
    void resolve_trustedProxyShouldUseFirstForwardedForIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        assertEquals("203.0.113.10", ClientIpResolver.resolve(request, true));
    }
}
