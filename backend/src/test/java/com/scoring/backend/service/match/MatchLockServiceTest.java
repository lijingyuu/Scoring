package com.scoring.backend.service.match;

import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.vo.MatchLockStatusVO;
import com.scoring.backend.mapper.MatchRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 锁状态只读探测（GET /matches/{id}/lock-status）的语义用例。
 *
 * 红线：探测不得抢锁、不得写库、不得加行锁；且不得下发持锁人 userId 与 lockToken 本身（仅 sameAccount / sameToken 布尔）。
 */
@ExtendWith(MockitoExtension.class)
class MatchLockServiceTest {

    private static final String MATCH_ID = "m-1";
    private static final String TOURNAMENT_ID = "t-1";
    private static final String CURRENT_USER = "user-1";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Mock
    private MatchRecordMapper matchRecordMapper;
    @Mock
    private MatchAccessGuard matchAccessGuard;

    private MatchLockService newService() {
        return new MatchLockService(matchRecordMapper, matchAccessGuard);
    }

    /** 锁空：无持锁人、token、过期时间 → 未锁定，前端不遮罩 */
    @Test
    void getMatchLockStatus_reportsUnlockedWhenLockEmpty() {
        MatchRecord match = match();
        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);

        MatchLockStatusVO vo = newService().getMatchLockStatus(CURRENT_USER, MATCH_ID, null);

        assertFalse(vo.isLocked());
        assertFalse(vo.isSameAccount());
        assertFalse(vo.isSameToken());
        assertNull(vo.getLockExpireTime());
        verify(matchAccessGuard).requireMatchOperator(CURRENT_USER, TOURNAMENT_ID);
    }

    /** 本账号持锁（未过期）：locked=true / sameAccount=true，供前端区分"自己另一台设备"与"他人"；
     *  sameToken 仅在请求头 token 与库内一致时为 true（缺失/异 token 视为换设备后的旧会话） */
    @Test
    void getMatchLockStatus_reportsSameAccountWhenHolderIsCurrentUser() {
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(75).withNano(0);
        MatchRecord match = match();
        match.setLockedByUserId(CURRENT_USER);
        match.setLockToken("token-1");
        match.setLockExpireTime(expireTime);
        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);

        MatchLockStatusVO vo = newService().getMatchLockStatus(CURRENT_USER, MATCH_ID, "token-1");

        assertTrue(vo.isLocked());
        assertTrue(vo.isSameAccount());
        assertTrue(vo.isSameToken(), "请求头 token 与库内一致 → 锁由本设备当前会话持有");
        assertEquals(expireTime.format(DATETIME_FORMATTER), vo.getLockExpireTime(),
                "lockExpireTime 需与 MatchLockVO.lockExpireTime 同格式（yyyy-MM-dd HH:mm:ss）");
        // 请求头缺失 / 异 token（换设备顶替后的旧会话）→ sameToken=false
        assertFalse(newService().getMatchLockStatus(CURRENT_USER, MATCH_ID, null).isSameToken());
        assertFalse(newService().getMatchLockStatus(CURRENT_USER, MATCH_ID, "token-2").isSameToken());
    }

    /** 他人持锁：locked=true / sameAccount=false / sameToken=false，且 VO 不暴露持锁人 ID */
    @Test
    void getMatchLockStatus_reportsLockedButNotSameAccountForOtherUser() {
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(30).withNano(0);
        MatchRecord match = match();
        match.setLockedByUserId("user-2");
        match.setLockToken("token-2");
        match.setLockExpireTime(expireTime);
        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);

        MatchLockStatusVO vo = newService().getMatchLockStatus(CURRENT_USER, MATCH_ID, "token-1");

        assertTrue(vo.isLocked());
        assertFalse(vo.isSameAccount());
        assertFalse(vo.isSameToken(), "本会话 token 与库内不同 → 非本设备持有");
        assertEquals(expireTime.format(DATETIME_FORMATTER), vo.getLockExpireTime());
    }

    /** 锁已过期：判活口径与 acquire 一致 → 未锁定，前端可直接抢锁（无需顶替确认）；sameToken 恒 false */
    @Test
    void getMatchLockStatus_reportsUnlockedWhenLockExpired() {
        MatchRecord match = match();
        match.setLockedByUserId(CURRENT_USER);
        match.setLockToken("token-1");
        match.setLockExpireTime(LocalDateTime.now().minusSeconds(5));
        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);

        MatchLockStatusVO vo = newService().getMatchLockStatus(CURRENT_USER, MATCH_ID, "token-1");

        assertFalse(vo.isLocked());
        assertFalse(vo.isSameAccount());
        assertFalse(vo.isSameToken(), "locked=false 时 sameToken 恒 false（即使 token 恰好一致）");
        assertNull(vo.getLockExpireTime());
    }

    /** 只读性：探测既不取 FOR UPDATE 行锁，也不写库 */
    @Test
    void getMatchLockStatus_neverTakesRowLockOrWrites() {
        MatchRecord match = match();
        match.setLockedByUserId("user-2");
        match.setLockToken("token-2");
        match.setLockExpireTime(LocalDateTime.now().plusSeconds(75));
        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);

        newService().getMatchLockStatus(CURRENT_USER, MATCH_ID, null);

        verify(matchRecordMapper, never()).selectByIdForUpdate(anyString());
        verify(matchRecordMapper, never()).updateById(any(MatchRecord.class));
        verify(matchRecordMapper, never()).update(any(), any());
    }

    private MatchRecord match() {
        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setTournamentId(TOURNAMENT_ID);
        return match;
    }
}
