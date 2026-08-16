# 核心类图

> 本文只画核心功能模块，不做全量类图。DTO、VO、Mapper 只在影响模块关系时出现。

## 1. 核心领域模型

```mermaid
classDiagram
direction LR

class User {
  +String id
  +String openid
  +String username
  +String passwordHash
  +String nickname
  +String avatarUrl
  +Boolean profileCompleted
}

class Tournament {
  +String id
  +String name
  +String location
  +Integer status
  +Integer sportType
  +Integer participantType
  +Integer teamMatchTemplate
  +Integer tournamentType
  +Integer knockoutSlots
  +Integer knockoutRounds
  +Integer qualifiersPerGroup
  +Integer currentStage
  +Boolean knockoutGenerated
  +Integer bestOf
  +Integer gamesToWin
  +Integer pointsToWin
  +Integer decidingPointsToWin
  +Boolean enableDeuce
  +Integer capPoint
  +Boolean thirdPlaceEnabled
  +Integer thirdPlaceBestOf
  +Integer thirdPlaceGamesToWin
  +Integer thirdPlacePointsToWin
  +Integer thirdPlaceDecidingPointsToWin
  +Boolean thirdPlaceEnableDeuce
  +Integer thirdPlaceCapPoint
  +Boolean roundRuleEnabled
  +Integer roundRobinRounds
  +Integer groupSize
  +String creatorUserId
  +Integer favoriteCount
  +Boolean archived
}

class Player {
  +String id
  +String tournamentId
  +String name
  +Integer seedRank
  +Integer groupNo
  +Integer groupPosition
}

class TournamentTeamMember {
  +String id
  +String tournamentId
  +String participantId
  +String name
  +Integer jerseyNumber
  +Integer displayOrder
  +Boolean libero
  +Boolean captain
}

class MatchRecord {
  +String id
  +String tournamentId
  +Integer stageType
  +Integer roundNum
  +Integer matchIndex
  +Integer groupNo
  +String leftPlayerId
  +String rightPlayerId
  +String winnerId
  +Integer status
  +String scoreDisplay
  +Integer leftGameWins
  +Integer rightGameWins
  +String gameScores
  +String nextMatchId
  +String nextMatchSlot
  +Integer matchRole
  +String loserNextMatchId
  +String loserNextMatchSlot
  +String retiredSide
}

class TeamMatchItem {
  +String id
  +String matchId
  +String tournamentId
  +Integer displayOrder
  +String itemCode
  +String itemName
  +Integer playerCount
  +String leftMemberIdsJson
  +String rightMemberIdsJson
  +String childMatchId
  +String winnerSide
  +Integer status
}

class MatchLineupConfig {
  +String id
  +String matchId
  +Integer gameNo
  +String leftCourtJson
  +String rightCourtJson
  +String leftMiddlePairIndexesJson
  +String rightMiddlePairIndexesJson
  +String leftLibero1Id
  +String leftLibero2Id
  +String rightLibero1Id
  +String rightLibero2Id
  +String serveSide
}

class MatchEvent {
  +String id
  +String matchId
  +Integer eventSeq
  +String eventType
  +Integer gameNo
  +Integer leftScore
  +Integer rightScore
  +String serveSide
  +String payloadJson
}

class TournamentRoundRule {
  +String id
  +String tournamentId
  +Integer stageType
  +Integer roundNum
  +Integer bestOf
  +Integer gamesToWin
  +Integer pointsToWin
  +Integer decidingPointsToWin
  +Boolean enableDeuce
  +Integer capPoint
}

class TournamentRefereeGrant {
  +String id
  +String tournamentId
  +String userId
}

class TournamentRankingConfig {
  +String id
  +String tournamentId
  +Integer configVersion
  +String configJson
  +LocalDateTime lockedAt
}

class TournamentQualificationOverride {
  +String id
  +String tournamentId
  +Integer groupNo
  +Integer rankSlot
  +String playerId
  +String operatorUserId
}

User "1" --> "0..*" Tournament : creatorUserId
Tournament "1" --> "0..*" Player : tournamentId
Player "1" --> "0..*" TournamentTeamMember : participantId
Tournament "1" --> "0..*" MatchRecord : tournamentId
MatchRecord "0..*" --> "1" Player : left/right/winner
MatchRecord "0..1" --> "0..1" MatchRecord : nextMatchId
MatchRecord "0..1" --> "0..1" MatchRecord : loserNextMatchId
MatchRecord "1" --> "0..*" TeamMatchItem : parent match
TeamMatchItem "0..1" --> "0..1" MatchRecord : childMatchId
MatchRecord "1" --> "0..*" MatchLineupConfig : matchId
MatchRecord "1" --> "0..*" MatchEvent : matchId
Tournament "1" --> "0..*" TournamentRoundRule : round rules
Tournament "1" --> "0..*" TournamentRefereeGrant : referee grants
User "1" --> "0..*" TournamentRefereeGrant : userId
Tournament "1" --> "0..1" TournamentRankingConfig : tournamentId
Tournament "1" --> "0..*" TournamentQualificationOverride : tournamentId
Player "1" --> "0..*" TournamentQualificationOverride : playerId
```

