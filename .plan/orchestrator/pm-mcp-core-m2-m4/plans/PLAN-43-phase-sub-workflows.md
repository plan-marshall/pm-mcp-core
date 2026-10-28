# PLAN-43: Phases as sub-workflows (the core of the package)

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-43-phase-sub-workflows.md` and is queued as one row file, `queue/PLAN-43.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

> **Split on 2026-10-27** (operator: "Split it into three plans"; its first lane stopped on a non-compiling branch after one session, the plan was too large for one): this plan is now **the core** of the package `phase-sub-workflows`: Deliverables 1 to 4 and 6 below. **Deliverables 5, 7, 8, 9 and 10 moved:** the plan's history (5) and the plan instance as a set of files (the lane's finding B4) to **PLAN-105**, the intent record, the outline record, the simplify ladder and the constraint checks (7 to 10) to **PLAN-106**. The package file and its index row are removed by the **last** of the three plans to land (R01).
> **Scope decisions of 2026-10-27** (the lane's findings B1 to B4 settled by the operator's paste): **B1** the compiled invariants are decided over the call-resolved graph of the composed version (a call edge goes into the callee's entry and from its exit back to the return state; verifier logic only, no inline expansion in storage; no invariant is weakened, `INV-GEN-4`), with the paragraph added to Workflow DSL 04 and 06 in this plan's pull request and matched controls for the cross-phase invariants (`INV-EXEC-2`, `INV-FIN-*`); **B2/B3** this repository builds the engine and language half only: the real default units (`plan.pmwf`, `phase-1-init.pmwf` to `phase-6-finalize.pmwf`, the `land.*` sub-workflow) are `plan-marshall-mcp` resources (its package `default-units-and-drift-tests` is `blocked`), here only the fixtures under `pm-workflow/src/test/resources/unit-fixtures/defaults/` exist: convert them to the phase sub-workflow shape and prove the Done-when over them, name the real units as owed in the landing residue; **B4** moved to PLAN-105. The phase is derived from the phase-unit identity (the operator's answer to the first lane).
> **State after the fourth session (2026-10-28), which supersedes the paragraphs below:** session 4 pushed five commits to `origin/feature/43-phase-sub-workflows` (`b50c368d`, `d0ccf70b`, `073a19ae`, `cb28eb4c`, `51b60077` on top of `092e1b44`; no pull request) and brought the suite from 240 failures to about 10 non-composer failures plus about 21 `PlanInstanceComposerTest` cases. Green: the verifier fixtures, the parser refusals, the loaders and project-step binding, the engine and sub-workflow execution, the lexer, the lane entry ids. **Still red, in the order of `progress.md` § Session 4 (`.plan/temp/rb-43-phase-sub-workflows/progress.md` in the main checkout, untracked):** `CompiledInvariantsTest` `INV-EXEC-1`, `INV-EXEC-2` and `INV-FIN-3` (their refusing cases do not fire over the plan expansion), `ProjectUnitLoaderTest` and `ProjectStepBindingTest` cases, the diagram goldens (the AsciiDoc and terminal goldens still hold the phase block and the `Phase` column; the AsciiDoc renderer prints `Phase value` = `inherit` for a unit without a phase, which is to be removed, since no unit declares a phase). **Not yet written:** the ADRs, the developer documentation, the specification-part deletions, the R05/R06 Traceability lines, the links check (R17), the pull request. **Decided (the composer tests; the orchestrator's recommendation, adopted by the operator on 2026-10-28; the lane does not ask again):** `PlanUnitMerger` and the merged template are removed by this plan's own design, so the `PlanInstanceComposerTest` cases that exercise them are rewritten to the shape this plan leaves or deleted with their subject; this plan keeps only the composition that `PlanInstanceComposer` needs to compile and stay green over the phase-sub-workflow shape. The composition of the phases into the plan instance as a set of files (one instance file per composed unit, deliverable 2's last clause as the package states it) is **PLAN-105's**, which declares `PlanInstanceComposer`, `InstanceMermaidRenderer` and `PlanInstanceComposerTest` in its Expected Surface; do not implement it here and do not leave a test red or disabled for it. A test whose subject only PLAN-105 can deliver is named in the landing residue, not left red. **Stop condition for the next session:** the suite green, the branch pushed, the pull request opened, then the documentation and the review in the same session if capacity remains; commit and push after each group. The real default units stay owed to `plan-marshall-mcp` (`default-units-and-drift-tests`), as before.
> **State after the third session (2026-10-28), superseded by the paragraph above:** the branch holds two more local commits, `33e27a90` (the production half: `CallGraphExpansion`, the phase from the phase unit, depth 5, `PlanUnitMerger`/`partVersions`/`StateNode.phase` removed, renderers and pruner migrated, the default-unit corpus re-cut) and `092e1b44` (the phase clause stripped from 172 fixture files); `compile` and `test-compile` are green, **the suite is red: 298 failures** (a repair of expectations, not a design question). A fresh lane starts from the branch and **`.plan/temp/rb-43-phase-sub-workflows/progress.md` § Session 3 work state** (in `.plan/temp/` of the main checkout, untracked), repairs the groups in the order the file gives (first the `# expect:` line numbers of `verify/refused/*.pmwf` and the inline units, which collapse about 140 failures), **commits and pushes the branch after each group** so no work is held only in the worktree, and then does the ADRs, the guides and the specification and Traceability lines.
> **State of the first run:** branch `feature/43-phase-sub-workflows` holds a WIP commit `d4c1788a` (33 files, the phase member removed, consumers not migrated, **does not compile**) and a merge of `origin/main`; nothing was pushed past the merge; the exact resume list (file and line) is `.plan/temp/rb-43-phase-sub-workflows/progress.md`: **a fresh lane starts from that branch and that file** and finishes the migration (the eight invariant classes, `InstancePruner`, the diagram renderers, the fixtures and about 25 test classes).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`).

## Objective

Implement the work package `phase-sub-workflows` of `doc/plans/phase-sub-workflows.adoc`: phases as sub-workflows, and the plan's history. Serves: roadmap Milestone 3 (the remainder of the loader of project-owned units: the plan unit composed of its six phase sub-workflows); Use Cases, UC-PLN-1; the drill-down of a plan in both clients of `pm-mcp-clients`. Where: pm-workflow (units, composition, engine, static verifier, diagram); pm-core (the plan's history). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/phase-sub-workflows.adoc` § What, in its words (regenerated by cleanup at 99971f3 from the package file).

1. Implements Store Schemas § Phases as Sub-Workflows and the Phase of a Record, State Catalogue § Plan Unit `phases`, and Store Schemas § The Plan's History, with § The Start of the Current Phase (`PM-WF-16`, `PM-IMPL-5`).
2. Phases as sub-workflows: the plan unit (`plan.pmwf`, unit id `plan`, its call states `phases.*`) becomes a workflow of the six phases `init`, `refine`, `outline`, `plan`, `execute`, and `finalize`, each the sub-workflow `phase-1-init.pmwf` … `phase-6-finalize.pmwf` the Phase Workflows specify, with the entries and exits of its frontmatter (`start` for every phase, `rework` of `outline`, `land` and `reentry` of `execute`, `adopt`, `hand_back`, and `post_run` of `finalize`), called by the plan unit at the entry its call state names; the frontmatter, `entry <name> -> <state>`, `call <unit> entry <name>`, and the interface rules are implemented (Workflow DSL Developer Guide § The Unit Frontmatter, Workflow Verification Developer Guide § Interface Rules and Unit Types); a loop-back a call of a phase again, a re-entry from finalize into execute an outcome of finalize. The `phase` clause of a state (`StateNode.phase`, `phase inherit`) and every mapping of a state to its phase go, and with the merge of the phase files `PlanUnitMerger` and `partVersions`; the call stack holds at most five frames, the phase level adding one frame to every call path: the code's limit of four goes up to five in every place that states it, the status store (`WorkflowStatus.MAX_CALL_DEPTH`), the engine's `CallStack.MAX_DEPTH` and its push, and the static verifier's depth rule (`WorkflowValidator`, `VerificationRule`), so a fifth frame is admitted and a sixth refused, statically and at run time alike; the plan instance composes the phases, one instance file per composed unit, and prunes a phase as a call, with its reason.
3. Lanes as a choice of phases: a lane is never a phase. The land lane calls no refine, outline, or plan phase, and its `land.*` states (today `land.pmwf`, run in phase `execute`) become a sub-workflow called within the phase `execute`; the adopt lane calls `init` and then `finalize`.
4. The phase of a sub-workflow: a plan's phase is the phase sub-workflow on its call stack, and every sub-workflow called within a phase (a remediation unit, `land.*`, a chat) has that phase; no unit and no state declares a phase of its own, the entity units included.
5. *(moved to PLAN-105)* The plan's history: an append-only history per plan of states entered and left, sub-workflows called and returned, with their outcomes, the instance version in force and the time; archived with the plan; the runtime derives each node's state from it.
6. Where `workflow-engine`, `plan-instance`, or `project-units-and-invariants` have already implemented the flat plan unit, this package restructures them.
7. *(moved to PLAN-106)* The plan's intent record.
8. *(moved to PLAN-106)* The outline record and the specification parts of the planning phases.
9. *(moved to PLAN-106)* The `review-simplify` restraint ladder.
10. *(moved to PLAN-106)* Constraints and non-goals in the planning phases.

**Done when** (the package's own acceptance): Every phase is a sub-workflow the plan unit calls, and the static verifier and the fuzzer pass over the default units; the plan unit enters each phase at the entry its call state names; a test per lane shows the phases it calls and prunes, the land lane its `land.*` sub-workflow within `execute`; a sub-workflow called within a phase reports that phase; a call path of five frames is accepted by the static verifier and pushed by the engine, and one of six refused by both. (The history, the instance files, the intent and outline records, the simplify ladder and the constraint checks are the Done-when of PLAN-105 and PLAN-106; "the static verifier and the fuzzer pass over the default units" is proven over the fixtures of this repository, the real units are owed to `plan-marshall-mcp`.) **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-workflow`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names `PM-WATCH-CLIENT-13`, `PM-WATCH-DISP-33`, `-43`, `-48`, `-55`, `-64` or `-65` is the sibling's own links to watch items deleted by #190 and #196, not a defect of this change (PLAN-42 and PLAN-102 recorded 10 and 2798 as the pre-existing figures). **Size:** CodeRabbit refuses a pull request over 100 files (PLAN-42 split its documentation into a second pull request at the operator's direction): this plan touches about 80 fixtures and 40 test classes, so ask the operator at the plan step whether to split code and documentation before opening the pull request.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use case: UC-PLN-1
- Requirements: PM-WF-16; PM-IMPL-5
- Specification: the sections the first bullet of _What_ names; Workflow DSL § Workflow Definition Units; the interface rules, implemented: Workflow Verification Developer Guide § Interface Rules and Unit Types; § Reusable Sub-Workflows; § Plan Instance; Phase Workflows; § Land Lane; § `satisfies` Coverage; § Q-Gate & Execute; § Finalize Step Ordering Bands; § Builder Check; Store Schemas § Intent Record; § Outline Record; § Capability Spec; § Epic Header; Filesystem Layout § The Source Directory; Hypermedia Format § Link Grammar; Identifiers § Registry Stores to Web
- Watch: PM-WATCH-PHASE-106; PM-WATCH-PHASE-107; PM-WATCH-PHASE-110; PM-WATCH-PHASE-111; Phase Workflows

## Specification Added Since The Package File

Merged outside the ledger on 2026-10-21 by the operator, specification only: #134 `50322a7` (from the analysis `barely-read-the-code` of `plan-marshall-documentation`). The package file does not name it; this plan carries it (carried by the cleanup of 2026-10-22). The executing plan reads the named parts at its branch point and builds what they now say; the package file is deleted with the package as usual.

- **Trade-offs nobody named, at refine.** The refine task asks for them: a goal that improves one measure (speed, throughput, size, cost) names the measures that must not get worse, each an `unchanged` criterion or a constraint with its bound; a rewrite, a port or a replacement states whether what existing callers observe stays the same, as `unchanged` criteria or a non-goal that says it does not; a change that adds or changes a queue, a cache, a buffer, a retry or a limit states what happens when the limit is reached (what is dropped, delayed or refused) as a criterion, or asks the operator when nothing in the sources says it — `doc/specification/phase-workflows/01-planning-phases-1-2.adoc`, the refine task.
- **The builder check asks three questions.** The role skill of the builder check (class `guidance`) names the three questions a builder of a performance, rewrite or capacity change asks when the record is silent on them: which measures must not get worse while the goal's measure improves, whether existing callers observe the same behaviour afterwards, and what happens when a limit the change adds or changes is reached. A silence the record cannot answer is an `operator` question, never the builder's `minor` call — the same part, the self-scoring of refine.

Folded into the deliverable on the constraints and non-goals of the planning phases (the intent record's `unchanged` criteria, `CN-<n>` and `NG-<n>`); no new deliverable. A scenario per class of change (an improvement of one measure, a rewrite, a limit) whose record is silent: the refine task asks, and the builder check files an `operator` question, not a `minor` one.

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/phase-sub-workflows.adoc` § What, Trace, Done When on `origin/main` at `4617865` (10 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: doc/plans/phase-sub-workflows.adoc unchanged since 88ec9c70 (last c3a4a6f8, #168); its bullet 10 adds per decision DC-n (followed with evidence, departed as a finding, or not_verified), spec D10 lacks it
- OBSERVED: the package file and its index row (`open`; depends on PLAN-42 (`operator-wait-and-machine-scope`)) — read at `doc/plans/phase-sub-workflows.adoc` and `doc/plans/README.adoc` on `origin/main` at `4617865`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row open, depends on operator-wait-and-machine-scope only; PLAN-42 not shipped
- OBSERVED: the call-graph depth limit of 4 (Rule 7, `CALL_GRAPH_DAG`) shipped with PLAN-41 (#34); this package grows the call depth by one — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: VerificationRule.CALL_GRAPH_DAG maximum depth 4; WorkflowStatus.MAX_CALL_DEPTH = 4; CallStack.MAX_DEPTH; WorkflowValidator message says 4
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line; the plan's history lands in `core.store` — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: no plan-history type in core.store; mcp/history.jsonl is the ADR core-store/044 append idiom, which supports core.store but is not proof
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: the refutation stands: the package file carries DC-n, spec D10 does not; row open with PLAN-42 still listed
- OBSERVED: the specification part above carries the added content — the bullet *Trade-offs nobody named* (refine) and the paragraph on the three questions of the builder check in `phase-workflows/01-planning-phases-1-2.adoc` — read at `4617865`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: phase-workflows/01 Trade-offs nobody named and the three builder-check questions still present; #189 and #192 touched only the source-lesson row and the builder-check operator bullet

## Expected Surface

- OBSERVED: `doc/plans/phase-sub-workflows.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/state-catalogue/18-plan-unit-axes-short-landings.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/02-workflow-state-store.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/01-grammar.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/02-units-jobs-decisions.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` — § Positioned Layout, read, not edited
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/StateNode.java` — D2: the phase member goes
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/WorkflowDefinition.java` — D2: partVersions goes
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/ast/CallFrame.java` — D2/D4: callerPhase goes with the phase mapping
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/WorkflowParser.java` — D2: the phase clause and phase inherit go
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/WorkflowPrinter.java` — D2: prints the phase clause today
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/PlanUnitMerger.java` — D2: removed with the merge of the phase files
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/DefaultUnitLoader.java` — D2: merges PHASE_FILES today
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/ProjectUnitLoader.java` — D2: merges the project's phase files today
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/UnitFinding.java` — D2: refers to PlanUnitMerger.PLAN_UNIT
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/CallStack.java` — D2: MAX_DEPTH to five
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/WorkflowEngine.java` — D2/D4/D5: phase mapping goes, the history is written here
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java` — D2: CALL_GRAPH_DAG depth five
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/WorkflowValidator.java` — D2: depth rule, phase inherit check
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/CompiledInvariants.java` — D2: PHASE_* constants keyed on state.phase()
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/InitInvariants.java` — D2: reads state.phase()
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/RefineInvariants.java` — D2: reads state.phase()
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/OutlineInvariants.java` — D2: reads state.phase()
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/PlanInvariants.java` — D2: reads state.phase()
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/ExecuteInvariants.java` — D2: reads state.phase()
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/FinalizeInvariants.java` — D2: reads state.phase()
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/RemediationInvariants.java` — D2/D4: reads state.phase()
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/SubWorkflowInvariants.java` — D2: copies StateNode with its phase
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/InstancePruner.java` — D2: prunes a phase as a call; copies StateNode with its phase
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/MermaidRenderer.java` — D2: groups by state.phase(); phase drill-down
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/AsciiDocTableRenderer.java` — D2: groups by state.phase()
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatus.java` — D2: MAX_CALL_DEPTH to five
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatusCodec.java` — D2/D4: the phase member of the status
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/PlanUnitMergerTest.java` — deleted with PlanUnitMerger
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/DefaultUnitLoaderTest.java` — loader without the merge
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/ProjectUnitLoaderTest.java` — loader without the merge
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/ProjectStepBindingTest.java` — uses PlanUnitMerger
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/WorkflowParserTest.java` — phase clause and partVersions in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/WorkflowParserRefusalTest.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/WorkflowPrinterTest.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/WorkflowLexerTest.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/AstWithoutLocations.java` — builds StateNode with phase
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/ast/AstNodeModelTest.java` — StateNode, CallFrame, WorkflowDefinition constructors
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/WorkflowEngineTest.java` — phase of the status, history
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/SubWorkflowExecutionTest.java` — depth five/six, phase of a called sub-workflow
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/WorkflowEnginePhaseParityTest.java` — the phase mapping goes
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/EngineFixtures.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/EngineResumeTest.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/EngineEventTest.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/GuardEvaluatorTest.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/LoopBackBudgetTest.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/WorkflowValidatorTest.java` — depth rule five/six
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/AdditionalRulesTest.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/VerificationResultTest.java` — pins CALL_GRAPH_DAG
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/CompiledInvariantsTest.java` — phase-keyed invariants
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/InvariantFixtures.java` — phase clause, WorkflowDefinition constructor
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/DeclarationBoundsTest.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/TestPrimitives.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/InstancePrunerTest.java` — prune a phase as a call
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanFixtures.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/WorkflowDiagramGeneratorTest.java` — phase drill-down
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/DiagramFixtures.java` — phase clause in fixtures
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioFixtures.java` — phase clause in fixtures (also PLAN-35's package)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusCodecTest.java` — phase and depth of the status
- HYPOTHESIS: `pm-workflow/src/test/resources/dsl/` — every fixture with the phase clause
- HYPOTHESIS: `pm-workflow/src/test/resources/verify/` — every fixture with the phase clause
- HYPOTHESIS: `pm-workflow/src/test/resources/unit-fixtures/defaults/` — the phase part files (init, refine, outline, plan, execute, finalize, land) become phase sub-workflows

## Re-grounding Record (2026-10-26, HEAD `f4a1bd07`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `f4a1bd07`. This section is the **latest** reading; the earlier section below stays as the audit record, and **where they differ, this section is the later reading**.

- Claims re-read: 6: 3 corroborated, 2 contradicted (re-scoped by this section), 1 unverifiable. Verdicts are stamped on the claims above (`checked_at: f4a1bd07`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claims 0 and 4: `doc/plans/phase-sub-workflows.adoc` (last `c3a4a6f8`, #168) carries in its bullet 10 "per decision DC-n (followed with its evidence, departed as a finding, or not_verified)"; spec D10 lacks it. Bullets 1 to 9, Done-when and Trace match. The row is open with PLAN-42 still listed.

### Surface

- Added to the Expected Surface above: `workflow-finalize` guide and ADR directory, `workflow-ast` ADR directory, `workflow-dsl/07`.
- wrong: the ADR directories of `workflow-unit`, `workflow-engine`, `workflow-verify`, `workflow-plan`, `workflow-diagram`, `core-store` are annotated "new if absent"; all exist (last 006, 012, 030, 017, 011, 045).

### Specification changes carried (not yet in the Deliverables above)

- **#192 (`2dba06fd`) — Phase Workflows 01 § Builder Check, the `operator` question bullet: a goal the criteria do not reach is always an operator question**, restated as the implementation and what of the goal it leaves undone, naming the goal and the criteria it passes; a second fixture (goal "an export completes for every account") is added to `PM-WATCH-PHASE-111` (present at phase-workflows.adoc:1652). The package file, spec D8, D10 and the Done-when do not carry it: add it to D8 and D10 at the outline.
- Add the DC-n clause to D10 (above).
- #186, #191 — Store Schemas 03 (`security`, `submitted_severity`, Located Finding Grounding, `credential_shaped`), Phase Workflows 04 and Workflow DSL 02 (`security_severity_caps`): owned by `security-audit-and-code-scanning`; this plan deletes only § Phases as Sub-Workflows from `store-schemas/03` and must not touch those. #189 — the `source-lesson` row is `start-source-intake`'s; `FindingSourceKind.ADDED_LINES` exists.
- #181 — Workflow DSL 07 § Positioned Layout: a graph is one unit of the plan instance in force (the plan unit or one phase sub-workflow), addressed by the process tree's `node_id`; **no package owns it**.

### Shipped or superseded since the last grounding

- #185 gives the `ScopeTransaction.append` idiom for `history.jsonl` (ADRs `core-store/044`, `045`); PLAN-31's `ModelSlots` and #190's `ModelSlots` allow D9's test "never takes the plan's writer slot". Nothing delivers the history, the phases or the intent record. Dependencies: PLAN-42 open.

### Open questions for the outline

- Does the builder-check goal question (#192) apply to a chat or re-planning revision (D7 and D8 carry only the submit path)? Who builds the Positioned Layout of a unit? Do not delete or implement the `02-planning-lanes-phases-3-4` rows owned by `finalize-pipeline-and-merge`.

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 6: 3 corroborated, 2 contradicted (re-scoped by this section), 1 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: Package bullet 10 changed at HEAD by #168 c3a4a6f8: the self-review also returns per decision DC-n followed with evidence, departed as a finding, or not_verified; spec Deliverable 10 and Done-when still name only CN and NG; the other 9 bullets and Trace equal the spec.
- Claim 4: doc/plans/phase-sub-workflows.adoc last changed in c3a4a6f8 (#168) not 72aec5cb; the row is still open and PLAN-42 still listed; open-PR list not re-read.

### Surface

- Added to the Expected Surface above: `pm-core/src/main/java/de/planmarshall/core/spi/validation/OutlineValidator.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/finalize/`, `pm-workflow/src/main/resources/de/planmarshall/workflow/plan/composition-rules.json`, `pm-workflow/doc/developer/workflow-ast.adoc`, `pm-core/doc/developer/core-spi.adoc`, `doc/specification/store-schemas/06-task-and-references.adoc`, `doc/implementation-watch/phase-workflows.adoc`, `doc/specification/phase-workflows/04-finalize-step-bands.adoc`.
- understated: Code surface to add: pm-core/src/main/java/de/planmarshall/core/spi/validation/OutlineValidator.java (deliverable 10, markdown affected-files validator today), pm-workflow/src/main/java/de/planmarshall/workflow/finalize/ (FinalizeStepDescriptor, FinalizePipelineValidator, bands of deliverable 9), pm-workflow/src/main/java/de/planmarshall/workflow/verify/SchemaRegistry.java (no intent-record or outline-record in defaults()), pm-workflow/src/main/resources/de/planmarshall/workflow/plan/composition-rules.json (default:finalize-step-simplify); docs pm-workflow/doc/developer/workflow-ast.adoc and workflow-dsl.adoc with ADR dirs workflow-ast and w…
- understated: Specification parts the deliverables name but the surface omits: doc/specification/store-schemas/06-task-and-references.adoc (Intent Record line 205, Outline Record 391, Capability Spec 792), store-schemas/04-epic-and-change-ledger.adoc (Epic Header line 68), phase-workflows/03-q-gate-and-execute.adoc, phase-workflows/04-finalize-step-bands.adoc (Finalize Step Ordering Bands, DC results), hypermedia-format/02-link-grammar.adoc, filesystem-layout/03-project-store.adoc (Source Directory line 153), state-catalogue/03-plan-phases-4-5-land.adoc (Land Lane line 184).
- understated: Watch: add doc/implementation-watch/phase-workflows.adoc (PM-WATCH-PHASE-106, 107, 110, 111; 111 extended by #168); the trace names it but the surface does not.

### Specification changes carried (not yet in the Deliverables above)

- #168 — phase-workflows/04-finalize-step-bands.adoc step pre-submission-self-review and watch PM-WATCH-PHASE-111 (self-review judges every decision DC-n): Extend Deliverable 10 and the Done-when with per decision DC-n results (followed with evidence, departed as a finding, not_verified) and refusal of an omitted DC invalid_link_args; the package file already carries it since c3a4a6f8. Add doc/specification/phase-workflows/04-finalize-step-bands.adoc and doc/implementation-watch/phase-workflows.adoc to the Expected Surface.
- #175 — phase-workflows/01-planning-phases-1-2.adoc A fix is tested (line 453): Fold into the refine and outline deliverables: a fix criterion is verified by test where an affected module has a test framework in its domain (its build reports test counts), else the criterion states why no test can hold it; the satisfying deliverable writes the test that fails on an assertion before the fix; give it a matched fixture beside PM-WATCH-PHASE-110. Paths: doc/specification/phase-workflows/01-planning-phases-1-2.adoc (already listed) and pm-core/src/main/java/de/planmarshall/core/spi/validation/OutlineValidator.java.

### Shipped or superseded since the last grounding

- partly shipped dependency: #150 ac347032 made icon a required frontmatter key (UnitFrontmatter.icon, ADR workflow-dsl 008); the depth limit is still 4 in WorkflowStatus.MAX_CALL_DEPTH, CallStack.MAX_DEPTH and WorkflowValidator; StateNode.phase and PlanUnitMerger.partVersions still exist, so no deliverable is fixed.

### Open questions for the outline

- The 02-planning-lanes-phases-3-4.adoc part is listed whole, but #141 added rows owned by finalize-pipeline-and-merge (the Merge Danger section of the PR body, the ADR number at landing); the plan must not delete or implement them.

### Second pass (HEAD `8233fffc`, after #177 and #179)

The specification pull requests #177 (capability specs: Proportion, Amended after refine, Verified by, the Capability Test Guard, the replan triggers `capability_conflict` and `intent_amended`) and #179 landed after the first pass. 6 claims were re-read: 3 corroborated, 2 contradicted (still re-scoped by this record), 1 unverifiable; stamped `checked_at: 8233fffc`.

Specification changes carried (not yet in the Deliverables above):

- #177 — phase-workflows/01 Intent Record bullet Proportion (record as large as the change on either lane; a fix is its triple; one defect is one criterion; bound 30 is a ceiling): Fold into Deliverable 7 and the refine task: add the Proportion rule with a matched scenario (a one-defect fix yields its triple and test and not several criteria); the maxItems 30 already stands in store-schemas/06 (line 253); no new path since phase-workflows/01 is on the surface; the Planning Weight figure it points to is in phase-workflows/05 and is not PLAN-43 scope
- #177 — phase-workflows/02 Outline role skill paragraph (outline as large as the change: no deliverable or affected file or verification for behaviour the record does not name): Fold into Deliverable 8 (outline phase content) as a guidance-class role skill statement; phase-workflows/02 is already on the surface so no new path
- #177 — phase-workflows/01 Revisions sentence and Amended after refine; phase-workflows/07 Intent Amendments changes member; store-schemas/02 amendments[].changes: Fold into Deliverable 7 revision rule: an amendment naming a capability-bound criterion is folded into a new revision before the capability specs are written; folded means a revision accepted after the amendment at (the intent store revision already has submitted_at in store-schemas/06); the trigger intent_amended itself belongs to re-planning and domain-extensions-and-architecture so PLAN-43 only carries the submit path; store-schemas/02 is already on the surface and phase-workflows/07 is to be added (exists at HEAD)
- #177 and #179 — phase-workflows/04 simplify row (server checks a deletion only where a capability requirement names the test) and architecture-refresh row; phase-workflows/05 Capability Test Guard; store-schemas/03…: Not PLAN-43 scope except the simplify row: Deliverable 9 should state that a finalize.simplify-apply that deletes a test named in a requirement verified_by is judged by the guard after finalize.settle-commit (capability_test_removed) and is never an exemption; add to Open questions that PLAN-43 must not delete or implement the architecture-refresh row text, the Capability Test Guard section, the capability_tests source enumeration value, verified_by or the two watch items (owners unstaged: domain-extensions-and-architecture and finalize-pipeline-and-merge); the files already sit on its surface whole

Applicability:

- no deliverable becomes done or impossible or duplicated; D7 and re-planning share a boundary: no source for #177 or #179 content exists (git grep for intent_amended and capability_conflict and capability_test and verified_by and planning weight in Java and JSON finds only the raw amendments member in WorkflowStatusCodec line 151); spec 08 now requires a revised_intent for intent_amended so Deliverable 7 (a re-planning that changes intent carries the record) supplies the submit path while the trigger stays in the re-planning package

## Dependencies and Sequencing

- After #150 (`ac34703`, 2026-10-23): `icon` is a required key of every unit's frontmatter (`UnitFrontmatter` carries `String icon` after `type`; grammar `[a-z][a-z0-9._-]{0,63}`, no `..` segment; the printer prints it after `type`; `PlanUnitMerger` and `PlanInstanceComposer` keep the icon of the part or template they merge or compose; format 1, no compatibility path). Every unit this plan writes or restructures (the six phase sub-workflows, `plan.pmwf`, `land.pmwf`) and every fixture or inline unit of its tests carries an `icon:` line. The default units live in `plan-marshall-mcp`, and their `icon:` lines wait for its package `default-units-and-drift-tests` (`blocked`): the clause that runs the verifier and the fuzzer over the default units needs that package. The `workflow-dsl` ADR directory exists (ADRs 001 to 008): the next free number is 009 against `origin/main` (R11).
- Depends on: PLAN-42 (`operator-wait-and-machine-scope`, shipped: #199 `915a6353` and #203 `7383e045`); nothing else
- Blocks: PLAN-105 (`plan-instance-files-and-history`, sequenced after this plan); PLAN-50 (`recipe-workflows`); PLAN-55 (`chat`); PLAN-62 (`process-designer`); PLAN-67 (`process-and-overview`); transitively 16 of the open packages (the largest blocker after `token-usage`)
- Runs beside (runbook adaptation 2026-10-27, standing rule R19): PLAN-61 (`token-usage`, running) and PLAN-97 (`endpoint-harness`, emitted with this plan); the Expected Surface above is class level and shares **no file** with either. **PLAN-71 (`plan-instance-versions`) is not emitted beside it: it shares 14 real entries with this plan** (`WorkflowEngine`, `PlanInstanceComposer`, `EngineFixtures`, `PlanFixtures` and their tests, `store-schemas/02`, `workflow-dsl/09`): PLAN-71 follows when this plan has merged. **Read at the head of `main` before editing:** PLAN-42 (#199) changed `workflow.engine`, `workflow.verify` (`VerificationRule.OPERATOR_LINKS_APART(10)` replaced `SESSION_DRIVEN_LINKS(10)`, the session-link fixtures are deleted or renamed), `workflow.ast`, the parser, the printer and `workflow.setup`; PLAN-73 (#201) changed `StateBehavior`, `WorkflowParser`, `WorkflowPrinter`, `InstancePruner`, `SubWorkflowInvariants`, `AstNodeModelTest` and `AstWithoutLocations` (`ask fresh`): **these are the files this plan edits to remove the `phase` member**, so the fixture count and the file list of the Expected Surface may be off by a few. **ADR numbers are taken at write time** against `origin/main` (R11): `workflow-unit` 007, `workflow-engine` 014, `workflow-verify` 033, `workflow-plan` 018, `workflow-ast` 003, `workflow-diagram` 012, `workflow-finalize` 004, `workflow-dsl` 011, `core-store` 048, `core-spi` 002. Merge the base branch (the lane's step 7) before the merge gate.
- Restructures: the flat plan unit of PLAN-13, PLAN-17 and PLAN-24; Rule 7's depth limit of PLAN-41

## Re-scoping (2026-10-27, settled: the record of why the first run stopped)

> **Settled:** B1, B2/B3 and B4 are answered in the scope-decisions block at the head of this spec; the split below was applied as three plans (this core, PLAN-105, PLAN-106), with the units half left to `plan-marshall-mcp`. The text that follows is the history, kept so a lane knows what the first run found; where it says "parked" or "not applied", the head of the spec governs.

The lane started on 2026-10-27 (row `running`), stopped before any implementation and filed `rb-43-phase-sub-workflows-001.md` (0 of 10, `implement:blocked`); the operator **parked** the row ("Park and re-scope it"). The lane's branch `feature/43-phase-sub-workflows` is pushed empty and its worktree holds 15 uncommitted, non-compiling files of a partial D2 (removal of `StateNode.phase`, the phase derived from the phase-unit identity as the operator chose, a `Phase` type in `workflow.ast`; the file list is in `.plan/temp/rb-43-phase-sub-workflows/progress.md`): reuse or discard, the decision follows the re-scope.

Four findings, read by the orchestrator against the repository and the sibling index:

- **B1 — the compiled phase invariants have no stated semantics over separate units.** `INV-OUTLINE-1`, `INV-PLAN-1`, `INV-EXEC-1..3`, `INV-FIN-1..6`, `INV-REM-5` (`workflow-dsl/04` § Compiled Invariants) are written as "every path into `phase-4-plan` ..." over the plan unit's graph; with seven phase units joined by `call`, each cross-phase edge of today's merged unit (`execute.pmwf` to `finalize.hand-back` and to `outline.deep`, `plan.pmwf` to `outline.deep`) becomes an exit plus a call. `INV-GEN-4` forbids weakening a guarantee. **A decision for the specification, not for a lane:** the natural reading is that the invariants are decided over the call-expanded graph of the composed plan instance (a call is inlined with its frame), which keeps every guarantee as written; the alternative (per-unit invariants with declared exits) weakens them. Either is a text change in `workflow-dsl/04` and `/06`.
- **B2 — the Done-when clause "the static verifier and the fuzzer pass over the default units" cannot be proven in this repository.** The default units are resources of `plan-marshall-mcp`'s `pm-mcp-server`; its package `default-units-and-drift-tests` is `blocked` (index of `plan-marshall-mcp`, read 2026-10-27), and `pm-workflow/src/main/resources` holds only `composition-rules.json`. The clause can only be proven over the fixtures here. (The lane says the spec's Dependencies record the package: they do not; this spec names no such dependency.)
- **B3 — the unit authorship has no home here.** D2's plan-unit restructure, D3's lanes and its `land.*` sub-workflow and every `.pmwf` the deliverables name are model-facing content that lives in `plan-marshall-mcp` (`CLAUDE.md` forbids `workflows/` in a JAR of this repository). This repository carries the AST, the parser, the printer, the verifier, the engine, the composer and the fixtures. This is the defect of the eleven specs of the Open Defect of 2026-10-25; this spec was not on that list and should have been.
- **B4 — "one instance file per composed unit" has no contract.** `PlanInstanceComposer` composes one flat unit per version and `PlanInstanceStore` writes one version file plus content-addressed sub-unit texts; the instance model, its verification and the pruner's "prunes a phase as a call" are one clause of the package file, and changing them changes the store layout (R07).

**Proposed split (as proposed then; applied on 2026-10-27 as the three plans named at the head, B4 going to PLAN-105):** (a) **the engine and language half in this repository**: the `phase` member and `partVersions` removed from the AST, the call-depth limit 4 to 5 in `WorkflowStatus`, `CallStack` and the verifier, the parser and the printer, the invariants decided over the call-expanded graph (B1), the instance model and store (B4), with the fixtures converted; acceptance over the fixtures; (b) **the units half in `plan-marshall-mcp`**: the seven phase sub-workflows and the plan unit as resources, the `land.*` sub-workflow, owned by `default-units-and-drift-tests` (a package there, not a plan of this epic), with the fuzzer and verifier clause. PLAN-71 (`plan-instance-versions`), which shares 14 entries with (a), would follow (a); PLAN-50, 55, 62, 67 depend on the whole package.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-43-phase-sub-workflows.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
