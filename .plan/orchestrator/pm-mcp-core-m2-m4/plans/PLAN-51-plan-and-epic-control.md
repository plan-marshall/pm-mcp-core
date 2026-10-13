# PLAN-51: Controlling plans and epics

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-51-plan-and-epic-control.md` and is queued as one row file, `queue/PLAN-51.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `plan-and-epic-control` of `doc/plans/plan-and-epic-control.adoc` (added to the index on 2026-10-12): controlling plans and epics. Serves: Use Cases, UC-CTL-1, UC-CTL-2, UC-CTL-4, UC-CTL-5, UC-PLN-5, UC-PRJ-3; the actions on plans and epics, a plan's settings, and the release of a project in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-workflow (pause and start, the end of a plan, a plan's settings, the epic's next plan, archive check, and cancel, the release); pm-core (the plan's activity, the archive of an ended plan, the inbox records of the operator's acts, the epic's `terminal_reason`); pm-runtime (the endpoints, the operator verbs). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/plan-and-epic-control.adoc` § What, in its words.

1. Specification first*: the package writes the changes Pausing and Starting a Plan, Cancelling and Abandoning Archive, The Epic Is Told, An Epic's Actions, Holds and Their Acts, and Releasing Ends Work as the Operator Ends It into `plan-marshall-documentation` as part of its own work, once UC-CTL-1, UC-CTL-2, UC-CTL-4, UC-CTL-5, and UC-PRJ-3 are agreed (Specification Changes for the Clients, Packages).
2. A plan's actions by state* (UC-CTL-1): running: pause, cancel, chat, settings; pausing: start (the pause withdrawn), cancel, chat, settings; paused: start, abandon, chat, settings; waiting for the operator: those of running, beside the decision it waits for; held: those of running and the acts of its hold; ended: its view and a read-only chat. Pause with its point (now: tasks withdrawn and jobs stopped, both run again on start; after the current task, the default; after the current phase); a plan of an epic created and started in one act, never waiting to be started.
3. The ends of a plan*: cancel (a running, pausing, waiting, or held plan) and abandon (a paused one) through `plan.aborted` with the outcome `cancelled` or `abandoned` and the operator's reason, then the archive; the cancel of a plan held by an integrity pause is that pause's _abort_, with the outcome `aborted` and the operator's reason. The closed list of plan outcomes stays the terminal-state catalogue's (below), which this package extends by `cancelled`, `abandoned`, and `released`, each archived with its reason; the open pull request closed with the reason or left open; a claimed lesson released on every end without a landing (A Claimed Lesson Ends with the Landing, built by `start-from-clients`).
4. A plan's settings* (UC-CTL-5): `plan-settings.open` (each value with where it comes from: the plan, the project, the machine), `plan-settings.aspect`, `plan-settings.value` (the current value preselected, or remove the plan's value), `plan-settings.validate` (an enrolled harness, an allowed model, an available provider, a landing variant that fits the project and the plan; refused with the reason, back to `plan-settings.value`), `plan-settings.apply` (written to the plan's `overrides.json` of `machine-onboarding-unit`, audited, a new instance version of the composition event `override` under the position rule); a change whose new version would not contain the plan's current state and call stack is refused as too late, back to `plan-settings.aspect`; a change made against an older revision of the plan's settings is refused at `plan-settings.apply` with `conflict` and the current value, back to `plan-settings.value` (the rule of `machine-onboarding-unit`, Concurrent Changes of a Setting); what runs keeps its value, the new one applies where the plan next reads it.
5. Its epic told*: an inbox record per operator act on a plan of a live epic (`paused`, `started`, `cancelled`, `abandoned`, `revised`, `settings_changed` with the aspect), the queue row following the plan's state (its status gains `pausing`, `paused`, `cancelled`, `abandoned`), `coordinate` deciding on it.
6. A restart*: a paused plan stays paused, a pausing one holds at its point.
7. The epic as a resource* (UC-PLN-5): `GET /api/v1/epics/{epic_id}` with its rows, sources, landings, registers' counts, the draft while one is open, the archive check, its history; the operator's acts as its endpoints.
8. Holds and their acts* (UC-CTL-4): each hold a record with its kind from the closed list `stalled`, `condition`, `integrity_pause`, `held`, `landing_failed`, its cause, attempts, and the acts that resolve it, each an operator endpoint of both clients, and beside them cancel (for an integrity pause its _abort_: a plan `aborted`, an epic `cancelled`); after an act `hold.apply` goes to `hold.show` with what still holds and its cause, or to `hold.released`, from which the plan, the epic, or the landing goes on (the only way out of a hold that no longer holds); a condition of an expired credential resolved by the machine's settings (UC-ONB-10); `paused` is neither a hold nor a kind of `needs_operator`, a paused plan shows by its state; `facts.operator_command` goes.
9. Chat*: offered among the actions of a plan and of an epic in every state that has not ended, and read-only on an ended plan or epic beside its view, over its archive entry; the conversation itself, also its follow-up plan from ended work, is the sub-workflow of `chat`.
10. An epic's actions by state* (UC-CTL-2): running: next plan, decompose again, findings review, watches review, archive, cancel, chat; waiting for the operator: those of running, beside what it waits for; held: those of running and the acts of its hold; landing: those of running, archive offered disabled until the ledger has landed; ended: its view and a read-only chat.
11. Next plan*: start the next launchable row, creating its plan; the reasons per row when none is; with the scope full the operator wait `epic.raise-scope` (raise `parallelization_scope` by one, a ledger write of the header, then `epic.next-work`; or leave it).
12. Archive*: the archive check before `epic.archive`, where only an `open` (undecided) finding holds it; kept findings and watches are listed as carried and archived with the ledger; `terminal_reason` `completed`.
13. Cancel*: with the choice for its plans; `terminal_reason` `cancelled`, the value `abandoned` of today renamed (pre-1.0, no shim).
14. The release of a project* (UC-PRJ-3, moved here from `project-onboarding-unit`, since it ends plans and epics with the outcomes of this package): release and unenrol as operator acts of both clients, confirmed with what they end; unenrol refused while work runs, listing it; `project.release-wait` lets a running `git.land-removal` of the project finish within its bound and refuses the release, ending nothing, when it does not; `project.release` deletes the pending entries with their sources, ends a running onboarding of the project, ends every plan whatever its state through `plan.aborted` with the outcome `released` (never `cancelled` or `abandoned`) and every epic with the `terminal_reason` `released`, archives each, then closes the plan lifecycle; nothing is left to tell the epics.

**Done when** (the package's own acceptance): The specification of the changes is merged in `plan-marshall-documentation`; a test per action and state of a plan and of an epic shows what it does and what it refuses, also waiting for the operator, held, and an epic landing with archive disabled; each pause point holds where it says, also at an operator wait and at a loop-back; a paused plan serves nothing until started and resumes where it stopped; an ended plan is archived with each of its outcomes and reason, the cancel of an integrity pause with `aborted`; a settings change applies from the plan's next read, is refused with its reason for an invalid value, as too late where the new version would not contain the plan's position, and with `conflict` and the current value where another change came first, and composes an instance version of event `override`; every act on a plan of an epic, a settings change included, reaches the epic's inbox; next plan with `epic.raise-scope`, the archive check (an open finding holds it, kept findings and watches carried), and cancel behave as specified, also with no launchable row, and an epic ends `completed`, `cancelled`, or `released`, never `abandoned`; every hold of the closed list offers its acts and cancel as endpoints, an act ending in `hold.show` while something still holds and in `hold.released` otherwise, and no representation carries `facts.operator_command` any more; a release waits for a running `git.land-removal` and is refused when it does not finish, deletes the pending entries, ends a running onboarding, and ends every plan `released` and every epic `released`, each archived.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use cases: UC-CTL-1, UC-CTL-2, UC-CTL-4, UC-CTL-5, UC-PLN-5 and their changes; UC-PRJ-3 and its change Releasing Ends Work as the Operator Ends It; Plan Overrides, Concurrent Changes of a Setting
* Specification: State Catalogue § Terminal States; Model Work § Epic Coordination; State Catalogue § Epic; Filesystem Layout § Archives; Workflow DSL § Plan Instance; Store Schemas § Epic Header; CLI & Security § Enrolment, Lifecycle Gate & Release

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

- OBSERVED: the package file and its index row (`blocked`; depends on `operator-wait-and-machine-scope`, `chat`, `machine-onboarding-unit`, `project-onboarding-unit`; UC-CTL-1, UC-CTL-2, UC-CTL-4, UC-CTL-5, UC-PLN-5, and UC-PRJ-3 agreed (no work package)) — read at `doc/plans/plan-and-epic-control.adoc` and `doc/plans/README.adoc` (main at bed24eb)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); PLAN-55 (`chat`); PLAN-46 (`machine-onboarding-unit`); PLAN-45 (`project-onboarding-unit`); UC-CTL-1, UC-CTL-2, UC-CTL-4, UC-CTL-5, UC-PLN-5, and UC-PRJ-3 agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-51-plan-and-epic-control.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
