CREATE DATABASE IF NOT EXISTS `scoring_mvp` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `scoring_mvp`;

DROP TABLE IF EXISTS `match_record`;
DROP TABLE IF EXISTS `player`;
DROP TABLE IF EXISTS `tournament`;

CREATE TABLE `tournament` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `name` VARCHAR(128) NOT NULL COMMENT 'tournament name',
  `location` VARCHAR(255) DEFAULT NULL COMMENT 'location',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-not started, 1-running, 2-finished',
  `tournament_type` TINYINT NOT NULL DEFAULT 0 COMMENT '0-knockout, 1-group plus knockout',
  `group_size` INT DEFAULT NULL COMMENT 'target players per group',
  `knockout_slots` INT DEFAULT NULL COMMENT 'total knockout qualifiers',
  `qualifiers_per_group` INT DEFAULT NULL COMMENT 'qualifiers per group',
  `current_stage` TINYINT NOT NULL DEFAULT 1 COMMENT '0-group, 1-knockout',
  `knockout_generated` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'whether knockout bracket exists',
  `best_of` INT NOT NULL DEFAULT 3 COMMENT 'total games in one match',
  `games_to_win` INT NOT NULL DEFAULT 2 COMMENT 'games needed to win one match',
  `points_to_win` INT NOT NULL DEFAULT 21 COMMENT 'points needed to win one game',
  `enable_deuce` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'whether deuce rule is enabled',
  `cap_point` INT NOT NULL DEFAULT 30 COMMENT 'maximum points in one game',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tournament';

CREATE TABLE `player` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `name` VARCHAR(64) NOT NULL COMMENT 'player name',
  `seed_rank` INT DEFAULT NULL COMMENT 'seed rank',
  `group_no` INT DEFAULT NULL COMMENT 'group number',
  `group_position` INT DEFAULT NULL COMMENT 'position in group',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  KEY `idx_player_tournament_id` (`tournament_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='player';

CREATE TABLE `match_record` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `round_num` INT NOT NULL COMMENT 'round number',
  `match_index` INT NOT NULL DEFAULT 0 COMMENT 'index in round',
  `stage_type` TINYINT NOT NULL DEFAULT 1 COMMENT '0-group, 1-knockout',
  `group_no` INT DEFAULT NULL COMMENT 'group number',
  `left_player_id` VARCHAR(32) DEFAULT NULL COMMENT 'left player id',
  `right_player_id` VARCHAR(32) DEFAULT NULL COMMENT 'right player id',
  `score_display` VARCHAR(255) DEFAULT NULL COMMENT 'score text',
  `winner_id` VARCHAR(32) DEFAULT NULL COMMENT 'winner player id',
  `left_game_wins` INT DEFAULT NULL COMMENT 'left won games',
  `right_game_wins` INT DEFAULT NULL COMMENT 'right won games',
  `game_scores` TEXT DEFAULT NULL COMMENT 'game scores json',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-pending, 1-running, 2-finished, 3-retired',
  `next_match_id` VARCHAR(32) DEFAULT NULL COMMENT 'next match id',
  `next_match_slot` VARCHAR(10) DEFAULT NULL COMMENT 'left/right slot in next match',
  `retired_side` VARCHAR(10) DEFAULT NULL COMMENT 'retired side',
  PRIMARY KEY (`id`),
  KEY `idx_match_tournament_id` (`tournament_id`),
  KEY `idx_match_next_match_id` (`next_match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='match record';