## 2. 赛事创建与赛程生成

```mermaid
classDiagram
direction LR

class TournamentController {
  +createTournament(req)
  +getBracket(id)
  +getGroups(id)
  +generateKnockout(id)
}

class TournamentService {
  <<interface>>
  +createTournament(creatorUserId, req)
  +getBracket(tournamentId, currentUserId)
  +getGroups(tournamentId, currentUserId)
  +generateKnockout(userId, tournamentId)
}

class TournamentServiceImpl {
  +createTournament(creatorUserId, req)
  +generateKnockout(userId, tournamentId)
  -buildPlayers()
  -generateMatchesForType()
  -collectQualifiers()
}

class BracketEngine {
  +generateKnockoutBracket(tournamentId, players)
  +generateKnockoutBracketBySlots(tournamentId, playerIds)
  -buildSeedOrder(p)
}

class RoundRobinEngine {
  +generateGroupMatches(tournamentId, players)
  +generateLeagueMatches(tournamentId, players, rounds)
  -generateOneRound(...)
}

class TournamentMapper
class PlayerMapper
class MatchRecordMapper
class TournamentRoundRuleMapper
class TournamentTeamMemberMapper
class Tournament
class Player
class MatchRecord
class TournamentRoundRule
class TournamentTeamMember

TournamentController --> TournamentService
TournamentService <|.. TournamentServiceImpl
TournamentServiceImpl --> TournamentMapper
TournamentServiceImpl --> PlayerMapper
TournamentServiceImpl --> MatchRecordMapper
TournamentServiceImpl --> TournamentRoundRuleMapper
TournamentServiceImpl --> TournamentTeamMemberMapper
TournamentServiceImpl --> BracketEngine
TournamentServiceImpl --> RoundRobinEngine
TournamentMapper --> Tournament
PlayerMapper --> Player
MatchRecordMapper --> MatchRecord
TournamentRoundRuleMapper --> TournamentRoundRule
TournamentTeamMemberMapper --> TournamentTeamMember
BracketEngine --> MatchRecord : creates
RoundRobinEngine --> MatchRecord : creates
```

## 3. 比赛记分与结算

