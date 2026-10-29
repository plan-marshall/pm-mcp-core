envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:58:47Z

# False self-report in AuditRotation class Javadoc (sole write path)

- Source: Q-Gate record 33efc0 (6-finalize, functional-review, error, false_self_report at AuditRotation.java:50-53), resolution `taken_into_account` (fixed by TASK-13).
- Suggested component: `pm-dev-java:javadoc` / project-local.

## Observation

The class Javadoc claimed `AuditLog.appendCheckpoint` is the one path by which the package writes into the log, while the code also used `AuditLog.append` (see f12251). One of four places (Javadoc, developer doc, core-store doc, ADR 026) that repeated the same untrue claim.

## Candidate rule

When code documentation restates a design invariant, write it after the code exists and verify it against call sites; a claim copied from the outline into four documents multiplies one defect into four findings.
