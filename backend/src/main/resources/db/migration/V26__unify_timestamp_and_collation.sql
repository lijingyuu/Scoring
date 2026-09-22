-- V26: 时间列类型收敛 + 连接层 collation 钉死（P3 尾巴收尾）
-- 背景一：V4（match_event）与 V18（tournament_qualification_override）建表时把 create_time 写成了
--         TIMESTAMP，而其余表统一是 DATETIME。MySQL 的 TIMESTAMP 上限是 2038-01-19（2038 问题）。
-- 背景二：较新的迁移只写 DEFAULT CHARSET=utf8mb4、不写 COLLATE，换库后新表列会继承库级默认排序规则
--         （MySQL 8 为 utf8mb4_0900_ai_ci），与旧表（本机 utf8mb4_general_ci）比较时复现 V23 式 error 1267。
-- 做法：本迁移只把上述两列收敛为 DATETIME（保留 DEFAULT CURRENT_TIMESTAMP，MySQL 8 的 DATETIME 支持该默认值）；
--       collation 不逐表改 DDL，改由连接层钉死（application.yml 的 JDBC URL 加 connectionCollation=utf8mb4_general_ci），
--       一次覆盖所有新表，改动面更小。
-- 影响面：MODIFY COLUMN 会重建这两张表（表不大，首次执行有秒级成本）；DATETIME 无 2038 上限，语义与其余表一致。

ALTER TABLE `match_event`
  MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time';

ALTER TABLE `tournament_qualification_override`
  MODIFY COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time';
