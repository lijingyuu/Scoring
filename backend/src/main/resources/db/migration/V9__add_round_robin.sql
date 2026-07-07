ALTER TABLE `tournament`
  ADD COLUMN `round_robin_rounds` TINYINT NOT NULL DEFAULT 1 COMMENT '1=single round robin, 2=double round robin' AFTER `cap_point`;
