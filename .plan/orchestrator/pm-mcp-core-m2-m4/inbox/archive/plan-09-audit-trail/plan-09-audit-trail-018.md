envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:59:30Z

# Failed rotation rename let a record slip behind the checkpoint (double index entry)

- Source: Q-Gate record 4f1136 (6-finalize, code-review, error, AuditRotation.java:173), resolution `taken_into_account` (fixed by TASK-11, superseded at f35a61c).
- Suggested component: project-local (runtime.audit rotation) / `persona-module-tester` (crash-window tests).

## Observation

If the rename after the checkpoint failed, a following record could be appended behind the checkpoint; the retry then wrote a second index entry for the same rotated file and `full()` reported a false break. First of six code-review findings, all in the same rotation/head crash windows (see 91499b, 3d0b4d, 7ecb8a, bbc237, 3f412a).

## Candidate rule

Multi-step file protocols (checkpoint, index, rename, head) need a test per interruption point that asserts both "nothing lost" and "verification still says Intact"; plan these crash-window tests in the outline rather than discovering them across review rounds.
