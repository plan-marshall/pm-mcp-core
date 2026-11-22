envelope_version=1
sender_type=plan
sender_id=plan-v2-20-git-commit-primitives
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-22T17:21:48Z

component=plan-marshall:plan-marshall
category=bug

# Attribute loop-back re-entries of 5-execute in metrics and the boundary ledgers

## Context

PLAN-V2-20 re-entered 5-execute seven times (one baseline-drift re-cycle and six finalize loop-backs), but metrics.toon shows close_count 2 for 5-execute and its latest end_time is 2026-11-21T13:11:50Z. The five 5-execute dispatch-boundary rows after 19:54Z all carry 0 tokens and 0 tool uses, the 6-finalize boundary ledger stops at 2026-11-21T16:18Z although finalize ran until 2026-11-22T17:18Z, and 72 of 83 execution_log rows are unmeasured. The reported 4.73M tokens is therefore a floor that cannot say where the loop-back spend went.

## Root cause

The loop-back path (6-finalize -> 5-execute -> 6-finalize) neither stamps a phase-boundary pair for each re-entry nor forwards the leaf's usage figures to record-dispatch-boundary / record-step; later finalize dispatches (review authors, verifiers, triage, resolver) record no boundary row at all.

## Proposed action

On every loop-back transition, stamp phase-boundary for the closing and re-entered phases; forward the returned usage of every execution-context dispatch to record-dispatch-boundary (with --step-id) and record-step; add a retrospective check that flags a boundary ledger whose last row predates the phase's last [STEP] line.

## Evidence

- aspect: plan-efficiency - 5-execute close_count 2 vs 7 re-entries; totals are floors (n=5/6)
- aspect: logging-gap-analysis - 6-finalize boundary rows stop after day one; 87% of execution_log rows unmeasured
- aspect: execution-context-dispatch-audit - channel_completeness ratio 0.101, confidence low; 70 unpaired finalize execution rows
