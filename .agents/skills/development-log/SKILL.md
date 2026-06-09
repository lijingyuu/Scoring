---
name: development-log
description: Writes or updates the repo's DevelopmentLog.md in a compact self-facing development diary style, extracting only meaningful implementation context from the current collaboration. Use when the user asks to summarize progress, append a 开发日志, update DevelopmentLog.md, or capture current coding context into the project log.
---

# Development Log

## Quick Start

当用户要你把“当前这轮合作里真正有价值的开发上下文”整理进 `DevelopmentLog.md` 时，按下面流程做：

1. 先读取现有 `DevelopmentLog.md`，继承它已经确认下来的风格。
2. 只提炼高价值内容：关键决策、反复讨论后修正的点、已实现功能、重要 bug 与修复、当前阶段判断。
3. 直接更新 `DevelopmentLog.md`，不要顺手新建别的总结文档。

详细写法见 [REFERENCE.md](REFERENCE.md)。

## Workflow

### 1. 收集内容

优先从当前上下文中提炼这些信息：

- 产品或架构方向怎么定下来的
- 用户反复纠正过的设计细节
- 已经实际落地的功能主链路
- 联调中暴露的真实问题和修复方式
- 当前项目所处阶段与后续原则

不要机械记录：

- 每一条命令
- 零碎试错过程
- 没有落地价值的闲聊
- 过长的测试输出

### 2. 写作风格

- 全文用中文
- 语气是“自己回看用的开发日记”，不是正式汇报
- 保留关键细节，但不要写成一大坨流水账
- 重点保留“我们反复讨论后修改过什么”
- 不要把简单问题复杂化

### 3. 文件格式

- 如果文件不存在，就创建 `DevelopmentLog.md`
- 如果使用硬 Tab 缩进条目，文件顶部保留 `<!-- markdownlint-disable -->`
- 使用日期二级标题，例如 `## 2026-06-09 排球模块第一阶段`
- 正文以短段落为主，必要时插入带缩进的 `-` 条目
- 不要空太多行，但也不要把所有内容挤成一整坨

### 4. 更新策略

- 默认追加新日志，不要无故重写旧日志
- 如果用户正在反复调整同一段日志样式，可以按要求直接微调排版
- 修改时优先保留用户已经确认过的版本，只动必要部分

### 5. 完成后

完成后简短说明：

- 这次写进了什么
- 是否还受限于权限或文件位置
- 如果有风格风险，明确指出
