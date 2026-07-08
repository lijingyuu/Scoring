---
name: workflow-retrospective
description: Reviews a recent coding collaboration to diagnose workflow friction, communication cost, tool failures, and durable process improvements. Use when the user asks to 复盘, improve workflow, analyze low efficiency, summarize repeated mistakes, or turn a difficult development session into better agent/user operating rules.
---

# Workflow Retrospective

## Quick Start

When this skill is invoked, produce a factual, Chinese-language retrospective of the recent task. The goal is not to defend the agent or flatter the user; the goal is to identify what slowed work down, why it took multiple rounds to correct, and what operating rules should change next time.

Use this output shape by default:

1. 事实时间线
2. 低效点与证据
3. 多轮纠错的根因
4. Agent 自己要改变的策略
5. 需要用户协助的策略
6. 已固化或建议固化到项目上下文的规则
7. 下次同类任务的检查清单

## Core Workflow

1. Reconstruct the task from observable facts.
   - List the user goal, key implementation steps, tests, failures, and user corrections.
   - Separate local code facts from assumptions, logs, screenshots, and inferred causes.
   - If a running service or UI state is involved, distinguish source code state from runtime state.

2. Classify friction points.
   Use these buckets when applicable:
   - Tool/sandbox friction: apply_patch failure, approval flow, filesystem restrictions, git index restrictions.
   - Shell/runtime friction: PowerShell execution policy, different sandbox user, command availability.
   - Encoding/file-edit friction: BOM, CRLF/LF churn, PowerShell ConvertTo-Json reformatting, Chinese mojibake.
   - Environment drift: old backend process, stale build, wrong port, frontend hitting a different service.
   - Repo hygiene: generated files tracked by Git, target/dist pollution, noisy status.
   - Reasoning mistakes: trusting an HTTP status without checking business response body, assuming user shell equals Codex shell.
   - Communication cost: questions asked too late, assumptions not stated, diagnostics not reduced to a probe.

3. Identify high-cost corrections.
   For each correction that took multiple turns, explain:
   - What the agent believed.
   - What evidence contradicted it.
   - What the user pointed out.
   - What diagnostic would have found it earlier.
   - What rule prevents recurrence.

4. Turn findings into operating rules.
   Split actions into:
   - Agent-owned defaults: commands, probes, edit methods, verification habits.
   - User-assisted setup: environment changes, approvals, IDE/service restart steps.
   - Project-context updates: AGENTS.md rules, README command fixes, .gitignore cleanup.

5. Be concrete.
   Prefer commands, file paths, and exact probes over generic advice. If recommending a rule, write it in a form that can be pasted into AGENTS.md.

## Case Study From This Project

Use this case as a reference pattern when relevant:

- apply_patch failed with `windows sandbox failed: orchestrator_helper_launch_canceled` and Windows error 1223. Treat this as Codex Windows sandbox/helper friction. If it repeats, switch to a safer fallback instead of repeatedly retrying.
- `npm test` failed because PowerShell blocked `npm.ps1`. The user had `RemoteSigned`, but Codex ran as `CodexSandboxOffline`, so the user-level policy did not apply. Rule: in this project, run front-end npm scripts as `npm.cmd test`, `npm.cmd run build:h5`, etc.
- PowerShell JSON/string edits caused risk: `ConvertTo-Json` reformatted route files and stdin to Node produced `????` for Chinese. Rule: for UTF-8/Chinese/JSON edits, prefer Node fs scripts or careful targeted replacements; verify by reading back.
- Backend logs showed HTTP `status=200`, but the response body still contained `code=500 No static resource...` because global exception handling wraps errors as HTTP 200. Rule: verify both HTTP status and business response body.
- The archive route appeared missing even after source changes. The correct probe was `PUT /api/v1/tournaments/__codex_probe__/archive`; if it returns `No static resource`, the running backend is stale or not the current source.
- `backend/target` was tracked by Git, causing generated class files to dirty the worktree after tests. Rule: generated outputs should be untracked and ignored.

## Retrospective Style

- Be direct and evidence-based.
- Do not over-apologize; name mistakes clearly and move to prevention.
- Praise is optional; concrete improvement is required.
- If the user corrected the agent, explicitly record what changed in the agent's model.
- End with the smallest next action, not a vague offer.

## Checklist Before Finalizing

- Did you cite concrete errors, commands, logs, or files?
- Did you distinguish source-code state from runtime state?
- Did you identify at least one agent-owned improvement?
- Did you identify whether any user action is truly needed?
- Did you propose durable context updates only when useful?
- Is the final answer short enough to be acted on, but detailed enough to prevent recurrence?