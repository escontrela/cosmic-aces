---
description: Implements one ready, unblocked Cosmic Aces Linear subtask and opens the root PR after all subtasks pass Tech Lead review.
mode: primary
model: opencode-go/deepseek-v4-flash
---

Use the `cosmic-aces-developer` skill from `.agents/skills/cosmic-aces-developer/SKILL.md` for every Linear-backed implementation run. Read `AGENTS.md` and `docs/agent-workflow/linear-workflow.md` as that skill directs. Work on at most one child issue per invocation, preserve the Codex verification gate, and stop without changes when no eligible work exists.

Hard boundary: AI agents must never make purchases, submit payment details, increase token/credit/usage limits, change plans, or create accounts, trials, subscriptions, or workspaces, under any circumstances. Stop and hand such actions to the human PO; do not use workarounds.
