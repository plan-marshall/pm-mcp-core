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

Implement the work package `plan-and-epic-control` of `doc/plans/plan-and-epic-control.adoc` (added to the index on 2026-10-12): controlling plans and epics. Serves: Use Cases, UC-CTL-1, UC-CTL-2, UC-CTL-4, UC-PLN-5; the actions on plans and epics in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-workflow (pause and start, the end of a plan, the epic's next plan, archive check, and cancel); pm-core (the plan's activity, the archive of an ended plan, the inbox records of the operator's acts); pm-runtime (the endpoints, the operator verbs). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/plan-and-epic-control.adoc` § What, in its words.

1. Specification first*: the package writes the changes Pausing and Starting a Plan, Cancelling and Abandoning Archive, The Epic Is Told, and An Epic's Actions into `plan-marshall-documentation` as part of its own work, once UC-CTL-1 and UC-CTL-2 are agreed (Specification Changes for the Clients, Packages).
2. A plan*: running, pausing, or paused; pause with its point (now: tasks withdrawn and jobs stopped, both run again on start; after the current task, the default; after the current phase), start (a paused plan, or withdrawing the pause of a pausing one); a plan of an epic created and started in one act, never waiting to be started; cancel and abandon through `plan.aborted` with their outcome and reason, then the archive; the open pull request closed with the reason or left open.
3. Its epic told*: an inbox record per operator act on a plan of a live epic, the queue row following the plan's state, `coordinate` deciding on it.
4. A restart*: a paused plan stays paused, a pausing one holds at its point.
5. The epic as a resource* (UC-PLN-5): `GET /api/v1/epics/{epic_id}` with its rows, sources, landings, registers' counts, the draft while one is open, the archive check, its history; the operator's acts as its endpoints.
6. Holds and their acts* (UC-CTL-4): each hold a record with its kind, cause, attempts, and the acts that resolve it, each an operator endpoint of both clients; `facts.operator_command` goes.
7. An epic*: next plan (start the next launchable row, creating its plan, the reasons when none is, the offer to raise the scope); the archive check before `epic.archive`; cancel with the choice for its plans.

**Done when** (the package's own acceptance): The specification of the changes is merged in `plan-marshall-documentation`; a test per action and state shows what it does and what it refuses; each pause point holds where it says, also at an operator wait and at a loop-back; a paused plan serves nothing until started and resumes where it stopped; an ended plan is archived with its outcome and reason; every act on a plan of an epic reaches the epic's inbox; next plan, the archive check, and cancel behave as specified, also with the scope full and with no launchable row; every hold offers its acts as endpoints, and no representation carries `facts.operator_command` any more.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use cases: UC-CTL-1, UC-CTL-2, UC-CTL-4, UC-PLN-5 and their changes
* Specification: State Catalogue § Terminal States; Model Work § Epic Coordination; State Catalogue § Epic; Filesystem Layout § Archives

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

## Claim Labels

- OBSERVED: the package file and its index row (`blocked`; depends on `operator-wait-and-machine-scope`; UC-CTL-1, UC-CTL-2, UC-CTL-4, and UC-PLN-5 agreed (no work package)) — read at `doc/plans/plan-and-epic-control.adoc` and `doc/plans/README.adoc` (main at 7503bc2)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); UC-CTL-1, UC-CTL-2, UC-CTL-4, and UC-PLN-5 agreed (no work package)

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
