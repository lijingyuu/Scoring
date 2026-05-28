package com.scoring.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.service.MatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchServiceImpl implements MatchService {

    private final MatchRecordMapper matchRecordMapper;
    private final TournamentMapper tournamentMapper;

    public MatchServiceImpl(MatchRecordMapper matchRecordMapper, TournamentMapper tournamentMapper) {
        this.matchRecordMapper = matchRecordMapper;
        this.tournamentMapper = tournamentMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMatchResult(String userId, String matchId, UpdateScoreReq req) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId不能为空");
        }
        if (req == null || StrUtil.isBlank(req.getWinnerId())) {
            throw new IllegalArgumentException("winnerId不能为空");
        }

        MatchRecord current = matchRecordMapper.selectById(matchId);
        if (current == null) {
            throw new IllegalArgumentException("比赛记录不存在: " + matchId);
        }

        Tournament tournament = requireCreatorTournament(userId, current.getTournamentId());

        MatchRecord updateCurrent = new MatchRecord();
        updateCurrent.setId(matchId);
        updateCurrent.setScoreDisplay(req.getScoreDisplay());
        updateCurrent.setWinnerId(req.getWinnerId());
        updateCurrent.setStatus(2);
        matchRecordMapper.updateById(updateCurrent);

        if (StrUtil.isBlank(current.getNextMatchId())) {
            if (Integer.valueOf(0).equals(current.getStageType())
                    && Integer.valueOf(1).equals(tournament.getTournamentType())) {
                return;
            }
            Tournament updateTournament = new Tournament();
            updateTournament.setId(current.getTournamentId());
            updateTournament.setStatus(2);
            tournamentMapper.updateById(updateTournament);
            return;
        }

        MatchRecord next = matchRecordMapper.selectById(current.getNextMatchId());
        if (next == null) {
            throw new IllegalStateException("下一场比赛不存在: " + current.getNextMatchId());
        }

        MatchRecord updateNext = new MatchRecord();
        updateNext.setId(next.getId());
        if ("left".equals(current.getNextMatchSlot())) {
            updateNext.setLeftPlayerId(req.getWinnerId());
        } else if ("right".equals(current.getNextMatchSlot())) {
            updateNext.setRightPlayerId(req.getWinnerId());
        } else {
            throw new IllegalStateException("nextMatchSlot非法: " + current.getNextMatchSlot());
        }

        matchRecordMapper.updateById(updateNext);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishMatch(String userId, String matchId, FinishMatchReq req) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId不能为空");
        }
        if (req == null || StrUtil.isBlank(req.getWinnerSide())) {
            throw new IllegalArgumentException("winnerSide不能为空");
        }

        MatchRecord current = matchRecordMapper.selectById(matchId);
        if (current == null) {
            throw new IllegalArgumentException("比赛记录不存在: " + matchId);
        }

        Tournament tournament = requireCreatorTournament(userId, current.getTournamentId());

        String winnerId;
        if ("left".equals(req.getWinnerSide())) {
            winnerId = current.getLeftPlayerId();
        } else if ("right".equals(req.getWinnerSide())) {
            winnerId = current.getRightPlayerId();
        } else {
            throw new IllegalArgumentException("winnerSide非法: " + req.getWinnerSide());
        }
        if (StrUtil.isBlank(winnerId)) {
            throw new IllegalStateException("获胜方选手ID为空，无法结算");
        }

        validateFinishReq(req, tournament);
        String scoreDisplay = buildScoreDisplay(req);

        MatchRecord updateCurrent = new MatchRecord();
        updateCurrent.setId(matchId);
        updateCurrent.setScoreDisplay(scoreDisplay);
        updateCurrent.setWinnerId(winnerId);
        updateCurrent.setLeftGameWins(req.getLeftGameWins());
        updateCurrent.setRightGameWins(req.getRightGameWins());
        if (req.getGameScores() != null) {
            updateCurrent.setGameScores(JSONUtil.toJsonStr(req.getGameScores()));
        }
        updateCurrent.setStatus(2);
        if (StrUtil.isNotBlank(req.getRetiredSide())) {
            updateCurrent.setRetiredSide(req.getRetiredSide());
        }
        matchRecordMapper.updateById(updateCurrent);

        if (StrUtil.isBlank(current.getNextMatchId())) {
            if (Integer.valueOf(0).equals(current.getStageType())
                    && Integer.valueOf(1).equals(tournament.getTournamentType())) {
                return;
            }
            Tournament updateTournament = new Tournament();
            updateTournament.setId(current.getTournamentId());
            updateTournament.setStatus(2);
            tournamentMapper.updateById(updateTournament);
            return;
        }

        MatchRecord next = matchRecordMapper.selectById(current.getNextMatchId());
        if (next == null) {
            throw new IllegalStateException("下一场比赛不存在: " + current.getNextMatchId());
        }

        MatchRecord updateNext = new MatchRecord();
        updateNext.setId(next.getId());
        if ("left".equals(current.getNextMatchSlot())) {
            updateNext.setLeftPlayerId(winnerId);
        } else if ("right".equals(current.getNextMatchSlot())) {
            updateNext.setRightPlayerId(winnerId);
        } else {
            throw new IllegalStateException("nextMatchSlot非法: " + current.getNextMatchSlot());
        }

        matchRecordMapper.updateById(updateNext);
    }

    private Tournament requireCreatorTournament(String userId, String tournamentId) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("只有创建者可以修改比赛");
        }
        return tournament;
    }

    private String buildScoreDisplay(FinishMatchReq req) {
        if (req.getGameScores() == null || req.getGameScores().isEmpty()) {
            return req.getLeftScore() + ":" + req.getRightScore();
        }

        return req.getGameScores().stream()
                .map(score -> score.getLeftScore() + ":" + score.getRightScore())
                .reduce((a, b) -> a + ", " + b)
                .orElse(req.getLeftScore() + ":" + req.getRightScore());
    }

    private void validateFinishReq(FinishMatchReq req, Tournament tournament) {
        int leftWins = req.getLeftGameWins() == null ? 0 : req.getLeftGameWins();
        int rightWins = req.getRightGameWins() == null ? 0 : req.getRightGameWins();
        if (leftWins < 0 || rightWins < 0) {
            throw new IllegalArgumentException("game wins cannot be negative");
        }

        int gamesToWin = tournament == null || tournament.getGamesToWin() == null
                ? Math.max(leftWins, rightWins)
                : tournament.getGamesToWin();
        if (gamesToWin <= 0) {
            throw new IllegalArgumentException("gamesToWin is invalid");
        }

        if ("left".equals(req.getWinnerSide())) {
            if (leftWins <= rightWins || leftWins != gamesToWin) {
                throw new IllegalArgumentException("left winner does not match game wins");
            }
        } else if ("right".equals(req.getWinnerSide())) {
            if (rightWins <= leftWins || rightWins != gamesToWin) {
                throw new IllegalArgumentException("right winner does not match game wins");
            }
        } else {
            throw new IllegalArgumentException("winnerSide must be left or right");
        }

        if (req.getGameScores() == null || req.getGameScores().isEmpty()) {
            if (StrUtil.isBlank(req.getRetiredSide())) {
                throw new IllegalArgumentException("gameScores cannot be empty");
            }
            return;
        }

        if (req.getGameScores().size() != leftWins + rightWins) {
            throw new IllegalArgumentException("gameScores size does not match game wins");
        }

        int countedLeftWins = 0;
        int countedRightWins = 0;
        for (FinishMatchReq.GameScore score : req.getGameScores()) {
            if (score == null || score.getLeftScore() == null || score.getRightScore() == null) {
                throw new IllegalArgumentException("game score cannot be empty");
            }
            if (score.getLeftScore() < 0 || score.getRightScore() < 0) {
                throw new IllegalArgumentException("game score cannot be negative");
            }
            if (score.getLeftScore().equals(score.getRightScore())) {
                throw new IllegalArgumentException("single game cannot end in a draw");
            }

            String expectedWinner = score.getLeftScore() > score.getRightScore() ? "left" : "right";
            if (!expectedWinner.equals(score.getWinnerSide())) {
                throw new IllegalArgumentException("game winner does not match score");
            }
            if ("left".equals(expectedWinner)) {
                countedLeftWins++;
            } else {
                countedRightWins++;
            }
        }

        if (countedLeftWins != leftWins || countedRightWins != rightWins) {
            throw new IllegalArgumentException("gameScores winners do not match game wins");
        }
    }
}
