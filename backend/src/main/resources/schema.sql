CREATE DATABASE IF NOT EXISTS `scoring_mvp` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `scoring_mvp`;

DROP TABLE IF EXISTS `match_record`;
DROP TABLE IF EXISTS `player`;
DROP TABLE IF EXISTS `tournament`;

CREATE TABLE `tournament` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键ID',
  `name` VARCHAR(128) NOT NULL COMMENT '赛事名称',
  `location` VARCHAR(255) DEFAULT NULL COMMENT '比赛地点',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0-未开始,1-进行中,2-已结束',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛事表';

CREATE TABLE `player` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键ID',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT '关联赛事ID',
  `name` VARCHAR(64) NOT NULL COMMENT '选手名称',
  `seed_rank` INT DEFAULT NULL COMMENT '预留种子序号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_player_tournament_id` (`tournament_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选手表';

CREATE TABLE `match_record` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键ID',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT '赛事ID',
  `round_num` INT NOT NULL COMMENT '轮次',
  `left_player_id` VARCHAR(32) DEFAULT NULL COMMENT '左侧选手ID',
  `right_player_id` VARCHAR(32) DEFAULT NULL COMMENT '右侧选手ID',
  `score_display` VARCHAR(255) DEFAULT NULL COMMENT '比分显示,例如:21:19, 15:21',
  `winner_id` VARCHAR(32) DEFAULT NULL COMMENT '获胜方ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0-待赛,1-进行中,2-已完赛,3-退赛异常',
  `next_match_id` VARCHAR(32) DEFAULT NULL COMMENT '下一场比赛ID(决赛为空)',
  `next_match_slot` VARCHAR(10) DEFAULT NULL COMMENT '晋级槽位:left/right(决赛为空)',
  PRIMARY KEY (`id`),
  KEY `idx_match_tournament_id` (`tournament_id`),
  KEY `idx_match_next_match_id` (`next_match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对阵记录表';
