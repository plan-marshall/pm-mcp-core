envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:58:55Z

# False self-report in core-store developer documentation (sole write member)

- Source: Q-Gate record 492d6a (6-finalize, functional-review, error, false_self_report at pm-core/doc/developer/core-store.adoc, "The Chain and the Predecessor Rule"), resolution `taken_into_account` (fixed by TASK-13).
- Suggested component: `pm-documents:ref-documentation` / project-local.

## Observation

The core-store developer doc claimed `AuditLog.appendCheckpoint` is the one member through which the runtime writes into the log; untrue (see f12251).

## Candidate rule

Same family as 33efc0 / 03879b: one outline claim propagated into the documentation of a second module without verification.
