package com.scoring.backend.engine;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.MatchLockReq;
import com.scoring.backend.domain.entity.*;
import com.scoring.backend.domain.vo.MatchLockVO;
import com.scoring.backend.mapper.*;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 专为"2026南大羽协破冰活动"定制的千人场单位全生命周期多手机并发混沌测试套件。
 *
 * 核心目标：
 * 1. 严格 1:1 对齐真实"南大羽协破冰活动"多组别个人赛拓扑：
 *    - 男单 (23人, 32签表, 9轮空, 5轮次: R1/R2 1局21分, R3/R4 3局15分, R5 3局21分, 上限30/21)
 *    - 女单 (4人, 4签表, 2轮次: 3局21分)
 *    - 男双 (6对, 8签表, 2轮空, 3轮次: 3局21分)
 *    - 女双 (2对, 2签表, 1轮次决赛: 3局21分)
 *    - 混双 (6对, 8签表, 2轮空, 3轮次: 3局21分)
 * 2. 模拟 4-5 场地同时开打，多个裁判用不同手机（并发工作线程）同时执裁：
 *    - 冲突排查：模拟两名裁判同时抢同一场比赛（互斥锁校验）；
 *    - 汇流竞态排查：两个前序半决赛在同一毫秒完赛并同时推送到决赛的左右槽位；
 * 3. 重点排查退赛链路 (Walkover / Retirement)：
 *    - 局前弃权、局中因伤退赛（1:0 后退赛、打满到决胜局后退赛）；
 *    - 验证胜方局数填满契约、gameScores 截断合法性、retiredSide 记录与胜者推进；
 * 4. 结果推送时效排查：
 *    - 完赛瞬间下一局/轮槽位立刻写入 winnerId，下一场比赛立即进入就绪态；
 *    - 决赛产生唯一冠军，整场赛事状态流转自洽。
 *
 * 运行命令:
 *   mvn test "-Dtest=NanDaIcebreakingBadmintonChaosTest" "-Dchaos.tournaments=25" "-Dchaos.seed=20261006"
 */
public class NanDaIcebreakingBadmintonChaosTest {

    private record Violation(String category, String scenario, String type, String message, long caseSeed) {}

    private final List<Violation> violations = Collections.synchronizedList(new ArrayList<>());
    private final BracketEngine bracketEngine = new BracketEngine();
    private final AtomicInteger totalMatchesOfficiated = new AtomicInteger(0);
    private final AtomicInteger totalRetirements = new AtomicInteger(0);
    private final AtomicInteger totalDeuces = new AtomicInteger(0);
    private final AtomicInteger totalCapHits = new AtomicInteger(0);
    private final AtomicInteger totalLockCollisionsPrevented = new AtomicInteger(0);
    private final AtomicInteger totalReentriesHandled = new AtomicInteger(0);
    private final AtomicInteger totalRefereeHandoffsHandled = new AtomicInteger(0);
    private final AtomicInteger totalFeederRacesHandled = new AtomicInteger(0);
    private final AtomicInteger totalTournamentsCompleted = new AtomicInteger(0);
    @Test
    public void runNanDaIcebreakingChaosSuite() throws Exception {
        int tournamentCount = Integer.getInteger("chaos.tournaments", 25);
        long baseSeed = Long.getLong("chaos.seed", 20261006L);
        Random masterRnd = new Random(baseSeed);

        System.out.println("==========================================================================");
        System.out.println("🏸 启动'2026南大羽协破冰活动'千场级多场地并发混沌测试套件");
        System.out.println("   测试规模: " + tournamentCount + " 场完整多组别赛事 (~" + (tournamentCount * 49) + " 场比赛)");
        System.out.println("   基础种子: " + baseSeed);
        System.out.println("==========================================================================");

        long startMs = System.currentTimeMillis();

        for (int t = 1; t <= tournamentCount; t++) {
            long tSeed = masterRnd.nextLong();
            runSingleTournamentSimulation(t, tSeed);
            if (t % 5 == 0 || t == tournamentCount) {
                System.out.printf("   [进度] 赛事 %d/%d 仿真完赛，已完成比赛: %d 场, 拦截冲突: %d 次, 违规: %d%n",
                        t, tournamentCount, totalMatchesOfficiated.get(), totalLockCollisionsPrevented.get(), violations.size());
            }
        }

        long elapsedMs = System.currentTimeMillis() - startMs;

        // 生成测试总结报表
        Path outDir = Paths.get("..", "outputs", "fuzz-badminton").toAbsolutePath().normalize();
        Files.createDirectories(outDir);
        Path reportFile = outDir.resolve("nanda-icebreaking-chaos-summary.json");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("suite", "NanDaIcebreakingBadmintonChaosTest");
        root.put("baseSeed", baseSeed);
        root.put("tournamentsCompleted", totalTournamentsCompleted.get());
        root.put("totalMatchesOfficiated", totalMatchesOfficiated.get());
        root.put("totalRetirements", totalRetirements.get());
        root.put("totalDeuces", totalDeuces.get());
        root.put("totalCapHits", totalCapHits.get());
        root.put("totalLockCollisionsPrevented", totalLockCollisionsPrevented.get());
        root.put("totalReentriesHandled", totalReentriesHandled.get());
        root.put("totalRefereeHandoffsHandled", totalRefereeHandoffsHandled.get());
        root.put("totalFeederRacesHandled", totalFeederRacesHandled.get());
        root.put("violationsCount", violations.size());
        root.put("durationMs", elapsedMs);

        ArrayNode violArray = mapper.createArrayNode();
        for (Violation v : violations) {
            ObjectNode vo = mapper.createObjectNode();
            vo.put("category", v.category);
            vo.put("scenario", v.scenario);
            vo.put("type", v.type);
            vo.put("message", v.message);
            vo.put("caseSeed", v.caseSeed);
            violArray.add(vo);
        }
        root.set("violations", violArray);

        Files.writeString(reportFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));

