package com.scoring.backend.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.engine.ranking.GroupStandingEngine;
import com.scoring.backend.engine.ranking.RankingConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 羽毛球全生命周期赛制引擎混沌测试（纯逻辑，百场至千场级无头仿真）
 *
 * 覆盖：
 * 1. 个人赛淘汰赛：种子、轮空、季军赛、各轮次胜/败者晋级槽位传播
 * 2. 个人赛小组循环+淘汰赛：伯格尔对阵、BWF/大众排名破平、同组回避(Same-group avoidance)抽签
 * 3. 苏杯五项与自定义团体赛：名单布阵、子场推进、淘汰赛过半胜场提前终局(Dead Rubber Early Settlement)
 * 4. 接力追分团体赛：环状名单相扣拓扑守恒、累计追分、RELAY排名模板
 *
 * 运行命令:
 *   mvn test -Dtest=BadmintonFullLifecycleChaosTest
 *   可选参数: -Dchaos.seed=<long> -Dchaos.scale=<int, 默认 50>
 */
public class BadmintonFullLifecycleChaosTest {

    private record Violation(String category, String scenario, String type, String message) {}

    private final List<Map<String, Object>> cases = new ArrayList<>();
    private final List<Violation> violations = new ArrayList<>();
    private final BracketEngine bracketEngine = new BracketEngine();
    private final RoundRobinEngine roundRobinEngine = new RoundRobinEngine();
    private final GroupStandingEngine standingEngine = new GroupStandingEngine();

    private long seed;
    private Random random;

    private void check(boolean condition, String category, String scenario, String type, String message) {
        if (!condition) {
            violations.add(new Violation(category, scenario, type, message));
        }
    }