```mermaid
classDiagram
direction LR

class MatchController {
  +updateScore(id, req)
  +finishMatch(id, req)
  +restartMatch(id)
  +saveMatchEvents(id, req)
  +saveLineupConfig(id, req)
  +getMatchRecord(id)
  +canOperateMatch(id)
}

class MatchService {
  <<interface>>
  +updateMatchResult(userId, matchId, req)
  +finishMatch(userId, matchId, req)
  +settleTeamMatch(userId, matchId)
  +restartMatch(userId, matchId)
  +saveMatchEvents(userId, matchId, req)
  +canOperateMatch(userId, matchId)
}

class MatchServiceImpl {
  +updateMatchResult(...)
  +finishMatch(...)
  +settleTeamMatch(...)
  +restartMatch(...)
  -finishParentTeamMatchIfSettled(...)
  -propagateFinishedMatch(...)
  -requireMatchOperator(...)
}

class TournamentRuleResolver {
  +resolveForMatch(tournament, match)
  -resolveScopeMatch(match)
}

class MatchRecordMapper
class PlayerMapper
class TournamentMapper
class MatchLineupConfigMapper
class MatchEventMapper
class TeamMatchItemMapper
class TournamentRefereeGrantMapper
class MatchRecord
class MatchLineupConfig
class MatchEvent
class TeamMatchItem

MatchController --> MatchService
MatchService <|.. MatchServiceImpl
MatchServiceImpl --> MatchRecordMapper
MatchServiceImpl --> PlayerMapper
MatchServiceImpl --> TournamentMapper
MatchServiceImpl --> MatchLineupConfigMapper
MatchServiceImpl --> MatchEventMapper
MatchServiceImpl --> TeamMatchItemMapper
MatchServiceImpl --> TournamentRefereeGrantMapper
MatchServiceImpl --> TournamentRuleResolver
MatchRecordMapper --> MatchRecord
MatchLineupConfigMapper --> MatchLineupConfig
MatchEventMapper --> MatchEvent
TeamMatchItemMapper --> TeamMatchItem
TournamentRuleResolver --> TeamMatchItemMapper
TournamentRuleResolver --> MatchRecordMapper
```

## 4. 团体赛与子比赛

```mermaid
classDiagram
direction LR

class MatchController {
  +getTeamMatchLineup(id)
  +saveTeamMatchLineup(id, req)
  +startTeamMatchItem(id, itemCode)
  +settleTeamMatch(id)
}

class TeamMatchService {
  <<interface>>
  +getLineup(currentUserId, matchId)
  +saveLineup(userId, matchId, req)
  +startChildMatch(userId, matchId, itemCode)
}

class TeamMatchServiceImpl {
  +getLineup(...)
  +saveLineup(...)
  +startChildMatch(...)
  -validateRelayLineup(...)
  -createChildMatch(...)
}

class MatchService {
  <<interface>>
  +settleTeamMatch(userId, matchId)
  +finishMatch(userId, childMatchId, req)
}

class MatchRecord {
  +String id
  +Integer stageType
  +String winnerId
  +Integer status
}

class TeamMatchItem {
  +String matchId
  +String childMatchId
  +String itemCode
  +String winnerSide
  +Integer status
}

class Player {
  +String id
  +String name
}

class TournamentTeamMember {
  +String participantId
  +String name
  +Integer jerseyNumber
}

class MatchRecordMapper
class TeamMatchItemMapper
class PlayerMapper
class TournamentTeamMemberMapper

MatchController --> TeamMatchService
MatchController --> MatchService
TeamMatchService <|.. TeamMatchServiceImpl
TeamMatchServiceImpl --> MatchRecordMapper
TeamMatchServiceImpl --> TeamMatchItemMapper
TeamMatchServiceImpl --> PlayerMapper
TeamMatchServiceImpl --> TournamentTeamMemberMapper
MatchRecord "1" --> "0..*" TeamMatchItem : parent matchId
TeamMatchItem "0..1" --> "0..1" MatchRecord : childMatchId
Player "1 team" --> "1..*" TournamentTeamMember : members
```

## 5. 排球记分前端状态协作

