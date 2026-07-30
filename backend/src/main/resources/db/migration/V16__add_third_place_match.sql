ALTER TABLE `tournament`
  ADD COLUMN `third_place_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether third place match is enabled' AFTER `round_rule_enabled`,
  ADD COLUMN `third_place_best_of` INT DEFAULT NULL COMMENT 'third place total games' AFTER `cap_point`,
  ADD COLUMN `third_place_games_to_win` INT DEFAULT NULL COMMENT 'third place games needed to win' AFTER `third_place_best_of`,
  ADD COLUMN `third_place_points_to_win` INT DEFAULT NULL COMMENT 'third place normal game target points' AFTER `third_place_games_to_win`,
  ADD COLUMN `third_place_deciding_points_to_win` INT DEFAULT NULL COMMENT 'third place deciding game target points' AFTER `third_place_points_to_win`,
  ADD COLUMN `third_place_enable_deuce` TINYINT(1) DEFAULT NULL COMMENT 'third place deuce enabled' AFTER `third_place_deciding_points_to_win`,
  ADD COLUMN `third_place_cap_point` INT DEFAULT NULL COMMENT 'third place cap point' AFTER `third_place_enable_deuce`;

UPDATE `tournament`
SET `third_place_best_of` = `best_of`,
    `third_place_games_to_win` = `games_to_win`,
    `third_place_points_to_win` = `points_to_win`,
    `third_place_deciding_points_to_win` = `deciding_points_to_win`,
    `third_place_enable_deuce` = `enable_deuce`,
    `third_place_cap_point` = `cap_point`
WHERE `third_place_best_of` IS NULL;

ALTER TABLE `match_record`
  ADD COLUMN `match_role` TINYINT NOT NULL DEFAULT 0 COMMENT '0-normal, 1-third place' AFTER `stage_type`,
  ADD COLUMN `loser_next_match_id` VARCHAR(32) DEFAULT NULL COMMENT 'next match for loser' AFTER `next_match_slot`,
  ADD COLUMN `loser_next_match_slot` VARCHAR(10) DEFAULT NULL COMMENT 'loser slot in next match' AFTER `loser_next_match_id`,
  ADD KEY `idx_match_loser_next_match_id` (`loser_next_match_id`);
