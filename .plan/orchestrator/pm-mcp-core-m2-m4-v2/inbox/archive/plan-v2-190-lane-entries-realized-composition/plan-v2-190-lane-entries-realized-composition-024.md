envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:23:32Z

# Classify the four script-failure clusters lessons-capture deferred

- Component: plan-marshall:plan-retrospective
- Category: improvement
- Source: plan-retrospective (plan-v2-190-lane-entries-realized-composition)

## Context

lessons-capture sent four candidate-lesson messages for plan-v2-190 (manage-findings, manage-solution-outline, manage-status, phase_handshake) with the classification deferred, because it could not filter the 2482-entry work log within its budget. The retrospective's script-failure-analysis classifies the same four calls deterministically from script-execution.log.

## Root cause

lessons-capture reads the work-log signal scan, which names only the notation, while the classification lives in the script log that script-failure-analysis parses. The two steps do not share the result.

## Proposed action

The classification for the orchestrator, one occurrence each:
- manage-findings `qgate`, exit 2, argparse_other, 2026-11-27T18:46:52Z (likely the `--component` refusal the Q-Gate leaf reported: a deliverable reference rejected as invalid_component)
- manage-solution-outline `get-deliverable`, exit 2, argparse_other, 2026-11-27T19:30:49Z
- phase_handshake `verify`, exit 1, drift at 3-outline (drift_count=2; main_sha and task_state_hash drift recorded by the invariant summary), 2026-11-27T18:56:42Z - a detected drift, not a script defect
- manage-status `transition`, exit 1, script_internal_error, 2026-11-27T20:37:23Z, no stderr captured

Longer term: let lessons-capture consume the script-failure-analysis classification (or a manage-logging filter by notation) instead of deferring.

## Evidence

- aspect: script_failure_analysis - 4 unique failures with subtype and timestamp
- aspect: invariant_summary - main_sha drift 2-refine -> 3-outline, task_state_hash drift 3-outline -> 4-plan
