envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:12:20Z

component=plan-marshall:plan-marshall
category=bug
signal=qgate
evidence=qgate finding dc556f (3-outline, resolution accepted)

# Q-Gate assessment_coverage fails every deliverable when the outline path writes no assessments

## Observation

In plan runtime-lifecycle (deep planning lane, Maven project pm-mcp-core) the 3-outline q-gate-validation flagged all 11 deliverables with `assessment_coverage — no CERTAIN_INCLUDE assessment backs any affected file`. The plan's assessment store was empty (`assessment list --certainty CERTAIN_INCLUDE` returned 0, `findings_store_state: missing`) because this project's outline path never records component assessments. File existence of all 43 affected files was verified separately. The operator had to accept the finding by hand.

Decision log: "Deliverables 1, 2, 3, 5, 9, 10: fail - ... flagged only by missing CERTAIN_INCLUDE assessments (dc556f)" and "Summary: 0 passed, 11 flagged".

## Why it matters

The check cannot pass for any deliverable in a project whose outline lane produces no assessments, so it adds one guaranteed operator interruption per deep-lane plan and hides the real per-deliverable verdicts behind a blanket fail.

## Suggested direction

Make the assessment-coverage check conditional on the outline lane actually producing assessments (or report it as not_applicable / unmeasured when the store is absent), instead of failing every deliverable.
