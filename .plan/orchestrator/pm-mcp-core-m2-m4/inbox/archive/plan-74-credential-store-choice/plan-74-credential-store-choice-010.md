envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:22:05Z

component=pm-runtime
category=bug
source=qgate 6-finalize c7150b (code-review, error); related pr-comment a59c97 (sourcery High)

# A move into a store that already holds an integrity key overwrote or deleted it

## Observation

A move into a target store that already held another integrity key overwrote it, or deleted it in the clean-up of the failed move (CredentialStoreMove.java:436-438). Fixed by TASK-029 (5bb94ea). This was found only in the fourth, full code-review round, after the third delta round had closed. It is the key-level twin of sourcery's a59c97 "existing target credentials are lost", which TASK-027 fixed for Token Lane entries only.

## Rule

When a review finding is fixed for one kind of object a protocol copies (entries), check the same pre-existing-target hazard for every other kind it copies (integrity key, sidecars) in the same task. A move must refuse, or reconcile, any pre-existing object in the target before writing its record, and a failure clean-up may delete only what this move created.
