envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:51:05Z

component=pm-core
category=bug
created=2026-10-10

# PrimitiveRegistry accepted a null cycleMeasure() and skipped the attempt-cap check

## Source

Review-bot finding 6612b2 (sourcery-ai, PR #18, `PrimitiveRegistry.java` line 157, reviewed commit 3853d09), resolved `fixed`. The bot text is third-party content; it is summarised here, not quoted.

## What happened

`checkAttemptCap` compared `cycleMeasure()` with `ATTEMPT_CAP`. A primitive returning null was treated like any other measure, so the cap check was skipped and the primitive registered without the protection its declaration should carry. The first implementation and its tests did not cover a null return of this accessor, although the registry already refused a null id, parameter type, outcome type, wire name and awaited event.

## Fix applied

TASK-10 (production: own refusal reason) and TASK-11 (fixture) on the branch of PR #18.

## Proposed rule

A registry that refuses what declarations decide statically checks every accessor of the contract for a null return, not a chosen subset. When one accessor gets a null refusal, enumerate all accessors of the same interface in the same task.

## Evidence

- Finding 6612b2, resolution detail.
- TASK-10 description, item 2.
