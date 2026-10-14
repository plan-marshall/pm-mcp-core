# PLAN-53: The decomposition of an epic, reviewed by the operator

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-53-epic-decomposition.md` and is queued as one row file, `queue/PLAN-53.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `epic-decomposition` of `doc/plans/epic-decomposition.adoc` (added to the index on 2026-10-12): the decomposition of an epic, reviewed by the operator. Serves: Use Cases, UC-STA-6; the decomposition in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-workflow (the sub-workflow `epic.decomposition`, called by `epic.decompose` and `epic.recut`); pm-core (the draft apart from the ledger until agreed). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/epic-decomposition.adoc` § What, in its words.

1. Specification first*: the package writes the change The Decomposition Is Reviewed into `plan-marshall-documentation` as part of its own work, once UC-STA-6 is agreed (Specification Changes for the Clients, Packages).
2. The draft of the role `decompose` held apart from the ledger; the server's checks over it (disjoint surfaces of plans that may run together, no dependency cycle, a workstream per plan); the operator wait `decomposition.review` with keep, edit, drop, add, chat (the sub-workflow `chat` with the draft as its scope), and draft again with an instruction; the commit only after the operator agrees and every check passes.
3. Edit and add*: `decomposition.instruct` (the operator's words for the plan), `decomposition.rewrite` (the role drafts or rewrites the plan), `decomposition.read-back` (old against new: accept to `decomposition.check`, instruct again to `decomposition.instruct`, discard back to `decomposition.review`); every model change through that read-back or the chat's.
4. How many plans run at once* is no part of the decomposition: it is the project's `epic.parallelization_scope` (machine default 1), never asked at the start; the operator raises it through _Next plan_ (`epic.raise-scope`, `plan-and-epic-control`) or the project's settings.
5. A later run (decompose again, stale rows) over staged rows only, each change against the row it replaces; no plan of a new epic starts before its first decomposition is committed. A recut after a landing that only re-orders rows or amends a staged row's surface or prerequisites is applied without the review once the checks pass (`epic-auto`); one that adds, drops, or re-scopes a plan comes to `decomposition.review`.
6. Where `epic.decompose` and `epic.commit-decomposition` have already been implemented without the review, this package restructures them.

**Done when** (the package's own acceptance): The specification is merged in `plan-marshall-documentation`; a decomposition reaches the ledger only through the operator's agreement with every check passing, the first one and every later run that adds, drops, or re-scopes a plan; only a recut after a landing that re-orders staged rows or amends a staged row's surface or prerequisites is applied without it, once the checks pass (`epic-auto`); each operator act of the review and each branch of `decomposition.read-back` is tested; no state of the start or the decomposition asks `parallelization_scope`; a later run changes only staged rows and reports each change.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use case: UC-STA-6 and its change The Decomposition Is Reviewed
* Specification: Model Work § Epic Workflow; § Staging-Time Surface Disjointness; State Catalogue § Epic

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
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of `plan-marshall-documentation`, under the holder's section, in the documentation pull request.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the package file and its index row (`blocked`; depends on `operator-wait-and-machine-scope`, `chat`; UC-STA-6 agreed (no work package)) — read at `doc/plans/epic-decomposition.adoc` and `doc/plans/README.adoc` (main at 557ee81)
  - verdict: corroborated | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/epic-decomposition.adoc exists at fa58b0d (presence checked, content not re-read)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: fa58b0dc0424f17f90fb9f938ffed3f77d5f4369 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: verify-first clause is executed by the plan at refine

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-unit.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-unit/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended (added by cleanup 2026-10-13: the Standing Constraints require it)
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent (added by cleanup 2026-10-13)

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); PLAN-55 (`chat`); UC-STA-6 agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-53-epic-decomposition.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
