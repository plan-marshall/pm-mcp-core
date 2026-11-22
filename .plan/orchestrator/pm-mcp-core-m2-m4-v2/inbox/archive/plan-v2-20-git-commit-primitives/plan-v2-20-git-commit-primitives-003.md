envelope_version=1
sender_type=plan
sender_id=plan-v2-20-git-commit-primitives
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-22T17:21:57Z

component=plan-marshall:phase-3-outline
category=bug

# Close the 3-outline phase row when outline re-entry skips the re-transition

## Context

At the end of PLAN-V2-20 the status record still shows `3-outline` as `in_progress` while 4-plan and 5-execute are `done` and 6-finalize is running. The decision log at 2026-11-21T10:35:51Z says the operator-answer re-entry of the outline finished with "3-outline already transitioned, no re-transition". The phase table therefore disagrees with the plan's actual progress for the rest of the run.

## Root cause

The outline re-entry path marks its own phase row `in_progress` again on entry and, because it sees the phase as already transitioned, never sets it back to `done`.

## Proposed action

When phase-3-outline re-enters after its transition (operator answers, Q-Gate re-entry), either do not touch the phase row on entry or set it back to `done` at exit; add an invariant that no phase before current_phase may be `in_progress`.

## Evidence

- manage-status read at retrospective time: phases 3-outline=in_progress, 4-plan=done, 5-execute=done, 6-finalize=in_progress
- decision.log 2026-11-21T10:35:51Z: "3-outline already transitioned, no re-transition"