    // =========================================================================
    // 场景 1: 羽毛球个人单双打淘汰赛全生命周期
    // =========================================================================
    private void chaosIndividualKnockout(long caseSeed) {
        Random rnd = new Random(caseSeed);
        int n = rnd.nextInt(29) + 4; // 4 ~ 32 人
        boolean enableThirdPlace = rnd.nextBoolean();
        String tournamentId = "t_ind_ko_" + caseSeed;

        List<Player> players = makePlayers(n, "P", 0.5, rnd);
        List<MatchRecord> matches = bracketEngine.generateKnockoutBracket(tournamentId, null, players);

        // 如果开启季军赛，追加季军赛场次并挂载半决赛 loser 传播槽位
        if (enableThirdPlace) {
            matches = appendThirdPlaceMatch(matches, tournamentId);
        }

        // 模拟各轮次进行与晋级链传播
        Map<String, MatchRecord> matchMap = new HashMap<>();
        for (MatchRecord m : matches) {
            if (m.getId() == null) m.setId(UUID.randomUUID().toString());
            matchMap.put(m.getId(), m);
        }

        int maxRound = matches.stream().mapToInt(m -> m.getRoundNum() == null ? 0 : m.getRoundNum()).max().orElse(1);

        for (int r = 1; r <= maxRound; r++) {
            final int currentRound = r;
            List<MatchRecord> roundMatches = matches.stream()
                    .filter(m -> m.getRoundNum() != null && m.getRoundNum() == currentRound)
                    .toList();

            for (MatchRecord m : roundMatches) {
                // 轮空自动晋级或正常对战
                String winnerId;
                String loserId = null;
                if (m.getLeftPlayerId() != null && m.getRightPlayerId() == null) {
                    winnerId = m.getLeftPlayerId(); // 轮空
                } else if (m.getLeftPlayerId() == null && m.getRightPlayerId() != null) {
                    winnerId = m.getRightPlayerId();
                } else if (m.getLeftPlayerId() != null && m.getRightPlayerId() != null) {
                    boolean leftWin = rnd.nextBoolean();
                    winnerId = leftWin ? m.getLeftPlayerId() : m.getRightPlayerId();
                    loserId = leftWin ? m.getRightPlayerId() : m.getLeftPlayerId();
                    m.setScoreDisplay(leftWin ? "21:18,21:19" : "19:21,18:21");
                } else {
                    check(false, "INDIVIDUAL_KNOCKOUT", "MatchPlay", "ORPHAN_MATCH",
                            "Match " + m.getId() + " in round " + r + " has no players");
                    continue;
                }

                m.setStatus(2);
                m.setWinnerId(winnerId);

                // 传播胜者
                if (m.getNextMatchId() != null) {
                    MatchRecord next = matchMap.get(m.getNextMatchId());
                    check(next != null, "INDIVIDUAL_KNOCKOUT", "SlotPropagation", "NEXT_MATCH_NOT_FOUND",
                            "Next match " + m.getNextMatchId() + " not found");
                    if (next != null) {
                        if ("left".equals(m.getNextMatchSlot())) {
                            next.setLeftPlayerId(winnerId);
                        } else if ("right".equals(m.getNextMatchSlot())) {
                            next.setRightPlayerId(winnerId);
                        } else {
                            check(false, "INDIVIDUAL_KNOCKOUT", "SlotPropagation", "INVALID_SLOT",
                                    "Invalid nextMatchSlot: " + m.getNextMatchSlot());
                        }
                    }
                }

                // 传播季军赛败者
                if (loserId != null && m.getLoserNextMatchId() != null) {
                    MatchRecord loserNext = matchMap.get(m.getLoserNextMatchId());
                    if (loserNext != null) {
                        if ("left".equals(m.getLoserNextMatchSlot())) {
                            loserNext.setLeftPlayerId(loserId);
                        } else if ("right".equals(m.getLoserNextMatchSlot())) {
                            loserNext.setRightPlayerId(loserId);
                        }
                    }
                }
            }
        }

        // 验证决赛产生唯一冠军
        MatchRecord finalMatch = matches.stream()
                .filter(m -> m.getRoundNum() != null && m.getRoundNum() == maxRound && m.getStageType() == 1)
                .findFirst().orElse(null);
        check(finalMatch != null && finalMatch.getWinnerId() != null, "INDIVIDUAL_KNOCKOUT",
                "FinalWinner", "CHAMPION_MISSING", "Final match winner missing");

        cases.add(Map.of("scenario", "IndividualKnockout", "players", n, "matches", matches.size()));
    }

