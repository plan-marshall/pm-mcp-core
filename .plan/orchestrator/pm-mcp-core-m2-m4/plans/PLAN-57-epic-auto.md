# PLAN-57: An epic runs by itself

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-57-epic-auto.md` and is queued as one row file, `queue/PLAN-57.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `epic-auto` of `doc/plans/epic-auto.adoc` (added to the index on 2026-10-12): an epic runs by itself. Serves: Use Cases, An Epic Runs by Itself; the epic's automatic mode and its reactions on a finished plan (UC-CTL-2), the mode in force shown at the start of an epic (UC-STA-2); the automatic toggle and the overlap wait in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-workflow (the epic workflow: starting by itself, the overlap admission, the reactions on a finished plan, the completion review once the epic is through); pm-core (the epic header's `auto_start` and `overlap_max_files`, the queue row's wait reason `overlap`, the project keys `epic.auto_start` and `epic.overlap_max_files` with their machine defaults); pm-runtime (the toggle and the overlap limit as operator endpoints and verbs). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/epic-auto.adoc` § What, in its words.

1. Specification first*: the package writes the change An Epic Runs by Itself into `plan-marshall-documentation` as part of its own work, once it and UC-CTL-2 are agreed (Specification Changes for the Clients, Packages): the epic workflow, launchable rows, staging-time surface disjointness and runtime drift, the epic header, the queue row's wait reasons, and the project and machine keys, each building on what epic coordination already states and never stating it twice.
2. The setting*: `epic.auto_start` (`true` or `false`), a project key with the machine default `true`, so the specified behaviour stays the default; each epic carries its own value in its header, taken from the project at the start and never asked there (UC-STA-2 shows it on the ready step as the value in force). `parallelization_scope` stays the upper limit of plans of the epic running at once (machine default 1, raised by the operator, `plan-and-epic-control`).
3. The toggle*: _Automatic: on/off_ among the epic's actions in every state that has not ended (UC-CTL-2, the actions by state of `plan-and-epic-control`), an operator endpoint of both clients, a ledger write of the header, audited and told in the epic's events; switched on, the epic runs `epic.next-work` at once.
4. On*: whenever a place frees or a row becomes launchable, `epic.next-work` starts every launchable row up to the limit by itself. *Off*: the epic starts nothing by itself; the operator starts the next plan with _Next plan_ (`plan-and-epic-control`); every reaction below still runs.
5. Too many common changes*: a launchable row is admitted beside the running plans only while its declared surface shares at most `epic.overlap_max_files` files with each running plan's declared surface and realized changes (a project key with a compiled upper bound, machine default 0: any common file; held in the epic header like `auto_start`, taken from the project at the start, and changeable from the epic's actions, an audited header write like the toggle; `epic.pmwf` stays compiled-only). Over it, the row waits with the reason `waits: overlap with <plan>` beside dependency, lock, and scope, until that plan has ended; it is never started in parallel, in either mode, also not by _Next plan_.
6. Overlap found while running*: a running plan whose realized changes come to overlap another running plan's beyond the limit is recorded in the ledger and the epic is told; neither plan is stopped (runtime drift never blocks); the one that lands second meets the base-advanced trigger of re-planning (PM-WF-17). The report of it to the operator is `operator-reports`'s.
7. Reacting on a finished plan*: every end of a plan of the epic reaches its inbox (the landing, and the operator's acts of `plan-and-epic-control`); the epic workflow then:
** for a landed plan: records the landing and runs the landing task's downstream impact judgment; a change of staged rows it proposes goes through `epic.recut`: one that only re-orders rows or amends a staged row's surface or prerequisites is applied once the server's checks pass; one that adds, drops, or re-scopes a plan waits for the operator in `decomposition.review` (`epic-decomposition`), in both modes;
** for a plan that ended without landing (cancelled, abandoned, handed back, released, aborted): holds the rows that depend on it with that reason and asks the operator as specified;
** then `epic.next-work`: with automatic on it starts the next launchable rows up to the limit; with it off it records what is launchable now for the epic's view and _Next plan_ (reported as `epic.launchable`, `operator-reports`).
8. Through*: the epic is through when every row is shipped or ended; with automatic on it then runs the completion review and records it; archiving stays the operator's act (`plan-and-epic-control`).
9. A restart*: the epic's mode and its waiting rows are read back from the ledger; an epic with automatic on runs `epic.next-work` once recovered, starting nothing twice.

**Done when** (the package's own acceptance): The specification of the change is merged in `plan-marshall-documentation`; a new epic takes `auto_start` and `overlap_max_files` from the project, the project's values from the machine defaults `true` and 0, a value over the compiled upper bound is refused, and its start asks for neither; with automatic on, a freed place or a newly launchable row starts every launchable row up to `parallelization_scope` and no more, also after a restart and without starting a row twice; with it off nothing starts by itself while every reaction still runs and what is launchable is recorded; the toggle and a change of the overlap limit are audited header writes told in the epic's events and refused on an ended epic; a row whose surface overlaps a running plan's beyond `epic.overlap_max_files` waits with `waits: overlap with <plan>` in both modes and through _Next plan_, and is admitted once that plan has ended; an overlap found between running plans is recorded and told and stops neither; a landing's re-order or amendment of staged rows is applied after the checks pass, an added, dropped, or re-scoped plan waits in `decomposition.review`; a plan ended without landing holds its dependants with the reason; an epic through with automatic on runs its completion review and is not archived by itself.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use cases: An Epic Runs by Itself; UC-CTL-2; UC-STA-2
* Requirements: PM-WF-15, PM-WF-17
* Specification: Model Work § Epic Workflow; § Staging-Time Surface Disjointness; § Server-Derived Landing Facts, Spec Re-Grounding & Cross-Plan Locks; Phase Workflows § Re-Planning Triggers; Store Schemas § Epic Header; § Epic Queue Row; § Project Configuration; State Catalogue § Epic

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

- OBSERVED: the package file and its index row (`blocked`; depends on `plan-and-epic-control`, `epic-decomposition`; the change An Epic Runs by Itself and UC-CTL-2 agreed (no work package)) — read at `doc/plans/epic-auto.adoc` and `doc/plans/README.adoc` (main at 557ee81)
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/epic-auto.adoc exists at fa58b0d (presence checked, content not re-read)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-engine.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-engine/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)

## Dependencies and Sequencing

- Depends on: PLAN-51 (`plan-and-epic-control`); PLAN-53 (`epic-decomposition`); the change An Epic Runs by Itself and UC-CTL-2 agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-57-epic-auto.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
