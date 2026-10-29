# Landing Analysis: PLAN-42 — Operator wait and machine scope

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #199 (code, merged as 915a6353) and #203 (documentation, merged as 7383e045)

> Landing record for one shipped plan, delivered in **two pull requests at the operator's direction** (CodeRabbit refused #199 at 106
> files, its limit is 100; the lane split the documentation into #203). Source: inbox message `rb-42-operator-wait-and-machine-scope-001.md`
> (runbook lane, opencode, 2026-10-27T12:51:20Z), corroborated against git at both merge commits, the two pull requests through `ci`, the
> SonarCloud public API and the code. Every fact marked "checked" was read by me; no test was re-run, the counts are the lane's.

Standing rules: r1, R01 to R15, R17, R18 named by the landing. **R05 departure:** the package file, its index row and the dropped names
in the _Depends on_ cells are in #203, not in the plan's own code pull request (operator-authorised). R19 (class-level surface, r2)
was not in force when the lane started (the lane read r1) and is not applied here. The lane read the **ledger worktree copy** of its spec
(`spec_source: ledger_worktree`, `spec_differ: true`: the main-checkout copy was older by the PLAN-34/PLAN-66 note, the ADR numbers
and the runbook adaptation, because #195 had not landed at its start).

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 Workflow DSL § Waiting for the Operator and § Machine Scope (code) | shipped-as-specified | Checked: `EngineEvent.OperatorAct(stateId, linkId, parameters)` (`EngineEvent.java:135`), `AwaitedEvent` gains the operator act, `VerificationRule.OPERATOR_LINKS_APART(10)` replaces `SESSION_DRIVEN_LINKS(10)` (`VerificationRule.java:98`); the session-link fixtures are deleted or renamed (`session-link-forbidden.pmwf` renamed to `operator-link-in-worker.pmwf`, three others deleted). |
| D2 the operator wait | shipped | Checked: `OperatorWaitTest`, `WaiverDeclineTest`, `AdditionalRulesTest` exist; a link state holds no worker, no slot, no deadline; the waiver wait ends by `waive`, `raise` or `decline`. |
| D3 the setup gate | **met at library level**, as the lane says | Checked: `pm-workflow/.../workflow/setup/SetupGate.java` and `MachineOnboarding.java` (the package was package-info only), `SetupGateTest` (9 tests). The callers are `start-from-clients` and `project-onboarding-unit`, not built. |
| D4 the machine scope | **not fully met**, as the lane says | Checked: `JournalScopeType.MACHINE`, `JournalScope.machine`, `LockKey.machineScope`, the machine arms of the stores, `MachineScopeStoreTest`, `MachineResidentUnitTest`. Not met: the resident unit `onboarding.pmwf` (this repository carries no model-facing content, `CLAUDE.md`: owed to `plan-marshall-mcp`) and the machine's condition backoff/hold (the runtime's). |
| D5 removed deadlines and fallbacks | shipped | Checked: `QuestionClassRegistry.defaults()`, `QuestionEvent` without expiry, the waiver outcomes; `WaiverDeclineTest`. |
| D6 schema parity | shipped | Checked: `WorkflowStatusSchemaParityTest` `phasesEqualSchema` and `scopeTypesEqualSchema` without `@Disabled`. |

**Whole diff, checked.** #199 (`git diff 915a6353^1 915a6353`): **77 paths: 7 `A`, 4 `D`, 65 `M`, 1 rename; +1,575/−524**, code and tests only.
#203 (`git diff 7383e045^1 7383e045`): **28 paths: 7 `A`, 1 `D`, 20 `M`; +510/−99**: ADRs `core-store/046`, `workflow-dsl/009`,
`workflow-engine/013`, `workflow-setup/001`, `workflow-verify/031` and `032`, the new developer guide `workflow-setup.adoc`, the package file
`doc/plans/operator-wait-and-machine-scope.adoc` deleted, its row removed (the file is gone, `grep` of the index finds 0 mentions), the
`Depends on` cells of eight plan files edited.

