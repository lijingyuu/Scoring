-- V32: 排球三局两胜"打满三局"开关（force_full_games）
-- 语义：开启后（仅 bestOf=3 允许）终场条件从"先到 gamesToWin 胜局"改为"打满 bestOf 局按胜局定胜负"，
--       最终局分只能是 3:0 或 2:1；第三局仍走决胜局 15 分制 + 8 分换边。
-- 原则：只加不改不删；存量数据默认 0（关闭），行为零变化。
--       tournament.force_full_games 为可空镜像占位列（与 best_of 等 V23 下沉列同策略）：
--       正常流程以 tournament_division 为权威，仅 MatchRuleConfig.fromTournament 在组别数据
--       缺失的异常兑底路径读取。

ALTER TABLE `tournament_division`
  ADD COLUMN `force_full_games` TINYINT(1) NOT NULL DEFAULT 0
    COMMENT 'BO3 forced full games: 0-off (first to gamesToWin wins), 1-play all bestOf games then decide by game wins'
    AFTER `cap_point`;

ALTER TABLE `tournament_round_rule`
  ADD COLUMN `force_full_games` TINYINT(1) NOT NULL DEFAULT 0
    COMMENT 'BO3 forced full games, per-round scope (stageType独立存值，天然支持仅小组赛强制)'
    AFTER `cap_point`;

ALTER TABLE `tournament`
  ADD COLUMN `force_full_games` TINYINT(1) DEFAULT NULL
    COMMENT 'mirror placeholder of division.force_full_games (V23 sink column policy); normal flow reads tournament_division as authority, only MatchRuleConfig.fromTournament fallback reads this when division data is missing'
    AFTER `cap_point`;
