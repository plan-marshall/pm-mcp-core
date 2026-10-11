envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:12:54Z

component=pm-runtime
category=bug
signal=automated-review
evidence=pr-comment finding f525c3 (sourcery, LeaseRecoveryStep.java:97), resolution fixed by TASK-22

# A step that wraps a sweep must turn the sweep's refusal into a failed outcome

## Observation

`StaleLeaseSweep.sweep` returns a refusal (unreadable or unsupported lease store) inside its result. `LeaseRecoveryStep.run` only reported the result and returned `Done`, so `ProjectRecovery` treated the project as recovered although its leases were never inspected.

## Rule (pm-mcp-core)

When a callee reports refusal through its result value rather than an exception, every caller that maps the result to an outcome must check the refusal field explicitly; a step reports `Done` only when the work was actually performed. Cover the refused branch in the step's unit test.
