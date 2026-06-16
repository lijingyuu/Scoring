CREATE TABLE IF NOT EXISTS `global_theme_config` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `scene_key` VARCHAR(64) NOT NULL COMMENT 'global theme scene key',
  `theme_json` TEXT NOT NULL COMMENT 'theme json',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_global_theme_scene` (`scene_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='global theme config';
