package com.scoring.backend.service.tournament;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import com.scoring.backend.domain.dto.TournamentRefereeAuthReq;
import com.scoring.backend.domain.dto.UpdateTournamentRefereePasswordReq;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeConfig;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeConfigMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentRefereeServiceTest {

    private static final String TOURNAMENT_ID = "t-referee-1";
    private static final String CREATOR_ID = "user-1";
    private static final String REFEREE_ID = "user-2";
    private static final String PASSWORD = "1234567890";
    private static final String LEGACY_SALT = "tournament_referee_password";

    @Mock
    private TournamentMapper tournamentMapper;
    @Mock
    private TournamentRefereeConfigMapper tournamentRefereeConfigMapper;
    @Mock
    private TournamentRefereeGrantMapper tournamentRefereeGrantMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TournamentAccessGuard accessGuard;

    private TournamentRefereeService service;

    @BeforeEach
    void setUp() {
        service = new TournamentRefereeService(tournamentMapper, tournamentRefereeConfigMapper,
                tournamentRefereeGrantMapper, userMapper, accessGuard);
    }

    @Test
    void authenticateReferee_bcryptHash_shouldVerify() {
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()));
        stubGrant();

        assertTrue(service.authenticateReferee(REFEREE_ID, TOURNAMENT_ID, authReq(PASSWORD)).getGranted());
    }

    @Test
    void authenticateReferee_legacySha256Hash_shouldVerifyAndUpgradeToBcrypt() {
        stubConfig(DigestUtil.sha256Hex(PASSWORD + LEGACY_SALT));
        stubGrant();

        assertTrue(service.authenticateReferee(REFEREE_ID, TOURNAMENT_ID, authReq(PASSWORD)).getGranted());

        ArgumentCaptor<TournamentRefereeConfig> captor = ArgumentCaptor.forClass(TournamentRefereeConfig.class);
        verify(tournamentRefereeConfigMapper).updateById(captor.capture());
        String upgradedHash = captor.getValue().getPasswordHash();
        assertTrue(upgradedHash.startsWith("$2"), "升级后应为 BCrypt 哈希");
        assertTrue(BCrypt.checkpw(PASSWORD, upgradedHash));
    }

    @Test
    void authenticateReferee_fiveFailures_shouldLockFor15Minutes() {
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()));

        for (int i = 0; i < 5; i++) {
            IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                    () -> service.authenticateReferee(REFEREE_ID, TOURNAMENT_ID, authReq("0000000000")));
            assertEquals("裁判密码错误", wrong.getMessage());
        }

        // 第 6 次即使密码正确也被锁定拦截（锁 15 分钟）
        IllegalArgumentException locked = assertThrows(IllegalArgumentException.class,
                () -> service.authenticateReferee(REFEREE_ID, TOURNAMENT_ID, authReq(PASSWORD)));
        assertEquals("尝试次数过多，请15分钟后再试", locked.getMessage());
    }

    @Test
    void authenticateReferee_lockIsPerUser() {
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()));

        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.authenticateReferee(REFEREE_ID, TOURNAMENT_ID, authReq("0000000000")));
        }

        // 另一个用户不受该次数的锁定影响
        stubGrant();
        assertTrue(service.authenticateReferee(CREATOR_ID, TOURNAMENT_ID, authReq(PASSWORD)).getGranted());
    }

    @Test
    void updateRefereePassword_eightDigits_shouldBeAccepted() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setCreatorUserId(CREATOR_ID);
        when(accessGuard.requireTournament(TOURNAMENT_ID)).thenReturn(tournament);
        when(tournamentRefereeConfigMapper.selectOne(any())).thenReturn(null);

        UpdateTournamentRefereePasswordReq req = new UpdateTournamentRefereePasswordReq();
        req.setPassword("12345678");

        service.updateRefereePassword(CREATOR_ID, TOURNAMENT_ID, req);

        ArgumentCaptor<TournamentRefereeConfig> captor = ArgumentCaptor.forClass(TournamentRefereeConfig.class);
        verify(tournamentRefereeConfigMapper).insert(captor.capture());
        assertTrue(captor.getValue().getPasswordHash().startsWith("$2"), "8 位密码应以 BCrypt 落库");
    }

    @Test
    void updateRefereePassword_shortPassword_shouldReject() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setCreatorUserId(CREATOR_ID);
        when(accessGuard.requireTournament(TOURNAMENT_ID)).thenReturn(tournament);

        UpdateTournamentRefereePasswordReq req = new UpdateTournamentRefereePasswordReq();
        req.setPassword("12345");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateRefereePassword(CREATOR_ID, TOURNAMENT_ID, req));
        assertEquals("裁判密码必须不少于8位数字", error.getMessage());
    }

    private void stubConfig(String storedHash) {
        TournamentRefereeConfig config = new TournamentRefereeConfig();
        config.setId("config-1");
        config.setTournamentId(TOURNAMENT_ID);
        config.setPasswordHash(storedHash);
        when(tournamentRefereeConfigMapper.selectOne(any())).thenReturn(config);
    }

    private void stubGrant() {
        TournamentRefereeGrant grant = new TournamentRefereeGrant();
        grant.setTournamentId(TOURNAMENT_ID);
        grant.setUserId(REFEREE_ID);
        when(tournamentRefereeGrantMapper.selectOne(any())).thenReturn(grant);
    }

    private TournamentRefereeAuthReq authReq(String password) {
        TournamentRefereeAuthReq req = new TournamentRefereeAuthReq();
        req.setPassword(password);
        return req;
    }
}
