ALTER TABLE `tournament`
  ADD COLUMN `round_rule_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether per-round rules are enabled' AFTER `round_robin_rounds`,
  ADD COLUMN `knockout_rounds` INT DEFAULT NULL COMMENT 'knockout rounds' AFTER `knockout_slots`,
  ADD COLUMN `deciding_points_to_win` INT DEFAULT NULL COMMENT 'target points for deciding game' AFTER `points_to_win`;

UPDATE `tournament`
SET `knockout_rounds` = CASE
  WHEN `tournament_type` = 1 AND `knockout_slots` = 2 THEN 1
  WHEN `tournament_type` = 1 AND `knockout_slots` = 4 THEN 2
  WHEN `tournament_type` = 1 AND `knockout_slots` = 8 THEN 3
  WHEN `tournament_type` = 1 AND `knockout_slots` = 16 THEN 4
  WHEN `tournament_type` = 1 AND `knockout_slots` = 32 THEN 5
  ELSE `knockout_rounds`
END
WHERE `tournament_type` = 1;

UPDATE `tournament`
SET `deciding_points_to_win` = 15
WHERE `sport_type` = 1
  AND `deciding_points_to_win` IS NULL;

CREATE TABLE IF NOT EXISTS `tournament_round_rule` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `stage_type` TINYINT NOT NULL COMMENT '0-group, 1-knockout',
  `round_num` INT NOT NULL COMMENT '0 for group stage, knockout round number otherwise',
  `best_of` INT NOT NULL COMMENT 'total games in one match',
  `games_to_win` INT NOT NULL COMMENT 'games needed to win one match',
  `points_to_win` INT NOT NULL COMMENT 'points needed to win one normal game',
  `deciding_points_to_win` INT DEFAULT NULL COMMENT 'points needed to win deciding game',
  `enable_deuce` TINYINT(1) NOT NULL COMMENT 'whether deuce rule is enabled',
  `cap_point` INT NOT NULL COMMENT 'maximum points in one game',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tournament_round_rule` (`tournament_id`, `stage_type`, `round_num`),
  KEY `idx_round_rule_tournament_id` (`tournament_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tournament round rule';
