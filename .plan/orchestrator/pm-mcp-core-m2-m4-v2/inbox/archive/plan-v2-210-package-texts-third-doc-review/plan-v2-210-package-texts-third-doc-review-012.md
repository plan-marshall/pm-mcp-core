envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:26:22Z

# Record a dispatch boundary with measured tokens for every finalize and execute dispatch

- Source: plan-retrospective (aspects log-analysis, execution-context-dispatch-audit, logging-gap-analysis)
- Component: plan-marshall:phase-6-finalize
- Category: bug
- Confidence: high

## Context

The 6-finalize boundary file holds 3 rows (the last at 15:57) for about 25 finalize dispatch lines and 36 execution_log rows; every later review round left no boundary row. In 5-execute the last 2 of 4 rows (envelopes 3 and 4, 16:43 and 18:07) record total_tokens=0 and tool_uses=0 although both envelopes committed fixes. All 8 rows mark the four token columns unmeasured, and the dispatch-audit channel confidence is low (10 finalize DISPATCH lines against 39 completions, ratio 0.256; 9 of 14 finalize steps have no token evidence).

## Root cause

Boundary recording is not called on every dispatch path of the finalize loop (re-entry rounds skip it), and the execute re-entry path records the boundary without the leaf's usage figures.

## Proposed action

Call record-dispatch-boundary on every finalize step dispatch including re-entry rounds, and pass the leaf's reported usage on the execute re-entry path; treat a zero-token row for a dispatch that produced a commit as a recorder defect.

## Evidence

- aspect: log_analysis — dispatch_boundaries 6-finalize rows=3; 5-execute rows 3 and 4 total_tokens=0, tool_uses=0
- aspect: execution_context_dispatch_audit — channel_completeness ratio 0.256, confidence low; dispatch_coverage no_evidence 9 of 14
- aspect: logging_gap_analysis — DISPATCH_TERMINATION_CAUSE gaps for 5-execute and 6-finalize
