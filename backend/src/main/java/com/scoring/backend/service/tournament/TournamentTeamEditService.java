package com.scoring.backend.service.tournament;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.UpdateTournamentTeamReq;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 创建者编辑队伍：修改队名、追加队员。
 * 有意不支持增删队伍与删除队员（需求约定收窄）。
 * 校验规则与创建时保持一致：排球号码必填且全队唯一、自由人必须带号码、全队恰好 1 名队长。
 */
@Service
public class TournamentTeamEditService {

    private static final int SPORT_VOLLEYBALL = 1;
    private static final int PARTICIPANT_TEAM = 1;

    private final TournamentMapper tournamentMapper;
    private final PlayerMapper playerMapper;
    private final TournamentTeamMemberMapper tournamentTeamMemberMapper;
    private final TournamentAccessGuard accessGuard;

    public TournamentTeamEditService(TournamentMapper tournamentMapper,
                                     PlayerMapper playerMapper,
                                     TournamentTeamMemberMapper tournamentTeamMemberMapper,
                                     TournamentAccessGuard accessGuard) {
        this.tournamentMapper = tournamentMapper;
        this.playerMapper = playerMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.accessGuard = accessGuard;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateTeam(String userId, String tournamentId, String participantId, UpdateTournamentTeamReq req) {
        Tournament tournament = tournamentMapper.selectByIdForUpdate(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        accessGuard.requireCreator(userId, tournament);
        accessGuard.requireNotArchived(tournament);
        if (!isTeamParticipant(tournament)) {
            throw new IllegalArgumentException("仅团体赛支持编辑队伍");
        }

        Player participant = playerMapper.selectOne(new QueryWrapper<Player>()
                .eq("id", participantId)
                .eq("tournament_id", tournamentId));
        if (participant == null) {
            throw new IllegalArgumentException("队伍不存在");
        }

        boolean volleyball = Integer.valueOf(SPORT_VOLLEYBALL).equals(tournament.getSportType());
        boolean renamed = applyRename(tournamentId, participant, req == null ? null : req.getName());
        boolean appended = applyAddMembers(tournamentId, participant, volleyball, req == null ? null : req.getAddMembers());
        if (!renamed && !appended) {
            throw new IllegalArgumentException("没有需要保存的修改");
        }
    }

    /** 与 TournamentServiceImpl.safeParticipantType 同语义：排球默认团体赛，其余按存储值/成员数据兜底 */
    private boolean isTeamParticipant(Tournament tournament) {
        if (Integer.valueOf(PARTICIPANT_TEAM).equals(tournament.getParticipantType())) {
            return true;
        }
        if (Integer.valueOf(SPORT_VOLLEYBALL).equals(tournament.getSportType())) {
            return true;
        }
        return tournamentTeamMemberMapper.selectCount(new QueryWrapper<TournamentTeamMember>()
                .eq("tournament_id", tournament.getId())) > 0;
    }

    private boolean applyRename(String tournamentId, Player participant, String rawName) {
        if (rawName == null) {
            return false;
        }
        String name = StrUtil.trim(rawName);
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("队名不能为空");
        }
        if (StrUtil.equals(name, participant.getName())) {
            return false;
        }
        Player update = new Player();
        update.setId(participant.getId());
        update.setName(name);
        playerMapper.updateById(update);
        return true;
    }

    private boolean applyAddMembers(String tournamentId, Player participant, boolean volleyball,
                                    List<UpdateTournamentTeamReq.TeamMemberEntry> rawMembers) {
        List<UpdateTournamentTeamReq.TeamMemberEntry> newMembers = normalizeMembers(rawMembers, volleyball);
        if (newMembers.isEmpty()) {
            return false;
        }

        List<TournamentTeamMember> existing = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>()
                        .eq("participant_id", participant.getId())
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("display_order", "id"));

        Set<Integer> jerseyNumbers = new HashSet<>();
        int captainCount = 0;
        for (TournamentTeamMember member : existing) {
            if (member.getJerseyNumber() != null && !jerseyNumbers.add(member.getJerseyNumber())) {
                throw new IllegalArgumentException(participant.getName() + " 存在重复球衣号码");
            }
            if (Boolean.TRUE.equals(member.getCaptain())) {
                captainCount++;
            }
        }

        for (UpdateTournamentTeamReq.TeamMemberEntry member : newMembers) {
            if (volleyball) {
                Integer jerseyNumber = member.getJerseyNumber();
                if (jerseyNumber == null || jerseyNumber <= 0) {
                    throw new IllegalArgumentException(participant.getName() + " 新队员需要有效球衣号码");
                }
                if (!jerseyNumbers.add(jerseyNumber)) {
                    throw new IllegalArgumentException(participant.getName() + " 球衣号码 " + jerseyNumber + " 已被使用");
                }
            }
            if (Boolean.TRUE.equals(member.getCaptain())) {
                captainCount++;
            }
        }
        if (captainCount != 1) {
            throw new IllegalArgumentException(participant.getName() + " 全队必须有且仅有1名队长");
        }

        int displayOrder = existing.size();
        for (UpdateTournamentTeamReq.TeamMemberEntry member : newMembers) {
            TournamentTeamMember entity = new TournamentTeamMember();
            entity.setTournamentId(tournamentId);
            entity.setParticipantId(participant.getId());
            entity.setName(member.getName());
            entity.setJerseyNumber(volleyball ? member.getJerseyNumber() : null);
            entity.setLibero(volleyball && Boolean.TRUE.equals(member.getLibero()));
            entity.setCaptain(Boolean.TRUE.equals(member.getCaptain()));
            entity.setDisplayOrder(++displayOrder);
            tournamentTeamMemberMapper.insert(entity);
        }
        return true;
    }

    private List<UpdateTournamentTeamReq.TeamMemberEntry> normalizeMembers(
            List<UpdateTournamentTeamReq.TeamMemberEntry> rawMembers, boolean volleyball) {
        if (rawMembers == null) {
            return List.of();
        }
        return rawMembers.stream()
                .filter(member -> member != null && StrUtil.isNotBlank(member.getName()))
                .map(member -> {
                    UpdateTournamentTeamReq.TeamMemberEntry clean = new UpdateTournamentTeamReq.TeamMemberEntry();
                    clean.setName(member.getName().trim());
                    clean.setJerseyNumber(volleyball ? member.getJerseyNumber() : null);
                    clean.setLibero(volleyball && Boolean.TRUE.equals(member.getLibero()));
                    clean.setCaptain(Boolean.TRUE.equals(member.getCaptain()));
                    return clean;
                })
                .toList();
    }
}
