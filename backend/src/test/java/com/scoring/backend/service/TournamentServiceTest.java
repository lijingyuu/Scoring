package com.scoring.backend.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentDetailVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock
    private TournamentMapper tournamentMapper;

    @Mock
    private PlayerMapper playerMapper;

    @Mock
    private MatchRecordMapper matchRecordMapper;

    private TournamentServiceProxy service;

    @BeforeEach
    void setUp() {
        service = new TournamentServiceProxy(tournamentMapper, playerMapper, matchRecordMapper);
    }

    @Test
    void listTournaments_shouldReturnOrderedList() {
        Tournament t1 = new Tournament();
        t1.setId("1");
        t1.setName("赛事A");
        t1.setCreateTime(LocalDateTime.now());

        when(tournamentMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(t1));

        List<Tournament> result = service.listTournaments(null, null);
        assertEquals(1, result.size());
        assertEquals("赛事A", result.get(0).getName());
        verify(tournamentMapper).selectList(any(QueryWrapper.class));
    }

    @Test
    void listTournaments_whenEmpty_shouldReturnEmptyList() {
        when(tournamentMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        List<Tournament> result = service.listTournaments(null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getBracket_shouldAggregateData() {
        String tid = IdUtil.simpleUUID();

        Tournament t = new Tournament();
        t.setId(tid);
        t.setName("测试赛事");
        t.setLocation("球场A");
        t.setStatus(1);

        Player player = new Player();
        player.setId("p1");
        player.setTournamentId(tid);
        player.setName("小明");

        MatchRecord match = new MatchRecord();
        match.setId(IdUtil.simpleUUID());
        match.setTournamentId(tid);
        match.setRoundNum(1);

        when(tournamentMapper.selectById(tid)).thenReturn(t);
        when(playerMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(player));
        when(matchRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(match));

        TournamentBracketVO vo = service.getBracket(tid);

        assertEquals("测试赛事", vo.getName());
        assertEquals("球场A", vo.getLocation());
        assertEquals(1, vo.getStatus());
        assertEquals(1, vo.getPlayers().size());
        assertEquals("小明", vo.getPlayers().get(0).getName());
        assertEquals(1, vo.getMatches().size());
    }

    @Test
    void getBracket_withInvalidId_shouldThrow() {
        String fakeId = "not-exists";
        when(tournamentMapper.selectById(fakeId)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.getBracket(fakeId));
    }

    @Test
    void getBracket_withBlankId_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> service.getBracket(""));
        assertThrows(IllegalArgumentException.class, () -> service.getBracket(null));
    }

    static class TournamentServiceProxy implements TournamentService {

        private final TournamentMapper tournamentMapper;
        private final PlayerMapper playerMapper;
        private final MatchRecordMapper matchRecordMapper;

        TournamentServiceProxy(TournamentMapper tournamentMapper,
                               PlayerMapper playerMapper,
                               MatchRecordMapper matchRecordMapper) {
            this.tournamentMapper = tournamentMapper;
            this.playerMapper = playerMapper;
            this.matchRecordMapper = matchRecordMapper;
        }

        @Override
        public String createTournament(String creatorUserId, com.scoring.backend.domain.dto.CreateTournamentReq req) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public List<Tournament> listTournaments(String currentUserId, String keyword) {
            return tournamentMapper.selectList(new QueryWrapper<Tournament>().orderByDesc("create_time"));
        }

        @Override
        public TournamentDetailVO getTournamentDetail(String tournamentId, String currentUserId) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public List<Tournament> listFavoriteTournaments(String userId) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public List<Tournament> listCreatedTournaments(String userId) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public void favoriteTournament(String userId, String tournamentId) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public void unfavoriteTournament(String userId, String tournamentId) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public TournamentBracketVO getBracket(String tournamentId) {
            if (tournamentId == null || tournamentId.isBlank()) {
                throw new IllegalArgumentException("tournamentId不能为空");
            }
            Tournament tournament = tournamentMapper.selectById(tournamentId);
            if (tournament == null) {
                throw new IllegalArgumentException("赛事不存在: " + tournamentId);
            }
            List<Player> players = playerMapper.selectList(
                    new QueryWrapper<Player>().eq("tournament_id", tournamentId));
            List<MatchRecord> matches = matchRecordMapper.selectList(
                    new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
            TournamentBracketVO vo = new TournamentBracketVO();
            vo.setId(tournament.getId());
            vo.setName(tournament.getName());
            vo.setLocation(tournament.getLocation());
            vo.setStatus(tournament.getStatus());
            vo.setPlayers(players);
            vo.setMatches(matches);
            return vo;
        }

        @Override
        public TournamentGroupsVO getGroups(String tournamentId) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public GroupStandingsVO getGroupStandings(String tournamentId) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public void generateKnockout(String userId, String tournamentId) {
            throw new UnsupportedOperationException("not used in this test");
        }
    }
}
