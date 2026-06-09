CREATE TABLE IF NOT EXISTS `match_lineup_config` (
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
