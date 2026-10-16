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
 import com.scoring.backend.domain.entity.MatchRecord;
 import com.scoring.backend.mapper.MatchRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 创建者编辑队伍：修改队名、追加队员、修改已有队员姓名/号码。
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
     private final MatchRecordMapper matchRecordMapper;
     private final TournamentAccessGuard accessGuard;
 
     public TournamentTeamEditService(TournamentMapper tournamentMapper,
                                      PlayerMapper playerMapper,
                                      TournamentTeamMemberMapper tournamentTeamMemberMapper,
                                      MatchRecordMapper matchRecordMapper,
                                      TournamentAccessGuard accessGuard) {
         this.tournamentMapper = tournamentMapper;
         this.playerMapper = playerMapper;
         this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
         this.matchRecordMapper = matchRecordMapper;
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
         boolean teamParticipant = isTeamParticipant(tournament);
         if (!teamParticipant) {
             // P3-5 个人赛改名通道：仅未开赛（无记分/执裁锁/完赛痕迹）且纯改名时放行。
             // 签位/晋级链/战报全部按 playerId 外键联查（名字实时读取），改名即时生效且无结构风险；
             // 种子不支持后改——赛程与签表在创建时已按 seedRank 物化，后改只造成展示与实际不符。
             if (hasStartedAnyMatch(tournamentId) || Integer.valueOf(2).equals(tournament.getStatus())) {
                 throw new IllegalArgumentException("个人赛仅未开赛时支持修改选手姓名");
             }
             boolean nameOnly = req != null
                     && req.getName() != null
                     && (req.getAddMembers() == null || req.getAddMembers().isEmpty())
                     && (req.getUpdateMembers() == null || req.getUpdateMembers().isEmpty());
             if (!nameOnly) {
                 throw new IllegalArgumentException("个人赛仅支持修改选手姓名");
             }
         }
        // 完赛闸门：完赛后仅放行改队名（队名是战报表头实时联查的展示层）；增加队员与修改姓名/号码
        // 会漂移团体赛 roster 语义（羽毛球团体子场成员名单按 memberId 联查展示），一律拒绝。
        boolean memberEdit = req != null
                && ((req.getAddMembers() != null && !req.getAddMembers().isEmpty())
                    || (req.getUpdateMembers() != null && !req.getUpdateMembers().isEmpty()));
        if (Integer.valueOf(2).equals(tournament.getStatus()) && memberEdit) {
            throw new IllegalArgumentException("赛事已完赛，仅允许修改队名");
        }

        Player participant = playerMapper.selectOne(new QueryWrapper<Player>()
                .eq("id", participantId)
                .eq("tournament_id", tournamentId));
        if (participant == null) {
            throw new IllegalArgumentException(teamParticipant ? "队伍不存在" : "选手不存在");
        }

        boolean volleyball = Integer.valueOf(SPORT_VOLLEYBALL).equals(tournament.getSportType());
        boolean renamed = applyRename(tournamentId, participant, req == null ? null : req.getName(),
                teamParticipant ? "队名" : "选手姓名");
        boolean appended = applyAddMembers(tournamentId, participant, volleyball, req == null ? null : req.getAddMembers());
        boolean updated = applyUpdateMembers(participant, volleyball, req == null ? null : req.getUpdateMembers());
        if (!renamed && !appended && !updated) {
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
 
     /** 是否有任何开赛痕迹（已有记分/小局/退赛/执裁锁/完赛）。 */
     public boolean hasStartedAnyMatch(String tournamentId) {
         return matchRecordMapper.selectCount(new QueryWrapper<MatchRecord>()
                 .eq("tournament_id", tournamentId)
                 .and(w -> w.isNotNull("score_display")
                         .or().isNotNull("game_scores")
                         .or().isNotNull("left_game_wins")
                         .or().isNotNull("right_game_wins")
                         .or().isNotNull("retired_side")
                         .or().isNotNull("lock_token"))) > 0;
     }

    private boolean applyRename(String tournamentId, Player participant, String rawName, String nameLabel) {
        if (rawName == null) {
            return false;
        }
        String name = StrUtil.trim(rawName);
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException(nameLabel + "不能为空");
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

    /**
     * 修改已有队员的姓名/号码。号码修改先在内存合并后做全队唯一性校验，
     * 两条互换号码的修改也应放行；无实际变化的项不写库。
     */
    private boolean applyUpdateMembers(Player participant, boolean volleyball,
                                       List<UpdateTournamentTeamReq.MemberUpdateEntry> rawUpdates) {
        List<UpdateTournamentTeamReq.MemberUpdateEntry> updates = normalizeMemberUpdates(rawUpdates);
        if (updates.isEmpty()) {
            return false;
        }

        List<TournamentTeamMember> existing = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>()
                        .eq("participant_id", participant.getId())
                        .eq("tournament_id", participant.getTournamentId())
                        .orderByAsc("display_order", "id"));
        Map<String, TournamentTeamMember> byId = new HashMap<>();
        Map<String, Integer> effectiveNumbers = new HashMap<>();
        for (TournamentTeamMember member : existing) {
            byId.put(member.getId(), member);
            if (member.getJerseyNumber() != null) {
                effectiveNumbers.put(member.getId(), member.getJerseyNumber());
            }
        }

        for (UpdateTournamentTeamReq.MemberUpdateEntry update : updates) {
            if (!byId.containsKey(update.getMemberId())) {
                throw new IllegalArgumentException("队员不存在或已被删除");
            }
            String name = update.getName() == null ? null : StrUtil.trim(update.getName());
            if (name != null && StrUtil.isBlank(name)) {
                throw new IllegalArgumentException("队员姓名不能为空");
            }
            if (volleyball && update.getJerseyNumber() != null && update.getJerseyNumber() <= 0) {
                throw new IllegalArgumentException(participant.getName() + " 球衣号码必须为正整数");
            }
            if (volleyball && update.getJerseyNumber() != null) {
                effectiveNumbers.put(update.getMemberId(), update.getJerseyNumber());
            }
        }

        Set<Integer> seenNumbers = new HashSet<>();
        for (Integer number : effectiveNumbers.values()) {
            if (!seenNumbers.add(number)) {
                throw new IllegalArgumentException(participant.getName() + " 球衣号码 " + number + " 已被使用");
            }
        }

        boolean changed = false;
        for (UpdateTournamentTeamReq.MemberUpdateEntry update : updates) {
            TournamentTeamMember member = byId.get(update.getMemberId());
            TournamentTeamMember updateEntity = new TournamentTeamMember();
            boolean dirty = false;
            String name = update.getName() == null ? null : StrUtil.trim(update.getName());
            if (name != null && !StrUtil.equals(name, member.getName())) {
                updateEntity.setName(name);
                dirty = true;
            }
            if (volleyball && update.getJerseyNumber() != null
                    && !update.getJerseyNumber().equals(member.getJerseyNumber())) {
                updateEntity.setJerseyNumber(update.getJerseyNumber());
                dirty = true;
            }
            if (dirty) {
                updateEntity.setId(member.getId());
                tournamentTeamMemberMapper.updateById(updateEntity);
                changed = true;
            }
        }
        return changed;
    }

    private List<UpdateTournamentTeamReq.MemberUpdateEntry> normalizeMemberUpdates(
            List<UpdateTournamentTeamReq.MemberUpdateEntry> rawUpdates) {
        if (rawUpdates == null) {
            return List.of();
        }
        Map<String, UpdateTournamentTeamReq.MemberUpdateEntry> byMemberId = new LinkedHashMap<>();
        for (UpdateTournamentTeamReq.MemberUpdateEntry update : rawUpdates) {
            if (update == null || StrUtil.isBlank(update.getMemberId())) {
                throw new IllegalArgumentException("队员修改项缺少队员标识");
            }
            String memberId = update.getMemberId().trim();
            if (byMemberId.putIfAbsent(memberId, update) != null) {
                throw new IllegalArgumentException("同一队员的修改项重复");
            }
            update.setMemberId(memberId);
        }
        return List.copyOf(byMemberId.values());
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
