package com.scoring.backend.service.tournament;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.dto.UpdateGroupAssignmentsReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentCustomItem;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.domain.entity.TournamentRankingConfig;
import com.scoring.backend.domain.entity.TournamentRefereeConfig;
import com.scoring.backend.domain.entity.TournamentRoundRule;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.engine.BracketEngine;
import com.scoring.backend.engine.RoundRobinEngine;
import com.scoring.backend.engine.ranking.GroupStandingEngine;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentCustomItemMapper;
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
import com.scoring.backend.mapper.TournamentRankingConfigMapper;
import com.scoring.backend.mapper.TournamentRefereeConfigMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 比赛创建链路混沌测试（真实 TournamentCreationFactory + TournamentDrawService.updateGroupAssignments）。
 *
 * 与 BadmintonFullLifecycleChaosTest（只覆盖赛制引擎，完全不覆盖创建）互补：本测试从"创建比赛"
 * 入口驱动真实写路径服务，只把持久层 mapper mock 成内存态，断言物化结果（match_record /
 * player 分组座次 / custom_item / team_member）的不变式，并用种子化恶意探针断言非法输入被
 * 真实校验拒绝、且拒绝理由与源码文案一致。
 *
 * 场景 1 chaosCreateHappyPath：随机合法组合创建成功，断言物化不变式
 *  - type0 个人赛：容量=2^k、场次数=容量-1（+季军赛）、首轮容量/2 场、无双轮空、每人恰一个首轮签位、
 *    manual 模式下首轮 left/right 与 knockoutSlotOrder 完全一致、轮空自动坍缩（status=2 + winnerId=在场侧 +
 *    胜者已传播到父场槽位）、nextMatchId/Slot 链（round r 第 i 场 → r+1 第 i/2 场，slot=i%2?right:left）、
 *    决赛无 next、thirdPlaceEnabled 时恰 +1 场 matchRole=1；
 *  - type1：小组赛场次数=ΣC(size,2)、同组每对恰一次、match.groupNo 正确、无 stageType=1（淘汰赛延后）、
 *    manual-groups 下 player.group_no/group_position 与请求分组/组内顺序完全一致、auto 下分组尺寸合法；
 *  - type2：场次数=C(n,2)×rounds、每对恰赛 rounds 次；
 *  - 团体 template=1/3：custom item 落库（itemCode=type_displayOrder、playerCount 按单双打）、
 *    每队恰 1 名队长、队员数与入参一致；
 *  - division / tournament 落库字段（tournamentType、drawMode 0/1/2、knockoutSlots/knockoutRounds/qpg、status=1）。
 *
 * 场景 2 chaosCreateMaliciousProbe：每案例随机 3~5 个探针，断言被 IllegalArgumentException /
 *  IllegalStateException 拒绝且消息包含源码真实文案子串（逐条与源码行号对照，见各 probe 注释）。
 *  其他异常类型（如 NPE）= 违规记录。探针不做"无部分写入"断言：生产有 @Transactional 回滚，
 *  mock 世界无回滚，故只断言拒绝与理由。
 *
 * 场景 3 chaosGroupAssignmentEdit：创建 type1+manual-groups 赛事后调真实 updateGroupAssignments
 *  - 合法重分组 → group_no 更新、小组赛全删全建（旧 match id 消失、新场次数=ΣC(size,2)、每对恰一场）；
 *  - 幂等重提交 → match id 不变（sameAsCurrentAssignment 短路）；
 *  - 探针（组数错/空 id/重复 id/漏人/外来 id）→ 拒绝且 match id 不变（校验先于删除）；
 *  - 守卫：drawMode=0 的 type1 赛事拒绝、type0 赛事拒绝。
 *
 * 服务装配（真实实例 + mock 持久层）：
 *  - TournamentAccessGuard / TournamentRankingService / BracketEngine / RoundRobinEngine / GroupStandingEngine
 *    均为真实实例；TournamentCreationFactory 为构造器注入，直接 new；
 *  - mapper 全部 Mockito mock：insert 落内存（id 为 null 时补 UUID，模拟 MP ASSIGN_ID），
 *    selectList 按 QueryWrapper 的 SQL 片段 + 参数过滤内存数据，updateById 按 MP 默认策略只覆盖非 null 字段。
 *
 * 运行命令:
 *   cd backend && mvn test -q -Dtest=BadmintonCreationChaosTest
 *   可选参数: -Dchaos.seed=<long, 默认 20260919> -Dchaos.scale=<int, 默认 30>
 */
public class BadmintonCreationChaosTest {

    private static final String CREATOR = "creator-1";

    private record Violation(String category, String scenario, String type, String message, long caseSeed) {}

    private record Probe(String type, String desc, CreateTournamentReq req, String expectedPhrase) {}

    private final List<Map<String, Object>> cases = new ArrayList<>();
    private final List<Violation> violations = new ArrayList<>();

    private long seed;
    private Random random;
    /** 当前正在执行的混沌案例种子：随 Violation 落盘，用于违规回放 */
    private long currentCaseSeed;

    /** 守卫探针用世界（与案例无关且不会被改动，惰性构建一次复用） */
    private CreationWorld guardType1AutoWorld;
    private CreationWorld guardType0World;

    private void check(boolean condition, String category, String scenario, String type, String message) {
        if (!condition) {
            violations.add(new Violation(category, scenario, type, message, currentCaseSeed));
        }
    }

    // ==================================================================================
    // 场景 1: 合法创建 + 物化不变式
    // ==================================================================================
    private void chaosCreateHappyPath(long caseSeed) {
        currentCaseSeed = caseSeed;
        Random rnd = new Random(caseSeed);
        boolean teamEvent = rnd.nextInt(10) < 4; // 个人 60% / 团体 40%
        if (teamEvent) {
            chaosHappyTeam(rnd, caseSeed);
            return;
        }
        int tournamentType = switch (rnd.nextInt(5)) {
            case 0, 1 -> 0;
            case 2, 3 -> 1;
            default -> 2;
        };
        switch (tournamentType) {
            case 0 -> chaosHappyKnockout(rnd, caseSeed);
            case 1 -> chaosHappyGroupStage(rnd, caseSeed);
            default -> chaosHappyRoundRobin(rnd, caseSeed);
        }
    }

    /** type0 个人赛：容量/首轮/轮空坍缩/晋级链/季军赛不变式。 */
    private void chaosHappyKnockout(Random rnd, long caseSeed) {
        CreationWorld w = buildWorld();
        int playerCount = 4 + rnd.nextInt(30);            // 4 ~ 33
        int capacity = nextPowerOfTwo(playerCount);
        boolean manual = rnd.nextBoolean();
        boolean thirdPlace = rnd.nextBoolean();
        List<Integer> slotOrder = manual ? buildManualSlotOrder(playerCount, capacity, rnd) : null;

        CreateTournamentReq req = individualReq("个人淘汰赛_" + caseSeed, playerCount);
        req.setTournamentType(0);
        req.setThirdPlaceEnabled(thirdPlace);
        if (manual) {
            req.setDrawMode("manual");
            req.setKnockoutSlotOrder(slotOrder);
        }

        String tournamentId = w.factory.createTournament(CREATOR, req);
        check(tournamentId != null && !tournamentId.isBlank(), "CREATION", "HappyKnockout", "NO_TOURNAMENT_ID",
                "创建未返回赛事 id");
        TournamentDivision division = w.divisions.get(0);
        Tournament tournament = w.tournaments.get(0);
        List<Player> roster = w.playersInInsertionOrder(division.getId());
        List<MatchRecord> matches = w.matchesOf(division.getId(), 1);
        Integer rounds = division.getKnockoutRounds();

        // ---- 落库字段 ----
        check(Integer.valueOf(0).equals(division.getTournamentType()), "CREATION", "HappyKnockout", "DIV_TYPE",
                "division.tournamentType=" + division.getTournamentType());
        check(Integer.valueOf(manual ? 1 : 0).equals(division.getDrawMode()), "CREATION", "HappyKnockout", "DIV_DRAW_MODE",
                "division.drawMode=" + division.getDrawMode() + " 期望 " + (manual ? 1 : 0));
        check(rounds != null && (1 << rounds) == capacity, "CREATION", "HappyKnockout", "DIV_ROUNDS",
                "division.knockoutRounds=" + rounds + " 期望容量 " + capacity);
        check(division.getKnockoutSlots() == null && division.getQualifiersPerGroup() == null,
                "CREATION", "HappyKnockout", "DIV_UNUSED_COLUMNS",
                "type0 的 knockoutSlots/qualifiersPerGroup 应为 null: " + division.getKnockoutSlots()
                        + "/" + division.getQualifiersPerGroup());
        check(Integer.valueOf(1).equals(division.getStatus()), "CREATION", "HappyKnockout", "DIV_STATUS",
                "division.status=" + division.getStatus() + " 期望 1");
        check(Integer.valueOf(0).equals(tournament.getTournamentType())
                        && Integer.valueOf(1).equals(tournament.getStatus()),
                "CREATION", "HappyKnockout", "TOURNAMENT_MIRROR",
                "tournament.type=" + tournament.getTournamentType() + " status=" + tournament.getStatus());

        // ---- 场次数 ----
        int expectedTotal = capacity - 1 + (thirdPlace ? 1 : 0);
        check(matches.size() == expectedTotal, "CREATION", "HappyKnockout", "MATCH_COUNT",
                "场次数=" + matches.size() + " 期望 " + expectedTotal + "（容量 " + capacity + "，季军赛 " + thirdPlace + "）");
        long thirdPlaceCount = matches.stream().filter(m -> Integer.valueOf(1).equals(m.getMatchRole())).count();
        check(thirdPlaceCount == (thirdPlace ? 1 : 0), "CREATION", "HappyKnockout", "THIRD_PLACE_ROLE",
                "matchRole=1 场次数=" + thirdPlaceCount);
        if (thirdPlace) {
            MatchRecord third = matches.stream().filter(m -> Integer.valueOf(1).equals(m.getMatchRole()))
                    .findFirst().orElse(null);
            check(third != null && Objects.equals(third.getRoundNum(), rounds)
                            && Integer.valueOf(1).equals(third.getStageType()),
                    "CREATION", "HappyKnockout", "THIRD_PLACE_SHAPE",
                    "季军赛场次形状异常: " + (third == null ? "null" : third.getRoundNum() + "/" + third.getStageType()));
            if (third != null) {
                List<MatchRecord> semis = matches.stream()
                        .filter(m -> !Integer.valueOf(1).equals(m.getMatchRole()))
                        .filter(m -> rounds != null && rounds - 1 == safeInt(m.getRoundNum()))
                        .sorted(Comparator.comparingInt(m -> safeInt(m.getMatchIndex())))
                        .toList();
                check(semis.size() == 2, "CREATION", "HappyKnockout", "SEMIFINAL_COUNT",
                        "半决赛场次数=" + semis.size() + " 期望 2");
                if (semis.size() == 2) {
                    check(third.getId().equals(semis.get(0).getLoserNextMatchId())
                                    && "left".equals(semis.get(0).getLoserNextMatchSlot()),
                            "CREATION", "HappyKnockout", "THIRD_PLACE_LINK_LEFT",
                            "半决赛一败者槽位未指向季军赛: " + semis.get(0).getLoserNextMatchId()
                                    + "/" + semis.get(0).getLoserNextMatchSlot());
                    check(third.getId().equals(semis.get(1).getLoserNextMatchId())
                                    && "right".equals(semis.get(1).getLoserNextMatchSlot()),
                            "CREATION", "HappyKnockout", "THIRD_PLACE_LINK_RIGHT",
                            "半决赛二败者槽位未指向季军赛: " + semis.get(1).getLoserNextMatchId()
                                    + "/" + semis.get(1).getLoserNextMatchSlot());
                }
            }
        }

        // ---- 首轮签位 ----
        Map<String, MatchRecord> slotMatches = new HashMap<>();
        for (MatchRecord m : matches) {
            if (Integer.valueOf(1).equals(m.getMatchRole())) continue;
            slotMatches.put(safeInt(m.getRoundNum()) + ":" + safeInt(m.getMatchIndex()), m);
        }
        List<MatchRecord> firstRound = new ArrayList<>();
        for (int i = 0; i < capacity / 2; i++) {
            MatchRecord m = slotMatches.get("1:" + i);
            check(m != null, "CREATION", "HappyKnockout", "FIRST_ROUND_MISSING",
                    "首轮第 " + i + " 场缺失（容量 " + capacity + "）");
            if (m != null) firstRound.add(m);
        }
        check(firstRound.size() == capacity / 2, "CREATION", "HappyKnockout", "FIRST_ROUND_COUNT",
                "首轮场次数=" + firstRound.size() + " 期望 " + (capacity / 2));

        Map<String, Integer> slotOccurrence = new HashMap<>();
        for (int i = 0; i < firstRound.size(); i++) {
            MatchRecord m = firstRound.get(i);
            String left = m.getLeftPlayerId();
            String right = m.getRightPlayerId();
            check(!(left == null && right == null), "CREATION", "HappyKnockout", "DOUBLE_BYE",
                    "首轮第 " + i + " 场两侧均为轮空");
            countOccurrence(slotOccurrence, left);
            countOccurrence(slotOccurrence, right);

            if (manual) {
                Integer slotLeft = slotOrder.get(i * 2);
                Integer slotRight = slotOrder.get(i * 2 + 1);
                String expectedLeft = slotLeft == null ? null : roster.get(slotLeft).getId();
                String expectedRight = slotRight == null ? null : roster.get(slotRight).getId();
                check(Objects.equals(left, expectedLeft) && Objects.equals(right, expectedRight),
                        "CREATION", "HappyKnockout", "MANUAL_SLOT_ORDER_MISMATCH",
                        "首轮第 " + i + " 场 left/right=" + left + "/" + right
                                + " 期望 " + expectedLeft + "/" + expectedRight);
            }

            // 轮空自动坍缩 + 胜者传播到父场槽位
            boolean oneSided = (left == null) != (right == null);
            if (oneSided) {
                String winner = left != null ? left : right;
                check(Integer.valueOf(2).equals(m.getStatus()) && Objects.equals(winner, m.getWinnerId()),
                        "CREATION", "HappyKnockout", "BYE_NOT_COLLAPSED",
                        "首轮第 " + i + " 场单侧轮空但 status=" + m.getStatus() + " winnerId=" + m.getWinnerId()
                                + " 期望 status=2 winnerId=" + winner);
                MatchRecord parent = slotMatches.get("2:" + (i / 2));
                String parentSlot = Objects.equals(m.getNextMatchSlot(), "right") ? "right" : "left";
                check(parent != null, "CREATION", "HappyKnockout", "BYE_PARENT_MISSING",
                        "轮空坍缩时父场缺失: " + m.getNextMatchId());
                if (parent != null) {
                    String propagated = "right".equals(parentSlot) ? parent.getRightPlayerId() : parent.getLeftPlayerId();
                    check(Objects.equals(winner, propagated), "CREATION", "HappyKnockout", "BYE_NOT_PROPAGATED",
                            "轮空胜者未传播到父场 " + parentSlot + " 槽位: got " + propagated + " 期望 " + winner);
                }
            } else {
                check(!Integer.valueOf(2).equals(m.getStatus()), "CREATION", "HappyKnockout", "NORMAL_MATCH_PREFINISHED",
                        "首轮第 " + i + " 场双侧有选手却被置为已完赛");
            }
        }
        // 每名选手恰出现在一个首轮签位
        for (Player p : roster) {
            check(Integer.valueOf(1).equals(slotOccurrence.get(p.getId())), "CREATION", "HappyKnockout", "PLAYER_SLOT_DUP",
                    "选手 " + p.getName() + " 首轮签位出现次数=" + slotOccurrence.get(p.getId()));
        }

        // ---- nextMatchId / nextMatchSlot 晋级链 ----
        if (rounds != null) {
            for (int r = 1; r <= rounds; r++) {
                int count = capacity >> r;
                for (int i = 0; i < count; i++) {
                    MatchRecord child = slotMatches.get(r + ":" + i);
                    check(child != null, "CREATION", "HappyKnockout", "CHAIN_NODE_MISSING",
                            "晋级链缺少 round " + r + " 第 " + i + " 场");
                    if (child == null) continue;
                    if (r == rounds) {
                        check(child.getNextMatchId() == null && child.getNextMatchSlot() == null,
                                "CREATION", "HappyKnockout", "FINAL_HAS_NEXT",
                                "决赛仍有 next: " + child.getNextMatchId() + "/" + child.getNextMatchSlot());
                    } else {
                        MatchRecord parent = slotMatches.get((r + 1) + ":" + (i / 2));
                        check(parent != null && Objects.equals(child.getNextMatchId(), parent.getId()),
                                "CREATION", "HappyKnockout", "CHAIN_NEXT_MATCH",
                                "round " + r + " 第 " + i + " 场 nextMatchId=" + child.getNextMatchId()
                                        + " 期望 round " + (r + 1) + " 第 " + (i / 2) + " 场");
                        check(Objects.equals(child.getNextMatchSlot(), i % 2 == 0 ? "left" : "right"),
                                "CREATION", "HappyKnockout", "CHAIN_NEXT_SLOT",
                                "round " + r + " 第 " + i + " 场 nextMatchSlot=" + child.getNextMatchSlot());
                    }
                }
            }
        }

        cases.add(Map.of("scenario", "HappyKnockout", "players", playerCount, "capacity", capacity,
                "manual", manual, "thirdPlace", thirdPlace, "matches", matches.size()));
    }

