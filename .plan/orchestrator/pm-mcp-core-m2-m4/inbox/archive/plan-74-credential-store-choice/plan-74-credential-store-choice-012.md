envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:22:13Z

component=pm-runtime
category=bug
source=pr-comment e2870c (PR #90, sourcery-ai Medium, fixed TASK-27)

# Resuming a switched record deleted from the store still recorded

## Observation

`resume()` called `finish()` for every `SWITCHED` record without checking that `credentials.store` names `record.to()`. With an inconsistent but readable configuration that still recorded `from`, `finish()` deleted the active credentials (CredentialStoreMove.java:237). Fixed by TASK-27: resuming a switched record requires the target to be recorded, and otherwise refuses and deletes from neither store.

## Rule

This is the SWITCHED-phase twin of the COPYING-phase defect e64f55, which was fixed earlier in the same plan. When a guard is added to one phase branch of a resume, add the symmetric guard to every other phase branch in the same change.
