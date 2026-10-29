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

Implement the work package `epic-findings-review` of `doc/plans/epic-findings-review.adoc`: an epic's findings and watches, and their review. Serves: roadmap Milestone 12 (the review of an epic's findings and watches in both clients); Use Cases, UC-CTL-3; the reviews in both clients of `pm-mcp-clients`. Where: pm-workflow (the sub-workflow `review`, called by `epic.review`, the epic's tasks that add items, the watches' re-check `epic.recheck-watches`); pm-core (the two registers in the ledger). The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

**Parked:** waits on packages that are not done: `plan-and-epic-control`, `chat`. Re-ground this spec before emitting it.

## Deliverables

The bullets of `doc/plans/epic-findings-review.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. Implements Model Work § An Epic's Findings and Watches, § The Review, State Catalogue § Review: `review`, and the registers of Store Schemas § Findings and Watches Registers (`PM-WF-15`).
2. The registers of findings (a problem no plan owns yet, to be fixed; never called a defect) and watches (an observation to check again, with its trigger) in the epic's ledger; each item with its epic-scoped id, statement, rules, origin, and state `open` (not decided yet), `kept`, `dropped` (with the reason), or `promoted` with its row; the epic's tasks adding items with the repeatable submit `submit register_item`; a watch's trigger (`landing` or `base_advanced`) re-checked, the watch becoming a finding or closed; new findings as a `needs_operator` kind. Only an `open` finding holds the epic's archive; kept findings and watches are carried into it (the check is `plan-and-epic-control`'s).
3. The sub-workflow `review`, called by `epic.review`: `review.open` with the planning role's proposal per item; at `review.item` keep (`review.keep`: kept as a finding, or moved to the watches where the kind changes), drop (`review.drop`, with the reason), promote, edit, chat (the sub-workflow `chat` with one item or the whole register as its scope); the remaining proposals accepted at once after one confirmation; `review.commit` writes the dispositions and lands them in one ledger write.
4. Promote: `review.draft-plan` by the planning role, then `review.confirm-plan` with stage (a staged row under the surface checks), back, and edit: `review.instruct-plan` (the operator's words for the drafted plan), `review.redraft-plan` (the role drafts the plan again), `review.read-back-plan` (old against new: accept back to `review.confirm-plan` with the new draft, instruct again, or discard back to `review.confirm-plan` with the draft before).
5. Edit: `review.instruct`, the role's rewrite `review.rewrite`, `review.read-back` (accept: applied, back to `review.item`; instruct again; discard: back to `review.item`, the item unchanged).
6. The operator's words in edit and chat are not interpreted: the role works from them, and only what it proposes (a rewrite, a redrafted plan, a change in a chat) passes a read-back; the role reads the items as data, a finding from outside the machine still marked untrusted.

**Done when** (the package's own acceptance): A landing and an inbox finding each add an item; a review takes every disposition, also keeping a finding as a watch and the reverse, and lands them in one ledger write; no model's change reaches a register or a drafted plan without the operator's read-back, and each branch of `review.read-back` and `review.read-back-plan` is tested; a promoted item is a staged row that passed the surface check.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use case: UC-CTL-3
- Requirements: PM-WF-15; PM-WF-24 (the chat in the review)
- Specification: the sections the first bullet of _What_ names; Model Work § Epic Coordination; Store Schemas § Epic & Change Ledger; Model Work § Default Role Set

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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/epic-findings-review.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/epic-findings-review.adoc is unchanged since d1d58d2 (#77, no later commit touches it); its What, Trace and Done When equal the Deliverables, trace set and acceptance above
- OBSERVED: the package file and its index row (`open`; depends on PLAN-51 (`plan-and-epic-control`); PLAN-55 (`chat`)) — read at `doc/plans/epic-findings-review.adoc` and `doc/plans/README.adoc` (main at a8a4214, regenerated by cleanup)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: README.adoc row epic-findings-review re-read at 238c823: open; depends on plan-and-epic-control, chat; matches the claim
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: Model Work Epic Coordination has its package workflow.epic (module-structure.adoc, package-info.java), not on the surface; added
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the preconditions the clause checks hold at 238c823: the index row is open and not blocked, and no open pull request names the package

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/epic/` — the package of Model Work § Epic Coordination (epic review and registers; `package-info.java` only today); added by cleanup on 2026-10-16
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-unit.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-unit/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- OBSERVED: `doc/plans/epic-findings-review.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/specification/model-work/01-roles-and-configuration.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/08-epic-coordination.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/17-epic-decomposition-and-review.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/04-epic-and-change-ledger.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-51 (`plan-and-epic-control`); PLAN-55 (`chat`); PLAN-75 (`work-package-redistribution`), which merges this package into `epic-decomposition` (PLAN-53) and deletes its package file

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
