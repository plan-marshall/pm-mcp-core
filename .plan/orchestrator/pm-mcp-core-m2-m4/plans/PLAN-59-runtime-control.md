# PLAN-59: Controlling the runtime: stop, start, restart, upgrade

epic: pm-mcp-core-m2-m4
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-59-runtime-control.md` and is queued as one row file, `queue/PLAN-59.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `runtime-control` of `doc/plans/runtime-control.adoc` (added to the index on 2026-10-12): controlling the runtime: stop, start, restart, upgrade. Serves: Use Cases, The Runtime's Lifecycle; UC-SYS-1; the runtime's lifecycle in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-runtime package `runtime.start` (stop now and drain as acts of the runtime, the withdrawable drain, restart, the upgrade, the operator endpoints); pm-core (the consent kind `check_updates` in the consent store). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/runtime-control.adoc` § What, in its words.

1. The packages `runtime-lifecycle` and `service-unit` are done and stay as they are: they built the drain on a signal, the recovery that resumes work after it, the version mismatch, and the unit file. This package adds the lifecycle as acts of the operator in both clients on top of them; it is a package of its own because those two are closed, and because its acts need the operator endpoints, the consent store, and the jobs, workers, and tasks they stop, none of which existed when they were cut.
2. Specification first*: the package writes the change The Runtime's Lifecycle into `plan-marshall-documentation` as part of its own work, once it and UC-SYS-1 are agreed: the requirement the change drafts or extends (PM-ARCH-7, the inspection and lifecycle verbs); the sections it names: Drain, Stop & Upgrade (stop now, the withdrawable drain, restart, the upgrade from a client), the Local API (the operator endpoints of the lifecycle; drain stops being a signal only), Installation Layout (where versions come from, the switch done by the runtime), the consent kind `check_updates` in the closed list of consents, and the texts of the use case.
3. Stop*: one act with two modes. `drain`: nothing new starts, what runs finishes its current task or job, then the runtime stops as the drain of `runtime-lifecycle` does, bounded by the drain timeout. `now`: running tasks are withdrawn, jobs stopped, worker generations fenced, and everything resumes from its last committed state at the next start through the per-project recovery. The answer names what runs (plans, epics, jobs, workers), so the client can ask its confirmation with it.
4. The drain as a state*: a drain started through the API is visible (`facts.runtime: draining`, the status, the event stream) and can be withdrawn while it runs, before its final step; a withdrawn drain leaves every participant running as before. A running drain can be turned into a stop now (the bound passed, or the operator's choice). `pm-operator drain` stays a signal for a runtime that does not answer.
5. Restart*: a stop (now or drain) followed by a start of the same installation, without a terminal: the runtime hands its successor start to a detached start of `<PM_MCP_HOME>/bin/pm-mcpd` that waits for the singleton lock of the stopping runtime; under the service unit the service manager's own start is used. The change fixes the mechanism; a stopped runtime is started only from the terminal client (on demand, or `pm-operator runtime start`).
6. Version and the update check*: the installed version and build from the runtime itself, a local read without consent; whether a newer release is available from the place the release comes from (the organisation's registry, `PM-DIST-1`), a network read that needs the consent kind `check_updates`, rememberable, asked before the first check and never repeated while remembered.
7. Upgrade*: on the operator's choice, the runtime fetches the release, verifies it, unpacks it into a new versioned installation directory, switches the two `PATH` symlinks, drains (or stops now, the operator's choice), and its successor starts from the new directory; running work resumes after it like after a restart. The old directory stays until the drain completed. A failure before the switch leaves the installed version running and unchanged; a failure after it (the new runtime does not reach its authenticated round trip within the start timeout) switches the symlinks back and starts the previous version; both say why. A stop requested while an upgrade runs is refused with the upgrade's state, a second upgrade likewise. Pre-1.0: no store migration beyond what the specification defines.
8. The endpoints*: the lifecycle acts as operator endpoints under `/api/v1/operator/` (stop with its mode, withdraw a drain, turn a drain into a stop now, restart, the update check, the upgrade), accepted from `pm-operator` and from a paired web device and audited with their channel; the version and the upgrade state as part of `GET /api/v1/status`; the lifecycle events (draining, drain withdrawn, stopping, upgrading, upgraded, upgrade failed) on the runtime-wide event stream, so every open client sees the runtime stopping.

**Done when** (the package's own acceptance): The requirement and the specification of the change are merged in `plan-marshall-documentation`; with a killed-runtime fixture and fixture jobs and workers: a stop now ends every job and fences every worker at once and the next start resumes each plan from its last committed state; a drain lets the running task and job finish and starts nothing new; a withdrawn drain leaves everything running; a drain turned into a stop now behaves like a stop now; a restart comes back on the same installation without a terminal; the update check runs only with `check_updates` given and never asks again while it is remembered; an upgrade from a staged release switches to the new directory and resumes the running work; a failure before and one after the switch each leave the previous version running with the reason; a stop during an upgrade is refused; every act is audited with its channel, and every open event stream sees the runtime stopping.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use cases: UC-SYS-1 and its change The Runtime's Lifecycle
* Requirements: PM-ARCH-7 and what the change drafts there; PM-DIST-1, PM-DIST-4; PM-SEC-3, PM-SEC-7 (the operator's channels and devices)
* Specification: Runtime Model § Drain, Stop & Upgrade; § Per-Project Recovery; § Local API; § Start Modes; CLI & Security § Installation Layout; § Operator Channel Isolation; Timeouts § Runtime Lifecycle; the consents Use Cases, Consents
* Watch: Runtime Model; CLI & Security

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

- OBSERVED: the package file and its index row (`blocked`; depends on `runtime-lifecycle`, `audit-trail`, `service-unit`, `job-runner`, `supervisor`, `task-queue-and-protocol`, `machine-onboarding-unit`; (the consent store); `pm-mcp-clients` / `installation-layout`; the change The Runtime's Lifecycle and UC-SYS-1 agreed (no work package)) — read at `doc/plans/runtime-control.adoc` and `doc/plans/README.adoc` (main at 557ee81)
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/runtime-control.adoc exists at fa58b0d (presence checked, content not re-read)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/start/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-start.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-start/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)

## Dependencies and Sequencing

- Depends on: PLAN-08 (`runtime-lifecycle`); PLAN-09 (`audit-trail`); PLAN-04 (`service-unit`); PLAN-20 (`job-runner`); PLAN-30 (`supervisor`); PLAN-28 (`task-queue-and-protocol`); PLAN-46 (`machine-onboarding-unit`); (the consent store); `pm-mcp-clients` / `installation-layout`; the change The Runtime's Lifecycle and UC-SYS-1 agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-59-runtime-control.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
