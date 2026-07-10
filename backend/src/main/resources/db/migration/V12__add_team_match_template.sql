ALTER TABLE `tournament`
  ADD COLUMN `team_match_template` TINYINT NOT NULL DEFAULT 0 COMMENT '0-none, 1-sudirman-5, 2-reserved, 3-reserved' AFTER `participant_type`;

UPDATE `tournament`
SET `team_match_template` = 1
WHERE `sport_type` = 0 AND `participant_type` = 1;
