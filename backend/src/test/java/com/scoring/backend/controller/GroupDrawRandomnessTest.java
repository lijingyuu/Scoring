package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.UserMapper;
import com.scoring.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分组抽签随机性验证：
 * - 未刻意设置种子的选手必须随机分组（重复创建同一名单，分组结果不能恒定）；
 * - 刻意设置种子的选手按种子序蛇形保位（种子间互不同组）。
 */
@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:group_draw_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class GroupDrawRandomnessTest {

    private static final int PLAYER_COUNT = 8;
    private static final int KNOCKOUT_SLOTS = 4;
    private static final int QUALIFIERS_PER_GROUP = 1;
    private static final int GROUP_COUNT = KNOCKOUT_SLOTS / QUALIFIERS_PER_GROUP;
    private static final int RUNS = 20;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlayerMapper playerMapper;
    @Autowired
    private MatchRecordMapper matchRecordMapper;
    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        matchRecordMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-1"));
    }

    @Test
    void unseededPlayers_shouldBeRandomlyGrouped_acrossRepeatedCreations() throws Exception {
        Set<String> signatures = new HashSet<>();
        for (int run = 0; run < RUNS; run++) {
            String tournamentId = createGroupTournament(PLAYER_COUNT, null);
            Map<String, Integer> groupOf = loadGroupAssignment(tournamentId);

            assertGroupStructure(groupOf);
            signatures.add(signature(groupOf));
        }
        // 8 人分 4 组的分配方式极多，20 次创建全部相同的概率可忽略不计；
        // 若出现恒等分组说明引入了确定性排序（回归）。
        assertTrue(signatures.size() >= 2,
                "重复创建相同名单应产生不同分组，实际只出现 " + signatures.size() + " 种");
    }

    @Test
    void seededPlayers_shouldKeepSerpentinePositions_andUnseededStayRandom() throws Exception {
        // P1~P4 设置种子 1~4，P5~P8 不设种子
        Map<Integer, Integer> seeds = new HashMap<>();
        for (int i = 1; i <= 4; i++) {
            seeds.put(i, i);
        }
        Set<String> unseededSignatures = new HashSet<>();
        for (int run = 0; run < 10; run++) {
            String tournamentId = createGroupTournament(PLAYER_COUNT, seeds);
            Map<String, Integer> groupOf = loadGroupAssignment(tournamentId);

            assertGroupStructure(groupOf);
            // 种子保位：种子1→组1、种子2→组2、种子3→组3、种子4→组4
            assertEquals(1, groupOf.get("P1"));
            assertEquals(2, groupOf.get("P2"));
            assertEquals(3, groupOf.get("P3"));
            assertEquals(4, groupOf.get("P4"));

            unseededSignatures.add(signature(groupOf));
        }
        // 种子位置固定，但非种子 4 人的分布仍应随机
        assertTrue(unseededSignatures.size() >= 2,
                "非种子选手分组应随机，实际只出现 " + unseededSignatures.size() + " 种");
    }

    // ==================== 团体赛种子机制 ====================

    @Test
    void badmintonTeamGroup_seededTeamsKeepSerpentinePositions_andUnseededStayRandom() throws Exception {
        Map<Integer, Integer> seeds = new HashMap<>();
        for (int i = 1; i <= 4; i++) {
            seeds.put(i, i);
        }
        Set<String> signatures = new HashSet<>();
        for (int run = 0; run < 10; run++) {
            String tournamentId = createTeamGroupTournament(0, PLAYER_COUNT, seeds);
            Map<String, Integer> groupOf = loadGroupAssignment(tournamentId);

            assertGroupStructure(groupOf);
            assertEquals(1, groupOf.get("P1"));
            assertEquals(2, groupOf.get("P2"));
            assertEquals(3, groupOf.get("P3"));
            assertEquals(4, groupOf.get("P4"));
            signatures.add(signature(groupOf));
        }
        assertTrue(signatures.size() >= 2,
                "团体赛非种子队伍分组应随机，实际只出现 " + signatures.size() + " 种");
    }

    @Test
    void volleyballTeamGroup_seededTeamsKeepSerpentinePositions() throws Exception {
        Map<Integer, Integer> seeds = new HashMap<>();
        for (int i = 1; i <= 4; i++) {
            seeds.put(i, i);
        }
        String tournamentId = createTeamGroupTournament(1, PLAYER_COUNT, seeds);
        Map<String, Integer> groupOf = loadGroupAssignment(tournamentId);

        assertGroupStructure(groupOf);
        assertEquals(1, groupOf.get("P1"));
        assertEquals(2, groupOf.get("P2"));
        assertEquals(3, groupOf.get("P3"));
        assertEquals(4, groupOf.get("P4"));
    }

    @Test
    void teamPureKnockout_seededTeamsPlacedInDifferentHalves() throws Exception {
        // 4 队，种子1、2，其余 2 队不设种子；种子应分居上下半区（首轮不相遇，只能决赛相遇）
        Map<Integer, Integer> seeds = new HashMap<>();
        seeds.put(1, 1);
        seeds.put(2, 2);
        String tournamentId = createTeamKnockoutTournament(PLAYER_COUNT, seeds);

        Map<Integer, Integer> seedOf = new HashMap<>();
        for (Player p : playerMapper.selectList(new QueryWrapper<Player>().eq("tournament_id", tournamentId))) {
            if (p.getSeedRank() != null) {
                seedOf.put(p.getSeedRank(), Integer.valueOf(p.getName().substring(1)));
            }
        }
        assertEquals(Integer.valueOf(1), seedOf.get(1));
        assertEquals(Integer.valueOf(2), seedOf.get(2));

        List<MatchRecord> firstRound = matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournamentId)
                .eq("stage_type", 1)
                .eq("round_num", 1));
        assertEquals(4, firstRound.size());
        for (MatchRecord match : firstRound) {
            boolean hasSeedOne = containsParticipant(match, "P1");
            boolean hasSeedTwo = containsParticipant(match, "P2");
            assertTrue(!(hasSeedOne && hasSeedTwo), "种子1与种子2不应在首轮相遇");
        }
    }

    @Test
    void teamsEndpoint_shouldExposeSeedRank() throws Exception {
        Map<Integer, Integer> seeds = new HashMap<>();
        seeds.put(1, 1);
        seeds.put(3, 2);
        // 8 队分 4 组（每组 2 队），避免出现单人组
        String tournamentId = createTeamGroupTournament(0, 8, seeds);

        String response = mockMvc.perform(get("/api/v1/tournaments/{id}/teams", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(1, objectMapper.readTree(response).path("data").path("teams").get(0).path("seedRank").asInt());
        assertEquals(2, objectMapper.readTree(response).path("data").path("teams").get(2).path("seedRank").asInt());
        assertEquals(0, objectMapper.readTree(response).path("data").path("teams").get(1).path("seedRank").asInt());
    }

    private boolean containsParticipant(MatchRecord match, String name) {
        for (String playerId : List.of(match.getLeftPlayerId(), match.getRightPlayerId())) {
            if (playerId == null) {
                continue;
            }
            Player p = playerMapper.selectById(playerId);
            if (p != null && name.equals(p.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 团体赛分组赛创建；sportType: 0=羽毛球团体(苏杯), 1=排球
     */
    private String createTeamGroupTournament(int sportType, int teamCount, Map<Integer, Integer> seeds) throws Exception {
        Map<String, Object> request = sportType == 1
                ? buildVolleyballTeamRequest(teamCount, seeds, 1)
                : buildBadmintonTeamRequest(teamCount, seeds, 1);
        return postTournament(request);
    }

    private String createTeamKnockoutTournament(int teamCount, Map<Integer, Integer> seeds) throws Exception {
        Map<String, Object> request = buildBadmintonTeamRequest(teamCount, seeds, 0);
        return postTournament(request);
    }

    private Map<String, Object> buildBadmintonTeamRequest(int teamCount, Map<Integer, Integer> seeds, int tournamentType) {
        List<Map<String, Object>> teams = new ArrayList<>();
        for (int i = 1; i <= teamCount; i++) {
            teams.add(badmintonTeam("P" + i, seeds == null ? null : seeds.get(i)));
        }
        Map<String, Object> request = baseTeamRequest(sportType_BADMINTON, tournamentType);
        request.put("teamMatchTemplate", 1);
        request.put("teams", teams);
        request.put("rule", Map.of("bestOf", 3, "gamesToWin", 2, "pointsToWin", 21, "enableDeuce", true, "capPoint", 30));
        return request;
    }

    private Map<String, Object> buildVolleyballTeamRequest(int teamCount, Map<Integer, Integer> seeds, int tournamentType) {
        List<Map<String, Object>> teams = new ArrayList<>();
        for (int i = 1; i <= teamCount; i++) {
            Map<String, Object> team = volleyballTeam("P" + i, seeds == null ? null : seeds.get(i));
            teams.add(team);
        }
        Map<String, Object> request = baseTeamRequest(sportType_VOLLEYBALL, tournamentType);
        request.put("teams", teams);
        request.put("rule", Map.of("bestOf", 3, "gamesToWin", 2, "pointsToWin", 25, "decidingPointsToWin", 15,
                "enableDeuce", false, "capPoint", 99));
        return request;
    }

    private static final int sportType_BADMINTON = 0;
    private static final int sportType_VOLLEYBALL = 1;

    private Map<String, Object> baseTeamRequest(int sportType, int tournamentType) {
        Map<String, Object> request = new HashMap<>();
        request.put("sportType", sportType);
        request.put("participantType", 1);
        request.put("name", "team seed draw");
        request.put("location", "court");
        request.put("tournamentType", tournamentType);
        request.put("knockoutSlots", KNOCKOUT_SLOTS);
        request.put("qualifiersPerGroup", QUALIFIERS_PER_GROUP);
        request.put("players", List.of());
        return request;
    }

    private Map<String, Object> badmintonTeam(String name, Integer seed) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (int j = 1; j <= 2; j++) {
            members.add(Map.of("name", name + "M" + j, "captain", j == 1));
        }
        Map<String, Object> team = new HashMap<>();
        team.put("name", name);
        if (seed != null) {
            team.put("seed", seed);
        }
        team.put("members", members);
        return team;
    }

    private Map<String, Object> volleyballTeam(String name, Integer seed) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (int j = 1; j <= 6; j++) {
            Map<String, Object> member = new HashMap<>();
            member.put("name", name + "M" + j);
            member.put("jerseyNumber", j);
            member.put("captain", j == 1);
            member.put("libero", false);
            members.add(member);
        }
        Map<String, Object> team = new HashMap<>();
        team.put("name", name);
        if (seed != null) {
            team.put("seed", seed);
        }
        team.put("members", members);
        return team;
    }

    private String postTournament(Map<String, Object> request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    // ==================== helpers ====================

    private void assertGroupStructure(Map<String, Integer> groupOf) {
        assertEquals(PLAYER_COUNT, groupOf.size(), "所有选手都应被分配小组");
        Map<Integer, List<String>> byGroup = new HashMap<>();
        groupOf.forEach((name, groupNo) -> byGroup.computeIfAbsent(groupNo, k -> new ArrayList<>()).add(name));
        for (int g = 1; g <= GROUP_COUNT; g++) {
            List<String> members = byGroup.get(g);
            assertEquals(2, members == null ? 0 : members.size(), "组 " + g + " 应有 2 名选手");
        }
    }

    private String signature(Map<String, Integer> groupOf) {
        return new TreeMap<>(groupOf).toString();
    }

    private Map<String, Integer> loadGroupAssignment(String tournamentId) {
        List<Player> players = playerMapper.selectList(
                new QueryWrapper<Player>().eq("tournament_id", tournamentId));
        Map<String, Integer> groupOf = new HashMap<>();
        for (Player p : players) {
            groupOf.put(p.getName(), p.getGroupNo());
        }
        return groupOf;
    }

    /**
     * @param seeds 选手序号(1-based) → 种子序号；null 表示全部不设种子
     */
    private String createGroupTournament(int playerCount, Map<Integer, Integer> seeds) throws Exception {
        List<Map<String, Object>> players = new ArrayList<>();
        for (int i = 1; i <= playerCount; i++) {
            Map<String, Object> player = new HashMap<>();
            player.put("name", "P" + i);
            if (seeds != null && seeds.containsKey(i)) {
                player.put("seed", seeds.get(i));
            }
            players.add(player);
        }

        Map<String, Object> rule = new HashMap<>();
        rule.put("bestOf", 3);
        rule.put("gamesToWin", 2);
        rule.put("pointsToWin", 21);
        rule.put("enableDeuce", true);
        rule.put("capPoint", 30);

        Map<String, Object> request = new HashMap<>();
        request.put("sportType", 0);
        request.put("name", "group draw randomness");
        request.put("location", "court");
        request.put("tournamentType", 1);
        request.put("knockoutSlots", KNOCKOUT_SLOTS);
        request.put("qualifiersPerGroup", QUALIFIERS_PER_GROUP);
        request.put("players", players);
        request.put("rule", rule);

        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    private User buildUser(String id) {
        User user = new User();
        user.setId(id);
        user.setOpenid("openid-" + id);
        user.setNickname(id);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setProfileCompleted(true);
        return user;
    }
}
