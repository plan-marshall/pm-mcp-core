# PLAN-70: Plan instance aligned with the specification: prune reasons, shipped rules, plan axes, header, and diff; lane-route pruning at the `outline` event

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-70-plan-instance-alignment.md` and is queued as one row file, `queue/PLAN-70.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `plan-instance-alignment` of `doc/plans/plan-instance-alignment.adoc`: plan instance aligned with the specification: prune reasons, shipped rules, plan axes, header, and diff; lane-route pruning at the `outline` event. Serves: Roadmap Milestone 3 (the plan instance: the prune reasons `plan_default` and `merge_by_operator` in place of the shipped rules on `merge.auto_merge` and `recipe_id`, the adjudication unit in the header, `diff_summary` of the composition log; the lane-route pruning at the `outline` event). Where: pm-workflow package `workflow.plan` (`PruneReason`, `CompositionCause`, `composition-rules.json`, `CompositionRules`, `PlanFacts`, `PlanInstanceComposer`, `HeaderSchemaCheck`, `CompositionRecord`) and package `workflow.verify` (the declaration schemas of the method and asking variants in `DeclarationBounds`; `CompiledInvariants`); the default plan unit and its templates in `plan-marshall-mcp`; the specification in `plan-marshall-documentation`. The package is the merge of the former `plan-instance-alignment` and `lane-route-pruning` that PLAN-75 (#85) made on 2026-10-17; the spec of PLAN-65 is superseded by this one. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

The package file introduces itself: The composer of `plan-instance` implements an earlier form of the header and of the shipped rule file. This package aligns the code with Store Schemas § Plan Instance, § Composition Rules, and Workflow DSL § Plan Behaviour Axes as far as the composition reads them, without a deprecation (pre-1.0). The engine's side of the versions is `plan-instance-versions`'s. To be confirmed by the § Discussion:

## Deliverables

The bullets of `doc/plans/plan-instance-alignment.adoc` § What at `c281f6c`, in its words.

1. The closed reasons: `PruneReason` loses `recipe_override` and `auto_merge_off` and gains `plan_default` and `merge_by_operator`, so it equals the `prune_reason` enum of the header schema (`posture`, `provider_unconfigured`, `footprint_disjoint`, `change_type_exempt`, `local_only`, `land_lane`, `adopt`, `merge_by_operator`, `plan_default`, `returned`, `handed_back`, `empty_harvest`); `CompositionCause.FallbackCause` loses `question_expired` (no decision ends by time) and `auto_merge_off`, and gains `merge_by_operator` and `files_not_applicable`, so it equals `fallback_cause` of the composition record; `HeaderSchemaCheck` and the header's element `call` (a pruned call of a phase sub-workflow or an axis variant) follow.
2. The shipped rule file: the rules `auto-merge-off` (fact `merge.auto_merge`) and `simplify-recipe` (fact `recipe_id`, reason `recipe_override`) leave `composition-rules.json`, and with them the configuration fact `merge.auto_merge` of `CompositionRules` and `PlanFacts.RECIPE_ID_FACT`; the simplify recipe is its own workflow (`recipe-workflows`). A new rule prunes the merge execution (`finalize.merge-dispatch`, `finalize.merge-queue-wait`, `finalize.merge-queue-exit`) with the reason `merge_by_operator` where the bound asking variant declares `self_merge: false`, its source fact the bound variant; the file's `revision` is raised.
3. The plan axes in the composition: a rule's `when` may read the variant bound on a behaviour axis and its declarations; the declaration schemas of the method variants (`order`) and the asking variants (`wait_after`, `outline_review`, `self_merge`) join the compiled schemas of `DeclarationBounds`; the composer binds the variant of each axis from the plan's own value (`overrides.json`), else the project's, else the machine's, where a level sets it, and prunes the variants not chosen with the reason `plan_default` as pruned calls; `overrides.json` is read as a plan fact and counted in `plan_facts_digest`; a value of the plan set or changed later composes a version with the event `override`, refused with `position_not_contained` where it would not contain the plan's position. Writing the machine's and the project's values and the plan's overrides is `machine-onboarding-unit`'s and `plan-and-epic-control`'s; the landing variant composed into the file of `phase-6-finalize`, and one composed file per phase sub-workflow and bound variant, are `phase-sub-workflows`'s.
4. The adjudication unit in the header: `adjudicate.decisions`, which no call site calls and the engine runs for the decisions of the version, is listed in `inputs.sub_units` with its digest and its level, and its verified text is stored like every bound sub-unit.
5. `diff_summary`: each composition record of a composed version carries the pruned elements added and removed, the ceilings changed, and the gates waived or unwaived relative to the previous version, `null` for version 1; the specification names only the content, so the package states the members of the object in the schema of `plan-marshall-documentation` before it writes them.
6. The documents: the developer guide of `workflow.plan` and ADR `workflow-plan/008` without the shipped rules on `merge.auto_merge` and `recipe_id`; in `plan-marshall-documentation` the parts of `PM-IMPL-5` and of the roadmap item of the plan instance that this package implements.
7. The per-lane rework and replan routes in the State Catalogue and in the plan templates of `plan-marshall-mcp`.
8. `INV-INIT-2` reworded per lane, in the specification and in `CompiledInvariants`.
9. The prune reason `other_lane` in `PruneReason`, `HeaderSchemaCheck` and the header schema.
10. Reachability pruning at the `outline` event in `workflow.plan`, with a test per lane and a matched control: a `deep` version without the light route verifies; a `light` version keeps the deep route.
11. The corrected `outline` event sentence; the developer guide of `workflow.plan` without the limitation.

**Done when** (the package's own acceptance): The § Discussion is decided with the operator first and recorded as an ADR. `PruneReason` and `FallbackCause` equal their enums of the specification, and a header or a composition record with `recipe_override`, `auto_merge_off`, or `question_expired` is refused; the shipped rule file has no rule on `merge.auto_merge` or `recipe_id`, and a fixture plan whose asking variant declares `self_merge: false` loses the merge execution with `merge_by_operator` while one with `self_merge: true` keeps it; a fixture with an axis set at plan, project, and machine level binds the plan's variant and prunes the others as calls with `plan_default`, and an override after the start composes an `override` version or is refused with `position_not_contained`; the header lists `adjudicate.decisions` with its digest; each composed version after the first records its `diff_summary`; a `deep` instance composed at the `outline` event contains no state of the light route and verifies, and a `light` instance keeps the escalation into `deep` and verifies, each with its test; the specification states what the `outline` event prunes, and `INV-INIT-2` holds per lane; `verify`, `verify -Ppre-commit`, `verify -Pcoverage`, and `verify -Pintegration-tests` are green on the pushed head.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-5; PM-WF-25; PM-WF-1
- Specification: the sections the introduction of _What_ names; Workflow DSL § Composition Rules; § Plan Overrides; § Plan Instance (Versions & Composition Events, the `outline` event); § AST Node Model (compiled invariant `INV-INIT-2`); Store Schemas § Plan Overrides; § Gap Record (the gap causes); State Catalogue § Plan Workflow: Phases 1-3 (`outline.route-rework`, `outline.light-regate`, Phase 1 `init`); Identifier Registry § Prune reason
- Watch: PM-WATCH-PHASE-89; Workflow DSL
- Decision: `pm-workflow/doc/adr/workflow-plan/011-lane-route-pruning-deferred.adoc`

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`) (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `{module}/doc/adr/{package}/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **A sibling link into a package file this plan deletes (found by PLAN-75, 2026-10-17).** `plan-marshall-documentation` `doc/roadmap.adoc` line 233 (the plan-instance entry of Milestone 3) links `doc/plans/plan-instance-alignment.adoc`, the file this plan deletes in its last commit. The `Links` check of this repository runs `trace.py` over the documentation repository as well, so that link blocks this pull request until a fix there has merged: PLAN-75 met the same failure for `lane-route-pruning` and lost 38 minutes of ci-verify. Open the one-line fix pull request in `plan-marshall-documentation` during execute, as soon as the deletion is staged, not at finalize; the roadmap entry then points at the plan's landed result or drops the link, and merging that pull request is the operator's. It is the only link from that repository into a package file of this one (`git grep` of `origin/main` at `f0eeeb5`). Run the check with the absolute sibling path (see PLAN-20 and PLAN-69 for the form).
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/plan-instance-alignment.adoc` § What, Trace, Done When on `origin/main` at `c281f6c` (the file PLAN-75 #85 rewrote: 11 bullets)
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: doc/plans/plan-instance-alignment.adoc re-read at c281f6c: What holds 11 bullets, Where and Done When as in the Objective and Deliverables above
- OBSERVED: the index gives this package the status `open` and the dependencies below — read at `doc/plans/README.adoc` § Packages on `origin/main` at `c281f6c`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: README.adoc row plan-instance-alignment re-read at c281f6c: open; depends on nothing
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file and the class-level surfaces of the former specs, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; surface carried over from the former specs, each checked against the code at 238c823, and partitioned or merged at c281f6c
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (60), `WorkflowLogMessages` (5) — `static final LogRecord` counted on `origin/main` at `c281f6c`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: static final LogRecord counted at c281f6c: CoreLogMessages 5, RuntimeLogMessages 60, WorkflowLogMessages 5
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis: whether the package logs is decided at the trace matrix; no implementing code exists yet
- HYPOTHESIS: whether the package makes an HTTP call is decided at the specification sections of the trace starting set; the two HTTP constraints bind only if the trace matrix shows one, and only in `pm-runtime` or a provider module — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; pm-core and pm-workflow cannot depend on cui-http
- OBSERVED: the specification and watch parts of the Expected Surface changed since the specs of the former packages were last grounded at `238c823` only as listed in the verdict — `git diff --numstat 238c823..c281f6c`; the executing plan re-reads them at its branch point
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git diff --numstat 238c823..c281f6c over the declared specification and watch parts: implementation-watch/phase-workflows.adoc (+1/-1); specification/identifiers/02-registry-stores-to-web.adoc (+2/-2); specification/workflow-dsl/04-ast-node-model.adoc (+3/-3)
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `plan-instance-alignment`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: row plan-instance-alignment is open at c281f6c; open PRs 71, 86 and 87 (ci pr view): none names the package; dependencies not done are listed in the Parked line, if any

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PruneReason.java` — Deliverable 1, the closed reasons; class-level surface drafted by cleanup on 2026-10-17, replaces the directory entries
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/CompositionCause.java` — Deliverable 1
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/HeaderSchemaCheck.java` — Deliverable 1 (shared hot file with PLAN-72)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PrunedElement.java` — Deliverables 1 and 3
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/CompositionRules.java` — Deliverables 2 and 3
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanFacts.java` — Deliverables 2 and 3
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/CompositionRequest.java` — Deliverable 3, only if the axis input is a component of the request
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceComposer.java` — Deliverables 3, 4, 5 (REAL collision with PLAN-69, which changes its constructors and `SubWorkflowCall`)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/InstancePruner.java` — Deliverable 3 (REAL collision with PLAN-69)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/CompositionRecord.java` — Deliverable 5
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceStore.java` — Deliverable 5
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/package-info.java` — Deliverable 1
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/DiffSummary.java` — Deliverable 5, new
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/CompositionRulesTest.java` — Deliverables 1 and 2
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/CompositionRuleEvaluatorTest.java` — Deliverables 2 and 3
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/HeaderSchemaCheckTest.java` — Deliverable 1 (shared hot file with PLAN-72)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanFixtures.java` — Deliverables 2 and 3
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceComposerTest.java` — Deliverables 2 to 5 (REAL collision with PLAN-69 and PLAN-65)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceHeaderTest.java` — Deliverable 1 (shared with PLAN-65)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceStoreTest.java` — Deliverables 4 and 5
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/DiffSummaryTest.java` — Deliverable 5, new
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/DiagramFixtures.java` — Deliverable 1, renders the pruned element `auto_merge_off` (REAL collision with PLAN-69)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/WorkflowDiagramGeneratorTest.java` — Deliverable 1 (REAL collision with PLAN-69)
- OBSERVED: `pm-workflow/src/test/resources/diagram/instance-fixture.txt` — Deliverable 1, golden file
- OBSERVED: `pm-workflow/src/test/resources/diagram/instance-fixture.mmd` — Deliverable 1, golden file
- OBSERVED: `pm-workflow/src/test/resources/diagram/instance-fixture.adoc` — Deliverable 1, golden file
- HYPOTHESIS: `pm-workflow/src/test/resources/plan/plan-template-fixture.pmwf` — Deliverables 2 and 3 (REAL collision with PLAN-69)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-plan.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-plan/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/DeclarationBounds.java` — Deliverable 3, bounds for `self_merge`, `wait_after` and `order` (a boolean `CallValue` would ripple into `CallStack`, `WorkflowPrinter` and `HeaderJson` where PLAN-69 is active: staying with `EnumValue` avoids it; settle at refine)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/DeclarationBoundsTest.java` — Deliverable 3 (shared hot file with PLAN-69)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/src/main/resources/de/planmarshall/workflow/plan/composition-rules.json` — the Deliverable removes the rules `auto-merge-off` and `simplify-recipe` and adds a `merge_by_operator` rule here; added by cleanup on 2026-10-16
- OBSERVED: `doc/plans/README.adoc` — this package's own row only
- OBSERVED: `doc/plans/plan-instance-alignment.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/phase-workflows.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/workflow-dsl.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/identifiers/02-registry-stores-to-web.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/09-plan-instance.adoc` — specification part; the implemented sections deleted
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/CompositionEvent.java` — Deliverable 5, the sentence on the `OUTLINE` event (from the former `lane-route-pruning`)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/InitInvariants.java` — Deliverable 2, `INV-INIT-2` per lane (from the former `lane-route-pruning`)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/InvariantId.java` — Deliverable 2, `INV_INIT_2` at line 63 (shared hot file with PLAN-72, which changes `INV_FIN_5` at line 99) (from the former `lane-route-pruning`)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/CompiledInvariants.java` — Deliverable 2, the lane input of `check`; if `check` gains a parameter, its other caller `workflow/unit/ProjectUnitLoader.java` joins the surface (from the former `lane-route-pruning`)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/CompiledInvariantsTest.java` — Deliverable 2, per-lane `INV-INIT-2` cases (shared hot file with PLAN-72) (from the former `lane-route-pruning`)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/InvariantFixtures.java` — Deliverables 1 and 2, per-lane fixture units (from the former `lane-route-pruning`)
- HYPOTHESIS: `pm-workflow/src/test/resources/unit-fixtures/defaults/init.pmwf` — Deliverable 1, per-lane routes of the fixture unit; changes only if the trace matrix needs it (from the former `lane-route-pruning`)
- HYPOTHESIS: `pm-workflow/src/test/resources/unit-fixtures/defaults/outline.pmwf` — Deliverable 1, as above (from the former `lane-route-pruning`)
- OBSERVED: `doc/specification/state-catalogue/02-plan-phases-1-3.adoc` — specification part; the implemented sections deleted (from the former `lane-route-pruning`)
- OBSERVED: `doc/specification/workflow-dsl/04-ast-node-model.adoc` — specification part; the implemented sections deleted (from the former `lane-route-pruning`)

## Dependencies and Sequencing

- Depends on: PLAN-69 (`unit-frontmatter-and-types`) — not an index edge but the sequencing the operator approved at the emit of PLAN-69 on 2026-10-17: PLAN-69 is a repo-wide refactor of `pm-workflow` and runs alone there, and this package shares 15 declared files with it (`PlanInstanceComposer`, `InstancePruner`, the `workflow.verify` fixtures); nothing else in the index
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-70-plan-instance-alignment.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
