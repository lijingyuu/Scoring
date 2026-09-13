-- V23: 组别（division）层级 —— 赛事(tournament)与比赛(match_record)之间插入组别
-- 设计：每个赛事必有 >=1 个组别；存量赛事回填一个默认组别；赛制/规则/排名键全部下沉到组别。
-- 原则：只加不改不删，tournament 表旧列保留（单组别镜像写保持回滚兼容）。

-- 1. 组别表
CREATE TABLE IF NOT EXISTS `tournament_division` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `name` VARCHAR(64) NOT NULL COMMENT 'division display name, e.g. 男单组',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT 'display order within tournament',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-not started, 1-running, 2-finished',
  `participant_type` TINYINT NOT NULL DEFAULT 0 COMMENT 'reserved, always 0 (individual) in v1',
  `tournament_type` TINYINT NOT NULL DEFAULT 0 COMMENT '0-knockout, 1-group plus knockout, 2-single round robin',
  `group_size` INT DEFAULT NULL COMMENT 'target players per group',
  `knockout_slots` INT DEFAULT NULL COMMENT 'total knockout qualifiers',
  `knockout_rounds` INT DEFAULT NULL COMMENT 'knockout rounds',
  `qualifiers_per_group` INT DEFAULT NULL COMMENT 'qualifiers per group',
  `round_robin_rounds` TINYINT NOT NULL DEFAULT 1 COMMENT '1=single round robin, 2=double round robin',
  `current_stage` TINYINT NOT NULL DEFAULT 1 COMMENT '0-group, 1-knockout',
  `knockout_generated` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'whether knockout bracket exists',
  `best_of` INT NOT NULL DEFAULT 3 COMMENT 'total games in one match',
  `games_to_win` INT NOT NULL DEFAULT 2 COMMENT 'games needed to win one match',
  `points_to_win` INT NOT NULL DEFAULT 21 COMMENT 'points needed to win one game',
  `deciding_points_to_win` INT DEFAULT NULL COMMENT 'target points for deciding game',
  `enable_deuce` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'whether deuce rule is enabled',
  `cap_point` INT NOT NULL DEFAULT 30 COMMENT 'maximum points in one game',
  `round_rule_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether per-round rules are enabled',
  `third_place_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether third place match is enabled',
  `third_place_best_of` INT DEFAULT NULL COMMENT 'third place total games',
  `third_place_games_to_win` INT DEFAULT NULL COMMENT 'third place games needed to win',
  `third_place_points_to_win` INT DEFAULT NULL COMMENT 'third place normal game target points',
  `third_place_deciding_points_to_win` INT DEFAULT NULL COMMENT 'third place deciding game target points',
  `third_place_enable_deuce` TINYINT(1) DEFAULT NULL COMMENT 'third place deuce enabled',
  `third_place_cap_point` INT DEFAULT NULL COMMENT 'third place cap point',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_division_tournament_name` (`tournament_id`, `name`),
  KEY `idx_division_tournament_id` (`tournament_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tournament division (组别)';

-- 2. 存量回填：每个赛事一个默认组别（id = 赛事id + 'D01'，与运行时雪花id（纯数字）无碰撞）
INSERT INTO `tournament_division` (
  `id`, `tournament_id`, `name`, `sort_order`, `status`, `participant_type`,
  `tournament_type`, `group_size`, `knockout_slots`, `knockout_rounds`, `qualifiers_per_group`,
  `round_robin_rounds`, `current_stage`, `knockout_generated`,
  `best_of`, `games_to_win`, `points_to_win`, `deciding_points_to_win`, `enable_deuce`, `cap_point`,
  `round_rule_enabled`,
  `third_place_enabled`, `third_place_best_of`, `third_place_games_to_win`, `third_place_points_to_win`,
  `third_place_deciding_points_to_win`, `third_place_enable_deuce`, `third_place_cap_point`
)
SELECT
  CONCAT(t.`id`, 'D01'), t.`id`, '默认组别', 0, t.`status`, t.`participant_type`,
  t.`tournament_type`, t.`group_size`, t.`knockout_slots`, t.`knockout_rounds`, t.`qualifiers_per_group`,
  t.`round_robin_rounds`, t.`current_stage`, t.`knockout_generated`,
  t.`best_of`, t.`games_to_win`, t.`points_to_win`, t.`deciding_points_to_win`, t.`enable_deuce`, t.`cap_point`,
  t.`round_rule_enabled`,
  t.`third_place_enabled`, t.`third_place_best_of`, t.`third_place_games_to_win`, t.`third_place_points_to_win`,
  t.`third_place_deciding_points_to_win`, t.`third_place_enable_deuce`, t.`third_place_cap_point`
