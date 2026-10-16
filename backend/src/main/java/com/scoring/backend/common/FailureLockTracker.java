package com.scoring.backend.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

/**
 * 失败计数 + 锁定器：同一个 key 在统计窗口内累计失败达到阈值后锁定一个窗口，
 * 锁定期内 requireNotLocked 直接抛 400 语义异常。
 * <p>
 * key 维度由调用方决定（登录为 username+clientIp 复合，见 AuthServiceImpl——纯账号锁会被用来
 * DoS 合法用户，纯 IP 锁挡不住多 IP 爆破，复合维度是单实例场景的折中）。
 * <p>
 * 状态由 Caffeine 托管（maximumSize + expireAfterAccess(2×window)）：
 * - 内存有上界（10k 条），key 喷射下淘汰冷 key，不会无界膨胀（与 RequestRateLimiter 同模式）；
 * - 记录保留期（2×window）恒大于锁定期（window），仍在锁定期内的 key 不会被提前淘汰
 *   （修复旧实现「Map 超 4096 时 removeIf 可能摘除未到期锁」的缺陷）。
 * 状态仅在内存持有，进程重启即清零（单实例部署适用）。
 */
public class FailureLockTracker {

    /** 跟踪 key 的内存上界，超限淘汰最近最少使用的冷 key。 */
    static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Cache<String, FailureRecord> records;

    public FailureLockTracker(int maxFailures, Duration window) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.records = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterAccess(window.multipliedBy(2))
                .build();
    }

    /** 锁定中直接抛业务异常（400 语义），锁定文案与窗口时长保持一致。 */
    public void requireNotLocked(String key) {
        FailureRecord record = records.getIfPresent(key);
        if (record != null) {
            synchronized (record) {
                if (record.lockedUntil > System.currentTimeMillis()) {
                    throw new IllegalArgumentException("尝试次数过多，请" + window.toMinutes() + "分钟后再试");
                }
            }
        }
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        FailureRecord record = records.get(key, ignored -> new FailureRecord());
        synchronized (record) {
            if (now - record.windowStartAt >= windowMillis) {
                record.windowStartAt = now;
                record.count = 0;
            }
            record.count++;
            if (record.count >= maxFailures) {
                record.lockedUntil = now + windowMillis;
            }
        }
    }

    /** 成功一次即清零该 key 的历史失败，避免跨次累计误锁。 */
    public void clear(String key) {
        records.invalidate(key);
    }

    private static class FailureRecord {
        private long windowStartAt = System.currentTimeMillis();
        private long lockedUntil;
        private int count;
    }
}
