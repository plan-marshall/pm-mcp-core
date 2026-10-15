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

Implement the work package `runtime-metrics` of `doc/plans/runtime-metrics.adoc`: what the runtime is doing: its metrics. Serves: roadmap Milestone 2 (the runtime metrics and `pm-operator system`); Use Cases, UC-SYS-2; the system view in both clients of `pm-mcp-clients`. Where: pm-core (the registry seam and the closed catalogue of metric names, so every module records where things happen); pm-workflow (the counts of plans and epics by state, kept at their transitions); pm-runtime (the registry, the process figures, the jobs, workers, tasks, slots, locks, and model calls, the endpoints and the stream). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

**Parked:** waits on packages that are not done: `job-runner`, `supervisor`, `task-queue-and-protocol`, `slots-usage-and-budgets`, `token-usage`, `plan-and-epic-control`. Re-ground this spec before emitting it.

## Deliverables

The bullets of `doc/plans/runtime-metrics.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Runtime Model § The Runtime's Metrics (`PM-ARCH-9`), with the disk-use step of § Periodic Sweep and the metrics event interval of Timeouts § Metrics Event Interval.
2. The registry: one registry of named counters, gauges, and timers; each metric has one writer, its unit, and its labels from the closed set `project`, `role`, `harness`, `model`, `kind`, `state`, and a name distinct from the names of the metrics store; no figure is computed by reading the stores on a request, none leaves the machine, and a figure the platform does not give is `unavailable` with its reason, never `0`.
3. The catalogue, closed, as the specification lists it: `runtime.version` (with its build), `runtime.started_at`, `runtime.uptime`; `runtime.memory.heap`, `runtime.memory.resident`, `runtime.cpu` (read at a snapshot from `/proc` on Linux and the task information on macOS); `runtime.disk.machine_store` (kept by the periodic sweep with when it was measured); `runtime.listeners`; `work.scopes`, `work.tasks`, `work.workers`, `work.jobs`, `work.slots`, `work.locks`, `work.leases`; `models.calls`, `models.tokens_in`, `models.tokens_out`, `models.total_tokens` (per hour with the coverage of the hour, each token figure the unified one of `token-usage`), `models.errors`, `models.retries`. No metric is recorded outside the catalogue, and none carries content of a plan, a task, or a secret, or a cost.
4. Maintained where things happen: each figure is recorded by the code that makes it true (a plan's or epic's transition, the task queue, the supervisor, the job runner, the slot scheduler, the lock manager and the lease stores, the transaction that writes a task row or ends an attempt); the work's gauges are set from the recovered state at every start, and the models' hourly figures filled at the start from the task rows of the metrics stores and the machine's usage aggregate `state/usage-aggregate.jsonl` of `cleanup`, which stay the truth; unmeasured usage counted as unmeasured.
5. The endpoints: `GET /api/v1/system` (the snapshot of the system view, with the window `hour`, `today`, or `7d` and the project as parameters) and `GET /api/v1/system/metrics` (every metric with its labels), both never waiting for a project's recovery and marking a recovering project; the runtime-wide stream carries `system.metrics` with the figures that changed, at most once per metrics event interval; each figure with the link to what it counts where there is one, addressed through `entity-addressing`; read by a paired device like every resource and by `pm-operator system`; `GET /api/v1/system/metrics` with `Accept: application/openmetrics-text` may answer the OpenMetrics text format, for a tool of the operator that holds the runtime token, on the socket only, the runtime opening no listener of its own for it (`PM-SEC-4`).
6. The library is not chosen here: which library implements the registry is an open question of the documentation repository (Open Questions, metrics library) and a dependency decision for the operator (CLAUDE.md: no dependency without asking). The package blocks on it and never decides it; the seam of pm-core names no library type.

**Done when** (the package's own acceptance): The operator has chosen the library; a test per metric of the catalogue shows the code that makes it true recording it in the same step, and no metric outside the catalogue; no request reads a store to answer a figure; the OpenMetrics text is answered on the socket only, never on a web listener; after a restart the model windows and the counts by state equal those rebuilt from the stores; a figure the OS does not give is reported unavailable with its reason, never `0`; the snapshot endpoints and the stream answer both channels, the stream announcing a changed value; no metric carries content, a secret, or a cost, and every token metric carries its split and its coverage.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-SYS-2
- Requirements: PM-ARCH-9; PM-ARCH-7 (the status of the runtime); PM-WORK-3; PM-SEC-7 (the paired devices)
- Specification: the sections the first bullet of _What_ names; Client Profiles § Tokens, Not Money; Runtime Model § Local API; § Sessions & Workers; Model Work § Usage Reporting & Spend Classes; Store Schemas § Metrics & Quality Report; Job Runtime § Pipeline & Subprocesses; Hypermedia Format § Representation Kinds; Filesystem Layout § Machine Layout
- Watch: Runtime Model; Model Work

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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/runtime-metrics.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 710da6150c6da19d4335643ca6070ef5517f0896 | by: pm-mcp-core-m2-m4/restructure | rescoped: n/a | evidence: regenerated by the orchestrator from the package file and its index row at 710da6150c6da19d4335643ca6070ef5517f0896 after the specification moved into pm-mcp-core (#77); § What, Trace, Done When and Depends on are the package's own
- OBSERVED: the package file and its index row (`blocked`; depends on PLAN-08 (`runtime-lifecycle`); PLAN-01 (`lock-manager-and-leases`); PLAN-20 (`job-runner`); PLAN-30 (`supervisor`); PLAN-28 (`task-queue-and-protocol`); PLAN-31 (`slots-usage-and-budgets`); PLAN-61 (`token-usage`); PLAN-51 (`plan-and-epic-control`); PLAN-21 (`entity-addressing`); the operator's choice of the metrics library (no work package)) — read at `doc/plans/runtime-metrics.adoc` and `doc/plans/README.adoc` (main at a8a4214, regenerated by cleanup)
  - verdict: corroborated | checked_at: a8a421408bfa31a4a64630c5139fd9e50bfac6f3 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: regenerated by cleanup from doc/plans package file and index row at a8a421408bfa31a4a64630c5139fd9e50bfac6f3; Objective, Deliverables, Done when, Trace and Depends on are the package's own at that sha
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/metrics/` — inferred from the `Where` line
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — inferred from the `Where` line
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/start/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/core-metrics.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-metrics/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-engine.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-engine/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-start.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-start/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- OBSERVED: `doc/plans/runtime-metrics.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/filesystem-layout/02-machine-runtime.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/hypermedia-format/01-representation-and-link-forms.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/09-completion-usage-and-spend.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/runtime-lifecycle/03-runtime-metrics.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/05-metrics-and-quality-report.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/timeouts/02-runtime-leases-operator-web.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: `lock-manager-and-leases`; PLAN-20 (`job-runner`); PLAN-30 (`supervisor`); PLAN-28 (`task-queue-and-protocol`); PLAN-31 (`slots-usage-and-budgets`); PLAN-61 (`token-usage`); PLAN-51 (`plan-and-epic-control`) (held and paused); the operator's choice of the metrics library (no work package)

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
