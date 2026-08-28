ALTER TABLE `match_record`
  ADD COLUMN `locked_by_user_id` VARCHAR(32) DEFAULT NULL COMMENT 'current match lock holder user id' AFTER `retired_side`,
  ADD COLUMN `lock_token` VARCHAR(64) DEFAULT NULL COMMENT 'current match lock session token' AFTER `locked_by_user_id`,
  ADD COLUMN `lock_expire_time` DATETIME DEFAULT NULL COMMENT 'current match lock expire time' AFTER `lock_token`,
  ADD KEY `idx_match_lock_expire_time` (`lock_expire_time`);
