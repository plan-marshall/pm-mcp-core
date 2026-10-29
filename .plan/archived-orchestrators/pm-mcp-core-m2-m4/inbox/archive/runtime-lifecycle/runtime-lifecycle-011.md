envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:12:56Z

component=pm-runtime
category=bug
signal=automated-review
evidence=pr-comment findings 7118b4 (sourcery, PeriodicSweep.java:141), b0f31d (sourcery, PeriodicSweep.java:94), 760dad (coderabbit, PeriodicSweep.java:149), all resolution fixed by TASK-24

# A task given to scheduleAtFixedRate must contain every failure, or the schedule silently stops

## Observation

`PeriodicSweep.sweep` caught only `StoreRefusal` and `UncheckedIOException`. Any other unchecked exception from a step aborted later steps and projects in the run, and because it escaped the `scheduleAtFixedRate` callback, `ScheduledExecutorService` suppressed all later executions with nothing logged, against the contract "A failing step is logged and stops neither the other steps nor the other projects". CodeRabbit proposed `catch (RuntimeException e)`, which the repository forbids; the fix used the handle/exceptionally pattern already used by `ProjectRecovery`.

## Rule (pm-mcp-core)

Every runnable handed to `scheduleAtFixedRate` / `scheduleWithFixedDelay` must contain and log all failures at its boundary. In this repository that cannot be a `RuntimeException` catch, so route the work through a `CompletableFuture` handle/exceptionally boundary (as `ProjectRecovery` does) and test that a throwing step leaves the next scheduled run alive.
