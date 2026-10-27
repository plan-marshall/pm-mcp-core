# WS-02: The follow-up of the question-loop verification

epic: question-loop-corrections

## Charter

Make the follow-up package `question-loop-follow-up` of `plan-marshall-mcp` runnable and binding (durable evidence, the
specified scale, criteria fixed before the first run, no dependency cycle, the engine's parts in an engine package), make
`question-continuation` here wait for its settled values, and then run the re-measurement at the specified scale.
Closed when the re-measurement has landed and its settled values are written into their owning specifications.

## Scope

- In scope: `plan-marshall-mcp` `doc/plans/question-loop-follow-up.adoc`, `doc/plans/README.adoc`; `pm-mcp-core`
  `doc/plans/question-continuation.adoc`, `doc/plans/README.adoc` and a new engine package for the measured values if
  PLAN-02 creates one; the re-measurement itself (PLAN-03) in `plan-marshall-mcp`.
- Out of scope: the corrections of the published smoke results (WS-01); the implementation of `question-continuation`
  (PLAN-73 of the epic `pm-mcp-core-m2-m4`).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-02-revise-follow-up-package | staged | revise the follow-up package and the dependencies of `question-continuation` |
| PLAN-03-run-follow-up-measurement | staged | run `question-loop-follow-up` as PLAN-02 leaves it, at the specified scale |

## Sequencing and Surface Notes

- PLAN-03 depends on PLAN-02 and on PLAN-01 (it reads the corrected evaluation rows), and on the operator's run budget.
- PLAN-02 changes `doc/plans/question-continuation.adoc`, the package PLAN-73 of `pm-mcp-core-m2-m4` implements; PLAN-73
  is staged there, so the files are not in flight. `doc/plans/README.adoc` is shared with every plan of this repository
  (one row each).
