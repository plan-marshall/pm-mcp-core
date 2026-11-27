envelope_version=1
sender_type=plan
sender_id=plan-v2-189-outline-record-scope
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T16:08:20Z

component=plan-marshall:phase-6-finalize
category=improvement
confidence=high
source_aspects=plan-efficiency,manifest-decisions,llm-to-script-opportunities

# Skip pre-push-quality-gate whole-tree builds when the tree is already verified green

## Context

In plan-v2-189-outline-record-scope, phase-5 verification ran whole-tree quality-gate and module-tests green (13:27-14:17). The review loop-backs were closed by operator override without code changes, so the tree was unchanged. pre-push-quality-gate then re-ran whole-tree builds for 28 minutes (14:38-15:06) on that identical tree. build-maven owned 71% of the plan's script time (40 calls, 6690 s).

## Root cause

At manifest compose (4-plan) the step was kept on an unknown verdict ("pre_push_quality_gate_inactive - plan footprint unresolvable, no materialized worktree"), and nothing at run time compares the HEAD tree with the tree of the last green whole-tree verify.

## Proposed action

Record the git tree hash of each green whole-tree verify (per canonical command) and have pre-push-quality-gate skip, with a logged decision, when HEAD's tree hash equals a recorded green run for the same commands. Re-evaluate the compose-time keep rule at execute time once the worktree exists.

## Evidence

- decision.log 13:01:09 pre_push_quality_gate_inactive kept on unknown build verdict
- build-results/default: whole-tree runs at 13:27, 13:45, 14:02 then again 14:47, 15:04
- status.json: pre-push-quality-gate head_at_completion equals the review heads (99f905f2)
