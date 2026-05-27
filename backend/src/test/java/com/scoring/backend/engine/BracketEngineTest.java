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

    private Player createSeededPlayer(String name, int seed) {
        Player p = new Player();
        p.setId(IdUtil.simpleUUID());
        p.setName(name);
        p.setSeedRank(seed);
        return p;
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
        assertEquals(0, match.getMatchIndex(), "单场比赛matchIndex应为0");
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

    // ─── match_index 排序测试 ───

    @Test
    void matchIndex_shouldBeConsecutiveWithinEachRound() {
        List<MatchRecord> matches = engine.generateKnockoutBracket("T", createPlayers(8));

        for (int round = 1; round <= 3; round++) {
            List<MatchRecord> roundMatches = filterRound(matches, round);
            List<Integer> indices = roundMatches.stream()
                    .map(MatchRecord::getMatchIndex)
                    .sorted()
                    .collect(Collectors.toList());

            for (int i = 0; i < indices.size(); i++) {
                assertEquals(Integer.valueOf(i), indices.get(i),
                        "第" + round + "轮的matchIndex应从0连续递增");
            }
        }
    }

    @Test
    void adjacentMatchesInRound1_shouldFeedSameParent() {
        List<MatchRecord> matches = engine.generateKnockoutBracket("T", createPlayers(8));
        List<MatchRecord> round1 = filterRound(matches, 1).stream()
                .sorted(java.util.Comparator.comparingInt(MatchRecord::getMatchIndex))
                .collect(Collectors.toList());

        // matchIndex 0 和 1 应指向同一个父比赛
        assertEquals(round1.get(0).getNextMatchId(), round1.get(1).getNextMatchId());
        // matchIndex 2 和 3 应指向同一个父比赛
        assertEquals(round1.get(2).getNextMatchId(), round1.get(3).getNextMatchId());
        // 但两组不应指向同一个
        assertNotEquals(round1.get(0).getNextMatchId(), round1.get(2).getNextMatchId());
    }

    // ─── 种子机制测试 ───

    @Test
    void seededPlayers_shouldBePlacedAtCorrectSlots() {
        // 8人，p=8，seedOrder=[1,8,4,5,2,7,3,6]
        // 1号种子应在slot[0]，2号种子应在slot[4]
        List<Player> players = new ArrayList<>();
        players.add(createSeededPlayer("一号种子", 1));
        players.add(createSeededPlayer("二号种子", 2));
        // 其余6名无种子
        for (int i = 3; i <= 8; i++) {
            Player p = new Player();
            p.setId(IdUtil.simpleUUID());
            p.setName("P" + i);
            players.add(p);
        }

        List<MatchRecord> matches = engine.generateKnockoutBracket("T", players);
        List<MatchRecord> round1 = filterRound(matches, 1).stream()
                .sorted(java.util.Comparator.comparingInt(MatchRecord::getMatchIndex))
                .collect(Collectors.toList());

        // matchIndex 0 是 slots[0] vs slots[1] → 1号种子在matchIndex 0的左或右
        MatchRecord match0 = round1.get(0);
        String seed1Id = players.get(0).getId();
        assertTrue(seed1Id.equals(match0.getLeftPlayerId()) || seed1Id.equals(match0.getRightPlayerId()),
                "1号种子应在第一场比赛中");

        // matchIndex 2 是 slots[4] vs slots[5] → 2号种子在matchIndex 2
        MatchRecord match2 = round1.get(2);
        String seed2Id = players.get(1).getId();
        assertTrue(seed2Id.equals(match2.getLeftPlayerId()) || seed2Id.equals(match2.getRightPlayerId()),
                "2号种子应在第三场比赛(下半区)中");
    }

    @Test
    void seed1AndSeed2_shouldBeInOppositeHalves() {
        List<Player> players = new ArrayList<>();
        players.add(createSeededPlayer("一号种子", 1));
        players.add(createSeededPlayer("二号种子", 2));
        for (int i = 3; i <= 8; i++) {
            Player p = new Player();
            p.setId(IdUtil.simpleUUID());
            p.setName("P" + i);
            players.add(p);
        }

        List<MatchRecord> matches = engine.generateKnockoutBracket("T", players);
        String seed1Id = players.get(0).getId();
        String seed2Id = players.get(1).getId();

        // 追踪两人的晋级路径，决赛前不应相遇
        String seed1Next = null, seed2Next = null;
        for (MatchRecord m : matches) {
            if (seed1Id.equals(m.getLeftPlayerId()) || seed1Id.equals(m.getRightPlayerId())) {
                seed1Next = m.getNextMatchId();
            }
            if (seed2Id.equals(m.getLeftPlayerId()) || seed2Id.equals(m.getRightPlayerId())) {
                seed2Next = m.getNextMatchId();
            }
        }
        assertNotNull(seed1Next);
        assertNotNull(seed2Next);
        assertNotEquals(seed1Next, seed2Next,
                "1号和2号种子不应在半决赛(第二轮)相遇");
    }

    @Test
    void duplicateSeed_shouldThrow() {
        List<Player> players = new ArrayList<>();
        players.add(createSeededPlayer("A", 1));
        players.add(createSeededPlayer("B", 1)); // 重复种子

        assertThrows(IllegalArgumentException.class,
                () -> engine.generateKnockoutBracket("T", players));
    }

    @Test
    void seedOutOfRange_shouldThrow() {
        List<Player> players = new ArrayList<>();
        players.add(createSeededPlayer("A", 5)); // 只有2人，种子5超出范围
        players.add(new Player() {{ setId(IdUtil.simpleUUID()); setName("B"); }});

        assertThrows(IllegalArgumentException.class,
                () -> engine.generateKnockoutBracket("T", players));
    }

    private List<MatchRecord> filterRound(List<MatchRecord> matches, int round) {
        return matches.stream()
                .filter(m -> m.getRoundNum() == round)
                .collect(Collectors.toList());
    }
}
