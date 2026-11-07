envelope_version=1
sender_type=plan
sender_id=plan-v2-93-http-tests-annotations
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-07T19:04:34Z

component=plan-marshall:manage-metrics
category=bug
confidence=high
source_plan=plan-v2-93-http-tests-annotations

# Accept verification_handback in record-dispatch-boundary wherever docs name it

## Context

In plan-v2-93 three phase-5 dispatches returned `verification_handback: true` (envelope 2, envelope 3, and the freshness hand-back). The orchestrator tried to record the first one with `--termination-cause verification_handback` and the call was rejected by argparse (exit 2, 2026-11-07T13:47:27Z, bundle 0.1.2041). It then recorded the row as `budget_yield`; the next two hand-backs were recorded as `voluntary_checkpoint`. The SKILL.md and execution.md of that bundle already described `verification_handback` as a cause.

## Root cause

The documented cause set and the live `DISPATCH_TERMINATION_CAUSES` tuple of the installed bundle disagreed, so a caller that followed the docs hit a rejection and fell back to a wrong cause.

## Proposed action

Confirm the tuple of the current bundle (0.1.2073 lists `verification_handback`) closes this; if it does, close the candidate as already fixed. Otherwise add the cause to the tuple. Either way, guard that docs and tuple cannot diverge in a shipped bundle (the structural-equality tests should run against the released artifact).

## Evidence

- aspect: script-failure-analysis - `manage-metrics record-dispatch-boundary` argparse rejection, exit 2, 13:47:27Z
- aspect: logging-gap-analysis - 3 of 7 5-execute boundary rows mis-classify a verification hand-back
- dispatcher fact: one boundary row recorded as budget_yield because the recorder had no verification_handback cause although the docs describe one
