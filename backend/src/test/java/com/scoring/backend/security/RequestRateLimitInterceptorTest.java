package com.scoring.backend.security;

import com.scoring.backend.common.RequestRateLimiter;
import com.scoring.backend.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RequestRateLimitInterceptorTest {

    private RequestRateLimiter limiter;
    private RateLimitProperties properties;
    private RequestRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        limiter = mock(RequestRateLimiter.class);
        when(limiter.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setTrustProxyHeaders(false);
        interceptor = new RequestRateLimitInterceptor(limiter, properties);
    }

    @Test
    void disabled_shouldSkipAllLimiting() {
        properties.setEnabled(false);
        assertTrue(interceptor.preHandle(request("POST", "/api/v1/auth/password-login"), response(), null));
        verifyNoInteractions(limiter);
    }

    @Test
    void optionsRequest_shouldSkip() {
        assertTrue(interceptor.preHandle(request("OPTIONS", "/api/v1/auth/password-login"), response(), null));
        verifyNoInteractions(limiter);
    }

    @Test
    void loginPost_shouldUseLoginBucketKeyedByIp() {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/password-login");
        request.setRemoteAddr("1.2.3.4");
        assertTrue(interceptor.preHandle(request, response(), null));
        verify(limiter).allow(eq("login:1.2.3.4"), eq(30), any(Duration.class));
        verifyNoMoreInteractions(limiter);
    }

    @Test
    void writePost_shouldUseWriteBucketKeyedByIp() {
        MockHttpServletRequest request = request("POST", "/api/v1/match/score");
        request.setRemoteAddr("4.5.6.7");
        assertTrue(interceptor.preHandle(request, response(), null));
        verify(limiter).allow(eq("write:4.5.6.7"), eq(120), any(Duration.class));
        verifyNoMoreInteractions(limiter);
    }

    @Test
    void otherGet_shouldNotTouchAnyBucket() {
        MockHttpServletRequest request = request("GET", "/api/v1/tournaments");
        request.setRemoteAddr("3.4.5.6");
        assertTrue(interceptor.preHandle(request, response(), null));
        verifyNoInteractions(limiter);
    }

    /** P2-5：免登录的 PC 扫码轮询 GET 必须双维度限流（每 ticket + 每 IP） */
    @Test
    void pcStatusGet_shouldLimitPerTicketAndPerIp() {
        String ticket = "a".repeat(32);
        MockHttpServletRequest request = request("GET", "/api/v1/auth/pc/status");
        request.setRemoteAddr("2.3.4.5");
        request.addParameter("ticket", ticket);
        assertTrue(interceptor.preHandle(request, response(), null));
        verify(limiter).allow(eq("pcstatus-ticket:" + ticket), eq(60), any(Duration.class));
        verify(limiter).allow(eq("pcstatus-ip:2.3.4.5"), eq(240), any(Duration.class));
        verifyNoMoreInteractions(limiter);
    }

    @Test
    void pcStatusGet_withoutTicketParam_shouldStillLimitPerIp() {
        MockHttpServletRequest request = request("GET", "/api/v1/auth/pc/status");
        request.setRemoteAddr("2.3.4.5");
        assertTrue(interceptor.preHandle(request, response(), null));
        verify(limiter).allow(eq("pcstatus-ip:2.3.4.5"), eq(240), any(Duration.class));
        verifyNoMoreInteractions(limiter);
    }

    @Test
    void denied_shouldThrowTooManyRequests() {
        when(limiter.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        MockHttpServletRequest request = request("GET", "/api/v1/auth/pc/status");
        request.setRemoteAddr("5.6.7.8");
        request.addParameter("ticket", "b".repeat(32));
        assertThrows(TooManyRequestsException.class,
                () -> interceptor.preHandle(request, response(), null));
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }
}
