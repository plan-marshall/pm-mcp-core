envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:22:22Z

component=pm-runtime
category=bug
source=pr-comment df8089 (PR #90, sourcery-ai High, fixed TASK-27)

# Store choice trusted a caller-asserted STORE_MOVE writer

## Observation

`CredentialStoreChoice.record` trusted the caller-supplied `Writer.STORE_MOVE` and did not check that a matching move record was open, so any caller could switch the active store with no copy and no key carry (CredentialStoreChoice.java:329). Fixed by TASK-27: recording with the STORE_MOVE writer requires an open move record under the credentials lock.

## Rule

A capability enum passed by the caller (for example "I am the move") is an assertion, not an authorization. Verify it against durable state under the same lock. This is the follow-on gap left by the f7d46e fix (TASK-021).
