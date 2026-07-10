CREATE TABLE IF NOT EXISTS `team_match_item` (
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
