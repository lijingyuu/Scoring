package com.scoring.backend.engine;

import cn.hutool.core.util.IdUtil;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BracketEngineTest {

    private final BracketEngine engine = new BracketEngine();

    private List<Player> createPlayers(int count) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Player p = new Player();
            p.setId(IdUtil.simpleUUID());
            p.setName("P" + (i + 1));
            players.add(p);
        }
        return players;
    }

    @Test
    void generate_with2Players_shouldCreateOneMatch() {
        List<MatchRecord> matches = engine.generateKnockoutBracket("T1", createPlayers(2));

        assertEquals(1, matches.size(), "2人应生成1场比赛");
        MatchRecord match = matches.get(0);
        assertEquals(1, match.getRoundNum());
        assertNotNull(match.getLeftPlayerId(), "左侧应有选手");
        assertNotNull(match.getRightPlayerId(), "右侧应有选手");
        assertNull(match.getNextMatchId(), "决赛应无下一场");
        assertEquals(0, match.getStatus(), "初始状态应为待赛");
    }

    @Test
    void generate_with3Players_shouldHandleByes() {
        List<MatchRecord> matches = engine.generateKnockoutBracket("T2", createPlayers(3));

        assertEquals(3, matches.size(), "3人应生成3场比赛");

        List<MatchRecord> round1 = filterRound(matches, 1);
        assertEquals(2, round1.size(), "首轮应有2场");

        long byeCount = round1.stream().filter(m -> m.getStatus() == 2).count();
        assertEquals(1, byeCount, "首轮应有1场轮空自动晋级");

        List<MatchRecord> round2 = filterRound(matches, 2);
        assertEquals(1, round2.size(), "次轮应为决赛");
        assertNull(round2.get(0).getNextMatchId(), "决赛应无下一场");
    }

    @Test
    void generate_with4Players_shouldHaveNoByes() {
        List<MatchRecord> matches = engine.generateKnockoutBracket("T3", createPlayers(4));

        assertEquals(3, matches.size(), "4人应生成3场比赛");

        List<MatchRecord> round1 = filterRound(matches, 1);
        assertEquals(2, round1.size(), "首轮应有2场");

        boolean hasBye = round1.stream().anyMatch(m -> m.getStatus() == 2);
        assertFalse(hasBye, "4人时应无轮空");

        String finalId = filterRound(matches, 2).get(0).getId();
        Set<String> nextMatchIds = round1.stream()
                .map(MatchRecord::getNextMatchId)
                .collect(Collectors.toSet());
        assertTrue(nextMatchIds.contains(finalId), "首轮胜者应指向决赛");
    }

    @Test
    void generate_with6Players_shouldCreateBracketAndCollapseByes() {
        List<Player> players = createPlayers(6);
        List<MatchRecord> matches = engine.generateKnockoutBracket("T100", players);

        assertEquals(7, matches.size(), "6人应生成7场比赛");

        List<MatchRecord> firstRound = filterRound(matches, 1);
        assertEquals(4, firstRound.size(), "首轮应有4场");

        long byeCollapsed = firstRound.stream().filter(m -> m.getStatus() == 2).count();
        assertEquals(2, byeCollapsed, "首轮应有2场自动轮空结束");

        List<MatchRecord> secondRound = filterRound(matches, 2);
        assertEquals(2, secondRound.size(), "次轮应有2场");

        Set<String> secondRoundPlayers = secondRound.stream()
                .flatMap(m -> Stream.of(m.getLeftPlayerId(), m.getRightPlayerId()))
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.toSet());

        assertTrue(secondRoundPlayers.size() >= 2, "轮空晋级选手应被填入第二轮");

        MatchRecord finalMatch = filterRound(matches, 3).get(0);
        assertNull(finalMatch.getNextMatchId(), "决赛nextMatchId必须为空");
        assertNull(finalMatch.getNextMatchSlot(), "决赛nextMatchSlot必须为空");
    }

    @Test
    void generate_with8Players_shouldCreateFullBracket() {
        List<MatchRecord> matches = engine.generateKnockoutBracket("T4", createPlayers(8));

        assertEquals(7, matches.size(), "8人应生成7场比赛");
        assertEquals(4, filterRound(matches, 1).size());
        assertEquals(2, filterRound(matches, 2).size());
        assertEquals(1, filterRound(matches, 3).size());

        boolean noBye = matches.stream().noneMatch(m -> m.getStatus() == 2);
        assertTrue(noBye, "8人时应无轮空（恰好2^3）");
    }

    @Test
    void generate_withInvalidTournamentId_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.generateKnockoutBracket("", createPlayers(2)));
    }

    @Test
    void generate_withEmptyPlayers_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.generateKnockoutBracket("T1", new ArrayList<>()));
    }

    @Test
    void allMatchesInBracket_shouldHaveUniqueIds() {
        List<MatchRecord> matches = engine.generateKnockoutBracket("T5", createPlayers(6));
        long uniqueCount = matches.stream().map(MatchRecord::getId).distinct().count();
        assertEquals(matches.size(), uniqueCount, "每场比赛ID应全局唯一");
    }

    @Test
    void bracketRoundCount_shouldBeLog2OfCapacity() {
        assertEquals(1, filterRound(engine.generateKnockoutBracket("T", createPlayers(2)), 1).size());
        assertEquals(2, filterRound(engine.generateKnockoutBracket("T", createPlayers(3)), 1).size());
        assertEquals(2, filterRound(engine.generateKnockoutBracket("T", createPlayers(4)), 1).size());
        assertEquals(4, filterRound(engine.generateKnockoutBracket("T", createPlayers(5)), 1).size());
    }

    private List<MatchRecord> filterRound(List<MatchRecord> matches, int round) {
        return matches.stream()
                .filter(m -> m.getRoundNum() == round)
                .collect(Collectors.toList());
    }
}
