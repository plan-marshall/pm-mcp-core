envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:12:55Z

component=pm-runtime
category=bug
signal=automated-review
evidence=pr-comment findings 16df8c (sourcery, ProjectRecovery.java:149) and 10f0cc (coderabbit, ProjectRecovery.java:225), both resolution fixed by TASK-23

# Validate completeness of an enum-keyed step list, and guard indexes derived from progress counters

## Observation

1. The `ProjectRecovery` constructor documented one step per `RecoveryStep.Name` but only rejected duplicates; a list omitting a name ran only the supplied steps and `isRecovered` could return true with required work skipped.
2. `brokenStep` indexed `steps.get(results.size())` on every exceptional completion. Failures not coming from a step (a `RejectedExecutionException` from a shut-down executor, an exception after the last step) make `results.size() == steps.size()`, so the handler itself threw `IndexOutOfBoundsException`; the broken future stayed cached and the project never recovered in that runtime instance.

## Rule (pm-mcp-core)

A constructor that promises one entry per enum constant must check that every constant is present, not only that none repeats. An exception handler that derives an index from a progress counter must guard the index, because the handler also sees failures from outside the stepped work.
