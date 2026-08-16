# DATABASE.md — 数据字典与状态枚举

> **用途**: AI 写后端 SQL 和前端条件渲染（`v-if`）时的"圣经"。所有枚举值以此文档为准，禁止猜测。
> **关联**: [[ARCHITECTURE.md]] · [[BUSINESS_RULES.md]]

---

## 1. 数据库概览

- **数据库名**: `scoring_mvp`
- **字符集**: `utf8mb4`
- **引擎**: InnoDB
- **迁移工具**: Flyway（19个迁移版本，V1 ~ V19）
- **ID 策略**: MyBatis-Plus `ASSIGN_ID`（雪花算法，19位数字，**以字符串传输**）
- **表数量**: 17 张（`match_theme_config` 为历史残留实体/Mapper，`global_theme_config` 仅有建表脚本，无有效实体/Mapper/API）

---

## 2. 表结构

### 2.1 `app_user` — 用户

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | 雪花 ID |
| `openid` | VARCHAR(64) | UNIQUE, 可为空 | 微信 openid；Web 账号可为空（V14 调整） |
| `username` | VARCHAR(64) | UNIQUE | Web 登录用户名（V14 新增） |
| `password_hash` | VARCHAR(255) | | Web 登录密码哈希（V14 新增） |
| `nickname` | VARCHAR(64) | | 昵称 |
| `avatar_url` | VARCHAR(512) | | 头像 URL |
| `profile_completed` | TINYINT(1) | DEFAULT 0 | 是否已完善资料 |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

### 2.2 `tournament` — 赛事

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | 雪花 ID |
| `name` | VARCHAR(128) | NOT NULL | 赛事名称 |
| `location` | VARCHAR(255) | | 地点 |
| `status` | TINYINT | DEFAULT 0 | **见 §3.1** |
| `sport_type` | TINYINT | DEFAULT 0 | **见 §3.2** |
| `tournament_type` | TINYINT | DEFAULT 0 | **见 §3.3** |
| `participant_type` | TINYINT | DEFAULT 0 | **见 §3.8**（V11 新增） |
| `team_match_template` | TINYINT | DEFAULT 0 | **见 §3.9**（V12 新增） |
| `group_size` | INT | | 每组人数（小组赛时有效） |
| `knockout_slots` | INT | | 淘汰赛名额（2的幂） |
| `knockout_rounds` | INT | | 淘汰赛轮数（V15 新增） |
| `qualifiers_per_group` | INT | | 每组出线人数（1或2） |
| `round_robin_rounds` | TINYINT | DEFAULT 1 | 循环赛轮数：1=单循环，2=双循环（V9 新增） |
| `round_rule_enabled` | TINYINT(1) | DEFAULT 0 | 是否启用赛段规则（V15 新增） |
| `current_stage` | TINYINT | DEFAULT 1 | **见 §3.4** |
| `knockout_generated` | TINYINT(1) | DEFAULT 1 | 是否已生成淘汰赛对阵 |
| `best_of` | INT | DEFAULT 3 | 总局数（3/5） |
| `games_to_win` | INT | DEFAULT 2 | 赢得局数阈值 |
| `points_to_win` | INT | DEFAULT 21 | 每局目标分（羽毛球21/排球25） |
| `deciding_points_to_win` | INT | | 决胜局目标分（V15 新增，排球默认15） |
| `enable_deuce` | TINYINT(1) | DEFAULT 1 | 是否启用追分 |
| `cap_point` | INT | DEFAULT 30 | 单局封顶分（接力赛模式下复用为接力人数） |
| `third_place_enabled` | TINYINT(1) | DEFAULT 0 | 是否启用三四名决赛（V16 新增） |
| `third_place_best_of` | INT | | 三四名总局数（V16 新增） |
| `third_place_games_to_win` | INT | | 三四名获胜局数（V16 新增） |
| `third_place_points_to_win` | INT | | 三四名常规局目标分（V16 新增） |
| `third_place_deciding_points_to_win` | INT | | 三四名决胜局目标分（V16 新增） |
| `third_place_enable_deuce` | TINYINT(1) | | 三四名是否追分（V16 新增） |
| `third_place_cap_point` | INT | | 三四名封顶分（V16 新增） |
| `archived` | TINYINT(1) | DEFAULT 0 | 是否已归档（V10 新增） |
| `creator_user_id` | VARCHAR(32) | NOT NULL | 创建者 |
| `favorite_count` | INT | DEFAULT 0 | 收藏数 |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

