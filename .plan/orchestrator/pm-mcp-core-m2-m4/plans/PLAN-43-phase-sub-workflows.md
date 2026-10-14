# PLAN-43: Phases as sub-workflows, and the plan's history

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-43-phase-sub-workflows.md` and is queued as one row file, `queue/PLAN-43.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `phase-sub-workflows` of `doc/plans/phase-sub-workflows.adoc` (added to the index by #36 on 2026-10-12): the plan unit as a workflow of phases, each phase a sub-workflow it calls, and an append-only history per plan. It serves UC-PLN-1 and the plan drill-down of both clients, no roadmap milestone yet, and lands in pm-workflow (units, composition, engine, static verifier, diagram) and pm-core (the plan's history). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** Index changed since staging (#52, #55 on 2026-10-13) — re-read the package file and its index row before emitting. the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation`, not on code. Re-ground this spec against the agreed use cases and the specification before emitting it.

## Deliverables

The bullets of `doc/plans/phase-sub-workflows.adoc` § What, in its words.

1. Specification first: the changes Phases as Sub-Workflows and The Plan's History, once UC-PLN-1 is agreed.
2. Phases as sub-workflows: the plan unit becomes a workflow of phases, each phase a sub-workflow; a lane is the choice of phases it calls, a loop-back a call of a phase again, a re-entry from finalize into execute an outcome of finalize. The `phase` clause and the phase value table go; a plan's phase is the phase on its call stack; the call depth grows by one; the plan instance composes the phases and prunes a phase as a call.
3. The plan's history: append-only per plan, states entered and left, sub-workflows called and returned with their outcomes, the instance version in force, the time; archived with the plan; the runtime derives each node's state from it.
4. Where `workflow-engine`, `plan-instance` or `project-units-and-invariants` already implemented the flat plan unit, restructure them.

**Done when** (the package's own acceptance): the specification of both changes merged in `plan-marshall-documentation`; every phase is a sub-workflow the plan unit calls, and the static verifier and the fuzzer pass over the default units; a test per lane shows the phases it calls and prunes; a loop-back calls a phase again and the history records both runs; a plan's history reproduces the node states of its process after a runtime restart.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

- Use case: UC-PLN-1 and its changes Phases as Sub-Workflows, The Plan's History (`use-cases/03-plans.adoc`)
- Specification: Workflow DSL § Workflow Definition Units, § Reusable Sub-Workflows, § Plan Instance; Phase Workflows

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request sets the package's row to `done` within its commits. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents.** Javadoc, `package-info.java` and the work package file live here. Requirement and specification documents, the user documentation, the deletion of implemented specification sections and watch items and the roadmap update are made directly in `plan-marshall-documentation` by this plan, in a pull request there that need not name the code pull request; merging it is the operator's.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`) (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `{module}/doc/adr/{package}/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **One file is shared by every plan.** In `doc/plans/README.adoc` change only this package's own row.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of `plan-marshall-documentation`, under the holder's section, in the documentation pull request.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the package file and its index row (`blocked`; depends on `operator-wait-and-machine-scope`, `workflow-engine`, `plan-instance`, and UC-PLN-1 agreed) — read at `doc/plans/phase-sub-workflows.adoc` and `doc/plans/README.adoc` (main at ebe82b3)
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/phase-sub-workflows.adoc exists at fa58b0d (presence checked, content not re-read)
- OBSERVED: the call-graph depth limit of 4 (Rule 7, `CALL_GRAPH_DAG`) shipped with PLAN-41 (#34); this package grows the call depth by one — read at `pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java`
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-workflow/src/main/java/de/planmarshall/workflow/verify/VerificationRule.java exists at fa58b0d (presence checked, content not re-read)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line; the plan's history lands in `core.store` — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/` — the plan unit as a workflow of phases
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — the call stack and the history
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — call depth and phase rules
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/` — composition and pruning
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/` — phase drill-down
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — the plan's history
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-unit.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-unit/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-engine.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-engine/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-verify.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-verify/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-plan.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-plan/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-diagram.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-diagram/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`), PLAN-13 (`workflow-engine`), PLAN-24 (`plan-instance`), and UC-PLN-1 agreed (no work package)
- Restructures: the flat plan unit of PLAN-13, PLAN-17 and PLAN-24; Rule 7's depth limit of PLAN-41

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-43-phase-sub-workflows.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