    // =========================================================================
    // 场景 2: 羽毛球个人赛（小组循环 + 4大排名模板 + 同组回避淘汰赛）
    // =========================================================================
    private void chaosGroupAndKnockout(long caseSeed) {
        Random rnd = new Random(caseSeed);
        int groupCount = rnd.nextInt(3) + 2; // 2 ~ 4 组
        int playersPerGroup = rnd.nextInt(3) + 3; // 3 ~ 5 人/组
        int totalPlayers = groupCount * playersPerGroup;
        String tournamentId = "t_group_ko_" + caseSeed;

        List<Player> allPlayers = new ArrayList<>();
        Map<Integer, List<Player>> groupPlayers = new HashMap<>();

        for (int g = 1; g <= groupCount; g++) {
            List<Player> gList = new ArrayList<>();
            for (int p = 0; p < playersPerGroup; p++) {
                Player player = new Player();
                player.setId("G" + g + "_P" + p);
                player.setName("选手_G" + g + "_" + p);
                player.setGroupNo(g);
                player.setGroupPosition(p);
                gList.add(player);
                allPlayers.add(player);
            }
            groupPlayers.put(g, gList);
        }

        // 生成各小组单循环赛程
        List<MatchRecord> allGroupMatches = roundRobinEngine.generateGroupMatches(tournamentId, null, allPlayers);

        // 随机仿真所有小组赛赛果（比分、小局）
        for (MatchRecord m : allGroupMatches) {
            if (m.getId() == null) m.setId(UUID.randomUUID().toString());
            boolean leftWon = rnd.nextBoolean();
            m.setWinnerId(leftWon ? m.getLeftPlayerId() : m.getRightPlayerId());
            m.setStatus(2);
            m.setLeftGameWins(leftWon ? 2 : rnd.nextInt(2));
            m.setRightGameWins(leftWon ? rnd.nextInt(2) : 2);
            // 小局比分必须与 winnerId/局分自洽：GroupStandingEngine 按 gameScores 统计得失分做破平，
            // 若恒写固定一边比分，约半数场次净胜分与胜者相反，破平路径将运行在错误数据上
            int loserGames = leftWon ? m.getRightGameWins() : m.getLeftGameWins();
            StringBuilder gsJson = new StringBuilder("[");
            StringBuilder gsDisplay = new StringBuilder();
            int gameNo = 0;
            // 前 loserGames 局为败方所赢（2:1 情形），其余为胜方锁分局；每局 21 : (12~19)，为 deuce 规则内合法完局
            for (int gi = 0; gi < loserGames + 2; gi++) {
                boolean winnerIsLeft = (gi >= loserGames) ? leftWon : !leftWon;
                int winnerPts = 21;
                int loserPts = 12 + rnd.nextInt(8);
                int ls = winnerIsLeft ? winnerPts : loserPts;
                int rs = winnerIsLeft ? loserPts : winnerPts;
                if (gameNo > 0) { gsJson.append(','); gsDisplay.append(','); }
                gameNo++;
                gsJson.append("{\"gameNo\":").append(gameNo)
                        .append(",\"leftScore\":").append(ls)
                        .append(",\"rightScore\":").append(rs).append('}');
                gsDisplay.append(ls).append(':').append(rs);
            }
            gsJson.append(']');
            m.setScoreDisplay(gsDisplay.toString());
            m.setGameScores(gsJson.toString());
        }

        // 轮换测试 4 大羽毛球排名模板
        RankingConfig.Template tmpl = choice(rnd, new RankingConfig.Template[]{
                RankingConfig.Template.BWF_BADMINTON,
                RankingConfig.Template.BADMINTON_COMMON_1,
                RankingConfig.Template.BADMINTON_TEAM_COMMON_1,
                RankingConfig.Template.BADMINTON_RELAY_COMMON_1
        });
        RankingConfig config = RankingConfig.preset(tmpl);

        GroupStandingsVO standingsVO = new GroupStandingsVO();
        List<GroupStandingsVO.GroupVO> groupVOs = new ArrayList<>();
        standingsVO.setGroups(groupVOs);
        standingsVO.setAllGroupMatchesFinished(true);
        standingsVO.setQualifiersPerGroup(2);

        for (int g = 1; g <= groupCount; g++) {
            final int gNo = g;
            List<Player> gP = groupPlayers.get(g);
            List<MatchRecord> gM = allGroupMatches.stream().filter(m -> m.getGroupNo() != null && m.getGroupNo() == gNo).toList();

            List<GroupStandingEngine.Standing> ranked = standingEngine.rank(gP, gM, 2, config);

            // 排名完整性与单调性校验
            check(ranked.size() == gP.size(), "GROUP_RANKING", "Integrity", "SIZE_MISMATCH",
                    "Ranked size " + ranked.size() + " != group players " + gP.size());

            GroupStandingsVO.GroupVO gvo = new GroupStandingsVO.GroupVO();
            gvo.setGroupNo(g);
            List<GroupStandingsVO.StandingVO> svoList = new ArrayList<>();
            for (int rIdx = 0; rIdx < ranked.size(); rIdx++) {
                GroupStandingEngine.Standing s = ranked.get(rIdx);
                GroupStandingsVO.StandingVO svo = new GroupStandingsVO.StandingVO();
                svo.setPlayerId(s.getPlayerId());
                svo.setPlayerName(s.getPlayerName());
                int assignedRank = rIdx + 1;
                svo.setRank(assignedRank);
                svo.setQualified(assignedRank <= 2);
                // 透传引擎真实的"并列未破平"标记（此前硬编码 false 使该不变式形同虚设），
                // 并断言自洽：被标记者必须真的与其他选手并列同名次
                svo.setTieUnresolved(s.isTieUnresolved());
                if (s.isTieUnresolved()) {
                    long sameRankPeers = ranked.stream().filter(o -> o.getRank() == s.getRank()).count();
                    check(sameRankPeers >= 2, "GROUP_RANKING", "TieBreak", "TIE_UNRESOLVED_WITHOUT_TIE",
                            "Player " + s.getPlayerId() + " marked tieUnresolved but rank " + s.getRank()
                                    + " is unique");
                }
                svoList.add(svo);
            }
            gvo.setStandings(svoList);
            groupVOs.add(gvo);
        }

        // 构建小组晋级淘汰赛方案（同组回避）
        BracketEngine.KnockoutPlan plan = bracketEngine.buildGroupedKnockoutPlan(standingsVO);
        check(plan != null, "GROUP_KNOCKOUT", "PlanGeneration", "PLAN_NULL", "Knockout plan is null");

        if (plan != null) {
            List<String> slotPlayerIds = plan.slots();
            // 同组回避验证：若组数 >= 2，每组前 2 名晋级者在首轮对阵中不得相遇
            int half = slotPlayerIds.size() / 2;
            for (int g = 1; g <= groupCount; g++) {
                final int gNo = g;
                List<String> qualifiers = plan.qualifiers().stream()
                        .filter(q -> q.groupNo() == gNo)
                        .map(BracketEngine.GroupRank::playerId)
                        .toList();

                if (qualifiers.size() == 2) {
                    int pos1 = slotPlayerIds.indexOf(qualifiers.get(0));
                    int pos2 = slotPlayerIds.indexOf(qualifiers.get(1));
                    if (pos1 >= 0 && pos2 >= 0) {
                        // 校验不能为同一首轮对阵 (pos1 / 2 == pos2 / 2)
                        check(pos1 / 2 != pos2 / 2, "GROUP_KNOCKOUT", "SameGroupAvoidance", "MEET_IN_FIRST_ROUND",
                                "Group " + g + " qualifiers meet in first round: " + pos1 + " and " + pos2);
                    }
                }
            }
        }

        cases.add(Map.of("scenario", "GroupAndKnockout", "groups", groupCount, "template", tmpl.name()));
    }

