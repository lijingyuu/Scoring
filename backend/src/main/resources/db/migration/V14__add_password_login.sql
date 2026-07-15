ALTER TABLE `app_user`
  MODIFY COLUMN `openid` VARCHAR(64) NULL COMMENT 'wechat openid',
  ADD COLUMN `username` VARCHAR(64) NULL COMMENT 'web login username' AFTER `openid`,
  ADD COLUMN `password_hash` VARCHAR(255) NULL COMMENT 'web login password hash' AFTER `username`,
  ADD UNIQUE KEY `uk_user_username` (`username`);