```mermaid
classDiagram
direction LR

class VolleyballScoreboardPage {
  <<Vue page>>
  +ctx = reactive(useScoreboard())
}

class ScoreboardPhone {
  <<Vue component>>
  +render(ctx)
}

class ScoreboardPad {
  <<Vue component>>
  +render(ctx)
}

class useScoreboard {
  <<composable>>
  +loadMatch()
  +addScore(side)
  +undo()
  +syncAndBack()
  -finishGame(winnerSide)
  -swapSides(reason)
  -flushPendingEvents()
  -syncCaptainState()
  -settleAllLiberoStates()
}

class match_state {
  <<module>>
  +createEmptyMatchState()
  +loadMatchState(matchId)
  +saveMatchState(matchId, state)
  +clearMatchState(matchId)
  +swapMatchStateSides(state)
  +buildHistoryEntry(state)
}

class match_guard {
  <<module>>
  +requireMatchOperator(matchId)
}

class request {
  <<module>>
  +request(url, options)
}

class auth_store {
  <<module>>
  +ensureAuth()
  +authState
}

class MatchController {
  <<REST>>
  +getMatchRecord(id)
  +saveLineupConfig(id, req)
  +saveMatchEvents(id, req)
  +finishMatch(id, req)
}

VolleyballScoreboardPage --> useScoreboard
VolleyballScoreboardPage --> ScoreboardPhone
VolleyballScoreboardPage --> ScoreboardPad
ScoreboardPhone --> useScoreboard : ctx
ScoreboardPad --> useScoreboard : ctx
useScoreboard --> match_state
useScoreboard --> match_guard
useScoreboard --> request
useScoreboard --> auth_store
request --> MatchController
match_guard --> request
match_guard --> auth_store
```

## 6. 鉴权与裁判权限

```mermaid
classDiagram
direction LR

class AuthController {
  +wechatLogin(req)
  +register(req)
  +passwordLogin(req)
  +updateProfile(req)
  +getCurrentProfile()
}

class AuthService {
  <<interface>>
  +loginWithCode(code)
  +register(req)
  +loginWithPassword(req)
  +verifyToken(token)
}

class AuthServiceImpl {
  +loginWithCode(code)
  +register(req)
  +loginWithPassword(req)
  +verifyToken(token)
  -signToken(String userId)
  -fetchOpenid(code)
}

class UserService {
  <<interface>>
  +updateProfile(userId, req)
  +getCurrentProfile(userId)
}

class AuthInterceptor {
  +preHandle(...)
  +afterCompletion(...)
}

class AuthGuard {
  +requireUserId()
}

class AuthContext {
  <<ThreadLocal>>
  +setUserId(userId)
  +getUserId()
  +clear()
}

class TournamentController {
  +authenticateReferee(id, req)
  +listReferees(id)
  +removeReferee(id, userId)
  +updateRefereePassword(id, req)
}

class TournamentService {
  <<interface>>
  +authenticateReferee(userId, tournamentId, req)
  +listReferees(userId, tournamentId)
  +removeReferee(userId, tournamentId, refereeUserId)
  +updateRefereePassword(userId, tournamentId, req)
}

class TournamentServiceImpl {
  +authenticateReferee(...)
  +listReferees(...)
  +removeReferee(...)
  +updateRefereePassword(...)
}

class MatchService {
  <<interface>>
  +canOperateMatch(userId, matchId)
}

class MatchServiceImpl {
  +canOperateMatch(userId, matchId)
}

class TournamentRefereeConfig {
  +String tournamentId
  +String passwordHash
}

class TournamentRefereeGrant {
  +String tournamentId
  +String userId
}

class UserMapper
class TournamentRefereeConfigMapper
class TournamentRefereeGrantMapper

AuthController --> AuthService
AuthController --> UserService
AuthController --> AuthGuard
AuthService <|.. AuthServiceImpl
AuthServiceImpl --> UserMapper
AuthInterceptor --> AuthService
AuthInterceptor --> AuthContext
AuthGuard --> AuthContext
TournamentController --> AuthGuard
TournamentController --> TournamentService
TournamentService <|.. TournamentServiceImpl
TournamentServiceImpl --> TournamentRefereeConfigMapper
TournamentServiceImpl --> TournamentRefereeGrantMapper
MatchService <|.. MatchServiceImpl
MatchServiceImpl --> TournamentRefereeGrantMapper : canOperate
TournamentRefereeConfigMapper --> TournamentRefereeConfig
TournamentRefereeGrantMapper --> TournamentRefereeGrant
```

## 7. 接力追分前端纯逻辑