### 2.3 `player` — 参赛选手/队伍

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | 羽毛球=选手ID，排球=队伍ID |
| `tournament_id` | VARCHAR(32) | NOT NULL, IDX | 所属赛事 |
| `name` | VARCHAR(64) | NOT NULL | 选手名/队伍名 |
| `seed_rank` | INT | | 种子排名（1-based） |
| `group_no` | INT | | 小组编号（小组赛） |
| `group_position` | INT | | 组内排名（1-based） |
| `create_time` | DATETIME | | |

> **注意**: 排球模式中，`player` 表的记录代表**队伍**，队员信息在 `tournament_team_member` 表。

### 2.4 `tournament_team_member` — 队员详情（仅排球）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `tournament_id` | VARCHAR(32) | IDX | 所属赛事 |
| `participant_id` | VARCHAR(32) | IDX | 所属队伍（player.id） |
| `name` | VARCHAR(64) | NOT NULL | 队员姓名 |
| `jersey_number` | INT | | 球衣号码（队内唯一，羽毛球团体赛可为空） |
| `is_libero` | TINYINT(1) | DEFAULT 0 | 是否为自由人 |
| `is_captain` | TINYINT(1) | DEFAULT 0 | 是否为队长 |
| `display_order` | INT | DEFAULT 0 | 显示排序 |
| `create_time` | DATETIME | | |

### 2.5 `match_record` — 比赛记录

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `tournament_id` | VARCHAR(32) | IDX | 所属赛事 |
| `round_num` | INT | NOT NULL | 轮次号（1-based） |
| `match_index` | INT | DEFAULT 0 | 轮内序号 |
| `stage_type` | TINYINT | DEFAULT 1 | **见 §3.5** |
| `group_no` | INT | | 小组编号（小组赛） |
| `left_player_id` | VARCHAR(32) | | 左侧选手/队伍 |
| `right_player_id` | VARCHAR(32) | | 右侧选手/队伍 |
| `score_display` | VARCHAR(255) | | 比分文本 "21:15,21:18" |
| `winner_id` | VARCHAR(32) | | 胜者 player.id |
| `left_game_wins` | INT | | 左侧赢得局数 |
| `right_game_wins` | INT | | 右侧赢得局数 |
| `game_scores` | TEXT | | 局分 JSON |
| `status` | TINYINT | DEFAULT 0 | **见 §3.6** |
| `next_match_id` | VARCHAR(32) | IDX | 下一场比赛 ID（淘汰赛晋级链） |
| `next_match_slot` | VARCHAR(10) | | 在下一场的位置 `"left"/"right"` |
| `retired_side` | VARCHAR(10) | | 弃权方 `"left"/"right"` |
| `match_role` | TINYINT | DEFAULT 0 | 比赛角色：0=普通，1=三四名（V16 新增） |
| `loser_next_match_id` | VARCHAR(32) | | 败者下一场（三四名决赛用，V16 新增） |
| `loser_next_match_slot` | VARCHAR(10) | | 败者在下一场的位置（V16 新增） |

### 2.6 `team_match_item` — 团体赛子项目（V13 新增）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | 雪花 ID |
| `match_id` | VARCHAR(32) | NOT NULL, IDX | 所属团体赛 |
| `tournament_id` | VARCHAR(32) | NOT NULL | 所属赛事 |
| `display_order` | INT | | 显示顺序 |
| `item_code` | VARCHAR(16) | NOT NULL | 项目编码（MS/WS/MD/WD/XD 或 R1..RN） |
| `item_name` | VARCHAR(64) | | 项目名称（男单/女单/…/第1段） |
| `player_count` | INT | | 每方上场人数（1=单打/2=双打/接力） |
| `left_member_ids_json` | VARCHAR(512) | | 左方出场队员 ID JSON 数组 |
| `right_member_ids_json` | VARCHAR(512) | | 右方出场队员 ID JSON 数组 |
| `child_match_id` | VARCHAR(32) | | 子比赛 ID（关联 match_record） |
| `winner_side` | VARCHAR(10) | | 胜方 `"left"/"right"` |
| `status` | TINYINT | DEFAULT 0 | 0=待赛, 1=进行中, 2=已完赛 |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

