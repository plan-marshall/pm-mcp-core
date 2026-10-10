# Landing Analysis: PLAN-03 — Workspace confinement

epic: pm-mcp-core-m2-m4
workstream: WS-01
pr: #19 (https://github.com/plan-marshall/pm-mcp-core/pull/19), merge commit 7d99648

> Landing record for one shipped plan. Lives at `landings/PLAN-03.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `workspace-confinement-020.md` (landing-facts complete, no missing key),
corroborated on 2026-10-10 against the PR state, the merge commit, the index on `origin/main` and
the documentation repository.

## Deliverable Fidelity vs Spec

Verdicts rest on the file list and diff of the merge commit and on PR states. Test content was
not read; "the acceptance holds" is the plan's claim plus green CI.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Every file and repository operation the daemon performs for a project is confined to the enrolled root and its worktrees | shipped-modified | The check exists: `core/store/PathConfinement.java`, `WorkspaceRoots.java`, `ConfinementOutcome.java`, `runtime/workspace/WorkspaceConfinement.java`, with `PathConfinementTest`, `LinkedWorktreeConfinementTest`, `WorkspaceConfinementTest`. No consumer is wired to it; `pm-provider-git` was put out of slice by operator decision |
| The rules for the daemon's own machine files, named per path in the trace matrix | shipped-as-specified | Recorded in the specification by `plan-marshall-documentation` #19, MERGED ("record the path confinement of PM-SEC-2 and its machine-file boundary"); the trace matrix itself was not read |
| Per-use checks of enrolment files are out of slice | shipped-as-specified | Nothing of the kind in the diff |
| Reword `CLAUDE.md` and "How to Work a Package" of the index | added-unplanned | Operator decision during planning; `CLAUDE.md` and `doc/plans/README.adoc` changed in 7d99648: no draft pull request claims a package, the documentation pull request need not name the code pull request |
| Index row | shipped-as-specified | `workspace-confinement` reads `done` on `origin/main` |

Surface: expansion detected (`inbox landing-check`). 12 realized paths against 5 declared entries;
11 added, 4 declared entries untouched. Both package homes the spec inferred were wrong:
`core.config` and `runtime.security` were declared, `core.store` and a new package
`runtime.workspace` were realized. Also outside the declaration: `CLAUDE.md` (the unplanned
deliverable) and `PmMcpLogMessages` (undeclared by design; WARN 150 `PROJECT_PATH_REFUSED`, the
first identifier of the WS-01 block).

## Metrics and Anomalies

- Tokens: 5,218,327 recorded (not re-derived; PLAN-02's total was overstated by its own account).
- Duration: 64,403 s wall time (about 17.9 hours), including an overnight wait on the review step.
- Anomalies: refine returned three operator questions because of the two wrong package homes;
  three outline dispatches; two fix rounds after review (Sonar java:S7467; a linked-worktree
  confinement bypass first declined in triage, then fixed on operator decision); the pre-push
  gate's module-tests arm was recorded DEGRADED.

## Routing and Merge Behavior

- Review: CodeRabbit raised the Major finding that led to the second fix; Sourcery raised the same
  point in round 1. `cuioss-review-bot` is a required reviewer that does not re-review after a fix
  commit; the review step stalled from 23:53Z to 06:16Z until the operator made it optional for
  this plan. Second plan in a row with this block.
- CI/merge: merged through the merge queue as squash commit 7d99648, after #18.
- Collisions: one, not predicted by the gate. PLAN-01's open PR #20 also takes WARN 150 (and
  INFO 50, ERROR 250) in `PmMcpLogMessages`, and reads CONFLICTING against `main` after this merge.
  `PmMcpLogMessages` is undeclared by the operator's block decision, and PLAN-01 and PLAN-03 share
  the WS-01 block. PLAN-01 was told through its mailbox.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-03 --status shipped`
- [x] row `pr` stamped — `#19`
- [x] row `landing` stamped — `landings/PLAN-03.md`
- [x] row `plan_marshall_plan_id` stamped — `workspace-confinement`
- [x] epic.md narrative reconciled (inbox dispositions, defects, watches, annotations)
- [x] 34 not-yet-started specs regenerated with the reworded working rules, the settled
      confinement design and corrected package-home claims
- [x] Open Defects opened: review-bot configuration blocks every plan; no consumer wired to the
      confinement check; same-block identifier clash
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- PLAN-01 must rebase #20 onto `main` and move its WARN record to the next free identifier.
- PLAN-15 `project-machine-store` depended on this plan; its other dependencies are still open.
- Statements the plan itself lists as unverified: PM-WATCH-PHASE-42 never read; CLI and Security
  specification parts 01-03 not read in full; `.claude/skills/traced-implementation/SKILL.md` and
  the introduction of the index may still mention the paired-pull-request rule.
