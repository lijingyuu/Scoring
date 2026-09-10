-- PC 网页扫码登录临时会话表
-- 票据生命周期：CREATED(出码) -> SCANNED(小程序上报) -> CONFIRMED(确认授权) -> CONSUMED(PC 取走 token)
-- EXPIRED 为虚拟态（NOW() > expire_time 即判定），不落库
CREATE TABLE `web_login_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `ticket` VARCHAR(32) NOT NULL COMMENT 'PC扫码登录票据(32位hex,即小程序码scene)',
  `status` VARCHAR(16) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED已出码/SCANNED已扫码/CONFIRMED已确认/CONSUMED已取走token',
  `user_id` VARCHAR(32) DEFAULT NULL COMMENT '确认授权的用户id',
  `expire_time` DATETIME NOT NULL COMMENT '过期时间(出码+3分钟)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket` (`ticket`),
  KEY `idx_web_login_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PC网页扫码登录临时会话';