FROM `tournament` t;

-- 3. player 加组别
ALTER TABLE `player`
  ADD COLUMN `division_id` VARCHAR(32) NULL COMMENT 'division id' AFTER `tournament_id`;

UPDATE `player` p
JOIN `tournament_division` d ON d.`tournament_id` = p.`tournament_id`
SET p.`division_id` = d.`id`;

ALTER TABLE `player`
  MODIFY COLUMN `division_id` VARCHAR(32) NOT NULL COMMENT 'division id',
  ADD INDEX `idx_player_division_id` (`division_id`);

-- 4. match_record 加组别
ALTER TABLE `match_record`
  ADD COLUMN `division_id` VARCHAR(32) NULL COMMENT 'division id' AFTER `tournament_id`;

UPDATE `match_record` m
JOIN `tournament_division` d ON d.`tournament_id` = m.`tournament_id`
SET m.`division_id` = d.`id`;

ALTER TABLE `match_record`
  MODIFY COLUMN `division_id` VARCHAR(32) NOT NULL COMMENT 'division id',
  ADD INDEX `idx_match_division_id` (`division_id`);

-- 5. tournament_round_rule 加组别；唯一键锚定组别
ALTER TABLE `tournament_round_rule`
  ADD COLUMN `division_id` VARCHAR(32) NULL COMMENT 'division id' AFTER `tournament_id`;

UPDATE `tournament_round_rule` r
JOIN `tournament_division` d ON d.`tournament_id` = r.`tournament_id`
SET r.`division_id` = d.`id`;

ALTER TABLE `tournament_round_rule`
  MODIFY COLUMN `division_id` VARCHAR(32) NOT NULL COMMENT 'division id',
  DROP INDEX `uk_tournament_round_rule`,
  ADD UNIQUE KEY `uk_round_rule_division` (`division_id`, `stage_type`, `round_num`),
  ADD INDEX `idx_round_rule_division_id` (`division_id`);

-- 6. tournament_ranking_config 加组别；唯一键锚定组别
ALTER TABLE `tournament_ranking_config`
  ADD COLUMN `division_id` VARCHAR(32) NULL COMMENT 'division id' AFTER `tournament_id`;

UPDATE `tournament_ranking_config` c
JOIN `tournament_division` d ON d.`tournament_id` = c.`tournament_id`
SET c.`division_id` = d.`id`;

ALTER TABLE `tournament_ranking_config`
  MODIFY COLUMN `division_id` VARCHAR(32) NOT NULL COMMENT 'division id',
  DROP INDEX `uk_tournament_ranking_config_tournament`,
  ADD UNIQUE KEY `uk_ranking_config_division` (`division_id`);

-- 7. tournament_qualification_override 加组别；两个唯一键锚定组别
-- 兼容遗留：V18 建表时未显式声明字符集，表继承了库级默认排序规则（本机为 utf8mb4_general_ci），
-- 与本迁移用 `DEFAULT CHARSET=utf8mb4` 建出的列（utf8mb4 默认排序规则）比较时报 error 1267。
-- 这里先按同一习惯（只写 charset，不写 collate）把整表归一化，保证新列与 tournament_division 可比较。
ALTER TABLE `tournament_qualification_override`
  CONVERT TO CHARACTER SET utf8mb4;

ALTER TABLE `tournament_qualification_override`
  ADD COLUMN `division_id` VARCHAR(32) NULL COMMENT 'division id' AFTER `tournament_id`;

UPDATE `tournament_qualification_override` o
JOIN `tournament_division` d ON d.`tournament_id` = o.`tournament_id`
SET o.`division_id` = d.`id`;

ALTER TABLE `tournament_qualification_override`
  MODIFY COLUMN `division_id` VARCHAR(32) NOT NULL COMMENT 'division id',
  DROP INDEX `uk_qualification_override_slot`,
  DROP INDEX `uk_qualification_override_player`,
  ADD UNIQUE KEY `uk_qualification_override_division_slot` (`division_id`, `group_no`, `rank_slot`),
  ADD UNIQUE KEY `uk_qualification_override_division_player` (`division_id`, `group_no`, `player_id`);
