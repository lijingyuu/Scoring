package com.scoring.backend.service.match;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.scoring.backend.domain.dto.MatchLockReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.vo.MatchLockStatusVO;
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
            // 顶锁提示：同账号换设备接管了仍有效的旧锁（首次加锁/过期接管不算 kicked）
            MatchLockVO vo = buildLockVO(true, sameSession, userId, expireTime);
            vo.setKicked(!sameSession && isLockActive(match));
            return vo;
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
     * 只读探测锁状态（不抢锁、不加行锁、不写库）。
     * 供前端「抢锁前先确认」：判断锁是否有效、是否为本账号持有，据此决定遮罩文案与顶替确认。
     * 判活口径复用 {@link #isLockActive}（与 acquire 的锁空闲判定、requireActiveMatchLock 同源），
     * 因此 locked=false 时一定可以抢锁成功，避免"提示被占但其实能抢"的不一致。
     */
    public MatchLockStatusVO getMatchLockStatus(String userId, String matchId) {
        MatchRecord match = requireMatch(matchId);
        matchAccessGuard.requireMatchOperator(userId, match.getTournamentId());
        boolean locked = isLockActive(match);
        boolean sameAccount = locked && StrUtil.equals(match.getLockedByUserId(), userId);
        MatchLockStatusVO vo = new MatchLockStatusVO();
        vo.setLocked(locked);
        vo.setSameAccount(sameAccount);
        vo.setLockExpireTime(locked ? formatLockExpireTime(match.getLockExpireTime()) : null);
        return vo;
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

    /**
     * 是否存在未过期的有效锁持有者（restart 级联清理前的前置检查用，不做抛异常处理）。
     * 判活口径与 {@link #requireActiveMatchLock} 一致：token 非空且 expireTime 未过期。
     */
    public boolean isLockActive(MatchRecord match) {
        return match != null
                && StrUtil.isNotBlank(match.getLockedByUserId())
                && StrUtil.isNotBlank(match.getLockToken())
                && match.getLockExpireTime() != null
                && !match.getLockExpireTime().isBefore(LocalDateTime.now());
    }

    public void clearMatchLock(String matchId) {
        if (StrUtil.isBlank(matchId)) {
            return;
        }
        matchRecordMapper.update(
                null,
                new UpdateWrapper<MatchRecord>()
                        .eq("id", matchId)
                        .set("locked_by_user_id", null)
                        .set("lock_token", null)
                        .set("lock_expire_time", null)
        );
    }
    /** 非锁定读（不做 FOR UPDATE）：只读探测与权限校验用，避免把读请求卷进行锁排队。 */
    private MatchRecord requireMatch(String matchId) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        MatchRecord match = matchRecordMapper.selectById(matchId);
        if (match == null) {
            throw new IllegalArgumentException("match record not found: " + matchId);
        }
        return match;
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

    /** 锁过期时间序列化：与 buildLockVO 中 lockExpireTime 完全同源同格式（yyyy-MM-dd HH:mm:ss） */
    private String formatLockExpireTime(LocalDateTime lockExpireTime) {
        return lockExpireTime == null ? null : lockExpireTime.format(DATETIME_FORMATTER);
    }
}
