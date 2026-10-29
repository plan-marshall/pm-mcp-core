envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T19:00:02Z

# verify() false break for an intact trail stopped between rotation rename and next record

- Source: Q-Gate record 3f412a (6-finalize, code-review round 4, error, AuditVerification.java:197-203), resolution `taken_into_account` (fixed by TASK-17); operator granted an extra loop-back round (decision log bcb8ac).
- Suggested component: project-local (runtime.audit verification).

## Observation

After a crash between a rotation's rename and the next record/head, `verify()` reported head_mismatch or missing for a trail that was in fact intact (wrong_result, fail-closed side). Sixth crash-window finding in the rotation protocol (see 4f1136).

## Candidate rule

Same as 4f1136: enumerate every interruption point of the rotation protocol and test verification at each one; this plan needed four code-review loop-backs (two operator-granted) to cover them one by one.
