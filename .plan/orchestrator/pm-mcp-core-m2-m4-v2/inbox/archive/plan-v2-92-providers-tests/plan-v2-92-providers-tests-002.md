envelope_version=1
sender_type=plan
sender_id=plan-v2-92-providers-tests
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-07T08:21:16Z

component=plan-marshall:manage-solution-outline
category=bug
confidence=high
source_plan=plan-v2-92-providers-tests

# Let snapshot-criteria accept a byte-identical re-freeze without operator authorization

## Context

PLAN-V2-92 re-entered 3-outline from 4-plan. After the operator authorized one re-freeze, a later outline pass (adding ADR 005 to deliverable 10) left the success criteria byte-identical to the frozen record, yet `manage-solution-outline snapshot-criteria` refused again with `refreeze_requires_decision`. The phase stayed at 3-outline and the orchestrator had to put a second criteria decision to the operator.

## Root cause

The refusal fires on any second freeze once `work/criteria-snapshot.json` exists, without comparing the new criteria digest to the frozen one.

## Proposed action

In `snapshot-criteria`, compare the recomputed digest with the frozen `snapshot_digest`; when equal, return success as a no-op (`action: unchanged`) instead of demanding `criteria-refreeze-authorize`. Keep the refusal for a genuine criteria change.

## Evidence

- aspect: chat_history_analysis - leaf report: "The tool refuses any second freeze once a snapshot exists, even when the criteria are identical"
- aspect: chat_history_analysis - two `refreeze_requires_decision` returns from phase-3-outline dispatches
- status.metadata.criteria_integrity: snapshot_frozen true, refreeze_authorized false
