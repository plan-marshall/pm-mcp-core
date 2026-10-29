envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:51:06Z

component=pm-core
category=bug
created=2026-10-10

# PrimitiveRegistry accepted an outcome whose outcomeClass() is null

## Source

Review-bot finding 411f5f (sourcery-ai, PR #18, `PrimitiveRegistry.java` line 123, reviewed commit 3853d09), resolved `fixed`. The bot text is third-party content; it is summarised here, not quoted.

## What happened

`checkedOutcomes` validated the wire name of each outcome constant but not its outcome class. An outcome with a null class registered, leaving the engine without a class to select the step record. The gap is of the same kind as the null `cycleMeasure()` gap (finding 6612b2): one accessor of a contract was checked, its sibling was not.

## Fix applied

TASK-10 (own refusal reason) and TASK-11 (fixture) on the branch of PR #18.

## Proposed rule

Same family as 6612b2: when a contract type has several accessors and the registry refuses a missing value on one, the refusal covers all of them, and the fixture set has one case per accessor.

## Evidence

- Finding 411f5f, resolution detail.
- TASK-10 description, item 3.
