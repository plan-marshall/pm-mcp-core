envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:51:19Z

component=plan-marshall:automatic-review
category=bug
created=2026-10-10

# A required review bot without a push trigger makes automatic-review loop back with no way to progress

## Source

Observed by the orchestrator during the finalize phase of plan primitive-spi-and-validators (PR #18). Checked against the decision log of the plan.

## What happened

`cuioss-review-bot` was a required bot. It has no push trigger and reviewed only the first commit of PR #18. After each fix push the automatic-review step found it `participated_stale` and recorded `loop_back`. It did so twice. Nothing the run could do would make the bot review the new head, so the loop could not end by itself. It ended when the operator moved the bot from `required_bots` to `optional_bots` for this plan only (plan-local step parameters; `marshal.json` untouched).

## Verification of the wording

- Confirmed: two `loop_back` outcomes of automatic-review (decision log 269436 at 23:44:18Z, 85984a at 00:15:49Z).
- Confirmed: `participated_stale` at 3144ca4, loop-back iteration 1/3 (f4ac48).
- Confirmed: operator decision of 2026-10-10 and its scope (762ee6), and the settled state "cuioss-review-bot optional and stale by operator decision" (318de9).
- NOT confirmed from the decision log: the setting `re_review_on_loopback false`. The log does not name it.

## Proposed rule

A bot may be listed as required only when it reviews again on push, or when the step can ask it to. For a required bot that reviewed an older commit and cannot be re-triggered, the step reports that state once and asks the operator, instead of spending loop-back iterations on a condition that cannot change.

## Evidence

- Decision log 269436, f4ac48, 85984a, 762ee6, 318de9.
