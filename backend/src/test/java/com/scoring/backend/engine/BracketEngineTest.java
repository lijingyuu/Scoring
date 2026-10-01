package com.scoring.backend.engine;

import cn.hutool.core.util.IdUtil;
import com.scoring.backend.domain.vo.GroupStandingsVO;
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

    /** 单人淘汰赛：引擎前置拒绝，而非 rounds.get(-1) 数组越界 500 */
    @Test
    void generate_withSinglePlayer_shouldThrowIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> engine.generateKnockoutBracket("T1", createPlayers(1)));
        assertTrue(ex.getMessage().contains("至少需要2"));
    }

    /** 单槽位淘汰赛（小组出线仅 1 人等异常数据）：前置拒绝而非越界 */
    @Test
    void generateBySlots_withSingleSlot_shouldThrowIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> engine.generateKnockoutBracketBySlots("T1", List.of("p1")));
        assertTrue(ex.getMessage().contains("at least 2"));
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
    // ------------------------- buildGroupedKnockoutPlan：同组回避 -------------------------

    private GroupStandingsVO.StandingVO standing(String playerId, int rank, boolean qualified) {
        GroupStandingsVO.StandingVO vo = new GroupStandingsVO.StandingVO();
        vo.setPlayerId(playerId);
        vo.setPlayerName(playerId);
        vo.setRank(rank);
        vo.setQualified(qualified);
        vo.setTieUnresolved(false);
        return vo;
    }

    private GroupStandingsVO.GroupVO group(int groupNo, GroupStandingsVO.StandingVO... standings) {
        GroupStandingsVO.GroupVO group = new GroupStandingsVO.GroupVO();
        group.setGroupNo(groupNo);
        group.setStandings(new ArrayList<>(List.of(standings)));
        return group;
    }

    private GroupStandingsVO standings(int qualifiersPerGroup, GroupStandingsVO.GroupVO... groups) {
        GroupStandingsVO vo = new GroupStandingsVO();
        vo.setQualifiersPerGroup(qualifiersPerGroup);
        vo.setAllGroupMatchesFinished(true);
        vo.setGroups(new ArrayList<>(List.of(groups)));
        return vo;
    }

    @Test
    void groupedPlan_twoGroups_shouldCrossPairForSameGroupAvoidance() {
        GroupStandingsVO vo = standings(2,
                group(1, standing("A1", 1, true), standing("A2", 2, true)),
                group(2, standing("B1", 1, true), standing("B2", 2, true)));

        BracketEngine.KnockoutPlan plan = engine.buildGroupedKnockoutPlan(vo);

        assertEquals(List.of("A1", "B2", "B1", "A2"), plan.slots(),
                "2 组时应为 A1-B2 / B1-A2 交叉对阵");
    }

    @Test
    void groupedPlan_threeAndFourGroups_shouldNeverPairSameGroupInFirstRound() {
        for (int groupCount : new int[]{3, 4}) {
            GroupStandingsVO.GroupVO[] groups = new GroupStandingsVO.GroupVO[groupCount];
            for (int g = 1; g <= groupCount; g++) {
                groups[g - 1] = group(g, standing("G" + g + "_1", 1, true), standing("G" + g + "_2", 2, true));
            }
            BracketEngine.KnockoutPlan plan = engine.buildGroupedKnockoutPlan(standings(2, groups));

            assertEquals(groupCount * 2, plan.slots().size());
            for (int pair = 0; pair < plan.slots().size() / 2; pair++) {
                String left = plan.slots().get(pair * 2);
                String right = plan.slots().get(pair * 2 + 1);
                assertNotEquals(left.charAt(1), right.charAt(1),
                        groupCount + " 组时首轮 pair " + pair + " 出现同组对决: " + left + " vs " + right);
            }
        }
    }

    @Test
    void groupedPlan_singleQualifierPerGroup_shouldOrderFirstsByGroupNo() {
        GroupStandingsVO vo = standings(1,
                group(2, standing("B1", 1, true)),
                group(1, standing("A1", 1, true)),
                group(3, standing("C1", 1, true)));

        assertEquals(List.of("A1", "B1", "C1"), engine.buildGroupedKnockoutPlan(vo).slots());
    }

    @Test
    void groupedPlan_onlyOneGroupQualified_shouldFallBackToSameGroupPairing() {
        // 单组赛事：出线即同组前二，同组内战是唯一合法对阵（与集成测试
        // TournamentControllerIntegrationTest.whenSingleGroupTakesTwo 钉死的 200 行为一致）
        GroupStandingsVO vo = standings(2,
                group(1, standing("A1", 1, true), standing("A2", 2, true)));

        assertEquals(List.of("A1", "A2"), engine.buildGroupedKnockoutPlan(vo).slots(),
                "单组赛事应回退为同组前二内战");
    }

    @Test
    void groupedPlan_unbalancedQualifiers_shouldThrowInsteadOfSameGroupPairing() {
        // 多组赛事但第 2 组只出线 1 人（第 2 名缺失）：B1 无法找到其他组的第 2 名，
        // 同组回避不可满足，应显式拒绝而非静默配成同组内战
        GroupStandingsVO vo = standings(2,
                group(1, standing("A1", 1, true), standing("A2", 2, true)),
                group(2, standing("B1", 1, true)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> engine.buildGroupedKnockoutPlan(vo));
        assertTrue(ex.getMessage().contains("同组回避"), "应明确拒绝而非静默同组配对: " + ex.getMessage());
    }

    @Test
    void groupedPlan_tieUnresolvedQualifiers_shouldBeExcludedFromSlots() {
        // 引擎语义：tieUnresolved=true 的出线者不进入淘汰赛（生产侧由 hasUnresolvedTie 守卫兜底）
        GroupStandingsVO.StandingVO tieSecond = standing("B2", 2, true);
        tieSecond.setTieUnresolved(true);
        GroupStandingsVO vo = standings(2,
                group(1, standing("A1", 1, true), standing("A2", 2, true)),
                group(2, standing("B1", 1, true), tieSecond));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> engine.buildGroupedKnockoutPlan(vo));
        assertTrue(ex.getMessage().contains("同组回避"), "tieUnresolved 出线者被排除后应显式拒绝: " + ex.getMessage());
    }

    private List<MatchRecord> filterRound(List<MatchRecord> matches, int round) {
        return matches.stream()
                .filter(m -> m.getRoundNum() == round)
                .collect(Collectors.toList());
    }
}
