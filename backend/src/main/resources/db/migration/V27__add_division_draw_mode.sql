-- V27: 手写签表（线下抽签录入）——组别签表模式标记
-- 设计：draw_mode 0=自动抽签（现状，创建时 shuffle），1=手写签表（创建时按 knockout_slot_order
--       显式给位，轮空=空槽位）；仅 tournament_type=0（纯淘汰赛）允许 1。
--       首场比赛开赛前（零开赛）允许创建者在网页端整体重提交签位。
-- 原则：只加不改不删，遵循全库单数表名约定。

ALTER TABLE `tournament_division`
  ADD COLUMN `draw_mode` TINYINT NOT NULL DEFAULT 0 COMMENT 'draw mode: 0=auto draw, 1=manual bracket' AFTER `knockout_generated`;
