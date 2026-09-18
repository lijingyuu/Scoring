package com.scoring.backend.engine;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.SaveTeamMatchLineupReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.TeamMatchLineupVO;
import com.scoring.backend.engine.ranking.GroupStandingEngine;
import com.scoring.backend.engine.ranking.RankingConfig;
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
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.service.impl.TeamMatchServiceImpl;
import com.scoring.backend.service.impl.TournamentRuleResolver;
import com.scoring.backend.service.match.MatchAccessGuard;
import com.scoring.backend.service.match.MatchLockService;
import com.scoring.backend.service.match.MatchReportAssembler;
import com.scoring.backend.service.match.MatchSettlementService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 羽毛球全生命周期赛制引擎混沌测试（纯逻辑，百场至千场级无头仿真）
 *
 * 覆盖：
 * 1. 个人赛淘汰赛：种子、轮空、季军赛、各轮次胜/败者晋级槽位传播（真实 BracketEngine）
 * 2. 个人赛小组循环+淘汰赛：伯格尔对阵、BWF/大众排名破平、同组回避(Same-group avoidance)抽签
 *    （真实 RoundRobinEngine + GroupStandingEngine + BracketEngine，BADMINTON_RELAY_COMMON_1 等 4 模板轮换）
 * 3. 苏杯五项与自定义团体赛：随机逐场经真实 MatchSettlementService.finishMatch 完赛，驱动真实
 *    settleParentTeamMatch 的"过半胜场提前终局(Dead Rubber Early Settlement)"。真实产品语义为
 *    双路径：一方达到门槛时由裁判经 settleTeamMatch 手动提前结算（前端"直接结算"弹窗，
 *    PUT /team-match/settle），或继续打完剩余项目、由自动路径在全部完赛时结算。
 *    断言两条路径的结算语义（不可追平多数才允许/发生结算）、未达门槛时手动结算被拒、
 *    僵尸子项真实行为（保持未完赛 + startChildMatch 守卫拒绝）、循环赛制全量完赛路径、
 *    真实 propagateFinishedMatch 胜者/败者槽位传播
 * 4. 接力追分团体赛：环状名单喂给真实 TeamMatchServiceImpl.saveLineup（normalizeRelayItems +
 *    validateRelayLineup + 入库回读），断言真实 normalize 输出的 R1..RN 分段编码与环状拓扑；
 *    种子化恶意探针（破环/非法人数/编码不连续/null 项/外队队员/段内人数/首位重复）断言真实校验
 *    的拒绝理由文案；追分目标(base*人数)由真实服务计算
 *
 * 场景 3/4 的服务实例注入方式：MatchSettlementService / TeamMatchServiceImpl 均为构造器注入，
 * 直接 new 真实实例；持久层 mapper 用 Mockito mock 成内存态（selectByIdForUpdate/selectList/
 * updateById 等打到内存 Map/List，updateById 按 MyBatis-Plus 语义只覆盖非 null 字段）。
 * 无持久化逻辑的协作者（TournamentRuleResolver、MatchAccessGuard）同样 new 真实实例以走真实分支；
 * MatchLockService.clearMatchLock 使用 LambdaUpdateWrapper（依赖 MyBatis-Plus TableInfo 元数据，
 * 纯单测无容器不初始化）且本测试全部走无锁重载，故 mock 之；MatchReportAssembler 仅重启路径使用，mock 之。
 *
 * 运行命令:
 *   mvn test -Dtest=BadmintonFullLifecycleChaosTest
 *   可选参数: -Dchaos.seed=<long> -Dchaos.scale=<int, 默认 50>
 */
public class BadmintonFullLifecycleChaosTest {

    private record Violation(String category, String scenario, String type, String message, long caseSeed) {}

    private final List<Map<String, Object>> cases = new ArrayList<>();
    private final List<Violation> violations = new ArrayList<>();
    private final BracketEngine bracketEngine = new BracketEngine();
    private final RoundRobinEngine roundRobinEngine = new RoundRobinEngine();
    private final GroupStandingEngine standingEngine = new GroupStandingEngine();

    private long seed;
    private Random random;
    /** 当前正在执行的混沌案例种子：随 Violation 落盘，用于违规回放 */
    private long currentCaseSeed;

    private void check(boolean condition, String category, String scenario, String type, String message) {
        if (!condition) {
            violations.add(new Violation(category, scenario, type, message, currentCaseSeed));
        }
    }