**唯一约束**: `(match_id, item_code)` — 同一团体赛的项目编码唯一

### 2.7 `match_event` — 比赛事件（排球）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `match_id` | VARCHAR(32) | IDX, UNIQUE组合 | |
| `event_seq` | INT | UNIQUE组合 | 全局事件序号（>0，从1递增） |
| `event_type` | VARCHAR(32) | NOT NULL | **见 §3.7** |
| `game_no` | INT | NOT NULL | 所在局号 |
| `left_score` | INT | NOT NULL | 事件时左侧得分 |
| `right_score` | INT | NOT NULL | 事件时右侧得分 |
| `serve_side` | VARCHAR(10) | NOT NULL | 发球方 `"left"/"right"` |
| `payload_json` | TEXT | NOT NULL | 事件负载 JSON |
| `create_time` | TIMESTAMP | | |

**唯一约束**: `(match_id, event_seq)` — 按序号的幂等 upsert

### 2.8 `match_lineup_config` — 阵容配置（排球每局）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `match_id` | VARCHAR(32) | UNIQUE组合 | |
| `game_no` | INT | UNIQUE组合 | 局号 |
| `left_court_json` | TEXT | NOT NULL | 左队场上6人 memberId 数组 |
| `right_court_json` | TEXT | NOT NULL | 右队场上6人 memberId 数组 |
| `left_middle_pair_indexes_json` | VARCHAR(64) | | 左队副攻对角位索引 |
| `right_middle_pair_indexes_json` | VARCHAR(64) | | 右队副攻对角位索引 |
| `left_libero1_id` | VARCHAR(32) | | 左队自由人1 |
| `left_libero2_id` | VARCHAR(32) | | 左队自由人2 |
| `right_libero1_id` | VARCHAR(32) | | 右队自由人1 |
| `right_libero2_id` | VARCHAR(32) | | 右队自由人2 |
| `serve_side` | VARCHAR(10) | NOT NULL | 发球方 `"left"/"right"` |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

**唯一约束**: `(match_id, game_no)`

### 2.9 `match_theme_config` — 比赛配色主题

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `match_id` | VARCHAR(32) | UNIQUE | |
| `theme_json` | TEXT | NOT NULL | 14色主题 JSON（含 phoneTheme/padTheme） |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

> ⚠️ 当前已废弃：配色改为前端硬编码直选，后端接口已注释；实体和 Mapper 暂时保留，不能据此推断接口可用。

### 2.10 `global_theme_config` — 全局配色主题（仅建表脚本）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `scene_key` | VARCHAR(64) | UNIQUE | 场景键 |
| `theme_json` | TEXT | NOT NULL | 14色主题 JSON |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

> 当前没有对应实体、Mapper 和有效 API，仅数据库 schema 中保留。

### 2.11 `match_report_meta` — 比赛报告元数据

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `match_id` | VARCHAR(32) | UNIQUE | |
| `meta_json` | MEDIUMTEXT | NOT NULL | 报告元数据 JSON（matchTimeText / chiefRefereeName / assistantRefereeName / 签名等；V19 扩容为 MEDIUMTEXT） |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

### 2.12 `tournament_favorite` — 收藏关联

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `user_id` | VARCHAR(32) | UNIQUE组合 | |
| `tournament_id` | VARCHAR(32) | UNIQUE组合, IDX | |
| `create_time` | DATETIME | | |

**唯一约束**: `(user_id, tournament_id)`

### 2.13 `tournament_referee_config` — 裁判密码配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `tournament_id` | VARCHAR(32) | UNIQUE | |
| `password_hash` | VARCHAR(128) | NOT NULL | 密码哈希 |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

### 2.14 `tournament_referee_grant` — 裁判授权

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `tournament_id` | VARCHAR(32) | UNIQUE组合 | |
| `user_id` | VARCHAR(32) | UNIQUE组合, IDX | 被授权用户 |
| `create_time` | DATETIME | | |

