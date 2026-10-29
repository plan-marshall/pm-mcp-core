envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:22:09Z

component=pm-runtime
category=bug
source=pr-comment a59c97 (PR #90, sourcery-ai High, fixed TASK-27 d3d46f2)

# Existing target credentials were lost when a move failed

## Observation

When the target already held credentials, `copyEntry` overwrote a target value for an account also in the source, and the failure clean-up deleted that account instead of restoring it. Target-only credentials also stayed when the target became active (CredentialStoreMove.java:281, also :269-271, :352-361, :367-369, IntegrityKeyCarry.java:82). Fixed by TASK-27: the move refuses, before it writes its record, when the target already holds a Token Lane entry.

## Rule

Four internal review rounds passed this lost-data defect, and an external bot caught it. Any copy-then-switch protocol must have a test whose target is pre-populated, both with an overlapping key and with a target-only key, plus a failure injected after the copy.