    // =========================================================================
    // 场景 1: 羽毛球个人单双打淘汰赛全生命周期
    // =========================================================================
    private void chaosIndividualKnockout(long caseSeed) {
        currentCaseSeed = caseSeed;
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
        currentCaseSeed = caseSeed;
        Random rnd = new Random(caseSeed);
        int groupCount = rnd.nextInt(3) + 2; // 2 ~ 4 组
        int playersPerGroup = rnd.nextInt(3) + 3; // 3 ~ 5 人/组
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

        // 轮换测试 4 大羽毛球排名模板（含 BADMINTON_RELAY_COMMON_1 接力排名模板）
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
    //
    // 驱动真实代码：子场逐个经真实 MatchSettlementService.finishMatch 完赛（内部走
    // requireMatchForUpdate -> validateFinishReq -> findTeamChildItem -> finishParentTeamMatchIfSettled
    // -> settleParentTeamMatch -> propagateFinishedMatch 完整链路），胜场门槛公式
    // winThreshold=(totalItems/2)+1 只存在于真实服务里，测试不复刻公式，改断言其语义：
    // 手动提前结算被接受 / 自动结算发生的充要时刻 = 胜方构成"不可追平多数"（2*胜场 > 总项数）。
    //
    // 真实产品的提前终局是双路径（与前端 team-match.vue 的 maybePromptEarlySettlement 弹窗一致）：
    // - 手动路径：门槛到达时裁判选"直接结算" -> settleTeamMatch（directSettlement 分支凭
    //   earlyKnockout 接受提前结算，未达门槛则抛 "requires all items finished..."）；
    // - 自动路径：选"继续打完" -> finishMatch 链路仅在全部子项完赛（allFinished）时结算父场
    //   （settleParentTeamMatch 的 else-if 分支，earlyKnockout 不参与自动触发）。
    // 两种决策在混沌案例间随机轮换覆盖。
    // =========================================================================
    private void chaosTeamTournamentAndEarlySettlement(long caseSeed) {
        currentCaseSeed = caseSeed;
        Random rnd = new Random(caseSeed);
        boolean isSudirman = rnd.nextBoolean();
        int itemCount = isSudirman ? 5 : choice(rnd, new Integer[]{3, 5, 7});
        // knockout 路径验证"过半胜场提前终局"；roundrobin 路径（tournamentType=2）验证
        // "全部子项完赛才结算"（提前终局分支被真实条件禁用）
        boolean knockoutStage = rnd.nextInt(4) != 0;
        String operator = "op_" + caseSeed;
        String tournamentId = "tt_" + caseSeed;
        String parentId = "parent_" + caseSeed;
        String teamA = "teamA_" + caseSeed;
        String teamB = "teamB_" + caseSeed;

        Tournament tournament = new Tournament();
        tournament.setId(tournamentId);
        tournament.setName("羽毛球团体赛_" + caseSeed);
        tournament.setCreatorUserId(operator);
        tournament.setSportType(0);
        tournament.setParticipantType(1);
        tournament.setTeamMatchTemplate(isSudirman ? 1 : 3);
        tournament.setTournamentType(knockoutStage ? 1 : 2);
        tournament.setStatus(1);
        tournament.setBestOf(3);
        tournament.setGamesToWin(2);
        tournament.setPointsToWin(21);

        Player leftTeam = new Player();
        leftTeam.setId(teamA);
        leftTeam.setName("A队");
        Player rightTeam = new Player();
        rightTeam.setId(teamB);
        rightTeam.setName("B队");
        List<TournamentTeamMember> leftMembers = makeTeamMembers(tournamentId, teamA, "LM_");
        List<TournamentTeamMember> rightMembers = makeTeamMembers(tournamentId, teamB, "RM_");

        MatchRecord parent = new MatchRecord();
        parent.setId(parentId);
        parent.setTournamentId(tournamentId);
        parent.setStageType(1);
        parent.setStatus(1);
        parent.setRoundNum(1);
        parent.setMatchIndex(0);
        parent.setLeftPlayerId(teamA);
        parent.setRightPlayerId(teamB);

        // 淘汰赛路径：父场挂下一轮槽位（随机 left/right），部分案例再挂季军赛败者槽位，
        // 供真实 propagateFinishedMatch / propagateLoserIfNeeded 写入
        MatchRecord nextMatch = null;
        String nextSlot = null;
        MatchRecord thirdMatch = null;
        String loserSlot = null;
        if (knockoutStage) {
            nextMatch = new MatchRecord();
            nextMatch.setId("next_" + caseSeed);
            nextMatch.setTournamentId(tournamentId);
            nextMatch.setStageType(1);
            nextMatch.setStatus(0);
            nextMatch.setRoundNum(2);
            nextSlot = rnd.nextBoolean() ? "left" : "right";
            parent.setNextMatchId(nextMatch.getId());
            parent.setNextMatchSlot(nextSlot);
            if (rnd.nextBoolean()) {
                thirdMatch = new MatchRecord();
                thirdMatch.setId("third_" + caseSeed);
                thirdMatch.setTournamentId(tournamentId);
                thirdMatch.setStageType(1);
                thirdMatch.setStatus(0);
                thirdMatch.setRoundNum(2);
                loserSlot = rnd.nextBoolean() ? "left" : "right";
                parent.setLoserNextMatchId(thirdMatch.getId());
                parent.setLoserNextMatchSlot(loserSlot);
            }
        }

        String[] itemCodes;
        String[] itemNames;
        int[] playerCounts;
        if (isSudirman) {
            itemCodes = new String[]{"MS", "WS", "MD", "WD", "XD"};
            itemNames = new String[]{"男单", "女单", "男双", "女双", "混双"};
            playerCounts = new int[]{1, 1, 2, 2, 2};
        } else {
            itemCodes = new String[itemCount];
            itemNames = new String[itemCount];
            playerCounts = new int[itemCount];
            for (int i = 0; i < itemCount; i++) {
                itemCodes[i] = "ITEM_" + (i + 1);
                itemNames[i] = "第" + (i + 1) + "项";
                playerCounts[i] = rnd.nextBoolean() ? 1 : 2;
            }
        }

        TeamServiceWorld world = buildTeamServiceWorld(tournament, leftTeam, rightTeam, leftMembers, rightMembers);
        world.matches.put(parentId, parent);
        if (nextMatch != null) world.matches.put(nextMatch.getId(), nextMatch);
        if (thirdMatch != null) world.matches.put(thirdMatch.getId(), thirdMatch);

        List<TeamMatchItem> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            TeamMatchItem item = new TeamMatchItem();
            item.setId("item_" + (i + 1) + "_" + caseSeed);
            item.setMatchId(parentId);
            item.setTournamentId(tournamentId);
            item.setDisplayOrder(i + 1);
            item.setItemCode(itemCodes[i]);
            item.setItemName(itemNames[i]);
            item.setPlayerCount(playerCounts[i]);
            item.setStatus(0);
            item.setChildMatchId("child_" + (i + 1) + "_" + caseSeed);
            item.setLeftMemberIdsJson(memberIdsJson(leftMembers, playerCounts[i]));
            item.setRightMemberIdsJson(memberIdsJson(rightMembers, playerCounts[i]));
            items.add(item);
            world.items.add(item);

            MatchRecord child = new MatchRecord();
            child.setId(item.getChildMatchId());
            child.setTournamentId(tournamentId);
            child.setStageType(2);
            child.setStatus(1);
            child.setRoundNum(0);
            child.setMatchIndex(0);
            child.setLeftPlayerId(teamA);
            child.setRightPlayerId(teamB);
            world.matches.put(child.getId(), child);
        }

        // 随机顺序逐场喂给真实 finishMatch
        List<TeamMatchItem> playOrder = new ArrayList<>(items);
        Collections.shuffle(playOrder, rnd);
        // dead rubber 决策（模拟前端弹窗）：一方达到门槛时，裁判选"直接结算"（手动提前终局）
        // 还是"继续打完剩余项目"（自动路径全量完赛结算）。两种真实路径在案例间随机覆盖。
        boolean earlyManualSettle = rnd.nextBoolean();
        boolean prematureProbeDone = false;

        int finished = 0;
        int settledAfter = -1;
        for (TeamMatchItem item : playOrder) {
            boolean leftWin = rnd.nextBoolean();
            String winnerSide = leftWin ? "left" : "right";
            world.settlementService.finishMatch(operator, item.getChildMatchId(), buildChildFinishReq(winnerSide, rnd));
            finished++;

            MatchRecord child = world.matches.get(item.getChildMatchId());
            check(child != null && Integer.valueOf(2).equals(child.getStatus())
                            && (leftWin ? teamA : teamB).equals(child.getWinnerId()),
                    "TEAM_MATCH", "ChildFinish", "CHILD_NOT_FINALIZED",
                    "子场 " + item.getItemCode() + " 经真实 finishMatch 后 status="
                            + (child == null ? "null" : child.getStatus())
                            + " winnerId=" + (child == null ? "null" : child.getWinnerId()));
            check(winnerSide.equals(item.getWinnerSide()) && Integer.valueOf(2).equals(item.getStatus()),
                    "TEAM_MATCH", "ChildFinish", "ITEM_WINNER_SIDE_MISMATCH",
                    "子项 " + item.getItemCode() + " winnerSide=" + item.getWinnerSide()
                            + " status=" + item.getStatus());

            int leftWins = countSideWins(world.items, "left");
            int rightWins = countSideWins(world.items, "right");

            // 探针（第 1 场完赛后，任何一方都不可能构成不可追平多数）：手动提前结算必须被
            // 真实 settleTeamMatch 的 directSettlement 分支拒绝（"requires all items finished"）。
            // 若真实门槛公式偏低（如允许 1 胜结算 3 项赛），此处会失败。
            if (!prematureProbeDone) {
                prematureProbeDone = true;
                try {
                    world.settlementService.settleTeamMatch(operator, parentId);
                    check(false, "TEAM_MATCH", "DirectSettlement", "PREMATURE_SETTLE_ACCEPTED",
                            "仅 1 项完赛时手动提前结算未被真实服务拒绝");
                } catch (IllegalArgumentException expected) {
                    check(expected.getMessage() != null
                                    && expected.getMessage().contains("requires all items finished"),
                            "TEAM_MATCH", "DirectSettlement", "REJECT_REASON_MISMATCH",
                            "手动提前结算拒绝理由与真实语义不符: " + expected.getMessage());
                }
            }

            if (settledAfter < 0 && Integer.valueOf(2).equals(parent.getStatus())) {
                settledAfter = finished;
                if (knockoutStage) {
                    // 语义断言（非公式复刻）：父场被真实服务结算的时刻，
                    // 胜方必须已构成"不可追平的多数"（2*胜场 > 总项数）。
                    // 若真实门槛偏低（提前一场结算）或偏高（错过触发点），此处会失败。
                    int winnerWins = Math.max(leftWins, rightWins);
                    check(2 * winnerWins > itemCount, "TEAM_MATCH", "EarlySettlement",
                            "SETTLED_WITHOUT_UNBEATABLE_MAJORITY",
                            "父场在 " + winnerWins + "/" + itemCount + "（仍可被追平）时即被真实服务结算");
                } else {
                    // 循环赛制：提前终局分支被真实条件禁用（earlyKnockout 要求 tournamentType != 2），
                    // 必须全部完赛才结算
                    check(finished == itemCount, "TEAM_MATCH", "FullSettlement", "SETTLED_BEFORE_ALL_FINISHED",
                            "循环赛制在 " + finished + "/" + itemCount + " 完赛时即被结算（提前终局不应生效）");
                }
            }

            // 手动提前终局路径：淘汰赛 + 裁判选择"直接结算" + 一方已构成不可追平多数。
            // 真实 settleTeamMatch 凭 earlyKnockout 接受（若门槛公式偏高会拒绝 -> 违规记录）。
            if (settledAfter < 0 && knockoutStage && earlyManualSettle
                    && (2 * leftWins > itemCount || 2 * rightWins > itemCount)) {
                try {
                    world.settlementService.settleTeamMatch(operator, parentId);
                    check(Integer.valueOf(2).equals(parent.getStatus()), "TEAM_MATCH", "EarlySettlement",
                            "MANUAL_SETTLE_NOT_EFFECTIVE",
                            "手动提前终局后父场 status=" + parent.getStatus());
                    settledAfter = finished;
                } catch (IllegalArgumentException rejected) {
                    check(false, "TEAM_MATCH", "EarlySettlement", "MANUAL_EARLY_SETTLE_REJECTED",
                            "已构成不可追平多数(" + leftWins + ":" + rightWins + "/" + itemCount
                                    + ")但真实 settleTeamMatch 拒绝: " + rejected.getMessage());
                    // 拒绝后继续打完剩余子项，由自动路径在全量完赛时兜底结算
                }
            }

            if (settledAfter >= 0 && knockoutStage) {
                break; // dead rubber：提前终局后剩余子项不再进行（"僵尸子项"，见下方真实行为断言）
            }
        }

        check(settledAfter >= 0, "TEAM_MATCH", "EarlySettlement", "PARENT_NOT_SETTLED",
                "所有子项结果均喂给真实服务后父场仍未结算");

        // 父场结算终态按真实语义断言（settleParentTeamMatch 写入的字段）
        int leftWins = countSideWins(world.items, "left");
        int rightWins = countSideWins(world.items, "right");
        String expectedWinnerId = leftWins > rightWins ? teamA : teamB;
        check(Integer.valueOf(2).equals(parent.getStatus()), "TEAM_MATCH", "ParentSettlement", "PARENT_STATUS",
                "父场结算后 status=" + parent.getStatus());
        check(expectedWinnerId.equals(parent.getWinnerId()), "TEAM_MATCH", "ParentSettlement", "PARENT_WINNER",
                "父场 winnerId=" + parent.getWinnerId() + " 期望 " + expectedWinnerId);
        check((leftWins + ":" + rightWins).equals(parent.getScoreDisplay()),
                "TEAM_MATCH", "ParentSettlement", "PARENT_SCORE",
                "父场 scoreDisplay=" + parent.getScoreDisplay() + " 期望 " + leftWins + ":" + rightWins);
        check(Integer.valueOf(leftWins).equals(parent.getLeftGameWins())
                        && Integer.valueOf(rightWins).equals(parent.getRightGameWins()),
                "TEAM_MATCH", "ParentSettlement", "PARENT_GAME_WINS",
                "父场 gameWins=" + parent.getLeftGameWins() + ":" + parent.getRightGameWins()
                        + " 期望 " + leftWins + ":" + rightWins);

        if (knockoutStage && settledAfter >= 0) {
            // b) 僵尸子项真实行为（对照调研文档风险 #3）：提前终局后剩余未打子项保持原状
            //    （winnerSide=null、status!=2，真实服务不清理也不补赛），且真实 startChildMatch
            //    守卫（ensureParentMatchEditable）拒绝在已结算父场上再开子场
            for (TeamMatchItem item : items) {
                if (item.getWinnerSide() != null) continue;
                check(!Integer.valueOf(2).equals(item.getStatus()),
                        "TEAM_MATCH", "DeadRubberLeftovers", "LEFTOVER_ITEM_MUTATED",
                        "提前终局后剩余子项 " + item.getItemCode() + " 被置为已完赛（真实行为应为保持原状）");
                try {
                    world.teamMatchService.startChildMatch(operator, parentId, item.getItemCode());
                    check(false, "TEAM_MATCH", "DeadRubberLeftovers", "START_AFTER_SETTLE_ACCEPTED",
                            "父场已结算后真实 startChildMatch 仍接受未打子项 " + item.getItemCode());
                } catch (IllegalArgumentException guard) {
                    check(guard.getMessage() != null && guard.getMessage().contains("团体赛已结束"),
                            "TEAM_MATCH", "DeadRubberLeftovers", "GUARD_REASON_MISMATCH",
                            "守卫拒绝理由与真实语义不符: " + guard.getMessage());
                }
                break; // 守卫在 loadContext 之前触发，与具体子项无关，探针一次即可
            }
            // d) 晋级传播：真实 propagateFinishedMatch 把胜者写入下一轮槽位，对侧槽位不受影响
            String propagated = "left".equals(nextSlot) ? nextMatch.getLeftPlayerId() : nextMatch.getRightPlayerId();
            check(expectedWinnerId.equals(propagated), "TEAM_MATCH", "Propagation", "WINNER_NOT_PROPAGATED",
                    "下一轮槽位 " + nextSlot + " 值=" + propagated + " 期望 " + expectedWinnerId);
            String otherSlotValue = "left".equals(nextSlot) ? nextMatch.getRightPlayerId() : nextMatch.getLeftPlayerId();
            check(otherSlotValue == null, "TEAM_MATCH", "Propagation", "OTHER_SLOT_TOUCHED",
                    "传播时改写了不应改写的对侧槽位: " + otherSlotValue);
            if (thirdMatch != null) {
                // 真实 propagateLoserIfNeeded 把败者写入季军赛槽位
                String loserId = expectedWinnerId.equals(teamA) ? teamB : teamA;
                String loserValue = "left".equals(loserSlot) ? thirdMatch.getLeftPlayerId() : thirdMatch.getRightPlayerId();
                check(loserId.equals(loserValue), "TEAM_MATCH", "Propagation", "LOSER_NOT_PROPAGATED",
                        "季军赛槽位值=" + loserValue + " 期望 " + loserId);
            }
        }

        cases.add(Map.of("scenario", "TeamMatch", "items", itemCount,
                "mode", knockoutStage ? "knockout" : "roundrobin",
                "settledAfter", settledAfter, "earlySettled", settledAfter >= 0 && settledAfter < itemCount,
                "score", leftWins + ":" + rightWins));
    }

