# DATABASE.md — 数据字典与状态枚举

> **用途**: AI 写后端 SQL 和前端条件渲染（`v-if`）时的"圣经"。所有枚举值以此文档为准，禁止猜测。
> **关联**: [[ARCHITECTURE.md]] · [[BUSINESS_RULES.md]]

---

## 1. 数据库概览

- **数据库名**: `scoring_mvp`
- **字符集**: `utf8mb4`
- **引擎**: InnoDB
- **迁移工具**: Flyway（8个迁移版本，V1 ~ V8）
- **ID 策略**: MyBatis-Plus `ASSIGN_ID`（雪花算法，19位数字，**以字符串传输**）

---

## 2. 表结构

### 2.1 `app_user` — 用户

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | 雪花 ID |
| `openid` | VARCHAR(64) | UNIQUE | 微信 openid |
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
| `group_size` | INT | | 每组人数（小组赛时有效） |
| `knockout_slots` | INT | | 淘汰赛名额（2的幂） |
| `qualifiers_per_group` | INT | | 每组出线人数（1或2） |
| `current_stage` | TINYINT | DEFAULT 1 | **见 §3.4** |
| `knockout_generated` | TINYINT(1) | DEFAULT 1 | 是否已生成淘汰赛对阵 |
| `best_of` | INT | DEFAULT 3 | 总局数（3/5） |
| `games_to_win` | INT | DEFAULT 2 | 赢得局数阈值 |
| `points_to_win` | INT | DEFAULT 21 | 每局目标分（羽毛球21/排球25） |
| `enable_deuce` | TINYINT(1) | DEFAULT 1 | 是否启用追分 |
| `cap_point` | INT | DEFAULT 30 | 单局封顶分 |
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
| `jersey_number` | INT | NOT NULL | 球衣号码（队内唯一） |
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

### 2.6 `match_event` — 比赛事件（排球）

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

### 2.7 `match_lineup_config` — 阵容配置（排球每局）

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

### 2.8 `match_theme_config` — 比赛配色主题

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `match_id` | VARCHAR(32) | UNIQUE | |
| `theme_json` | TEXT | NOT NULL | 14色主题 JSON（含 phoneTheme/padTheme） |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

> ⚠️ 当前已废弃：配色改为前端硬编码直选，后端接口已注释。

### 2.9 `global_theme_config` — 全局配色主题

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `scene_key` | VARCHAR(64) | UNIQUE | 场景键 |
| `theme_json` | TEXT | NOT NULL | 14色主题 JSON |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

### 2.10 `match_report_meta` — 比赛报告元数据

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `match_id` | VARCHAR(32) | UNIQUE | |
| `meta_json` | TEXT | NOT NULL | 报告元数据 JSON（matchTimeText / chiefRefereeName / assistantRefereeName 等） |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

### 2.11 `tournament_favorite` — 收藏关联

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `user_id` | VARCHAR(32) | UNIQUE组合 | |
| `tournament_id` | VARCHAR(32) | UNIQUE组合, IDX | |
| `create_time` | DATETIME | | |

**唯一约束**: `(user_id, tournament_id)`

### 2.12 `tournament_referee_config` — 裁判密码配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `tournament_id` | VARCHAR(32) | UNIQUE | |
| `password_hash` | VARCHAR(128) | NOT NULL | 密码哈希 |
| `create_time` | DATETIME | | |
| `update_time` | DATETIME | | ON UPDATE |

### 2.13 `tournament_referee_grant` — 裁判授权

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | VARCHAR(32) | PK | |
| `tournament_id` | VARCHAR(32) | UNIQUE组合 | |
| `user_id` | VARCHAR(32) | UNIQUE组合, IDX | 被授权用户 |
| `create_time` | DATETIME | | |

**唯一约束**: `(tournament_id, user_id)`

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

### 3.8 方向/位置枚举

| 字段 | 可选值 | 说明 |
|------|--------|------|
| `winner_side` / `serve_side` / `retired_side` | `"left"` / `"right"` | 参与者侧（原始主队=left，客队=right） |
| `next_match_slot` | `"left"` / `"right"` | 晋级后在下一场的槽位 |

---

## 4. 表关系图

```
tournament (1) ─────< player (N)          ← 参赛选手/队伍
    │                    │
    │                    └──< tournament_team_member (N)  ← 队员详情（仅排球）
    │
    ├──< tournament_favorite (N)          ← 用户收藏
    ├──< tournament_referee_config (1)    ← 裁判密码
    ├──< tournament_referee_grant (N)     ← 裁判授权
    │
    └──< match_record (N)                ← 比赛记录
            │
            ├──< match_event (N)          ← 比赛事件（仅排球）
            ├──< match_lineup_config (N)  ← 阵容配置（仅排球）
            ├──< match_theme_config (1)   ← 比赛配色
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
```
