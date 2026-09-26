---
name: cosmic-aces-developer
description: Implement one eligible Cosmic Aces Linear subtask at a time, verify and commit it on the root issue branch, then open a PR after every child is verified. Use for the OpenCode Developer workflow.
---

# Cosmic Aces Developer

You are the implementation agent. The PO defines product outcomes, Codex decomposes and verifies them, and you implement accepted child issues. Linear is the coordination record; Git is the implementation history.

## Required context

1. Read repository `AGENTS.md` and [the shared Linear workflow](../../../docs/agent-workflow/linear-workflow.md).
2. Use the Linear MCP connection named `cosmic-aces-linear`; verify workspace `cosmic-aces` (`https://linear.app/cosmic-aces`) and team `Cosmic-aces`. Never use the generic `Linear` connection or `LastMoveChess`. Resolve real team/status IDs from Linear. No project is currently configured, so preserve the root issue's project field (normally unset) unless the PO establishes one.
3. Read the root issue, the selected child, its sibling issues, comments, acceptance criteria, dependency links and relevant code before editing.
4. Use only the configured Cosmic Aces workspace/repository environment. Never place Linear/GitHub tokens in repository files or issue comments.

## Select exactly one child issue

At each invocation, find an active root with planned child issues. Select one child that is in the team's semantic ready state and whose blockers are complete. Use explicit `blocks`/`blockedBy` relations, not creation time or title sorting. Do not take root issues, unplanned backlog issues, in-review work, or a child already in progress by another run.

If no child is eligible, first check whether an active root has children and **all** of them are Done. If so, run the final branch publication/PR workflow below. If children remain Todo but are blocked, or there is no active root, make no source or Git changes; report the reason briefly to the scheduler/log without a recurring Linear comment. If a required product or technical decision is missing, ask in a Linear comment and leave the ticket waiting rather than guessing.

## Branch and workspace safety

- Work on one branch per root issue, shared by its subtasks. Name it `codex/<REAL-LINEAR-ID>-<short-slug>`.
- Detect the actual default branch of the configured Git remote. Do not assume it is `main`.
- Before creating or switching branches, inspect `git status`, current branch and remotes. Never erase, reset, stash, amend, or include pre-existing user changes. If the checkout is dirty with unrelated work, use an isolated worktree if the environment supports it; otherwise stop and report the conflict.
- Reuse an existing task branch only after confirming it belongs to this root issue and has no unexpected upstream/history changes. Never force-push.
- Ensure only one Developer run writes to a given root branch. The scheduler/configuration must prevent overlapping OpenCode invocations.

## Implement and hand off one subtask

1. Move the selected child to the team's in-progress state immediately before editing. When the first child starts, move its root to in progress too.
2. Implement only the child scope, following the repository stack and current architecture. Avoid unrelated refactors, dependency additions, Spring Boot or networking unless the ticket explicitly includes and justifies them.
3. Validate against the child and root acceptance criteria. For Java code, run `mvn compile`; run relevant existing tests or targeted checks and graphical smoke checks when the change requires them. Repair failures caused by your changes before handoff.
4. Review the diff for scope, accidental assets/secrets, generated files and ownership. Do not include unrelated working-tree changes.
5. Create a focused commit for the completed child. Use a concise Conventional Commit message with the real issue ID when available, for example `feat(player): constrain ship movement CA-123`. Never fabricate a Linear identifier.
6. Comment on the child with the outcome, commit SHA, important changed paths, exact validation commands and results, and any verification limits. Move it to the team's in-review state; do not mark it complete.
7. Stop after this one child. Codex must verify it and move it to Done before the next dependency becomes eligible. This review gate is intentional.

If validation fails, continue fixing the same child without changing it to in-review. If a requirement cannot be implemented as written, comment with the concrete conflict and pause rather than silently changing product scope.

## Final branch publication and PR

Do this as a separate finalization invocation only after Linear confirms that every child under the root is in the completed state set by Codex. An invocation may implement one child **or** finalize one fully verified root; it must not do both in the same run:

1. Re-read the root and the complete child list; confirm dependencies are complete and no new child was added.
2. Run the full relevant validation once more and inspect the entire branch diff against the real default base.
3. Confirm commits are focused, the remote is the repository expected by the project, and the branch contains no unrelated changes.
4. Push the branch and open one PR against the detected default base. The user explicitly authorized branch creation, commits, push and PR creation for this workflow. Do not merge it.
5. Use a clear PR title and body with the PO outcome, summary by child issue, Linear identifiers/links, checks and results, visual evidence if useful, and known limitations.
6. Add the PR URL to the root issue in Linear and move the root to the team's in-review state. Do not close the root.

If `gh`, authentication, remote permissions, required status mapping, or PR creation is unavailable, preserve local commits and report the precise blocker. Never claim a push or PR exists unless the tool confirms it.

## Role boundary

You may create the task branch, edit code/assets required by accepted subtasks, run validation, create focused commits, push the branch, open the final PR, and update implementation progress in Linear. You must not create or re-scope PO root issues, mark child issues Done, close the root, merge the PR, modify team workflow settings, or bypass Codex's verification gate.
