# PLAN-65: Lane-route pruning at the `outline` event

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-65-lane-route-pruning.md` and is queued as one row file, `queue/PLAN-65.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `lane-route-pruning` of `doc/plans/lane-route-pruning.adoc`: lane-route pruning at the `outline` event. Serves: Roadmap Milestone 3. Where: pm-workflow package `workflow.plan`; the default plan unit and its templates in `plan-marshall-mcp`; the specification in `plan-marshall-documentation`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/lane-route-pruning.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

Context: To be confirmed by the § Discussion:

1. The per-lane rework and replan routes in the State Catalogue and in the plan templates of `plan-marshall-mcp`.
2. `INV-INIT-2` reworded per lane, in the specification and in `CompiledInvariants`.
3. The prune reason `other_lane` in `PruneReason`, `HeaderSchemaCheck` and the header schema.
4. Reachability pruning at the `outline` event in `workflow.plan`, with a test per lane and a matched control: a `deep` version without the light route verifies; a `light` version keeps the deep route.
5. The corrected `outline` event sentence; the developer guide of `workflow.plan` without the limitation.

**Done when** (the package's own acceptance): * The § Discussion is decided with the operator and recorded as an ADR. * A `deep` instance composed at the `outline` event contains no state of the light route and verifies; a `light` instance keeps the escalation into `deep` and verifies; each has its test. * The specification states what the `outline` event prunes, and `INV-INIT-2` holds per lane. * `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` are green on the pushed head.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-5
- Specification: Workflow DSL § Plan Instance (Versions & Composition Events, the `outline` event); Workflow DSL § AST Node Model (compiled invariant `INV-INIT-2`); State Catalogue § Plan Workflow: Phases 1-3 (`outline.route-rework`, `outline.light-regate`, Phase 1 `init`); Store Schemas § Plan Instance (`prune_reason`)
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
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/lane-route-pruning.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/lane-route-pruning.adoc is unchanged since d1d58d2 (#77, no later commit touches it); its What, Trace and Done When equal the Deliverables, trace set and acceptance above
- OBSERVED: the package file and its index row (`open`; depends on nothing; `plan-instance` is done and left the index) — read at `doc/plans/lane-route-pruning.adoc` and `doc/plans/README.adoc` (main at a8a4214, regenerated by cleanup)
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: README.adoc row lane-route-pruning re-read at 238c823: open; depends on nothing; the claim named other dependencies (done and dropped, or added since); rewritten from the row
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the class names of § What, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the Expected Surface mirrors the Where line of doc/plans/lane-route-pruning.adoc; listed paths exist or are declared new; packages the Where line names but the surface omitted were added by cleanup
- Verify-first clause: before scoping, list the open pull requests for one that names `lane-route-pruning`, check in `doc/plans/README.adoc` that every dependency is `done`, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PruneReason.java` — Deliverable 3, the reason `other_lane` (rule-family plans change it too); class-level surface drafted by cleanup on 2026-10-17, replaces the directory entries
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceComposer.java` — Deliverable 4, the prune at the `outline` event (shared hot file; PLAN-24, 43, 50, 67 change it)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/InstancePruner.java` — Deliverable 4, reachability from the lane entry, unless the Discussion puts it in a new class
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/CompositionEvent.java` — Deliverable 5, the sentence on the `OUTLINE` event
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/InitInvariants.java` — Deliverable 2, `INV-INIT-2` per lane
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/InvariantId.java` — Deliverable 2, `INV_INIT_2` at line 63 (shared hot file with PLAN-72, which changes `INV_FIN_5` at line 99)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/CompiledInvariants.java` — Deliverable 2, the lane input of `check`; if `check` gains a parameter, its other caller `workflow/unit/ProjectUnitLoader.java` joins the surface
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceComposerTest.java` — Deliverable 4, a test per lane with a matched control
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceHeaderTest.java` — Deliverable 3, "closed at twelve" and the wire-name table
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/HeaderSchemaCheckTest.java` — Deliverable 3, the enum message at lines 411-414 (shared hot file with PLAN-72); `HeaderSchemaCheck.java` itself needs no edit: its `REASONS` come from `PruneReason.values()`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/CompiledInvariantsTest.java` — Deliverable 2, per-lane `INV-INIT-2` cases (shared hot file with PLAN-72)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/InvariantFixtures.java` — Deliverables 1 and 2, per-lane fixture units
- HYPOTHESIS: `pm-workflow/src/test/resources/unit-fixtures/defaults/init.pmwf` — Deliverable 1, per-lane routes of the fixture unit; changes only if the trace matrix needs it
- HYPOTHESIS: `pm-workflow/src/test/resources/unit-fixtures/defaults/outline.pmwf` — Deliverable 1, as above
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- OBSERVED: `pm-workflow/doc/developer/workflow-plan.adoc` — developer documentation of the package; created or extended
- OBSERVED: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended
- OBSERVED: `pm-workflow/doc/adr/workflow-plan/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/lane-route-pruning.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/state-catalogue/02-plan-phases-1-3.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/04-ast-node-model.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/workflow-dsl/09-plan-instance.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-75 (`work-package-redistribution`), which merges this package into `plan-instance-alignment` (PLAN-70) and deletes its package file; this spec is then superseded by the regenerated spec of the merged package; nothing else in the index
- Overlaps with: PLAN-10 (`static-verifier-and-fuzzer`) in `workflow.verify`; PLAN-17 (`project-units-and-invariants`) in `workflow.verify`; PLAN-24 (`plan-instance`) in `workflow.plan`; PLAN-41 (`static-verifier-remaining-rules`) in `workflow.verify`; PLAN-42 (`operator-wait-and-machine-scope`) in `workflow.verify`; PLAN-43 (`phase-sub-workflows`) in `workflow.plan`, `workflow.verify`; PLAN-44 (`static-verifier-rule-gaps`) in `workflow.verify`; PLAN-48 (`start-from-clients`) in `workflow.verify`; PLAN-50 (`recipe-workflows`) in `workflow.plan`; PLAN-54 (`static-verifier-rule-gaps-completion`) in `workflow.verify`; PLAN-56 (`static-verifier-residuals`) in `workflow.verify`; PLAN-62 (`process-designer`) in `workflow.verify`; PLAN-67 (`process-and-overview`) in `workflow.plan`; every plan through `doc/plans/README.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-65-lane-route-pruning.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
