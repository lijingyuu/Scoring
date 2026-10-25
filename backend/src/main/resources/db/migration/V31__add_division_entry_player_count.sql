-- V31: 羽毛球个人赛双打参赛单位（可行性报告 D1/D2 阶段2）
-- 设计：参赛方仍为 player 表一行（组合显示名，如"张三/李四"），签表/分组/排名/记分
--       引擎以 player.id 为原子单位零改动；成员明细复用 tournament_team_member
--       （participant_id 指向 player.id，与团体赛队伍成员同构）。
-- 每参赛方人数挂在组别维度：多组别赛事各组别可独立单打/双打。
-- 兼容：历史组别 entry_player_count 默认 1（单打），行为不变；只加不改不删。

ALTER TABLE `tournament_division`
  ADD COLUMN `entry_player_count` TINYINT NOT NULL DEFAULT 1
    COMMENT 'individual entry size per participant: 1 singles / 2 doubles (badminton)'
    AFTER `participant_type`;
