package com.scoring.backend.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FailureLockTrackerTest {

    @Test
    void recordFailure_belowThreshold_shouldNotLock() {
        FailureLockTracker tracker = new FailureLockTracker(5, Duration.ofMinutes(15));
        for (int i = 0; i < 4; i++) {
            tracker.recordFailure("admin@1.1.1.1");
            assertDoesNotThrow(() -> tracker.requireNotLocked("admin@1.1.1.1"));
        }
    }

    @Test
    void recordFailure_reachingThreshold_shouldLock() {
        FailureLockTracker tracker = new FailureLockTracker(5, Duration.ofMinutes(15));
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("admin@1.1.1.1");
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tracker.requireNotLocked("admin@1.1.1.1"));
        assertEquals("尝试次数过多，请15分钟后再试", ex.getMessage());
    }

    @Test
    void lock_isScopedToKey_notGlobal() {
        FailureLockTracker tracker = new FailureLockTracker(3, Duration.ofMinutes(15));
        for (int i = 0; i < 3; i++) {
            tracker.recordFailure("admin@attacker-ip");
        }
        assertThrows(IllegalArgumentException.class, () -> tracker.requireNotLocked("admin@attacker-ip"));
        // 复合 key 语义：同一账号、另一来源不受影响（P1-2 账号 DoS 防护的基础）
        assertDoesNotThrow(() -> tracker.requireNotLocked("admin@legit-user-ip"));
    }

    @Test
    void clear_shouldResetFailureCount() {
        FailureLockTracker tracker = new FailureLockTracker(3, Duration.ofMinutes(15));
        for (int i = 0; i < 3; i++) {
            tracker.recordFailure("admin@2.2.2.2");
        }
        tracker.clear("admin@2.2.2.2");
        assertDoesNotThrow(() -> tracker.requireNotLocked("admin@2.2.2.2"));
        tracker.recordFailure("admin@2.2.2.2");
        assertDoesNotThrow(() -> tracker.requireNotLocked("admin@2.2.2.2"));
    }

    /**
     * 旧实现缺陷回归：Map 超 4096 触发 removeIf 全表清理时，仍在锁定期内的 key 可能被
     * 提前摘除（清理条件按 windowStartAt，锁定后不再刷新）。
     * Caffeine 实现：未触发容量淘汰时（互异 key < maximumSize），锁定 key 完整存活到锁定期结束
     * （保留期 2×window > 锁定期 window）。注：攻击者喷射 >1 万互异 key 触发容量淘汰时，
     * 个别锁定记录可能被 fail-open 丢弃（与旧实现持平的降级，可再尝试但内存安全）。
     */
    @Test
    void lockedKey_shouldSurviveBelowCapacityPressure() {
        FailureLockTracker tracker = new FailureLockTracker(2, Duration.ofMinutes(15));
        tracker.recordFailure("victim@3.3.3.3");
        tracker.recordFailure("victim@3.3.3.3");
        assertThrows(IllegalArgumentException.class, () -> tracker.requireNotLocked("victim@3.3.3.3"));

        // 容量压力之内的一半 key（不触发 maximumSize 淘汰，但超过旧实现 4096 的清理阈值）
        for (int i = 0; i < FailureLockTracker.MAX_TRACKED_KEYS / 2; i++) {
            tracker.recordFailure("spray:" + i);
        }

        assertThrows(IllegalArgumentException.class,
                () -> tracker.requireNotLocked("victim@3.3.3.3"),
                "未触发容量淘汰时，锁定期内的 key 不应被清理");
    }
}
