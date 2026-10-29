envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:21:40Z

component=pm-runtime
category=anti-pattern
source=qgate 6-finalize 3ef5a9 (functional-review false_self_report, error)

# Javadoc invariant false in the window resume() branches on

## Observation

`CredentialStoreMoveRecord.java:45` and `:64` said `credentials.store` names the old store while the phase is `copying`. That is false for the window between `choice.record(to)` and the record rewrite, which is exactly the window `resume()` branches on. Fixed in TASK-023.

## Rule

When documenting an invariant of a multi-write protocol, state it per crash window, not per phase. If a later write is not atomic with an earlier one, the Javadoc must name the in-between state, because that is the state recovery code has to handle.
