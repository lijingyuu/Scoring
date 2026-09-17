package com.scoring.backend.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 赛事引擎混沌测试（纯逻辑，不依赖数据库与服务）。
 *
 * 覆盖：淘汰赛抽签（种子/轮空/晋级链）、循环赛（单/双循环、小组循环）、
 * 小组出线对阵规划（同组回避）。每次运行以种子驱动大量随机输入，
 * 全部用例记录与断言结果写入 outputs/fuzz-volleyball/night/<日期>/ 供夜间审查。
 *
 * 运行：mvn test -Dtest=TournamentEngineChaosTest
 *   可选参数：-Dchaos.seed=<long> -Dchaos.scale=<int，默认 100，控制每组随机规模数>
 */
class TournamentEngineChaosTest {

    private record Violation(String engine, String scenario, String type, String message) {
    }

    private final List<Map<String, Object>> cases = new ArrayList<>();
    private final List<Violation> violations = new ArrayList<>();

    private long seed;
    private Random random;

    private void check(boolean condition, String engine, String scenario, String type, String message) {
        if (!condition) {
            violations.add(new Violation(engine, scenario, type, message));
        }
    }

    private List<Player> makePlayers(int n, String prefix, double seedRatio, boolean withGroups,
                                     int groupCount, Random rnd) {
        List<Player> players = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < n; i++) {
            Player p = new Player();
            p.setId(prefix + "-" + i);
            p.setName(prefix + "选手" + i);
            p.setCreateTime(base.plusMinutes(i));
            if (withGroups) {
                p.setGroupNo(rnd.nextInt(groupCount) + 1);
                p.setGroupPosition(i);
            }
            players.add(p);
        }
        // 随机挑选不重复的种子序号（合法范围 [1, n]）
        List<Integer> ranks = new ArrayList<>();
        for (int r = 1; r <= n; r++) {
            ranks.add(r);
        }
        java.util.Collections.shuffle(ranks, rnd);
        int seeded = (int) Math.round(n * seedRatio);
        for (int i = 0; i < seeded; i++) {
            players.get(i).setSeedRank(ranks.get(i));
        }
        return players;
    }

    /** 与引擎同算法的标准种子序列（用于校验种子落位）。 */
    private List<Integer> expectedSeedOrder(int p) {
        if (p == 1) return new ArrayList<>(List.of(1));
        List<Integer> prev = expectedSeedOrder(p / 2);
        List<Integer> result = new ArrayList<>();
        for (Integer s : prev) {
            result.add(s);
            result.add(p + 1 - s);
        }
        return result;
    }

    // ─────────────────────────── 淘汰赛 ───────────────────────────

    private void chaosKnockout(long caseSeed, List<Player> players) {
        String scenario = "knockout-n" + players.size() + "-seed" + caseSeed;
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("scenario", scenario);
        record.put("playerCount", players.size());
        record.put("seededPlayers", players.stream().filter(p -> p.getSeedRank() != null).count());
        BracketEngine engine = new BracketEngine();
        List<MatchRecord> all = engine.generateKnockoutBracket("t-chaos", null, players);

        int n = players.size();
        int p = Integer.highestOneBit(Math.max(n - 1, 1));
        if (Integer.bitCount(n) == 1) {
            p = n;
        } else {
            p <<= 1;
        }
        int roundCount = Integer.numberOfTrailingZeros(p);
        record.put("bracketCapacity", p);
        record.put("rounds", roundCount);
        record.put("matchCount", all.size());

        // 1. 总场次 = p-1，轮次齐全
        check(all.size() == p - 1, "knockout", scenario, "MATCH_COUNT",
                "期望 " + (p - 1) + " 场，实际 " + all.size());
        for (int r = 1; r <= roundCount; r++) {
            int expected = p >> r;
            int actual = 0;
            for (MatchRecord m : all) {
                if (m.getRoundNum() != null && m.getRoundNum() == r) {
                    actual++;
                }
            }
            check(actual == expected, "knockout", scenario, "ROUND_SIZE",
                    "第 " + r + " 轮期望 " + expected + " 场，实际 " + actual);
        }

        Map<String, MatchRecord> byId = new HashMap<>();
        for (MatchRecord m : all) {
            byId.put(m.getId(), m);
            check(m.getStageType() != null && m.getStageType() == 1, "knockout", scenario, "STAGE_TYPE",
                    "淘汰赛场次 stageType 异常: " + m.getStageType());
        }

        // 2. 晋级链：非决赛场都有 next（指向下一轮），决赛无 next；slot 左右交替
        for (MatchRecord m : all) {
            if (m.getRoundNum() == roundCount) {
                check(m.getNextMatchId() == null, "knockout", scenario, "FINAL_LINK",
                        "决赛不应有 next_match_id");
                continue;
            }
            check(m.getNextMatchId() != null, "knockout", scenario, "MISSING_LINK",
                    "第 " + m.getRoundNum() + " 轮第 " + m.getMatchIndex() + " 场缺少晋级指向");
            if (m.getNextMatchId() != null) {
                MatchRecord parent = byId.get(m.getNextMatchId());
                check(parent != null, "knockout", scenario, "DANGLING_LINK",
                        "晋级指向的父场次不存在: " + m.getNextMatchId());
                check(parent != null && parent.getRoundNum() == m.getRoundNum() + 1,
                        "knockout", scenario, "LINK_ROUND",
                        "晋级指向的父场次轮次错误");
                boolean parityOk = "left".equals(m.getNextMatchSlot()) == (m.getMatchIndex() % 2 == 0);
                check(parent != null && parityOk, "knockout", scenario, "LINK_SLOT",
                        "晋级槽位与索引奇偶不符");
            }
        }

        // 3. 首轮选手占用：每个选手恰好出现一次；轮空数 = p-n；无自打；无双空
        List<MatchRecord> firstRound = all.stream()
                .filter(m -> m.getRoundNum() != null && m.getRoundNum() == 1).toList();
        Map<String, Integer> occupancy = new HashMap<>();
        int byes = 0;
        for (MatchRecord m : firstRound) {
            String l = m.getLeftPlayerId();
            String r = m.getRightPlayerId();
            check(l == null || r == null || !l.equals(r), "knockout", scenario, "SELF_MATCH",
                    "首轮出现自打: " + l);
            check(!(l == null && r == null), "knockout", scenario, "DOUBLE_BYE",
                    "首轮出现双轮空场次");
            if (l == null || r == null) {
                byes++;
                // 轮空自动坍缩
                check(m.getStatus() != null && m.getStatus() == 2, "knockout", scenario, "BYE_NOT_COLLAPSED",
                        "轮空场次未自动坍缩");
                String winner = l != null ? l : r;
                check(winner != null && winner.equals(m.getWinnerId()), "knockout", scenario, "BYE_WINNER",
                        "轮空场次胜者不等于在场选手");
                if (m.getNextMatchId() != null) {
                    MatchRecord parent = byId.get(m.getNextMatchId());
                    if (parent != null) {
                        String parentSlot = "left".equals(m.getNextMatchSlot())
                                ? parent.getLeftPlayerId() : parent.getRightPlayerId();
                        check(winner.equals(parentSlot), "knockout", scenario, "BYE_PROPAGATION",
                                "轮空胜者未传播到父场次对应槽位");
                    }
                }
            }
            if (l != null) occupancy.merge(l, 1, Integer::sum);
            if (r != null) occupancy.merge(r, 1, Integer::sum);
        }
        check(byes == p - n, "knockout", scenario, "BYE_COUNT",
                "轮空数期望 " + (p - n) + "，实际 " + byes);
        check(occupancy.size() == n, "knockout", scenario, "PLAYER_OCCUPANCY",
                "首轮占用选手数期望 " + n + "，实际 " + occupancy.size());
        for (Map.Entry<String, Integer> e : occupancy.entrySet()) {
            check(e.getValue() == 1, "knockout", scenario, "PLAYER_DUPLICATED",
                    "选手 " + e.getKey() + " 在首轮出现 " + e.getValue() + " 次");
        }

        // 4. 种子落位：与标准种子序列一致；1/2 号分属上下半区
        List<Integer> expectedOrder = expectedSeedOrder(p);
        Map<Integer, String> seedToPlayer = new HashMap<>();
        for (Player pl : players) {
            if (pl.getSeedRank() != null) {
                seedToPlayer.put(pl.getSeedRank(), pl.getId());
            }
        }
        String[] slots = new String[p];
        for (MatchRecord m : firstRound) {
            if (m.getLeftPlayerId() != null) {
                int idx = firstRound.indexOf(m) * 2;
                slots[idx] = m.getLeftPlayerId();
            }
            if (m.getRightPlayerId() != null) {
                int idx = firstRound.indexOf(m) * 2 + 1;
                slots[idx] = m.getRightPlayerId();
            }
        }
        for (Map.Entry<Integer, String> e : seedToPlayer.entrySet()) {
            int expectedSlot = expectedOrder.indexOf(e.getKey());
            check(expectedSlot >= 0 && e.getValue().equals(slots[expectedSlot]),
                    "knockout", scenario, "SEED_PLACEMENT",
                    e.getKey() + " 号种子未落在标准坑位 " + expectedSlot);
        }
        if (seedToPlayer.containsKey(1) && seedToPlayer.containsKey(2)) {
            int s1 = expectedOrder.indexOf(1);
            int s2 = expectedOrder.indexOf(2);
            check((s1 < p / 2) != (s2 < p / 2), "knockout", scenario, "SEED_HALF_SEPARATION",
                    "1/2 号种子未分属上下半区");
        }
        record.put("violationsAtCase", 0);
        cases.add(record);
    }

    private void chaosKnockoutNegative() {
        BracketEngine engine = new BracketEngine();
        List<Player> players = makePlayers(8, "N", 0.5, false, 0, new Random(1));
        // 重复种子序号
        players.get(1).setSeedRank(players.get(0).getSeedRank());
        boolean threw = false;
        try {
            engine.generateKnockoutBracket("t-neg", players);
        } catch (IllegalArgumentException ex) {
            threw = true;
        }
        check(threw, "knockout-negative", "duplicate-seed", "DUP_SEED_NOT_REJECTED",
                "重复种子序号未被拒绝");
        // 种子序号越界
        List<Player> players2 = makePlayers(8, "M", 0, false, 0, new Random(2));
        players2.get(0).setSeedRank(99);
        threw = false;
        try {
            engine.generateKnockoutBracket("t-neg", players2);
        } catch (IllegalArgumentException ex) {
            threw = true;
        }
        check(threw, "knockout-negative", "seed-out-of-range", "RANGE_SEED_NOT_REJECTED",
                "越界种子序号未被拒绝");
        // BySlots 非二次幂
        threw = false;
        try {
            engine.generateKnockoutBracketBySlots("t-neg", List.of("a", "b", "c"));
        } catch (IllegalArgumentException ex) {
            threw = true;
        }
        check(threw, "knockout-negative", "non-power-of-two-slots", "SLOTS_NOT_REJECTED",
                "非 2 的幂的槽位数未被拒绝");
    }

    // ─────────────────────────── 循环赛 / 小组赛 ───────────────────────────

    private void chaosLeague(long caseSeed, int n, int rounds) {
        String scenario = "league-n" + n + "-r" + rounds + "-seed" + caseSeed;
        List<Player> players = makePlayers(n, "L", 1.0, false, 0, new Random(caseSeed));
        List<MatchRecord> all = new RoundRobinEngine().generateLeagueMatches("t-chaos", players, rounds);

        Map<String, Integer> pairCount = new HashMap<>();
        Map<String, Integer> played = new HashMap<>();
        for (MatchRecord m : all) {
            String l = m.getLeftPlayerId();
            String r = m.getRightPlayerId();
            check(l != null && r != null, "league", scenario, "NULL_SIDE", "循环赛场次出现空一侧");
            check(!l.equals(r), "league", scenario, "SELF_MATCH", "循环赛出现自打: " + l);
            String pair = l.compareTo(r) < 0 ? l + "|" + r : r + "|" + l;
            pairCount.merge(pair, 1, Integer::sum);
            played.merge(l, 1, Integer::sum);
            played.merge(r, 1, Integer::sum);
        }
        long expectedPairs = (long) n * (n - 1) / 2;
        check(pairCount.size() == expectedPairs, "league", scenario, "PAIR_COVERAGE",
                "对阵组合数期望 " + expectedPairs + "，实际 " + pairCount.size());
        for (Map.Entry<String, Integer> e : pairCount.entrySet()) {
            check(e.getValue() == rounds, "league", scenario, "PAIR_ROUNDS",
                    "对阵 " + e.getKey() + " 期望交手 " + rounds + " 次，实际 " + e.getValue());
        }
        long expectedPerPlayer = rounds * (n - 1);
        for (Player p : players) {
            check(played.getOrDefault(p.getId(), 0) == expectedPerPlayer, "league", scenario, "PLAYER_BALANCE",
                    "选手 " + p.getId() + " 场次数应为 " + expectedPerPlayer);
        }
        // 双循环时主客交换：每对阵两轮应各在左右侧一次
        if (rounds == 2) {
            Map<String, Integer> homeCount = new HashMap<>();
            for (MatchRecord m : all) {
                homeCount.merge(m.getLeftPlayerId() + "|" + m.getRightPlayerId(), 1, Integer::sum);
            }
            for (MatchRecord m : all) {
                String forward = m.getLeftPlayerId() + "|" + m.getRightPlayerId();
                String backward = m.getRightPlayerId() + "|" + m.getLeftPlayerId();
                check(homeCount.getOrDefault(backward, 0) >= 1, "league", scenario, "DOUBLE_RR_SWAP",
                        "双循环缺少主客交换的对阵: " + forward);
                check(homeCount.getOrDefault(forward, 0) == 1, "league", scenario, "DOUBLE_RR_DUP",
                        "双循环同主客对阵重复: " + forward);
            }
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("scenario", scenario);
        record.put("playerCount", n);
        record.put("rounds", rounds);
        record.put("matchCount", all.size());
        cases.add(record);
    }

    private void chaosGroups(long caseSeed) {
        Random rnd = new Random(caseSeed);
        int groupCount = 2 + rnd.nextInt(3);
        String scenario = "groups-" + groupCount + "-seed" + caseSeed;
        List<Player> players = makePlayers(6 + rnd.nextInt(10), "G", 0, true, groupCount, rnd);
        // 保证每组至少 2 人（引擎约束）
        Map<Integer, List<Player>> byGroup = new HashMap<>();
        for (Player p : players) {
            byGroup.computeIfAbsent(p.getGroupNo(), k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<Integer, List<Player>> e : byGroup.entrySet()) {
            while (e.getValue().size() < 2) {
                Player mover = players.stream().filter(p -> byGroup.get(p.getGroupNo()).size() > 2)
                        .findFirst().orElse(null);
                if (mover == null) return;
                byGroup.get(mover.getGroupNo()).remove(mover);
                mover.setGroupNo(e.getKey());
                e.getValue().add(mover);
            }
        }

        List<MatchRecord> all = new RoundRobinEngine().generateGroupMatches("t-chaos", players);
        Map<Integer, List<MatchRecord>> byGroupMatches = new HashMap<>();
        for (MatchRecord m : all) {
            check(m.getGroupNo() != null, "groups", scenario, "GROUP_NO", "小组赛场次缺 groupNo");
            check(m.getStageType() != null && m.getStageType() == 0, "groups", scenario, "STAGE_TYPE",
                    "小组赛场次 stageType 应为 0");
            byGroupMatches.computeIfAbsent(m.getGroupNo(), k -> new ArrayList<>()).add(m);
        }
        check(byGroupMatches.keySet().equals(byGroup.keySet()), "groups", scenario, "GROUP_COVERAGE",
                "生成场次的小组集合与报名小组不一致");
        for (Map.Entry<Integer, List<MatchRecord>> e : byGroupMatches.entrySet()) {
            List<Player> groupPlayers = byGroup.get(e.getKey());
            int gn = groupPlayers.size();
            long expectedPairs = (long) gn * (gn - 1) / 2;
            Map<String, Integer> pairCount = new HashMap<>();
            for (MatchRecord m : e.getValue()) {
                check(groupPlayers.stream().anyMatch(p -> p.getId().equals(m.getLeftPlayerId()))
                        && groupPlayers.stream().anyMatch(p -> p.getId().equals(m.getRightPlayerId())),
                        "groups", scenario, "FOREIGN_IN_GROUP",
                        "小组 " + e.getKey() + " 场次出现外组选手");
                String pair = m.getLeftPlayerId().compareTo(m.getRightPlayerId()) < 0
                        ? m.getLeftPlayerId() + "|" + m.getRightPlayerId()
                        : m.getRightPlayerId() + "|" + m.getLeftPlayerId();
                pairCount.merge(pair, 1, Integer::sum);
            }
            check(pairCount.size() == expectedPairs, "groups", scenario, "GROUP_PAIR_COVERAGE",
                    "小组 " + e.getKey() + " 对阵组合数期望 " + expectedPairs + "，实际 " + pairCount.size());
            for (Map.Entry<String, Integer> pe : pairCount.entrySet()) {
                check(pe.getValue() == 1, "groups", scenario, "GROUP_PAIR_DUP",
                        "小组内对阵 " + pe.getKey() + " 重复 " + pe.getValue() + " 次");
            }
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("scenario", scenario);
        record.put("groups", groupCount);
        record.put("playerCount", players.size());
        record.put("matchCount", all.size());
        cases.add(record);
    }

    // ─────────────────────────── 小组出线对阵规划 ───────────────────────────

    private void chaosGroupedPlan(long caseSeed) {
        Random rnd = new Random(caseSeed);
        int groupCount = 2 + rnd.nextInt(4);
        int perGroup = 1 + rnd.nextInt(2);
        String scenario = "plan-g" + groupCount + "-q" + perGroup + "-seed" + caseSeed;

        GroupStandingsVO vo = new GroupStandingsVO();
        vo.setQualifiersPerGroup(perGroup);
        List<GroupStandingsVO.GroupVO> groups = new ArrayList<>();
        List<String> allQualified = new ArrayList<>();
        Map<String, Integer> playerGroup = new HashMap<>();
        for (int g = 1; g <= groupCount; g++) {
            GroupStandingsVO.GroupVO group = new GroupStandingsVO.GroupVO();
            group.setGroupNo(g);
            List<GroupStandingsVO.StandingVO> standings = new ArrayList<>();
            for (int r = 1; r <= perGroup + 1; r++) {
                GroupStandingsVO.StandingVO st = new GroupStandingsVO.StandingVO();
                String pid = "G" + g + "-R" + r;
                st.setPlayerId(pid);
                st.setRank(r);
                st.setQualified(r <= perGroup);
                st.setTieUnresolved(false);
                standings.add(st);
                if (r <= perGroup) {
                    allQualified.add(pid);
                    playerGroup.put(pid, g);
                }
            }
            group.setStandings(standings);
            groups.add(group);
        }
        vo.setGroups(groups);

        BracketEngine.KnockoutPlan plan = new BracketEngine().buildGroupedKnockoutPlan(vo);
        List<String> slots = plan.slots();

        check(plan.qualifiers().size() == groupCount * perGroup, "grouped-plan", scenario,
                "QUALIFIER_COUNT", "出线数期望 " + groupCount * perGroup + "，实际 " + plan.qualifiers().size());
        check(slots.size() == allQualified.size(), "grouped-plan", scenario, "SLOT_COUNT",
                "槽位数与出线数不一致");
        Set<String> seen = new HashSet<>(slots);
        check(seen.size() == slots.size(), "grouped-plan", scenario, "SLOT_DUP", "槽位出现重复选手");
        check(seen.containsAll(allQualified), "grouped-plan", scenario, "SLOT_MISSING",
                "出线选手未全部进入槽位");
        // 相邻两槽构成一场：第一与第二的配对应尽量不同组
        int sameGroupPairs = 0;
        for (int i = 0; i + 1 < slots.size(); i += 2) {
            Integer g1 = playerGroup.get(slots.get(i));
            Integer g2 = playerGroup.get(slots.get(i + 1));
            if (g1 != null && g1.equals(g2)) {
                sameGroupPairs++;
            }
        }
        // 同组配对仅在无可避免时允许（组数=出线组数、每组 2 名且组数为 1 时无法回避）
        boolean unavoidable = groupCount == 1 && perGroup == 2;
        check(sameGroupPairs == 0 || unavoidable, "grouped-plan", scenario, "SAME_GROUP_PAIRING",
                "出现可避免的同组配对 " + sameGroupPairs + " 对");
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("scenario", scenario);
        record.put("groups", groupCount);
        record.put("qualifiersPerGroup", perGroup);
        record.put("slots", slots.size());
        record.put("sameGroupPairs", sameGroupPairs);
        cases.add(record);
    }

    // ─────────────────────────── 主入口 ───────────────────────────

    @Test
    void chaosTournamentEngines() throws Exception {
        seed = Long.getLong("chaos.seed", System.currentTimeMillis());
        int scale = Integer.getInteger("chaos.scale", 100);
        random = new Random(seed);

        BracketEngine bracketEngine = new BracketEngine();
        // 淘汰赛：覆盖 2..scale 中所有 2 的幂边界附近 + 随机规模
        List<Integer> sizes = new ArrayList<>(List.of(2, 3, 4, 5, 8, 9, 16, 17, 32, 33, 64, 65));
        for (int i = 0; i < scale && sizes.size() < 400; i++) {
            sizes.add(2 + random.nextInt(Math.min(70, scale)));
        }
        Set<Integer> seenSizes = new HashSet<>();
        for (Integer n : sizes) {
            if (n < 2 || n > 70 || !seenSizes.add(n)) {
                continue;
            }
            chaosKnockout(random.nextLong(), makePlayers(n, "K", 0.3 + random.nextDouble() * 0.7,
                    false, 0, random));
        }
        chaosKnockoutNegative();

        // 循环赛：单/双循环 × 各种人数（含奇数）
        for (int n = 2; n <= 9; n++) {
            chaosLeague(random.nextLong(), n, 1);
            chaosLeague(random.nextLong(), n, 2);
        }
        // 小组赛
        for (int i = 0; i < Math.max(8, scale / 10); i++) {
            chaosGroups(random.nextLong());
        }
        // 小组出线对阵规划
        for (int i = 0; i < Math.max(8, scale / 10); i++) {
            chaosGroupedPlan(random.nextLong());
        }

        writeJournal();
        if (!violations.isEmpty()) {
            System.err.println("════ 赛制引擎混沌发现 " + violations.size() + " 处违反期望 ════");
            for (Violation v : violations) {
                System.err.println("[" + v.engine() + "][" + v.scenario() + "] " + v.type() + ": " + v.message());
            }
            AssertionError failure = new AssertionError("赛制引擎混沌测试发现 " + violations.size()
                    + " 处违反期望，详见夜间 journal");
            for (Violation v : violations) {
                failure.addSuppressed(new AssertionError(v.engine() + "/" + v.scenario() + "/" + v.type()
                        + ": " + v.message()));
            }
            throw failure;
        }
        System.out.println("赛制引擎混沌通过: cases=" + cases.size() + " seed=" + seed);
    }

    private void writeJournal() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("suite", "TournamentEngineChaosTest");
        root.put("seed", seed);
        root.put("caseCount", cases.size());
        root.put("violationCount", violations.size());
        root.put("generatedAt", java.time.LocalDateTime.now().toString());
        ArrayNode casesNode = root.putArray("cases");
        for (Map<String, Object> c : cases) {
            ObjectNode node = casesNode.addObject();
            for (Map.Entry<String, Object> e : c.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Integer i) node.put(e.getKey(), i);
                else if (v instanceof Long l) node.put(e.getKey(), l);
                else node.put(e.getKey(), String.valueOf(v));
            }
        }
        ArrayNode violationsNode = root.putArray("violations");
        for (Violation v : violations) {
            ObjectNode node = violationsNode.addObject();
            node.put("engine", v.engine());
            node.put("scenario", v.scenario());
            node.put("type", v.type());
            node.put("message", v.message());
        }

        String date = java.time.LocalDate.now().toString();
        Path dir = Paths.get("..", "outputs", "fuzz-volleyball", "night", date);
        Files.createDirectories(dir);
        Path file = dir.resolve("backend-engines-" + System.currentTimeMillis() + ".json");
        Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        System.out.println("journal 已写入: " + file.toAbsolutePath());
    }
}
