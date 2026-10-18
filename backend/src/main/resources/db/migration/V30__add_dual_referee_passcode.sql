-- V30: 裁判双级口令模型（方案 B，§9-4）
-- 设计：单一裁判密码裂解为两级——【现场执裁密码】(password_hash，既有列) 与
--       【赛务编排密码】(admin_password_hash，本次新增)；授权记录以 grant_role 区分
--       ADMIN(赛务编排管理员) / SCORER(现场执裁裁判)，ADMIN 天然包含 SCORER 能力。
-- 兼容：历史赛事 admin_password_hash 为 NULL => 未启用编排密码，编排权限仅创建者持有
--       （行为等价于方案 A，自然降级）；历史授权记录默认 SCORER，记分/战报能力无损，
--       同时自动回收其对出线覆盖/排签/重开已完赛比赛的渗透能力。
-- 原则：只加不改不删，一次性写入全部 DDL（Flyway checksum 校验要求首版即完整）。

ALTER TABLE `tournament_referee_config`
  ADD COLUMN `admin_password_hash` VARCHAR(128) NULL DEFAULT NULL
    COMMENT 'tournament admin referee password hash (BCrypt), NULL = admin passcode disabled'
    AFTER `password_hash`;

ALTER TABLE `tournament_referee_grant`
  ADD COLUMN `grant_role` VARCHAR(16) NOT NULL DEFAULT 'SCORER'
    COMMENT 'grant role: ADMIN (tournament ops) / SCORER (match officiating)'
    AFTER `user_id`;

ALTER TABLE `tournament_referee_grant`
  ADD INDEX `idx_referee_grant_tour_role` (`tournament_id`, `grant_role`);
