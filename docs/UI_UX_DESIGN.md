# UI_UX_DESIGN.md — 设计系统与交互规范

> **用途**: 让 AI 每次写前端页面都符合"高级竞技风"设计语言。
> **关联**: [[ARCHITECTURE.md]] · [[BUSINESS_RULES.md]]

---

## 1. 色彩系统

### 1.1 全局品牌色

| 色名 | HEX | 用途 |
|------|-----|------|
| 深邃海军蓝 | `#1A2A3A` / `#13202D` | 全局背景、TabBar 背景 |
| 亮橙色 | `#FF8C00` | 强调色、选中态、TabBar 选中 |
| 纯白 | `#FFFFFF` | 主文字 |
| 置灰 | `#888888` / `#7C8A99` | 辅助文字、未选中 Tab |

### 1.2 计分板 14 色主题变量

每个比赛可以有独立的 Phone/Pad 两套配色。以下为默认值：

#### Phone 默认主题

| 变量 | 默认值 | 作用 |
|------|--------|------|
| `--theme-base` | `#003E50` | 页面主背景 |
| `--theme-base-deep` | `#00123A` | 深色区域背景 |
| `--theme-accent` | `#EC822F` | 按钮、高亮、强调元素 |
| `--theme-accent-ink` | `#194955` | 强调色上的文字 |
| `--captain` | `#2EC6FD` | 队长标识色（青蓝色，不复用橙色体系） |
| `--court-surface` | `#194955` | 球场底色 |
| `--right-score-accent` | `#F49227` | 右侧比分边框强调 |
| `--danger-accent` | `#F49227` | 危险操作按钮（退赛/重开） |
| `--text-strong` | `#EEFFE0` | 主白色文字 |
| `--surface-glass` | `#002F00` | 玻璃面板色 |
| `--shadow` | `#000000` | 阴影 |
| `--overlay-mask` | `#07121C` | 遮罩层 |
| `--court-slot-accent` | `#F49227` | 球场 slot 描边 |
| `--rotation-panel-surface` | `#005058` | 轮次面板背景 |

#### Pad 默认主题（关键差异）

| 变量 | 默认值 | 与 Phone 的差异 |
|------|--------|----------------|
| `--theme-base` | `#225F6E` | 更亮的蓝绿 |
| `--theme-accent` | `#F4A53A` | 琥珀色 → 暖橙 |
| `--captain` | `#739C69` | 绿色队长标识 |
| `--court-surface` | `#1E4F2B` | 深绿球场（手机端无球场底色） |
| `--right-score-accent` | `#52C41A` | 绿色边框 |
| `--danger-accent` | `#FF7A45` | 橙红危险色 |

### 1.3 语义色

| 语义 | 颜色 | 用途 |
|------|------|------|
| 成功/完赛 | `#00b894` / `#52C41A` | 胜者标记、完赛状态 |
| 危险/退赛 | `#d63031` / `#FF7A45` | 退赛按钮、删除操作 |
| 警告 | `#fdcb6e` | 提示信息 |
| 自由人高亮 | 橙色（动态） | 场上自由人号码额外橙色标记 |

---

## 2. 字体与排版

### 2.1 字号阶梯

```
场景          字号策略
────────────────────────────
页面标题      大号 bold
比分数字      最大号 (clamp动态)  — 记分板最核心信息
局分/队名     中等
操作按钮      中等 bold
辅助信息      小号 灰色
```

### 2.2 计分板核心原则

- **比分数值 > 队名 > 操作按钮** 的信息优先级
- 比分数字使用 `clamp()` 流体尺寸，确保在大屏和小屏上都清晰
- 队名允许截断（`text-overflow: ellipsis`），比分永远完整显示

---

## 3. 布局原则

### 3.1 记分板横屏三栏布局

```
┌──────────┬──────────────────────────────┬──────────┐
│ 左队名单  │        中央面板               │ 右队名单  │
│ (收缩)   │  局号/规则/比分/球场/操作      │ (收缩)   │
│ scroll   │  不允许滚动，优先保比分区       │ scroll   │
└──────────┴──────────────────────────────┴──────────┘
```

- **左右名单栏**: 允许缩窄（极限 ~130px），允许独立滚动
- **中央面板**: 不允许滚动，比分区和轮转区优先
- **空间紧张时**: 优先挪操作按钮位（如撤销/退赛放顶部），不缩比分字号

### 3.2 排球 lineup 页布局

```
Phone: 上站位 + 下名单（竖向流）
Pad:   左站位/自由人配置 + 右队员名单（双栏同屏工作台）
```

---

## 4. 交互底线（铁律）

### 4.1 数据刷新

> **所有 Tab 栏页面的数据刷新必须放在 `onShow` 生命周期里。**
>
> 不要只在 `onLoad`/`onMounted` 里拉数据——从记分板返回对阵图时，`onShow` 是确保晋级结果即时可见的唯一可靠时机。

### 4.2 敏感操作拦截

> **创建赛事、收藏赛事等操作前，必须先检查 `profileCompleted`。**
>
> 前端: `requireProfile()` → 未补全则弹 `ProfileGatePopup` 拦截
> 后端: `TournamentServiceImpl` 同样校验 `profileCompleted`（双保险）

### 4.3 防误触

```
- 所有关键操作 → useActionLock(350ms) 防重复点击
- "重新开始"按钮 → 10秒倒计时后才能点击
- "同步结算"按钮 → 操作前强制 flushPendingEvents()
- 弹窗/遮罩 → 使用 useDelayedTapGate(120ms) 防弹窗闪现误触
- 禁止全屏点击热区 → 保留防误触边缘
```

### 4.4 错误处理