```mermaid
classDiagram
direction LR

class TeamRelayPage {
  <<Vue page>>
  +score
  +segmentScores
  +segmentSwitchPending
  +sidesSwapped
}

class TeamLineupPage {
  <<Vue page>>
  +leftOrder
  +rightOrder
  +saveLineup()
}

class relay_scoring {
  <<module>>
  +computeTargetScore(baseScore, itemsLength, relayMemberCount)
  +computeSegmentTarget(baseScore, currentSegmentNo, targetScore)
  +isRelayMatchEnded(matchStatus, targetScore, leftScore, rightScore)
  +isSegmentTargetReached(leftScore, rightScore, segmentTarget)
  +appendSegmentScore(segmentScores, segmentNo, leftScore, rightScore)
  +buildRelayItemsFromOrders(leftOrder, rightOrder, memberCount)
  +validateRelayChain(pairs)
  +buildRelayOrderFromItems(items, side, count)
  +relayStorageKey(matchId)
}

class TeamMatchService {
  <<REST>>
  +getLineup(matchId)
  +saveLineup(matchId, req)
}

class MatchService {
  <<REST>>
  +settleTeamMatch(matchId)
  +finishMatch(matchId, req)
}

class TeamMatchItem {
  +String itemCode
  +String leftMemberIdsJson
  +String rightMemberIdsJson
}

TeamLineupPage --> relay_scoring
TeamRelayPage --> relay_scoring
TeamLineupPage --> TeamMatchService
TeamRelayPage --> MatchService
relay_scoring --> TeamMatchItem : builds items payload
```

## 8. 小组排名与晋级资格覆盖

```mermaid
classDiagram
direction LR

class TournamentController {
  +getRankingConfig(id)
  +updateRankingConfig(id, req)
  +updateQualificationOverrides(id, req)
  +previewKnockout(id)
}

class TournamentServiceImpl {
  +getRankingConfig(tournamentId, currentUserId)
  +updateRankingConfig(userId, tournamentId, req)
  +updateQualificationOverrides(userId, tournamentId, req)
  +previewKnockout(userId, tournamentId)
  -loadRankingConfig(tournamentId)
  -parseRankingConfig(req)
  -buildGroupStandingsWithEngine(players, matches, q, config)
  -applyQualificationOverrides(standings, overrides)
}

class GroupStandingEngine {
  +rank(players, matches, qualifiersPerGroup, config)
  -orderByPriority(...)
  -resolveMultiHeadToHead(...)
  -markRanksAndTies(...)
  -applyWithdrawPolicy(...)
}

class RankingConfig {
  +Template template
  +List~Criterion~ priorities
  +MathType mathType
  +boolean twoWayTieH2HFirst
  +WithdrawPolicy withdrawPolicy
  +PointsSystem pointsSystem
  +toJson()
  +fromJson(json)
  +preset(template)
  +legacyDefault()
}

class TournamentRankingConfig {
  +String tournamentId
  +Integer configVersion
  +String configJson
  +LocalDateTime lockedAt
}

class TournamentQualificationOverride {
  +String tournamentId
  +Integer groupNo
  +Integer rankSlot
  +String playerId
  +String operatorUserId
}

class TournamentRankingConfigMapper
class TournamentQualificationOverrideMapper
class GroupStandingsVO

TournamentController --> TournamentServiceImpl
TournamentServiceImpl --> GroupStandingEngine
GroupStandingEngine --> RankingConfig
TournamentServiceImpl --> TournamentRankingConfigMapper
TournamentServiceImpl --> TournamentQualificationOverrideMapper
TournamentRankingConfigMapper --> TournamentRankingConfig
TournamentQualificationOverrideMapper --> TournamentQualificationOverride
TournamentServiceImpl --> GroupStandingsVO
```

## 9. 战报签章

```mermaid
classDiagram
direction LR

class MatchController {
  +saveReportMeta(id, req)
  +sealMatchReport(id)
}

class MatchServiceImpl {
  +saveReportMeta(userId, matchId, req)
  +sealMatchReport(userId, matchId)
  -buildReportMetaJson(req, current)
  -ensureReportComplete(root)
  -ensureReportDraft(root)
  -ensureReportNotSealed(matchId)
}

class MatchReportMeta {
  +String matchId
  +String metaJson
}

class MatchReportMetaMapper

MatchController --> MatchServiceImpl
MatchServiceImpl --> MatchReportMetaMapper
MatchReportMetaMapper --> MatchReportMeta
```
