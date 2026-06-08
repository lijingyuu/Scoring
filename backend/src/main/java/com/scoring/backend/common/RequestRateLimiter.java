package com.scoring.backend.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestRateLimiter {

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public boolean allow(String key, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        long windowMillis = Math.max(window.toMillis(), 1L);
        Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(now));

        synchronized (counter) {
            if (now - counter.windowStartAt >= windowMillis) {
                counter.windowStartAt = now;
                counter.count = 0;
            }
            if (counter.count >= limit) {
                return false;
            }
            counter.count++;
        }

        if (counters.size() > 4096) {
            cleanupExpired(now, windowMillis);
        }
        return true;
    }

    private void cleanupExpired(long now, long windowMillis) {
        counters.entrySet().removeIf(entry -> now - entry.getValue().windowStartAt >= windowMillis * 2);
    }

    private static class Counter {
        private long windowStartAt;
        private int count;

        private Counter(long windowStartAt) {
            this.windowStartAt = windowStartAt;
        }
    }
}
