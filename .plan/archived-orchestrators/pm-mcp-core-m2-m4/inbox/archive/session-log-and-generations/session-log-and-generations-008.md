envelope_version=1
sender_type=plan
sender_id=session-log-and-generations
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T13:15:17Z

# Candidate lesson: Sourcery's budget-exhausted notice was not classified as a refusal

- Source: dispatcher observation (in-run, not a filed finding)
- Plan: session-log-and-generations (PR #51)

## Observation

During automatic review on PR #51, Sourcery posted a notice that its review budget was
exhausted, so it did no review. The review-bot completion logic did not classify that
notice as a refusal, because its wording is not covered by the configured
`refusal_patterns`. A bot that declined to review can therefore be read as a bot that
reviewed and found nothing, or as one still pending.

## Possible lesson

Add Sourcery's budget- or quota-exhausted phrasing to the refusal patterns, and add a
test using the real notice text. More generally, treat any bot notice that states it
did not review as a refusal, not as a completed review. Candidate owners:
plan-marshall:automatic-review and plan-marshall:workflow-integration-github
(bot_completion).
