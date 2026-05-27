package com.scoring.backend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;
import com.scoring.backend.engine.BracketEngine;
import com.scoring.backend.engine.RoundRobinEngine;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.service.TournamentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TournamentServiceImpl implements TournamentService {

    private static final int TYPE_KNOCKOUT = 0;
    private static final int TYPE_GROUP = 1;
    private static final int STAGE_GROUP = 0;
    private static final int STAGE_KNOCKOUT = 1;

    private static final int DEFAULT_BEST_OF = 3;
    private static final int DEFAULT_GAMES_TO_WIN = 2;
    private static final int DEFAULT_POINTS_TO_WIN = 21;
    private static final boolean DEFAULT_ENABLE_DEUCE = true;
    private static final int DEFAULT_CAP_POINT = 30;

    private final TournamentMapper tournamentMapper;
    private final PlayerMapper playerMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final BracketEngine bracketEngine;
    private final RoundRobinEngine roundRobinEngine;

    public TournamentServiceImpl(TournamentMapper tournamentMapper,
                                 PlayerMapper playerMapper,
                                 MatchRecordMapper matchRecordMapper,
                                 BracketEngine bracketEngine,
                                 RoundRobinEngine roundRobinEngine) {
        this.tournamentMapper = tournamentMapper;
        this.playerMapper = playerMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.bracketEngine = bracketEngine;
        this.roundRobinEngine = roundRobinEngine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createTournament(CreateTournamentReq req) {
        if (req == null || StrUtil.isBlank(req.getName())) {
            throw new IllegalArgumentException("赛事名称不能为空");
        }
        if (CollUtil.isEmpty(req.getPlayers())) {
            throw new IllegalArgumentException("选手列表不能为空");
        }

        List<CreateTournamentReq.PlayerEntry> entries = req.getPlayers().stream()
                .filter(p -> p != null && StrUtil.isNotBlank(p.getName()))
                .map(p -> {
                    CreateTournamentReq.PlayerEntry clean = new CreateTournamentReq.PlayerEntry();
                    clean.setName(p.getName().trim());
                    clean.setSeed(p.getSeed());
                    return clean;
                })
                .collect(Collectors.toList());

        if (entries.size() < 2) {
            throw new IllegalArgumentException("至少需要2名选手");
        }

        Tournament tournament = new Tournament();
        tournament.setName(req.getName().trim());
        tournament.setLocation(StrUtil.blankToDefault(req.getLocation(), null));
        tournament.setStatus(0);
        applyRule(tournament, req.getRule());
        applyTournamentType(tournament, req, entries.size());
        tournamentMapper.insert(tournament);

        List<Player> players = buildPlayers(tournament.getId(), entries);
        if (TYPE_GROUP == tournament.getTournamentType()) {
            int groupCount = tournament.getKnockoutSlots() / tournament.getQualifiersPerGroup();
            assignGroups(players, groupCount);
        }

        for (Player player : players) {
            playerMapper.insert(player);
        }

        List<MatchRecord> matches = TYPE_GROUP == tournament.getTournamentType()
                ? roundRobinEngine.generateGroupMatches(tournament.getId(), players)
                : bracketEngine.generateKnockoutBracket(tournament.getId(), players);

        for (MatchRecord matchRecord : matches) {
            matchRecordMapper.insert(matchRecord);
        }

        if (CollUtil.isNotEmpty(matches)) {
            Tournament update = new Tournament();
            update.setId(tournament.getId());
            update.setStatus(1);
            tournamentMapper.updateById(update);
        }

        return tournament.getId();
    }

    private List<Player> buildPlayers(String tournamentId, List<CreateTournamentReq.PlayerEntry> entries) {
        List<Player> players = new ArrayList<>();
        for (CreateTournamentReq.PlayerEntry entry : entries) {
            Player player = new Player();
            player.setTournamentId(tournamentId);
            player.setName(entry.getName());
            player.setSeedRank(entry.getSeed());
            players.add(player);
        }
        return players;
    }

    @Override
    public List<Tournament> listTournaments() {
        return tournamentMapper.selectList(
                new LambdaQueryWrapper<Tournament>()
                        .orderByDesc(Tournament::getCreateTime)
        );
    }

    @Override
    public TournamentBracketVO getBracket(String tournamentId) {
        if (StrUtil.isBlank(tournamentId)) {
            throw new IllegalArgumentException("tournamentId不能为空");
        }

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }

        List<Player> players = playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("create_time", "id")
        );

        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", STAGE_KNOCKOUT)
                        .orderByAsc("round_num", "match_index")
        );

        TournamentBracketVO vo = new TournamentBracketVO();
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setTournamentType(tournament.getTournamentType());
        vo.setGroupSize(tournament.getGroupSize());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setCurrentStage(tournament.getCurrentStage());
        vo.setKnockoutGenerated(tournament.getKnockoutGenerated());
        vo.setBestOf(tournament.getBestOf());
        vo.setGamesToWin(tournament.getGamesToWin());
        vo.setPointsToWin(tournament.getPointsToWin());
        vo.setEnableDeuce(tournament.getEnableDeuce());
        vo.setCapPoint(tournament.getCapPoint());
        vo.setPlayers(players);
        vo.setMatches(matches);
        return vo;
    }

    @Override
    public TournamentGroupsVO getGroups(String tournamentId) {
        if (StrUtil.isBlank(tournamentId)) {
            throw new IllegalArgumentException("tournamentId不能为空");
        }

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }

        List<Player> players = playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("group_no", "group_position", "create_time", "id")
        );
        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", STAGE_GROUP)
                        .orderByAsc("group_no", "round_num", "match_index")
        );

        Map<Integer, List<Player>> playersByGroup = players.stream()
                .filter(player -> player.getGroupNo() != null)
                .collect(Collectors.groupingBy(Player::getGroupNo));
        Map<Integer, List<MatchRecord>> matchesByGroup = matches.stream()
                .filter(match -> match.getGroupNo() != null)
                .collect(Collectors.groupingBy(MatchRecord::getGroupNo));

        List<TournamentGroupsVO.GroupVO> groups = playersByGroup.keySet().stream()
                .sorted()
                .map(groupNo -> {
                    TournamentGroupsVO.GroupVO group = new TournamentGroupsVO.GroupVO();
                    group.setGroupNo(groupNo);
                    group.setPlayers(playersByGroup.getOrDefault(groupNo, List.of()));
                    group.setMatches(matchesByGroup.getOrDefault(groupNo, List.of()));
                    return group;
                })
                .collect(Collectors.toList());

        TournamentGroupsVO vo = new TournamentGroupsVO();
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setTournamentType(tournament.getTournamentType());
        vo.setGroupSize(tournament.getGroupSize());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setCurrentStage(tournament.getCurrentStage());
        vo.setKnockoutGenerated(tournament.getKnockoutGenerated());
        vo.setBestOf(tournament.getBestOf());
        vo.setGamesToWin(tournament.getGamesToWin());
        vo.setPointsToWin(tournament.getPointsToWin());
        vo.setEnableDeuce(tournament.getEnableDeuce());
        vo.setCapPoint(tournament.getCapPoint());
        vo.setGroups(groups);
        return vo;
    }

    @Override
    public GroupStandingsVO getGroupStandings(String tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        List<Player> players = loadPlayers(tournamentId);
        List<MatchRecord> matches = loadGroupMatches(tournamentId);
        return buildStandingsVO(tournament, players, matches);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateKnockout(String tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (TYPE_GROUP != tournament.getTournamentType()) {
            throw new IllegalArgumentException("only group plus knockout tournaments can generate knockout");
        }
        if (Boolean.TRUE.equals(tournament.getKnockoutGenerated())) {
            throw new IllegalStateException("knockout bracket already generated");
        }

        List<Player> players = loadPlayers(tournamentId);
        List<MatchRecord> groupMatches = loadGroupMatches(tournamentId);
        GroupStandingsVO standingsVO = buildStandingsVO(tournament, players, groupMatches);
        if (!Boolean.TRUE.equals(standingsVO.getAllGroupMatchesFinished())) {
            throw new IllegalStateException("group matches are not finished");
        }
        if (Boolean.TRUE.equals(standingsVO.getHasUnresolvedTie())) {
            throw new IllegalStateException("group ranking has unresolved tie");
        }

        List<GroupRank> qualifiers = collectQualifiers(standingsVO);
        if (qualifiers.size() != tournament.getKnockoutSlots()) {
            throw new IllegalStateException("qualifier count does not match knockout slots");
        }

        List<String> slots = buildKnockoutSlots(qualifiers, tournament.getQualifiersPerGroup());
        List<MatchRecord> knockoutMatches = bracketEngine.generateKnockoutBracketBySlots(tournamentId, slots);
        for (MatchRecord match : knockoutMatches) {
            matchRecordMapper.insert(match);
        }

        Tournament update = new Tournament();
        update.setId(tournamentId);
        update.setCurrentStage(STAGE_KNOCKOUT);
        update.setKnockoutGenerated(true);
        update.setStatus(1);
        tournamentMapper.updateById(update);
    }

    private void applyRule(Tournament tournament, CreateTournamentReq.RuleConfig rule) {
        int bestOf = rule == null || rule.getBestOf() == null ? DEFAULT_BEST_OF : rule.getBestOf();
        int gamesToWin = rule == null || rule.getGamesToWin() == null ? DEFAULT_GAMES_TO_WIN : rule.getGamesToWin();
        int pointsToWin = rule == null || rule.getPointsToWin() == null ? DEFAULT_POINTS_TO_WIN : rule.getPointsToWin();
        boolean enableDeuce = rule == null || rule.getEnableDeuce() == null ? DEFAULT_ENABLE_DEUCE : rule.getEnableDeuce();
        int capPoint = rule == null || rule.getCapPoint() == null ? DEFAULT_CAP_POINT : rule.getCapPoint();

        if (bestOf != 1 && bestOf != 3 && bestOf != 5) {
            throw new IllegalArgumentException("bestOf must be 1, 3 or 5");
        }
        if (gamesToWin < 1 || gamesToWin > bestOf || gamesToWin != bestOf / 2 + 1) {
            throw new IllegalArgumentException("gamesToWin does not match bestOf");
        }
        if (pointsToWin < 1 || pointsToWin > 99) {
            throw new IllegalArgumentException("pointsToWin must be between 1 and 99");
        }
        if (capPoint <= pointsToWin || capPoint > 99) {
            throw new IllegalArgumentException("capPoint must be greater than pointsToWin and no more than 99");
        }

        tournament.setBestOf(bestOf);
        tournament.setGamesToWin(gamesToWin);
        tournament.setPointsToWin(pointsToWin);
        tournament.setEnableDeuce(enableDeuce);
        tournament.setCapPoint(capPoint);
    }

    private Tournament requireTournament(String tournamentId) {
        if (StrUtil.isBlank(tournamentId)) {
            throw new IllegalArgumentException("tournamentId涓嶈兘涓虹┖");
        }
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("璧涗簨涓嶅瓨鍦? " + tournamentId);
        }
        return tournament;
    }

    private List<Player> loadPlayers(String tournamentId) {
        return playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("group_no", "group_position", "create_time", "id")
        );
    }

    private List<MatchRecord> loadGroupMatches(String tournamentId) {
        return matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", STAGE_GROUP)
                        .orderByAsc("group_no", "round_num", "match_index")
        );
    }

    private GroupStandingsVO buildStandingsVO(Tournament tournament, List<Player> players, List<MatchRecord> matches) {
        Map<String, Player> playerMap = players.stream()
                .filter(player -> player.getId() != null)
                .collect(Collectors.toMap(Player::getId, player -> player));
        Map<Integer, List<Player>> playersByGroup = players.stream()
                .filter(player -> player.getGroupNo() != null)
                .collect(Collectors.groupingBy(Player::getGroupNo));
        Map<Integer, List<MatchRecord>> matchesByGroup = matches.stream()
                .filter(match -> match.getGroupNo() != null)
                .collect(Collectors.groupingBy(MatchRecord::getGroupNo));

        boolean allFinished = matches.stream().allMatch(match -> Integer.valueOf(2).equals(match.getStatus()));
        List<GroupStandingsVO.GroupVO> groups = new ArrayList<>();
        boolean hasUnresolvedTie = false;

        for (Integer groupNo : playersByGroup.keySet().stream().sorted().collect(Collectors.toList())) {
            List<Standing> standings = buildGroupStandings(
                    playersByGroup.getOrDefault(groupNo, List.of()),
                    matchesByGroup.getOrDefault(groupNo, List.of()),
                    playerMap,
                    tournament.getQualifiersPerGroup()
            );
            if (standings.stream().anyMatch(standing -> standing.tieUnresolved)) {
                hasUnresolvedTie = true;
            }

            GroupStandingsVO.GroupVO group = new GroupStandingsVO.GroupVO();
            group.setGroupNo(groupNo);
            group.setStandings(standings.stream().map(this::toStandingVO).collect(Collectors.toList()));
            groups.add(group);
        }

        GroupStandingsVO vo = new GroupStandingsVO();
        vo.setId(tournament.getId());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setAllGroupMatchesFinished(allFinished);
        vo.setHasUnresolvedTie(hasUnresolvedTie);
        vo.setGroups(groups);
        return vo;
    }

    private List<Standing> buildGroupStandings(List<Player> players,
                                               List<MatchRecord> matches,
                                               Map<String, Player> playerMap,
                                               Integer qualifiersPerGroup) {
        Map<String, Standing> standingMap = new HashMap<>();
        for (Player player : players) {
            Standing standing = new Standing();
            standing.playerId = player.getId();
            standing.playerName = player.getName();
            standing.seedRank = player.getSeedRank();
            standingMap.put(player.getId(), standing);
        }

        Map<String, String> h2hWinner = new HashMap<>();
        for (MatchRecord match : matches) {
            if (!Integer.valueOf(2).equals(match.getStatus()) || StrUtil.isBlank(match.getWinnerId())) {
                continue;
            }
            Standing left = standingMap.get(match.getLeftPlayerId());
            Standing right = standingMap.get(match.getRightPlayerId());
            if (left == null || right == null) {
                continue;
            }

            boolean leftWon = match.getWinnerId().equals(match.getLeftPlayerId());
            if (leftWon) {
                left.matchWins++;
                right.matchLosses++;
            } else {
                right.matchWins++;
                left.matchLosses++;
            }
            left.gameWins += safeInt(match.getLeftGameWins());
            left.gameLosses += safeInt(match.getRightGameWins());
            right.gameWins += safeInt(match.getRightGameWins());
            right.gameLosses += safeInt(match.getLeftGameWins());
            applyPointStats(match, left, right);
            h2hWinner.put(pairKey(match.getLeftPlayerId(), match.getRightPlayerId()), match.getWinnerId());
        }

        List<Standing> standings = new ArrayList<>(standingMap.values());
        standings.sort((a, b) -> compareStanding(a, b, h2hWinner));
        markRanksAndTies(standings, qualifiersPerGroup == null ? 0 : qualifiersPerGroup);
        return standings;
    }

    private void applyPointStats(MatchRecord match, Standing left, Standing right) {
        if (StrUtil.isBlank(match.getGameScores())) {
            return;
        }
        JSONArray scores = JSONUtil.parseArray(match.getGameScores());
        for (Object item : scores) {
            if (!(item instanceof JSONObject score)) {
                continue;
            }
            int leftScore = safeInt(score.getInt("leftScore"));
            int rightScore = safeInt(score.getInt("rightScore"));
            left.pointsFor += leftScore;
            left.pointsAgainst += rightScore;
            right.pointsFor += rightScore;
            right.pointsAgainst += leftScore;
        }
    }

    private int compareStanding(Standing a, Standing b, Map<String, String> h2hWinner) {
        int result = Integer.compare(b.matchWins, a.matchWins);
        if (result != 0) return result;
        result = Integer.compare(b.netGames(), a.netGames());
        if (result != 0) return result;
        result = Integer.compare(b.netPoints(), a.netPoints());
        if (result != 0) return result;

        String winner = h2hWinner.get(pairKey(a.playerId, b.playerId));
        if (winner != null) {
            if (winner.equals(a.playerId)) return -1;
            if (winner.equals(b.playerId)) return 1;
        }
        return a.playerName.compareTo(b.playerName);
    }

    private void markRanksAndTies(List<Standing> standings, int qualifiersPerGroup) {
        for (int i = 0; i < standings.size(); i++) {
            Standing standing = standings.get(i);
            standing.rank = i + 1;
            standing.qualified = i < qualifiersPerGroup;
        }

        Map<String, List<Standing>> tiedByStats = standings.stream()
                .collect(Collectors.groupingBy(standing -> standing.matchWins + ":" + standing.netGames() + ":" + standing.netPoints()));
        Set<String> unresolvedIds = new HashSet<>();
        for (List<Standing> tied : tiedByStats.values()) {
            if (tied.size() < 3) {
                continue;
            }
            boolean crossesLine = tied.stream().anyMatch(s -> s.rank <= qualifiersPerGroup)
                    && tied.stream().anyMatch(s -> s.rank > qualifiersPerGroup);
            if (crossesLine) {
                tied.forEach(s -> unresolvedIds.add(s.playerId));
            }
        }
        standings.forEach(standing -> standing.tieUnresolved = unresolvedIds.contains(standing.playerId));
    }

    private GroupStandingsVO.StandingVO toStandingVO(Standing standing) {
        GroupStandingsVO.StandingVO vo = new GroupStandingsVO.StandingVO();
        vo.setPlayerId(standing.playerId);
        vo.setPlayerName(standing.playerName);
        vo.setSeedRank(standing.seedRank);
        vo.setRank(standing.rank);
        vo.setQualified(standing.qualified);
        vo.setTieUnresolved(standing.tieUnresolved);
        vo.setMatchWins(standing.matchWins);
        vo.setMatchLosses(standing.matchLosses);
        vo.setGameWins(standing.gameWins);
        vo.setGameLosses(standing.gameLosses);
        vo.setNetGames(standing.netGames());
        vo.setPointsFor(standing.pointsFor);
        vo.setPointsAgainst(standing.pointsAgainst);
        vo.setNetPoints(standing.netPoints());
        return vo;
    }

    private List<GroupRank> collectQualifiers(GroupStandingsVO standingsVO) {
        List<GroupRank> qualifiers = new ArrayList<>();
        for (GroupStandingsVO.GroupVO group : standingsVO.getGroups()) {
            for (GroupStandingsVO.StandingVO standing : group.getStandings()) {
                if (Boolean.TRUE.equals(standing.getQualified()) && !Boolean.TRUE.equals(standing.getTieUnresolved())) {
                    qualifiers.add(new GroupRank(group.getGroupNo(), standing.getRank(), standing.getPlayerId()));
                }
            }
        }
        return qualifiers;
    }

    private List<String> buildKnockoutSlots(List<GroupRank> qualifiers, int qualifiersPerGroup) {
        Map<Integer, List<GroupRank>> byRank = qualifiers.stream()
                .collect(Collectors.groupingBy(GroupRank::rank));
        List<GroupRank> firsts = byRank.getOrDefault(1, List.of()).stream()
                .sorted(Comparator.comparingInt(GroupRank::groupNo))
                .collect(Collectors.toList());
        if (qualifiersPerGroup == 1) {
            return firsts.stream().map(GroupRank::playerId).collect(Collectors.toList());
        }

        List<GroupRank> seconds = new ArrayList<>(byRank.getOrDefault(2, List.of()).stream()
                .sorted(Comparator.comparingInt(GroupRank::groupNo).reversed())
                .collect(Collectors.toList()));
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < firsts.size(); i++) {
            GroupRank first = firsts.get(i);
            int secondIndex = findOpponentIndex(seconds, first.groupNo());
            if (secondIndex < 0) {
                throw new IllegalStateException("cannot avoid same group in first knockout round");
            }
            GroupRank second = seconds.remove(secondIndex);
            slots.add(first.playerId());
            slots.add(second.playerId());
        }
        return slots;
    }

    private int findOpponentIndex(List<GroupRank> seconds, Integer forbiddenGroupNo) {
        for (int i = 0; i < seconds.size(); i++) {
            if (!seconds.get(i).groupNo().equals(forbiddenGroupNo)) {
                return i;
            }
        }
        return -1;
    }

    private String pairKey(String a, String b) {
        if (a == null || b == null) {
            return "";
        }
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void applyTournamentType(Tournament tournament, CreateTournamentReq req, int playerCount) {
        int tournamentType = req.getTournamentType() == null ? TYPE_KNOCKOUT : req.getTournamentType();
        if (tournamentType != TYPE_KNOCKOUT && tournamentType != TYPE_GROUP) {
            throw new IllegalArgumentException("tournamentType must be 0 or 1");
        }

        tournament.setTournamentType(tournamentType);
        if (tournamentType == TYPE_KNOCKOUT) {
            tournament.setGroupSize(null);
            tournament.setKnockoutSlots(null);
            tournament.setQualifiersPerGroup(null);
            tournament.setCurrentStage(STAGE_KNOCKOUT);
            tournament.setKnockoutGenerated(true);
            return;
        }

        int knockoutSlots = req.getKnockoutSlots() == null ? 8 : req.getKnockoutSlots();
        int qualifiers = req.getQualifiersPerGroup() == null ? 2 : req.getQualifiersPerGroup();
        if (!isPowerOfTwo(knockoutSlots) || knockoutSlots < 2) {
            throw new IllegalArgumentException("knockoutSlots must be a power of two and at least 2");
        }
        if (knockoutSlots > playerCount) {
            throw new IllegalArgumentException("knockoutSlots must not exceed player count");
        }
        if (qualifiers != 1 && qualifiers != 2) {
            throw new IllegalArgumentException("qualifiersPerGroup must be 1 or 2");
        }
        if (knockoutSlots % qualifiers != 0) {
            throw new IllegalArgumentException("knockoutSlots must be divisible by qualifiersPerGroup");
        }
        int groupCount = knockoutSlots / qualifiers;
        int minGroupSize = playerCount / groupCount;
        if (groupCount < 1 || minGroupSize <= qualifiers) {
            throw new IllegalArgumentException("each group must have more players than qualifiers");
        }

        tournament.setGroupSize((int) Math.ceil(playerCount * 1.0 / groupCount));
        tournament.setKnockoutSlots(knockoutSlots);
        tournament.setQualifiersPerGroup(qualifiers);
        tournament.setCurrentStage(STAGE_GROUP);
        tournament.setKnockoutGenerated(false);
    }

    private boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private void assignGroups(List<Player> players, int groupCount) {
        List<Player> ordered = players.stream()
                .sorted((a, b) -> {
                    Integer seedA = a.getSeedRank();
                    Integer seedB = b.getSeedRank();
                    if (seedA == null && seedB == null) return a.getName().compareTo(b.getName());
                    if (seedA == null) return 1;
                    if (seedB == null) return -1;
                    return seedA.compareTo(seedB);
                })
                .collect(Collectors.toList());

        int[] groupPositions = new int[groupCount];
        for (int i = 0; i < ordered.size(); i++) {
            int block = i / groupCount;
            int offset = i % groupCount;
            int groupIndex = block % 2 == 0 ? offset : groupCount - 1 - offset;
            Player player = ordered.get(i);
            player.setGroupNo(groupIndex + 1);
            player.setGroupPosition(++groupPositions[groupIndex]);
        }
    }

    private static class Standing {
        private String playerId;
        private String playerName;
        private Integer seedRank;
        private int rank;
        private boolean qualified;
        private boolean tieUnresolved;
        private int matchWins;
        private int matchLosses;
        private int gameWins;
        private int gameLosses;
        private int pointsFor;
        private int pointsAgainst;

        private int netGames() {
            return gameWins - gameLosses;
        }

        private int netPoints() {
            return pointsFor - pointsAgainst;
        }
    }

    private record GroupRank(Integer groupNo, Integer rank, String playerId) {
    }
}
