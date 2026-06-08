ALTER TABLE `tournament`
  ADD COLUMN `sport_type` TINYINT NOT NULL DEFAULT 0 AFTER `status`;

CREATE TABLE IF NOT EXISTS `tournament_team_member` (
  `id` VARCHAR(32) NOT NULL COMMENT 'primary id',
  `tournament_id` VARCHAR(32) NOT NULL COMMENT 'tournament id',
  `participant_id` VARCHAR(32) NOT NULL COMMENT 'participant id stored in player table',
  `name` VARCHAR(64) NOT NULL COMMENT 'member name',
  `jersey_number` INT NOT NULL COMMENT 'jersey number',
  `is_libero` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether libero',
  `is_captain` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether captain',
  `display_order` INT NOT NULL DEFAULT 0 COMMENT 'display order',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  KEY `idx_team_member_tournament_id` (`tournament_id`),
  KEY `idx_team_member_participant_id` (`participant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='volleyball team members';