    // =========================================================================
    // 场景 4: 羽毛球接力追分赛（环状名单拓扑 + 恶意探针 + 真实追分目标计算）
    //
    // 驱动真实代码：TeamMatchServiceImpl.saveLineup（内部 normalizeItems -> normalizeRelayItems
    // -> validateRelayLineup -> upsertLineupItems -> loadContext 回读 buildVO）。
    // 断言对象是真实服务持久化后回读的 VO（而非测试输入），环状拓扑/分段编码/追分目标
    // 均来自真实 normalize 与 relayBaseScore 计算。
    // =========================================================================
    private void chaosRelayTournament(long caseSeed) {
        currentCaseSeed = caseSeed;
        Random rnd = new Random(caseSeed);
        int memberCount = rnd.nextInt(10) + 3; // 3 ~ 12 人接力环（relayMemberCount 上下限之内）
        int baseScore = choice(rnd, new Integer[]{10, 11, 15});
        String operator = "op_" + caseSeed;
        String tournamentId = "tt_relay_" + caseSeed;
        String parentId = "relay_parent_" + caseSeed;
        String teamA = "relayA_" + caseSeed;
        String teamB = "relayB_" + caseSeed;

        Tournament tournament = new Tournament();
        tournament.setId(tournamentId);
        tournament.setName("接力追分赛_" + caseSeed);
        tournament.setCreatorUserId(operator);
        tournament.setSportType(0);
        tournament.setParticipantType(1);
        tournament.setTeamMatchTemplate(2);
        tournament.setTournamentType(1);
        tournament.setStatus(1);
        tournament.setCapPoint(memberCount); // 真实 relayMemberCount 来源：capPoint 夹在 [3,12]
        tournament.setPointsToWin(baseScore); // 真实 relayBaseScore 来源
        tournament.setBestOf(3);
        tournament.setGamesToWin(2);

        Player leftTeam = new Player();
        leftTeam.setId(teamA);
        leftTeam.setName("接力A队");
        Player rightTeam = new Player();
        rightTeam.setId(teamB);
        rightTeam.setName("接力B队");
        List<TournamentTeamMember> leftMembers = makeTeamMembers(tournamentId, teamA, "LM_", memberCount);
        List<TournamentTeamMember> rightMembers = makeTeamMembers(tournamentId, teamB, "RM_", memberCount);

        // 左队环：随机旋转；右队环：随机全排列——两队的环序互相独立
        List<String> leftRing = new ArrayList<>();
        List<String> rightRing = new ArrayList<>();
        for (TournamentTeamMember m : leftMembers) leftRing.add(m.getId());
        for (TournamentTeamMember m : rightMembers) rightRing.add(m.getId());
        Collections.rotate(leftRing, rnd.nextInt(memberCount));
        Collections.shuffle(rightRing, rnd);

        MatchRecord parent = new MatchRecord();
        parent.setId(parentId);
        parent.setTournamentId(tournamentId);
        parent.setStageType(1);
        parent.setStatus(1);
        parent.setRoundNum(1);
        parent.setMatchIndex(0);
        parent.setLeftPlayerId(teamA);
        parent.setRightPlayerId(teamB);

        TeamServiceWorld world = buildTeamServiceWorld(tournament, leftTeam, rightTeam, leftMembers, rightMembers);
        world.matches.put(parentId, parent);

        // 合法环状名单喂给真实 saveLineup
        SaveTeamMatchLineupReq req = buildRelayLineupRequest(leftRing, rightRing, relayCodes(memberCount));
        TeamMatchLineupVO vo = world.teamMatchService.saveLineup(operator, parentId, req);

        check(vo != null && vo.getItems() != null && vo.getItems().size() == memberCount,
                "RELAY_TOURNAMENT", "LineupNormalize", "SEGMENT_COUNT_MISMATCH",
                "真实服务返回分段数=" + (vo == null || vo.getItems() == null ? "null" : vo.getItems().size())
                        + " 期望 " + memberCount);
        if (vo != null && vo.getItems() != null && vo.getItems().size() == memberCount) {
            List<List<String>> leftPairs = new ArrayList<>();
            List<List<String>> rightPairs = new ArrayList<>();
            for (int i = 0; i < memberCount; i++) {
                TeamMatchLineupVO.ItemVO item = vo.getItems().get(i);
                check(("R" + (i + 1)).equals(item.getItemCode()),
                        "RELAY_TOURNAMENT", "LineupNormalize", "CODE_SEQUENCE_BROKEN",
                        "第 " + (i + 1) + " 段编码=" + item.getItemCode() + " 期望 R" + (i + 1));
                check(Integer.valueOf(2).equals(item.getPlayerCount()),
                        "RELAY_TOURNAMENT", "LineupNormalize", "PLAYER_COUNT_NOT_TWO",
                        "第 " + (i + 1) + " 段 playerCount=" + item.getPlayerCount());
                leftPairs.add(memberIds(item.getLeftMembers()));
                rightPairs.add(memberIds(item.getRightMembers()));
            }
            // 真实 normalize 输出的环状拓扑守恒：第 i 段第二人 == 第 i+1 段第一人（含尾段接回首段）
            checkRelayRing(leftPairs, "左队");
            checkRelayRing(rightPairs, "右队");
            // 追分目标由真实服务计算（relayBaseScore * 段数 = 累计追分上限）
            check(Integer.valueOf(memberCount).equals(vo.getRelayMemberCount()),
                    "RELAY_TOURNAMENT", "TargetScore", "MEMBER_COUNT_MISMATCH",
                    "relayMemberCount=" + vo.getRelayMemberCount() + " 期望 " + memberCount);
            check(Integer.valueOf(baseScore).equals(vo.getRelayBaseScore()),
                    "RELAY_TOURNAMENT", "TargetScore", "BASE_SCORE_MISMATCH",
                    "relayBaseScore=" + vo.getRelayBaseScore() + " 期望 " + baseScore);
            check(Integer.valueOf(baseScore * memberCount).equals(vo.getRelayTargetScore()),
                    "RELAY_TOURNAMENT", "TargetScore", "TARGET_SCORE_MISMATCH",
                    "relayTargetScore=" + vo.getRelayTargetScore() + " 期望 " + baseScore * memberCount);
        }
        // 持久层收到 R1..RN（经 mock mapper 的 insert 落入内存 world.items）
        List<String> persistedCodes = world.items.stream()
                .sorted(Comparator.comparingInt(i -> i.getDisplayOrder() == null ? 0 : i.getDisplayOrder()))
                .map(TeamMatchItem::getItemCode)
                .toList();
        check(relayCodes(memberCount).equals(persistedCodes),
                "RELAY_TOURNAMENT", "Persistence", "PERSISTED_CODES_MISMATCH",
                "入库分段编码=" + persistedCodes + " 期望 R1..R" + memberCount);

        // 恶意探针（每案例 2~3 个，种子化选取），断言真实校验拒绝且理由符合源码文案
        int probeCount = 2 + rnd.nextInt(2);
        List<Integer> kinds = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6));
        Collections.shuffle(kinds, rnd);
        for (int p = 0; p < probeCount && p < kinds.size(); p++) {
            RelayProbe probe = buildRelayProbe(kinds.get(p), leftRing, rightRing, rnd);
            try {
                world.teamMatchService.saveLineup(operator, parentId, probe.req());
                check(false, "RELAY_TOURNAMENT", "MaliciousProbe", probe.type() + "_ACCEPTED",
                        "非法名单未被真实校验拒绝: " + probe.desc());
            } catch (IllegalArgumentException rejected) {
                check(rejected.getMessage() != null && rejected.getMessage().contains(probe.expectedPhrase()),
                        "RELAY_TOURNAMENT", "MaliciousProbe", probe.type() + "_REASON_MISMATCH",
                        "拒绝理由与真实语义不符: got '" + rejected.getMessage()
                                + "' 期望包含 '" + probe.expectedPhrase() + "'");
            }
            // 校验必须先于 upsert：失败的 saveLineup 不得触发任何持久化写入
            check(world.items.size() == memberCount,
                    "RELAY_TOURNAMENT", "MaliciousProbe", probe.type() + "_PERSISTED",
                    "非法名单触发了持久化写入（校验应先于 upsert）");
        }

        cases.add(Map.of("scenario", "RelayTournament", "members", memberCount,
                "targetScore", baseScore * memberCount));
    }

    // =========================================================================
    // 真实服务装配：mock 持久层 + 真实 MatchSettlementService / TeamMatchServiceImpl
    // =========================================================================

    /** 单个混沌案例的内存世界：mock mapper 的读写都落在这里，服务实例对它产生真实副作用 */
    private static final class TeamServiceWorld {
        final Tournament tournament;
        final Player leftTeam;
        final Player rightTeam;
        final List<TournamentTeamMember> leftMembers;
        final List<TournamentTeamMember> rightMembers;
        final Map<String, MatchRecord> matches = new LinkedHashMap<>();
        final List<TeamMatchItem> items = new ArrayList<>();
        MatchSettlementService settlementService;
        TeamMatchServiceImpl teamMatchService;

        private TeamServiceWorld(Tournament tournament, Player leftTeam, Player rightTeam,
                                 List<TournamentTeamMember> leftMembers, List<TournamentTeamMember> rightMembers) {
            this.tournament = tournament;
            this.leftTeam = leftTeam;
            this.rightTeam = rightTeam;
            this.leftMembers = leftMembers;
            this.rightMembers = rightMembers;
        }
    }

    /**
     * 构造带内存持久层的真实服务世界。注入方式依据服务实际结构：
     * 两个服务均为构造器注入（无 @Autowired 字段），直接 new；mapper 全部 Mockito mock，
     * 读走内存 Map/List，updateById 按 MyBatis-Plus 默认策略只覆盖非 null 字段。
     */
    private TeamServiceWorld buildTeamServiceWorld(Tournament tournament, Player leftTeam, Player rightTeam,
                                                   List<TournamentTeamMember> leftMembers,
                                                   List<TournamentTeamMember> rightMembers) {
        TeamServiceWorld world = new TeamServiceWorld(tournament, leftTeam, rightTeam, leftMembers, rightMembers);

        MatchRecordMapper matchMapper = mock(MatchRecordMapper.class);
        TeamMatchItemMapper itemMapper = mock(TeamMatchItemMapper.class);
        TournamentMapper tournamentMapper = mock(TournamentMapper.class);
        TournamentDivisionMapper divisionMapper = mock(TournamentDivisionMapper.class);
        MatchEventMapper eventMapper = mock(MatchEventMapper.class);
        MatchLineupConfigMapper lineupMapper = mock(MatchLineupConfigMapper.class);
        MatchReportMetaMapper reportMetaMapper = mock(MatchReportMetaMapper.class);
        TournamentQualificationOverrideMapper qualOverrideMapper = mock(TournamentQualificationOverrideMapper.class);
        TournamentRefereeGrantMapper refereeGrantMapper = mock(TournamentRefereeGrantMapper.class);
        PlayerMapper playerMapper = mock(PlayerMapper.class);
        TournamentTeamMemberMapper memberMapper = mock(TournamentTeamMemberMapper.class);
        TournamentCustomItemMapper customItemMapper = mock(TournamentCustomItemMapper.class);
        TournamentRoundRuleMapper roundRuleMapper = mock(TournamentRoundRuleMapper.class);

        when(matchMapper.selectByIdForUpdate(anyString()))
                .thenAnswer(inv -> world.matches.get(inv.getArgument(0, String.class)));
        when(matchMapper.selectById(anyString()))
                .thenAnswer(inv -> world.matches.get(inv.getArgument(0, String.class)));
        when(matchMapper.updateById(any(MatchRecord.class))).thenAnswer(inv -> {
            MatchRecord patch = inv.getArgument(0);
            MatchRecord stored = world.matches.get(patch.getId());
            if (stored != null) applyPatch(stored, patch);
            return 1;
        });
        when(itemMapper.selectList(any())).thenAnswer(inv -> {
            List<TeamMatchItem> sorted = new ArrayList<>(world.items);
            sorted.sort(Comparator.comparingInt(i -> i.getDisplayOrder() == null ? 0 : i.getDisplayOrder()));
            return sorted;
        });
        when(itemMapper.selectOne(any())).thenAnswer(inv -> {
            Object wrapper = inv.getArgument(0);
            if (!(wrapper instanceof QueryWrapper<?> qw)) return null;
            Collection<Object> paramValues = wrapperParams(qw);
            return world.items.stream()
                    .filter(i -> i.getChildMatchId() != null && paramValues.contains(i.getChildMatchId()))
                    .findFirst().orElse(null);
        });
        when(itemMapper.updateById(any(TeamMatchItem.class))).thenAnswer(inv -> {
            TeamMatchItem patch = inv.getArgument(0);
            world.items.stream().filter(i -> i.getId().equals(patch.getId())).findFirst()
                    .ifPresent(stored -> applyPatch(stored, patch));
            return 1;
        });
        when(itemMapper.insert(any(TeamMatchItem.class))).thenAnswer(inv -> {
            TeamMatchItem item = inv.getArgument(0);
            if (item.getId() == null) item.setId(UUID.randomUUID().toString());
            world.items.add(item);
            return 1;
        });
        when(tournamentMapper.selectById(anyString())).thenReturn(tournament);
        when(tournamentMapper.selectByIdForUpdate(anyString())).thenReturn(tournament);
        when(tournamentMapper.updateById(any(Tournament.class))).thenAnswer(inv -> {
            applyPatch(world.tournament, inv.getArgument(0));
            return 1;
        });
        when(playerMapper.selectById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0, String.class);
            if (leftTeam.getId().equals(id)) return leftTeam;
            if (rightTeam.getId().equals(id)) return rightTeam;
            return null;
        });
        when(memberMapper.selectList(any())).thenAnswer(inv -> {
            Object wrapper = inv.getArgument(0);
            Collection<Object> paramValues = wrapper instanceof QueryWrapper<?> qw
                    ? wrapperParams(qw) : List.of();
            if (paramValues.contains(leftTeam.getId())) return new ArrayList<>(world.leftMembers);
            if (paramValues.contains(rightTeam.getId())) return new ArrayList<>(world.rightMembers);
            return new ArrayList<TournamentTeamMember>();
        });

        // 无持久化逻辑的协作者用真实实例，走真实分支（规则解析、操作权限守卫）
        TournamentRuleResolver ruleResolver = new TournamentRuleResolver(roundRuleMapper, itemMapper, matchMapper, divisionMapper);
        MatchAccessGuard accessGuard = new MatchAccessGuard(tournamentMapper, refereeGrantMapper);
        // MatchLockService.clearMatchLock 走 LambdaUpdateWrapper（依赖 MyBatis-Plus TableInfo 元数据，
        // 纯单测不初始化会抛异常），且本测试全部调用无锁重载，锁校验逻辑不在覆盖范围，故 mock
        MatchLockService lockService = mock(MatchLockService.class);
        MatchReportAssembler reportAssembler = mock(MatchReportAssembler.class);

        world.settlementService = new MatchSettlementService(matchMapper, tournamentMapper, itemMapper, divisionMapper,
                eventMapper, lineupMapper, reportMetaMapper, qualOverrideMapper, ruleResolver, accessGuard,
                reportAssembler, lockService);
        world.teamMatchService = new TeamMatchServiceImpl(matchMapper, reportMetaMapper, tournamentMapper, playerMapper,
                memberMapper, itemMapper, refereeGrantMapper, customItemMapper, ruleResolver);
        return world;
    }

    /** 模拟 MyBatis-Plus updateById 默认策略：仅覆盖非 null 字段 */
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

    private List<TournamentTeamMember> makeTeamMembers(String tournamentId, String participantId,
                                                       String idPrefix, int count) {
        List<TournamentTeamMember> members = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TournamentTeamMember member = new TournamentTeamMember();
            member.setId(idPrefix + i);
            member.setTournamentId(tournamentId);
            member.setParticipantId(participantId);
            member.setName(idPrefix + "队员" + i);
            member.setDisplayOrder(i);
            member.setCaptain(i == 0);
            members.add(member);
        }
        return members;
    }

    private List<TournamentTeamMember> makeTeamMembers(String tournamentId, String participantId, String idPrefix) {
        return makeTeamMembers(tournamentId, participantId, idPrefix, 6);
    }

    private String memberIdsJson(List<TournamentTeamMember> members, int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count && i < members.size(); i++) ids.add(members.get(i).getId());
        return JSONUtil.toJsonStr(ids);
    }

    private int countSideWins(List<TeamMatchItem> items, String side) {
        int wins = 0;
        for (TeamMatchItem item : items) {
            if (side.equals(item.getWinnerSide())) wins++;
        }
        return wins;
    }

    /** 构造能通过真实 validateFinishReq 的完赛请求（2:0 或 2:1，与 tournament.gamesToWin=2 自洽） */
    private FinishMatchReq buildChildFinishReq(String winnerSide, Random rnd) {
        FinishMatchReq req = new FinishMatchReq();
        req.setWinnerSide(winnerSide);
        int loserGames = rnd.nextInt(2);
        List<FinishMatchReq.GameScore> games = new ArrayList<>();
        int leftPoints = 0;
        int rightPoints = 0;
        int loserGamesLeft = loserGames;
        for (int g = 1; g <= 2 + loserGames; g++) {
            String gameWinner;
            if (loserGamesLeft > 0) {
                gameWinner = "left".equals(winnerSide) ? "right" : "left";
                loserGamesLeft--;
            } else {
                gameWinner = winnerSide;
            }
            int winnerPts = 21;
            int loserPts = 12 + rnd.nextInt(8);
            int leftScore = "left".equals(gameWinner) ? winnerPts : loserPts;
            int rightScore = "left".equals(gameWinner) ? loserPts : winnerPts;
            leftPoints += leftScore;
            rightPoints += rightScore;
            FinishMatchReq.GameScore game = new FinishMatchReq.GameScore();
            game.setGameNo(g);
            game.setLeftScore(leftScore);
            game.setRightScore(rightScore);
            game.setWinnerSide(gameWinner);
            games.add(game);
        }
        req.setGameScores(games);
        req.setLeftScore(leftPoints);
        req.setRightScore(rightPoints);
        if ("left".equals(winnerSide)) {
            req.setLeftGameWins(2);
            req.setRightGameWins(loserGames);
        } else {
            req.setRightGameWins(2);
            req.setLeftGameWins(loserGames);
        }
        return req;
    }

    // ------------------------- 接力赛辅助 -------------------------

    private record RelayProbe(String type, String desc, SaveTeamMatchLineupReq req, String expectedPhrase) {}

    private List<String> relayCodes(int count) {
        List<String> codes = new ArrayList<>();
        for (int i = 1; i <= count; i++) codes.add("R" + i);
        return codes;
    }

    private SaveTeamMatchLineupReq buildRelayLineupRequest(List<String> leftRing, List<String> rightRing,
                                                            List<String> codes) {
        SaveTeamMatchLineupReq req = new SaveTeamMatchLineupReq();
        List<SaveTeamMatchLineupReq.ItemLineup> items = new ArrayList<>();
        int k = leftRing.size();
        for (int i = 0; i < codes.size(); i++) {
            SaveTeamMatchLineupReq.ItemLineup item = new SaveTeamMatchLineupReq.ItemLineup();
            item.setItemCode(codes.get(i));
            item.setLeftMemberIds(List.of(leftRing.get(i), leftRing.get((i + 1) % k)));
            item.setRightMemberIds(List.of(rightRing.get(i), rightRing.get((i + 1) % k)));
            items.add(item);
        }
        req.setItems(items);
        return req;
    }

    private List<String> memberIds(List<TeamMatchLineupVO.MemberVO> members) {
        List<String> ids = new ArrayList<>();
        if (members == null) return ids;
        for (TeamMatchLineupVO.MemberVO member : members) ids.add(member.getId());
        return ids;
    }

    /** 校验真实 normalize 持久化后回读出的环状拓扑：相邻段相扣 + 首位队员不重复 */
    private void checkRelayRing(List<List<String>> pairs, String sideLabel) {
        Set<String> firstMembers = new HashSet<>();
        for (int i = 0; i < pairs.size(); i++) {
            List<String> current = pairs.get(i);
            List<String> next = pairs.get((i + 1) % pairs.size());
            check(current.size() == 2, "RELAY_TOURNAMENT", "RealChainTopology", "PAIR_NOT_DOUBLE",
                    sideLabel + " 第 " + (i + 1) + " 段成员数=" + current.size() + "（每段必须是双打）");
            if (current.size() != 2) return;
            firstMembers.add(current.get(0));
            check(current.get(1).equals(next.get(0)), "RELAY_TOURNAMENT", "RealChainTopology", "CHAIN_BROKEN",
                    sideLabel + " 真实 normalize 输出在第 " + (i + 1) + " 段断链: "
                            + current.get(1) + " != " + next.get(0));
        }
        check(firstMembers.size() == pairs.size(), "RELAY_TOURNAMENT", "RealChainTopology", "FIRST_MEMBER_NOT_UNIQUE",
                sideLabel + " 各段首位队员存在重复: 去重后 " + firstMembers.size() + "/" + pairs.size());
    }

    /**
     * 恶意探针构造（expectedPhrase 与 TeamMatchServiceImpl 源码文案逐一对照）：
     * 0 破坏环 / 1 分段数少 1 / 2 编码不连续 / 3 null 项（真实语义：静默跳过后按人数拒绝）/
     * 4 外队队员 / 5 段内 3 人 / 6 首位队员跨段重复
     */
    private RelayProbe buildRelayProbe(int kind, List<String> leftRing, List<String> rightRing, Random rnd) {
        int k = leftRing.size();
        List<String> codes = relayCodes(k);
        switch (kind) {
            case 0: {
                SaveTeamMatchLineupReq req = buildRelayLineupRequest(leftRing, rightRing, codes);
                req.getItems().get(0).setLeftMemberIds(List.of(leftRing.get(0), leftRing.get(2 % k)));
                return new RelayProbe("BROKEN_RING",
                        "左队第 1 段第二人换成队员 " + leftRing.get(2 % k),
                        req, "接力顺序必须按相邻队员流转");
            }
            case 1: {
                return new RelayProbe("SEGMENT_COUNT", "只提交 " + (k - 1) + " 段",
                        buildRelayLineupRequest(leftRing, rightRing, relayCodes(k - 1)),
                        "出场人数必须等于");
            }
            case 2: {
                List<String> badCodes = new ArrayList<>();
                badCodes.add("R1");
                for (int i = 1; i < k; i++) badCodes.add("R" + (i + 2));
                return new RelayProbe("NON_CONTIGUOUS", "编码 R1,R3..R" + (k + 1),
                        buildRelayLineupRequest(leftRing, rightRing, badCodes),
                        "接力赛分段必须从 R1 连续到 RN");
            }
            case 3: {
                SaveTeamMatchLineupReq req = buildRelayLineupRequest(leftRing, rightRing, codes);
                // 真实语义：null 项被 normalizeRelayItems 静默跳过，等效分段数少 1 -> 人数校验拒绝
                req.getItems().set(rnd.nextInt(req.getItems().size()), null);
                return new RelayProbe("NULL_ITEM", "k 段中 1 项替换为 null", req, "出场人数必须等于");
            }
            case 4: {
                SaveTeamMatchLineupReq req = buildRelayLineupRequest(leftRing, rightRing, codes);
                req.getItems().get(0).setLeftMemberIds(List.of(leftRing.get(0), "ghost_member"));
                return new RelayProbe("FOREIGN_MEMBER", "第 1 段混入外队队员", req, "不属于本队");
            }
            case 5: {
                SaveTeamMatchLineupReq req = buildRelayLineupRequest(leftRing, rightRing, codes);
                req.getItems().get(0).setLeftMemberIds(
                        List.of(leftRing.get(0), leftRing.get(1), leftRing.get(2 % k)));
                return new RelayProbe("SEGMENT_SIZE", "第 1 段左队 3 人", req, "需要 2 人");
            }
            default: {
                // 首位跨段重复：末两段改为 [L(k-2),L0] 与 [L0,L(k-1)]，保持倒数第二段的
                // 连续性（第 k-1 段首位 = 第 k-2 段第二人），使校验推进到末段才触发重复检查
                SaveTeamMatchLineupReq req = buildRelayLineupRequest(leftRing, rightRing, codes);
                req.getItems().get(k - 2).setLeftMemberIds(List.of(leftRing.get(k - 2), leftRing.get(0)));
                req.getItems().get(k - 1).setLeftMemberIds(List.of(leftRing.get(0), leftRing.get(k - 1)));
                return new RelayProbe("DUP_FIRST_MEMBER", "末段首位与第 1 段首位重复（前段连续性保持）",
                        req, "队员顺序不能重复");
            }
        }
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

        // try/finally：场景循环中任何意外异常也必须保证 journal 落盘
        try {
            for (int i = 0; i < scale; i++) {
                chaosIndividualKnockout(random.nextLong());
                chaosGroupAndKnockout(random.nextLong());
                chaosTeamTournamentAndEarlySettlement(random.nextLong());
                chaosRelayTournament(random.nextLong());
            }
        } finally {
            writeJournal();
        }

        if (!violations.isEmpty()) {
            System.err.println("════ 羽毛球生命周期混沌发现 " + violations.size() + " 处违反期望 ════");
            for (Violation v : violations) {
                System.err.println("[caseSeed=" + v.caseSeed() + "][" + v.category() + "][" + v.scenario() + "] "
                        + v.type() + ": " + v.message());
            }
            AssertionError failure = new AssertionError("羽毛球生命周期混沌测试发现 " + violations.size() + " 处违规");
            for (Violation v : violations) {
                failure.addSuppressed(new AssertionError("caseSeed=" + v.caseSeed() + " " + v.category()
                        + "/" + v.scenario() + "/" + v.type() + ": " + v.message()));
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
            node.put("caseSeed", v.caseSeed());
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