    /** type1 个人赛：分组座次 + 小组赛场次不变式。 */
    private void chaosHappyGroupStage(Random rnd, long caseSeed) {
        CreationWorld w = buildWorld();
        int[][] combos = {{2, 1, 2}, {4, 1, 4}, {2, 2, 4}, {4, 2, 8}};
        int[] combo = combos[rnd.nextInt(combos.length)];
        int groupCount = combo[0];
        int qualifiers = combo[1];
        int knockoutSlots = combo[2];
        int minPerGroup = Math.max(2, qualifiers);
        int base = 3 + rnd.nextInt(4);                       // 每组 3~6 人
        int[] sizes = new int[groupCount];
        Arrays.fill(sizes, base);
        boolean uneven = groupCount >= 2 && rnd.nextBoolean();
        if (uneven) {                                       // 允许不均：组的 1 让 1 人给组 2
            sizes[0]--;
            sizes[1]++;
        }
        int playerCount = Arrays.stream(sizes).sum();
        boolean manualGroups = rnd.nextBoolean();

        CreateTournamentReq req = individualReq("个人小组赛_" + caseSeed, playerCount);
        req.setTournamentType(1);
        req.setKnockoutSlots(knockoutSlots);
        req.setQualifiersPerGroup(qualifiers);
        List<List<Integer>> requestedGroups = null;
        if (manualGroups) {
            requestedGroups = partitionIndices(playerCount, sizes, rnd);
            req.setDrawMode("manual-groups");
            req.setGroups(requestedGroups);
        }

        String tournamentId = w.factory.createTournament(CREATOR, req);
        check(tournamentId != null && !tournamentId.isBlank(), "CREATION", "HappyGroupStage", "NO_TOURNAMENT_ID",
                "创建未返回赛事 id");
        TournamentDivision division = w.divisions.get(0);
        Tournament tournament = w.tournaments.get(0);
        List<Player> roster = w.playersInInsertionOrder(division.getId());
        List<MatchRecord> matches = w.matchesOf(division.getId(), 0);
        List<MatchRecord> knockoutMatches = new ArrayList<>(w.matches);
        knockoutMatches.removeAll(matches);

        check(Integer.valueOf(1).equals(division.getTournamentType())
                        && Integer.valueOf(manualGroups ? 2 : 0).equals(division.getDrawMode())
                        && Integer.valueOf(knockoutSlots).equals(division.getKnockoutSlots())
                        && Integer.valueOf(qualifiers).equals(division.getQualifiersPerGroup())
                        && Integer.valueOf(Integer.numberOfTrailingZeros(knockoutSlots)).equals(division.getKnockoutRounds())
                        && Boolean.FALSE.equals(division.getKnockoutGenerated())
                        && Integer.valueOf(1).equals(division.getStatus()),
                "CREATION", "HappyGroupStage", "DIV_FIELDS",
                "division 字段异常: type=" + division.getTournamentType() + " drawMode=" + division.getDrawMode()
                        + " slots=" + division.getKnockoutSlots() + " qpg=" + division.getQualifiersPerGroup()
                        + " rounds=" + division.getKnockoutRounds() + " generated=" + division.getKnockoutGenerated()
                        + " status=" + division.getStatus());
        check(Integer.valueOf((int) Math.ceil(playerCount * 1.0 / groupCount)).equals(division.getGroupSize()),
                "CREATION", "HappyGroupStage", "DIV_GROUP_SIZE",
                "division.groupSize=" + division.getGroupSize());
        check(Integer.valueOf(1).equals(tournament.getStatus())
                        && Integer.valueOf(1).equals(tournament.getTournamentType()),
                "CREATION", "HappyGroupStage", "TOURNAMENT_MIRROR",
                "tournament status=" + tournament.getStatus() + " type=" + tournament.getTournamentType());

        // 淘汰赛延后：创建期不得有任何 stageType=1 场次
        check(knockoutMatches.isEmpty(), "CREATION", "HappyGroupStage", "KNOCKOUT_PREGENERATED",
                "type1 创建期出现 stageType=1 场次 " + knockoutMatches.size() + " 场");

        // 分组座次
        Map<Integer, List<Player>> byGroup = new HashMap<>();
        for (Player p : roster) {
            check(p.getGroupNo() != null && p.getGroupPosition() != null,
                    "CREATION", "HappyGroupStage", "PLAYER_UNASSIGNED",
                    "选手 " + p.getName() + " 未分组: " + p.getGroupNo() + "/" + p.getGroupPosition());
            if (p.getGroupNo() == null) continue;
            byGroup.computeIfAbsent(p.getGroupNo(), k -> new ArrayList<>()).add(p);
        }
        check(byGroup.size() == groupCount, "CREATION", "HappyGroupStage", "GROUP_COUNT",
                "落库组数=" + byGroup.size() + " 期望 " + groupCount);

        if (manualGroups) {
            for (int gi = 0; gi < requestedGroups.size(); gi++) {
                List<Integer> group = requestedGroups.get(gi);
                for (int pi = 0; pi < group.size(); pi++) {
                    Player p = roster.get(group.get(pi));
                    check(Integer.valueOf(gi + 1).equals(p.getGroupNo())
                                    && Integer.valueOf(pi + 1).equals(p.getGroupPosition()),
                            "CREATION", "HappyGroupStage", "MANUAL_GROUP_MISMATCH",
                            "选手 " + p.getName() + " 落库 (" + p.getGroupNo() + "," + p.getGroupPosition()
                                    + ") 期望 (" + (gi + 1) + "," + (pi + 1) + ")");
                }
            }
        } else {
            int expectedMin = playerCount / groupCount;
            int expectedMax = (int) Math.ceil(playerCount * 1.0 / groupCount);
            int total = 0;
            for (Map.Entry<Integer, List<Player>> e : byGroup.entrySet()) {
                int size = e.getValue().size();
                total += size;
                check(size >= expectedMin && size <= expectedMax && size >= minPerGroup,
                        "CREATION", "HappyGroupStage", "AUTO_GROUP_SIZE",
                        "auto 分组第 " + e.getKey() + " 组人数=" + size + " 期望 [" + expectedMin + "," + expectedMax + "]");
                Set<Integer> positions = new HashSet<>();
                for (Player p : e.getValue()) positions.add(p.getGroupPosition());
                check(positions.size() == size, "CREATION", "HappyGroupStage", "AUTO_GROUP_POSITION_DUP",
                        "auto 分组第 " + e.getKey() + " 组座次重复: " + positions.size() + "/" + size);
            }
            check(total == playerCount, "CREATION", "HappyGroupStage", "AUTO_GROUP_TOTAL",
                    "auto 分组总人数=" + total + " 期望 " + playerCount);
        }

        // 小组赛场次：Σ C(size,2)、每对恰一场、groupNo 正确
        int expectedGroupMatches = 0;
        for (List<Player> group : byGroup.values()) {
            expectedGroupMatches += group.size() * (group.size() - 1) / 2;
        }
        check(matches.size() == expectedGroupMatches, "CREATION", "HappyGroupStage", "GROUP_MATCH_COUNT",
                "小组赛场次数=" + matches.size() + " 期望 " + expectedGroupMatches);
        Map<String, Integer> pairCount = new HashMap<>();
        for (MatchRecord m : matches) {
            check(Integer.valueOf(0).equals(m.getStageType()), "CREATION", "HappyGroupStage", "GROUP_STAGE_TYPE",
                    "小组赛 stageType=" + m.getStageType());
            check(m.getGroupNo() != null && m.getGroupNo() >= 1 && m.getGroupNo() <= groupCount,
                    "CREATION", "HappyGroupStage", "GROUP_NO_RANGE", "小组赛 groupNo=" + m.getGroupNo());
            if (m.getLeftPlayerId() == null || m.getRightPlayerId() == null) {
                check(false, "CREATION", "HappyGroupStage", "GROUP_MATCH_NULL_SIDE",
                        "小组赛出现空侧: " + m.getLeftPlayerId() + "/" + m.getRightPlayerId());
                continue;
            }
            int leftGroup = groupNoOf(roster, m.getLeftPlayerId());
            int rightGroup = groupNoOf(roster, m.getRightPlayerId());
            check(leftGroup == rightGroup && leftGroup == safeInt(m.getGroupNo()),
                    "CREATION", "HappyGroupStage", "GROUP_MATCH_CROSSING",
                    "小组赛跨组或 groupNo 不符: left=" + leftGroup + " right=" + rightGroup
                            + " matchGroupNo=" + m.getGroupNo());
            pairCount.merge(pairKey(m.getLeftPlayerId(), m.getRightPlayerId()), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : pairCount.entrySet()) {
            check(e.getValue() == 1, "CREATION", "HappyGroupStage", "GROUP_PAIR_REPEAT",
                    "小组赛对阵 " + e.getKey() + " 出现 " + e.getValue() + " 次");
        }

        cases.add(Map.of("scenario", "HappyGroupStage", "players", playerCount, "groups", groupCount,
                "qpg", qualifiers, "slots", knockoutSlots, "manualGroups", manualGroups,
                "groupMatches", matches.size()));
    }

    /** type2 个人赛：纯循环赛场次数与对阵重复度不变式。 */
    private void chaosHappyRoundRobin(Random rnd, long caseSeed) {
        CreationWorld w = buildWorld();
        int playerCount = 3 + rnd.nextInt(6);                // 3 ~ 8
        int rounds = 1 + rnd.nextInt(2);                     // 1 | 2

        CreateTournamentReq req = individualReq("个人循环赛_" + caseSeed, playerCount);
        req.setTournamentType(2);
        req.setRoundRobinRounds(rounds);

        String tournamentId = w.factory.createTournament(CREATOR, req);
        check(tournamentId != null && !tournamentId.isBlank(), "CREATION", "HappyRoundRobin", "NO_TOURNAMENT_ID",
                "创建未返回赛事 id");
        TournamentDivision division = w.divisions.get(0);
        Tournament tournament = w.tournaments.get(0);
        List<MatchRecord> matches = w.matchesOf(division.getId(), null);

        check(Integer.valueOf(2).equals(division.getTournamentType())
                        && Integer.valueOf(rounds).equals(division.getRoundRobinRounds())
                        && division.getKnockoutSlots() == null
                        && Integer.valueOf(1).equals(division.getStatus()),
                "CREATION", "HappyRoundRobin", "DIV_FIELDS",
                "division 字段异常: type=" + division.getTournamentType() + " rounds=" + division.getRoundRobinRounds()
                        + " slots=" + division.getKnockoutSlots() + " status=" + division.getStatus());
        check(Integer.valueOf(1).equals(tournament.getStatus())
                        && Integer.valueOf(2).equals(tournament.getTournamentType()),
                "CREATION", "HappyRoundRobin", "TOURNAMENT_MIRROR",
                "tournament status=" + tournament.getStatus() + " type=" + tournament.getTournamentType());

        int expected = playerCount * (playerCount - 1) / 2 * rounds;
        check(matches.size() == expected, "CREATION", "HappyRoundRobin", "MATCH_COUNT",
                "场次数=" + matches.size() + " 期望 " + expected);
        Map<String, Integer> pairCount = new HashMap<>();
        for (MatchRecord m : matches) {
            if (m.getLeftPlayerId() == null || m.getRightPlayerId() == null) {
                check(false, "CREATION", "HappyRoundRobin", "NULL_SIDE",
                        "循环赛出现空侧: " + m.getLeftPlayerId() + "/" + m.getRightPlayerId());
                continue;
            }
            check(!m.getLeftPlayerId().equals(m.getRightPlayerId()), "CREATION", "HappyRoundRobin", "SELF_MATCH",
                    "选手与自己对阵: " + m.getLeftPlayerId());
            pairCount.merge(pairKey(m.getLeftPlayerId(), m.getRightPlayerId()), 1, Integer::sum);
        }
        check(pairCount.size() == playerCount * (playerCount - 1) / 2, "CREATION", "HappyRoundRobin", "DISTINCT_PAIRS",
                "不同对阵数=" + pairCount.size() + " 期望 " + (playerCount * (playerCount - 1) / 2));
        for (Map.Entry<String, Integer> e : pairCount.entrySet()) {
            check(e.getValue() == rounds, "CREATION", "HappyRoundRobin", "PAIR_ROUNDS",
                    "对阵 " + e.getKey() + " 出现 " + e.getValue() + " 次 期望 " + rounds);
        }

        cases.add(Map.of("scenario", "HappyRoundRobin", "players", playerCount, "rounds", rounds,
                "matches", matches.size()));
    }

    /** 团体赛：template=1/3 + custom items + 队员/队长落库不变式。 */
    private void chaosHappyTeam(Random rnd, long caseSeed) {
        CreationWorld w = buildWorld();
        int template = rnd.nextBoolean() ? 1 : 3;
        int teamCount = 3 + rnd.nextInt(4);                  // 3 ~ 6
        int memberCount = 3 + rnd.nextInt(4);                // 每队 3 ~ 6 人

        List<int[]> typeCandidates = new ArrayList<>();
        typeCandidates.add(new int[]{0, 0, 0});
        typeCandidates.add(new int[]{2, 0, 0});
        for (int slots : new int[]{2, 4, 8}) {
            if (slots > teamCount) continue;
            for (int qpg : new int[]{1, 2}) {
                if (slots % qpg != 0) continue;
                int groups = slots / qpg;
                if (teamCount / groups >= Math.max(2, qpg)) typeCandidates.add(new int[]{1, slots, qpg});
            }
        }
        int[] typeChoice = typeCandidates.get(rnd.nextInt(typeCandidates.size()));

        CreateTournamentReq req = new CreateTournamentReq();
        req.setName("团体赛_" + caseSeed);
        req.setSportType(0);
        req.setParticipantType(1);
        req.setTeamMatchTemplate(template);
        req.setTournamentType(typeChoice[0]);
        if (typeChoice[0] == 1) {
            req.setKnockoutSlots(typeChoice[1]);
            req.setQualifiersPerGroup(typeChoice[2]);
        }
        if (typeChoice[0] == 2) req.setRoundRobinRounds(1 + rnd.nextInt(2));
        req.setRule(defaultRule());
        req.setTeams(buildTeams(teamCount, memberCount));

        List<String> expectedItemTypes = new ArrayList<>();
        if (template == 3) {
            int itemCount = choice(rnd, new Integer[]{3, 5, 7});
            String[] types = {"S", "D", "MS", "WS", "MD", "WD", "XD"};
            List<CreateTournamentReq.CustomItemSpec> specs = new ArrayList<>();
            for (int i = 0; i < itemCount; i++) {
                String type = choice(rnd, types);
                expectedItemTypes.add(type);
                CreateTournamentReq.CustomItemSpec spec = new CreateTournamentReq.CustomItemSpec();
                spec.setItemType(type);
                spec.setDisplayOrder(i + 1);   // 出厂顺序即期望顺序（列表被 shuffle 后由 displayOrder 还原）
                specs.add(spec);
            }
            Collections.shuffle(specs, rnd);
            req.setCustomItems(specs);
        }

        String tournamentId = w.factory.createTournament(CREATOR, req);
        check(tournamentId != null && !tournamentId.isBlank(), "CREATION", "HappyTeam", "NO_TOURNAMENT_ID",
                "创建未返回赛事 id");
        TournamentDivision division = w.divisions.get(0);
        Tournament tournament = w.tournaments.get(0);

        check(Integer.valueOf(1).equals(tournament.getParticipantType())
                        && Integer.valueOf(template).equals(tournament.getTeamMatchTemplate())
                        && Integer.valueOf(1).equals(tournament.getStatus()),
                "CREATION", "HappyTeam", "TOURNAMENT_FIELDS",
                "tournament participantType=" + tournament.getParticipantType() + " template="
                        + tournament.getTeamMatchTemplate() + " status=" + tournament.getStatus());
        check(Integer.valueOf(typeChoice[0]).equals(division.getTournamentType())
                        && Integer.valueOf(1).equals(division.getStatus()),
                "CREATION", "HappyTeam", "DIV_FIELDS",
                "division type=" + division.getTournamentType() + " status=" + division.getStatus());

        // 参赛单位=队伍，name/seed 落库
        List<Player> participants = w.playersInInsertionOrder(division.getId());
        check(participants.size() == teamCount, "CREATION", "HappyTeam", "PARTICIPANT_COUNT",
                "参赛队伍数=" + participants.size() + " 期望 " + teamCount);

        // 队员：每队恰 1 名队长 + 人数与入参一致（badminton 不落球衣号）
        for (Player participant : participants) {
            List<TournamentTeamMember> members = w.membersOfParticipant(participant.getId());
            check(members.size() == memberCount, "CREATION", "HappyTeam", "MEMBER_COUNT",
                    "队伍 " + participant.getName() + " 队员数=" + members.size() + " 期望 " + memberCount);
            long captains = members.stream().filter(m -> Boolean.TRUE.equals(m.getCaptain())).count();
            check(captains == 1, "CREATION", "HappyTeam", "CAPTAIN_COUNT",
                    "队伍 " + participant.getName() + " 队长数=" + captains);
            Set<Integer> orders = new HashSet<>();
            for (TournamentTeamMember member : members) {
                orders.add(member.getDisplayOrder());
                check(member.getJerseyNumber() == null, "CREATION", "HappyTeam", "JERSEY_PERSISTED",
                        "羽毛球团体赛不应落球衣号: " + member.getJerseyNumber());
            }
            check(orders.size() == members.size(), "CREATION", "HappyTeam", "MEMBER_ORDER_DUP",
                    "队伍 " + participant.getName() + " displayOrder 重复");
        }

        // 自定义子项落库
        if (template == 3) {
            List<TournamentCustomItem> items = new ArrayList<>(w.customItems);
            items.sort(Comparator.comparingInt(i -> safeInt(i.getDisplayOrder())));
            check(items.size() == expectedItemTypes.size(), "CREATION", "HappyTeam", "CUSTOM_ITEM_COUNT",
                    "custom item 条数=" + items.size() + " 期望 " + expectedItemTypes.size());
            for (int i = 0; i < items.size() && i < expectedItemTypes.size(); i++) {
                TournamentCustomItem item = items.get(i);
                String type = expectedItemTypes.get(i);
                boolean singles = "S".equals(type) || "MS".equals(type) || "WS".equals(type);
                check(type.equals(item.getItemType()) && (type + "_" + (i + 1)).equals(item.getItemCode())
                                && Integer.valueOf(i + 1).equals(item.getDisplayOrder())
                                && Integer.valueOf(singles ? 1 : 2).equals(item.getPlayerCount())
                                && item.getItemName() != null && !item.getItemName().isBlank(),
                        "CREATION", "HappyTeam", "CUSTOM_ITEM_SHAPE",
                        "custom item 第 " + (i + 1) + " 项=" + item.getItemType() + "/" + item.getItemCode()
                                + "/" + item.getDisplayOrder() + "/" + item.getPlayerCount() + " 期望类型 " + type);
                check(tournamentId.equals(item.getTournamentId()), "CREATION", "HappyTeam", "CUSTOM_ITEM_TOURNAMENT",
                        "custom item 关联赛事=" + item.getTournamentId());
            }
        } else {
            check(w.customItems.isEmpty(), "CREATION", "HappyTeam", "CUSTOM_ITEM_UNEXPECTED",
                    "template=1 却落库 custom item " + w.customItems.size() + " 条");
        }

        cases.add(Map.of("scenario", "HappyTeam", "template", template, "teams", teamCount,
                "membersPerTeam", memberCount, "tournamentType", typeChoice[0],
                "matches", w.matchesOf(division.getId(), null).size()));
    }

    // ==================================================================================
    // 场景 2: 恶意探针（断言被真实校验拒绝且理由与源码文案一致）
    // ==================================================================================
    private void chaosCreateMaliciousProbe(long caseSeed) {
        currentCaseSeed = caseSeed;
        Random rnd = new Random(caseSeed);
        List<Probe> pool = new ArrayList<>();
        pool.add(probeDrawModeIllegal());
        pool.add(probeManualOnGroupType());
        pool.add(probeManualGroupsOnKnockout());
        pool.add(probeManualOnRoundRobin());
        pool.add(probeGroupsWrongCount());
        pool.add(probeGroupsTooSmall());
        pool.add(probeGroupsDuplicate());
        pool.add(probeGroupsNotCovering());
        pool.add(probeGroupsOutOfRange());
        pool.add(probeGroupsNullElement());
        pool.add(probeSlotOrderWrongLength(rnd));
        pool.add(probeSlotOrderDuplicate());
        pool.add(probeSlotOrderOutOfRange());
        pool.add(probeSlotOrderDoubleBye());
        pool.add(probeQualifiersThree());
        pool.add(probeKnockoutSlotsNotPowerOfTwo());
        pool.add(probeSinglePlayer());
        pool.add(probeTeamNoCaptain());
        pool.add(probeTeamTwoCaptains());
        pool.add(probeCustomItemsFour());
        pool.add(probeCustomItemTypeUnknown());
        pool.add(probeRefereePasswordWeak());
        Collections.shuffle(pool, rnd);
        int probeCount = 3 + rnd.nextInt(3);                 // 3 ~ 5

        List<String> exercised = new ArrayList<>();
        for (int i = 0; i < probeCount && i < pool.size(); i++) {
            Probe probe = pool.get(i);
            exercised.add(probe.type());
            CreationWorld w = buildWorld();
            try {
                w.factory.createTournament(CREATOR, probe.req());
                check(false, "CREATION", "MaliciousProbe", probe.type() + "_ACCEPTED",
                        "非法输入未被真实校验拒绝: " + probe.desc());
            } catch (IllegalArgumentException | IllegalStateException rejected) {
                String message = rejected.getMessage();
                check(message != null && message.contains(probe.expectedPhrase()),
                        "CREATION", "MaliciousProbe", probe.type() + "_REASON_MISMATCH",
                        "拒绝理由与真实语义不符: got '" + message + "' 期望包含 '" + probe.expectedPhrase() + "'");
            } catch (RuntimeException other) {
                check(false, "CREATION", "MaliciousProbe", probe.type() + "_WRONG_EXCEPTION",
                        "非法输入抛出非预期异常 " + other.getClass().getName() + ": " + other.getMessage());
            }
        }

        cases.add(Map.of("scenario", "MaliciousProbe", "probes", probeCount,
                "kinds", String.join(",", exercised)));
    }

    // ------------------------------------------------------------------ 探针构造
    // 每条探针的 expectedPhrase 均取自源码真实文案，注释给出文件:行号。

    /** drawMode 非法取值 → TournamentCreationFactory.java:557。 */
    private Probe probeDrawModeIllegal() {
        CreateTournamentReq req = individualReq("probe-drawmode", 8);
        req.setTournamentType(0);
        req.setDrawMode("foo");
        return new Probe("DRAW_MODE_ILLEGAL", "drawMode=foo", req,
                "drawMode 仅支持 auto、manual 或 manual-groups");
    }

    /** type1 + manual（手写签表只支持纯淘汰赛）→ TournamentCreationFactory.java:1240-1242。 */
    private Probe probeManualOnGroupType() {
        CreateTournamentReq req = individualReq("probe-manual-on-group", 8);
        req.setTournamentType(1);
        req.setKnockoutSlots(4);
        req.setQualifiersPerGroup(2);
        req.setDrawMode("manual");
        req.setKnockoutSlotOrder(List.of(0, 1, 2, 3));
        return new Probe("MANUAL_ON_GROUP_TYPE", "tournamentType=1 + drawMode=manual", req,
                "手写签表仅支持纯淘汰赛（tournamentType=0）");
    }

    /** type0 + manual-groups → TournamentCreationFactory.java:1243-1245。 */
    private Probe probeManualGroupsOnKnockout() {
        CreateTournamentReq req = individualReq("probe-manual-groups-on-ko", 4);
        req.setTournamentType(0);
        req.setDrawMode("manual-groups");
        req.setGroups(List.of(List.of(0, 1), List.of(2, 3)));
        return new Probe("MANUAL_GROUPS_ON_KNOCKOUT", "tournamentType=0 + drawMode=manual-groups", req,
                "手写分组仅支持小组赛+淘汰赛（tournamentType=1）");
    }

    /** type2 + manual → TournamentCreationFactory.java:1240-1242。 */
    private Probe probeManualOnRoundRobin() {
        CreateTournamentReq req = individualReq("probe-manual-on-rr", 6);
        req.setTournamentType(2);
        req.setRoundRobinRounds(1);
        req.setDrawMode("manual");
        req.setKnockoutSlotOrder(List.of(0, 1, 2, 3, 4, 5, 6, 7));
        return new Probe("MANUAL_ON_ROUND_ROBIN", "tournamentType=2 + drawMode=manual", req,
                "手写签表仅支持纯淘汰赛（tournamentType=0）");
    }

    /** groups 组数不符 → TournamentCreationFactory.java:1373-1375。 */
    private Probe probeGroupsWrongCount() {
        CreateTournamentReq req = type1ManualGroupsReq(8, 4, 2);
        req.setGroups(List.of(List.of(0, 1, 2, 3, 4, 5, 6, 7)));
        return new Probe("GROUPS_WRONG_COUNT", "需要 2 组却给 1 组", req,
                "分组数量不匹配：当前赛制需要 2 组，收到 1 组");
    }

    /** 组过小 → TournamentCreationFactory.java:1382-1385。 */
    private Probe probeGroupsTooSmall() {
        CreateTournamentReq req = type1ManualGroupsReq(8, 4, 2);
        req.setGroups(List.of(Arrays.asList(0, 1, 2, 3, 4, 5, 6), List.of(7)));
        return new Probe("GROUPS_TOO_SMALL", "第 2 组只有 1 人（下限 2）", req,
                "第 2 组至少需要 2 个参赛单位");
    }

    /** 下标重复 → TournamentCreationFactory.java:1392-1394。 */
    private Probe probeGroupsDuplicate() {
        CreateTournamentReq req = type1ManualGroupsReq(8, 4, 2);
        req.setGroups(List.of(Arrays.asList(0, 0, 2, 3), Arrays.asList(1, 4, 5, 6)));
        return new Probe("GROUPS_DUPLICATE_INDEX", "下标 0 出现在两个签位", req,
                "名单第 1 位被重复放入分组");
    }

    /** 不全覆盖 → TournamentCreationFactory.java:1400-1402。 */
    private Probe probeGroupsNotCovering() {
        CreateTournamentReq req = type1ManualGroupsReq(8, 4, 2);
        req.setGroups(List.of(Arrays.asList(0, 1), Arrays.asList(2, 3)));
        return new Probe("GROUPS_NOT_COVERING", "8 人只覆盖 4 个下标", req,
                "还有 4 个参赛单位未放入分组");
    }

    /** 下标越界 → TournamentCreationFactory.java:1388-1391。 */
    private Probe probeGroupsOutOfRange() {
        CreateTournamentReq req = type1ManualGroupsReq(8, 4, 2);
        req.setGroups(List.of(Arrays.asList(0, 1, 2, 8), Arrays.asList(3, 4, 5, 6)));
        return new Probe("GROUPS_OUT_OF_RANGE", "下标 8 超出名单 [0,7]", req,
                "第 1 组第 4 个下标 8 超出名单范围 [0, 7]");
    }

    /** 内层 null 元素 → TournamentCreationFactory.java:1388-1391。 */
    private Probe probeGroupsNullElement() {
        CreateTournamentReq req = type1ManualGroupsReq(8, 4, 2);
        req.setGroups(List.of(Arrays.asList(0, 1, 2, null), Arrays.asList(3, 4, 5, 6)));
        return new Probe("GROUPS_NULL_ELEMENT", "第 1 组第 4 个下标为 null", req,
                "第 1 组第 4 个下标 null 超出名单范围 [0, 7]");
    }

    /** 签位数量不匹配 → TournamentCreationFactory.java:575-577。 */
    private Probe probeSlotOrderWrongLength(Random rnd) {
        CreateTournamentReq req = individualReq("probe-slot-length", 5);
        req.setTournamentType(0);
        req.setDrawMode("manual");
        List<Integer> order = new ArrayList<>(buildManualSlotOrder(5, 8, rnd));
        order.remove(0);
        req.setKnockoutSlotOrder(order);
        return new Probe("SLOT_ORDER_LENGTH", "容量 8 只给 7 个签位", req,
                "签位数量不匹配：当前赛制需要 8 个签位，收到 7");
    }

    /** 下标重复 → TournamentCreationFactory.java:590-592。 */
    private Probe probeSlotOrderDuplicate() {
        CreateTournamentReq req = individualReq("probe-slot-dup", 5);
        req.setTournamentType(0);
        req.setDrawMode("manual");
        req.setKnockoutSlotOrder(Arrays.asList(0, 0, 1, 2, 3, null, null, 4));
        return new Probe("SLOT_ORDER_DUPLICATE", "下标 0 放在签位 1、2", req,
                "名单第 1 位被重复放入签位 2");
    }

    /** 下标越界 → TournamentCreationFactory.java:587-589。 */
    private Probe probeSlotOrderOutOfRange() {
        CreateTournamentReq req = individualReq("probe-slot-range", 5);
        req.setTournamentType(0);
        req.setDrawMode("manual");
        req.setKnockoutSlotOrder(Arrays.asList(0, 5, 1, 2, 3, null, null, 4));
        return new Probe("SLOT_ORDER_OUT_OF_RANGE", "5 人名单出现下标 5", req,
                "签位 2 的下标 5 超出名单范围 [0, 4]");
    }

    /** 同一对阵位双轮空 → TournamentCreationFactory.java:598-601。 */
    private Probe probeSlotOrderDoubleBye() {
        CreateTournamentReq req = individualReq("probe-slot-double-bye", 5);
        req.setTournamentType(0);
        req.setDrawMode("manual");
        req.setKnockoutSlotOrder(Arrays.asList(null, null, 0, 1, 2, 3, 4, null));
        return new Probe("SLOT_ORDER_DOUBLE_BYE", "第 1 场（签位 1、2）均轮空", req,
                "均为轮空，请调整签位摆放");
    }

    /** qualifiersPerGroup=3 越界 → TournamentCreationFactory.java:1284-1286。 */
    private Probe probeQualifiersThree() {
        CreateTournamentReq req = individualReq("probe-qpg-three", 8);
        req.setTournamentType(1);
        req.setKnockoutSlots(4);
        req.setQualifiersPerGroup(3);
        return new Probe("QPG_THREE", "qualifiersPerGroup=3", req, "qualifiersPerGroup must be 1 or 2");
    }

    /** knockoutSlots 非 2 的幂 → TournamentCreationFactory.java:1278-1280。 */
    private Probe probeKnockoutSlotsNotPowerOfTwo() {
        CreateTournamentReq req = individualReq("probe-slots-pow2", 8);
        req.setTournamentType(1);
        req.setKnockoutSlots(6);
        req.setQualifiersPerGroup(2);
        return new Probe("SLOTS_NOT_POWER_OF_TWO", "knockoutSlots=6", req,
                "knockoutSlots must be a power of two and at least 2");
    }

    /** 个人赛 1 名选手 → TournamentCreationFactory.java:991-993（normalizePlayers）。 */
    private Probe probeSinglePlayer() {
        CreateTournamentReq req = individualReq("probe-single-player", 1);
        req.setTournamentType(0);
        return new Probe("SINGLE_PLAYER", "个人赛仅 1 名选手", req, "至少需要2名选手");
    }
    private Probe probeTeamNoCaptain() {
        CreateTournamentReq req = teamReq("probe-team-no-captain", 1, 2, 4);
        for (CreateTournamentReq.TeamEntry team : req.getTeams()) {
            for (CreateTournamentReq.TeamMemberEntry member : team.getMembers()) {
                member.setCaptain(Boolean.FALSE);   // buildTeams 默认每队 1 名队长，这里全部清掉
            }
        }
        return new Probe("TEAM_NO_CAPTAIN", "2 队 × 4 人全部 captain=false", req, "必须指定1名队长");
    }

    /** 团体赛 2 名队长 → TournamentCreationFactory.java:1092-1095。 */
    private Probe probeTeamTwoCaptains() {
        CreateTournamentReq req = teamReq("probe-team-two-captain", 1, 2, 4);
        for (CreateTournamentReq.TeamEntry team : req.getTeams()) {
            team.getMembers().get(1).setCaptain(Boolean.TRUE);
        }
        return new Probe("TEAM_TWO_CAPTAINS", "2 队 × 4 人各有 2 名 captain=true", req, "必须指定1名队长");
    }

    /** customItems 4 项 → TournamentCreationFactory.java:391-393。 */
    private Probe probeCustomItemsFour() {
        CreateTournamentReq req = teamReq("probe-custom-four", 3, 2, 3);
        req.setCustomItems(customItems(4, new String[]{"MS", "WS", "MD", "WD"}));
        return new Probe("CUSTOM_ITEMS_FOUR", "template=3 且子项 4 项", req,
                "自定义多项团体赛项数必须为 3、5 或 7");
    }

    /** customItems 类型非法 → TournamentCreationFactory.java:396-399。 */
    private Probe probeCustomItemTypeUnknown() {
        CreateTournamentReq req = teamReq("probe-custom-xx", 3, 2, 3);
        req.setCustomItems(customItems(3, new String[]{"MS", "XX", "WD"}));
        return new Probe("CUSTOM_ITEM_TYPE_UNKNOWN", "子项类型 XX", req, "不支持的子项类型: XX");
    }

    /** refereePassword 弱口令 → TournamentCreationFactory.java:1461-1463。 */
    private Probe probeRefereePasswordWeak() {
        CreateTournamentReq req = individualReq("probe-referee-pwd", 8);
        req.setTournamentType(0);
        req.setRefereePassword("abc");
        return new Probe("REFEREE_PASSWORD_WEAK", "refereePassword=abc", req, "裁判密码必须不少于8位数字");
    }

    // ==================================================================================
    // 场景 3: type1 手写分组重提交（updateGroupAssignments）
    // ==================================================================================
    private void chaosGroupAssignmentEdit(long caseSeed) {
        currentCaseSeed = caseSeed;
        Random rnd = new Random(caseSeed);
        CreationWorld w = buildWorld();

        // 创建 type1 + manual-groups：8 人 2 组（knockoutSlots=4、qpg=2、每组 4 人）
        CreateTournamentReq createReq = type1ManualGroupsReq(8, 4, 2);
        createReq.setName("重分组_" + caseSeed);
        List<List<Integer>> initialGroups = List.of(List.of(0, 1, 2, 3), List.of(4, 5, 6, 7));
        createReq.setGroups(initialGroups);
        String tournamentId = w.factory.createTournament(CREATOR, createReq);
        TournamentDivision division = w.divisions.get(0);
        List<Player> roster = w.rosterAsLoaded(division.getId());
        List<MatchRecord> initialGroupMatches = w.matchesOf(division.getId(), 0);
        Set<String> initialMatchIds = idsOf(initialGroupMatches);

        check(tournamentId != null && !tournamentId.isBlank() && roster.size() == 8,
                "GROUP_EDIT", "Setup", "SETUP_FAILED",
                "type1+manual-groups 创建失败: id=" + tournamentId + " roster=" + roster.size());
        check(initialGroupMatches.size() == 12, "GROUP_EDIT", "Setup", "INITIAL_MATCH_COUNT",
                "初始小组赛场次数=" + initialGroupMatches.size() + " 期望 12");

        // ---- 合法重分组：3/5 不均拆分（每组 ≥ max(2,qpg)=2），组内顺序随机 ----
        List<String> shuffled = new ArrayList<>(roster.stream().map(Player::getId).toList());
        List<List<String>> regrouped;
        do {
            Collections.shuffle(shuffled, rnd);
            regrouped = List.of(new ArrayList<>(shuffled.subList(0, 3)), new ArrayList<>(shuffled.subList(3, 8)));
        } while (sameAssignment(roster, regrouped));

        w.drawService.updateGroupAssignments(CREATOR, tournamentId, division.getId(), assignmentReq(regrouped));

        for (int gi = 0; gi < regrouped.size(); gi++) {
            for (int pi = 0; pi < regrouped.get(gi).size(); pi++) {
                Player p = playerById(roster, regrouped.get(gi).get(pi));
                check(p != null && Integer.valueOf(gi + 1).equals(p.getGroupNo())
                                && Integer.valueOf(pi + 1).equals(p.getGroupPosition()),
                        "GROUP_EDIT", "Regroup", "GROUP_NO_NOT_REWRITTEN",
                        "重分组后 " + regrouped.get(gi).get(pi) + " = ("
                                + (p == null ? "null" : p.getGroupNo() + "," + p.getGroupPosition())
                                + ") 期望 (" + (gi + 1) + "," + (pi + 1) + ")");
            }
        }
        Set<String> rebuiltIds = idsOf(w.matchesOf(division.getId(), 0));
        int expectedRegrouped = 3 * 2 / 2 + 5 * 4 / 2;
        check(rebuiltIds.size() == expectedRegrouped, "GROUP_EDIT", "Regroup", "REBUILT_MATCH_COUNT",
                "重分组后小组赛场次数=" + rebuiltIds.size() + " 期望 " + expectedRegrouped);
        check(Collections.disjoint(initialMatchIds, rebuiltIds), "GROUP_EDIT", "Regroup", "STALE_MATCH_ID",
                "重分组后仍存在旧 match id（应为全删全建）");
        checkGroupPairs(w.matchesOf(division.getId(), 0), roster, regrouped);

        // ---- 幂等重提交：内容未变化 → match id 稳定 ----
        w.drawService.updateGroupAssignments(CREATOR, tournamentId, division.getId(), assignmentReq(regrouped));
        Set<String> idempotentIds = idsOf(w.matchesOf(division.getId(), 0));
        check(idempotentIds.equals(rebuiltIds), "GROUP_EDIT", "Idempotent", "MATCH_IDS_CHANGED",
                "同内容重提交后 match id 变化（应短路返回）: " + rebuiltIds.size() + " -> " + idempotentIds.size());

        // ---- 探针：非法分组必须被拒绝，且校验先于删除（match id 不变） ----
        List<Object[]> probes = new ArrayList<>();
        probes.add(new Object[]{"GROUPS_WRONG_COUNT", List.of(new ArrayList<>(shuffled.subList(0, 8))),
                "分组数量不匹配：当前赛制需要 2 组，收到 1 组"});
        probes.add(new Object[]{"GROUPS_NULL_ID",
                Arrays.asList(Arrays.asList(shuffled.get(0), null, shuffled.get(1)),
                        new ArrayList<>(shuffled.subList(2, 8))),
                "第 1 组存在空的参赛单位条目"});
        probes.add(new Object[]{"GROUPS_DUPLICATE_ID",
                Arrays.asList(Arrays.asList(shuffled.get(0), shuffled.get(0), shuffled.get(1)),
                        new ArrayList<>(shuffled.subList(2, 8))),
                "参赛单位被重复放入分组"});
        probes.add(new Object[]{"GROUPS_MISSING_MEMBER",
                Arrays.asList(Arrays.asList(shuffled.get(0), shuffled.get(1)),
                        new ArrayList<>(shuffled.subList(2, 5))),
                "个参赛单位未放入分组"});
        probes.add(new Object[]{"GROUPS_FOREIGN_ID",
                Arrays.asList(Arrays.asList("ghost-player", shuffled.get(0), shuffled.get(1)),
                        new ArrayList<>(shuffled.subList(2, 8))),
                "第 1 组存在不属于本组别名册的参赛单位"});
        Collections.shuffle(probes, rnd);

        for (Object[] probe : probes) {
            @SuppressWarnings("unchecked")
            List<List<String>> groups = (List<List<String>>) probe[1];
            try {
                w.drawService.updateGroupAssignments(CREATOR, tournamentId, division.getId(), assignmentReq(groups));
                check(false, "GROUP_EDIT", "MaliciousProbe", probe[0] + "_ACCEPTED",
                        "非法分组未被真实校验拒绝: " + probe[0]);
            } catch (IllegalArgumentException | IllegalStateException rejected) {
                String message = rejected.getMessage();
                check(message != null && message.contains((String) probe[2]),
                        "GROUP_EDIT", "MaliciousProbe", probe[0] + "_REASON_MISMATCH",
                        "拒绝理由与真实语义不符: got '" + message + "' 期望包含 '" + probe[2] + "'");
            } catch (RuntimeException other) {
                check(false, "GROUP_EDIT", "MaliciousProbe", probe[0] + "_WRONG_EXCEPTION",
                        "非法分组抛出非预期异常 " + other.getClass().getName() + ": " + other.getMessage());
            }
            Set<String> after = idsOf(w.matchesOf(division.getId(), 0));
            check(after.equals(rebuiltIds), "GROUP_EDIT", "MaliciousProbe", probe[0] + "_MATCHES_MUTATED",
                    "被拒绝的分组请求改动了小组赛赛程（校验应先于删除）");
        }

        List<String> probeKinds = new ArrayList<>();
        for (Object[] probe : probes) probeKinds.add((String) probe[0]);
        cases.add(Map.of("scenario", "GroupAssignmentEdit", "regrouped", true,
                "matchesAfterRegroup", rebuiltIds.size(), "probes", probes.size(),
                "kinds", String.join(",", probeKinds)));
    }


    /** 守卫探针用世界（drawMode=0 的 type1 / type0 赛事），惰性构建、跨案例复用。 */
    private void ensureGuardWorlds() {
        if (guardType1AutoWorld != null) {
            return;
        }
        CreationWorld auto = buildWorld();
        CreateTournamentReq autoReq = individualReq("guard-auto-type1", 8);
        autoReq.setTournamentType(1);
        autoReq.setKnockoutSlots(8);
        autoReq.setQualifiersPerGroup(2);
        auto.factory.createTournament(CREATOR, autoReq);
        guardType1AutoWorld = auto;

        CreationWorld knockout = buildWorld();
        CreateTournamentReq knockoutReq = individualReq("guard-type0", 4);
        knockoutReq.setTournamentType(0);
        knockout.factory.createTournament(CREATOR, knockoutReq);
        guardType0World = knockout;
    }

    private void chaosGroupAssignmentGuards(long caseSeed) {
        currentCaseSeed = caseSeed;
        ensureGuardWorlds();
        Random rnd = new Random(caseSeed);
        CreationWorld[] worlds = {guardType1AutoWorld, guardType0World};
        String[][] expectations = {
                {"GUARD_AUTO_MODE", "仅手写分组（drawMode=manual-groups）的赛事支持调整分组"},
                {"GUARD_NOT_GROUP_TYPE", "仅小组赛+淘汰赛支持调整分组"}
        };
        int start = rnd.nextInt(2);
        for (int k = 0; k < 2; k++) {
            int index = (start + k) % 2;
            CreationWorld w = worlds[index];
            TournamentDivision division = w.divisions.get(0);
            List<Player> roster = w.rosterAsLoaded(division.getId());
            List<List<String>> groups = List.of(
                    roster.subList(0, roster.size() / 2).stream().map(Player::getId).toList(),
                    roster.subList(roster.size() / 2, roster.size()).stream().map(Player::getId).toList());
            try {
                w.drawService.updateGroupAssignments(CREATOR, w.tournaments.get(0).getId(), division.getId(),
                        assignmentReq(groups));
                check(false, "GROUP_EDIT", "GuardProbe", expectations[index][0] + "_ACCEPTED",
                        "非手写分组赛事的分组编辑未被拒绝: " + expectations[index][1]);
            } catch (IllegalArgumentException | IllegalStateException rejected) {
                String message = rejected.getMessage();
                check(message != null && message.contains(expectations[index][1]),
                        "GROUP_EDIT", "GuardProbe", expectations[index][0] + "_REASON_MISMATCH",
                        "守卫拒绝理由与真实语义不符: got '" + message + "' 期望包含 '" + expectations[index][1] + "'");
            } catch (RuntimeException other) {
                check(false, "GROUP_EDIT", "GuardProbe", expectations[index][0] + "_WRONG_EXCEPTION",
                        "守卫探针抛出非预期异常 " + other.getClass().getName() + ": " + other.getMessage());
            }
        }
        cases.add(Map.of("scenario", "GroupAssignmentGuard", "guards", 2));
    }

    // ==================================================================================
    // 场景 4: 多组别创建（手写签表/手写分组推广到多组别赛事）
    // ==================================================================================

    /** 多组别蓝图：一个组别的随机规格（divisions[] 每项）。 */
    private record DivisionBlueprint(String name, int tournamentType, String drawMode,
                                     int playerCount, int knockoutRounds,
                                     int knockoutSlots, int qualifiersPerGroup, int roundRobinRounds,
                                     List<Integer> slotOrder, List<List<Integer>> groups) {
    }

    /**
     * 多组别合法创建：随机 2~4 个组别，每组别随机 tournamentType（type0/type1 为主，少量 type2）
     * 与匹配的 drawMode（type0→auto/manual，type1→auto/manual-groups，type2 仅 auto）。
     * 逐组别断言：draw_mode 与蓝图一致；type0-manual 签位摆放 + 轮空坍缩 + 晋级链；
     * type1-manual-groups 的 group_no/group_position 与蓝图 groups 一致；auto 路径沿用单组别不变式；
     * 最后断言 match_record 按组别守恒（总场次数 = Σ 各组别场次数）。
     */
    private void chaosMultiDivisionHappyPath(long caseSeed) {
        currentCaseSeed = caseSeed;
        Random rnd = new Random(caseSeed);
        CreationWorld w = buildWorld();

        int divisionCount = 2 + rnd.nextInt(3);              // 2 ~ 4
        List<DivisionBlueprint> blueprints = new ArrayList<>();
        for (int i = 0; i < divisionCount; i++) {
            int roll = rnd.nextInt(10);
            if (roll < 4) {
                blueprints.add(knockoutBlueprint("组别" + i, rnd));
            } else if (roll < 9) {
                blueprints.add(groupStageBlueprint("组别" + i, rnd));
            } else {
                blueprints.add(roundRobinBlueprint("组别" + i, rnd));
            }
        }

        CreateTournamentReq req = new CreateTournamentReq();
        req.setName("多组别_" + caseSeed);
        req.setSportType(0);
        req.setParticipantType(0);
        List<CreateTournamentReq.DivisionSpec> specs = new ArrayList<>();
        for (DivisionBlueprint bp : blueprints) {
            CreateTournamentReq.DivisionSpec spec = new CreateTournamentReq.DivisionSpec();
            spec.setName(bp.name());
            spec.setTournamentType(bp.tournamentType());
            if (!"auto".equals(bp.drawMode())) {
                spec.setDrawMode(bp.drawMode());
            }
            if (bp.tournamentType() == 1) {
                spec.setKnockoutSlots(bp.knockoutSlots());
                spec.setQualifiersPerGroup(bp.qualifiersPerGroup());
            }
            if (bp.tournamentType() == 2) {
                spec.setRoundRobinRounds(bp.roundRobinRounds());
            }
            if ("manual".equals(bp.drawMode())) {
                spec.setKnockoutSlotOrder(bp.slotOrder());
            }
            if ("manual-groups".equals(bp.drawMode())) {
                spec.setGroups(bp.groups());
            }
            spec.setRule(defaultRule());
            spec.setPlayers(playerEntries(bp.playerCount()));
            specs.add(spec);
        }
        req.setDivisions(specs);

        String tournamentId = w.factory.createTournament(CREATOR, req);
        check(tournamentId != null && !tournamentId.isBlank(), "CREATION", "MultiDivisionHappy", "NO_TOURNAMENT_ID",
                "创建未返回赛事 id");
        check(w.divisions.size() == divisionCount, "CREATION", "MultiDivisionHappy", "DIVISION_COUNT",
                "组别数=" + w.divisions.size() + " 期望 " + divisionCount);
        check(w.tournaments.size() == 1 && Integer.valueOf(1).equals(w.tournaments.get(0).getStatus()),
                "CREATION", "MultiDivisionHappy", "TOURNAMENT_STATUS",
                "tournament 数=" + w.tournaments.size() + " status="
                        + (w.tournaments.isEmpty() ? null : w.tournaments.get(0).getStatus()));

        int matchTotal = 0;
        List<String> typesSummary = new ArrayList<>();
        for (int i = 0; i < blueprints.size() && i < w.divisions.size(); i++) {
            DivisionBlueprint bp = blueprints.get(i);
            TournamentDivision division = w.divisions.get(i);
            check(bp.name().equals(division.getName()) && Integer.valueOf(i).equals(division.getSortOrder()),
                    "CREATION", "MultiDivisionHappy", "DIVISION_ORDER",
                    "组别 " + i + " name/sortOrder=" + division.getName() + "/" + division.getSortOrder());
            switch (bp.tournamentType()) {
                case 0 -> {
                    checkKnockoutDivisionInvariants(w, bp, division);
                    matchTotal += w.matchesOf(division.getId(), 1).size();
                    typesSummary.add("type0:" + bp.drawMode());
                }
                case 1 -> {
                    checkGroupDivisionInvariants(w, bp, division);
                    matchTotal += w.matchesOf(division.getId(), 0).size();
                    typesSummary.add("type1:" + bp.drawMode());
                }
                default -> {
                    checkRoundRobinDivisionInvariants(w, bp, division);
                    matchTotal += w.matchesOf(division.getId(), null).size();
                    typesSummary.add("type2");
                }
            }
        }
        // match_record.division_id 守恒：每条 match 恰属于一个组别（总数 = Σ 各组别）
        check(w.matches.size() == matchTotal, "CREATION", "MultiDivisionHappy", "MATCH_DIVISION_ISOLATION",
                "match 总数=" + w.matches.size() + " 各组别之和=" + matchTotal);

        cases.add(Map.of("scenario", "MultiDivisionHappy", "divisions", divisionCount,
                "types", String.join(",", typesSummary), "matches", w.matches.size()));
    }

    /** type0 蓝图：manual 概率 50%；4~11 人，容量由人数推导（=2^rounds）。 */
    private DivisionBlueprint knockoutBlueprint(String name, Random rnd) {
        int playerCount = 4 + rnd.nextInt(8);                // 4 ~ 11
        int rounds = Integer.numberOfTrailingZeros(nextPowerOfTwo(playerCount));
        boolean manual = rnd.nextBoolean();
        List<Integer> slotOrder = manual ? buildManualSlotOrder(playerCount, 1 << rounds, rnd) : null;
        return new DivisionBlueprint(name, 0, manual ? "manual" : "auto", playerCount, rounds,
                0, 0, 0, slotOrder, null);
    }

    /** type1 蓝图：manual-groups 概率 50%；组合 (slots,qpg) ∈ {(4,2),(8,2),(4,1)}，每组 3~4 人、可 1 人不均。 */
    private DivisionBlueprint groupStageBlueprint(String name, Random rnd) {
        int[][] combos = {{4, 2}, {8, 2}, {4, 1}};
        int[] combo = combos[rnd.nextInt(combos.length)];
        int knockoutSlots = combo[0];
        int qualifiers = combo[1];
        int groupCount = knockoutSlots / qualifiers;
        int[] sizes = new int[groupCount];
        Arrays.fill(sizes, 3 + rnd.nextInt(2));              // 每组 3~4 人
        if (groupCount >= 2 && rnd.nextBoolean()) {          // 允许不均：组 1 让 1 人给组 2（仍 ≥ 下限 2）
            sizes[0]--;
            sizes[1]++;
        }
        int playerCount = Arrays.stream(sizes).sum();
        boolean manualGroups = rnd.nextBoolean();
        List<List<Integer>> groups = manualGroups ? partitionIndices(playerCount, sizes, rnd) : null;
        return new DivisionBlueprint(name, 1, manualGroups ? "manual-groups" : "auto", playerCount, 0,
                knockoutSlots, qualifiers, 0, null, groups);
    }

    /** type2 蓝图：纯循环赛 3~6 人、1~2 轮（无手写模式）。 */
    private DivisionBlueprint roundRobinBlueprint(String name, Random rnd) {
        int playerCount = 3 + rnd.nextInt(4);                // 3 ~ 6
        int rounds = 1 + rnd.nextInt(2);                     // 1 | 2
        return new DivisionBlueprint(name, 2, "auto", playerCount, 0, 0, 0, rounds, null, null);
    }

    /** 多组别 type0 组别不变式：draw_mode / 容量 / 首轮签位 / 轮空坍缩 / 晋级链。 */
    private void checkKnockoutDivisionInvariants(CreationWorld w, DivisionBlueprint bp, TournamentDivision division) {
        String scenario = "MultiDivisionHappy";
        boolean manual = "manual".equals(bp.drawMode());
        List<Player> roster = w.playersInInsertionOrder(division.getId());
        List<MatchRecord> matches = w.matchesOf(division.getId(), 1);
        Integer rounds = division.getKnockoutRounds();
        int capacity = 1 << safeInt(rounds);

        check(roster.size() == bp.playerCount(), "CREATION", scenario, "DIV_ROSTER",
                "组别 " + bp.name() + " 人数=" + roster.size() + " 期望 " + bp.playerCount());
        check(Integer.valueOf(0).equals(division.getTournamentType())
                        && Integer.valueOf(manual ? 1 : 0).equals(division.getDrawMode())
                        && rounds != null && (1 << rounds) == nextPowerOfTwo(bp.playerCount())
                        && division.getKnockoutSlots() == null && division.getQualifiersPerGroup() == null
                        && Integer.valueOf(1).equals(division.getStatus()),
                "CREATION", scenario, "DIV_FIELDS",
                "组别 " + bp.name() + " type=" + division.getTournamentType() + " drawMode=" + division.getDrawMode()
                        + " rounds=" + rounds + " slots=" + division.getKnockoutSlots()
                        + " qpg=" + division.getQualifiersPerGroup() + " status=" + division.getStatus());
        int expectedTotal = capacity - 1;
        check(matches.size() == expectedTotal, "CREATION", scenario, "MATCH_COUNT",
                "组别 " + bp.name() + " 场次数=" + matches.size() + " 期望 " + expectedTotal);

        Map<String, MatchRecord> slotMatches = new HashMap<>();
        for (MatchRecord m : matches) {
            slotMatches.put(safeInt(m.getRoundNum()) + ":" + safeInt(m.getMatchIndex()), m);
        }
        Map<String, Integer> slotOccurrence = new HashMap<>();
        for (int i = 0; i < capacity / 2; i++) {
            MatchRecord m = slotMatches.get("1:" + i);
            check(m != null, "CREATION", scenario, "FIRST_ROUND_MISSING",
                    "组别 " + bp.name() + " 首轮第 " + i + " 场缺失（容量 " + capacity + "）");
            if (m == null) continue;
            String left = m.getLeftPlayerId();
            String right = m.getRightPlayerId();
            check(!(left == null && right == null), "CREATION", scenario, "DOUBLE_BYE",
                    "组别 " + bp.name() + " 首轮第 " + i + " 场两侧均为轮空");
            countOccurrence(slotOccurrence, left);
            countOccurrence(slotOccurrence, right);

            if (manual) {
                Integer slotLeft = bp.slotOrder().get(i * 2);
                Integer slotRight = bp.slotOrder().get(i * 2 + 1);
                String expectedLeft = slotLeft == null ? null : roster.get(slotLeft).getId();
                String expectedRight = slotRight == null ? null : roster.get(slotRight).getId();
                check(Objects.equals(left, expectedLeft) && Objects.equals(right, expectedRight),
                        "CREATION", scenario, "MANUAL_SLOT_ORDER_MISMATCH",
                        "组别 " + bp.name() + " 首轮第 " + i + " 场 left/right=" + left + "/" + right
                                + " 期望 " + expectedLeft + "/" + expectedRight);
            }

            boolean oneSided = (left == null) != (right == null);
            if (oneSided) {
                String winner = left != null ? left : right;
                check(Integer.valueOf(2).equals(m.getStatus()) && Objects.equals(winner, m.getWinnerId()),
                        "CREATION", scenario, "BYE_NOT_COLLAPSED",
                        "组别 " + bp.name() + " 首轮第 " + i + " 场单侧轮空但 status=" + m.getStatus()
                                + " winnerId=" + m.getWinnerId());
                MatchRecord parent = slotMatches.get("2:" + (i / 2));
                check(parent != null, "CREATION", scenario, "BYE_PARENT_MISSING",
                        "组别 " + bp.name() + " 轮空坍缩时父场缺失: " + m.getNextMatchId());
                if (parent != null) {
                    String parentSlot = Objects.equals(m.getNextMatchSlot(), "right") ? "right" : "left";
                    String propagated = "right".equals(parentSlot) ? parent.getRightPlayerId() : parent.getLeftPlayerId();
                    check(Objects.equals(winner, propagated), "CREATION", scenario, "BYE_NOT_PROPAGATED",
                            "组别 " + bp.name() + " 轮空胜者未传播到父场 " + parentSlot + " 槽位: got " + propagated
                                    + " 期望 " + winner);
                }
            } else {
                check(!Integer.valueOf(2).equals(m.getStatus()), "CREATION", scenario, "NORMAL_MATCH_PREFINISHED",
                        "组别 " + bp.name() + " 首轮第 " + i + " 场双侧有选手却被置为已完赛");
            }
        }
        for (Player p : roster) {
            check(Integer.valueOf(1).equals(slotOccurrence.get(p.getId())), "CREATION", scenario, "PLAYER_SLOT_DUP",
                    "组别 " + bp.name() + " 选手 " + p.getName() + " 首轮签位出现次数=" + slotOccurrence.get(p.getId()));
        }

        if (rounds != null) {
            for (int r = 1; r <= rounds; r++) {
                int count = capacity >> r;
                for (int i = 0; i < count; i++) {
                    MatchRecord child = slotMatches.get(r + ":" + i);
                    check(child != null, "CREATION", scenario, "CHAIN_NODE_MISSING",
                            "组别 " + bp.name() + " 晋级链缺少 round " + r + " 第 " + i + " 场");
                    if (child == null) continue;
                    if (r == rounds) {
                        check(child.getNextMatchId() == null && child.getNextMatchSlot() == null,
                                "CREATION", scenario, "FINAL_HAS_NEXT",
                                "组别 " + bp.name() + " 决赛仍有 next: " + child.getNextMatchId());
                    } else {
                        MatchRecord parent = slotMatches.get((r + 1) + ":" + (i / 2));
                        check(parent != null && Objects.equals(child.getNextMatchId(), parent.getId()),
                                "CREATION", scenario, "CHAIN_NEXT_MATCH",
                                "组别 " + bp.name() + " round " + r + " 第 " + i + " 场 nextMatchId="
                                        + child.getNextMatchId());
                        check(Objects.equals(child.getNextMatchSlot(), i % 2 == 0 ? "left" : "right"),
                                "CREATION", scenario, "CHAIN_NEXT_SLOT",
                                "组别 " + bp.name() + " round " + r + " 第 " + i + " 场 nextMatchSlot="
                                        + child.getNextMatchSlot());
                    }
                }
            }
        }
    }

    /** 多组别 type1 组别不变式：draw_mode / 分组座次 / 小组赛场次（淘汰赛延后）。 */
    private void checkGroupDivisionInvariants(CreationWorld w, DivisionBlueprint bp, TournamentDivision division) {
        String scenario = "MultiDivisionHappy";
        boolean manualGroups = "manual-groups".equals(bp.drawMode());
        int groupCount = bp.knockoutSlots() / bp.qualifiersPerGroup();
        List<Player> roster = w.playersInInsertionOrder(division.getId());
        List<MatchRecord> matches = w.matchesOf(division.getId(), 0);

        check(w.matchesOf(division.getId(), 1).isEmpty(), "CREATION", scenario, "KNOCKOUT_PREGENERATED",
                "组别 " + bp.name() + " 创建期出现 stageType=1 场次");
        check(Integer.valueOf(1).equals(division.getTournamentType())
                        && Integer.valueOf(manualGroups ? 2 : 0).equals(division.getDrawMode())
                        && Integer.valueOf(bp.knockoutSlots()).equals(division.getKnockoutSlots())
                        && Integer.valueOf(bp.qualifiersPerGroup()).equals(division.getQualifiersPerGroup())
                        && Boolean.FALSE.equals(division.getKnockoutGenerated())
                        && Integer.valueOf(1).equals(division.getStatus()),
                "CREATION", scenario, "DIV_FIELDS",
                "组别 " + bp.name() + " type=" + division.getTournamentType() + " drawMode=" + division.getDrawMode()
                        + " slots=" + division.getKnockoutSlots() + " qpg=" + division.getQualifiersPerGroup()
                        + " generated=" + division.getKnockoutGenerated() + " status=" + division.getStatus());

        Map<Integer, List<Player>> byGroup = new HashMap<>();
        for (Player p : roster) {
            check(p.getGroupNo() != null && p.getGroupPosition() != null, "CREATION", scenario, "PLAYER_UNASSIGNED",
                    "组别 " + bp.name() + " 选手 " + p.getName() + " 未分组");
            if (p.getGroupNo() == null) continue;
            byGroup.computeIfAbsent(p.getGroupNo(), k -> new ArrayList<>()).add(p);
        }
        check(byGroup.size() == groupCount, "CREATION", scenario, "GROUP_COUNT",
                "组别 " + bp.name() + " 落库组数=" + byGroup.size() + " 期望 " + groupCount);

        if (manualGroups) {
            for (int gi = 0; gi < bp.groups().size(); gi++) {
                List<Integer> group = bp.groups().get(gi);
                for (int pi = 0; pi < group.size(); pi++) {
                    Player p = roster.get(group.get(pi));
                    check(Integer.valueOf(gi + 1).equals(p.getGroupNo())
                                    && Integer.valueOf(pi + 1).equals(p.getGroupPosition()),
                            "CREATION", scenario, "MANUAL_GROUP_MISMATCH",
                            "组别 " + bp.name() + " 选手 " + p.getName() + " 落库 ("
                                    + p.getGroupNo() + "," + p.getGroupPosition() + ") 期望 ("
                                    + (gi + 1) + "," + (pi + 1) + ")");
                }
            }
        } else {
            int expectedMin = bp.playerCount() / groupCount;
            int expectedMax = (int) Math.ceil(bp.playerCount() * 1.0 / groupCount);
            int total = 0;
            for (Map.Entry<Integer, List<Player>> e : byGroup.entrySet()) {
                int size = e.getValue().size();
                total += size;
                check(size >= expectedMin && size <= expectedMax && size >= Math.max(2, bp.qualifiersPerGroup()),
                        "CREATION", scenario, "AUTO_GROUP_SIZE",
                        "组别 " + bp.name() + " auto 分组第 " + e.getKey() + " 组人数=" + size
                                + " 期望 [" + expectedMin + "," + expectedMax + "]");
                Set<Integer> positions = new HashSet<>();
                for (Player p : e.getValue()) positions.add(p.getGroupPosition());
                check(positions.size() == size, "CREATION", scenario, "AUTO_GROUP_POSITION_DUP",
                        "组别 " + bp.name() + " auto 分组第 " + e.getKey() + " 组座次重复");
            }
            check(total == bp.playerCount(), "CREATION", scenario, "AUTO_GROUP_TOTAL",
                    "组别 " + bp.name() + " auto 分组总人数=" + total + " 期望 " + bp.playerCount());
        }

        int expectedGroupMatches = 0;
        for (List<Player> group : byGroup.values()) {
            expectedGroupMatches += group.size() * (group.size() - 1) / 2;
        }
        check(matches.size() == expectedGroupMatches, "CREATION", scenario, "GROUP_MATCH_COUNT",
                "组别 " + bp.name() + " 小组赛场次数=" + matches.size() + " 期望 " + expectedGroupMatches);
        Map<String, Integer> pairCount = new HashMap<>();
        for (MatchRecord m : matches) {
            check(Integer.valueOf(0).equals(m.getStageType()), "CREATION", scenario, "GROUP_STAGE_TYPE",
                    "组别 " + bp.name() + " 小组赛 stageType=" + m.getStageType());
            if (m.getLeftPlayerId() == null || m.getRightPlayerId() == null) {
                check(false, "CREATION", scenario, "GROUP_MATCH_NULL_SIDE",
                        "组别 " + bp.name() + " 小组赛出现空侧");
                continue;
            }
            int leftGroup = groupNoOf(roster, m.getLeftPlayerId());
            int rightGroup = groupNoOf(roster, m.getRightPlayerId());
            check(leftGroup == rightGroup && leftGroup == safeInt(m.getGroupNo()),
                    "CREATION", scenario, "GROUP_MATCH_CROSSING",
                    "组别 " + bp.name() + " 小组赛跨组或 groupNo 不符: left=" + leftGroup + " right=" + rightGroup
                            + " matchGroupNo=" + m.getGroupNo());
            pairCount.merge(pairKey(m.getLeftPlayerId(), m.getRightPlayerId()), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : pairCount.entrySet()) {
            check(e.getValue() == 1, "CREATION", scenario, "GROUP_PAIR_REPEAT",
                    "组别 " + bp.name() + " 小组赛对阵 " + e.getKey() + " 出现 " + e.getValue() + " 次");
        }
    }

    /** 多组别 type2 组别不变式：循环赛场次数与对阵重复度。 */
    private void checkRoundRobinDivisionInvariants(CreationWorld w, DivisionBlueprint bp, TournamentDivision division) {
        String scenario = "MultiDivisionHappy";
        List<Player> roster = w.playersInInsertionOrder(division.getId());
        List<MatchRecord> matches = w.matchesOf(division.getId(), null);
        check(roster.size() == bp.playerCount(), "CREATION", scenario, "DIV_ROSTER",
                "组别 " + bp.name() + " 人数=" + roster.size() + " 期望 " + bp.playerCount());
        check(Integer.valueOf(2).equals(division.getTournamentType())
                        && Integer.valueOf(bp.roundRobinRounds()).equals(division.getRoundRobinRounds())
                        && Integer.valueOf(0).equals(division.getDrawMode())
                        && Integer.valueOf(1).equals(division.getStatus()),
                "CREATION", scenario, "DIV_FIELDS",
                "组别 " + bp.name() + " type=" + division.getTournamentType() + " rounds="
                        + division.getRoundRobinRounds() + " drawMode=" + division.getDrawMode()
                        + " status=" + division.getStatus());
        int expected = bp.playerCount() * (bp.playerCount() - 1) / 2 * bp.roundRobinRounds();
        check(matches.size() == expected, "CREATION", scenario, "MATCH_COUNT",
                "组别 " + bp.name() + " 场次数=" + matches.size() + " 期望 " + expected);
        Map<String, Integer> pairCount = new HashMap<>();
        for (MatchRecord m : matches) {
            if (m.getLeftPlayerId() == null || m.getRightPlayerId() == null) {
                check(false, "CREATION", scenario, "NULL_SIDE", "组别 " + bp.name() + " 循环赛出现空侧");
                continue;
            }
            pairCount.merge(pairKey(m.getLeftPlayerId(), m.getRightPlayerId()), 1, Integer::sum);
        }
        check(pairCount.size() == bp.playerCount() * (bp.playerCount() - 1) / 2, "CREATION", scenario, "DISTINCT_PAIRS",
                "组别 " + bp.name() + " 不同对阵数=" + pairCount.size());
        for (Map.Entry<String, Integer> e : pairCount.entrySet()) {
            check(e.getValue() == bp.roundRobinRounds(), "CREATION", scenario, "PAIR_ROUNDS",
                    "组别 " + bp.name() + " 对阵 " + e.getKey() + " 出现 " + e.getValue()
                            + " 次 期望 " + bp.roundRobinRounds());
        }
    }

    // ------------------------------------------------------------------ 多组别恶意探针

    /**
     * 多组别 payload 的逐组别非法组合探针：基础 payload 三组别合法
     * （type0-manual + type1-manual-groups + type0-auto），每条探针只破坏一个组别的一个字段，
     * 断言被 IllegalArgumentException / IllegalStateException 拒绝且消息包含源码真实文案子串。
     */
    private void chaosMultiDivisionMaliciousProbe(long caseSeed) {
        currentCaseSeed = caseSeed;
        Random rnd = new Random(caseSeed);
        List<Probe> pool = new ArrayList<>();
        pool.add(multiDivisionProbeManualOnGroupType());
        pool.add(multiDivisionProbeManualGroupsOnKnockout());
        pool.add(multiDivisionProbeGroupsOnNonManualGroups());
        pool.add(multiDivisionProbeSlotOrderWrongLength());
        pool.add(multiDivisionProbeSlotOrderDuplicate());
        pool.add(multiDivisionProbeSlotOrderOutOfRange());
        pool.add(multiDivisionProbeSlotOrderDoubleBye());
        pool.add(multiDivisionProbeMixedWithTopLevel());
        pool.add(multiDivisionProbeMixedWithTopLevelKnockoutRounds());
        pool.add(multiDivisionProbeMixedWithTopLevelRoundRobinRounds());
        pool.add(multiDivisionProbeMixedWithTopLevelRoundRuleEnabled());
        pool.add(multiDivisionProbeMixedWithTopLevelThirdPlaceEnabled());
        pool.add(multiDivisionProbeMixedWithTopLevelThirdPlaceRule());
        Collections.shuffle(pool, rnd);
        int probeCount = 3 + rnd.nextInt(3);                 // 3 ~ 5

        List<String> exercised = new ArrayList<>();
        for (int i = 0; i < probeCount && i < pool.size(); i++) {
            Probe probe = pool.get(i);
            exercised.add(probe.type());
            CreationWorld w = buildWorld();
            try {
                w.factory.createTournament(CREATOR, probe.req());
                check(false, "CREATION", "MultiDivisionProbe", probe.type() + "_ACCEPTED",
                        "非法输入未被真实校验拒绝: " + probe.desc());
            } catch (IllegalArgumentException | IllegalStateException rejected) {
                String message = rejected.getMessage();
                check(message != null && message.contains(probe.expectedPhrase()),
                        "CREATION", "MultiDivisionProbe", probe.type() + "_REASON_MISMATCH",
                        "拒绝理由与真实语义不符: got '" + message + "' 期望包含 '" + probe.expectedPhrase() + "'");
            } catch (RuntimeException other) {
                check(false, "CREATION", "MultiDivisionProbe", probe.type() + "_WRONG_EXCEPTION",
                        "非法输入抛出非预期异常 " + other.getClass().getName() + ": " + other.getMessage());
            }
        }

        cases.add(Map.of("scenario", "MultiDivisionProbe", "probes", probeCount,
                "kinds", String.join(",", exercised)));
    }

    /** 多组别探针基础 payload（三组别自身完全合法，每次调用新建可变副本）。 */
    private CreateTournamentReq multiDivisionProbeBase() {
        CreateTournamentReq req = new CreateTournamentReq();
        req.setName("probe-multi-division");
        req.setSportType(0);
        req.setParticipantType(0);

        CreateTournamentReq.DivisionSpec manual = new CreateTournamentReq.DivisionSpec();
        manual.setName("手写组");
        manual.setTournamentType(0);
        manual.setDrawMode("manual");
        manual.setKnockoutSlotOrder(Arrays.asList(0, null, 3, 2, 1, null, 4, null));
        manual.setPlayers(playerEntries(5));
        manual.setRule(defaultRule());

        CreateTournamentReq.DivisionSpec manualGroups = new CreateTournamentReq.DivisionSpec();
        manualGroups.setName("手写分组组");
        manualGroups.setTournamentType(1);
        manualGroups.setDrawMode("manual-groups");
        manualGroups.setKnockoutSlots(8);
        manualGroups.setQualifiersPerGroup(2);
        manualGroups.setGroups(List.of(List.of(0, 1), List.of(2, 3), List.of(4, 5), List.of(6, 7)));
        manualGroups.setPlayers(playerEntries(8));
        manualGroups.setRule(defaultRule());

        CreateTournamentReq.DivisionSpec auto = new CreateTournamentReq.DivisionSpec();
        auto.setName("自动组");
        auto.setTournamentType(0);
        auto.setPlayers(playerEntries(4));
        auto.setRule(defaultRule());

        req.setDivisions(new ArrayList<>(List.of(manual, manualGroups, auto)));
        return req;
    }

    /** 组别 2（type1）+ drawMode=manual → TournamentCreationFactory.java:1245-1247。 */
    private Probe multiDivisionProbeManualOnGroupType() {
        CreateTournamentReq req = multiDivisionProbeBase();
        CreateTournamentReq.DivisionSpec spec = req.getDivisions().get(1);
        spec.setDrawMode("manual");
        spec.setGroups(null);
        spec.setKnockoutSlotOrder(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7));
        return new Probe("MULTI_DIV_MANUAL_ON_GROUP_TYPE", "组别2 tournamentType=1 + drawMode=manual", req,
                "手写签表仅支持纯淘汰赛（tournamentType=0）");
    }

    /** 组别 1（type0）+ drawMode=manual-groups → TournamentCreationFactory.java:1248-1250。 */
    private Probe multiDivisionProbeManualGroupsOnKnockout() {
        CreateTournamentReq req = multiDivisionProbeBase();
        CreateTournamentReq.DivisionSpec spec = req.getDivisions().get(0);
        spec.setDrawMode("manual-groups");
        spec.setKnockoutSlotOrder(null);
        spec.setGroups(List.of(List.of(0, 1), List.of(2, 3)));
        return new Probe("MULTI_DIV_MANUAL_GROUPS_ON_KNOCKOUT", "组别1 tournamentType=0 + drawMode=manual-groups", req,
                "手写分组仅支持小组赛+淘汰赛（tournamentType=1）");
    }

    /** 组别 3（type0-auto）带 groups → TournamentCreationFactory.java:1251-1254。 */
    private Probe multiDivisionProbeGroupsOnNonManualGroups() {
        CreateTournamentReq req = multiDivisionProbeBase();
        req.getDivisions().get(2).setGroups(List.of(List.of(0, 1), List.of(2, 3)));
        return new Probe("MULTI_DIV_GROUPS_ON_NON_MANUAL_GROUPS", "组别3 drawMode=auto 却携带 groups", req,
                "仅手写分组（drawMode=manual-groups）支持 groups，请移除该字段或改用 manual-groups");
    }

    /** 组别 1 签位长度错（容量 8 只给 7）→ TournamentCreationFactory.java:580-582。 */
    private Probe multiDivisionProbeSlotOrderWrongLength() {
        CreateTournamentReq req = multiDivisionProbeBase();
        List<Integer> order = new ArrayList<>(req.getDivisions().get(0).getKnockoutSlotOrder());
        order.remove(0);
        req.getDivisions().get(0).setKnockoutSlotOrder(order);
        return new Probe("MULTI_DIV_SLOT_ORDER_LENGTH", "组别1 容量 8 只给 7 个签位", req,
                "签位数量不匹配：当前赛制需要 8 个签位，收到 7");
    }

    /** 组别 1 下标重复 → TournamentCreationFactory.java:595-597。 */
    private Probe multiDivisionProbeSlotOrderDuplicate() {
        CreateTournamentReq req = multiDivisionProbeBase();
        req.getDivisions().get(0).setKnockoutSlotOrder(Arrays.asList(0, 0, 1, 2, 3, null, null, 4));
        return new Probe("MULTI_DIV_SLOT_ORDER_DUPLICATE", "组别1 下标 0 放在签位 1、2", req,
                "名单第 1 位被重复放入签位 2");
    }

    /** 组别 1 下标越界 → TournamentCreationFactory.java:592-594。 */
    private Probe multiDivisionProbeSlotOrderOutOfRange() {
        CreateTournamentReq req = multiDivisionProbeBase();
        req.getDivisions().get(0).setKnockoutSlotOrder(Arrays.asList(0, 5, 1, 2, 3, null, null, 4));
        return new Probe("MULTI_DIV_SLOT_ORDER_OUT_OF_RANGE", "组别1（5 人）出现下标 5", req,
                "签位 2 的下标 5 超出名单范围 [0, 4]");
    }

    /** 组别 1 双轮空对位 → TournamentCreationFactory.java:603-607。 */
    private Probe multiDivisionProbeSlotOrderDoubleBye() {
        CreateTournamentReq req = multiDivisionProbeBase();
        req.getDivisions().get(0).setKnockoutSlotOrder(Arrays.asList(null, null, 0, 1, 2, 3, 4, null));
        return new Probe("MULTI_DIV_SLOT_ORDER_DOUBLE_BYE", "组别1 第 1 场（签位 1、2）均轮空", req,
                "均为轮空，请调整签位摆放");
    }

    /** divisions[] 与顶层 drawMode 混用 → TournamentCreationFactory.java:207-209。 */
    private Probe multiDivisionProbeMixedWithTopLevel() {
        CreateTournamentReq req = multiDivisionProbeBase();
        req.setDrawMode("auto");
        return new Probe("MULTI_DIV_MIXED_WITH_TOP_LEVEL", "divisions 与顶层 drawMode 混用", req,
                "divisions 与顶层选手/赛制/规则字段不可混用，请只在 divisions 内配置各组别");
    }

    private Probe multiDivisionProbeMixedWithTopLevelKnockoutRounds() {
        CreateTournamentReq req = multiDivisionProbeBase();
        req.setKnockoutRounds(3);
        return new Probe("MULTI_DIV_MIXED_WITH_TOP_LEVEL_KNOCKOUT_ROUNDS", "divisions 与顶层 knockoutRounds 混用", req,
                "divisions 与顶层选手/赛制/规则字段不可混用，请只在 divisions 内配置各组别");
    }

    private Probe multiDivisionProbeMixedWithTopLevelRoundRobinRounds() {
        CreateTournamentReq req = multiDivisionProbeBase();
        req.setRoundRobinRounds(2);
        return new Probe("MULTI_DIV_MIXED_WITH_TOP_LEVEL_ROUND_ROBIN_ROUNDS", "divisions 与顶层 roundRobinRounds 混用", req,
                "divisions 与顶层选手/赛制/规则字段不可混用，请只在 divisions 内配置各组别");
    }

    private Probe multiDivisionProbeMixedWithTopLevelRoundRuleEnabled() {
        CreateTournamentReq req = multiDivisionProbeBase();
        req.setRoundRuleEnabled(true);
        return new Probe("MULTI_DIV_MIXED_WITH_TOP_LEVEL_ROUND_RULE_ENABLED", "divisions 与顶层 roundRuleEnabled 混用", req,
                "divisions 与顶层选手/赛制/规则字段不可混用，请只在 divisions 内配置各组别");
    }

    private Probe multiDivisionProbeMixedWithTopLevelThirdPlaceEnabled() {
        CreateTournamentReq req = multiDivisionProbeBase();
        req.setThirdPlaceEnabled(true);
        return new Probe("MULTI_DIV_MIXED_WITH_TOP_LEVEL_THIRD_PLACE_ENABLED", "divisions 与顶层 thirdPlaceEnabled 混用", req,
                "divisions 与顶层选手/赛制/规则字段不可混用，请只在 divisions 内配置各组别");
    }

    private Probe multiDivisionProbeMixedWithTopLevelThirdPlaceRule() {
        CreateTournamentReq req = multiDivisionProbeBase();
        CreateTournamentReq.RuleConfig rule = new CreateTournamentReq.RuleConfig();
        rule.setBestOf(3);
        req.setThirdPlaceRule(rule);
        return new Probe("MULTI_DIV_MIXED_WITH_TOP_LEVEL_THIRD_PLACE_RULE", "divisions 与顶层 thirdPlaceRule 混用", req,
                "divisions 与顶层选手/赛制/规则字段不可混用，请只在 divisions 内配置各组别");
    }

    // ==================================================================================
    // 内存世界：mock 持久层 + 真实写路径服务
    // ==================================================================================
    private static final class CreationWorld {
        final List<Tournament> tournaments = new ArrayList<>();
        final List<TournamentDivision> divisions = new ArrayList<>();
        final List<Player> players = new ArrayList<>();
        final List<MatchRecord> matches = new ArrayList<>();
        final List<TournamentTeamMember> members = new ArrayList<>();
        final List<TournamentRankingConfig> rankingConfigs = new ArrayList<>();
        final List<TournamentRefereeConfig> refereeConfigs = new ArrayList<>();
        final List<TournamentRoundRule> roundRules = new ArrayList<>();
        final List<TournamentCustomItem> customItems = new ArrayList<>();
        TournamentCreationFactory factory;
        TournamentDrawService drawService;

        /** 入参名单顺序 = 落库顺序（工厂按 entries 顺序 buildPlayers + insert）。 */
        List<Player> playersInInsertionOrder(String divisionId) {
            List<Player> out = new ArrayList<>();
            for (Player p : players) {
                if (Objects.equals(divisionId, p.getDivisionId())) out.add(p);
            }
            return out;
        }

        /** 模拟 TournamentDrawService.loadRoster：order by create_time, id（createTime 均为 null → 按 id）。 */
        List<Player> rosterAsLoaded(String divisionId) {
            List<Player> out = playersInInsertionOrder(divisionId);
            out.sort(Comparator.comparing(Player::getId));
            return out;
        }

        List<MatchRecord> matchesOf(String divisionId, Integer stageType) {
            List<MatchRecord> out = new ArrayList<>();
            for (MatchRecord m : matches) {
                if (!Objects.equals(divisionId, m.getDivisionId())) continue;
                if (stageType != null && !stageType.equals(m.getStageType())) continue;
                out.add(m);
            }
            out.sort(Comparator.comparingInt((MatchRecord m) -> safeInt(m.getRoundNum()))
                    .thenComparingInt(m -> safeInt(m.getMatchIndex())));
            return out;
        }

        List<TournamentTeamMember> membersOfParticipant(String participantId) {
            List<TournamentTeamMember> out = new ArrayList<>();
            for (TournamentTeamMember m : members) {
                if (Objects.equals(participantId, m.getParticipantId())) out.add(m);
            }
            out.sort(Comparator.comparingInt(m -> m.getDisplayOrder() == null ? 0 : m.getDisplayOrder()));
            return out;
        }
    }

    /**
     * 装配「真实服务 + mock 持久层」世界。mock 语义对齐 MyBatis-Plus：
     * insert 落内存（id 为空时补 UUID，等价 ASSIGN_ID）；updateById 只覆盖非 null 字段；
     * selectList 按 QueryWrapper 的 SQL 片段（列名）+ 参数值过滤内存数据。
     */
    private CreationWorld buildWorld() {
        CreationWorld w = new CreationWorld();
        TournamentMapper tournamentMapper = mock(TournamentMapper.class);
        TournamentDivisionMapper divisionMapper = mock(TournamentDivisionMapper.class);
        PlayerMapper playerMapper = mock(PlayerMapper.class);
        MatchRecordMapper matchMapper = mock(MatchRecordMapper.class);
        TournamentRankingConfigMapper rankingConfigMapper = mock(TournamentRankingConfigMapper.class);
        TournamentRefereeConfigMapper refereeConfigMapper = mock(TournamentRefereeConfigMapper.class);
        TeamMatchItemMapper teamItemMapper = mock(TeamMatchItemMapper.class);
        TournamentRoundRuleMapper roundRuleMapper = mock(TournamentRoundRuleMapper.class);
        TournamentTeamMemberMapper memberMapper = mock(TournamentTeamMemberMapper.class);
        TournamentCustomItemMapper customItemMapper = mock(TournamentCustomItemMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        TournamentRefereeGrantMapper refereeGrantMapper = mock(TournamentRefereeGrantMapper.class);
        MatchEventMapper eventMapper = mock(MatchEventMapper.class);
        MatchLineupConfigMapper lineupMapper = mock(MatchLineupConfigMapper.class);
        MatchReportMetaMapper reportMetaMapper = mock(MatchReportMetaMapper.class);
        TournamentQualificationOverrideMapper qualOverrideMapper = mock(TournamentQualificationOverrideMapper.class);

        User creator = new User();
        creator.setId(CREATOR);
        creator.setNickname("chaos-creator");
        creator.setProfileCompleted(Boolean.TRUE);
        when(userMapper.selectById(anyString())).thenReturn(creator);
        when(refereeGrantMapper.selectCount(any())).thenReturn(0L);

        when(tournamentMapper.insert(any(Tournament.class))).thenAnswer(inv -> {
            Tournament entity = inv.getArgument(0);
            if (entity.getId() == null) entity.setId("t-" + UUID.randomUUID());
            w.tournaments.add(entity);
            return 1;
        });
        when(tournamentMapper.selectById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0, String.class);
            return w.tournaments.stream().filter(t -> id.equals(t.getId())).findFirst().orElse(null);
        });
        when(tournamentMapper.updateById(any(Tournament.class))).thenAnswer(inv -> {
            Tournament patch = inv.getArgument(0);
            w.tournaments.stream().filter(t -> patch.getId().equals(t.getId())).findFirst()
                    .ifPresent(stored -> applyPatch(stored, patch));
            return 1;
        });

        when(divisionMapper.insert(any(TournamentDivision.class))).thenAnswer(inv -> {
            TournamentDivision entity = inv.getArgument(0);
            if (entity.getId() == null) entity.setId("d-" + UUID.randomUUID());
            w.divisions.add(entity);
            return 1;
        });
        when(divisionMapper.updateById(any(TournamentDivision.class))).thenAnswer(inv -> {
            TournamentDivision patch = inv.getArgument(0);
            w.divisions.stream().filter(d -> patch.getId().equals(d.getId())).findFirst()
                    .ifPresent(stored -> applyPatch(stored, patch));
            return 1;
        });
        when(divisionMapper.selectOne(any())).thenAnswer(inv -> {
            Collection<Object> params = wrapperParams(inv.getArgument(0));
            return w.divisions.stream().filter(d -> params.contains(d.getId())).findFirst().orElse(null);
        });

        when(playerMapper.insert(any(Player.class))).thenAnswer(inv -> {
            Player entity = inv.getArgument(0);
            if (entity.getId() == null) entity.setId("p-" + UUID.randomUUID());
            w.players.add(entity);
            return 1;
        });
        when(playerMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> wrapper = inv.getArgument(0);
            String sql = wrapper.getSqlSegment();
            Collection<Object> params = wrapperParams(wrapper);
            List<Player> out = new ArrayList<>();
            for (Player p : w.players) {
                if (sql.contains("division_id") && !params.contains(p.getDivisionId())) continue;
                out.add(p);
            }
            out.sort(Comparator.comparing(Player::getId));
            return out;
        });

