package com.scoring.backend.controller;

import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.mapper.MatchRecordMapper;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;

final class MatchLockTestSupport {

    private static final String MATCH_LOCK_TOKEN_HEADER = "X-Match-Lock-Token";

    private MatchLockTestSupport() {
    }

    static RequestPostProcessor withMatchLock(MatchRecordMapper matchRecordMapper, String matchId) {
        return withMatchLock(matchRecordMapper, matchId, "user-1");
    }

    static RequestPostProcessor withMatchLock(MatchRecordMapper matchRecordMapper, String matchId, String userId) {
        return request -> {
            String lockToken = "test-lock-" + matchId;
            MatchRecord update = new MatchRecord();
            update.setId(matchId);
            update.setLockedByUserId(userId);
            update.setLockToken(lockToken);
            update.setLockExpireTime(LocalDateTime.now().plusMinutes(5));
            matchRecordMapper.updateById(update);
            request.addHeader(MATCH_LOCK_TOKEN_HEADER, lockToken);
            return request;
        };
    }
}
