envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:23:20Z

# Forward --step-id on every record-dispatch-boundary call

- Component: plan-marshall:plan-marshall
- Category: improvement
- Source: plan-retrospective (plan-v2-190-lane-entries-realized-composition)

## Context

Every dispatch-boundary row of plan-v2-190 is keyless: 1 row in 4-plan, 4 in 5-execute and 9 in 6-finalize carry no step_id. The retrospective's firing comparison therefore paired 0 of 27 execution_log rows (1 in 5-execute, 26 in 6-finalize) and every row fell back to the timestamp window.

## Root cause

The orchestrator calls `manage-metrics record-dispatch-boundary` without `--step-id`, although the verb documents that the key should be forwarded on every call so the ledger join runs key-first.

## Proposed action

Pass `--step-id {step}` from the phase-5 and phase-6 dispatch sites of the plan-marshall workflow (execution.md and the finalize dispatcher), and let the retrospective flag keyless rows on plans that ran on a build that offers the flag.

## Evidence

- aspect: execution_context_dispatch_audit - firing_comparison keyless_boundary_rows 1/4/9, paired_firings 0 in every phase
- aspect: logging_gap_analysis - DISPATCH_TERMINATION_CAUSE gap, timestamp fallback for every row
- aspect: log_analysis - dispatch_boundaries rows carry no step key
