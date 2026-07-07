package com.scoring.backend.engine;

import cn.hutool.core.util.IdUtil;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundRobinEngineTest {

    private final RoundRobinEngine engine = new RoundRobinEngine();

    @Test
    void generate_with4Players_shouldCreate6MatchesIn3Rounds() {
        List<MatchRecord> matches = engine.generateGroupMatches("T1", createGroupPlayers(1, 4));

        assertEquals(6, matches.size());
        assertEquals(3, matches.stream().map(MatchRecord::getRoundNum).distinct().count());
        assertAllPairsUnique(matches);
    }

    @Test
    void generate_with5Players_shouldCreate10MatchesIn5Rounds() {
        List<MatchRecord> matches = engine.generateGroupMatches("T1", createGroupPlayers(1, 5));

        assertEquals(10, matches.size());
        assertEquals(5, matches.stream().map(MatchRecord::getRoundNum).distinct().count());
        assertAllPairsUnique(matches);
    }

    @Test
    void generate_withTwoGroups_shouldKeepGroupNo() {
        List<Player> players = new ArrayList<>();
        players.addAll(createGroupPlayers(1, 4));
        players.addAll(createGroupPlayers(2, 4));

        List<MatchRecord> matches = engine.generateGroupMatches("T1", players);

        assertEquals(12, matches.size());
        assertEquals(6, matches.stream().filter(m -> m.getGroupNo() == 1).count());
        assertEquals(6, matches.stream().filter(m -> m.getGroupNo() == 2).count());
        assertTrue(matches.stream().allMatch(m -> m.getStageType() == 0));
    }

    // ==================== 新增边界用例 ====================

    @Test
    void generate_with3Players_shouldCreate3Matches() {
        List<MatchRecord> matches = engine.generateGroupMatches("T", createGroupPlayers(1, 3));

        // 3 players: 3 choose 2 = 3 matches
        assertEquals(3, matches.size());
        assertEquals(3, matches.stream().map(MatchRecord::getRoundNum).distinct().count());
        assertAllPairsUnique(matches);
        assertTrue(matches.stream().allMatch(m -> m.getStatus() == 0));
    }

    @Test
    void generate_with6Players_shouldCreate15Matches() {
        List<MatchRecord> matches = engine.generateGroupMatches("T", createGroupPlayers(1, 6));

        // 6 players: 6 choose 2 = 15 matches
        assertEquals(15, matches.size());
        assertAllPairsUnique(matches);
    }

    @Test
    void generate_allMatches_shouldHaveNoDuplicateIds() {
        List<MatchRecord> matches = engine.generateGroupMatches("T", createGroupPlayers(1, 5));
        long uniqueIds = matches.stream().map(MatchRecord::getId).distinct().count();
        assertEquals(matches.size(), uniqueIds);
    }

    @Test
    void generate_allMatches_shouldHaveNonNullRoundNumAndTournamentId() {
        List<MatchRecord> matches = engine.generateGroupMatches("T-ROUND", createGroupPlayers(1, 4));
        for (MatchRecord m : matches) {
            assertNotNull(m.getRoundNum(), "roundNum should not be null");
            assertEquals("T-ROUND", m.getTournamentId());
            assertEquals(Integer.valueOf(0), m.getStageType());
            assertEquals(0, m.getStatus());
        }
    }

    @Test
    void generate_matchesInEachRound_shouldBeEvenlyDistributed() {
        // With 4 teams, each round should have 2 matches (4/2 = 2)
        List<MatchRecord> matches = engine.generateGroupMatches("T", createGroupPlayers(1, 4));
        for (int round = 1; round <= 3; round++) {
            final int r = round;
            long count = matches.stream().filter(m -> m.getRoundNum() == r).count();
            assertEquals(2, count, "Round " + r + " should have exactly 2 matches");
        }
    }

    @Test
    void generate_withSinglePlayer_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.generateGroupMatches("T", createGroupPlayers(1, 1)));
    }

    @Test
    void generate_withTwoPlayers_shouldCreateOneMatch() {
        List<MatchRecord> matches = engine.generateGroupMatches("T", createGroupPlayers(1, 2));
        assertEquals(1, matches.size());
        assertEquals(Integer.valueOf(1), matches.get(0).getRoundNum());
        assertNotNull(matches.get(0).getLeftPlayerId());
        assertNotNull(matches.get(0).getRightPlayerId());
    }

    @Test
    void generate_emptyPlayerList_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.generateGroupMatches("T", new ArrayList<>()));
    }

    // ==================== generateLeagueMatches ====================

    @Test
    void generateLeague_singleRound4Players_shouldCreate6Matches() {
        List<Player> players = createPlayers(4);
        List<MatchRecord> matches = engine.generateLeagueMatches("T-LEAGUE", players, 1);

        // 4 players → C(4,2) = 6 matches
        assertEquals(6, matches.size());
        assertAllPairsUnique(matches);
        // All matches should have stageType=1 (non-group)
        assertTrue(matches.stream().allMatch(m -> m.getStageType() == 1));
        // All matches should have tournamentId set
        assertTrue(matches.stream().allMatch(m -> "T-LEAGUE".equals(m.getTournamentId())));
    }

    @Test
    void generateLeague_singleRound5Players_shouldCreate10Matches() {
        List<Player> players = createPlayers(5);
        List<MatchRecord> matches = engine.generateLeagueMatches("T-LEAGUE", players, 1);

        // 5 players → 10 matches (circle method, 5 rounds × 2 matches = 10)
        assertEquals(10, matches.size());
        assertAllPairsUnique(matches);
    }

    @Test
    void generateLeague_singleRound6Players_shouldCreate15Matches() {
        List<Player> players = createPlayers(6);
        List<MatchRecord> matches = engine.generateLeagueMatches("T-LEAGUE", players, 1);

        // 6 players → 15 matches
        assertEquals(15, matches.size());
        assertAllPairsUnique(matches);
    }

    @Test
    void generateLeague_doubleRound_shouldDoubleMatchCount() {
        List<Player> players = createPlayers(4);
        List<MatchRecord> matches = engine.generateLeagueMatches("T-LEAGUE", players, 2);

        // 4 players → single=6, double=12
        assertEquals(12, matches.size());

        // Each pair should appear exactly twice (home + away)
        Map<String, Long> pairCounts = new java.util.HashMap<>();
        for (MatchRecord m : matches) {
            String pair = m.getLeftPlayerId().compareTo(m.getRightPlayerId()) < 0
                    ? m.getLeftPlayerId() + ":" + m.getRightPlayerId()
                    : m.getRightPlayerId() + ":" + m.getLeftPlayerId();
            pairCounts.merge(pair, 1L, Long::sum);
        }
        for (Long count : pairCounts.values()) {
            assertEquals(2L, count, "each pair should appear exactly twice in double round robin");
        }
    }

    @Test
    void generateLeague_doubleRound_shouldSwapHomeAway() {
        List<Player> players = createPlayers(4);
        List<MatchRecord> matches = engine.generateLeagueMatches("T-LEAGUE", players, 2);

        // Find first pair's two matches and verify home/away is swapped
        String firstLeft = matches.get(0).getLeftPlayerId();
        String firstRight = matches.get(0).getRightPlayerId();

        // The same pair in the second half should have swapped sides
        boolean foundSwapped = false;
        for (int i = matches.size() / 2; i < matches.size(); i++) {
            if (matches.get(i).getLeftPlayerId().equals(firstRight)
                    && matches.get(i).getRightPlayerId().equals(firstLeft)) {
                foundSwapped = true;
                break;
            }
        }
        assertTrue(foundSwapped, "second round should swap home/away for each pair");
    }

    @Test
    void generateLeague_invalidRounds_shouldThrow() {
        List<Player> players = createPlayers(3);
        assertThrows(IllegalArgumentException.class,
                () -> engine.generateLeagueMatches("T", players, 0));
        assertThrows(IllegalArgumentException.class,
                () -> engine.generateLeagueMatches("T", players, 3));
    }

    @Test
    void generateLeague_tooFewPlayers_shouldThrow() {
        List<Player> onePlayer = createPlayers(1);
        assertThrows(IllegalArgumentException.class,
                () -> engine.generateLeagueMatches("T", onePlayer, 1));
    }

    @Test
    void generateLeague_emptyPlayers_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.generateLeagueMatches("T", new ArrayList<>(), 1));
    }

    @Test
    void generateLeague_roundNumbers_shouldBeConsecutive() {
        List<Player> players = createPlayers(6);
        List<MatchRecord> matches = engine.generateLeagueMatches("T-LEAGUE", players, 1);

        Set<Integer> roundNums = matches.stream()
                .map(MatchRecord::getRoundNum)
                .collect(java.util.stream.Collectors.toSet());
        // 6 players → 5 rounds, numbered 1-5
        assertEquals(5, roundNums.size());
        for (int r = 1; r <= 5; r++) {
            assertTrue(roundNums.contains(r), "round " + r + " should exist");
        }
    }

    @Test
    void generateLeague_allMatches_shouldHaveUniqueIds() {
        List<Player> players = createPlayers(5);
        List<MatchRecord> matches = engine.generateLeagueMatches("T-LEAGUE", players, 1);
        long uniqueIds = matches.stream().map(MatchRecord::getId).distinct().count();
        assertEquals(matches.size(), uniqueIds);
    }

    private List<Player> createPlayers(int count) {
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Player p = new Player();
            p.setId(IdUtil.simpleUUID());
            p.setName("P" + i);
            p.setSeedRank(i);
            players.add(p);
        }
        return players;
    }

    private List<Player> createGroupPlayers(int groupNo, int count) {
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Player player = new Player();
            player.setId(IdUtil.simpleUUID());
            player.setName("G" + groupNo + "P" + i);
            player.setGroupNo(groupNo);
            player.setGroupPosition(i);
            players.add(player);
        }
        return players;
    }

    private void assertAllPairsUnique(List<MatchRecord> matches) {
        Set<String> pairs = new HashSet<>();
        for (MatchRecord match : matches) {
            String a = match.getLeftPlayerId();
            String b = match.getRightPlayerId();
            String pair = a.compareTo(b) < 0 ? a + "-" + b : b + "-" + a;
            assertTrue(pairs.add(pair), "duplicated pair: " + pair);
        }
    }
}
