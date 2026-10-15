envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:58:51Z

# False self-report in runtime-audit developer documentation (sole write path)

- Source: Q-Gate record 03879b (6-finalize, functional-review, error, false_self_report at pm-runtime/doc/developer/runtime-audit.adoc:125-126 and Pitfall 2), resolution `taken_into_account` (fixed by TASK-13).
- Suggested component: `pm-documents:ref-documentation` / project-local.

## Observation

The developer documentation (body and "Pitfall 2") stated that `appendCheckpoint` is the one path by which the package writes into the log; untrue in the code (see f12251, 33efc0).

## Candidate rule

Same as 33efc0: developer-doc statements of invariants must be checked against the code they describe before the documentation task is closed.
