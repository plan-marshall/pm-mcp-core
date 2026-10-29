envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:58:39Z

# Pre-commit rewrite of untouched files tripped scope-creep (114 files)

- Source: Q-Gate record db52ba (5-execute, warning, scope_creep_warning), resolution `accepted` by operator.
- Suggested component: `plan-marshall:phase-5-execute` (scope_creep_check) / project-local quality gate.

## Observation

`verify -Ppre-commit` on pm-runtime rewrote the import layout of 117 files the plan never touched; TASK-8's envelope committed them separately (fa58482) and the scope-creep check then flagged 114 out-of-scope files. The operator chose to keep the rewrite in this PR.

## Candidate rule

Either the scope-creep check should recognise a commit produced purely by the quality-gate rewrite (and report it as such rather than as creep), or a plan that runs the pre-commit profile on a module with pending rewrites should land that rewrite first as its own change. Worth recording which so the next plan does not re-litigate it.
