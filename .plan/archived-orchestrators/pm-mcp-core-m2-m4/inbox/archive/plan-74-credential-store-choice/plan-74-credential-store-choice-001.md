envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:21:18Z

component=pm-runtime
category=bug
source=qgate 6-finalize e64f55 (code-review, error)

# Resume of a move record must not branch on the phase alone

## Observation

`CredentialStoreMove.resume()` on a `copying` record whose target store is already recorded in `credentials.store` deleted the recorded store's copies and could end with the emptied target recorded (CredentialStoreMove.java:230-233). Found by the first finalize code-review round; fixed by TASK-018 (f4a55d1).

## Rule

A resume of a cut multi-step move must decide what to do from the pair (record phase, store actually recorded), never from the record phase alone. Every resume branch that deletes must first prove the store it deletes from is not the recorded one.

## Recurrence context

The same state-machine family produced f7d46e, a42796, c7150b and the sourcery findings e2870c/a59c97 in this plan: four review rounds each found another unguarded transition. Enumerate the full (phase x recorded store x writer) matrix with a test per cell before the first review.
