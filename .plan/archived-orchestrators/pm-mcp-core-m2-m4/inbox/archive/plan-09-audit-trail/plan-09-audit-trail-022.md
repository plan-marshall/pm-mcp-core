envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:59:48Z

# Pending rotation completed over an unverified checkpoint (keyless forgery answers Intact)

- Source: Q-Gate record bbc237 (6-finalize, code-review round 3, error, AuditRotation.java:299-314), resolution `taken_into_account` (fixed by TASK-16); upgraded to fail_open by the verifier and the operator; operator granted an extra loop-back round (decision log cd6149).
- Suggested component: project-local (runtime.audit) / `plan-marshall:persona-security-expert`.

## Observation

`completePending` trusted the checkpoint record it found and signed a head over it without verifying its HMAC, so a keyless forged checkpoint over a cut log made startup `verify()` answer Intact.

## Candidate rule

Every input read back from disk during recovery is untrusted, including records the runtime itself wrote; verify its MAC before acting on it. Recovery paths deserve the same adversarial review as the happy path.
