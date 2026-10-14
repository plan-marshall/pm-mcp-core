# Landing Analysis: PLAN-56 — Static verifier, the residual gaps

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #66 (https://github.com/plan-marshall/pm-mcp-core/pull/66), merge commit 7981d17

> Landing record for one shipped plan. Lives at `landings/PLAN-56.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `static-verifier-residuals-011.md` (landing-facts complete, no missing key), drained
on 2026-10-14. The orchestrator verified the following:

- #66 is `merged` and its merge commit 7981d17 is `origin/main`.
- Documentation PR `plan-marshall-documentation` #72 is `merged` (c72cbf6).
- Specification issue #71 ("Workflow DSL: define how a `depends_on_config` value activates a guard") is `open`.
- The index row `static-verifier-residuals` is `done`.
- The diff 6bc8b71..7981d17 has 43 files, +1843/−545. In it: ADRs 021–025 were added and ADRs 001, 002, 004, 006, 008, 010, 013 and 014 changed, and 10 refused fixtures were added or changed. The `remediation-<class>.budget-check` / `remediation.check-budgets` admission is present in `WorkflowValidator`.

The orchestrator did not re-run the verifier against the specification; the deliverable verdicts below rest on the
plan's functional review (7 met, 1 met with residuals, 0 not met), checked against the diff.

Harness: Claude Code (operator rule since 2026-10-13).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Package file and index row | shipped-as-specified | `doc/plans/static-verifier-residuals.adoc`; README row `done` |
| 2. Rule 9 firing per step, STEP_LIST, ARCHIVE_STEPS | shipped-as-specified | ADRs 021, 022; fixtures `step-completion-firing-per-step`, `step-completion-loop-back-opening`, `cycle-measure-step-list-settle`, `cycle-measure-step-list-twice`, `cycle-measure-archive-commit-path` |
| 3. Rule 6 result guards everywhere | shipped-as-specified | ADR 023; fixtures `result-guard-outside-call`, `result-guard-callee-without-schema` |
| 4. Remediation budget re-check | shipped-as-specified | ADR 024, ADR 010 corrected; `WorkflowValidator` admits only `remediation-<class>.budget-check` running `remediation.check-budgets`; fixture `stepped-out-remediation-budget-check` |
| 5. Rule 2 `lost` in plan-only sub-workflows | shipped-as-specified | ADR 025; fixtures `model-job-lost-mixed-callers`, `model-job-lost-undeclared` |
| 6. Rule 3 activation | shipped-modified | ADRs 004 and 006 changed; issue #71 filed and open. Configuration reachability stays a named seam until #71 is answered |
| 7. Documentation honesty | shipped-as-specified | developer guide and ADR 001 changed (functional review: met) |
| 8. Documentation repository | shipped (by operator) | #72 merged before #66: trims PM-WATCH-DSL-3/4/8/9/11/15/16/29/32, rewords -5, updates the roadmap item. Phase 4 produced no task for it; the operator and an ad-hoc dispatch did the work (candidate lessons 003/008) |

**Surface delta** (landing-check, base origin/main = 7981d17, not stale): 7 declared, 43 realized, 4 added, 0 missing —
`doc/plans/static-verifier-rule-gaps.adoc`, `pm-workflow/.../workflow/engine/WorkflowEngine.java` (two
`new VerificationResult(...)` sites after the constructor change), `pm-workflow/src/test/java/.../workflow/diagram/DiagramFixtures.java`,
`pm-workflow/src/test/resources/unit-fixtures/defaults/remediation-qgate-findings.pmwf`. None touches the declared surface of
the running PLAN-09 (`core.store`, `runtime.start`) or PLAN-24 (`workflow.plan`).

## Metrics and Anomalies

- Tokens: 5,809,578 (landing-facts). The retrospective puts 6-finalize at 2.66M of 5.37M (49%). The two totals differ; read them with the epic's token-overstatement watch in mind.
- Duration: 20,177 s (about 5.6 hours).
- Anomalies:
  - Three code-review rounds; two blocking fail-opens were introduced by the plan's own corrections (lesson 001).
  - The pre-push gate's module-tests arm was DEGRADED (`derive_gate_bundles` resolves no Maven module); all four project gates ran green on 5523a90.
  - Finalize dispatches left no boundary file and some `[DISPATCH]` lines are missing (lesson 007).
  - The `archive-plan` step was still `pending` at emit time.

## Routing and Merge Behavior

- Review:
  - The code review went 3 rounds. Round 1 fixed a Rule 9 loop-back fail-open (8c3130d); round 2 fixed the STEP_LIST distinct-count fail-open (869b753); round 3 had 0 blocking findings.
  - The advisory fix 5523a90 changes wording only and shipped without another round by operator decision, so the record reads `operator_override` at 5523a90.
  - CodeRabbit's major comment that ARCHIVE_STEPS should require commit and land in one cycle was rejected with a reply, citing ADR 022 decision 2.
  - Sourcery declined on its review budget.
- CI/merge: merged through the merge queue as 7981d17. No collision with the running PLAN-09 or PLAN-24 was reported. The report notes that PLAN-17 (shipped) overlaps in `workflow.verify` and now builds on this change.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-56 --status shipped`
- [x] row `pr` stamped — `#66`
- [x] row `landing` stamped — `landings/PLAN-56.md`
- [x] row `plan_marshall_plan_id` stamped — `static-verifier-residuals`
- [x] epic.md narrative reconciled: the Open Defect "Static verifier residuals after PLAN-54" is retired; documentation defect (b) and the Rule 3 issue watch are retired (#72 merged, #71 filed); the queue annotations for PLAN-44/54/56 are updated
- [x] watch added: specification issue #71 open; the Rule 3 configuration-reachability seam remains
- [x] resume anchor updated
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- Ten candidate lessons (inbox 001–010) concern plan-marshall components and are routed to the plan-marshall `lessons-routing` inbox. Two pairs are merged (003+008 cross-repo deliverables, 005+010 Maven pre-push gate scope), and each recurrence names the earlier routed message it repeats.
- Issue #71: once it is answered, a follow-up package replaces the Rule 3 seam with the specified activation (watch).
