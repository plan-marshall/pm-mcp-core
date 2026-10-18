envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:22:18Z

component=pm-runtime
category=bug
source=pr-comment 7f84d7 (PR #90, sourcery-ai Medium, fixed TASK-27)

# Completion audit event lost when the record is deleted first

## Observation

`finish` deleted the move record before appending the completion audit event. A stop or a failing audit sink in between left a completed move with no audit event and no record from which `resume()` could retry it (CredentialStoreMove.java:376). Fixed by TASK-27: the audit event is appended before the record is deleted.

## Rule

In a durable protocol, the record that drives recovery is deleted last, after every required side effect (audit included) is durable. Otherwise the side effect cannot be retried.
