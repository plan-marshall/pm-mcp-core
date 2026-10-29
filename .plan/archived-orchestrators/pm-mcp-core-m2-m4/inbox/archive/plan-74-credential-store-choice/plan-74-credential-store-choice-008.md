envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:21:54Z

component=pm-runtime
category=bug
source=qgate 6-finalize a42796 (code-review, error)

# Recording an already recorded store must not switch stores without a move

## Observation

`CredentialStoreChoice.record(LockManager, user, store)` switched an already recorded store to a different one with no move and no key carry, stranding the entries and the integrity key in the old store (CredentialStoreChoice.java:361-366). Fixed by TASK-025 (d02f647). The verifier refused the first severity grading (see 61bc1a): the defect was real but strands rather than destroys.

## Rule

Once a store choice is recorded, the only path that may change it is the move. A first-recording API must refuse a different value when one is already recorded, rather than overwrite it.
