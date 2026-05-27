package com.scoring.backend.engine;

import cn.hutool.core.util.IdUtil;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
