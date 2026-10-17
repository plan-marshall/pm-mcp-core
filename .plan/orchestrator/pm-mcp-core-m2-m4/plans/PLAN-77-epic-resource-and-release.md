# PLAN-77: The epic as a resource, its actions, and the release of a project

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-77-epic-resource-and-release.md` and is queued as one row file, `queue/PLAN-77.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `epic-resource-and-release` of `doc/plans/epic-resource-and-release.adoc`: the epic as a resource, its actions, and the release of a project. Serves: roadmap Milestone 6 (the release of a project), Milestone 12 (the operator's acts on an epic); Use Cases, UC-CTL-2, UC-PLN-5, UC-PRJ-3; the actions on epics and the release of a project in both clients of `pm-mcp-clients`. Where: pm-workflow (the engine-owned acts on an epic: the epic's next plan, archive check, and cancel); pm-core (the epic's `terminal_reason`, the release recorded in the enrolment); pm-runtime (the operator endpoints of both clients and their verbs, the epic resource, the release). This package is the new half of `plan-and-epic-control` that PLAN-75 (#85) split off on 2026-10-17: it takes 7 of its 14 bullets (the epic as a resource, its actions and the release of a project), and it depends on `plan-and-epic-control` (PLAN-51). The old spec of PLAN-51 is the source of this one. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

The package file introduces itself: This package implements Model Work § An Epic's Actions, Runtime Model § The Epic as a Resource, and Credentials & Enrolment § Releasing Ends Work as the Operator Ends It (`PM-WF-23`, `PM-MIG-5`). The step names the use cases give these flows (`epic.actions`, `epic.raise-scope`, `epic.confirm-cancel`, `epic.cancel`) are operator acts over the local API, never workflow states (State Catalogue § Operator Flows That Are Not Workflow Units). It builds on the plan's activity, the ends of a plan, and the holds of `plan-and-epic-control`.

**Parked:** waits on packages that are not done: `plan-and-epic-control`. Re-ground this spec before emitting it.

## Deliverables

The bullets of `doc/plans/epic-resource-and-release.adoc` § What at `c281f6c`, in its words.

1. The epic as a resource (UC-PLN-5): `GET /api/v1/epics/{epic_id}` with its rows, sources, landings, registers' counts, the draft while one is open, the archive check, its history; the operator's acts as its endpoints.
2. Chat: not built here. The action Chat among the actions of a plan and of an epic in every state, read-only on an ended plan or epic, its endpoint, and the conversation itself, also its follow-up plan from ended work, are `chat`'s, which adds the action to the lists of actions this package builds; this package leaves its place in them and does not wait for it.
3. An epic's actions (UC-CTL-2): next plan, decompose again, the reviews, archive, cancel, chat, and the toggle _Automatic: on/off_ with the overlap limit, each an operator endpoint of the epic's resource, validated, answered with the epic's new state, audited, and re-entering `epic.next-work` as an `operator_action`; what the epic starts and does by itself is `epic-auto`'s, the review and decompose again `epic-decomposition`'s.
4. Next plan: `epic.next-work` starts the next launchable row now under the same admission tests; with no launchable row it answers why per row (`dependency`, `lock`, `scope`, `overlap`, `stale`, `held`); with `parallelization_scope` full it offers to raise it by one, a ledger write of the header.
5. Archive: `epic.review-completion` first where it has not run since the epic was through, then the archive check `epic.archive-check` (no plan running, pausing, or paused, every row shipped or ended, no `open` finding, the ledger landed, the completion review done), archiving through `epic.archive` only when it passes and otherwise answering what is open; kept findings and watches are listed as carried and archived with the epic; `terminal_reason` `completed`.
6. Cancel: with the operator's reason (`pm-operator abort epic` its shell form) and the choice for its plans (cancel them, each `cancelled`, or let them run on detached); the epic ends through `epic.aborted` with `terminal_reason` `cancelled`, the value `abandoned` of the code renamed (pre-1.0, no shim). This cancel is also the resolution _abort_ of an integrity pause of an epic, a hold of `plan-and-epic-control` (its bullet _Holds and their acts_), which offers it as the hold's act.
7. The release of a project (UC-PRJ-3, an act of both clients, `POST /api/v1/projects/{project_id}/release`, `pm-operator project release` its terminal form, confirmed with what it ends): a running short landing of the project (`git.land-removal`, `git.land-unit`) finishes first within `release.landing_wait_seconds`, no new start or short landing admitted meanwhile (`lifecycle_closed`), and when it does not, the release is refused `release_landing_pending` and ends nothing; then `released_at` is written into the enrolment with an `operator` audit record; every plan that has not ended, whatever its state, ends through `plan.aborted` with the outcome `released` (never `cancelled` or `abandoned`) and is archived, its open decisions and waiver requests closed `cancelled` and its lesson claims released; every epic ends with the `terminal_reason` `released` by an engine-owned transition into `epic.aborted` that bypasses `epic.archive`, its open tasks withdrawn and its machine scope archived in the project's machine store only, its ledger left in `.marshall/epics/<epic_id>/` untouched, with no ledger commit and no epic landing; the outcome `released` changes no queue row; the pending entries are deleted with their sources, each entry ended as a scope first so that whatever stands on its call stack ends with it (a running task withdrawn with the reason `withdrawn`), and a running onboarding or setup run is ended; the report `project.released`; the release touches no repository. While `released_at` is set, a start is refused `lifecycle_closed`; enrolling again clears it. Unenrol stays refused while work runs, `scope_active` naming it.

**Done when** (the package's own acceptance): A test per act of an epic shows what it does and what it refuses, also an epic whose ledger has not landed failing the archive check; next plan with the raise of `parallelization_scope`, the archive check (an open finding holds it, kept findings and watches carried), and cancel behave as specified, also with no launchable row, and an epic ends `completed`, `cancelled`, or `released`, never `abandoned`; a release waits for a running short landing and is refused `release_landing_pending` when it does not finish, deletes the pending entries, a task running in one withdrawn, ends a running onboarding, ends every plan `released` and every epic `released`, archives the epics machine-side only with their ledgers untouched, changes no queue row, and makes no commit in any repository. A release refuses new starts and short landings of its project while it runs, and a released project refuses a start until it is enrolled again, after which a start is accepted.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-CTL-2, UC-PLN-5; UC-PRJ-3
- Requirements: PM-WF-23; PM-WF-15; PM-MIG-5
- Specification: the sections the introduction of _What_ names; State Catalogue § Plan Terminal States; § Epic Workflow; Filesystem Layout § Archives; Store Schemas § Epic Header; Credentials & Enrolment § Enrolment, Lifecycle Gate & Release; Runtime Model § Error codes

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

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/epic-resource-and-release.adoc` § What, Trace, Done When on `origin/main` at `c281f6c` (the file PLAN-75 #85 created: 7 bullets)
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: doc/plans/epic-resource-and-release.adoc re-read at c281f6c: What holds 7 bullets, Where and Done When as in the Objective and Deliverables above
- OBSERVED: the index gives this package the status `open` and the dependencies below — read at `doc/plans/README.adoc` § Packages on `origin/main` at `c281f6c`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: README.adoc row epic-resource-and-release re-read at c281f6c: open; depends on PLAN-51 (plan-and-epic-control) (coupling: the bullet _Holds and their acts_ there offers the epic cancel built here as an integrity pause's resolution _abort_
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file and the class-level surfaces of the former specs, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; surface carried over from the former specs, each checked against the code at 238c823, and partitioned or merged at c281f6c
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (60), `WorkflowLogMessages` (5) — `static final LogRecord` counted on `origin/main` at `c281f6c`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: static final LogRecord counted at c281f6c: CoreLogMessages 5, RuntimeLogMessages 60, WorkflowLogMessages 5
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis: whether the package logs is decided at the trace matrix; no implementing code exists yet
- HYPOTHESIS: whether the package makes an HTTP call is decided at the specification sections of the trace starting set; the two HTTP constraints bind only if the trace matrix shows one, and only in `pm-runtime` or a provider module — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; pm-core and pm-workflow cannot depend on cui-http
- OBSERVED: the specification and watch parts of the Expected Surface changed since the specs of the former packages were last grounded at `238c823` only as listed in the verdict — `git diff --numstat 238c823..c281f6c`; the executing plan re-reads them at its branch point
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git diff --numstat 238c823..c281f6c over the declared specification and watch parts: specification/credentials-and-enrolment/02-enrolment-and-project-lifecycle.adoc (+1/-1); specification/filesystem-layout/03-project-store.adoc (+3/-3); specification/model-work/08-epic-coordination.adoc (+2/-2)
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `epic-resource-and-release`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: row epic-resource-and-release is open at c281f6c; open PRs 71, 86 and 87 (ci pr view): none names the package; dependencies not done are listed in the Parked line, if any

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/epic/` — epic coordination, next plan, archive check, cancel and the queue and inbox rows; carried from the former spec
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — the epic's `terminal_reason`, the release recorded in the enrolment — inferred from the `Where` line
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/web/` — the operator endpoints of both clients and their verbs, the epic resource — inferred from the `Where` line; the package name is a guess
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/` — the release of a project (unenrol); carried from the former spec
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- OBSERVED: `doc/plans/epic-resource-and-release.adoc` — the package file, deleted when the package is done
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-epic.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-epic/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-enrolment.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-enrolment/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/specification/credentials-and-enrolment/02-enrolment-and-project-lifecycle.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/filesystem-layout/03-project-store.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/08-epic-coordination.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/06-setup-epic-other.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/04-epic-and-change-ledger.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-51 (`plan-and-epic-control`) (coupling: the bullet _Holds and their acts_ there offers the epic cancel built here as an integrity pause's resolution _abort_; recorded on this edge, not as a reverse dependency)
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-77-epic-resource-and-release.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