        when(matchMapper.insert(any(MatchRecord.class))).thenAnswer(inv -> {
            MatchRecord entity = inv.getArgument(0);
            if (entity.getId() == null) entity.setId("m-" + UUID.randomUUID());
            w.matches.add(entity);
            return 1;
        });
        when(matchMapper.selectById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0, String.class);
            return w.matches.stream().filter(m -> id.equals(m.getId())).findFirst().orElse(null);
        });
        when(matchMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> wrapper = inv.getArgument(0);
            String sql = wrapper.getSqlSegment();
            Collection<Object> params = wrapperParams(wrapper);
            List<MatchRecord> out = new ArrayList<>();
            for (MatchRecord m : w.matches) {
                if (sql.contains("division_id") && !params.contains(m.getDivisionId())) continue;
                if (sql.contains("stage_type") && !params.contains(m.getStageType())) continue;
                out.add(m);
            }
            out.sort(Comparator.comparingInt((MatchRecord m) -> safeInt(m.getRoundNum()))
                    .thenComparingInt(m -> safeInt(m.getMatchIndex())));
            return out;
        });
        when(matchMapper.delete(any())).thenAnswer(inv -> {
            QueryWrapper<?> wrapper = inv.getArgument(0);
            String sql = wrapper.getSqlSegment();
            Collection<Object> params = wrapperParams(wrapper);
            int before = w.matches.size();
            w.matches.removeIf(m -> (!sql.contains("division_id") || params.contains(m.getDivisionId()))
                    && (!sql.contains("stage_type") || params.contains(m.getStageType())));
            return before - w.matches.size();
        });

        when(memberMapper.insert(any(TournamentTeamMember.class))).thenAnswer(inv -> {
            TournamentTeamMember entity = inv.getArgument(0);
            if (entity.getId() == null) entity.setId("mem-" + UUID.randomUUID());
            w.members.add(entity);
            return 1;
        });
        when(memberMapper.selectCount(any())).thenAnswer(inv -> {
            Collection<Object> params = wrapperParams(inv.getArgument(0));
            return w.members.stream().filter(m -> params.contains(m.getTournamentId())).count();
        });
        when(teamItemMapper.selectCount(any())).thenReturn(0L);
        when(teamItemMapper.selectList(any())).thenReturn(new ArrayList<TeamMatchItem>());
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(lineupMapper.selectCount(any())).thenReturn(0L);

        when(rankingConfigMapper.insert(any(TournamentRankingConfig.class))).thenAnswer(inv -> {
            w.rankingConfigs.add(inv.getArgument(0));
            return 1;
        });
        when(refereeConfigMapper.insert(any(TournamentRefereeConfig.class))).thenAnswer(inv -> {
            w.refereeConfigs.add(inv.getArgument(0));
            return 1;
        });
        when(roundRuleMapper.insert(any(TournamentRoundRule.class))).thenAnswer(inv -> {
            w.roundRules.add(inv.getArgument(0));
            return 1;
        });
        when(customItemMapper.insert(any(TournamentCustomItem.class))).thenAnswer(inv -> {
            w.customItems.add(inv.getArgument(0));
            return 1;
        });

        TournamentAccessGuard guard = new TournamentAccessGuard(tournamentMapper, userMapper, refereeGrantMapper);
        TournamentRankingService rankingService = new TournamentRankingService(playerMapper, matchMapper,
                teamItemMapper, rankingConfigMapper, qualOverrideMapper, new GroupStandingEngine(), guard);
        RoundRobinEngine roundRobinEngine = new RoundRobinEngine();
        w.factory = new TournamentCreationFactory(tournamentMapper, divisionMapper, playerMapper, matchMapper,
                rankingConfigMapper, refereeConfigMapper, teamItemMapper, roundRuleMapper, memberMapper,
                new BracketEngine(), roundRobinEngine, guard, customItemMapper, rankingService);
        w.drawService = new TournamentDrawService(tournamentMapper, divisionMapper, playerMapper, matchMapper,
                eventMapper, lineupMapper, teamItemMapper, reportMetaMapper, guard, roundRobinEngine);
        return w;
    }

    /** 模拟 MyBatis-Plus updateById 默认策略：仅覆盖非 null 字段。 */
    private static void applyPatch(Object stored, Object patch) {
        try {
            for (Class<?> c = patch.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object value = f.get(patch);
                    if (value != null) f.set(stored, value);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("applyPatch failed", e);
        }
    }

    /**
     * 读取 QueryWrapper 的条件参数值。MP 3.5.x 的 eq() 条件是惰性求值（ISqlSegment lambda），
     * paramNameValuePairs 在 SQL 渲染前为空，故先触发一次 getSqlSegment() 物化参数再读取。
     */
    private static Collection<Object> wrapperParams(QueryWrapper<?> wrapper) {
        wrapper.getSqlSegment();
        return wrapper.getParamNameValuePairs().values();
    }

    // ==================================================================================
    // 请求构造与断言辅助
    // ==================================================================================

    private CreateTournamentReq individualReq(String name, int playerCount) {
        CreateTournamentReq req = new CreateTournamentReq();
        req.setName(name);
        req.setSportType(0);
        req.setParticipantType(0);
        req.setRule(defaultRule());
        List<CreateTournamentReq.PlayerEntry> entries = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            CreateTournamentReq.PlayerEntry entry = new CreateTournamentReq.PlayerEntry();
            entry.setName("选手" + i);
            entries.add(entry);
        }
        req.setPlayers(entries);
        return req;
    }

    /** 无种子选手名单（多组别 spec 用，组别 i 的选手名为「选手i_序号」由调用方区分）。 */
    private List<CreateTournamentReq.PlayerEntry> playerEntries(int count) {
        List<CreateTournamentReq.PlayerEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CreateTournamentReq.PlayerEntry entry = new CreateTournamentReq.PlayerEntry();
            entry.setName("选手" + i);
            entries.add(entry);
        }
        return entries;
    }

    /** type1 + manual-groups 基础请求（groups 由调用方补全，保证能被 reject 到目标校验点）。 */
    private CreateTournamentReq type1ManualGroupsReq(int playerCount, int knockoutSlots, int qualifiers) {
        CreateTournamentReq req = individualReq("probe-manual-groups", playerCount);
        req.setTournamentType(1);
        req.setKnockoutSlots(knockoutSlots);
        req.setQualifiersPerGroup(qualifiers);
        req.setDrawMode("manual-groups");
        return req;
    }

    private CreateTournamentReq teamReq(String name, int template, int teamCount, int memberCount) {
        CreateTournamentReq req = new CreateTournamentReq();
        req.setName(name);
        req.setSportType(0);
        req.setParticipantType(1);
        req.setTeamMatchTemplate(template);
        req.setTournamentType(0);
        req.setRule(defaultRule());
        req.setTeams(buildTeams(teamCount, memberCount));
        return req;
    }

    private List<CreateTournamentReq.TeamEntry> buildTeams(int teamCount, int memberCount) {
        List<CreateTournamentReq.TeamEntry> teams = new ArrayList<>();
        for (int t = 0; t < teamCount; t++) {
            CreateTournamentReq.TeamEntry team = new CreateTournamentReq.TeamEntry();
            team.setName("队伍" + t);
            List<CreateTournamentReq.TeamMemberEntry> members = new ArrayList<>();
            for (int i = 0; i < memberCount; i++) {
                CreateTournamentReq.TeamMemberEntry member = new CreateTournamentReq.TeamMemberEntry();
                member.setName("队员" + t + "_" + i);
                member.setCaptain(i == 0);
                members.add(member);
            }
            team.setMembers(members);
            teams.add(team);
        }
        return teams;
    }

    private List<CreateTournamentReq.CustomItemSpec> customItems(int count, String[] types) {
        List<CreateTournamentReq.CustomItemSpec> specs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CreateTournamentReq.CustomItemSpec spec = new CreateTournamentReq.CustomItemSpec();
            spec.setItemType(types[i % types.length]);
            spec.setDisplayOrder(i + 1);
            specs.add(spec);
        }
        return specs;
    }

    private CreateTournamentReq.RuleConfig defaultRule() {
        CreateTournamentReq.RuleConfig rule = new CreateTournamentReq.RuleConfig();
        rule.setBestOf(3);
        rule.setGamesToWin(2);
        rule.setPointsToWin(21);
        rule.setEnableDeuce(true);
        rule.setCapPoint(30);
        return rule;
    }

    /**
     * 构造手写签位序列：元素为名册下标，null=轮空；长度=容量、每个下标恰一次、同一对阵位（2i,2i+1）不得双 null。
     * 先给每个对阵位放 1 人，再把多出来的选手随机塞进部分对阵位（每个对阵位最多 2 人）。
     */
    private List<Integer> buildManualSlotOrder(int playerCount, int capacity, Random rnd) {
        int pairs = capacity / 2;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) indices.add(i);
        Collections.shuffle(indices, rnd);
        int[] perPair = new int[pairs];
        Arrays.fill(perPair, 1);
        List<Integer> pairOrder = new ArrayList<>();
        for (int i = 0; i < pairs; i++) pairOrder.add(i);
        Collections.shuffle(pairOrder, rnd);
        for (int i = 0; i < playerCount - pairs; i++) perPair[pairOrder.get(i)]++;
        List<Integer> order = new ArrayList<>(Collections.<Integer>nCopies(capacity, null));
        int cursor = 0;
        for (int p = 0; p < pairs; p++) {
            if (perPair[p] == 2) {
                order.set(p * 2, indices.get(cursor++));
                order.set(p * 2 + 1, indices.get(cursor++));
            } else if (rnd.nextBoolean()) {
                order.set(p * 2, indices.get(cursor++));
            } else {
                order.set(p * 2 + 1, indices.get(cursor++));
            }
        }
        return order;
    }

    /** 把 0..n-1 随机切成 sizes 指定的各组（组内顺序随机）。 */
    private List<List<Integer>> partitionIndices(int playerCount, int[] sizes, Random rnd) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) indices.add(i);
        Collections.shuffle(indices, rnd);
        List<List<Integer>> groups = new ArrayList<>();
        int cursor = 0;
        for (int size : sizes) {
            groups.add(new ArrayList<>(indices.subList(cursor, cursor + size)));
            cursor += size;
        }
        return groups;
    }

    /** 校验小组赛对阵：每对恰一场、同组内、groupNo 与请求一致。 */
    private void checkGroupPairs(List<MatchRecord> matches, List<Player> roster, List<List<String>> groups) {
        Map<String, Integer> groupOfPlayer = new HashMap<>();
        for (int gi = 0; gi < groups.size(); gi++) {
            for (String id : groups.get(gi)) groupOfPlayer.put(id, gi + 1);
        }
        Set<String> seen = new HashSet<>();
        for (MatchRecord m : matches) {
            String left = m.getLeftPlayerId();
            String right = m.getRightPlayerId();
            if (left == null || right == null) {
                check(false, "GROUP_EDIT", "Regroup", "NULL_SIDE", "重分组后小组赛出现空侧");
                continue;
            }
            Integer leftGroup = groupOfPlayer.get(left);
            Integer rightGroup = groupOfPlayer.get(right);
            check(leftGroup != null && leftGroup.equals(rightGroup) && leftGroup.equals(m.getGroupNo()),
                    "GROUP_EDIT", "Regroup", "PAIR_GROUP_MISMATCH",
                    "小组赛跨组或 groupNo 不符: " + leftGroup + "/" + rightGroup + "/" + m.getGroupNo());
            check(seen.add(pairKey(left, right)), "GROUP_EDIT", "Regroup", "PAIR_REPEAT",
                    "重分组后对阵重复: " + pairKey(left, right));
        }
        check(seen.size() == matches.size(), "GROUP_EDIT", "Regroup", "PAIR_COUNT",
                "不同对阵数=" + seen.size() + " 场次数=" + matches.size());
    }

    /** 幂等判定（与 TournamentDrawService.sameAsCurrentAssignment 同口径）。 */
    private boolean sameAssignment(List<Player> roster, List<List<String>> groups) {
        Map<String, Player> byId = new HashMap<>();
        for (Player p : roster) byId.put(p.getId(), p);
        for (int gi = 0; gi < groups.size(); gi++) {
            for (int pi = 0; pi < groups.get(gi).size(); pi++) {
                Player p = byId.get(groups.get(gi).get(pi));
                if (p == null || !Integer.valueOf(gi + 1).equals(p.getGroupNo())
                        || !Integer.valueOf(pi + 1).equals(p.getGroupPosition())) {
                    return false;
                }
            }
        }
        return true;
    }

    private UpdateGroupAssignmentsReq assignmentReq(List<List<String>> groups) {
        UpdateGroupAssignmentsReq req = new UpdateGroupAssignmentsReq();
        req.setGroups(groups);
        return req;
    }

    private static Set<String> idsOf(List<MatchRecord> matches) {
        Set<String> ids = new LinkedHashSet<>();
        for (MatchRecord m : matches) ids.add(m.getId());
        return ids;
    }

    private static Player playerById(List<Player> roster, String playerId) {
        for (Player p : roster) {
            if (p.getId().equals(playerId)) return p;
        }
        return null;
    }

    private static int groupNoOf(List<Player> roster, String playerId) {
        Player p = playerById(roster, playerId);
        return p == null || p.getGroupNo() == null ? -1 : p.getGroupNo();
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static void countOccurrence(Map<String, Integer> counter, String id) {
        if (id == null) return;
        counter.merge(id, 1, Integer::sum);
    }

    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static <T> T choice(Random rnd, T[] arr) {
        return arr[rnd.nextInt(arr.length)];
    }

    // ==================================================================================
    // JUnit 执行入口
    // ==================================================================================

    /**
     * 场景 4 入口：多组别创建（手写签表/手写分组推广到多组别）混沌循环。
     * 与 runBadmintonCreationChaos 共用 -Dchaos.seed / -Dchaos.scale 参数（默认种子 20260919），
     * 固定种子下可完整复现；journal 独立落盘 creation-multidivision-summary.json。
     */
    @Test
    void runBadmintonMultiDivisionCreationChaos() throws Exception {
        String seedProp = System.getProperty("chaos.seed");
        seed = seedProp != null ? Long.parseLong(seedProp) : 20260919L;
        random = new Random(seed);

        String scaleProp = System.getProperty("chaos.scale");
        int scale = scaleProp != null ? Integer.parseInt(scaleProp) : 30;

        System.out.println("════ 启动多组别创建链路混沌测试 (scale=" + scale + ", seed=" + seed + ") ════");

        try {
            for (int i = 0; i < scale; i++) {
                chaosMultiDivisionHappyPath(random.nextLong());
                chaosMultiDivisionMaliciousProbe(random.nextLong());
            }
        } finally {
            writeJournal("BadmintonCreationChaosTest", "creation-multidivision-summary.json");
        }

        if (!violations.isEmpty()) {
            System.err.println("════ 多组别创建链路混沌发现 " + violations.size() + " 处违反期望 ════");
            for (Violation v : violations) {
                System.err.println("[caseSeed=" + v.caseSeed() + "][" + v.category() + "]["
                        + v.scenario() + "] " + v.type() + ": " + v.message());
            }
            AssertionError failure = new AssertionError("多组别创建链路混沌测试发现 " + violations.size() + " 处违规");
            for (Violation v : violations) {
                failure.addSuppressed(new AssertionError("caseSeed=" + v.caseSeed() + " " + v.category()
                        + "/" + v.scenario() + "/" + v.type() + ": " + v.message()));
            }
            throw failure;
        }

        System.out.println("多组别创建链路混沌测试通过: 总案例数=" + cases.size() + "，零违规！");
    }

    @Test
    void runBadmintonCreationChaos() throws Exception {
        String seedProp = System.getProperty("chaos.seed");
        seed = seedProp != null ? Long.parseLong(seedProp) : 20260919L;
        random = new Random(seed);

        String scaleProp = System.getProperty("chaos.scale");
        int scale = scaleProp != null ? Integer.parseInt(scaleProp) : 30;

        System.out.println("════ 启动羽毛球比赛创建链路混沌测试 (scale=" + scale + ", seed=" + seed + ") ════");

        // try/finally：场景循环中任何意外异常也必须保证 journal 落盘
        try {
            for (int i = 0; i < scale; i++) {
                chaosCreateHappyPath(random.nextLong());
                chaosCreateMaliciousProbe(random.nextLong());
                chaosGroupAssignmentEdit(random.nextLong());
                chaosGroupAssignmentGuards(random.nextLong());
            }
        } finally {
            writeJournal();
        }

        if (!violations.isEmpty()) {
            System.err.println("════ 比赛创建链路混沌发现 " + violations.size() + " 处违反期望 ════");
            for (Violation v : violations) {
                System.err.println("[caseSeed=" + v.caseSeed() + "][" + v.category() + "]["
                        + v.scenario() + "] " + v.type() + ": " + v.message());
            }
            AssertionError failure = new AssertionError("比赛创建链路混沌测试发现 " + violations.size() + " 处违规");
            for (Violation v : violations) {
                failure.addSuppressed(new AssertionError("caseSeed=" + v.caseSeed() + " " + v.category()
                        + "/" + v.scenario() + "/" + v.type() + ": " + v.message()));
            }
            throw failure;
        }

        System.out.println("羽毛球比赛创建链路混沌测试通过: 总案例数=" + cases.size() + "，零违规！");
    }

    private void writeJournal() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("suite", "BadmintonCreationChaosTest");
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
            node.put("caseSeed", v.caseSeed());
            node.put("category", v.category());
            node.put("scenario", v.scenario());
            node.put("type", v.type());
            node.put("message", v.message());
        }

        Path dir = Paths.get("..", "outputs", "fuzz-badminton");
        Files.createDirectories(dir);
        Path file = dir.resolve("creation-summary.json");
        Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        System.out.println("比赛创建链路 journal 已写入: " + file.toAbsolutePath());
    }

    /** 多组别场景 journal（独立文件，避免覆盖单组别场景的 creation-summary.json）。 */
    private void writeJournal(String suite, String fileName) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("suite", suite);
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
            node.put("caseSeed", v.caseSeed());
            node.put("category", v.category());
            node.put("scenario", v.scenario());
            node.put("type", v.type());
            node.put("message", v.message());
        }

        Path dir = Paths.get("..", "outputs", "fuzz-badminton");
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        System.out.println("多组别创建链路 journal 已写入: " + file.toAbsolutePath());
    }
}
