envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:59:53Z

# Code-review verdict refused: failure_class "none" on a fail-open finding

- Source: Q-Gate record 2d76ac (6-finalize, code-review-state, warning, verdict_refused), resolution `taken_into_account` (round 4 verifier accepted the grading at 77d2819).
- Suggested component: `plan-marshall:phase-6-finalize` (code-review workflow grading).

## Observation

The code-review author graded finding F2 (bbc237) with `failure_class: none`; the verifier refused the verdict because a keyless forged checkpoint making `verify()` answer Intact is wrong_result / fail_open. Grading, not the finding, decided the round.

## Candidate rule

A finding whose consequence is "the integrity check answers OK on tampered data" is fail_open by definition; the author's grading guidance should name that case so the verifier does not have to refuse it.
