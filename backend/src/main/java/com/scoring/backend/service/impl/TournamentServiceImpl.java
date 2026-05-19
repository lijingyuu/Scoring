package com.scoring.backend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.engine.BracketEngine;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.service.TournamentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TournamentServiceImpl implements TournamentService {

    private final TournamentMapper tournamentMapper;
    private final PlayerMapper playerMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final BracketEngine bracketEngine;

    public TournamentServiceImpl(TournamentMapper tournamentMapper,
                                 PlayerMapper playerMapper,
                                 MatchRecordMapper matchRecordMapper,
                                 BracketEngine bracketEngine) {
        this.tournamentMapper = tournamentMapper;
        this.playerMapper = playerMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.bracketEngine = bracketEngine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createTournament(CreateTournamentReq req) {
        if (req == null || StrUtil.isBlank(req.getName())) {
            throw new IllegalArgumentException("赛事名称不能为空");
        }
        if (CollUtil.isEmpty(req.getPlayerNames())) {
            throw new IllegalArgumentException("选手列表不能为空");
        }

        List<String> normalizedNames = req.getPlayerNames().stream()
                .map(name -> name == null ? "" : name.trim())
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());

        if (normalizedNames.size() < 2) {
            throw new IllegalArgumentException("至少需要2名选手");
        }

        Tournament tournament = new Tournament();
        tournament.setName(req.getName().trim());
        tournament.setLocation(StrUtil.blankToDefault(req.getLocation(), null));
        tournament.setStatus(0);
        tournamentMapper.insert(tournament);

        List<Player> players = new ArrayList<>();
        for (String name : normalizedNames) {
            Player player = new Player();
            player.setTournamentId(tournament.getId());
            player.setName(name);
            player.setSeedRank(null);
            players.add(player);
        }

        for (Player player : players) {
            playerMapper.insert(player);
        }

        List<MatchRecord> bracketMatches = bracketEngine.generateKnockoutBracket(tournament.getId(), players);

        for (MatchRecord matchRecord : bracketMatches) {
            matchRecordMapper.insert(matchRecord);
        }

        if (CollUtil.isNotEmpty(bracketMatches)) {
            Tournament update = new Tournament();
            update.setId(tournament.getId());
            update.setStatus(1);
            tournamentMapper.updateById(update);
        }

        return tournament.getId();
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
                        .orderByAsc("round_num", "id")
        );

        TournamentBracketVO vo = new TournamentBracketVO();
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setPlayers(players);
        vo.setMatches(matches);
        return vo;
    }
}
