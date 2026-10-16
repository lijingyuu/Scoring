package com.scoring.backend.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestRateLimiterTest {

    @Test
    void allow_withinLimit_shouldPass() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allow("login:1.1.1.1", 5, Duration.ofMinutes(1)));
        }
    }

    @Test
    void allow_overLimit_shouldDeny() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allow("login:2.2.2.2", 3, Duration.ofMinutes(1)));
        }
        assertFalse(limiter.allow("login:2.2.2.2", 3, Duration.ofMinutes(1)));
    }

    @Test
    void allow_keysAreIndependent() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        for (int i = 0; i < 3; i++) {
            limiter.allow("login:3.3.3.3", 3, Duration.ofMinutes(1));
        }
        assertFalse(limiter.allow("login:3.3.3.3", 3, Duration.ofMinutes(1)));
        assertTrue(limiter.allow("login:4.4.4.4", 3, Duration.ofMinutes(1)));
    }

    @Test
    void allow_zeroOrNegativeLimit_shouldAlwaysPass() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.allow("login:5.5.5.5", 0, Duration.ofMinutes(1)));
        }
    }

    /**
     * P1-1 回归：key 喷射攻击（每次请求换一个新 key）下内存必须有上界、且正常限流语义不退化。
     * 旧实现（裸 ConcurrentHashMap）在该场景下无界膨胀 OOM，且条目超 4096 后每个请求
     * 都同步全表遍历 removeIf（CPU 尖刺 + 锁争用）。
     */
    @Test
    void keySpray_memoryShouldStayBoundedAndLimiterStillWorks() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        int spray = RequestRateLimiter.MAX_TRACKED_KEYS * 2;
        for (int i = 0; i < spray; i++) {
            limiter.allow("spray:" + i, 3, Duration.ofMinutes(1));
        }
        limiter.cleanUp();
        assertTrue(limiter.estimatedSize() <= RequestRateLimiter.MAX_TRACKED_KEYS + 200,
                "喷射后跟踪 key 数应受上界约束，实际=" + limiter.estimatedSize());
        // 正常 key 的限流语义不受喷射影响
        assertTrue(limiter.allow("login:6.6.6.6", 2, Duration.ofMinutes(1)));
        assertTrue(limiter.allow("login:6.6.6.6", 2, Duration.ofMinutes(1)));
        assertFalse(limiter.allow("login:6.6.6.6", 2, Duration.ofMinutes(1)));
    }
}
