CREATE TABLE IF NOT EXISTS `tournament_ranking_config` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `config_version` INT NOT NULL DEFAULT 1 COMMENT 'configuration version',
  `config_json` TEXT NOT NULL COMMENT 'ordered ranking criteria JSON',
  `locked_at` DATETIME DEFAULT NULL COMMENT 'locked after first finished group match',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tournament_ranking_config_tournament` (`tournament_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tournament ranking configuration';
