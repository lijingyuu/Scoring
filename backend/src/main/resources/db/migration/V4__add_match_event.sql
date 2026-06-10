CREATE TABLE IF NOT EXISTS `match_event` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `match_id` VARCHAR(32) NOT NULL COMMENT 'match id',
  `event_seq` INT NOT NULL COMMENT 'global event seq within match',
  `event_type` VARCHAR(32) NOT NULL COMMENT 'event type',
  `game_no` INT NOT NULL COMMENT 'game number',
  `left_score` INT NOT NULL COMMENT 'left score at event time',
  `right_score` INT NOT NULL COMMENT 'right score at event time',
  `serve_side` VARCHAR(10) NOT NULL COMMENT 'left/right',
  `payload_json` TEXT NOT NULL COMMENT 'event payload json',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_event_seq` (`match_id`, `event_seq`),
  KEY `idx_match_event_match_id` (`match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='match event';
