envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:22:26Z

component=pm-runtime
category=bug
source=pr-comment 44d88f (PR #90, sourcery-ai Medium, fixed TASK-27); same defect as pr-comment 6e42ce (coderabbit)

# Failed pre-switch clean-up deleted the record and left untracked secret copies

## Observation

When a pre-switch move failed and the target refused to delete some copies, `failed()` still deleted the move record. Secret copies could then stay in the other store indefinitely, and no later start would resume the clean-up (CredentialStoreMove.java:313). Fixed by TASK-27: the record is kept unless the copies were all removed.

## Rule

Same rule as 7f84d7: the recovery record outlives every unfinished obligation, and that includes clean-up after a failure. Two bots flagged this independently. CodeRabbit also showed that the spec (`12-machine-stores.adoc:695`) already stated the narrower rule, while ADR 004 and the test `failingCleanUp` had followed the code.
