envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:59:34Z

# Head re-signed over a log shorter than the previous head anchored (hides truncation)

- Source: Q-Gate record 91499b (6-finalize, code-review, error, AuditHead.java:187-198), resolution `taken_into_account` (fixed by TASK-12).
- Suggested component: project-local (runtime.audit head) / `plan-marshall:persona-security-expert` (anchor monotonicity).

## Observation

The head was re-signed over the current log even when that log held fewer records than the last verifying head anchored, so a truncation would be laundered into a valid head.

## Candidate rule

A signed anchor must be monotonic: never re-sign over a state that is behind the previous verified anchor; refuse and report instead. Same family as 3d0b4d and 7ecb8a, which found the rule broken again on the damaged-head and rotation paths after this fix.
