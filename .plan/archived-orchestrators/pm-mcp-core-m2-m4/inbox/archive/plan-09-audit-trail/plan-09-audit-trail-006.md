envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:57:48Z

# Outline seam took the enrolment key under the audit leaf lock (lock-order break)

- Source: Q-Gate record a0fe06 (3-outline, warning, pm-core/.../store/AuditLogSink.java), resolution `taken_into_account`.
- Suggested component: project-local (pm-mcp-core core.store lock order, ADR core-store/011); `plan-marshall:phase-3-outline` for the general check.

## Observation

Deliverable 4 required the enrolment integrity key to be read or created before the audit leaf lock, but deliverable 1's seam only called the anchor under `LockKey.auditLog`, so `readOrCreate` would have taken the machine credentials lock after a leaf lock, breaking the total lock order. Settled by having AuditHead only `read()` an existing key (no LockKey) and defining the key-absent / key-unreadable outcomes.

## Candidate rule

When an outline introduces a callback invoked under a lock, it must state which locks the callback may take and check them against the project's lock order (ADR core-store/011) before Q-Gate.
