# PLAN-72: Spend limits removed from the code

epic: pm-mcp-core-m2-m4
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-72-spend-removal.md` and is queued as one row file, `queue/PLAN-72.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `spend-removal` of `doc/plans/spend-removal.adoc`: spend limits removed from the code. Serves: Roadmap Milestone 3 (the spend limits removed from the code). Where: pm-core package `core.store` (`WorkflowStatus`, `WorkflowStatusCodec`); pm-workflow packages `workflow.verify` (`RemediationInvariants`, `FinalizeInvariants`, `InvariantId`), `workflow.plan` (`HeaderSchemaCheck`), `workflow.engine` (`LoopBackBudget`), and `workflow.spend` (its Javadoc). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/spend-removal.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

Context: The specification counts tokens and limits none of a plan's use: it has no policy class `spend`, no remediation unit `remediation.spend`, no outcome `spend_overrun` of the pre-merge barrier, and no spend waiver (Model Work § Usage Reporting & Spend Classes). This package removes the code that `store-substrate` and `workflow-engine` built for them, without a deprecation (pre-1.0) and without reopening those packages. The recording of the usage in tokens is `token-usage`'s.

1. The policy class: `spend` leaves `WorkflowStatus.POLICY_CLASSES`, so `policy_counters` keyed `spend` are refused by the status codec as by the schema, and `LoopBackBudget` refuses a policy class `spend`; a workflow state naming it is refused.
2. The remediation unit: `RemediationInvariants.SPEND_UNIT` and every exemption that names `remediation.spend` go (the parameter set of `INV-REM-1` with `waiver_wait_seconds` only, the call routing of `INV-REM-5`, the state set of `INV-REM-7`), and the unit leaves the list of remediation units; a unit named `remediation.spend` is no remediation unit.
3. The pre-merge barrier: `INV-FIN-5` checks only that `finalize.merge-gate` lies on every path to the merge; `FinalizeInvariants.SPEND_OVERRUN` and `SPEND_REMEDIATION` and the clause "an overrun calls `remediation.spend`" of `InvariantId.INV_FIN_5` go.
4. The header: `HeaderSchemaCheck` admits as policy call units the six remediation units of the header schema and refuses `remediation.spend`.
5. The package `workflow.spend`: keeps the completion of a task and its usage only, its Javadoc and the module's README saying so; the spend classes, which report waste, stay.
6. Tests and documents: the fixtures and expectations that name the spend unit, the spend class, or the spend overrun (among them `HeaderSchemaCheckTest`) are changed to the six classes; the developer guides of `workflow.verify` and `workflow.engine` and ADR `workflow-verify/024` without the spend unit; in `plan-marshall-documentation` the roadmap item checked and the removal no longer stated as remaining in `token-usage`'s roadmap item.

**Done when** (the package's own acceptance): No class, test, or developer guide of pm-core or pm-workflow names `remediation.spend`, `spend_overrun`, or the policy class `spend` as a limit; a workflow state with `policy_counters.spend` is refused by the codec and by `LoopBackBudget`; a header binding `remediation.spend` is refused; a plan unit whose `finalize.merge-gate` routes no `spend_overrun` passes `INV-FIN-5`, and a remediation unit set without `remediation.spend` passes the remediation invariants; `workflow.spend` states completion and usage only; `verify`, `verify -Ppre-commit`, `verify -Pcoverage`, and `verify -Pintegration-tests` are green.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WF-14; PM-IMPL-5 (the compiled invariants)
- Specification: Model Work § Usage Reporting & Spend Classes; Store Schemas § Remediation Budget Accounting; § Plan Instance (the policy call units); Workflow DSL § Designable Workflow Units, Levels & Compiled Invariants (`INV-FIN-5`, `INV-REM-1` to `INV-REM-7`); State Catalogue § Remediation Units: Merge Conflict & Q-Gate Findings (no spend unit)
- Watch: PM-WATCH-DISP-7

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
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/spend-removal.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/spend-removal.adoc is unchanged since d1d58d2 (#77, no later commit touches it); its What, Trace and Done When equal the Deliverables, trace set and acceptance above
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line, the class names of § What and the specification parts the package links, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the Expected Surface mirrors the Where line of doc/plans/spend-removal.adoc; listed paths exist or are declared new; packages the Where line names but the surface omitted were added by cleanup
- Verify-first clause: before scoping, list the open pull requests for one that names `spend-removal`, check in `doc/plans/README.adoc` that no package it depends on is still listed, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatus.java` — Deliverable 1, `POLICY_CLASSES` loses `spend` (shared hot file); class-level surface drafted by cleanup on 2026-10-17, replaces the directory entry
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusCodecTest.java` — Deliverable 1
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/LoopBackBudgetTest.java` — Deliverable 1, the budget takes its classes from `WorkflowStatus.POLICY_CLASSES`, so no engine production file changes (the `Where` line of the package file over-declares `workflow.engine`: refute at the trace matrix)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-engine.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-engine/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/HeaderSchemaCheck.java` — Deliverable 4, the policy-unit message (line 65; shared hot file with PLAN-65, which changes the prune-reason message at lines 411-414)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/HeaderSchemaCheckTest.java` — Deliverables 4 and 6 (shared hot file with PLAN-65)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-plan.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-plan/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/spend/package-info.java` — Deliverable 5, the Javadoc states completion and usage only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-spend.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-spend/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/RemediationInvariants.java` — Deliverable 2, `SPEND_UNIT` removed (shared with the verifier plans)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/FinalizeInvariants.java` — Deliverable 3, `SPEND_OVERRUN` and `SPEND_REMEDIATION` removed (shared with the verifier plans)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/InvariantId.java` — Deliverable 3, `INV_FIN_5` at line 99 (shared hot file with PLAN-65, which changes `INV_INIT_2` at line 63)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/CompiledInvariantsTest.java` — Deliverables 2 and 3, matched controls without a spend unit and a merge gate without `spend_overrun` (shared with PLAN-65)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/README.adoc` — the Deliverable has the module README state `workflow.spend` as completion and usage only (line 32 still names the spend accounting); added by cleanup on 2026-10-16. The test directory `workflow/spend/` is no surface: `workflow.spend` holds only `package-info.java` and the Deliverable changes its Javadoc only
- OBSERVED: `doc/plans/README.adoc` — this package's own row only
- OBSERVED: `doc/plans/spend-removal.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/model-work/09-completion-usage-and-spend.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/11-remediation-units-merge-qgate-spend.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/04-ast-node-model.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-72-spend-removal.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
