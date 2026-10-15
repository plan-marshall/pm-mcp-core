envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:58:43Z

# Residual: runtime.audit wrote into the log through two paths, contradicting the outline

- Source: Q-Gate record f12251 (6-finalize, functional-review, error, residual at D4.R1), resolution `taken_into_account` (closed at f35a61c after TASK-13).
- Suggested component: `plan-marshall:phase-5-execute` / project-local (runtime.audit).

## Observation

D4 success criterion 3 said runtime.audit writes into the audit log only through `AuditLog.appendCheckpoint`; the implementation also called `AuditLog.append` from `AuditRotation.rename` (AuditRotation.java:249). Execute and its quality sweeps passed; only functional review found it.

## Candidate rule

"Sole path" / "only through" claims in an outline are verifiable invariants; the implementing task should check them mechanically (search for every call site of the guarded writer) before marking done.
