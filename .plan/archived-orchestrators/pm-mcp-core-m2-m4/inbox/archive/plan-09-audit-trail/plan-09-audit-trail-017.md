envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:58:59Z

# False self-report in ADR core-store/026 (sole write path)

- Source: Q-Gate record 2eea4c (6-finalize, functional-review, error, false_self_report at pm-core/doc/adr/core-store/026-audit-formats-in-core-store-keyed-anchor-in-the-runtime.adoc:29), resolution `taken_into_account` (fixed by TASK-13).
- Suggested component: `plan-marshall:manage-adr` / project-local.

## Observation

ADR 026 recorded as a decision consequence that runtime.audit writes into the log only through the public `AuditLog.appendCheckpoint`; untrue in the code (see f12251).

## Candidate rule

An ADR consequence that asserts a code property is a checkable claim; the documentation task should verify it against the code, or phrase it as the intent rather than as a fact.
