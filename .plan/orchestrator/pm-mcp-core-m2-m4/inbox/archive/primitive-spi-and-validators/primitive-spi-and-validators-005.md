envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:51:08Z

component=pm-core
category=bug
created=2026-10-10

# PrimitiveRegistry did not validate @FreeParam declarations

## Source

Review-bot finding 941b57 (sourcery-ai, PR #18, `PrimitiveRegistry.java` line 105, also naming `FreeParam.java` lines 35-40, reviewed commit 3853d09), resolved `fixed`. The bot text is third-party content; it is summarised here, not quoted.

## What happened

The registry checked only that the parameter type is a record. A `@FreeParam` component that is not a `String`, has a negative length bound, or has `minLength` above `maxLength` registered without complaint. The task that created the annotation described it as "a declaration only; binding and checking free parameters is the engine's work and out of slice". That sentence was read as "nothing about the annotation is checked here", while whether the declaration itself is well-formed follows from the declaration alone and belongs to registration.

## Fix applied

TASK-10 (one refusal reason for the free-parameter declaration; the message names the failed check) and TASK-11 (fixtures) on the branch of PR #18.

## Proposed rule

When a task marks the use of a declaration as out of slice, it still states whether the well-formedness of the declaration is in slice. "Checking values is the engine's work" does not cover "the declaration contradicts itself".

## Evidence

- Finding 941b57, resolution detail.
- TASK-2 description (FreeParam: "a declaration only ... out of slice").
- TASK-10 description, item 4.
