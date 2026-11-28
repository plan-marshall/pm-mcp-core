envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:23:31Z

# Resumed-session finalize dispatches record zero usage on boundary rows

- Component: plan-marshall:manage-metrics
- Category: bug
- Source: plan-retrospective (plan-v2-190-lane-entries-realized-composition)

## Context

Plan-v2-190's finalize resumed in a new session on 2026-11-28. The three 6-finalize dispatch-boundary rows written after the resume (09:19:06, 09:34:58, 12:17:40) record 0 tokens, 0 tool uses and 0 ms, one of them a `returned_with_findings` automatic-review firing that did real work. The 6-finalize total (1,325,989) therefore covers only the first session's dispatches.

## Root cause

After the resume the orchestrator called `record-dispatch-boundary` (and the accumulator) without the agent-reported figures, and the recorder writes 0 for an omitted `--total-tokens` / `--tool-uses` / `--duration-ms`, so an unmeasured dispatch reads as a measured zero.

## Proposed action

Make an omitted token/tool/duration flag on `record-dispatch-boundary` write `unmeasured`, as the four context-load columns already do, and have the orchestrator forward the usage figures on the resumed path.

## Evidence

- aspect: log_analysis - 6-finalize dispatch_boundaries rows 7-9 with total_tokens 0, tool_uses 0, duration_ms 0
- aspect: logging_gap_analysis - post-resume finalize spend unmeasured
- aspect: plan_efficiency - 6-finalize token figure is a floor (phase also unclosed)
