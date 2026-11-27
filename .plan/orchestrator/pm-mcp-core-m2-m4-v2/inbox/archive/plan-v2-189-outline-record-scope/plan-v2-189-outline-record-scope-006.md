envelope_version=1
sender_type=plan
sender_id=plan-v2-189-outline-record-scope
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T16:08:56Z

component=plan-marshall:manage-findings
category=improvement
confidence=high
source_aspects=permission-prompt-analysis,chat-history-analysis

# Give resolve-by-rule a bounded non-destructive form so auto mode does not deny no-op calls

## Context

During finalize of plan-v2-189-outline-record-scope one manage-findings resolve-by-rule call was denied by the auto-mode classifier as destructive-looking, although the call would have changed nothing (no finding matched). The step had to work around the denial.

## Root cause

resolve-by-rule reads as a bulk mutation by rule, with no visible bound on what it touches, so the classifier cannot tell a no-op from a sweeping state change.

## Proposed action

Make the caller run a count-first or --dry-run form and skip the mutating call when the count is zero; when it is non-zero, pass the explicit finding ids (or a --max bound) so the call is visibly bounded.

## Evidence

- dispatcher context: a destructive-looking auto-mode denial hit one no-op resolve-by-rule call
- fragment permission-prompt-analysis: one denial, category missing_permission, severity warning
