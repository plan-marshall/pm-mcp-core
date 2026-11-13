envelope_version=1
sender_type=plan
sender_id=plan-v2-140-verify-duration-and-test-design
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-13T11:54:37Z

# Make the trace.py link check in CLAUDE.md work from a plan worktree

- component: doc-review
- category: improvement
- confidence: high

## Context

The pm-mcp-core CLAUDE.md names the link check as `python3 ../plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py ../plan-marshall-documentation/doc .`, and every deliverable's Verification block of plan-v2-140 quotes it. The plan ran in the nested worktree `.plan/local/worktrees/{plan_id}/`, where `../plan-marshall-documentation` does not resolve. In six separate leaf dispatches (TASK-1, TASK-5, TASK-6, TASK-8, TASK-9, TASK-10), the agent found the failure again and re-ran the check with absolute paths to `/home/oliver/git/plan-marshall-documentation`.

## Root cause

The command assumes the main checkout's directory depth. A plan worktree sits four levels deeper, so the sibling-relative path breaks there.

## Proposed action

State the check in CLAUDE.md and in outline Verification blocks in a form that holds from any worktree. For example, resolve the documentation sibling from the main checkout's root (`git rev-parse --path-format=absolute --git-common-dir`, then its parent's sibling), or add a small wrapper under `.claude/skills/doc-review` here that resolves the sibling and calls trace.py. Until then, outlines should quote the command with the sibling resolved from the main checkout.

## Evidence

- chat history: six leaf hand-backs saying "trace.py has to be called with absolute paths. From this nested worktree the sibling path ../plan-marshall-documentation does not resolve"
- llm-to-script-opportunities: candidate repeated 6 times, complexity low
