# PLAN-55: Chatting with the planning role

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-55-chat.md` and is queued as one row file, `queue/PLAN-55.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `chat` of `doc/plans/chat.adoc` (added to the index on 2026-10-12): chatting with the planning role. Serves: Use Cases, Chatting with the Planning Role; Chat on a plan and on an epic (UC-CTL-1, UC-CTL-2), in the review of an epic's registers (UC-CTL-3) and of its decomposition (UC-STA-6); the chat in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-workflow (the compiled unit `chat`, its routing, its call from a plan and from an epic); pm-core (the transcript kept with its subject); pm-runtime (the endpoints and operator verbs of Chat on a plan and on an epic). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/chat.adoc` § What, in its words.

1. Specification first*: the package writes the change Chatting with the Planning Role into `plan-marshall-documentation` as part of its own work, once it is agreed (Specification Changes for the Clients, Packages): a new unit section of the state catalogue, the unit among the compiled units of the workflow language, the operator-started revision in re-planning, and Chat in epic coordination.
2. The unit `chat`*: compiled like the source and epic units, its states `chat.<state>`, of phase `inherit` when called from a plan; called with its subject (a plan or an epic) and a scope (the whole plan, the whole epic, the decomposition draft, one register item, or the whole register).
3. Routing*: a plan's chat goes to its planning role `replan`, whose facts carry the plan's outline, its tasks with their status, the done deliverables and the footprint; an epic's chat, also in its decomposition and its review, goes to the epic's planning role `decompose`. Chat is offered disabled, with the reason, while no configuration of the routed role qualifies.
4. The states*: `chat.open` assembles the facts of the scope as data, untrusted items still marked untrusted; `chat.turn` waits for the operator's message (an operator wait) and then runs one task of the routed role that answers and may propose changes, again until the operator ends the chat; `chat.read-back` shows each proposed change as a read-back to accept or reject, nothing changing without accept; `chat.apply` applies an accepted change through the caller's own path and its checks; `chat.end` ends the chat.
5. Apply by subject*: a plan through a revision under the guards of a first plan (outline validation, derivation dry-run, task graph, Q-Gate), composing an instance version of event `replan`; an epic through staged rows under the epic's checks (disjoint surfaces, dependencies without a cycle). The decomposition draft and a register item are applied by the path their callers pass in (`epic-decomposition`, `epic-findings-review`).
6. A running plan*: a question never touches it; an accepted revision takes effect at the next task boundary, what runs finishing first. A chat's revision is the operator's own act: it spends nothing of the plan's re-planning budget, and the server never starts a chat itself. A change to a plan an epic started reaches the epic's inbox like every act.
7. Chat as an action*: on a plan in every state that has not ended (running, pausing, paused, waiting for you, held) and on an epic in every state that has not ended, each an operator endpoint of both clients.
8. The transcript*: kept with its subject for the audit, never sent elsewhere; the operator's words interpreted with a read-back, the role reading the content of the plan or the epic as data.

**Done when** (the package's own acceptance): The specification of the change is merged in `plan-marshall-documentation`; a test per subject and scope shows the routing to its role, a turn, a read-back accepted and one rejected, and the end; nothing changes without an accept; an accepted revision of a running plan takes effect at the next task boundary, composes an instance version of event `replan`, passes the guards of a first plan, and leaves the re-planning budget untouched; an accepted change to an epic is a staged row that passed its checks; a change to a plan of an epic reaches the epic's inbox; Chat is disabled with its reason while the routed role is not qualified; the transcript is kept with its subject.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use case: Chatting with the Planning Role; Own Words (the read-back)
* Specification: Workflow DSL § Workflow Definition Units; § Reusable Sub-Workflows; Phase Workflows § Re-Planning, Actions & Guards; § Apply, Re-Entry & Instance Version; State Catalogue § Unit `replan`; Model Work § Staging-Time Surface Disjointness; § Default Role Set; § Qualification

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

- OBSERVED: the package file and its index row (`blocked`; depends on `operator-wait-and-machine-scope`, `workflow-engine`, `plan-instance`, `task-queue-and-protocol`, `role-sets-and-model-caller`, `role-qualification`, `decision-engine`; the re-planning of a running plan, its unit `replan` and its guards (no work package); the change Chatting with the Planning Role agreed (no work package)) — read at `doc/plans/chat.adoc` and `doc/plans/README.adoc` (main at c226120)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/web/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); PLAN-13 (`workflow-engine`); PLAN-24 (`plan-instance`); PLAN-28 (`task-queue-and-protocol`); PLAN-26 (`role-sets-and-model-caller`); PLAN-34 (`role-qualification`); PLAN-47 (`decision-engine`); the re-planning of a running plan, its unit `replan` and its guards (no work package); the change Chatting with the Planning Role agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-55-chat.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