**Surface delta** (`inbox landing-check`, both pull requests together, 105 paths against 29 declared): `expansion_detected`.
**60 undeclared:** mostly test and fixture files of `pm-workflow` (the spec declared the directories at package level, so the tool
counts the files), `AwaitedEvent.java`, `SpiVocabularyTest`, `InstancePruner`, `ProjectUnitLoader`, `StateTransitions`, `question/package-info`,
`pm-workflow/README.adoc`, the guide `workflow-ast.adoc`, eight plan files and `doc/Traceability.adoc`. **3 declared and untouched:**
`model-work/06`, `store-schemas/02` and `workflow-dsl/02` (specification parts the plan did not need to edit).

## Metrics and Anomalies

- **Checked CI:** #199 `success` over 30 checks, SonarCloud `pullRequest=199` gate OK, **0 open issues**, new coverage 87.7; #203 `success`
  over 30, gate OK, 0 open. `branch=main` still 2 open (`S9428` `StoreDigest.java:97`, `S112` `MacSecurity.java:305`), unchanged.
- **Not met as written:** D4's resident unit; D1 and D3's production callers; the Done-when "`BROKEN LINKS 0`" (the lane reports 10, all
  pre-existing sibling anchors on the watch items deleted by #190 and #196: the same figure as PLAN-102's pm-mcp-core form).
- **Review:** two self-review rounds (the findings listed in the message, all fixed), CodeRabbit 5 actionable findings on #199 (parameters
  and binder fixed, the guard, the gate caller and the reconfigure writer answered as other packages' work) and 12 documentation findings on
  #203 (all fixed); Sourcery refused #199 on size, reviewed #203 with no findings. Both merged under the standing review authorization.
- **Process:** the routed build attempt lost `--project-dir` and built the main checkout once; every later build ran `in_process`; the full
  `verify -Pcoverage` reactor exceeded the 1105 s bound (per-module runs reported); the server restarted once during a CI wait and the lane
  resumed from its progress note. One operator question beyond the executor skew: the PR-size split. `total_tokens=unknown` (35th),
  `plugin_version=unknown`.
- **PLAN-42 ran beside three plans for about a day and merged the base twice:** no collision with PLAN-61 (its branch merges cleanly into
  `main` now); **PLAN-73's pull request conflicts in two files of this landing** (`workflow/question/package-info.java`, `AstNodeModelTest.java`).

## Routing and Merge Behavior

Merged through the merge queue, the code first, the documentation after. Both lane worktrees are gone from `git worktree list`.

## Collision Analysis

`corpus declaration-currency` was not rerun for this footprint (the surface refinement of the day has class-level specs): **PLAN-73
conflicts** in `workflow/question/package-info.java` and `AstNodeModelTest.java` (real, see its landing observations); PLAN-61 none.
Specs that cite what changed: **PLAN-48** (`start-from-clients` D2 names `VerificationRule.SESSION_DRIVEN_LINKS(10)` and the
`session-link-*.pmwf` fixtures as OBSERVED surface: the rule is now `OPERATOR_LINKS_APART` and three fixtures are deleted, one renamed), **PLAN-43**
(the same `workflow.ast`/`dsl`/`verify` files PLAN-42 edited), **PLAN-71** (`WorkflowEngine`, `WorkflowStatus*`: the overlap with a running plan is gone),
and the specs declaring `core/store` switch files (the `MACHINE` arm exists). ADR numbers taken: `core-store` 046 (next 047), `workflow-dsl` 009
(next 010), `workflow-engine` 013 (next 014), `workflow-verify` 031 and 032 (next 033), `workflow-setup` 001 (next 002).

## Reconciliation Actions

- Row PLAN-42: `running` → `shipped`; `pr` `#199`; `landing` `landings/PLAN-42.md`; `plan_marshall_plan_id` `rb-42-operator-wait-and-machine-scope`.
- `epic.md`: the queue annotation; the Open Defect "PLAN-42 and PLAN-48 claim one verifier change" is closed on PLAN-42's side
  (PLAN-48 must re-read D2); PLAN-71 is released from the "PLAN-42 and PLAN-71 overlap" hold by the operator's earlier answer: it is
  ready and disjoint from the running plans.

## Follow-Ups

1. The resident unit `onboarding.pmwf` and its aspect sub-workflows (`plan-marshall-mcp`) and the machine's condition backoff/hold (runtime): owed.
2. The callers of the setup gate: `start-from-clients` (PLAN-48) and `project-onboarding-unit` (PLAN-45), both staged behind `token-usage`.
3. PLAN-48's re-read of D2 and of its fixtures (the session-link fixtures are gone).
