package com.scoring.backend.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 单机内存固定窗口限流器：同一 key 在窗口内超过 limit 次即拒绝。
 * 计数状态由 Caffeine 托管（maximumSize + expireAfterAccess）：
 * - 内存有上界（10k 条），key 喷射攻击下按 TinyLFU 淘汰冷 key，不再出现旧裸 ConcurrentHashMap 实现的
 *   无界膨胀 OOM 与「超 4096 条后每请求全表遍历」CPU 尖刺（见 docs/archive/审查与修复记录.md §4.6 P1-1）；
 * - 被持续访问的 key（含被拒绝的）不会过期，限流语义不因淘汰放宽；
 * - 极端淘汰仅意味着个别冷 key 的计数被丢弃（瞬时放宽一次窗口），不影响其他 key。
 */
@Component
public class RequestRateLimiter {

    /** 跟踪 key 的内存上界：正常赛事流量（数百在线 IP）远低于此；超限淘汰最近最少使用的冷 key。 */
    static final int MAX_TRACKED_KEYS = 10_000;

    /** 空闲 key 保留时长：调用方（拦截器）固定 1 分钟窗口，取 2 倍余量。 */
    private static final Duration IDLE_EXPIRY = Duration.ofMinutes(2);

    private final Cache<String, Counter> counters = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_KEYS)
            .expireAfterAccess(IDLE_EXPIRY)
            .build();

    public boolean allow(String key, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        long windowMillis = Math.max(window.toMillis(), 1L);
        Counter counter = counters.get(key, ignored -> new Counter(now));

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
        return true;
    }

    /** 当前跟踪的 key 数量（近似值；测试与运维观测用）。 */
    public long estimatedSize() {
        return counters.estimatedSize();
    }

    /** 触发一次同步清理（淘汰过期与超限条目）；测试与运维观测用。 */
    void cleanUp() {
        counters.cleanUp();
    }

    private static class Counter {
        private long windowStartAt;
        private int count;

        private Counter(long windowStartAt) {
            this.windowStartAt = windowStartAt;
        }
    }
}
