# Landing Analysis: PLAN-72 — Spend limits removed from the code

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #83 (https://github.com/plan-marshall/pm-mcp-core/pull/83), merged through the merge queue as squash `acb5bba` on 2026-10-17 17:40 UTC

> Landing record for one shipped plan. Lives at `landings/PLAN-72.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `spend-removal-010.md` (`kind: landing`, `landing-check`: `complete: true`, no missing key), corroborated by the orchestrator against `git show acb5bba`, `ci pr view 83`, and a `git grep` of `main` at `acb5bba`.

## Deliverable Fidelity vs Spec

The spec carries the six bullets of `doc/plans/spend-removal.adoc` § What; the plan counts them as five deliverables (`deliverables_total=5`), because it folds bullets 5 and 6 into one outline deliverable. Every verdict below is read from the merged diff, not from the plan's own report.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Policy class `spend` leaves `WorkflowStatus.POLICY_CLASSES`; codec and `LoopBackBudget` refuse it | shipped-as-specified | `WorkflowStatus.java` (1 line); refusal cases in `WorkflowStatusCodecTest` (+34) and `LoopBackBudgetTest` (+18, lines 178-182); no `LoopBackBudget` production change, as the spec's Non-goals state |
| 2. Remediation unit `remediation.spend` and its exemptions | shipped-as-specified | `RemediationInvariants.java` (-45/+), `SPEND_UNIT` gone; `CompiledInvariantsTest` (+66) covers the exemption refusals; the main-tree grep finds `remediation.spend` only in tests that assert its refusal |
| 3. Pre-merge barrier `INV-FIN-5` checks only `finalize.merge-gate` | shipped-as-specified | `FinalizeInvariants.java` (-27), `InvariantId.java` (1 line); `CompiledInvariantsTest` line 246 "a merge gate that routes spend_overrun … passes INV-FIN-5" |
| 4. `HeaderSchemaCheck` admits six units and refuses `remediation.spend` | shipped-as-specified | `HeaderSchemaCheck.java` (1 line); `HeaderSchemaCheckTest` line 431 refusal row |
| 5. `workflow.spend` states completion and usage only | shipped-as-specified | `workflow/spend/package-info.java` and `pm-workflow/README.adoc` (1 line each) |
| 6. Tests and documents | shipped-modified | Tests: shipped, six files. ADR `workflow-verify/024` lost the spend bullet. The developer guides of `workflow.verify` and `workflow.engine` needed no change: `git grep -i spend` over `pm-core/doc/developer` and `pm-workflow/doc/developer` finds nothing that names the unit. The two `plan-marshall-documentation` items (roadmap check-off, `token-usage` "remaining" statement) were deliberately not done: they are owed to the milestone exit by the operator decision of ADR 007, and the PR body lists them under "Owed to the milestone exit". Added unplanned: `doc/plans/token-usage.adoc` loses its link to the deleted package file |

Done-when, checked on `main` at `acb5bba`: no production class of `pm-core` or `pm-workflow` names `remediation.spend`, `spend_overrun`, `SPEND_UNIT`, `SPEND_OVERRUN` or `SPEND_REMEDIATION`; the three test hits are refusal and pass cases. The full `verify` chain (5150 tests per the PR body) is the plan's report; the merge queue admitted the PR, which needs the required checks green.

## Metrics and Anomalies

- Tokens: 3,943,550 total (plan-reported, `landing-facts`); the retrospective counts about 41% of them in finalize. Not re-measurable by the orchestrator.
- Duration: 15,347 s of wall clock (about 4 h 16 min), plan-reported.
- Anomalies, all plan-reported:
  - The run exceeded the retrospective's budget for a multi-module tech-debt plan: about 3.9M tokens against 2.5M.
  - The operator asked for five advisory review findings to be fixed before the merge. One small commit (two test files, one document) moved HEAD and re-fired every head-dependent step: both reviews, the pre-push gate, CI, the review bot, Sonar. Candidate lesson `spend-removal-002`.
  - The first functional-review round was closed by an operator override on one criterion that cannot be evidenced before the pull request exists (the PR body). Candidate lesson `spend-removal-003`.
  - The executor pinned 0.1.1916 while the installed plugin was 0.1.1947, so documented verbs were missing (`git-workflow commit`, `manage-solution-outline deliverable-hashes`). Candidate lesson `spend-removal-004`; the same skew keeps this epic's restart verdict `indeterminate`.

## Routing and Merge Behavior

- Review: functional review `deliverables_met=4`, `deliverables_met_with_residuals=1`, `not_met=0`, `criteria_unevidenced=0`. Code review `blocking_count=0`, `advisory_count=1`. Five first-round advisories were fixed before the merge; one advisory stayed open (below). Sonar: 0 new-code issues.
- CI/merge: merge queue (`merge_mechanism=merge_queue`), `cleanup_owed=false`. No rebase conflict reported.
- Open advisory, verified: `doc/Traceability.adoc` lines 247-248 list `CompositionRulesTest` and `PlanInstanceComposerTest` under PM-WF-14 "Verified by (no spend limit)". The diff only renamed a gate name in their fixtures, so the two entries are not evidence for the removal. Recorded as an Open Defect.
- Surface calibration (declared against realised, for the next pairing decisions): the Expected Surface declared 29 entries. The merged diff touched 18 files, of which 14 were declared or shared (`README`, `Traceability`). Three were NOT declared: `CompositionRulesTest.java`, `PlanInstanceComposerTest.java` and `doc/plans/token-usage.adoc`. About a dozen declared HYPOTHESIS entries went untouched (the developer guides and ADR directories of five packages, the watch document and four specification parts), because the specification already carried no spend unit. No collision with the concurrently running PLAN-74 and PLAN-75 was observed: both branches were rebased onto `acb5bba` and are 0 behind `main`. The under-declaration is recorded as calibration only; the spec is shipped, so there is no surface left to correct.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-72 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-72 --field pr --value "#83"`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-72 --field landing --value landings/PLAN-72.md`
- [x] row `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-72 --field plan_marshall_plan_id --value spend-removal`
- [x] epic.md narrative reconciled against the queue rows (queue annotation, Open Defect for the Traceability entries, Watch for the items owed to the milestone exit)
- [x] landing message `spend-removal-010.md` archived after the reconciliation was persisted
- [ ] nine candidate lessons `spend-removal-001` to `-009`: disposition pending the operator (see the decision log)
- [x] resume anchor updated in `resume_anchor.md` — `manage-status update-field --field resume_anchor --store orchestrator`
