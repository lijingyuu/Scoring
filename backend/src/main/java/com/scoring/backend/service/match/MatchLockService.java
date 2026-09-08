package com.scoring.backend.service.match;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.scoring.backend.domain.dto.MatchLockReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.vo.MatchLockVO;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.security.ForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 执裁会话锁（租约）服务：acquire / heartbeat / release 与锁校验。
 *
 * 语义红线（改动前必须与团队确认）：
 * - 锁租约 75s，前端心跳 15s，5 倍冗余是有意设计，两者需同步评估；
 * - acquire 允许"锁空闲"或"同一 userId 重取"，创建者抢占他人锁已被有意移除；
 * - sameSession 仅在 token + userId 双重匹配时为 true，前端据此区分重连与抢锁。
 */
@Service
public class MatchLockService {

    private static final long MATCH_LOCK_SECONDS = 75L;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MatchRecordMapper matchRecordMapper;
    private final MatchAccessGuard matchAccessGuard;

    public MatchLockService(MatchRecordMapper matchRecordMapper, MatchAccessGuard matchAccessGuard) {
        this.matchRecordMapper = matchRecordMapper;
        this.matchAccessGuard = matchAccessGuard;
    }

    @Transactional(rollbackFor = Exception.class)
    public MatchLockVO acquireMatchLock(String userId, String matchId, MatchLockReq req) {
        String lockToken = requireLockToken(req);
        MatchRecord match = requireMatchForUpdate(matchId);
        matchAccessGuard.requireMatchOperator(userId, match.getTournamentId());
        LocalDateTime now = LocalDateTime.now();
        boolean sameSession = StrUtil.equals(match.getLockToken(), lockToken)
                && StrUtil.equals(match.getLockedByUserId(), userId);

        if (isLockAvailable(match, now)
                || StrUtil.equals(match.getLockedByUserId(), userId)) {
            LocalDateTime expireTime = now.plusSeconds(MATCH_LOCK_SECONDS);
            MatchRecord update = new MatchRecord();
            update.setId(matchId);
            update.setLockedByUserId(userId);
            update.setLockToken(lockToken);
            update.setLockExpireTime(expireTime);
            matchRecordMapper.updateById(update);
            return buildLockVO(true, sameSession, userId, expireTime);
        }

        return buildLockVO(false, false, match.getLockedByUserId(), match.getLockExpireTime());
    }

    @Transactional(rollbackFor = Exception.class)
    public MatchLockVO heartbeatMatchLock(String userId, String matchId, MatchLockReq req) {
        String lockToken = requireLockToken(req);
        MatchRecord match = requireMatchForUpdate(matchId);
        matchAccessGuard.requireMatchOperator(userId, match.getTournamentId());
        if (!StrUtil.equals(match.getLockToken(), lockToken)
                || !StrUtil.equals(match.getLockedByUserId(), userId)) {
            return buildLockVO(false, false, match.getLockedByUserId(), match.getLockExpireTime());
        }

        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(MATCH_LOCK_SECONDS);
        MatchRecord update = new MatchRecord();
        update.setId(matchId);
        update.setLockExpireTime(expireTime);
        matchRecordMapper.updateById(update);
        return buildLockVO(true, true, userId, expireTime);
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseMatchLock(String userId, String matchId, MatchLockReq req) {
        String lockToken = requireLockToken(req);
        MatchRecord match = requireMatchForUpdate(matchId);
        matchAccessGuard.requireMatchOperator(userId, match.getTournamentId());
        if (StrUtil.equals(match.getLockToken(), lockToken)
                && StrUtil.equals(match.getLockedByUserId(), userId)) {
            clearMatchLock(matchId);
        }
    }

    /**
     * 写接口前置校验：当前请求必须持有未过期的有效锁，否则抛 ForbiddenException。
     */
    public void requireActiveMatchLock(MatchRecord match, String userId, String lockToken) {
        LocalDateTime now = LocalDateTime.now();
        if (match == null
                || StrUtil.isBlank(userId)
                || !StrUtil.equals(match.getLockedByUserId(), userId)
                || StrUtil.isBlank(lockToken)
                || !StrUtil.equals(match.getLockToken(), StrUtil.trim(lockToken))
                || match.getLockExpireTime() == null
                || match.getLockExpireTime().isBefore(now)) {
            throw new ForbiddenException("执裁会话已失效，请重新进入比赛");
        }
    }

    public void clearMatchLock(String matchId) {
        if (StrUtil.isBlank(matchId)) {
            return;
        }
        matchRecordMapper.update(
                null,
                new LambdaUpdateWrapper<MatchRecord>()
                        .eq(MatchRecord::getId, matchId)
                        .set(MatchRecord::getLockedByUserId, null)
                        .set(MatchRecord::getLockToken, null)
                        .set(MatchRecord::getLockExpireTime, null)
        );
    }

    private MatchRecord requireMatchForUpdate(String matchId) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        MatchRecord match = matchRecordMapper.selectByIdForUpdate(matchId);
        if (match == null) {
            throw new IllegalArgumentException("match record not found: " + matchId);
        }
        return match;
    }

    private String requireLockToken(MatchLockReq req) {
        if (req == null || StrUtil.isBlank(req.getLockToken())) {
            throw new IllegalArgumentException("lockToken cannot be blank");
        }
        return StrUtil.trim(req.getLockToken());
    }

    private boolean isLockAvailable(MatchRecord match, LocalDateTime now) {
        return match == null
                || StrUtil.isBlank(match.getLockedByUserId())
                || StrUtil.isBlank(match.getLockToken())
                || match.getLockExpireTime() == null
                || match.getLockExpireTime().isBefore(now);
    }

    private MatchLockVO buildLockVO(boolean success, boolean sameSession, String lockedByUserId, LocalDateTime lockExpireTime) {
        MatchLockVO vo = new MatchLockVO();
        vo.setSuccess(success);
        vo.setEditable(success);
        vo.setSameSession(sameSession);
        vo.setLockedByUserId(lockedByUserId);
        vo.setLockExpireTime(lockExpireTime == null ? null : lockExpireTime.format(DATETIME_FORMATTER));
        return vo;
    }
}
