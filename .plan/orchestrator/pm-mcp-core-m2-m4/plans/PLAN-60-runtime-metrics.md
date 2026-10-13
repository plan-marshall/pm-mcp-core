# PLAN-60: What the runtime is doing: its metrics

epic: pm-mcp-core-m2-m4
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-60-runtime-metrics.md` and is queued as one row file, `queue/PLAN-60.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `runtime-metrics` of `doc/plans/runtime-metrics.adoc` (added to the index on 2026-10-12): what the runtime is doing: its metrics. Serves: Use Cases, The Runtime's Metrics; UC-SYS-2; the system view in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-core (the registry seam and the closed catalogue of metric names, so every module records where things happen); pm-workflow (the counts of plans and epics by state, kept at their transitions); pm-runtime (the registry, the process figures, the jobs, workers, tasks, slots, locks, and model calls, the endpoints and the stream). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/runtime-metrics.adoc` § What, in its words.

1. Specification first*: the package writes the change The Runtime's Metrics into `plan-marshall-documentation` as part of its own work, once it and UC-SYS-2 are agreed: the new requirement the change drafts, under the next free id of the requirements domain it names (PM-ARCH); the metrics catalogue as a closed list in the specification; the endpoints and the stream (Runtime Model § Local API); the representation; and, where the change takes it, the exposition on loopback.
2. The requirement*: the runtime keeps the figures of what it is doing as metrics, a registry of named counters, gauges, and timers maintained where things happen, never computed by scanning stores on a request; a figure the platform cannot give is reported unavailable with its reason, never as `0`.
3. The catalogue*, closed, first version as the change fixes it: the runtime's identity (version, build, started at, uptime, the open listeners, loopback and network); the process (heap and resident memory, CPU where the OS gives it, the disk use of `<PM_MCP_BASE>`); the work (plans and epics by state, running, waiting, held, paused, across projects and per project; tasks queued and running; workers by role, harness, and model, busy and idle; jobs running and queued by kind, with the build and model slots; locks held); the models (calls, tokens, and cost per model and per role, errors and retries, over the windows now, today, and the last seven days). No metric is recorded outside the catalogue, and none carries content of a plan, a task, or a secret.
4. Maintained where things happen*: each figure is recorded by the code that makes it true, in the same step: a plan's or epic's transition, a task offered, leased, and ended, a worker started, idle, recycled, and ended, a job admitted and settled, a slot and a lock claimed and released, a model call's usage row written (`slots-usage-and-budgets`). The windows of the model figures are rebuilt once at startup from the usage records and then kept by the same increments; the counts of plans and epics by state once from the stores after recovery.
5. The endpoints*: `GET /api/v1/system` (the identity, the process, and the totals) and `GET /api/v1/system/metrics` (the catalogue's metrics with their labels, values, and units, an unavailable one with its reason); an event stream of changed values for live views, the interval and the stream's name as the change fixes them; each figure with the link to what it counts where there is one (the running plans, the jobs, the workers), addressed through `entity-addressing`. Both are read endpoints for both channels, from a paired web device as from `pm-operator`. A standard exposition (OpenMetrics text on loopback) for outside tools is delivered only if the change takes it.
6. The library is not chosen here*: which library implements the registry (a metrics library, or none and a registry of the product's own) is a dependency decision for the operator (CLAUDE.md: no dependency without asking). The package blocks on it and never decides it; the seam of pm-core names no library type.

**Done when** (the package's own acceptance): The requirement and the specification of the change are merged in `plan-marshall-documentation`, and the operator has chosen the library; a test per metric of the catalogue shows the code that makes it true recording it in the same step, and no metric outside the catalogue; no request reads a store to answer a figure; after a restart the model windows and the counts by state equal those rebuilt from the stores; a figure the OS does not give is reported unavailable with its reason, never `0`; the snapshot endpoints and the stream answer both channels, the stream announcing a changed value; no metric carries content or a secret.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use cases: UC-SYS-2 and its change The Runtime's Metrics
* Requirements: the new requirement the change drafts in PM-ARCH; PM-ARCH-7 (the status of the runtime); PM-WORK-3; PM-SEC-7 (the paired devices)
* Specification: Runtime Model § Local API; § Sessions & Workers; Model Work § Spend Accounting; Store Schemas § Metrics & Quality Report; Job Runtime § Pipeline & Subprocesses; Hypermedia Format § Representation Kinds; Filesystem Layout § Machine Layout
* Watch: Runtime Model; Model Work

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
- **Log messages: the identifier block of WS-03.** New records in `PmMcpLogMessages` take the lowest free identifier of INFO 070-079, WARN 170-179, ERROR 270-279 and stand under one block comment for the workstream, as the existing blocks do. Renumber nothing. The block is shared with the other plans of WS-03: after a rebase, run `PmMcpLogMessagesTest`, which refuses an identifier used twice, and move to the next free one. A plan that needs more than the block holds stops and reports it through its inbox message. The same identifiers go into `doc/LogMessages.adoc` in the documentation pull request.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the package file and its index row (`blocked`; depends on `runtime-lifecycle`, `lock-manager-and-leases`, `job-runner`, `supervisor`, `task-queue-and-protocol`, `slots-usage-and-budgets`, `plan-and-epic-control`, `entity-addressing`; (held and paused); the operator's choice of the metrics library (no work package); the change The Runtime's Metrics and UC-SYS-2 agreed (no work package)) — read at `doc/plans/runtime-metrics.adoc` and `doc/plans/README.adoc` (main at 557ee81)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/metrics/` — inferred from the `Where` line
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — inferred from the `Where` line
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/start/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-08 (`runtime-lifecycle`); PLAN-01 (`lock-manager-and-leases`); PLAN-20 (`job-runner`); PLAN-30 (`supervisor`); PLAN-28 (`task-queue-and-protocol`); PLAN-31 (`slots-usage-and-budgets`); PLAN-51 (`plan-and-epic-control`); PLAN-21 (`entity-addressing`); (held and paused); the operator's choice of the metrics library (no work package); the change The Runtime's Metrics and UC-SYS-2 agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-60-runtime-metrics.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
