envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:21:44Z

component=pm-runtime
category=bug
source=qgate 6-finalize f7d46e (code-review, error)

# A resume must not take a foreign recording of the target as its own switch

## Observation

`resume()` treated "copying + target recorded" as the move's own switch and finished the deletion even when another writer recorded the target mid-copy; `record()` took only the machine-config lock and refused nothing while a move record existed (CredentialStoreMove.java:245-248). Fixed by TASK-021 (908f003): only the move may record `credentials.store` while a move record exists.

## Rule

While a durable in-progress record exists for a protocol, every other writer of the state that protocol switches must be refused under the same lock. Otherwise recovery cannot tell its own write from a foreign one. Sourcery later found the follow-on gap (df8089: the STORE_MOVE writer was caller-asserted rather than verified against an open record).
