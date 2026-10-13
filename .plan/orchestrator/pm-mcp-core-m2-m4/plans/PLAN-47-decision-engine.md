# PLAN-47: Decisions as the operator meets them

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-47-decision-engine.md` and is queued as one row file, `queue/PLAN-47.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `decision-engine` of `doc/plans/decision-engine.adoc` (added to the index on 2026-10-12): decisions as the operator meets them. Serves: Use Cases, UC-PLN-2; decisions in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-workflow (the decision's states, the interpretation and read-back); pm-core (the decision record, its revisions, the support per option); pm-runtime (the open decisions across scopes, the channel `cli`). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/decision-engine.adoc` § What, in its words.

1. Specification first*: the package writes the engine side of The Decision Structure, Own Words, and Answers from the Terminal Client into `plan-marshall-documentation` as part of its own work, once UC-PLN-2 is agreed; `decision-contract` of `pm-mcp-clients` writes the client side beside it.
2. The decision as a structure with its id (`<scope_id>/<question_id>`) and revisions; what it blocks and how reversible it is; the support per option derived by the runtime from the quorum's votes.
3. The interpreting role `interpret` (`read_only`, its corpus topic `own_words`), the read-back and the clarification.
4. None fits and later*: _none fits_ goes to `decision.withdraw`, which closes the decision withdrawn with the operator's note, and the asking role thinks again and asks anew through `decision.open`, a new decision; _later_ answers nothing, the decision stays open and announced (`decision.announce`), the client leaves it, and its subject (the plan, the epic, or the setup) waits.
5. A waiver request* is announced like a decision and answered by `waive` or `decline` (the outcome `declined`, the remediation unit going on; the wait is `operator-wait-and-machine-scope`'s), both operator overrides: no preselection, no interpretation, no chat.
6. A project's setup asks*: a decision of a project's workspace scope (a question of its setup) is delivered, announced with its project, and answered like a plan's, through the same states and record; it holds the setup only, never a plan; Chat over it is offered as for a plan's, disabled with its reason where the setup's workflow raised the question and no planning role exists (the routing is `chat`'s).
7. The open decisions across scopes, the workspace scope of a project included, with since when each waits; the channel `cli`, the second channel of content answers beside `web`; the refusal of an answer to an older revision.
8. Chat before answering*: `decision.answer` and the decision record leave room for the choice Chat that `chat` adds on top of this package (the record saying that an answer followed a chat, the transcript kept with the decision, a decision closed as withdrawn with its reason); the chat itself, its routing, and its rules are that package's.

**Done when** (the package's own acceptance): The specification is merged in `plan-marshall-documentation`; every scenario of `decision-contract` replays against the runtime; an answer in own words reaches the decision only after its read-back; _none fits_ closes the decision withdrawn and a new one is asked, _later_ leaves it open; the first valid answer of either channel wins; a decision of a project's setup is announced, answered, and recorded like a plan's, holds no plan, and offers its chat disabled where the setup's workflow raised it.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use case: UC-PLN-2 and its changes
* Specification: Model Work § Questions; § Decision Quorum; Store Schemas § Decision Record

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

- OBSERVED: the package file and its index row (`blocked`; depends on `operator-wait-and-machine-scope`; UC-PLN-2 agreed (no work package)) — read at `doc/plans/decision-engine.adoc` and `doc/plans/README.adoc` (main at 557ee81)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/engine/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); UC-PLN-2 agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-47-decision-engine.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
