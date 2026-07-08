---
name: sandbox-guard
description: Prevents sandbox and permission-denied flows from corrupting the workspace by forcing checkpointed, serialized execution with explicit fallback handling. Use when a task may trigger require_escalated, approval prompts, auto_review rejection, sandbox denial, or when mixed parallel work could leave code edits in a stale or inconsistent state.
---

# Sandbox Guard

## Quick start

When a task may require escalation, switch to guarded mode before running the risky command.

1. Classify the next actions as `safe-read`, `safe-write`, or `risky`.
2. If any risky action is next, stop parallel work and create a checkpoint.
3. Run at most one risky command at a time.
4. If approval is denied or the command fails because of sandboxing, abandon that branch, resync the touched files, and continue with a fallback path.

## Risk classification

Treat these as `risky`:

- Any command using `require_escalated`
- Commands likely to write outside the workspace
- GUI launches
- Network-dependent installs or downloads
- Commands already suspected to trip `auto_review`
- Validation or build steps known to behave differently under sandbox restrictions

Treat these as `safe-read`:

- Reading files
- Searching code
- Inspecting diffs
- Static reasoning that does not modify the workspace

Treat these as `safe-write`:

- Local edits fully inside the writable workspace
- Small deterministic changes that do not depend on an approval result

## Guarded mode rules

- Never place a risky command inside `multi_tool_use.parallel`.
- Never continue editing code based on assumptions that depend on an approval result you do not have yet.
- If safe work is independent, finish it to a stable checkpoint first.
- If the risky command gates the rest of the task, request approval first and wait for the result before continuing that branch.
- Prefer one escalation attempt at a time.

## Checkpoint procedure

Before any risky command, record:

- The files already touched
- The files likely to be touched next
- The current `git diff --name-only`
- The exact purpose of the escalation
- At least one fallback path if the escalation is denied

The checkpoint is not for rollback. It is for fast resync after failure.

## On denial or sandbox failure

1. Stop the escalation branch immediately.
2. Assume the risky command did not succeed unless the output proves otherwise.
3. Re-read every touched file since the checkpoint.
4. Re-check the relevant diff before making new edits.
5. Choose exactly one fallback path and continue from there.
6. Tell the user which fallback path is now in effect.

## Fallback options

Use one of these:

- Narrow the command to a sandbox-safe version
- Replace the blocked step with targeted file inspection
- Replace the blocked step with smaller local validation
- Ask the user to run the blocked step locally
- Postpone the blocked validation and safely finish the remaining code work

## Recovery rules

- Do not repair forward from stale assumptions.
- Do not mix retries with new edits until the workspace has been resynced.
- Do not resume parallel reads or edits until the fallback path is stable again.
- If a denied escalation invalidates the active plan, restate the new plan briefly before continuing.

## Common examples

- Build blocked: run smaller static checks or inspect config and output paths instead.
- Test blocked: run narrower tests that stay inside the workspace.
- GUI blocked: prepare the exact path, command, and expected result for the user instead of guessing.
- Dist write blocked: avoid continuing with generated-file assumptions until the actual workspace state is re-read.

## Success criteria

This skill is successful when a denied or blocked permission step causes a clean branch switch, not a messy repair cycle.
