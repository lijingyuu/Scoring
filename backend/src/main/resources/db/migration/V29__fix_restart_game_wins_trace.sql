-- V29: 修复 restart 残留 game_wins=0 导致的手写签表重排窗口永久卡死（审查 P1-1）
-- 背景：resetMatchResult 曾把 left/right_game_wins 置 0 而非 NULL，而痕迹守卫把
--       非 NULL 的 game_wins 视为开赛痕迹，导致"重开一场完赛比赛后，整组签表
--       永远 400 已有比赛开始"，与"重开=窗口重新打开"的语义矛盾。
--       代码已改为置 NULL；本迁移清洗历史残留行。
-- 条件刻意保守：仅清洗"已被完全重置回未开始"的场（无胜者/比分展示/比分明细/
--       退赛标记/锁）。进行中比赛的 game_wins 属正常开赛痕迹，不受影响。
-- 原则：只清洗数据不改表结构，遵循全库单数表名约定。

UPDATE `match_record`
SET `left_game_wins` = NULL,
    `right_game_wins` = NULL
WHERE `status` = 0
  AND `winner_id` IS NULL
  AND `score_display` IS NULL
  AND `game_scores` IS NULL
  AND `retired_side` IS NULL
  AND `locked_by_user_id` IS NULL
  AND (`left_game_wins` = 0 OR `right_game_wins` = 0);