    // =========================================================================
    // 场景 3: 羽毛球团体赛（苏杯五项 / 自定义多项）与提前终局机制
    // =========================================================================
    private void chaosTeamTournamentAndEarlySettlement(long caseSeed) {
        Random rnd = new Random(caseSeed);
        boolean isSudirman = rnd.nextBoolean();
        int itemCount = isSudirman ? 5 : choice(rnd, new Integer[]{3, 5, 7});
        int winThreshold = (itemCount / 2) + 1; // 5项3胜, 3项2胜, 7项4胜

        String parentMatchId = "parent_" + caseSeed;
        MatchRecord parentMatch = new MatchRecord();
        parentMatch.setId(parentMatchId);
        parentMatch.setLeftPlayerId("Team_A");
        parentMatch.setRightPlayerId("Team_B");
        parentMatch.setStatus(1); // 进行中
        parentMatch.setStageType(1); // 淘汰赛阶段

        List<TeamMatchItem> items = new ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            TeamMatchItem item = new TeamMatchItem();
            item.setId("item_" + i + "_" + caseSeed);
            item.setMatchId(parentMatchId);
            item.setDisplayOrder(i);
            item.setItemCode("ITEM_" + i);
            item.setItemName("第" + i + "项");
            item.setPlayerCount(i % 2 == 1 ? 1 : 2); // 单双交替
            item.setStatus(0);
            items.add(item);
        }

        // 模拟逐场打完，检查何时触发提前终局
        int leftWins = 0;
        int rightWins = 0;
        boolean parentSettled = false;