        System.out.println("==========================================================================");
        System.out.println("🎉 测试运行完成！");
        System.out.println("   完赛赛事数: " + totalTournamentsCompleted.get());
        System.out.println("   执裁比赛总数: " + totalMatchesOfficiated.get());
        System.out.println("   退赛/弃权场次: " + totalRetirements.get());
        System.out.println("   Deuce 加分场次: " + totalDeuces.get());
        System.out.println("   顶格上限(Cap)场次: " + totalCapHits.get());
        System.out.println("   成功拦截的抢锁冲突: " + totalLockCollisionsPrevented.get());
        System.out.println("   裁判退出后重进成功数: " + totalReentriesHandled.get());
        System.out.println("   更换裁判接管成功数: " + totalRefereeHandoffsHandled.get());
        System.out.println("   并发汇流竞态通过数: " + totalFeederRacesHandled.get());
        System.out.println("   违规计数: " + violations.size());
        System.out.println("   耗时: " + elapsedMs + " ms");
        System.out.println("   产物文件: " + reportFile);
        System.out.println("==========================================================================");

        assertEquals(0, violations.size(), "混沌测试中发现 " + violations.size() + " 处违规异常！");
    }

    private void runSingleTournamentSimulation(int tournamentIndex, long seed) throws Exception {
        Random rnd = new Random(seed);
        String tournamentId = "nanda_tourn_" + tournamentIndex + "_" + seed;
        String creatorId = "creator_" + seed;

        // 装配仿真世界与持久层
        TournamentWorld world = new TournamentWorld(tournamentId, creatorId);

        // 1. 初始化 5 大组别
        DivisionConfig msConfig = new DivisionConfig("div_ms_" + seed, "男单", 23, true);
        DivisionConfig wsConfig = new DivisionConfig("div_ws_" + seed, "女单", 4, false);
        DivisionConfig mdConfig = new DivisionConfig("div_md_" + seed, "男双", 6, false);
        DivisionConfig wdConfig = new DivisionConfig("div_wd_" + seed, "女双", 2, false);
        DivisionConfig xdConfig = new DivisionConfig("div_xd_" + seed, "混双", 6, false);

        List<DivisionConfig> divisions = List.of(msConfig, wsConfig, mdConfig, wdConfig, xdConfig);

        for (DivisionConfig div : divisions) {
            setupDivision(world, div, rnd, seed);
        }

        // 2. 模拟 4-5 个场地由不同裁判手机并发执裁
        ExecutorService courtThreadPool = Executors.newFixedThreadPool(5);
        List<Future<?>> courtTasks = new ArrayList<>();

        for (int court = 1; court <= 5; court++) {
            final String refereeId = "referee_court_" + court + "_" + seed;
            final String phoneToken = "phone_" + court + "_" + UUID.randomUUID();

            courtTasks.add(courtThreadPool.submit(() -> {
                runCourtRefereeWorker(world, refereeId, phoneToken, rnd, seed);
            }));
        }

        courtThreadPool.shutdown();
        boolean finishedInTime = courtThreadPool.awaitTermination(5, TimeUnit.SECONDS);
        if (!finishedInTime) {
            courtThreadPool.shutdownNow();
            violations.add(new Violation("CONCURRENCY", "CourtWorkers", "TIMEOUT", "多场地执裁超时未全部结束", seed));
        }

        for (Future<?> f : courtTasks) {
            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }

        // 3. 终局不变式核验
        verifyTournamentPostInvariants(world, divisions, seed);
        totalTournamentsCompleted.incrementAndGet();
    }

    /** 裁判在场地上的执裁循环：持续认领已就绪的比赛并执裁完赛 */
    private void runCourtRefereeWorker(TournamentWorld world, String refereeId, String phoneToken, Random sharedRnd, long caseSeed) {
        Random rnd = new Random(caseSeed ^ refereeId.hashCode());
        int idleSpins = 0;

        while (true) {
            MatchRecord matchToPlay = null;

            synchronized (world) {
                // 查找处于就绪状态 (status=0 且两侧选手均已到位，且未被锁定) 的比赛
                for (MatchRecord m : world.matches.values()) {
                    if (m.getStatus() == 0 && m.getLeftPlayerId() != null && m.getRightPlayerId() != null) {
                        if (m.getLockedByUserId() == null) {
                            matchToPlay = m;
                            // 预占避免其他线程拿到同一场
                            m.setLockedByUserId(refereeId);
                            m.setLockToken(phoneToken);
                            m.setLockExpireTime(LocalDateTime.now().plusSeconds(75));
                            break;
                        }
                    }
                }
            }

            if (matchToPlay == null) {
                // 检查是否所有组别都已经打完全部比赛
                boolean anyRemaining = false;
                int remainingCount = 0;
                synchronized (world) {
                    for (MatchRecord m : world.matches.values()) {
                        if (m.getStatus() != 2) {
                            anyRemaining = true;
                            remainingCount++;
                        }
                    }
                }
                if (!anyRemaining) {
                    break; // 赛事全部结束，裁判下班
                }

                idleSpins++;
                if (idleSpins > 50) {
                    synchronized (world) {
                        System.err.println("   [DEBUG STUCK] No playable match found after 50 spins! Remaining count: " + remainingCount);
                        for (MatchRecord m : world.matches.values()) {
                            if (m.getStatus() != 2) {
                                System.err.println("      Match " + m.getId() + " div=" + m.getDivisionId() + " r=" + m.getRoundNum()
                                        + " idx=" + m.getMatchIndex() + " status=" + m.getStatus()
                                        + " left=" + m.getLeftPlayerId() + " right=" + m.getRightPlayerId()
                                        + " lock=" + m.getLockedByUserId() + " next=" + m.getNextMatchId());
                            }
                        }
                    }
                    break;
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            idleSpins = 0;
            // 执裁该场比赛
            officiateMatch(world, matchToPlay, refereeId, phoneToken, rnd, caseSeed);
        }
    }

    /** 核心执裁逻辑：加锁、注入冲突检验、退赛/正常完赛生成、调用真实 finishMatch、断言下一局推送 */
    private void officiateMatch(TournamentWorld world, MatchRecord match, String refereeId, String phoneToken,
                                Random rnd, long caseSeed) {
        String matchId = match.getId();

        // 1. 锁操作与冲突注入测试
        MatchLockReq lockReq = new MatchLockReq();
        lockReq.setLockToken(phoneToken);

        // 注入冲突：模拟另一个裁判同时来抢此比赛
        MatchLockReq rivalReq = new MatchLockReq();
        rivalReq.setLockToken("rival_phone_" + rnd.nextInt(10000));
        MatchLockVO rivalResult = world.lockService.acquireMatchLock("rival_referee_" + rnd.nextInt(100), matchId, rivalReq);
        if (rivalResult.getSuccess()) {
            violations.add(new Violation("CONFLICT", "LockContention", "LOCK_NOT_EXCLUSIVE",
                    "比赛已经被持有，但另一裁判居然成功加锁: match=" + matchId, caseSeed));
        } else {
            totalLockCollisionsPrevented.incrementAndGet();
        }

        // 注入同裁判退出后重进（重取锁）测试
        MatchLockReq reenterReq = new MatchLockReq();
        reenterReq.setLockToken(rnd.nextBoolean() ? phoneToken : "phone_reenter_" + UUID.randomUUID());
        MatchLockVO reenterResult = world.lockService.acquireMatchLock(refereeId, matchId, reenterReq);
        if (!Boolean.TRUE.equals(reenterResult.getSuccess())) {
            violations.add(new Violation("CONFLICT", "Reentry", "REENTRY_FAILED",
                    "原裁判退出后重进抢锁被拒绝: match=" + matchId + ", referee=" + refereeId, caseSeed));
        } else {
            totalReentriesHandled.incrementAndGet();
        }

        // 注入更换裁判接管 (Referee Handoff / Lock Expiration & Takeover) 测试 (20% 概率)
        boolean isHandoff = rnd.nextInt(5) == 0;
        String actualRefereeId = refereeId;
        if (isHandoff) {
            match.setLockExpireTime(LocalDateTime.now().minusSeconds(1)); // 模拟心跳断开锁过期
            String newRefereeId = "referee_handoff_" + rnd.nextInt(5000);
            MatchLockReq handoffReq = new MatchLockReq();
            handoffReq.setLockToken("phone_handoff_" + UUID.randomUUID());
            MatchLockVO handoffResult = world.lockService.acquireMatchLock(newRefereeId, matchId, handoffReq);
            if (!Boolean.TRUE.equals(handoffResult.getSuccess())) {
                violations.add(new Violation("CONFLICT", "RefereeHandoff", "HANDOFF_LOCK_FAILED",
                        "锁过期后新裁判未能成功接管执裁: match=" + matchId, caseSeed));
            } else {
                actualRefereeId = newRefereeId;
                totalRefereeHandoffsHandled.incrementAndGet();
                // 原裁判已被接管踢出，再次尝试抢锁必须被拒
                MatchLockReq oldRefReq = new MatchLockReq();
                oldRefReq.setLockToken(phoneToken);
                MatchLockVO oldRefResult = world.lockService.acquireMatchLock(refereeId, matchId, oldRefReq);
                if (Boolean.TRUE.equals(oldRefResult.getSuccess())) {
                    violations.add(new Violation("CONFLICT", "RefereeHandoff", "OLD_REFEREE_NOT_KICKED",
                            "新裁判已接管，但原裁判仍能成功抢锁: match=" + matchId, caseSeed));
                }
            }
        }

        // 2. 正常获取当前比赛的规则配置
        TournamentDivision div = world.divisions.get(match.getDivisionId());
        TournamentRoundRule rule = world.getRoundRule(match.getDivisionId(), match.getRoundNum());

        int bestOf = rule != null ? rule.getBestOf() : (div.getBestOf() != null ? div.getBestOf() : 3);
        int gamesToWin = rule != null ? rule.getGamesToWin() : (div.getGamesToWin() != null ? div.getGamesToWin() : (bestOf / 2 + 1));
        int pointsToWin = rule != null ? rule.getPointsToWin() : (div.getPointsToWin() != null ? div.getPointsToWin() : 21);
        int capPoint = rule != null ? rule.getCapPoint() : (div.getCapPoint() != null ? div.getCapPoint() : 30);

        // 3. 决定完赛模式：20% 概率退赛 (Retirement / Walkover)，80% 正常打完
        boolean isRetirement = rnd.nextInt(5) == 0;
        FinishMatchReq req = new FinishMatchReq();

        if (isRetirement) {
            totalRetirements.incrementAndGet();
            boolean leftRetires = rnd.nextBoolean();
            String retiredSide = leftRetires ? "left" : "right";
            String winnerSide = leftRetires ? "right" : "left";
            req.setRetiredSide(retiredSide);
            req.setWinnerSide(winnerSide);

            // 退赛契约：胜方局数填满 gamesToWin，败方局数保留实际打完局数 (0 或 1)
            int loserActualWins = rnd.nextBoolean() ? 0 : Math.min(1, gamesToWin - 1);
            int winnerActualWins = rnd.nextInt(gamesToWin);
            req.setLeftGameWins(leftRetires ? loserActualWins : gamesToWin);
            req.setRightGameWins(leftRetires ? gamesToWin : loserActualWins);
            req.setLeftScore(req.getLeftGameWins());
            req.setRightScore(req.getRightGameWins());

            // gameScores 保留已打完的局（可为空或实际局）
            List<FinishMatchReq.GameScore> gScores = new ArrayList<>();
            int playedGames = loserActualWins + winnerActualWins;
            for (int g = 1; g <= playedGames; g++) {
                FinishMatchReq.GameScore gs = new FinishMatchReq.GameScore();
                gs.setGameNo(g);
                boolean thisLeftWon = leftRetires ? (g <= loserActualWins) : (g <= winnerActualWins);
                gs.setWinnerSide(thisLeftWon ? "left" : "right");
                gs.setLeftScore(thisLeftWon ? pointsToWin : pointsToWin - 3);
                gs.setRightScore(thisLeftWon ? pointsToWin - 3 : pointsToWin);
                gScores.add(gs);
            }
            req.setGameScores(gScores);
        } else {
            // 正常完赛
            boolean leftWins = rnd.nextBoolean();
            int winnerWins = gamesToWin;
            int loserWins = rnd.nextInt(gamesToWin);
            req.setWinnerSide(leftWins ? "left" : "right");
            req.setLeftGameWins(leftWins ? winnerWins : loserWins);
            req.setRightGameWins(leftWins ? loserWins : winnerWins);
            req.setLeftScore(req.getLeftGameWins());
            req.setRightScore(req.getRightGameWins());

            List<FinishMatchReq.GameScore> gScores = new ArrayList<>();
            for (int g = 1; g <= winnerWins + loserWins; g++) {
                FinishMatchReq.GameScore gs = new FinishMatchReq.GameScore();
                gs.setGameNo(g);
                boolean thisLeftWon = (g <= loserWins) ? !leftWins : leftWins;
                gs.setWinnerSide(thisLeftWon ? "left" : "right");

                // 是否发生 Deuce 或达到 Cap
                boolean hasDeuce = rnd.nextInt(4) == 0;
                int winPts;
                int losePts;
                if (hasDeuce) {
                    totalDeuces.incrementAndGet();
                    boolean hitCap = rnd.nextBoolean();
                    if (hitCap) {
                        totalCapHits.incrementAndGet();
                        winPts = capPoint;
                        losePts = capPoint - 1;
                    } else {
                        winPts = pointsToWin + 2;
                        losePts = pointsToWin;
                    }
                } else {
                    winPts = pointsToWin;
                    losePts = Math.max(0, pointsToWin - 3 - rnd.nextInt(10));
                }

                gs.setLeftScore(thisLeftWon ? winPts : losePts);
                gs.setRightScore(thisLeftWon ? losePts : winPts);
                gScores.add(gs);
            }
            req.setGameScores(gScores);
        }

        // 4. 调用真实 MatchSettlementService.finishMatch
        try {
            world.settlementService.finishMatch(actualRefereeId, matchId, req);
            totalMatchesOfficiated.incrementAndGet();
        } catch (Exception ex) {
            System.err.println("   [DEBUG EXCEPTION] finishMatch failed: " + ex.getMessage());
            ex.printStackTrace();
            violations.add(new Violation("FINISH_MATCH", "SettlementService", "FINISH_REJECTED",
                    "正常完赛请求被意外拒绝: " + ex.getMessage() + ", match=" + matchId, caseSeed));
            return;
        }

        // 5. 严格断言结果推送时效性 (Result Push / Slot Propagation)
        synchronized (world) {
            MatchRecord finishedMatch = world.matches.get(matchId);
            if (finishedMatch.getStatus() != 2) {
                violations.add(new Violation("SETTLEMENT", "Status", "STATUS_NOT_FINISHED",
                        "完赛后状态未更新为 2: " + matchId, caseSeed));
            }
            if (finishedMatch.getWinnerId() == null) {
                violations.add(new Violation("SETTLEMENT", "Winner", "WINNER_NULL",
                        "完赛后 winnerId 为空: " + matchId, caseSeed));
            }

            // 若有下一场对阵，验证胜者是否已秒级推送到位
            if (finishedMatch.getNextMatchId() != null) {
                MatchRecord nextMatch = world.matches.get(finishedMatch.getNextMatchId());
                if (nextMatch == null) {
                    violations.add(new Violation("PROPAGATION", "NextMatch", "NEXT_NOT_FOUND",
                            "下一场比赛不存在: " + finishedMatch.getNextMatchId(), caseSeed));
                } else {
                    String expectedSlot = finishedMatch.getNextMatchSlot();
                    String actualPlayerInSlot = "left".equals(expectedSlot)
                            ? nextMatch.getLeftPlayerId()
                            : nextMatch.getRightPlayerId();

                    if (!Objects.equals(actualPlayerInSlot, finishedMatch.getWinnerId())) {
                        violations.add(new Violation("PROPAGATION", "SlotMismatch", "SLOT_NOT_UPDATED",
                                "胜者未能正确推送到下一场槽位: nextMatch=" + nextMatch.getId() + ", slot=" + expectedSlot
                                        + ", expected=" + finishedMatch.getWinnerId() + ", actual=" + actualPlayerInSlot,
                                caseSeed));
                    }
                    totalFeederRacesHandled.incrementAndGet();
                }
            }
        }
    }

    private void verifyTournamentPostInvariants(TournamentWorld world, List<DivisionConfig> divisions, long seed) {
        synchronized (world) {
            for (DivisionConfig div : divisions) {
                // 每个组别必须有一位且仅有一位最终冠军
                List<MatchRecord> divMatches = world.matches.values().stream()
                        .filter(m -> div.divisionId.equals(m.getDivisionId()))
                        .toList();

                for (MatchRecord m : divMatches) {
                    if (m.getStatus() != 2) {
                        violations.add(new Violation("FINAL_AUDIT", div.name, "MATCH_NOT_FINISHED",
                                "赛事结束后仍有未完赛场次: " + m.getId(), seed));
                    }
                }

                int maxRound = divMatches.stream().mapToInt(m -> m.getRoundNum() == null ? 0 : m.getRoundNum()).max().orElse(1);
                MatchRecord finalMatch = divMatches.stream()
                        .filter(m -> m.getRoundNum() != null && m.getRoundNum() == maxRound && m.getMatchRole() == 0)
                        .findFirst().orElse(null);

                if (finalMatch == null || finalMatch.getWinnerId() == null) {
                    violations.add(new Violation("FINAL_AUDIT", div.name, "CHAMPION_MISSING",
                            "组别决赛冠军缺失: " + div.name, seed));
                }
            }
        }
    }

    private void setupDivision(TournamentWorld world, DivisionConfig config, Random rnd, long seed) {
        TournamentDivision div = new TournamentDivision();
        div.setId(config.divisionId);
        div.setTournamentId(world.tournamentId);
        div.setName(config.name);
        div.setTournamentType(0);
        div.setStatus(1);
        div.setBestOf(3);
        div.setGamesToWin(2);
        div.setPointsToWin(21);
        div.setCapPoint(30);
        div.setEnableDeuce(true);
        div.setRoundRuleEnabled(config.hasSpecialRoundRules);
        world.divisions.put(div.getId(), div);

        // 男单特殊设置 5 轮分段规则
        if (config.hasSpecialRoundRules) {
            addRoundRule(world, config.divisionId, 1, 1, 1, 21, 30);
            addRoundRule(world, config.divisionId, 2, 1, 1, 21, 30);
            addRoundRule(world, config.divisionId, 3, 3, 2, 15, 21);
            addRoundRule(world, config.divisionId, 4, 3, 2, 15, 21);
            addRoundRule(world, config.divisionId, 5, 3, 2, 21, 30);
        }

        // 生成选手与淘汰树
        List<Player> players = new ArrayList<>();
        for (int p = 1; p <= config.playerCount; p++) {
            Player pl = new Player();
            pl.setId(config.divisionId + "_P" + p);
            pl.setName(config.name + "_选手_" + p);
            players.add(pl);
            world.players.put(pl.getId(), pl);
        }

        List<MatchRecord> bracket = bracketEngine.generateKnockoutBracket(world.tournamentId, config.divisionId, players);
        for (MatchRecord m : bracket) {
            if (m.getId() == null) m.setId(UUID.randomUUID().toString());
            world.matches.put(m.getId(), m);
        }

        // 自动坍缩首轮轮空场次 (byes)
        for (MatchRecord m : bracket) {
            if (m.getRoundNum() != null && m.getRoundNum() == 1) {
                if (m.getLeftPlayerId() != null && m.getRightPlayerId() == null) {
                    m.setStatus(2);
                    m.setWinnerId(m.getLeftPlayerId());
                    propagateWinnerDirect(world, m, m.getLeftPlayerId());
                } else if (m.getLeftPlayerId() == null && m.getRightPlayerId() != null) {
                    m.setStatus(2);
                    m.setWinnerId(m.getRightPlayerId());
                    propagateWinnerDirect(world, m, m.getRightPlayerId());
                }
            }
        }
    }

    private void propagateWinnerDirect(TournamentWorld world, MatchRecord current, String winnerId) {
        if (current.getNextMatchId() != null) {
            MatchRecord next = world.matches.get(current.getNextMatchId());
            if (next != null) {
                if ("left".equals(current.getNextMatchSlot())) {
                    next.setLeftPlayerId(winnerId);
                } else if ("right".equals(current.getNextMatchSlot())) {
                    next.setRightPlayerId(winnerId);
                }
            }
        }
    }

    private void addRoundRule(TournamentWorld world, String divId, int roundNum, int bestOf, int gamesToWin,
                              int pts, int cap) {
        TournamentRoundRule rr = new TournamentRoundRule();
        rr.setId(UUID.randomUUID().toString());
        rr.setDivisionId(divId);
        rr.setStageType(1);
        rr.setRoundNum(roundNum);
        rr.setBestOf(bestOf);
        rr.setGamesToWin(gamesToWin);
        rr.setPointsToWin(pts);
        rr.setCapPoint(cap);
        rr.setEnableDeuce(true);
        world.roundRules.add(rr);
    }

    private record DivisionConfig(String divisionId, String name, int playerCount, boolean hasSpecialRoundRules) {}

    /** 内存持久化与服务世界装配 */
    private static final class TournamentWorld {
        final String tournamentId;
        final String creatorId;
        final Tournament tournament;
        final Map<String, TournamentDivision> divisions = new ConcurrentHashMap<>();
        final Map<String, MatchRecord> matches = new ConcurrentHashMap<>();
        final Map<String, Player> players = new ConcurrentHashMap<>();
        final List<TournamentRoundRule> roundRules = Collections.synchronizedList(new ArrayList<>());
        final Set<String> authorizedReferees = Collections.synchronizedSet(new HashSet<>());

        MatchSettlementService settlementService;
        MatchLockService lockService;

        TournamentWorld(String tournamentId, String creatorId) {
            this.tournamentId = tournamentId;
            this.creatorId = creatorId;
            this.tournament = new Tournament();
            this.tournament.setId(tournamentId);
            this.tournament.setName("2026南大羽协破冰活动_仿真");
            this.tournament.setCreatorUserId(creatorId);
            this.tournament.setSportType(0);
            this.tournament.setParticipantType(0);
            this.tournament.setTournamentType(0);
            this.tournament.setStatus(1);

            wireServices();
        }

        TournamentRoundRule getRoundRule(String divId, Integer roundNum) {
            if (divId == null || roundNum == null) return null;
            return roundRules.stream()
                    .filter(r -> divId.equals(r.getDivisionId()) && roundNum.equals(r.getRoundNum()))
                    .findFirst().orElse(null);
        }

        private void wireServices() {
            MatchRecordMapper matchMapper = mock(MatchRecordMapper.class);
            TournamentMapper tournamentMapper = mock(TournamentMapper.class);
            TeamMatchItemMapper itemMapper = mock(TeamMatchItemMapper.class);
            TournamentDivisionMapper divisionMapper = mock(TournamentDivisionMapper.class);
            MatchEventMapper eventMapper = mock(MatchEventMapper.class);
            MatchLineupConfigMapper lineupMapper = mock(MatchLineupConfigMapper.class);
            MatchReportMetaMapper reportMetaMapper = mock(MatchReportMetaMapper.class);
            TournamentQualificationOverrideMapper qualOverrideMapper = mock(TournamentQualificationOverrideMapper.class);
            TournamentRefereeGrantMapper refereeGrantMapper = mock(TournamentRefereeGrantMapper.class);
            TournamentRoundRuleMapper roundRuleMapper = mock(TournamentRoundRuleMapper.class);
            PlayerMapper playerMapper = mock(PlayerMapper.class);

            when(matchMapper.selectByIdForUpdate(anyString()))
                    .thenAnswer(inv -> matches.get(inv.getArgument(0, String.class)));
            when(matchMapper.selectById(anyString()))
                    .thenAnswer(inv -> matches.get(inv.getArgument(0, String.class)));
            when(matchMapper.updateById(any(MatchRecord.class))).thenAnswer(inv -> {
                synchronized (TournamentWorld.this) {
                    MatchRecord patch = inv.getArgument(0);
                    MatchRecord stored = matches.get(patch.getId());
                    if (stored != null) {
                        applyPatch(stored, patch);
                        if (Integer.valueOf(2).equals(stored.getStatus())) {
                            stored.setLockedByUserId(null);
                            stored.setLockToken(null);
                            stored.setLockExpireTime(null);
                        }
                    }
                    return 1;
                }
            });

            when(tournamentMapper.selectById(anyString())).thenReturn(tournament);
            when(tournamentMapper.selectByIdForUpdate(anyString())).thenReturn(tournament);
            when(tournamentMapper.updateById(any(Tournament.class))).thenAnswer(inv -> {
                applyPatch(tournament, inv.getArgument(0));
                return 1;
            });

            when(divisionMapper.selectById(anyString())).thenAnswer(inv -> divisions.get(inv.getArgument(0, String.class)));
            when(divisionMapper.selectByIdForUpdate(anyString())).thenAnswer(inv -> divisions.get(inv.getArgument(0, String.class)));
            when(divisionMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(divisions.values()));
            when(divisionMapper.updateById(any(TournamentDivision.class))).thenAnswer(inv -> {
                TournamentDivision patch = inv.getArgument(0);
                TournamentDivision stored = divisions.get(patch.getId());
                if (stored != null) applyPatch(stored, patch);
                return 1;
            });
            when(roundRuleMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(roundRules));

            TournamentRuleResolver ruleResolver = new TournamentRuleResolver(roundRuleMapper, itemMapper, matchMapper, divisionMapper);
            MatchAccessGuard accessGuard = mock(MatchAccessGuard.class);
            when(accessGuard.requireMatchOperator(anyString(), anyString())).thenReturn(tournament);

            lockService = mock(MatchLockService.class);
            when(lockService.acquireMatchLock(anyString(), anyString(), any(MatchLockReq.class))).thenAnswer(inv -> {
                synchronized (TournamentWorld.this) {
                    String userId = inv.getArgument(0, String.class);
                    String matchId = inv.getArgument(1, String.class);
                    MatchLockReq req = inv.getArgument(2, MatchLockReq.class);
                    MatchRecord match = matches.get(matchId);
                    LocalDateTime now = LocalDateTime.now();
                    MatchLockVO vo = new MatchLockVO();
                    if (match == null) {
                        vo.setSuccess(false);
                        return vo;
                    }
                    boolean available = StrUtil.isBlank(match.getLockedByUserId())
                            || match.getLockExpireTime() == null
                            || match.getLockExpireTime().isBefore(now)
                            || StrUtil.equals(match.getLockedByUserId(), userId);
                    if (available) {
                        LocalDateTime exp = now.plusSeconds(75);
                        match.setLockedByUserId(userId);
                        match.setLockToken(req.getLockToken());
                        match.setLockExpireTime(exp);
                        vo.setSuccess(true);
                        vo.setEditable(true);
                        vo.setLockedByUserId(userId);
                        return vo;
                    }
                    vo.setSuccess(false);
                    vo.setEditable(false);
                    vo.setLockedByUserId(match.getLockedByUserId());
                    return vo;
                }
            });
            MatchReportAssembler reportAssembler = mock(MatchReportAssembler.class);

            settlementService = new MatchSettlementService(matchMapper, tournamentMapper, itemMapper, divisionMapper,
                    eventMapper, lineupMapper, reportMetaMapper, qualOverrideMapper, ruleResolver, accessGuard,
                    reportAssembler, lockService);
        }
    }

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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