**唯一约束**: `(tournament_id, user_id)`

### 2.15 `tournament_round_rule` — 赛段规则（V15 新增）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `tournament_id` | VARCHAR(32) | NOT NULL, UNIQUE组合 | 所属赛事 |
| `stage_type` | TINYINT | NOT NULL, UNIQUE组合 | 0=小组赛，1=淘汰赛 |
| `round_num` | INT | NOT NULL, UNIQUE组合 | 小组赛固定0；淘汰赛为轮次号 |
| `best_of` | INT | NOT NULL | 总局数 |
| `games_to_win` | INT | NOT NULL | 获胜局数 |
| `points_to_win` | INT | NOT NULL | 常规局目标分 |
| `deciding_points_to_win` | INT | | 决胜局目标分 |
| `enable_deuce` | TINYINT(1) | NOT NULL | 是否启用追分 |
| `cap_point` | INT | NOT NULL | 封顶分 |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

**唯一约束**: `(tournament_id, stage_type, round_num)`

### 2.16 `tournament_ranking_config` — 小组排名模板配置（V17 新增）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | 雪花 ID |
| `tournament_id` | VARCHAR(32) | NOT NULL, UNIQUE | 所属赛事 |
| `config_version` | INT | DEFAULT 1 | 配置版本 |
| `config_json` | TEXT | NOT NULL | 有序排名指标 JSON（见 RankingConfig） |
| `locked_at` | DATETIME | | 首场小组赛结束后锁定时间 |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

**唯一约束**: `(tournament_id)`

### 2.17 `tournament_qualification_override` — 晋级资格覆盖（V18 新增）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | 雪花 ID |
| `tournament_id` | VARCHAR(32) | NOT NULL | 所属赛事 |
| `group_no` | INT | NOT NULL | 小组编号 |
| `rank_slot` | INT | NOT NULL | 出线名额槽位 |
| `player_id` | VARCHAR(32) | NOT NULL | 指定晋级的选手/队伍 |
| `operator_user_id` | VARCHAR(32) | NOT NULL | 操作者 |
| `create_time` | TIMESTAMP | | |

**唯一约束**: `(tournament_id, group_no, rank_slot)` 与 `(tournament_id, group_no, player_id)`

---

## 3. 枚举字典（状态码黑话本）

### 3.1 `tournament.status` — 赛事状态

| 值 | 含义 | 前端条件渲染 |
|----|------|-------------|
| `0` | 未开始 | 显示"待开始"标签 |
| `1` | 进行中 | 正常交互 |
| `2` | 已结束 | 锁定，不可修改 |

### 3.2 `tournament.sport_type` — 运动类型

| 值 | 含义 |
|----|------|
| `0` | 羽毛球 |
| `1` | 排球 |

### 3.3 `tournament.tournament_type` — 赛制类型

| 值 | 含义 |
|----|------|
| `0` | 纯淘汰赛 |
| `1` | 小组赛 + 淘汰赛 |
| `2` | 纯循环赛（单/双循环由 `round_robin_rounds` 决定） |

### 3.4 `tournament.current_stage` — 当前阶段

| 值 | 含义 |
|----|------|
| `0` | 小组赛阶段 |
| `1` | 淘汰赛阶段 |

### 3.5 `match_record.stage_type` — 比赛阶段类型

| 值 | 含义 |
|----|------|
| `0` | 小组赛 |
| `1` | 淘汰赛 |
| `2` | 团体赛子比赛 |

### 3.5b `team_match_item.status` — 团体赛子项目状态

| 值 | 含义 |
|----|------|
| `0` | 待赛（阵容已保存，子比赛未创建） |
| `1` | 进行中（子比赛已创建/进行中） |
| `2` | 已完赛 |

### 3.6 `match_record.status` — 比赛状态 ⚠️ 重要

| 值 | 含义 | 前端行为 |
|----|------|----------|
| `0` | 待赛 | 选手未就位，不可进入记分板 |
| `1` | 进行中 | 可进入记分板正常操作 |
| `2` | 已完赛 | 显示结果，可查看记录 |
| `3` | 退赛 | 一方弃权，显示退赛标记 |

