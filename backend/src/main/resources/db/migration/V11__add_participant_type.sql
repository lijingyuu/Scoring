ALTER TABLE `tournament`
  ADD COLUMN `participant_type` TINYINT NOT NULL DEFAULT 0 COMMENT '0-individual, 1-team' AFTER `sport_type`;

UPDATE `tournament`
SET `participant_type` = 1
WHERE `sport_type` = 1;

ALTER TABLE `tournament_team_member`
  MODIFY COLUMN `jersey_number` INT NULL COMMENT 'jersey number';
