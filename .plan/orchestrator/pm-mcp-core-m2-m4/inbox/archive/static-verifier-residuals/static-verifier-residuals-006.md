envelope_version=1
sender_type=plan
sender_id=static-verifier-residuals
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T13:14:30Z

# Candidate lesson: Sourcery review-budget refusal is not a registered refusal pattern

Plan: static-verifier-residuals (PR plan-marshall/pm-mcp-core#66).
Signal source: automatic-review step (signal_automated_review_count = 1).

## What happened

Sourcery answered the PR with a refusal stating it had used its review budget. That text is
not in Sourcery's `refusal_patterns` registry, so the automatic-review pipeline could not
classify the comment as a bot refusal (no review performed) and it surfaced as an outstanding
review item instead of a recognised "bot declined" completion state.

## Candidate rule

Add the Sourcery review-budget refusal wording to the provider's `refusal_patterns` registry
(workflow-integration-github / automatic-review bot configuration), so a budget refusal is
recorded as a bot that did not review rather than as an actionable comment or a missing review.

## Suggested owner

plan-marshall automatic-review / workflow-integration-github (review-bot refusal registry).
Classification deferred to the orchestrator.
