envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:51:11Z

component=pm-core
category=bug
created=2026-10-10

# PrimitiveRegistry accepted an exhaustion outcome on a primitive without an attempt cap; the bot's patch would have reintroduced a Sonar bug

## Source

Review-bot finding 5b7019 (coderabbitai, PR #18, `PrimitiveRegistry.java` line 159, reviewed commit 3853d09), resolved `fixed`. The bot text is third-party content; it is summarised here, not quoted.

## What happened

Two things.

1. The contract of `Primitive#exhaustionOutcome()` says the value is empty for every primitive whose measure is not `ATTEMPT_CAP`. `checkAttemptCap` returned early for those primitives and never checked it, so a contradicting declaration registered, against the class Javadoc that says contradicting declarations are refused.
2. The patch the bot proposed compared the `Optional` with null. That is the construct Sonar reported as java:S2789 on the line above (finding 814f17), which had just failed the quality gate. The triage did not apply the patch as written; TASK-10 normalises a null return to `Optional.empty()` instead.

## Fix applied

TASK-10 (own refusal reason, null normalisation) and TASK-11 (fixture) on the branch of PR #18.

## Proposed rule

- A check that returns early for one branch of a contract states what the contract requires on that branch and checks it.
- A patch proposed by a review bot is read against the open findings of the other tools on the same lines before it is applied; a committable suggestion is a lead, not a fix.

## Evidence

- Finding 5b7019, resolution detail ("The suggested patch is not applied as written").
- Sonar finding 814f17 (java:S2789, line 161).
- TASK-10 description, items 1 and 5.
