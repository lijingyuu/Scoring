-- V24: 自定义多项团体赛（team_match_template = 3）子项配置表
-- 设计：支持 3 项 / 5 项 / 7 项自定义单打与双打项目组合（S/D/MS/WS/MD/WD/XD）；
--       每个自定义多项赛事的子项配置与 tournament 1:N 关联，按 display_order 顺序映射为比赛子项目；
-- 原则：只加不改不删，遵循全库单数表名约定与索引规范。

CREATE TABLE IF NOT EXISTS `tournament_custom_item` (
  `id`            VARCHAR(32)  NOT NULL COMMENT 'primary id',
  `tournament_id` VARCHAR(32)  NOT NULL COMMENT 'tournament id',
  `display_order` INT          NOT NULL COMMENT '1-based display order',
  `item_code`     VARCHAR(16)  NOT NULL COMMENT 'generated unique item code, e.g. MS_1 / XD_3',
  `item_type`     VARCHAR(8)   NOT NULL COMMENT 'item type: S/D/MS/WS/MD/WD/XD',
  `item_name`     VARCHAR(32)  NOT NULL COMMENT 'display name, e.g. 男单 / 男单1',
  `player_count`  INT          NOT NULL COMMENT '1=singles 2=doubles',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_custom_item_order` (`tournament_id`, `display_order`),
  KEY `idx_tournament_custom_item_tournament_id` (`tournament_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='custom multi-event team match item config (team_match_template=3)';
