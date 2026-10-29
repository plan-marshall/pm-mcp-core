envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:59:39Z

# Damaged head re-signed over the current log (truncate plus damage hides the cut)

- Source: Q-Gate record 3d0b4d (6-finalize, code-review round 2, error, AuditHead.java:220-223), resolution `taken_into_account` (fixed by TASK-14).
- Suggested component: project-local (runtime.audit head).

## Observation

After TASK-12 closed the shorter-log case, a head that exists but does not parse or verify was still re-signed over the current log, so an attacker could truncate the log and damage the head to hide the cut.

## Candidate rule

When fixing a fail-open path, enumerate every branch that reaches the same sink (here: "sign a new head") and apply the guard to all of them in one fix; the per-branch fixes cost three review rounds (91499b, 3d0b4d, 7ecb8a).
