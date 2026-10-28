package com.scoring.backend.service.match;

import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.vo.MatchRecordDetailVO;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MatchDetailAssemblerTest {

    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private TournamentTeamMemberMapper tournamentTeamMemberMapper;
    @Mock
    private MatchReportMetaMapper matchReportMetaMapper;

    private MatchDetailAssembler newAssembler() {
        return new MatchDetailAssembler(playerMapper, tournamentTeamMemberMapper,
                new MatchReportAssembler(matchReportMetaMapper));
    }

    /**
     * 记录页球衣号/姓名快照化：赛事结束后编辑球衣号不得追溯改写历史记录；
     * 快照里没有的成员（老数据/后续补录）仍回退实时 memberMap。
     */
    @Test
    void mergeSnapshotMemberMap_prefersRosterSnapshotAndFallsBackToLiveMembers() {
        TournamentTeamMember liveChanged = new TournamentTeamMember();
        liveChanged.setId("m1");
        liveChanged.setName("改号后姓名");
        liveChanged.setJerseyNumber(99);
        TournamentTeamMember liveUntouched = new TournamentTeamMember();
        liveUntouched.setId("m2");
        liveUntouched.setName("未被快照覆盖");
        liveUntouched.setJerseyNumber(20);

        MatchEvent rosterSnapshot = new MatchEvent();
        rosterSnapshot.setEventType("roster_snapshot");
        rosterSnapshot.setPayloadJson("{\"leftMembers\":[{\"id\":\"m1\",\"name\":\"开赛时姓名\",\"jerseyNumber\":7}],"
                + "\"rightMembers\":[{\"id\":\"m9\",\"name\":\"右队队员\",\"jerseyNumber\":11}]}");

        Map<String, TournamentTeamMember> merged = newAssembler().mergeSnapshotMemberMap(
                List.of(rosterSnapshot), Map.of("m1", liveChanged, "m2", liveUntouched));

        TournamentTeamMember snapshotWins = merged.get("m1");
        assertNotNull(snapshotWins);
        assertEquals(7, snapshotWins.getJerseyNumber().intValue());
        assertEquals("开赛时姓名", snapshotWins.getName());
        assertNotNull(merged.get("m9"), "快照独有的成员也应进入渲染用映射");
        assertEquals(11, merged.get("m9").getJerseyNumber().intValue());
        assertEquals(20, merged.get("m2").getJerseyNumber().intValue(), "快照未覆盖的成员回退实时数据");
    }

    private MatchEvent event(Integer seq, String type, String payloadJson) {
        MatchEvent event = new MatchEvent();
        event.setEventSeq(seq);
        event.setEventType(type);
        event.setPayloadJson(payloadJson);
        return event;
    }

    private MatchEvent undo(int seq, String revertToSeq) {
        String payload = revertToSeq == null
                ? "{\"reason\":\"undo\",\"leftScore\":1,\"rightScore\":0}"
                : "{\"reason\":\"undo\",\"revertToSeq\":" + revertToSeq + ",\"leftScore\":1,\"rightScore\":0}";
        return event(seq, "score_snapshot", payload);
    }

    /**
     * undo 补偿语义（审查 §5.4-③）：换人/暂停/队长变更/换边四类事件均可被 undo 快照回收；
     * 多次 undo 各自只回收"水位之上的尾部事件"，undo 之后的新事件不受影响；
     * 输入乱序时按 seq 排序后扫描，结果与顺序无关。
     */
    @Test
    void resolveRevertedEventSeqs_mixedUndoChain_shouldRevertOnlyEventsAboveWaterline() {
        List<MatchEvent> events = List.of(
                event(8, "substitution", "{\"side\":\"left\"}"),                 // undo 之后的新换人：必须保留
                undo(7, "4"),                                                   // 第二次 undo：回收 seq>4 的尾部
                event(6, "side_switch", "{}"),
                event(5, "captain_change", "{}"),
                undo(4, "2"),                                                   // 第一次 undo：回收 seq>2 的尾部
                event(3, "timeout", "{}"),
                event(2, "substitution", "{\"side\":\"right\"}"),
                event(1, "lineup_snapshot", "{}"));                             // 位于水位之上之外（seq 1 <= 水位 2）：保留

        Set<Integer> reverted = newAssembler().resolveRevertedEventSeqs(events);

        assertEquals(Set.of(3, 5, 6), reverted);
    }

    /**
     * undo 边界：revertToSeq=0 回收此前全部可撤销事件；seq==R 的事件恰在水位上、保留；
     * 旧客户端（无 revertToSeq）与非法值（负数/非数字）视为无补偿；缺 seq 的脏行不参与扫描。
     */
    @Test
    void resolveRevertedEventSeqs_waterlineAndMalformedPayloads_shouldStayConservative() {
        // revertToSeq=0：全撤
        Set<Integer> allReverted = newAssembler().resolveRevertedEventSeqs(List.of(
                event(1, "substitution", "{}"),
                event(2, "timeout", "{}"),
                undo(3, "0")));
        assertEquals(Set.of(1, 2), allReverted);

        // seq==R：恰好在水位上，不回收
        Set<Integer> boundary = newAssembler().resolveRevertedEventSeqs(List.of(
                event(1, "substitution", "{}"),
                event(2, "substitution", "{}"),
                undo(3, "1")));
        assertEquals(Set.of(2), boundary);

        // 非法/缺失 revertToSeq：视为无补偿，维持原状（兼容存量数据）
        assertEquals(Set.of(), newAssembler().resolveRevertedEventSeqs(List.of(
                event(1, "timeout", "{}"),
                undo(2, "-1"))));
        assertEquals(Set.of(), newAssembler().resolveRevertedEventSeqs(List.of(
                event(1, "timeout", "{}"),
                undo(2, "abc"))));
        assertEquals(Set.of(), newAssembler().resolveRevertedEventSeqs(List.of(
                event(1, "timeout", "{}"),
                undo(2, null))));

        // 缺 eventSeq 的脏行：被忽略，不抛异常
        MatchEvent dirty = event(null, "timeout", "{}");
        assertEquals(Set.of(), newAssembler().resolveRevertedEventSeqs(List.of(dirty, undo(2, "0"))));
        assertTrue(newAssembler().resolveRevertedEventSeqs(null).isEmpty());
    }

    private MatchEvent gameEvent(Integer seq, Integer gameNo, String type, String payloadJson) {
        MatchEvent event = event(seq, type, payloadJson);
        event.setGameNo(gameNo);
        return event;
    }

    private String lineupPayload(String leftFirst, String leftSecond) {
        return "{\"left\":{\"court\":[\"" + leftFirst + "\",\"" + leftSecond + "\"]},"
                + "\"right\":{\"court\":[\"B1\"]}}";
    }

    /**
     * 0:0 首发轮次修正（审查补充）：同一局会出现第二帧 lineup_snapshot 修正帧，
     * 记录表开局轮次以最后一帧为准；修正帧被 undo 回收后应取回第一帧。
     */
    @Test
    void buildLineupSnapshots_sameGameRotationAdjustFrame_prefersLastFrameUnlessReverted() {
        MatchEvent firstFrame = gameEvent(1, 1, "lineup_snapshot", lineupPayload("A1", "A2"));
        MatchEvent adjustFrame = gameEvent(3, 1, "lineup_snapshot", lineupPayload("A2", "A6"));

        // 无 undo：同局两帧取最后一帧（修正后的轮次）
        List<MatchRecordDetailVO.LineupSnapshotRecord> lastFrameWins = newAssembler().buildLineupSnapshots(
                List.of(firstFrame, adjustFrame), List.of(), Map.of());
        assertEquals(1, lastFrameWins.size());
        assertEquals(1, lastFrameWins.get(0).getGameNo());
        assertEquals("A2", lastFrameWins.get(0).getLeft().getCourt().get(0).getMemberId());
        assertEquals("A6", lastFrameWins.get(0).getLeft().getCourt().get(1).getMemberId());

        // undo 回收修正帧（revertToSeq=2 指向修正前）：取回第一帧
        List<MatchRecordDetailVO.LineupSnapshotRecord> revertedBack = newAssembler().buildLineupSnapshots(
                List.of(firstFrame, adjustFrame, undo(4, "2")), List.of(), Map.of());
        assertEquals(1, revertedBack.size());
        assertEquals("A1", revertedBack.get(0).getLeft().getCourt().get(0).getMemberId());
        assertEquals("A2", revertedBack.get(0).getLeft().getCourt().get(1).getMemberId());
    }
}
