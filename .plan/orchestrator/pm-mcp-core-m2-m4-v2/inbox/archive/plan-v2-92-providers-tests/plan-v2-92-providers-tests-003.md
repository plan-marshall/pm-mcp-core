envelope_version=1
sender_type=plan
sender_id=plan-v2-92-providers-tests
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-07T08:21:24Z

component=plan-marshall:phase-5-execute
category=bug
confidence=high
source_plan=plan-v2-92-providers-tests

# Fire the per-deliverable commit when the chain tail's dependants sit in a later envelope

## Context

In PLAN-V2-92, deliverable 8 (TASK-1/TASK-2) and deliverable 9 (TASK-3/TASK-4) were finished in envelope 1, but no per-deliverable commit fired: TASK-2 had a pending dependant TASK-11 in envelope 3, and TASK-5 had TASK-6 in envelope 2. The leaves reported that the chain-tail rule "never marks TASK-2 or TASK-3 as a chain tail", so deliverables 8, 9 and later 2-7 and 10 sat uncommitted in the worktree until the orchestrator committed them at the boundary.

## Root cause

The Step 10a chain-tail test checks whether ANY pending task depends on the finished task, across all deliverables and envelopes, instead of whether the finished task is the last task of its own deliverable.

## Proposed action

Define the chain tail per deliverable: commit when every task of the finished task's deliverable is done, regardless of cross-deliverable dependants. Log the commit decision either way.

## Evidence

- aspect: chat_history_analysis - TASK-3 leaf: "The rule as written checks only whether any pending task depends on the finished task ... Deliverables 8 and 9 therefore sit uncommitted together"
- aspect: chat_history_analysis - envelope 2 and 3 leaves: "Nothing was committed ... left in the working tree for the commit at the boundary"
- aspect: logging_gap_analysis - 3 envelope-3 tasks without [OUTCOME] lines
