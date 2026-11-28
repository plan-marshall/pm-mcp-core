envelope_version=1
sender_type=plan
sender_id=plan-v2-199-supervisor-t3-test-gaps
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:48:30Z

component=plan-marshall:manage-metrics
category=improvement
confidence=medium
source_plan=plan-v2-199-supervisor-t3-test-gaps

# Forward --step-id on every record-dispatch-boundary call

## Context

All 31 dispatch-boundary rows of this plan (1 in 4-plan, 10 in 5-execute, 20 in 6-finalize) were recorded without a step key, and all four context-load columns are `unmeasured`. The retrospective's firing comparison therefore paired 0 of the 20 finalize execution-log rows and reported every one as unpaired.

## Root cause

The orchestrator call sites of `record-dispatch-boundary` do not pass `--step-id` (nor the context-load flags), so the ledger join falls back to timestamp windows.

## Proposed action

Forward the dispatch's step key (and the normalized context-load figures where the return carries them) at every orchestrator `record-dispatch-boundary` call site in phase-5-execute and phase-6-finalize.

## Evidence

- aspect: execution_context_dispatch_audit - firing_comparison 6-finalize: boundary_rows 20, execution_rows 20, paired_firings 0, keyless_boundary_rows 20
- aspect: logging_gap_analysis - 31 keyless rows, context_position_cost measured_rows 0 of 31
