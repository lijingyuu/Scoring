package com.scoring.backend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.SaveTeamMatchLineupReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.MatchReportMeta;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.vo.MatchRuleConfig;
import com.scoring.backend.domain.vo.TeamMatchChildMatchVO;
import com.scoring.backend.domain.vo.TeamMatchLineupVO;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.security.ForbiddenException;
import com.scoring.backend.service.TeamMatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TeamMatchServiceImpl implements TeamMatchService {
    private static final int SPORT_BADMINTON = 0;
    private static final int PARTICIPANT_TEAM = 1;
    private static final int TEMPLATE_SUDIRMAN_5 = 1;
    private static final int TEMPLATE_RELAY = 2;
    private static final int STAGE_TEAM_CHILD = 2;

    private static final List<TemplateItem> SUDIRMAN_ITEMS = List.of(
            new TemplateItem(1, "MS", "\u7537\u5355", 1),
            new TemplateItem(2, "WS", "\u5973\u5355", 1),
            new TemplateItem(3, "MD", "\u7537\u53cc", 2),
            new TemplateItem(4, "WD", "\u5973\u53cc", 2),
            new TemplateItem(5, "XD", "\u6df7\u53cc", 2)
    );

    private final MatchRecordMapper matchRecordMapper;
    private final MatchReportMetaMapper matchReportMetaMapper;
    private final TournamentMapper tournamentMapper;
    private final PlayerMapper playerMapper;
    private final TournamentTeamMemberMapper tournamentTeamMemberMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;
    private final TournamentRuleResolver tournamentRuleResolver;

    public TeamMatchServiceImpl(MatchRecordMapper matchRecordMapper,
                                MatchReportMetaMapper matchReportMetaMapper,
                                TournamentMapper tournamentMapper,
                                PlayerMapper playerMapper,
                                TournamentTeamMemberMapper tournamentTeamMemberMapper,
                                TeamMatchItemMapper teamMatchItemMapper,
                                TournamentRefereeGrantMapper tournamentRefereeGrantMapper,
                                TournamentRuleResolver tournamentRuleResolver) {
        this.matchRecordMapper = matchRecordMapper;
        this.matchReportMetaMapper = matchReportMetaMapper;
        this.tournamentMapper = tournamentMapper;
        this.playerMapper = playerMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.tournamentRefereeGrantMapper = tournamentRefereeGrantMapper;
        this.tournamentRuleResolver = tournamentRuleResolver;
    }

    @Override
    public TeamMatchLineupVO getLineup(String currentUserId, String matchId) {
        MatchRecord match = requireMatch(matchId);
        Tournament tournament = requireReadableTournament(currentUserId, match);
        requireBadmintonTeamTournament(tournament);
        MatchContext context = loadContext(match, tournament);
        return buildVO(context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamMatchLineupVO saveLineup(String userId, String matchId, SaveTeamMatchLineupReq req) {
        return saveLineupInternal(userId, matchId, req, null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamMatchLineupVO saveLineup(String userId, String matchId, SaveTeamMatchLineupReq req, String lockToken) {
        return saveLineupInternal(userId, matchId, req, lockToken, true);
    }

    private TeamMatchLineupVO saveLineupInternal(String userId, String matchId, SaveTeamMatchLineupReq req, String lockToken, boolean requireLock) {
        MatchRecord match = requireMatchForUpdate(matchId);
        Tournament tournament = requireOperator(userId, match);
        if (requireLock) {
            requireActiveMatchLock(match, userId, lockToken);
        }
        requireBadmintonTeamTournament(tournament);
        ensureParentMatchEditable(match);
        MatchContext context = loadContext(match, tournament);
        List<TeamMatchItem> items = normalizeItems(req, context);

        upsertLineupItems(context.items(), items);
        return buildVO(loadContext(match, tournament));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamMatchChildMatchVO startChildMatch(String userId, String matchId, String itemCode) {
        return startChildMatchInternal(userId, matchId, itemCode, null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamMatchChildMatchVO startChildMatch(String userId, String matchId, String itemCode, String lockToken) {
        return startChildMatchInternal(userId, matchId, itemCode, lockToken, true);
    }

    private TeamMatchChildMatchVO startChildMatchInternal(String userId, String matchId, String itemCode, String lockToken, boolean requireLock) {
        MatchRecord parentMatch = requireMatchForUpdate(matchId);
        Tournament tournament = requireOperator(userId, parentMatch);
        if (requireLock) {
            requireActiveMatchLock(parentMatch, userId, lockToken);
        }
        requireBadmintonTeamTournament(tournament);
        ensureParentMatchEditable(parentMatch);
        if (templateOf(tournament) != TEMPLATE_SUDIRMAN_5) {
            throw new IllegalArgumentException("\u63a5\u529b\u8ffd\u5206\u8d5b\u4e0d\u4f7f\u7528\u5b50\u6bd4\u8d5b");
        }
        MatchContext context = loadContext(parentMatch, tournament);
        TemplateItem template = requireTemplateItem(itemCode);
        TeamMatchItem item = context.items().stream()
                .filter(saved -> template.code().equals(saved.getItemCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("\u8bf7\u5148\u4fdd\u5b58\u56e2\u4f53\u8d5b\u5e03\u9635"));
        ensureLineupItemComplete(item, template);

        MatchRecord childMatch = StrUtil.isBlank(item.getChildMatchId()) ? null : matchRecordMapper.selectById(item.getChildMatchId());
        if (childMatch == null) {
            childMatch = createChildMatch(context, item);
        }
        return buildChildMatchVO(context, item, childMatch);
    }

    private MatchRecord createChildMatch(MatchContext context, TeamMatchItem item) {
        MatchRecord child = new MatchRecord();
        child.setTournamentId(context.match().getTournamentId());
        child.setRoundNum(0);
        child.setMatchIndex(0);
        child.setStageType(STAGE_TEAM_CHILD);
        child.setGroupNo(null);
        child.setLeftPlayerId(context.leftTeam().getId());
        child.setRightPlayerId(context.rightTeam().getId());
        child.setStatus(0);
        matchRecordMapper.insert(child);

        TeamMatchItem update = new TeamMatchItem();
        update.setId(item.getId());
        update.setChildMatchId(child.getId());
        update.setStatus(1);
        teamMatchItemMapper.updateById(update);
        item.setChildMatchId(child.getId());
        item.setStatus(1);
        return child;
    }

    private TeamMatchChildMatchVO buildChildMatchVO(MatchContext context, TeamMatchItem item, MatchRecord childMatch) {
        TeamMatchChildMatchVO vo = new TeamMatchChildMatchVO();
        vo.setParentMatchId(context.match().getId());
        vo.setChildMatchId(childMatch.getId());
        vo.setItemCode(item.getItemCode());
        vo.setItemName(item.getItemName());
        vo.setLeftName(memberNames(parseIds(item.getLeftMemberIdsJson()), context.leftMemberMap()));
        vo.setRightName(memberNames(parseIds(item.getRightMemberIdsJson()), context.rightMemberMap()));
        MatchRuleConfig rule = tournamentRuleResolver.resolveForMatch(context.tournament(), childMatch);
        vo.setBestOf(rule.getBestOf());
        vo.setGamesToWin(rule.getGamesToWin());
        vo.setPointsToWin(rule.getPointsToWin());
        vo.setDecidingPointsToWin(rule.getDecidingPointsToWin());
        vo.setEnableDeuce(rule.getEnableDeuce());
        vo.setCapPoint(rule.getCapPoint());
        return vo;
    }

    private String memberNames(List<String> ids, Map<String, TournamentTeamMember> memberMap) {
        return ids.stream()
                .map(memberMap::get)
                .filter(member -> member != null)
                .map(TournamentTeamMember::getName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("/"));
    }

    private void ensureLineupItemComplete(TeamMatchItem item, TemplateItem template) {
        if (parseIds(item.getLeftMemberIdsJson()).size() != template.playerCount()
                || parseIds(item.getRightMemberIdsJson()).size() != template.playerCount()) {
            throw new IllegalArgumentException(template.name() + " \u5e03\u9635\u672a\u5b8c\u6210");
        }
    }

    private TemplateItem requireTemplateItem(String itemCode) {
        String code = StrUtil.trimToEmpty(itemCode);
        return SUDIRMAN_ITEMS.stream()
                .filter(item -> item.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("\u672a\u77e5\u7684\u56e2\u4f53\u8d5b\u9879\u76ee: " + code));
    }

    private void upsertLineupItems(List<TeamMatchItem> existingItems, List<TeamMatchItem> items) {
        Map<String, TeamMatchItem> existingByCode = existingItems.stream()
                .collect(Collectors.toMap(TeamMatchItem::getItemCode, item -> item, (a, b) -> b));
        Set<String> incomingCodes = items.stream()
                .map(TeamMatchItem::getItemCode)
                .collect(Collectors.toSet());
        for (TeamMatchItem item : items) {
            TeamMatchItem existing = existingByCode.get(item.getItemCode());
            if (existing == null) {
                teamMatchItemMapper.insert(item);
                continue;
            }
            item.setId(existing.getId());
            item.setChildMatchId(existing.getChildMatchId());
            item.setWinnerSide(existing.getWinnerSide());
            item.setStatus(existing.getStatus());
            teamMatchItemMapper.updateById(item);
        }
        for (TeamMatchItem existing : existingItems) {
            if (!incomingCodes.contains(existing.getItemCode())) {
                teamMatchItemMapper.deleteById(existing.getId());
            }
        }
    }

    private List<TeamMatchItem> normalizeItems(SaveTeamMatchLineupReq req, MatchContext context) {
        if (req == null || CollUtil.isEmpty(req.getItems())) {
            throw new IllegalArgumentException("\u56e2\u4f53\u8d5b\u5e03\u9635\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (isRelay(context.tournament())) {
            return normalizeRelayItems(req, context);
        }
        Map<String, SaveTeamMatchLineupReq.ItemLineup> byCode = new LinkedHashMap<>();
        for (SaveTeamMatchLineupReq.ItemLineup item : req.getItems()) {
            if (item == null || StrUtil.isBlank(item.getItemCode())) {
                continue;
            }
            String code = item.getItemCode().trim();
            if (byCode.containsKey(code)) {
                throw new IllegalArgumentException("\u91cd\u590d\u7684\u56e2\u4f53\u8d5b\u9879\u76ee: " + code);
            }
            byCode.put(code, item);
        }
        List<TeamMatchItem> result = new ArrayList<>();
        List<TemplateItem> templates = templateItems(context);
        for (TemplateItem template : templates) {
            SaveTeamMatchLineupReq.ItemLineup item = byCode.get(template.code());
            if (item == null) {
                throw new IllegalArgumentException(template.name() + " \u5e03\u9635\u7f3a\u5931");
            }
            List<String> leftIds = normalizeMemberIds(item.getLeftMemberIds(), context.leftMemberMap(), template, "\u5de6\u961f");
            List<String> rightIds = normalizeMemberIds(item.getRightMemberIds(), context.rightMemberMap(), template, "\u53f3\u961f");

            TeamMatchItem entity = new TeamMatchItem();
            entity.setMatchId(context.match().getId());
            entity.setTournamentId(context.match().getTournamentId());
            entity.setDisplayOrder(template.displayOrder());
            entity.setItemCode(template.code());
            entity.setItemName(template.name());
            entity.setPlayerCount(template.playerCount());
            entity.setLeftMemberIdsJson(JSONUtil.toJsonStr(leftIds));
            entity.setRightMemberIdsJson(JSONUtil.toJsonStr(rightIds));
            entity.setStatus(0);
            result.add(entity);
        }
        return result;
    }

    private List<TeamMatchItem> normalizeRelayItems(SaveTeamMatchLineupReq req, MatchContext context) {
        Map<String, SaveTeamMatchLineupReq.ItemLineup> byCode = new LinkedHashMap<>();
        for (SaveTeamMatchLineupReq.ItemLineup item : req.getItems()) {
            if (item == null || StrUtil.isBlank(item.getItemCode())) {
                continue;
            }
            String code = item.getItemCode().trim();
            if (!code.matches("R\\d+")) {
                throw new IllegalArgumentException("\u63a5\u529b\u8d5b\u5206\u6bb5\u7f16\u7801\u5fc5\u987b\u4e3a R1..RN");
            }
            if (byCode.containsKey(code)) {
                throw new IllegalArgumentException("\u91cd\u590d\u7684\u63a5\u529b\u8d5b\u5206\u6bb5: " + code);
            }
            byCode.put(code, item);
        }
        int relayMemberCount = relayMemberCount(context.tournament());
        if (byCode.size() != relayMemberCount) {
            throw new IllegalArgumentException("\u63a5\u529b\u8d5b\u51fa\u573a\u4eba\u6570\u5fc5\u987b\u7b49\u4e8e\u8d5b\u4e8b\u56fa\u5b9a\u8f6e\u8f6c\u4eba\u6570: " + relayMemberCount);
        }

        List<TeamMatchItem> result = new ArrayList<>();
        for (int i = 1; i <= relayMemberCount; i++) {
            String code = "R" + i;
            SaveTeamMatchLineupReq.ItemLineup item = byCode.get(code);
            if (item == null) {
                throw new IllegalArgumentException("\u63a5\u529b\u8d5b\u5206\u6bb5\u5fc5\u987b\u4ece R1 \u8fde\u7eed\u5230 RN");
            }
            TemplateItem template = new TemplateItem(i, code, "\u7b2c " + i + " \u6bb5", 2);
            List<String> leftIds = normalizeMemberIds(item.getLeftMemberIds(), context.leftMemberMap(), template, "\u5de6\u961f");
            List<String> rightIds = normalizeMemberIds(item.getRightMemberIds(), context.rightMemberMap(), template, "\u53f3\u961f");

            TeamMatchItem entity = new TeamMatchItem();
            entity.setMatchId(context.match().getId());
            entity.setTournamentId(context.match().getTournamentId());
            entity.setDisplayOrder(i);
            entity.setItemCode(code);
            entity.setItemName(template.name());
            entity.setPlayerCount(2);
            entity.setLeftMemberIdsJson(JSONUtil.toJsonStr(leftIds));
            entity.setRightMemberIdsJson(JSONUtil.toJsonStr(rightIds));
            entity.setStatus(0);
            result.add(entity);
        }
        validateRelayLineup(result, context);
        return result;
    }

    private void validateRelayLineup(List<TeamMatchItem> items, MatchContext context) {
        validateRelaySide(items, context.leftMemberMap(), "\u5de6\u961f", TeamMatchItem::getLeftMemberIdsJson, context);
        validateRelaySide(items, context.rightMemberMap(), "\u53f3\u961f", TeamMatchItem::getRightMemberIdsJson, context);
    }

    private void validateRelaySide(List<TeamMatchItem> items,
                                   Map<String, TournamentTeamMember> memberMap,
                                   String sideLabel,
                                   Function<TeamMatchItem, String> idsGetter,
                                   MatchContext context) {
        int relayMemberCount = relayMemberCount(context.tournament());
        if (items.size() != relayMemberCount) {
            throw new IllegalArgumentException(sideLabel + " \u63a5\u529b\u5206\u6bb5\u6570\u5fc5\u987b\u7b49\u4e8e" + relayMemberCount);
        }
        List<List<String>> pairs = items.stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder() == null ? 0 : a.getDisplayOrder(), b.getDisplayOrder() == null ? 0 : b.getDisplayOrder()))
                .map(item -> parseIds(idsGetter.apply(item)))
                .toList();
        Set<String> firstMembers = new HashSet<>();
        for (int i = 0; i < pairs.size(); i++) {
            List<String> current = pairs.get(i);
            List<String> next = pairs.get((i + 1) % pairs.size());
            if (current.size() != 2) {
                throw new IllegalArgumentException(sideLabel + " \u6bcf\u4e2a\u63a5\u529b\u5206\u6bb5\u5fc5\u987b\u662f\u53cc\u6253");
            }
            if (!firstMembers.add(current.get(0))) {
                throw new IllegalArgumentException(sideLabel + " \u961f\u5458\u987a\u5e8f\u4e0d\u80fd\u91cd\u590d");
            }
            if (!current.get(1).equals(next.get(0))) {
                throw new IllegalArgumentException(sideLabel + " \u63a5\u529b\u987a\u5e8f\u5fc5\u987b\u6309\u76f8\u90bb\u961f\u5458\u6d41\u8f6c");
            }
        }
    }

    private List<String> normalizeMemberIds(List<String> rawIds, Map<String, TournamentTeamMember> memberMap, TemplateItem template, String sideLabel) {
        List<String> ids = rawIds == null ? List.of() : rawIds.stream()
                .map(StrUtil::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
        if (ids.size() != template.playerCount()) {
            throw new IllegalArgumentException(sideLabel + " " + template.name() + " \u9700\u8981 " + template.playerCount() + " \u4eba");
        }
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (!seen.add(id)) {
                throw new IllegalArgumentException(sideLabel + " " + template.name() + " \u4e0d\u80fd\u91cd\u590d\u9009\u4eba");
            }
            if (!memberMap.containsKey(id)) {
                throw new IllegalArgumentException(sideLabel + " " + template.name() + " \u4e0d\u5c5e\u4e8e\u672c\u961f");
            }
        }
        return ids;
    }

    private TeamMatchLineupVO buildVO(MatchContext context) {
        TeamMatchLineupVO vo = new TeamMatchLineupVO();
        vo.setMatchId(context.match().getId());
        vo.setTournamentId(context.match().getTournamentId());
        vo.setTeamMatchTemplate(context.tournament().getTeamMatchTemplate());
        vo.setMatchStatus(context.match().getStatus());
        vo.setStageType(context.match().getStageType());
        vo.setTournamentType(context.tournament().getTournamentType());
        List<TemplateItem> templates = templateItems(context);
        if (isRelay(context.tournament())) {
            int baseScore = relayBaseScore(context);
            int segmentCount = relayMemberCount(context.tournament());
            vo.setRelayBaseScore(baseScore);
            vo.setRelayMemberCount(segmentCount);
            vo.setRelayTargetScore(baseScore * segmentCount);
        }
        vo.setScoreDisplay(context.match().getScoreDisplay());
        vo.setWinnerSide(resolveWinnerSide(context.match()));
        vo.setLeftTeam(toTeamVO(context.leftTeam(), context.leftMembers()));
        vo.setRightTeam(toTeamVO(context.rightTeam(), context.rightMembers()));
        vo.setReportSignatures(buildReportSignatures(context.match().getId()));
        vo.setReportState(buildReportState(context.match().getId()));

        Map<String, TeamMatchItem> savedByCode = context.items().stream()
                .collect(Collectors.toMap(TeamMatchItem::getItemCode, item -> item, (a, b) -> b));
        Map<String, MatchRecord> childById = loadChildMatches(context.items());
        List<TeamMatchLineupVO.ItemVO> itemVOs = new ArrayList<>();
        for (TemplateItem template : templates) {
            TeamMatchItem saved = savedByCode.get(template.code());
            TeamMatchLineupVO.ItemVO item = new TeamMatchLineupVO.ItemVO();
            item.setId(saved == null ? null : saved.getId());
            item.setDisplayOrder(template.displayOrder());
            item.setItemCode(template.code());
            item.setItemName(saved != null && StrUtil.isNotBlank(saved.getItemName()) ? saved.getItemName() : template.name());
            item.setPlayerCount(template.playerCount());
            item.setLeftMembers(toMemberVOs(parseIds(saved == null ? null : saved.getLeftMemberIdsJson()), context.leftMemberMap()));
            item.setRightMembers(toMemberVOs(parseIds(saved == null ? null : saved.getRightMemberIdsJson()), context.rightMemberMap()));
            item.setStatus(saved == null ? 0 : saved.getStatus());
            item.setChildMatchId(saved == null ? null : saved.getChildMatchId());
            item.setWinnerSide(saved == null ? null : saved.getWinnerSide());
            MatchRecord child = saved == null || StrUtil.isBlank(saved.getChildMatchId()) ? null : childById.get(saved.getChildMatchId());
            if (child != null) {
                item.setChildScoreDisplay(child.getScoreDisplay());
                item.setChildLeftGameWins(child.getLeftGameWins());
                item.setChildRightGameWins(child.getRightGameWins());
            }
            itemVOs.add(item);
        }
        vo.setItems(itemVOs);
        return vo;
    }

    private TeamMatchLineupVO.ReportSignaturesVO buildReportSignatures(String matchId) {
        MatchReportMeta entity = matchReportMetaMapper.selectOne(
                new QueryWrapper<MatchReportMeta>().eq("match_id", matchId)
        );
        JSONObject root = parseObject(entity == null ? null : entity.getMetaJson());
        JSONObject legacy = root.getJSONObject("teamRecordSignatures");
        JSONObject object = root.getJSONObject("reportSignatures");
        TeamMatchLineupVO.ReportSignaturesVO vo = new TeamMatchLineupVO.ReportSignaturesVO();
        String leftParticipant = StrUtil.trimToEmpty(object == null ? null : object.getStr("leftParticipant"));
        String rightParticipant = StrUtil.trimToEmpty(object == null ? null : object.getStr("rightParticipant"));
        String referee = StrUtil.trimToEmpty(object == null ? null : object.getStr("referee"));
        String matchDateText = StrUtil.trimToEmpty(object == null ? null : object.getStr("matchDateText"));
        if (StrUtil.isBlank(leftParticipant)) leftParticipant = StrUtil.trimToEmpty(legacy == null ? null : legacy.getStr("leftCaptain"));
        if (StrUtil.isBlank(rightParticipant)) rightParticipant = StrUtil.trimToEmpty(legacy == null ? null : legacy.getStr("rightCaptain"));
        if (StrUtil.isBlank(referee)) referee = StrUtil.trimToEmpty(legacy == null ? null : legacy.getStr("referee"));
        if (StrUtil.isBlank(matchDateText)) matchDateText = StrUtil.trimToEmpty(legacy == null ? null : legacy.getStr("matchDateText"));
        vo.setLeftParticipant(leftParticipant);
        vo.setRightParticipant(rightParticipant);
        vo.setLeftCaptain(leftParticipant);
        vo.setRightCaptain(rightParticipant);
        vo.setReferee(referee);
        vo.setMatchDateText(matchDateText);
        return vo;
    }

    private TeamMatchLineupVO.ReportStateVO buildReportState(String matchId) {
        MatchReportMeta entity = matchReportMetaMapper.selectOne(
                new QueryWrapper<MatchReportMeta>().eq("match_id", matchId)
        );
        JSONObject object = parseObject(entity == null ? null : entity.getMetaJson()).getJSONObject("reportState");
        TeamMatchLineupVO.ReportStateVO vo = new TeamMatchLineupVO.ReportStateVO();
        vo.setStatus(StrUtil.blankToDefault(StrUtil.trim(object == null ? null : object.getStr("status")), "draft"));
        vo.setSealedAt(StrUtil.trimToEmpty(object == null ? null : object.getStr("sealedAt")));
        vo.setSealedBy(StrUtil.trimToEmpty(object == null ? null : object.getStr("sealedBy")));
        return vo;
    }

    private String resolveWinnerSide(MatchRecord match) {
        if (match == null || StrUtil.isBlank(match.getWinnerId())) {
            return null;
        }
        if (StrUtil.equals(match.getWinnerId(), match.getLeftPlayerId())) {
            return "left";
        }
        if (StrUtil.equals(match.getWinnerId(), match.getRightPlayerId())) {
            return "right";
        }
        return null;
    }

    private Map<String, MatchRecord> loadChildMatches(List<TeamMatchItem> items) {
        List<String> childIds = items.stream()
                .map(TeamMatchItem::getChildMatchId)
                .filter(StrUtil::isNotBlank)
                .toList();
        if (childIds.isEmpty()) {
            return Map.of();
        }
        return matchRecordMapper.selectList(new QueryWrapper<MatchRecord>().in("id", childIds)).stream()
                .collect(Collectors.toMap(MatchRecord::getId, item -> item, (a, b) -> b));
    }

    private TeamMatchLineupVO.TeamVO toTeamVO(Player participant, List<TournamentTeamMember> members) {
        TeamMatchLineupVO.TeamVO vo = new TeamMatchLineupVO.TeamVO();
        vo.setId(participant.getId());
        vo.setName(participant.getName());
        vo.setMembers(members.stream().map(this::toMemberVO).toList());
        return vo;
    }

    private TeamMatchLineupVO.MemberVO toMemberVO(TournamentTeamMember member) {
        TeamMatchLineupVO.MemberVO vo = new TeamMatchLineupVO.MemberVO();
        vo.setId(member.getId());
        vo.setName(member.getName());
        vo.setCaptain(Boolean.TRUE.equals(member.getCaptain()));
        return vo;
    }

    private List<TeamMatchLineupVO.MemberVO> toMemberVOs(List<String> ids, Map<String, TournamentTeamMember> memberMap) {
        return ids.stream()
                .map(memberMap::get)
                .filter(member -> member != null)
                .map(this::toMemberVO)
                .toList();
    }

    private List<String> parseIds(String json) {
        if (StrUtil.isBlank(json)) return List.of();
        return JSONUtil.parseArray(json).stream()
                .map(String::valueOf)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    private JSONObject parseObject(String json) {
        if (StrUtil.isBlank(json)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    private MatchContext loadContext(MatchRecord match, Tournament tournament) {
        Player leftTeam = requireParticipant(match.getLeftPlayerId());
        Player rightTeam = requireParticipant(match.getRightPlayerId());
        List<TournamentTeamMember> leftMembers = loadMembers(tournament, leftTeam.getId());
        List<TournamentTeamMember> rightMembers = loadMembers(tournament, rightTeam.getId());
        List<TeamMatchItem> items = teamMatchItemMapper.selectList(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", match.getId())
                .orderByAsc("display_order"));
        return new MatchContext(
                match,
                tournament,
                leftTeam,
                rightTeam,
                leftMembers,
                rightMembers,
                leftMembers.stream().collect(Collectors.toMap(TournamentTeamMember::getId, item -> item)),
                rightMembers.stream().collect(Collectors.toMap(TournamentTeamMember::getId, item -> item)),
                items
        );
    }

    private Player requireParticipant(String participantId) {
        if (StrUtil.isBlank(participantId)) {
            throw new IllegalArgumentException("\u7f3a\u5c11\u5bf9\u9635\u53cc\u65b9");
        }
        Player player = playerMapper.selectById(participantId);
        if (player == null) {
            throw new IllegalArgumentException("\u53c2\u8d5b\u5355\u4f4d\u4e0d\u5b58\u5728: " + participantId);
        }
        return player;
    }

    private List<TournamentTeamMember> loadMembers(Tournament tournament, String participantId) {
        QueryWrapper<TournamentTeamMember> wrapper = new QueryWrapper<TournamentTeamMember>()
                .eq("tournament_id", tournament.getId())
                .eq("participant_id", participantId);
        if (isRelay(tournament)) {
            wrapper.orderByAsc("display_order", "id");
        } else {
            wrapper.orderByDesc("is_captain").orderByAsc("display_order", "id");
        }
        return tournamentTeamMemberMapper.selectList(wrapper);
    }

    private MatchRecord requireMatch(String matchId) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        MatchRecord match = matchRecordMapper.selectById(matchId);
        if (match == null) {
            throw new IllegalArgumentException("match record not found: " + matchId);
        }
        return match;
    }

    private MatchRecord requireMatchForUpdate(String matchId) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        MatchRecord match = matchRecordMapper.selectByIdForUpdate(matchId);
        if (match == null) {
            throw new IllegalArgumentException("match record not found: " + matchId);
        }
        return match;
    }

    private void requireActiveMatchLock(MatchRecord match, String userId, String lockToken) {
        if (match == null
                || StrUtil.isBlank(userId)
                || !StrUtil.equals(match.getLockedByUserId(), userId)
                || StrUtil.isBlank(lockToken)
                || !StrUtil.equals(match.getLockToken(), StrUtil.trim(lockToken))
                || match.getLockExpireTime() == null
                || match.getLockExpireTime().isBefore(java.time.LocalDateTime.now())) {
            throw new ForbiddenException("执裁会话已失效，请重新进入比赛");
        }
    }

    private void ensureParentMatchEditable(MatchRecord match) {
        if (Integer.valueOf(2).equals(match.getStatus()) || Integer.valueOf(3).equals(match.getStatus())) {
            throw new IllegalArgumentException("\u56e2\u4f53\u8d5b\u5df2\u7ed3\u675f");
        }
        if (StrUtil.isBlank(match.getLeftPlayerId()) || StrUtil.isBlank(match.getRightPlayerId())) {
            throw new IllegalArgumentException("\u5bf9\u9635\u672a\u5b8c\u6574");
        }
    }

    private Tournament requireReadableTournament(String userId, MatchRecord match) {
        Tournament tournament = requireTournament(match.getTournamentId());
        if (!Boolean.TRUE.equals(tournament.getArchived())) {
            return tournament;
        }
        if (StrUtil.isNotBlank(userId) && StrUtil.equals(userId, tournament.getCreatorUserId())) {
            return tournament;
        }
        throw new IllegalArgumentException("archived tournament is only visible to creator");
    }

    private Tournament requireOperator(String userId, MatchRecord match) {
        Tournament tournament = requireTournament(match.getTournamentId());
        if (Boolean.TRUE.equals(tournament.getArchived())) {
            throw new IllegalStateException("archived tournament is read-only");
        }
        if (StrUtil.equals(userId, tournament.getCreatorUserId())) {
            return tournament;
        }
        Long refereeCount = tournamentRefereeGrantMapper.selectCount(new QueryWrapper<TournamentRefereeGrant>()
                .eq("tournament_id", tournament.getId())
                .eq("user_id", userId));
        if (refereeCount > 0) {
            return tournament;
        }
        throw new IllegalArgumentException("only creator or referee can modify this match");
    }

    private Tournament requireTournament(String tournamentId) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("tournament not found: " + tournamentId);
        }
        return tournament;
    }

    private void requireBadmintonTeamTournament(Tournament tournament) {
        int sportType = tournament.getSportType() == null ? 0 : tournament.getSportType();
        int participantType = tournament.getParticipantType() == null ? 0 : tournament.getParticipantType();
        int template = templateOf(tournament);
        if (sportType != SPORT_BADMINTON || participantType != PARTICIPANT_TEAM
                || (template != TEMPLATE_SUDIRMAN_5 && template != TEMPLATE_RELAY)) {
            throw new IllegalArgumentException("\u4ec5\u652f\u6301\u7fbd\u6bdb\u7403\u56e2\u4f53\u8d5b\u6a21\u677f");
        }
    }

    private boolean isRelay(Tournament tournament) {
        return templateOf(tournament) == TEMPLATE_RELAY;
    }

    private int templateOf(Tournament tournament) {
        return tournament.getTeamMatchTemplate() == null ? 0 : tournament.getTeamMatchTemplate();
    }

    private int relayBaseScore(MatchContext context) {
        MatchRuleConfig rule = tournamentRuleResolver.resolveForMatch(context.tournament(), context.match());
        return rule.getPointsToWin() == null ? 10 : Math.max(1, rule.getPointsToWin());
    }

    private int relayMemberCount(Tournament tournament) {
        return tournament.getCapPoint() == null ? 6 : Math.max(3, Math.min(12, tournament.getCapPoint()));
    }

    private List<TemplateItem> templateItems(MatchContext context) {
        if (!isRelay(context.tournament())) {
            return SUDIRMAN_ITEMS;
        }
        return context.items().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder() == null ? 0 : a.getDisplayOrder(), b.getDisplayOrder() == null ? 0 : b.getDisplayOrder()))
                .map(item -> new TemplateItem(
                        item.getDisplayOrder(),
                        item.getItemCode(),
                        StrUtil.isBlank(item.getItemName()) ? item.getItemCode() : item.getItemName(),
                        item.getPlayerCount() == null ? 2 : item.getPlayerCount()
                ))
                .toList();
    }

    private record TemplateItem(Integer displayOrder, String code, String name, Integer playerCount) {
    }

    private record MatchContext(
            MatchRecord match,
            Tournament tournament,
            Player leftTeam,
            Player rightTeam,
            List<TournamentTeamMember> leftMembers,
            List<TournamentTeamMember> rightMembers,
            Map<String, TournamentTeamMember> leftMemberMap,
            Map<String, TournamentTeamMember> rightMemberMap,
            List<TeamMatchItem> items
    ) {
    }
}