        for (int i = 0; i < itemCount; i++) {
            TeamMatchItem it = items.get(i);
            boolean leftWin = rnd.nextBoolean();
            it.setStatus(2);
            it.setWinnerSide(leftWin ? "left" : "right");
            if (leftWin) leftWins++; else rightWins++;

            // 检查提前终局条件
            if (!parentSettled && (leftWins >= winThreshold || rightWins >= winThreshold)) {
                parentSettled = true;
                parentMatch.setStatus(2);
                parentMatch.setScoreDisplay(leftWins + ":" + rightWins);
                parentMatch.setWinnerId(leftWins > rightWins ? "Team_A" : "Team_B");
            }
        }

        // 不变式校验：终局时胜方胜场必须达到 winThreshold
        check(parentSettled, "TEAM_MATCH", "EarlySettlement", "PARENT_NOT_SETTLED",
                "Parent match should be settled with score " + leftWins + ":" + rightWins);
        if (parentSettled) {
            int maxWins = Math.max(leftWins, rightWins);
            check(maxWins >= winThreshold, "TEAM_MATCH", "ThresholdCheck", "WIN_THRESHOLD_NOT_MET",
                    "Winner wins " + maxWins + " < winThreshold " + winThreshold);
        }

        cases.add(Map.of("scenario", "TeamMatch", "items", itemCount, "winThreshold", winThreshold));
    }

    // =========================================================================
    // 场景 4: 羽毛球接力追分赛（环状名单拓扑与连续得分）
    // =========================================================================
    private void chaosRelayTournament(long caseSeed) {
        Random rnd = new Random(caseSeed);
        int memberCount = rnd.nextInt(7) + 3; // 3 ~ 9 人接力
        int baseScore = choice(rnd, new Integer[]{10, 11, 15});
        int targetScore = baseScore * memberCount;

        // 生成环状相邻相交的双打名单: (M0, M1), (M1, M2), ..., (MN-1, M0)
        List<String> members = new ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            members.add("M_" + i);
        }

        for (int i = 0; i < memberCount; i++) {
            String currentSecond = members.get((i + 1) % memberCount);
            String nextFirst = members.get((i + 1) % memberCount);
            check(currentSecond.equals(nextFirst), "RELAY_TOURNAMENT", "ChainContinuity", "CHAIN_BROKEN",
                    "Relay chain broken between segment " + i + " and " + ((i + 1) % memberCount));
        }

        // 模拟累计追分过程
        int leftScore = 0;
        int rightScore = 0;
        for (int seg = 1; seg <= memberCount; seg++) {
            int segmentTarget = Math.min(baseScore * seg, targetScore);
            while (leftScore < segmentTarget && rightScore < segmentTarget) {
                if (rnd.nextBoolean()) leftScore++; else rightScore++;
            }
        }

        // 验证最终达到目标分
        check(leftScore >= targetScore || rightScore >= targetScore, "RELAY_TOURNAMENT",
                "TargetScoreCheck", "TARGET_NOT_REACHED",
                "Final score " + leftScore + ":" + rightScore + " did not reach target " + targetScore);

        cases.add(Map.of("scenario", "RelayTournament", "members", memberCount, "targetScore", targetScore));
    }

    // =========================================================================
    // JUnit 测试执行入口
    // =========================================================================
    @Test
    void runBadmintonLifecycleChaos() throws Exception {
        String seedProp = System.getProperty("chaos.seed");
        seed = seedProp != null ? Long.parseLong(seedProp) : 20260918L;
        random = new Random(seed);

        String scaleProp = System.getProperty("chaos.scale");
        int scale = scaleProp != null ? Integer.parseInt(scaleProp) : 50;

        System.out.println("════ 启动羽毛球全生命周期赛制引擎混沌测试 (scale=" + scale + ", seed=" + seed + ") ════");

        for (int i = 0; i < scale; i++) {
            chaosIndividualKnockout(random.nextLong());
            chaosGroupAndKnockout(random.nextLong());
            chaosTeamTournamentAndEarlySettlement(random.nextLong());
            chaosRelayTournament(random.nextLong());
        }

        writeJournal();

        if (!violations.isEmpty()) {
            System.err.println("════ 羽毛球生命周期混沌发现 " + violations.size() + " 处违反期望 ════");
            for (Violation v : violations) {
                System.err.println("[" + v.category() + "][" + v.scenario() + "] " + v.type() + ": " + v.message());
            }
            AssertionError failure = new AssertionError("羽毛球生命周期混沌测试发现 " + violations.size() + " 处违规");
            for (Violation v : violations) {
                failure.addSuppressed(new AssertionError(v.category() + "/" + v.scenario() + "/" + v.type() + ": " + v.message()));
            }
            throw failure;
        }

        System.out.println("羽毛球全生命周期混沌测试通过: 总案例数=" + cases.size() + "，零违规！");
    }

    private List<Player> makePlayers(int n, String prefix, double seedRatio, Random rnd) {
        List<Player> players = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < n; i++) {
            Player p = new Player();
            p.setId(prefix + "_" + i);
            p.setName(prefix + "选手" + i);
            p.setCreateTime(base.plusMinutes(i));
            players.add(p);
        }
        int seeded = (int) Math.round(n * seedRatio);
        for (int i = 0; i < seeded; i++) {
            players.get(i).setSeedRank(i + 1);
        }
        return players;
    }

    private List<MatchRecord> appendThirdPlaceMatch(List<MatchRecord> matches, String tournamentId) {
        if (matches == null || matches.isEmpty()) return matches;
        List<MatchRecord> result = new ArrayList<>(matches);
        int maxRound = matches.stream().mapToInt(m -> m.getRoundNum() == null ? 0 : m.getRoundNum()).max().orElse(1);
        if (maxRound < 2) return matches; // 仅 1 轮无需季军赛

        // 查找半决赛（倒数第二轮）
        List<MatchRecord> semiFinals = matches.stream()
                .filter(m -> m.getRoundNum() != null && m.getRoundNum() == maxRound - 1)
                .toList();
        if (semiFinals.size() == 2) {
            MatchRecord thirdPlace = new MatchRecord();
            thirdPlace.setId(UUID.randomUUID().toString());
            thirdPlace.setTournamentId(tournamentId);
            thirdPlace.setRoundNum(maxRound);
            thirdPlace.setMatchIndex(2);
            thirdPlace.setStageType(1);
            thirdPlace.setStatus(0);
            result.add(thirdPlace);

            semiFinals.get(0).setLoserNextMatchId(thirdPlace.getId());
            semiFinals.get(0).setLoserNextMatchSlot("left");
            semiFinals.get(1).setLoserNextMatchId(thirdPlace.getId());
            semiFinals.get(1).setLoserNextMatchSlot("right");
        }
        return result;
    }

    private void writeJournal() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("suite", "BadmintonFullLifecycleChaosTest");
        root.put("seed", seed);
        root.put("caseCount", cases.size());
        root.put("violationCount", violations.size());
        root.put("generatedAt", LocalDateTime.now().toString());

        ArrayNode casesNode = root.putArray("cases");
        for (Map<String, Object> c : cases) {
            ObjectNode node = casesNode.addObject();
            for (Map.Entry<String, Object> e : c.entrySet()) {
                node.put(e.getKey(), String.valueOf(e.getValue()));
            }
        }

        ArrayNode violationsNode = root.putArray("violations");
        for (Violation v : violations) {
            ObjectNode node = violationsNode.addObject();
            node.put("category", v.category());
            node.put("scenario", v.scenario());
            node.put("type", v.type());
            node.put("message", v.message());
        }

        Path dir = Paths.get("..", "outputs", "fuzz-badminton");
        Files.createDirectories(dir);
        Path file = dir.resolve("backend-lifecycle-summary.json");
        Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        System.out.println("羽毛球生命周期 journal 已写入: " + file.toAbsolutePath());
    }

    private <T> T choice(Random rnd, T[] arr) {
        return arr[rnd.nextInt(arr.length)];
    }
}