### 3.7 `match_event.event_type` — 事件类型

| 值 | 含义 | payload 关键字段 |
|----|------|-----------------|
| `substitution` | 换人 | `{ side, outMemberId, inMemberId }` |
| `timeout` | 暂停 | `{ side }` |
| `captain_change` | 队长更换 | `{ side, captainMemberId, originalCaptainMemberId, source }` |
| `side_switch` | 换边 | `{ reason, screenLeftParticipantSide }` |
| `roster_snapshot` | 名单快照 | `{ leftMembers, rightMembers }` |
| `lineup_snapshot` | 阵容快照 | `{ left: {court,...}, right: {...}, serveSide }` |

### 3.8 `tournament.participant_type` — 参赛者类型（V11 新增）

| 值 | 含义 |
|----|------|
| `0` | 个人赛（羽毛球单打） |
| `1` | 团体赛（排球/羽毛球团体） |

### 3.9 `tournament.team_match_template` — 团体赛模板（V12 新增）

| 值 | 含义 |
|----|------|
| `0` | 无（非团体赛） |
| `1` | 苏迪曼杯式 5 项（MS/WS/MD/WD/XD） |
| `2` | 接力追分赛 |

### 3.10 `tournament.round_robin_rounds` — 循环赛轮数（V9 新增）

| 值 | 含义 |
|----|------|
| `1` | 单循环 |
| `2` | 双循环（主客各一场） |

### 3.11 方向/位置枚举

| 字段 | 可选值 | 说明 |
|------|--------|------|
| `winner_side` / `serve_side` / `retired_side` | `"left"` / `"right"` | 参与者侧（原始主队=left，客队=right） |
| `next_match_slot` | `"left"` / `"right"` | 晋级后在下一场的槽位 |

---

## 4. 表关系图

```
tournament (1) ─────< player (N)          ← 参赛选手/队伍
    │                    │
    │                    └──< tournament_team_member (N)  ← 队员详情
    │
    ├──< tournament_favorite (N)          ← 用户收藏
    ├──< tournament_round_rule (N)        ← 赛段规则
    ├──< tournament_ranking_config (1)    ← 排名模板配置
    ├──< tournament_qualification_override (N) ← 晋级资格覆盖
    ├──< tournament_referee_config (1)    ← 裁判密码
    ├──< tournament_referee_grant (N)     ← 裁判授权
    │
    └──< match_record (N)                ← 比赛记录
            │
            ├──< team_match_item (N)      ← 团体赛子项目（苏杯/接力）
            ├──< match_event (N)          ← 比赛事件（仅排球）
            ├──< match_lineup_config (N)  ← 阵容配置（仅排球）
            ├──< match_theme_config (1)   ← 比赛配色（已废弃）
            └──< match_report_meta (1)    ← 报告元数据

app_user (1) ─────< tournament_favorite (N)
app_user (1) ─────< tournament_referee_grant (N)
app_user (1) ───── tournament (creator_user_id)
```

---

## 5. AI 常见查询模板

```sql
-- 查询"进行中的比赛"
SELECT * FROM match_record WHERE status = 1;

-- 查询"纯淘汰赛且未结束的赛事"
SELECT * FROM tournament WHERE tournament_type = 0 AND status IN (0, 1);

-- 查询"排球赛事"
SELECT * FROM tournament WHERE sport_type = 1;

-- 查询"小组赛阶段的比赛"
SELECT * FROM match_record WHERE stage_type = 0;

-- 查询"已完赛的比赛"
SELECT * FROM match_record WHERE status = 2;

-- 查询某队所有队员
SELECT * FROM tournament_team_member WHERE participant_id = '<player_id>';

-- 查询某团体赛所有子项目
SELECT * FROM team_match_item WHERE match_id = '<match_id>' ORDER BY display_order;

-- 查询纯循环赛
SELECT * FROM tournament WHERE tournament_type = 2;

-- 查询团体赛赛事
SELECT * FROM tournament WHERE participant_type = 1 AND team_match_template IN (1, 2);

-- 查询某赛事的赛段规则
SELECT * FROM tournament_round_rule
WHERE tournament_id = '<tournament_id>'
ORDER BY stage_type, round_num;
```
