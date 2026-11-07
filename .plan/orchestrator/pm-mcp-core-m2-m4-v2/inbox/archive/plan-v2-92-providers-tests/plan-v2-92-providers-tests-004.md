envelope_version=1
sender_type=plan
sender_id=plan-v2-92-providers-tests
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-07T08:21:33Z

component=plan-marshall:plan-marshall
category=bug
confidence=high
source_plan=plan-v2-92-providers-tests

# Record verification_handback and --step-id on execute dispatch-boundary rows

## Context

In PLAN-V2-92 six of the seven 5-execute dispatch-boundary rows carry `termination_cause: voluntary_checkpoint`, although every one of those leaf returns carried `verification_handback: true` with a non-empty `pending_verify_steps[]`. In addition, all 22 boundary rows (4-plan, 5-execute, 6-finalize) were written without `--step-id`, so the retrospective's firing comparison paired 0 of the 18 finalize `execution_log` rows.

## Root cause

The orchestrator's after-execution-context classification records `voluntary_checkpoint` when the leaf return also carries `voluntary_checkpoint: true`, instead of preferring the more specific `verification_handback`; and the `record-dispatch-boundary` call sites do not forward the dispatch's step key.

## Proposed action

In `plan-marshall/workflow/execution.md` (and the finalize dispatcher), classify a return with `verification_handback: true` plus a non-empty `pending_verify_steps[]` as `verification_handback` before any `voluntary_checkpoint` test, and pass `--step-id` on every `record-dispatch-boundary` call.

## Evidence

- aspect: logging_gap_analysis - "5-execute: 6 voluntary_checkpoint, 1 budget_yield, 0 verification_handback"
- aspect: execution_context_dispatch_audit - keyless_boundary_rows 1/7/14, paired_firings 0 in every phase
- aspect: chat_history_analysis - TASK-1..TASK-5 and envelope-2/3 leaf returns all carry verification_handback: true
