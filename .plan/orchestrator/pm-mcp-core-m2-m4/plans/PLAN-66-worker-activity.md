# PLAN-66: What a worker does, and a worker off track

epic: pm-mcp-core-m2-m4
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-66-worker-activity.md` and is queued as one row file, `queue/PLAN-66.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `worker-activity` of `doc/plans/worker-activity.adoc`: what a worker does, and a worker off track. Serves: Roadmap Milestone 4 (the worker activity and the off-track signals); the activity of a task below a plan or an epic (Use Cases, UC-PLN-1) in both clients of `pm-mcp-clients`. Where: pm-core (the activity kinds of the profile schema and the mapping `headless.events`, the activity journal in the store of the task's scope); pm-runtime (the mapping of a worker's output stream while it runs, the off-track signals and the server's actions, the activity endpoints and the stream of one task). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/worker-activity.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Client Profiles § What a Worker Does, Model Work § A Worker Off Track, and Store Schemas § Activity Journal (`PM-WORK-7`).
2. Events, mapped per harness: `headless.events` maps a line of the worker's output stream onto the closed, harness-neutral activity kinds `text` (cut to the compiled bound), `tool_call` (the tool's name and its target, never its whole arguments), `tool_result` (its size and whether it failed, never its content), `file_changed`, and `turn` (its usage through the usage pointers of `token-usage`); a line that matches no mapping is counted, not shown; the server adds the entries it knows itself (the links the worker followed, its questions and answer acknowledgements, the off-track signals and actions).
3. The activity journal: per task, append-only in the store of the task's scope, written while the worker runs, masked, bounded, archived with the plan and deleted with it; untrusted data, shown as plain text and never read by a model as instructions.
4. The off-track signals: computed by the runtime from the activity journal and the task's records, never by a model (`repeating`, `no_progress`, `over_size`, `outside_footprint`), each against its compiled threshold, which a role artifact may lower in its `off_track` member and never raise, evaluated at every activity entry in the transaction that appends it, at most once per attempt and plan task; a signal is no limit and ends no plan; the action the role artifact chooses from the closed list (`show`, the default, `nudge`, `yield`, `restart`), recorded in the queue task's `signals[]` and as a `signal` entry of the activity journal, a `yield` or `restart` reported `task.off_track` (`operator-reports`); a running plan's `off_track` with its signal in the project's resource (`process-and-overview`).
5. Served to the clients: a task's activity from a cursor and as a live stream a client opens only while it shows the task; the plan's process names per leaf its tasks with a link to their activity.
6. Verified per harness: the stage `events` of the profile verification checks that every mapping resolves and every expected kind appears, recording per kind `reported` or `missing`; a recorded output of each shipped harness version is a parsing fixture, written for the test, never copied from the content of the product.

**Done when** (the package's own acceptance): A recorded output of each shipped harness version maps onto its expected activity entries, an unmatched line counted and not shown, no tool result's content and no whole argument list recorded; the journal is masked and bounded, archived with its plan, and served from a cursor and as a live stream; each off-track signal holds on a fixture at its threshold and not below it, a lowered threshold of a role artifact applies and a raised one is refused, each action is recorded in `signals[]` and the journal, a signal takes its action at most once per attempt and plan task, and a `yield` or `restart` is reported once; no signal ends a plan; the stage `events` records each kind `reported` or `missing` and fails for a mapping that does not resolve.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use case: UC-PLN-1
- Requirements: PM-WORK-7; PM-WORK-3; PM-SEC-1
- Specification: the sections the first bullet of _What_ names; Client Profiles § Profile Verification; Model Work § Output Read While the Worker Runs
- Watch: Model Work; Client Profiles

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

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/worker-activity.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 710da6150c6da19d4335643ca6070ef5517f0896 | by: pm-mcp-core-m2-m4/restructure | rescoped: n/a | evidence: regenerated by the orchestrator from the package file and its index row at 710da6150c6da19d4335643ca6070ef5517f0896 after the specification moved into pm-mcp-core (#77); § What, Trace, Done When and Depends on are the package's own
- OBSERVED: the package file and its index row (`open`; depends on PLAN-30 (`supervisor`); PLAN-19 (`profile-loader`); PLAN-28 (`task-queue-and-protocol`)) — read at `doc/plans/worker-activity.adoc` and `doc/plans/README.adoc` (main at a8a4214, regenerated by cleanup)
  - verdict: corroborated | checked_at: a8a421408bfa31a4a64630c5139fd9e50bfac6f3 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: regenerated by cleanup from doc/plans package file and index row at a8a421408bfa31a4a64630c5139fd9e50bfac6f3; Objective, Deliverables, Done when, Trace and Depends on are the package's own at that sha
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the class names of § What, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
- Verify-first clause: before scoping, list the open pull requests for one that names `worker-activity`, check in `doc/plans/README.adoc` that every dependency is `done`, and read the roadmap item this package serves for what the code already covers; a refutation of any claim above loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/profile/` — production code
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/profile/` — tests
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — production code
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/` — production code
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/` — tests
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/web/` — production code
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/web/` — tests
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/core-profile.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-worker.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-web.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-profile/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-worker/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-web/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/worker-activity.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/03-supervision-and-recycling.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/04-worker-launch.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-30 (`supervisor`); PLAN-19 (`profile-loader`); PLAN-28 (`task-queue-and-protocol`)
- Overlaps with: PLAN-01 (`lock-manager-and-leases`) in `core.store`; PLAN-06 (`store-substrate`) in `core.store`; PLAN-09 (`audit-trail`) in `core.store`; PLAN-11 (`enrolment-store`) in `core.store`; PLAN-12 (`session-log-and-generations`) in `core.store`; PLAN-13 (`workflow-engine`) in `core.store`; PLAN-14 (`operator-and-question-stores`) in `core.store`; PLAN-15 (`project-machine-store`) in `core.store`; PLAN-19 (`profile-loader`) in `core.profile`; PLAN-20 (`job-runner`) in `core.store`; PLAN-23 (`stalled-scope-detection`) in `core.store`; PLAN-30 (`supervisor`) in `runtime.worker`; PLAN-31 (`slots-usage-and-budgets`) in `core.store`; PLAN-42 (`operator-wait-and-machine-scope`) in `core.store`; PLAN-43 (`phase-sub-workflows`) in `core.store`; PLAN-45 (`project-onboarding-unit`) in `core.store`; PLAN-46 (`machine-onboarding-unit`) in `core.store`; PLAN-47 (`decision-engine`) in `core.store`; PLAN-48 (`start-from-clients`) in `core.store`; PLAN-50 (`recipe-workflows`) in `core.profile`; PLAN-51 (`plan-and-epic-control`) in `core.store`; PLAN-52 (`epic-findings-review`) in `core.store`; PLAN-53 (`epic-decomposition`) in `core.store`; PLAN-55 (`chat`) in `core.store`, `runtime.web`; PLAN-57 (`epic-auto`) in `core.store`; PLAN-58 (`operator-reports`) in `core.store`; PLAN-59 (`runtime-control`) in `core.store`; PLAN-61 (`token-usage`) in `core.profile`, `core.store`; PLAN-62 (`process-designer`) in `core.store`; PLAN-63 (`cleanup`) in `core.store`; PLAN-64 (`removed-concepts`) in `core.store`; PLAN-67 (`process-and-overview`) in `core.store`, `runtime.web`; PLAN-68 (`chat-plan-revision`) in `core.store`; every plan through `doc/plans/README.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-66-worker-activity.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
