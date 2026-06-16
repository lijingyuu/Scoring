CREATE TABLE IF NOT EXISTS `match_report_meta` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `match_id` VARCHAR(32) NOT NULL COMMENT 'match id',
  `meta_json` TEXT NOT NULL COMMENT 'report meta json',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_report_meta_match` (`match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='match report meta';
