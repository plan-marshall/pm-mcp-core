# PLAN-58: Reports to the operator

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-58-operator-reports.md` and is queued as one row file, `queue/PLAN-58.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `operator-reports` of `doc/plans/operator-reports.adoc` (added to the index on 2026-10-12): reports to the operator. Serves: Use Cases, Reports to the Operator; UC-PLN-6; the reports, their list, and a plan's completion report in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-core (the report record and its store in the machine scope, the read state, the catalogue of kinds and subjects); pm-workflow (the server transitions that write a report); pm-runtime (the endpoints, the event stream `reports`, the representation with its `detail` link, a plan's completion report as a view of an ended plan). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/operator-reports.adoc` § What, in its words.

1. Specification first*: the package writes the change Reports to the Operator into `plan-marshall-documentation` as part of its own work, once it and UC-PLN-6 are agreed (Specification Changes for the Clients, Packages): the new requirement the change drafts, in `doc/requirements/02-workflow.adoc` under the next free PM-WF id; and the specification sections it names: the report journal (Store Schemas, Filesystem Layout of the machine scope), the endpoints and the event stream (Runtime Model § Local API), the representation with its `detail` link (Hypermedia Format), the report id `rep-NNNN` (Identifiers), the event sources (which server transition writes which kind), the catalogue's texts, and a plan's completion report beside the completion and usage records.
2. A report*: a record the runtime writes for the operator when something happened that the operator should know without having to act; it never asks anything, and what blocks stays what it is (decisions, waits, holds: `needs_operator`). Its fields: id (`rep-NNNN`); `from` (the project, and the epic and the plan where there are, each with its link); `kind`; `subject` (one line, a text of the catalogue with its parameters, in the client's language); a short body; `created_at` (when it happened) and `sent_at` (when the clients could see it, later than `created_at` where it was written after a restart); `read_at` with the channel and device that read it; `detail`, the link to the detailed report where there is one.
3. The catalogue*: closed, first version `plan.started` (by the operator, an epic, or a recipe; a plan an epic starts gives this one report, its `from` naming the epic), `plan.ended` (with its outcome, one of the closed list of plan outcomes of the terminal-state catalogue as `plan-and-epic-control` extends it), `plan.revised` (a re-planning or a chat's revision), `plan.hold_released`, `epic.launchable` (automatic off, a row became launchable), `epic.waits_overlap`, `epic.overlap_found`, `epic.recut_applied`, `epic.through`, `epic.ended`, `project.onboarded`, `project.released`, `source.flagged`, `decision.closed_elsewhere`; each kind with its subject text in English and German and its `detail`.
4. The sources*: each kind written by the server transition that makes it true, once, in the same step: the start and the end of a plan and its outcomes and holds (`plan-and-epic-control`), a revision (re-planning and `chat`), a row launchable with automatic off, its overlap wait and the overlap found, an applied recut, and the epic through (`epic-auto`), the end of an epic and the release of a project (`plan-and-epic-control`), a project onboarded (`project-onboarding-unit`), a screened source flagged (`start-from-clients`), a decision closed in another channel (`decision-engine`); no report is written outside the catalogue.
5. The store*: one journal of the machine scope (`operator-wait-and-machine-scope`), `<PM_MCP_BASE>/state/reports.jsonl`, with the events `report` (a new report) and `read` (its `read_at`, channel, and device), the current state of a report folded from them; unread reports never expire; read ones are kept, and removing them is the open point of deleting archived work.
6. Read*: a report is read when the operator marks it read (one, or all shown) or opens its detail; read is per machine, so a report read on one device is read on all, with the channel and device recorded; marking a report read twice changes nothing.
7. The endpoints*: `GET /api/v1/reports?state=unread` (unread only, newest first, grouped by project, then epic and plan; the count of unread reports), `POST /api/v1/reports/{report_id}/read`, `POST /api/v1/reports/read` with the ids the client showed (only those, never one that arrived since), the event stream `GET /api/v1/reports/events` that announces a new report and a read one to every open client; the representation with `from`'s links and the `detail` link, addressed through `entity-addressing`; the same acts as operator verbs.
8. The details*: a plan's completion report `GET /api/v1/plans/{plan_id}/completion`, new as a view of an ended plan built on its archive entry and its records (outcome, reason, its pull request, deliverables done, re-planning records, decisions taken, spend, duration); the epic's completion review and the landing record as they are; a subject that has been archived opens from the archive; opening a detail through a report's `detail` link marks the report read.

**Done when** (the package's own acceptance): The requirement and the specification of the change are merged in `plan-marshall-documentation`; a test per kind of the catalogue shows its source transition writing exactly one report with `from`, subject, `created_at`, `sent_at`, and `detail`, and no transition writing a kind outside it; a report written after a restart carries a later `sent_at`; the list returns unread reports only, newest first and grouped, with their count; marking one read, marking the ids shown read (not one that arrived since), and opening the detail each set `read_at` with channel and device, once, and the read state is the same on every device; the journal folds to the same state after a restart; the event stream announces a new and a read report; a plan's completion report shows outcome, reason, pull request, deliverables, re-planning records, decisions, spend, and duration for each outcome, also for an archived plan; no report ever waits for the operator.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use cases: UC-PLN-6 and its change Reports to the Operator
* Requirements: the new requirement the change drafts in PM-WF; PM-SEC-3, PM-SEC-7 (the operator's channels and devices)
* Specification: Runtime Model § Local API; Hypermedia Format § Representation Kinds; Filesystem Layout § Machine Layout; § Plan & Epic Archives; Store Schemas § Format Versioning; Identifiers § Stores; Model Work § Spend Accounting; CLI & Security § Web Devices; § Operator Channel Isolation

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

- OBSERVED: the package file and its index row (`blocked`; depends on `operator-wait-and-machine-scope`, `entity-addressing`, `plan-and-epic-control`, `epic-auto`, `decision-engine`, `start-from-clients`; the change Reports to the Operator and UC-PLN-6 agreed (no work package)) — read at `doc/plans/operator-reports.adoc` and `doc/plans/README.adoc` (main at 557ee81)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); PLAN-21 (`entity-addressing`); PLAN-51 (`plan-and-epic-control`); PLAN-57 (`epic-auto`); PLAN-47 (`decision-engine`); PLAN-48 (`start-from-clients`); the change Reports to the Operator and UC-PLN-6 agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-58-operator-reports.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
