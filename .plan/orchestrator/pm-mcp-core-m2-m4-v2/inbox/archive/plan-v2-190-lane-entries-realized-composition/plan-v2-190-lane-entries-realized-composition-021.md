envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:23:29Z

# Record a record-step row for an orchestrator-tier verify:quality-gate

- Component: plan-marshall:phase-5-execute
- Category: bug
- Source: plan-retrospective (plan-v2-190-lane-entries-realized-composition)

## Context

In plan-v2-190 the 5-execute leaf handed `verify:quality-gate` back to the orchestrator (verification_handback), which ran the whole-tree verify green twice. The manifest's execution_log still carries only the `verify:module-tests` row. The closing 5-execute leaf and the functional review both noted the missing row, and the dispatch audit lists `verify:module-tests` as the only 5-execute execution row.

## Root cause

The hand-back path runs the build in the orchestrator's envelope, and neither the orchestrator nor the re-entering leaf writes the `record-step` row for the handed-back step. The freshness gate accepted the ledger-verified build, so nothing failed, but the manifest no longer records which composed verification steps ran.

## Proposed action

After the orchestrator finishes a handed-back verify step, write `manage-execution-manifest record-step` for it (the orchestrator or the re-entry leaf, whichever owns the hand-back close). Add a check that every composed `phase_5.verification_steps` id has an execution_log row before the 5-execute to 6-finalize transition.

## Evidence

- aspect: logging_gap_analysis - VERIFY gap, verify:quality-gate has no execution_log row
- aspect: execution_context_dispatch_audit - 5-execute execution_rows 1 (verify:module-tests only)
- aspect: chat_history_analysis - 5-execute closing leaf: "Nothing is recorded for verify:quality-gate"; functional review: "verify:quality-gate has no entry in the manifest execution_log"
