-- V28: 手写分组（小组赛+淘汰赛，tournamentType=1）——扩展 draw_mode 语义
-- 设计：0=auto（自动：type0 自动抽签 / type1 种子蛇形+shuffle 分组）；
--       1=manual-bracket（type0 手写签表，创建时按 knockout_slot_order 显式给位，轮空=空槽位）；
--       2=manual-groups（type1 手写分组，创建时按 groups 显式分组，跳过蛇形，允许组间不均；
--         仅 tournament_type=1 允许 2）。
--       首场小组赛开赛前允许创建者在网页端整体重提交分组（group-assignments 接口）。
-- 原则：仅注释/语义扩展，无类型与数据变化（TINYINT 足以承载 0/1/2）。

ALTER TABLE `tournament_division`
  MODIFY COLUMN `draw_mode` TINYINT NOT NULL DEFAULT 0 COMMENT 'draw mode: 0=auto, 1=manual bracket (type0), 2=manual groups (type1)' AFTER `knockout_generated`;
