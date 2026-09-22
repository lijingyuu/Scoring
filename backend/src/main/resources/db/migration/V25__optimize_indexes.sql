-- V25 索引优化（2026-09-21，源自全链路审查 §6.1-P3-1，经复核修正）
-- 删除 3 个被更长唯一键最左前缀覆盖的冗余索引；补 2 个高频查询缺失索引。
-- 注意：审查报告最初把 idx_round_rule_tournament_id 也列为冗余，复核发现 V23 已删除其覆盖键
-- uk_tournament_round_rule(tournament_id,stage_type,round_num)，它是 round_rule 表上 tournament_id
-- 的唯一索引，必须保留，不在本次清理范围。

-- 1) 冗余：被 uk_match_event_seq(match_id, event_seq) 最左前缀覆盖
ALTER TABLE `match_event` DROP INDEX `idx_match_event_match_id`;

-- 2) 冗余：被 uk_division_tournament_name(tournament_id, name) 最左前缀覆盖
ALTER TABLE `tournament_division` DROP INDEX `idx_division_tournament_id`;

-- 3) 冗余：被 uk_custom_item_order(tournament_id, display_order) 最左前缀覆盖
ALTER TABLE `tournament_custom_item` DROP INDEX `idx_tournament_custom_item_tournament_id`;

-- 4) 缺失：团体赛子场查询按 child_match_id 反查 item（MatchSettlementService 完赛回写路径）
ALTER TABLE `team_match_item` ADD INDEX `idx_team_match_item_child_match_id` (`child_match_id`);

-- 5) 缺失：/tournaments/mine/created 与归档过滤按 (creator_user_id, archived) 扫描，此前 tournament 表无任何二级索引
ALTER TABLE `tournament` ADD INDEX `idx_tournament_creator_archived` (`creator_user_id`, `archived`);
