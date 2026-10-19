# Landing Analysis: PLAN-70 — Align the plan instance with the specification

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #107 (https://github.com/plan-marshall/pm-mcp-core/pull/107), merged as squash `21939cb` on 2026-10-19

> Landing record for one shipped plan. Lives at `landings/PLAN-70.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `plan-70-plan-instance-alignment-026.md` (`kind: landing`, `landing-check`: `complete: true`, no missing key), corroborated by the orchestrator against `git diff --stat 21939cb^ 21939cb` (91 files, +4112 −559), `ci pr queue-state --pr-number 107` (`merged`, `21939cb2…`) and, in plan-marshall-documentation, `ci pr queue-state --pr-number 96` (`merged`, `8c1ddf4`). Tokens, wall clock, review dispositions and the gates are plan-reported.

## Deliverable Fidelity vs Spec

The plan counts ten deliverables (`deliverables_total=10`, `deliverables_done=10`). `functional-review`: 5 met, 5 met with residuals, 0 not met. The plan's one-line summary: closed prune reasons `plan_default` / `merge_by_operator` / `other_lane`, the shipped rule file at revision 2, the plan behaviour axes with the override event, `diff_summary` on the composition record, lane-route pruning at the outline event, `INV-INIT-2` per lane.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Closed prune reasons, rule file revision 2 | shipped-as-specified | `PruneReason` constants removed and the shipped `composition-rules.json` rewritten in the diff; ADR `workflow-plan/014` (diff summary as five flat arrays) |
| Plan behaviour axes and the override event | shipped-as-specified | ADR `workflow-plan/015` (axis levels and overrides carried in plan facts) |
| `diff_summary` on the composition record | shipped-as-specified | ADR `workflow-plan/014` |
| Lane-route pruning at the outline event (`InstancePruner`) | shipped-as-specified | `InstancePruner` with `DEEP_ENTRY = "refine.baseline-reconcile"` and `LIGHT_ENTRY = "init.light-gate"`; prune = reachable from the other lane's entry and not from the chosen one (lesson 01) |
| `INV-INIT-2` per lane | shipped-as-specified | `InitInvariants` (`LIGHT_GATE = "init.light-gate"`) |
| Package file `plan-instance-alignment.adoc`, its row, links | shipped-modified | The package file and row are gone from `main`; the one roadmap link in plan-marshall-documentation (`roadmap.adoc:233`) was fixed in documentation PR #96 (merged, `8c1ddf4`), a pull request of its own |
| Added by two operator decisions during execute (TASK-17..20) | added-unplanned | empty-list grammar in call values (ADR `workflow-dsl/006`) and the engine settling a pruned merge gate (`WorkflowValidator`, `VerificationRule`; ADR `workflow-plan/016`) |

Surface delta (`inbox landing-check`, base `21939cb^`): declared 51 entries, realized 91 files, 37 added, 4 declared entries untouched, `state: expansion_detected`. The additions are the two operator decisions above, the specification parts that cite renamed states (Q-Gate finding 760b6e: state-catalogue 04 and 07, workflow-dsl 02, 03 and 07) and ADRs. The declaration was a hypothesis inferred from the package file. The plan itself recorded the 20 knowingly added files only after the scope-creep check fired (lesson 06).

## Metrics and Anomalies

- Tokens: 13,471,210 total (plan-reported), more than five times the 2.5M threshold the plans of this epic are held to. Not re-measurable by the orchestrator.
- Duration: 38,407 s of wall clock (about 10 h 40 min), plan-reported.
- Anomalies, plan-reported: a baseline rebase rewrote the execute commits and the `execute -> finalize` transition refused with `execute_commits_unrecorded` (14 commits); one inline triage fix after the first push (`d8bfc8e`) re-fired every HEAD-dependent review and gate; the manifest's `envelope_count` (6) was not recomputed after the re-ordering; `verify -Pcoverage` and `verify -Pintegration-tests` were not run locally and are left to CI.

## Routing and Merge Behavior

- Review (plan-reported): `code-review` 0 blocking, 10 advisory, 7 rules cited; `functional-review` 9 advisories. CodeRabbit raised six findings that were fixed in run. The optional bot Sourcery answered only with its weekly review-budget refusal.
- CI/merge: merged by the merge queue as `21939cb`, `merge_state=merged`, `cleanup_owed=false`. CI green on the final head per the plan.
- Collision with PLAN-37, the plan that ran beside it: none observed. PLAN-37 landed first (`ebcf128`); the landing message reports no conflict. This is the second piece of evidence for the pairing PLAN-37 + PLAN-70, and it held.
- Breaking for `plan-marshall-mcp` — the plan says `CompositionRecord.composed` takes the previous version as a second argument, `CompiledInvariants.check` takes a lane, the `PlanFacts` constructor changed and `RECIPE_ID_FACT` is removed, and that code there stops compiling. **Refuted by the orchestrator:** at `plan-marshall-mcp` `91b60d5` no Java file names `CompositionRecord`, `CompiledInvariants`, `PlanFacts` or `RECIPE_ID_FACT` (`grep -rln`), as already found for #91 on 2026-10-18; nothing breaks today. It holds only once the assembly calls the composer.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-70 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-70 --field pr --value "#107"`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-70 --field landing --value landings/PLAN-70.md`
- [x] row `plan_marshall_plan_id` stamped — `plan-70-plan-instance-alignment`
- [x] epic.md narrative reconciled (dependents of the package, the owed roadmap check-off, the refuted breaking-change claim)
- [x] the 25 candidate lessons: 22 concern `plan-marshall:*` components and were forwarded to plan-marshall's `lessons-routing` inbox under the operator rule of 2026-10-17; 3 concern `pm-workflow` and were promoted into this repository's lessons corpus
- [x] the dependents PLAN-46, PLAN-50 and PLAN-51 no longer wait on this package
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- The advisories the plan left open, checked at `main`: the refused-parser arm of `PlanInstanceStore.previousHeader` (line 217) has no test (`grep previousHeader pm-workflow/src/test` finds nothing); the lane entry ids are declared in both `InstancePruner` (`DEEP_ENTRY`, `LIGHT_ENTRY`) and `InitInvariants` (`LIGHT_GATE`). The no-instance-header arm, the call selector and the land/adopt/unknown-lane arms of `InstancePruner`, the flat fixture units (`plan.pmwf`, `execute.pmwf`) and the disagreement between items 1 and 2 of the axes in Workflow DSL part 9 (ADR `workflow-plan/015`) are the plan's statements and were not re-read. Staged as PLAN-91 `plan-instance-alignment-advisories`.
- Owed to the milestone exit, written in plan-marshall-documentation, not here: the roadmap check-off of the plan-instance item and the documentation parts of PM-IMPL-5. The roadmap line 233 there still lists the prune reasons and `diff_summary` under "Remaining".
- `plan-marshall-mcp` is not affected today (see above); the breaking change is a statement for whoever first wires the composer into the assembly.
