CREATE TABLE IF NOT EXISTS `match_theme_config` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `match_id` VARCHAR(32) NOT NULL COMMENT 'match id',
  `theme_json` TEXT NOT NULL COMMENT 'theme json',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_theme_match` (`match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='match theme config';
