# Landing Analysis: PLAN-75 — Redistribute the work packages by component

epic: pm-mcp-core-m2-m4
workstream: WS-10
pr: #85 (https://github.com/plan-marshall/pm-mcp-core/pull/85), merged as squash `c281f6c` on 2026-10-17

> Landing record for one shipped plan. Lives at `landings/PLAN-75.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `work-package-redistribution-009.md` (`kind: landing`, `landing-check`: `complete: true`, no missing key), corroborated by the orchestrator against `git show --stat c281f6c`, `ci pr view 85`, a per-package bullet count of `doc/plans/*.adoc` at `c281f6c`, and the README rows at `c281f6c`.

## Deliverable Fidelity vs Spec

The spec has nine deliverables; the plan counts twelve (`deliverables_total=12`) because it split the module-spanning schema-parity deliverable per module and renumbered. `functional-review`: 11 met, 1 met with residuals (the sibling-repository link, below), 0 not met.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Own package file and row, first and last | shipped-as-specified | `doc/plans/work-package-redistribution.adoc` is absent from `main` and from the net diff; the README holds no row of it |
| 2. `project-machine-store` into `operator-and-question-stores` | shipped-as-specified | `project-machine-store.adoc` deleted (-26); survivor holds 6 bullets (2 + 4) |
| 3. `lane-route-pruning` into `plan-instance-alignment` | shipped-as-specified | `lane-route-pruning.adoc` deleted (-62); survivor holds 11 bullets (6 + 5) |
| 4. `stalled-scope-detection` into `removed-concepts` | shipped-as-specified | `stalled-scope-detection.adoc` deleted (-25); survivor holds 8 bullets (7 + 1) |
| 5. `epic-findings-review` into `epic-decomposition` | shipped-as-specified | `epic-findings-review.adoc` deleted (-28); survivor holds 12 bullets (6 + 6) |
| 6. Split `start-from-clients` | shipped-modified | Kept half: 9 bullets, as specified. New half: 12 bullets, as specified, but named `start-source-intake` instead of `start-entry-and-screening`: the spec allowed the name to be adjusted at refine, and the operator accepted the plan's proposal there |
| 7. Split `plan-and-epic-control` | shipped-modified | Kept half: 7 bullets. New half: 7 bullets, named `epic-resource-and-release` instead of `epic-actions` (same refine decision) |
| 8. Rows that name a changed package | shipped-as-specified | README: no row names `project-machine-store`, `lane-route-pruning`, `stalled-scope-detection` or `epic-findings-review`; the two new packages are named in the rows that need them |
| 9. Conservation and report | shipped-as-specified | 9 + 12 + 7 + 7 + 6 + 11 + 8 + 12 = 72, counted from `== What` of the eight surviving files at `c281f6c`; the PR body lists the owed corrections |

Unplanned edits, all in the diff: package names inside a Javadoc line (`CompositionEvent.java`), an ADR path (`011-lane-route-pruning-deferred.adoc`), the developer guide `workflow-plan.adoc`, and the reason strings of the two disabled schema-parity tests (`AuditSchemaParityTest`, `PendingEntrySchemaParityTest`). They are code-side but change no behaviour, and the PR body names them.

## Metrics and Anomalies

- Tokens: 3,825,553 total (plan-reported; a floor, because the init phase and several finalize steps carry no token record). Against a 2.5M threshold. Not re-measurable by the orchestrator.
- Duration: 23,098 s of wall clock (about 6 h 25 min), of which about 1 h 24 min worked; most of the wait was refine questions.
- Anomalies, all plan-reported: the `Links` check failed on PR #85 because `plan-marshall-documentation` `doc/roadmap.adoc:233` linked the deleted `lane-route-pruning.adoc`; the sibling PR plan-marshall-documentation #85 (merged, `f0eeeb5`) fixed it, and ci-verify ran 38 minutes. Sourcery did not review (budget exhausted). Eight candidate lessons (`-001` to `-008`), dispositioned below.

## Routing and Merge Behavior

- Review: functional review as above; code review `blocking_count=0`, `advisory_count=3`. CI/merge: squash `c281f6c`, `cleanup_owed=false`.
- Surface calibration: the spec declared the ten package files, two of them new. The merged diff touched 25 files: the ten declared packages and the two new ones (12), the README, seven further package files whose links were re-pointed (`chat`, `cleanup`, `decision-engine`, `epic-auto`, `machine-onboarding-unit`, `operator-reports`, `project-onboarding-unit`; deliverable 8 covers them), and five code-side files (the unplanned edits above). No collision with the concurrently running plans was reported or observed.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-75 --status shipped`
- [x] row `pr`, `landing`, `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-75`
- [x] epic.md narrative reconciled (queue annotation: absorbed and split rows, the new package names, the owed corrections)
- [x] landing message `work-package-redistribution-009.md` archived after the reconciliation was persisted
- [x] candidate lessons `-001` to `-008`: dispositioned in the decision log (seven forwarded to plan-marshall's `lessons-routing` inbox, one observed here)
- [ ] follow-up not done by this verb: the staged specs and queue rows of the ten changed packages still describe the old package files (see epic.md Queue annotations)
- [x] resume anchor updated in `resume_anchor.md`
