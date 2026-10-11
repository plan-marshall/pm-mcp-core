envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:12:51Z

component=pm-runtime
category=bug
signal=automated-review
evidence=pr-comment findings 5ffc0d (sourcery, RuntimeDrain.java:180) and bf72dc (coderabbit, RuntimeDrain.java:193), both resolution fixed by TASK-20

# Cleanup a class promises on failure must sit in finally, since unchecked exceptions are not caught here

## Observation

`RuntimeDrain` caught only `IOException` and `InterruptedException` around participant phases and the quiet wait. A participant throwing an unchecked exception escaped `runOnce` before the runtime files (`state/runtime.json`, `run/runtime.token`, `run/runtime.sock`) were removed and before `lock.close()`; other callers waiting on the shared future got a `CancellationException` instead of a drain outcome. The class Javadoc promised cleanup and lock release on participant failure. Both review bots flagged it; CodeRabbit proposed `catch (IOException | RuntimeException e)`, which the repository forbids in production code.

## Rule (pm-mcp-core)

The repository bans catching `RuntimeException`, so a class whose contract promises "X still happens when a collaborator fails" must put X (file removal, lock release, future completion) in a `finally` block and complete shared futures exceptionally, rather than relying on catch clauses. Write a test where a collaborator throws an unchecked exception for every such promise.