```
- 网络失败 → uni.showToast + 保留现场数据（不丢失本地状态）
- 事件同步失败 → 不阻塞记分操作，结算前再追平
- Storage 写满 → 清空撤销历史后重试落盘
```

### 4.5 加载态

```
scoreboard.vue 入口层负责:
  - 数据加载中 → 显示 loading 状态
  - 加载失败 → 显示错误提示 + 重试按钮
  - 加载成功 → 根据 ctx.isTablet 分流到 Phone/Pad 组件
```

---

## 5. 设备适配

### 5.1 设备判定

```js
isTablet = Math.min(windowWidth, windowHeight) >= 720  // CSS 像素
```

### 5.2 六级尺寸带

| Band | 条件 | 典型设备 |
|------|------|----------|
| `phone` | 短边 < 720px | 手机 |
| `pad-portrait-sm` | 竖屏宽度 ≤ 820px | iPad mini 竖屏 |
| `pad-portrait-lg` | 竖屏宽度 > 820px | iPad Pro 竖屏 |
| `pad-landscape-sm` | 横屏宽度 ≤ 1228px | iPad 横屏 |
| `pad-landscape-md` | 1228–1400px | iPad Pro 11" 横屏 |
| `pad-landscape-lg` | > 1400px | iPad Pro 12.9" 横屏 |

每个 band 通过 `pageClassNames` 注入 CSS 类，覆盖 CSS 变量。

### 5.3 H5 竖屏预览

H5 端在竖屏设备打开横屏记分板时 → CSS `transform: scale()` 将 1280×720 设计尺寸等比缩放适配视口。

### 5.4 Phone vs Pad 视觉差异

| 维度 | Phone | Pad |
|------|-------|-----|
| 球场渲染 | 简单矩形 + 玻璃边框 | 完整球场底色 + 白色边线 + 球网 + 进攻线伪元素 |
| 阴影 | 无面板阴影 | `box-shadow: 0 14px 34px` |
| 队长选择 | 3 列网格 | 4 列网格 (pad-landscape-lg) |
| 编队面板 | 纵向排列 | 横向卡片 |

---

## 6. CSS 架构

### 6.1 核心原则

1. **全 `clamp()` 流体尺寸**: 间距、字号、圆角使用 `clamp(min, preferred, max)` 基于 `vmin`
2. **CSS 自定义属性**: 所有颜色通过 `--theme-*` 和 `--*-rgb` 驱动，支持运行时动态修改
3. **Phone/Pad 共享模板**: 模板 HTML 几乎相同，差异通过 CSS 类 `.is-tablet` 覆盖
4. **避免硬编码 px**: 除 `border: 1px` 和极小值外不使用固定像素

### 6.2 变量层级

```
:root 默认（Phone 端）
  └── .is-tablet 覆盖（Pad 端更大字体/间距）
      ├── .pad-landscape-sm 微调
      ├── .pad-landscape-md 微调
      └── .pad-landscape-lg 微调
  └── @media (max-width: 1400px) 回退
  └── @media (max-width: 1100px) 回退
```

### 6.3 主题存储层级

```
localStorage (per-device key)  ← 最快
  ↓ fallback
Hard-coded defaults             ← 兜底
```

> 后端 `theme-config` 接口已废弃，当前配色以本地设备存储和前端默认值为准。

---

## 7. 组件模式

### 7.1 比赛卡片

- 显示: 双方名称、比分（如有）、状态标签
- 已完赛: 胜者橙色高亮 + 粗体，显示 `scoreDisplay`
- 待赛/进行中: 双方名称，可点击进入记分板
- 轮空: 自动标记"已完赛"

### 7.2 对阵图

- 横向 `<scroll-view>`，按 `roundNum` 分组
- 每列=一轮，列间连线（CSS 伪元素 / SVG）
- 点击比赛卡片 → 进入记分板

### 7.3 名单列表

- 队员列表使用 `<scroll-view>`，允许独立滚动
- 在场队员: 高亮标识
- 场上队长: 青蓝色外框 + 名字（不复用橙色体系）
- 自由人: 号码橙色标记
- 场下替补: 无特殊边框/颜色

### 7.4 弹窗/遮罩

- 全屏遮罩背景 + 底部确认按钮稳定露出
- 队长选择: 固定两行三列布局（第一行 4/3/2，第二行 5/6/1）
- 所有弹窗使用 `useDelayedTapGate` 防止闪现误触

---

## 8. 暗色主题全局设定

```
TabBar:
  color: #7C8A99 (未选中)
  selectedColor: #FF8C00 (选中)
  backgroundColor: #162434

全局背景: #13202D
全局 navigationStyle: custom
```

---

## 9. 踩坑记录（避免重复犯错）

| # | 问题 | 修复 |
|---|------|------|
| 1 | 微信 `<button>` 硬编码 `min-width: 120rpx` | 改用 `<view>` 实现步进器按钮 |
| 2 | 撤销历史套娃导致 Storage 10MB 溢出 | `pushHistory` 不带 `historyStack`，上限 40 条 |
| 3 | `reactive()` 包裹 composable 的 ref 解包 | `scoreboard.vue` 使用 `reactive(useScoreboard())` |
| 4 | 微信开发者工具看错构建产物目录 | 调试统一看 `dist/dev/mp-weixin`，非 `dist/build` |
| 5 | displaySideSwapped 显示映射导致语义错位 | 改用 `screenLeftParticipantSide` 真实交换状态 |
| 6 | 轮次填写页布局"像两列半" | 改成固定两行三列 grid: 第一行 4/3/2，第二行 5/6/1 |
| 7 | 队长候选人流式布局在 Pad 上 5+1 错乱 | 改成固定两行三列 grid |
