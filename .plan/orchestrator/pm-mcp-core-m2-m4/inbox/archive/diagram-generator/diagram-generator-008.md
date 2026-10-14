envelope_version=1
sender_type=plan
sender_id=diagram-generator
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T10:10:26Z

component=plan-marshall:manage-change-ledger
category=improvement
source_plan=diagram-generator
source_record=retrospective:llm-to-script-opportunities

# Resolve the full commit SHA inside manage-change-ledger instead of trusting the caller

## Context

In plan diagram-generator the phase-5 executor appended all three per-deliverable change rows (TASK-2 4019133, TASK-3 1115d42, TASK-4 1a4d339) with short SHAs. A later envelope saw that the workflow expects the full `git rev-parse HEAD` value. Because the ledger is append-only, it added three duplicate full-SHA `kind=change` rows, and the short-SHA rows stay beside them.

## Root cause

The ledger accepts any SHA string the LLM passes and does not normalise it, so getting the full SHA depends on the executor remembering a prose rule.

## Proposed action

Have `manage-change-ledger` resolve the given ref (or HEAD by default) to the full 40-character SHA with `git rev-parse` before appending, or reject an abbreviated SHA with a clear error. Readers then never meet short and full rows for the same commit.

## Evidence

- llm-to-script-opportunities: 3 short-SHA rows, then 3 duplicate full-SHA rows
- chat-history-analysis: execute envelope 2 hand-back ("All three per-deliverable entries had short SHAs ... I added full-SHA kind=change rows")
