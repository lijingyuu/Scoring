CREATE TABLE IF NOT EXISTS `match_runtime_state` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `match_id` VARCHAR(32) NOT NULL COMMENT 'match id',
  `schema_version` INT NOT NULL DEFAULT 1 COMMENT 'state json schema version',
  `last_event_seq` INT NOT NULL DEFAULT 0 COMMENT 'event seq watermark this state covers',
  `current_game_no` INT NOT NULL DEFAULT 1 COMMENT 'current game number',
  `left_score` INT NOT NULL DEFAULT 0 COMMENT 'current game left score',
  `right_score` INT NOT NULL DEFAULT 0 COMMENT 'current game right score',
  `serve_side` VARCHAR(10) NOT NULL DEFAULT 'left' COMMENT 'current serve side left/right',
  `state_json` TEXT NOT NULL COMMENT 'validated runtime state snapshot',
  `updated_by` VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'last writer user id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_runtime_state_match` (`match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='match runtime state checkpoint';
