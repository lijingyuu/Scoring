package com.scoring.backend.common;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 失败计数 + 锁定器：同一个 key 在统计窗口内累计失败达到阈值后锁定一个窗口，
 * 锁定期内 requireNotLocked 直接抛 400 语义异常。状态仅在内存持有，进程重启即清零
 * （与 RequestRateLimiter 同模式，适用于单实例部署的暴力破解防护）。
 */
public class FailureLockTracker {

    private static final int MAX_KEYS = 4096;

    private final int maxFailures;
    private final Duration window;
    private final Map<String, FailureRecord> records = new ConcurrentHashMap<>();

    public FailureLockTracker(int maxFailures, Duration window) {
        this.maxFailures = maxFailures;
        this.window = window;
    }

    /** 锁定中直接抛业务异常（400 语义），锁定文案与窗口时长保持一致。 */
    public void requireNotLocked(String key) {
        FailureRecord record = records.get(key);
        if (record != null && record.lockedUntil > System.currentTimeMillis()) {
            throw new IllegalArgumentException("尝试次数过多，请" + window.toMinutes() + "分钟后再试");
        }
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        records.compute(key, (ignored, existing) -> {
            FailureRecord record = existing;
            if (record == null || now - record.windowStartAt >= windowMillis) {
                record = new FailureRecord();
                record.windowStartAt = now;
            }
            record.count++;
            if (record.count >= maxFailures) {
                record.lockedUntil = now + windowMillis;
            }
            return record;
        });
        // 防止恶意构造 key 撑爆内存（同 RequestRateLimiter 的清理阈值）
        if (records.size() > MAX_KEYS) {
            records.entrySet().removeIf(entry -> now - entry.getValue().windowStartAt >= windowMillis * 2);
        }
    }

    /** 成功一次即清零该 key 的历史失败，避免跨次累计误锁。 */
    public void clear(String key) {
        records.remove(key);
    }

    private static class FailureRecord {
        private long windowStartAt;
        private long lockedUntil;
        private int count;
    }
}
