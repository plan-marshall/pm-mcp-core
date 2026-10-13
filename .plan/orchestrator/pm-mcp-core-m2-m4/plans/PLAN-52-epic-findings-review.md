# PLAN-52: An epic's findings and watches, and their review

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-52-epic-findings-review.md` and is queued as one row file, `queue/PLAN-52.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `epic-findings-review` of `doc/plans/epic-findings-review.adoc` (added to the index on 2026-10-12): an epic's findings and watches, and their review. Serves: Use Cases, UC-CTL-3; the reviews in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-workflow (the sub-workflow `epic.review`, the epic's tasks that add items); pm-core (the two registers in the ledger). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/epic-findings-review.adoc` § What, in its words.

1. Specification first*: the package writes the changes An Epic's Findings and Watches and The Review into `plan-marshall-documentation` as part of its own work, once UC-CTL-3 is agreed.
2. The registers of findings (a problem no plan owns yet, to be fixed; never called a defect) and watches (an observation to check again, with its trigger) in the epic's ledger; each item's state `open` (not decided yet), `kept`, `dropped`, or `promoted` with its row; `epic.analyze-landing` and `coordinate` adding items; a watch's trigger re-checked, the watch becoming a finding or closed; new findings as a `needs_operator` kind. Only an `open` finding holds the epic's archive; kept findings and watches are carried into it (the check is `plan-and-epic-control`'s).
3. The sub-workflow `epic.review`: `review.open` with the planning role's proposal per item; at `review.item` keep (`review.keep`: kept as a finding, or moved to the watches where the kind changes), drop (`review.drop`, with the reason), promote, edit, chat (the sub-workflow `chat` with one item or the whole register as its scope); the remaining proposals accepted at once after one confirmation; `review.commit` writes the dispositions and lands them in one ledger write.
4. Promote*: `review.draft-plan` by the planning role, then `review.confirm-plan` with stage (a staged row under the surface checks), back, and edit: `review.instruct-plan` (the operator's words for the drafted plan), `review.redraft-plan` (the role drafts the plan again), `review.read-back-plan` (old against new: accept back to `review.confirm-plan` with the new draft, instruct again, or discard back to `review.confirm-plan` with the draft before).
5. Edit*: `review.instruct`, the role's rewrite, `review.read-back` (accept: applied, back to `review.item`; instruct again; discard: back to `review.item`, the item unchanged).
6. The operator's words* in edit and chat are not interpreted: the role works from them, and only what it proposes (a rewrite, a redrafted plan, a change in a chat) passes a read-back; the role reads the items as data, a finding from outside the machine still marked untrusted.

**Done when** (the package's own acceptance): The specification of both changes is merged in `plan-marshall-documentation`; a landing and an inbox finding each add an item; a review takes every disposition, also keeping a finding as a watch and the reverse, and lands them in one ledger write; no model's change reaches a register or a drafted plan without the operator's read-back, and each branch of `review.read-back` and `review.read-back-plan` is tested; a promoted item is a staged row that passed the surface check.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use case: UC-CTL-3 and its changes
* Specification: Model Work § Epic Coordination; Store Schemas § Epic & Change Ledger; Model Work § Default Role Set

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

- OBSERVED: the package file and its index row (`blocked`; depends on `plan-and-epic-control`, `chat`; UC-CTL-3 agreed (no work package)) — read at `doc/plans/epic-findings-review.adoc` and `doc/plans/README.adoc` (main at bed24eb)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-51 (`plan-and-epic-control`); PLAN-55 (`chat`); UC-CTL-3 agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-52-epic-findings-review.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
