CREATE DATABASE IF NOT EXISTS `scoring_mvp` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `scoring_mvp`;

DROP TABLE IF EXISTS `match_report_meta`;
DROP TABLE IF EXISTS `global_theme_config`;
DROP TABLE IF EXISTS `match_theme_config`;
DROP TABLE IF EXISTS `match_lineup_config`;
DROP TABLE IF EXISTS `team_match_item`;
DROP TABLE IF EXISTS `match_record`;
DROP TABLE IF EXISTS `tournament_referee_grant`;
DROP TABLE IF EXISTS `tournament_referee_config`;
DROP TABLE IF EXISTS `tournament_team_member`;
DROP TABLE IF EXISTS `player`;
DROP TABLE IF EXISTS `tournament_favorite`;
DROP TABLE IF EXISTS `app_user`;
DROP TABLE IF EXISTS `tournament`;

CREATE TABLE `tournament` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `name` VARCHAR(128) NOT NULL COMMENT 'tournament name',
  `location` VARCHAR(255) DEFAULT NULL COMMENT 'location',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-not started, 1-running, 2-finished',
  `sport_type` TINYINT NOT NULL DEFAULT 0 COMMENT '0-badminton, 1-volleyball',
  `participant_type` TINYINT NOT NULL DEFAULT 0 COMMENT '0-individual, 1-team',
  `team_match_template` TINYINT NOT NULL DEFAULT 0 COMMENT '0-none, 1-sudirman-5, 2-relay-chase',
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
  `creator_user_id` VARCHAR(32) NOT NULL COMMENT 'creator user id',
  `favorite_count` INT NOT NULL DEFAULT 0 COMMENT 'favorite count',
  `archived` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether tournament is archived',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tournament';

CREATE TABLE `app_user` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `openid` VARCHAR(64) NOT NULL COMMENT 'wechat openid',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT 'nickname',
  `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT 'avatar url',
  `profile_completed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether profile completed',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user';

CREATE TABLE `tournament_favorite` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `user_id` VARCHAR(32) NOT NULL COMMENT 'user id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_tournament` (`user_id`, `tournament_id`),
  KEY `idx_favorite_tournament_id` (`tournament_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tournament favorite';

CREATE TABLE `tournament_referee_config` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `password_hash` VARCHAR(128) NOT NULL COMMENT 'referee password hash',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_referee_config_tournament` (`tournament_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tournament referee password config';

CREATE TABLE `tournament_referee_grant` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `user_id` VARCHAR(32) NOT NULL COMMENT 'granted user id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_referee_grant_tournament_user` (`tournament_id`, `user_id`),
  KEY `idx_referee_grant_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tournament referee grants';

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

CREATE TABLE `tournament_team_member` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `participant_id` VARCHAR(32) NOT NULL COMMENT 'participant id stored in player table',
  `name` VARCHAR(64) NOT NULL COMMENT 'member name',
  `jersey_number` INT DEFAULT NULL COMMENT 'jersey number',
  `is_libero` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether libero',
  `is_captain` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether captain',
  `display_order` INT NOT NULL DEFAULT 0 COMMENT 'display order',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  KEY `idx_team_member_tournament_id` (`tournament_id`),
  KEY `idx_team_member_participant_id` (`participant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='volleyball team members';

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

CREATE TABLE `team_match_item` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `match_id` VARCHAR(32) NOT NULL COMMENT 'parent match id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `display_order` INT NOT NULL COMMENT 'item display order',
  `item_code` VARCHAR(16) NOT NULL COMMENT 'MS/WS/MD/WD/XD',
  `item_name` VARCHAR(32) NOT NULL COMMENT 'item name',
  `player_count` INT NOT NULL COMMENT 'members needed per side',
  `left_member_ids_json` TEXT DEFAULT NULL COMMENT 'left side member ids json',
  `right_member_ids_json` TEXT DEFAULT NULL COMMENT 'right side member ids json',
  `child_match_id` VARCHAR(32) DEFAULT NULL COMMENT 'future child match id',
  `winner_side` VARCHAR(10) DEFAULT NULL COMMENT 'left/right',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-pending, 1-running, 2-finished',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_match_item_match_code` (`match_id`, `item_code`),
  KEY `idx_team_match_item_tournament_id` (`tournament_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='team match item';

CREATE TABLE `match_lineup_config` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `match_id` VARCHAR(32) NOT NULL COMMENT 'match id',
  `game_no` INT NOT NULL COMMENT 'game number',
  `left_court_json` TEXT NOT NULL COMMENT 'left court member ids json',
  `right_court_json` TEXT NOT NULL COMMENT 'right court member ids json',
  `left_middle_pair_indexes_json` VARCHAR(64) NOT NULL COMMENT 'left middle pair indexes json',
  `right_middle_pair_indexes_json` VARCHAR(64) NOT NULL COMMENT 'right middle pair indexes json',
  `left_libero1_id` VARCHAR(32) DEFAULT NULL COMMENT 'left libero1 member id',
  `left_libero2_id` VARCHAR(32) DEFAULT NULL COMMENT 'left libero2 member id',
  `right_libero1_id` VARCHAR(32) DEFAULT NULL COMMENT 'right libero1 member id',
  `right_libero2_id` VARCHAR(32) DEFAULT NULL COMMENT 'right libero2 member id',
  `serve_side` VARCHAR(10) NOT NULL COMMENT 'left/right',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lineup_match_game` (`match_id`, `game_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='match lineup config';

CREATE TABLE `match_theme_config` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `match_id` VARCHAR(32) NOT NULL COMMENT 'match id',
  `theme_json` TEXT NOT NULL COMMENT 'theme json',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_theme_match` (`match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='match theme config';

CREATE TABLE `global_theme_config` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `scene_key` VARCHAR(64) NOT NULL COMMENT 'global theme scene key',
  `theme_json` TEXT NOT NULL COMMENT 'theme json',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_global_theme_scene` (`scene_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='global theme config';

CREATE TABLE `match_report_meta` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `match_id` VARCHAR(32) NOT NULL COMMENT 'match id',
  `meta_json` TEXT NOT NULL COMMENT 'report meta json',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_report_meta_match` (`match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='match report meta';

CREATE TABLE `match_event` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `match_id` VARCHAR(32) NOT NULL COMMENT 'match id',
  `event_seq` INT NOT NULL COMMENT 'global event seq within match',
  `event_type` VARCHAR(32) NOT NULL COMMENT 'event type',
  `game_no` INT NOT NULL COMMENT 'game number',
  `left_score` INT NOT NULL COMMENT 'left score at event time',
  `right_score` INT NOT NULL COMMENT 'right score at event time',
  `serve_side` VARCHAR(10) NOT NULL COMMENT 'left/right',
  `payload_json` TEXT NOT NULL COMMENT 'event payload json',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_event_seq` (`match_id`, `event_seq`),
  KEY `idx_match_event_match_id` (`match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='match event';
