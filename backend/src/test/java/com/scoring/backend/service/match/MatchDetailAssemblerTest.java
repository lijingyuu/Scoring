package com.scoring.backend.service.match;

import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
