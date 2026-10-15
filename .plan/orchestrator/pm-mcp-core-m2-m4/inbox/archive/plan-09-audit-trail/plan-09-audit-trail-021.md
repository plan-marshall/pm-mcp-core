envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:59:43Z

# Rotation signed a checkpoint over a log cut below the anchored line

- Source: Q-Gate record 7ecb8a (6-finalize, code-review round 2, error, AuditHead.java:228-230), resolution `taken_into_account` (fixed by TASK-14).
- Suggested component: project-local (runtime.audit rotation).

## Observation

On the rotation path a checkpoint was signed over a log already cut below the head's anchored line, and the post-rotation recount hid the cut. Third branch of the same "re-sign over a cut log" defect (91499b, 3d0b4d).

## Candidate rule

See 3d0b4d: guard the signing sink, not the individual callers.
