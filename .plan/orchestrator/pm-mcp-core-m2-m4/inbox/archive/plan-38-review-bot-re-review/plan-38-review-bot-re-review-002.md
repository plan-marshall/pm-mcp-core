envelope_version=1
sender_type=plan
sender_id=plan-38-review-bot-re-review
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T19:52:14Z

# Candidate lesson: a .plan/-only PR cannot be reviewed by either required bot, so automatic-review loops back for nothing

**Signal source**: automatic-review outcome (`signal_automated_review_count`)

**Components**: `plan-marshall:automatic-review`, `plan-marshall:phase-6-finalize` (required-bots resolution), and the review-bot path filters (CodeRabbit and cuioss-review-bot `classify-review-diff`)

## Observation

When a PR's diff touches only `.plan/` files, neither required review bot can review it:

- CodeRabbit posts "Review skipped due to path filters".
- cuioss-review-bot's `classify-review-diff` returns `reviewable=false`, because its filter drops `.plan/` paths, so its review job is skipped.

`automatic-review` then loops back with both required bots `absent`. Another round cannot change that, so the loop-back is wasted. In this run it was resolved by clearing `required_bots` for the plan during phase 6.

## Suggested corrective action (for orchestrator triage)

Detect a `.plan/`-only footprint up front: either during planning (make the bots optional for that plan before finalize), or inside `automatic-review` by checking the diff against the bots' known path filters and treating their absence as expected rather than as a loop-back trigger. Unless the repository changes its bot filters, a config-only PR should never wait on a review that cannot happen.

Category guess: improvement
