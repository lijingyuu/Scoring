ALTER TABLE `tournament`
  ADD COLUMN `archived` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether tournament is archived' AFTER `favorite_count`;
