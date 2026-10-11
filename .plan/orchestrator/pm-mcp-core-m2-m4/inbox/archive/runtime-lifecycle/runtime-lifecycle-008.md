envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:12:52Z

component=pm-runtime
category=bug
signal=automated-review
evidence=pr-comment findings 6e5911 (sourcery, RuntimeDrain.java:168) and 6a48be (coderabbit, DrainTimeout.java:106), both resolution fixed by TASK-21

# Bound configured durations so deadline arithmetic cannot overflow

## Observation

`DrainTimeout.read` accepted any non-negative `long` from `machine-config.json` (`drain_timeout_seconds`). `RuntimeDrain.awaitQuiet` computed `clock.instant().plus(timeout)`, which throws `DateTimeException` / `ArithmeticException` beyond `Instant.MAX`, so a value such as `Long.MAX_VALUE` aborted the drain before cleanup and lock release, against the Javadoc "the drain never fails because of this value". Fixed with a documented upper bound; larger values fall back to the default and are logged as invalid.

## Rule (pm-mcp-core)

A configuration reader for a duration must validate an upper bound, not only non-negativity, whenever the value later feeds `Instant.plus` / `Duration` arithmetic. Add the overflow value to the reader's rejection tests.
