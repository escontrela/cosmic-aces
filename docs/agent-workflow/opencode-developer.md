# OpenCode invocation prompt — Cosmic Aces Developer

Use this message as the prompt for a scheduled/non-interactive OpenCode run after configuring OpenCode and authenticating its Linear MCP connection.

```text
Use the cosmic-aces-developer skill. Work in the Cosmic Aces repository and use the `cosmic-aces-linear` Linear connection, workspace `cosmic-aces`, and team `Cosmic-aces` as the source of truth. Never use the generic `Linear` connection or `LastMoveChess`. No project is currently configured; preserve the issue's project field as-is.

Implement at most one eligible, unblocked child issue under an active root per invocation. Read the parent, siblings, dependency links, repository instructions, shared Linear workflow, Tech Lead analysis, and the child’s detailed implementation proposal first. Inspect the actual checkout and treat the proposal as the default route. You may correct a technical plan that is stale, incompatible with the codebase, or needlessly risky while preserving every PO acceptance criterion. Before coding, comment on the child with the adjustment, repository evidence, and acceptance mapping. If it would change player-visible behavior, scope, or PO criteria, stop and ask the Tech Lead/PO. Preserve existing user changes. Create or reuse the single task branch for the root issue, implement, validate, make one focused commit, and move only that child to the team's semantic review state with commit and verification evidence. Do not mark it Done; Codex must verify it before the next sequential child.

When all children are Done, perform final validation, push the branch, open one PR against the repository's actual default branch, link it in the root issue, and move the root to In Review. Do not merge the PR or close Linear issues. If no work is eligible, make no changes. If there is a blocker, preserve the work and report it precisely.
```

`opencode run` supports non-interactive prompt execution. Schedule it from the host (for example, macOS `launchd`) with a single-instance lock and the repository working directory. Choose the polling interval during OpenCode setup; 15–30 minutes is a reasonable starting point. This file is the prompt contract, not an installed scheduler.
