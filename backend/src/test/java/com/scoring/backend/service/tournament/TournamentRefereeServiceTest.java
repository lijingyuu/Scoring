package com.scoring.backend.service.tournament;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import com.scoring.backend.domain.dto.TournamentRefereeAuthReq;
import com.scoring.backend.domain.dto.UpdateTournamentRefereePasswordReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeConfig;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.mapper.MatchRecordMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentRefereeServiceTest {

    private static final String TOURNAMENT_ID = "t-referee-1";
    private static final String CREATOR_ID = "user-1";
    private static final String REFEREE_ID = "user-2";
    private static final String PASSWORD = "1234567890";
    private static final String ADMIN_PASSWORD = "9876543210";
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
    private MatchRecordMapper matchRecordMapper;
    @Mock
    private TournamentAccessGuard accessGuard;

    private TournamentRefereeService service;

    @BeforeEach
    void setUp() {
        service = new TournamentRefereeService(tournamentMapper, tournamentRefereeConfigMapper,
                tournamentRefereeGrantMapper, userMapper, matchRecordMapper, accessGuard);
    }

    @Test
    void authenticateReferee_bcryptHash_shouldVerifyAsScorer() {
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()), null);
        stubGrant();

        var vo = service.authenticateReferee(REFEREE_ID, TOURNAMENT_ID, authReq(PASSWORD));
        assertTrue(vo.getGranted());
        assertEquals(TournamentRefereeGrant.GRANT_ROLE_SCORER, vo.getGrantRole());
    }

    @Test
    void authenticateReferee_adminPassword_shouldGrantAdminRole() {
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()), BCrypt.hashpw(ADMIN_PASSWORD, BCrypt.gensalt()));

        var vo = service.authenticateReferee(REFEREE_ID, TOURNAMENT_ID, authReq(ADMIN_PASSWORD));
        assertTrue(vo.getGranted());
        assertEquals(TournamentRefereeGrant.GRANT_ROLE_ADMIN, vo.getGrantRole());
    }

    @Test
    void authenticateReferee_scorerGrantWithAdminPassword_shouldPromoteInPlace() {
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()), BCrypt.hashpw(ADMIN_PASSWORD, BCrypt.gensalt()));
        stubGrant(TournamentRefereeGrant.GRANT_ROLE_SCORER);

        var vo = service.authenticateReferee(REFEREE_ID, TOURNAMENT_ID, authReq(ADMIN_PASSWORD));
        assertEquals(TournamentRefereeGrant.GRANT_ROLE_ADMIN, vo.getGrantRole());

        ArgumentCaptor<TournamentRefereeGrant> captor = ArgumentCaptor.forClass(TournamentRefereeGrant.class);
        verify(tournamentRefereeGrantMapper).updateById(captor.capture());
        assertEquals(TournamentRefereeGrant.GRANT_ROLE_ADMIN, captor.getValue().getGrantRole());
    }

    @Test
    void authenticateReferee_adminGrantWithScorerPassword_shouldNotDemote() {
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()), BCrypt.hashpw(ADMIN_PASSWORD, BCrypt.gensalt()));
        stubGrant(TournamentRefereeGrant.GRANT_ROLE_ADMIN);

        var vo = service.authenticateReferee(REFEREE_ID, TOURNAMENT_ID, authReq(PASSWORD));
        assertEquals(TournamentRefereeGrant.GRANT_ROLE_ADMIN, vo.getGrantRole());
        verify(tournamentRefereeGrantMapper, never()).updateById(any(TournamentRefereeGrant.class));
    }

    @Test
    void authenticateReferee_legacySha256Hash_shouldVerifyAndUpgradeToBcrypt() {
        stubConfig(DigestUtil.sha256Hex(PASSWORD + LEGACY_SALT), null);
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
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()), BCrypt.hashpw(ADMIN_PASSWORD, BCrypt.gensalt()));

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
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()), null);

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

    @Test
    void updateRefereePassword_bothProvidedAndEqual_shouldReject() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setCreatorUserId(CREATOR_ID);
        when(accessGuard.requireTournament(TOURNAMENT_ID)).thenReturn(tournament);

        UpdateTournamentRefereePasswordReq req = new UpdateTournamentRefereePasswordReq();
        req.setPassword(PASSWORD);
        req.setAdminPassword(PASSWORD);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateRefereePassword(CREATOR_ID, TOURNAMENT_ID, req));
        assertEquals("两级密码不可相同", error.getMessage());
    }

    @Test
    void updateRefereePassword_neitherProvided_shouldReject() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setCreatorUserId(CREATOR_ID);
        when(accessGuard.requireTournament(TOURNAMENT_ID)).thenReturn(tournament);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateRefereePassword(CREATOR_ID, TOURNAMENT_ID, new UpdateTournamentRefereePasswordReq()));
        assertEquals("请至少提供现场执裁密码或赛务编排密码之一", error.getMessage());
    }

    @Test
    void updateRefereePassword_scorerPasswordCollidesWithStoredAdmin_shouldReject() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setCreatorUserId(CREATOR_ID);
        when(accessGuard.requireTournament(TOURNAMENT_ID)).thenReturn(tournament);
        stubConfig(null, BCrypt.hashpw(ADMIN_PASSWORD, BCrypt.gensalt()));

        UpdateTournamentRefereePasswordReq req = new UpdateTournamentRefereePasswordReq();
        req.setPassword(ADMIN_PASSWORD);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateRefereePassword(CREATOR_ID, TOURNAMENT_ID, req));
        assertEquals("两级密码不可相同", error.getMessage());
    }

    @Test
    void updateRefereePassword_scorerOnly_shouldRevokeOnlyScorerGrantsAndClearLocks() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setCreatorUserId(CREATOR_ID);
        when(accessGuard.requireTournament(TOURNAMENT_ID)).thenReturn(tournament);
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()), BCrypt.hashpw(ADMIN_PASSWORD, BCrypt.gensalt()));
        TournamentRefereeGrant scorer = new TournamentRefereeGrant();
        scorer.setTournamentId(TOURNAMENT_ID);
        scorer.setUserId(REFEREE_ID);
        scorer.setGrantRole(TournamentRefereeGrant.GRANT_ROLE_SCORER);
        when(tournamentRefereeGrantMapper.selectList(any())).thenReturn(java.util.List.of(scorer));

        UpdateTournamentRefereePasswordReq req = new UpdateTournamentRefereePasswordReq();
        req.setPassword("1122334455");
        service.updateRefereePassword(CREATOR_ID, TOURNAMENT_ID, req);

        // 仅吊销 SCORER 授权，并同步清理被撤权人的执裁锁
        verify(tournamentRefereeGrantMapper).delete(any());
        verify(matchRecordMapper).update(any(), any());
    }
    @Test
    void updateRefereePassword_configNullWithOnlyAdminPassword_shouldReject() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setCreatorUserId(CREATOR_ID);
        when(accessGuard.requireTournament(TOURNAMENT_ID)).thenReturn(tournament);
        when(tournamentRefereeConfigMapper.selectOne(any())).thenReturn(null);

        UpdateTournamentRefereePasswordReq req = new UpdateTournamentRefereePasswordReq();
        req.setAdminPassword(ADMIN_PASSWORD);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateRefereePassword(CREATOR_ID, TOURNAMENT_ID, req));
        assertEquals("首次配置裁判密码时，必须提供现场执裁密码", error.getMessage());
    }

    @Test
    void updateRefereePassword_legacySha256Collision_shouldReject() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setCreatorUserId(CREATOR_ID);
        when(accessGuard.requireTournament(TOURNAMENT_ID)).thenReturn(tournament);
        stubConfig(DigestUtil.sha256Hex(ADMIN_PASSWORD + LEGACY_SALT), null);

        UpdateTournamentRefereePasswordReq req = new UpdateTournamentRefereePasswordReq();
        req.setAdminPassword(ADMIN_PASSWORD);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateRefereePassword(CREATOR_ID, TOURNAMENT_ID, req));
        assertEquals("两级密码不可相同", error.getMessage());
    }

    @Test
    void authenticateReferee_concurrentDuplicateKey_shouldPromoteToAdminIfRequested() {
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()), BCrypt.hashpw(ADMIN_PASSWORD, BCrypt.gensalt()));

        TournamentRefereeGrant concurrentScorer = new TournamentRefereeGrant();
        concurrentScorer.setId("grant-1");
        concurrentScorer.setTournamentId(TOURNAMENT_ID);
        concurrentScorer.setUserId(REFEREE_ID);
        concurrentScorer.setGrantRole(TournamentRefereeGrant.GRANT_ROLE_SCORER);

        when(tournamentRefereeGrantMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(concurrentScorer);

        org.springframework.dao.DuplicateKeyException dupEx =
                new org.springframework.dao.DuplicateKeyException("Duplicate entry 't-referee-1-user-2' for key 'uk_referee_grant'");
        when(tournamentRefereeGrantMapper.insert(any(TournamentRefereeGrant.class))).thenThrow(dupEx);

        var vo = service.authenticateReferee(REFEREE_ID, TOURNAMENT_ID, authReq(ADMIN_PASSWORD));
        assertEquals(TournamentRefereeGrant.GRANT_ROLE_ADMIN, vo.getGrantRole());

        ArgumentCaptor<TournamentRefereeGrant> captor = ArgumentCaptor.forClass(TournamentRefereeGrant.class);
        verify(tournamentRefereeGrantMapper).updateById(captor.capture());
        assertEquals(TournamentRefereeGrant.GRANT_ROLE_ADMIN, captor.getValue().getGrantRole());
    }

    @Test
    void updateRefereePassword_scorerPasswordRevoke_shouldAlsoRevokeLegacyNullRoleGrants() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setCreatorUserId(CREATOR_ID);
        when(accessGuard.requireTournament(TOURNAMENT_ID)).thenReturn(tournament);
        stubConfig(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()), null);

        TournamentRefereeGrant legacyGrant = new TournamentRefereeGrant();
        legacyGrant.setTournamentId(TOURNAMENT_ID);
        legacyGrant.setUserId(REFEREE_ID);
        legacyGrant.setGrantRole(null);
        when(tournamentRefereeGrantMapper.selectList(any())).thenReturn(java.util.List.of(legacyGrant));

        UpdateTournamentRefereePasswordReq req = new UpdateTournamentRefereePasswordReq();
        req.setPassword("5566778899");
        service.updateRefereePassword(CREATOR_ID, TOURNAMENT_ID, req);

        verify(tournamentRefereeGrantMapper).delete(any());
        verify(matchRecordMapper).update(any(), any());
    }

    private void stubConfig(String storedHash, String adminHash) {
        TournamentRefereeConfig config = new TournamentRefereeConfig();
        config.setId("config-1");
        config.setTournamentId(TOURNAMENT_ID);
        config.setPasswordHash(storedHash);
        config.setAdminPasswordHash(adminHash);
        when(tournamentRefereeConfigMapper.selectOne(any())).thenReturn(config);
    }

    private void stubGrant() {
        stubGrant(TournamentRefereeGrant.GRANT_ROLE_SCORER);
    }

    private void stubGrant(String grantRole) {
        TournamentRefereeGrant grant = new TournamentRefereeGrant();
        grant.setTournamentId(TOURNAMENT_ID);
        grant.setUserId(REFEREE_ID);
        grant.setGrantRole(grantRole);
        when(tournamentRefereeGrantMapper.selectOne(any())).thenReturn(grant);
    }

    private TournamentRefereeAuthReq authReq(String password) {
        TournamentRefereeAuthReq req = new TournamentRefereeAuthReq();
        req.setPassword(password);
        return req;
    }
}
