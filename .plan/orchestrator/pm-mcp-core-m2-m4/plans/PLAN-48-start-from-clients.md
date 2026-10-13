# PLAN-48: Starting work from the clients, and the source directory

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-48-start-from-clients.md` and is queued as one row file, `queue/PLAN-48.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `start-from-clients` of `doc/plans/start-from-clients.adoc` (added to the index on 2026-10-12): starting work from the clients, and the source directory. Serves: Use Cases, UC-STA-1 to UC-STA-4, UC-STA-7 to UC-STA-9; starting work in both clients of `pm-mcp-clients`; no roadmap milestone yet. Where: pm-workflow (the bootstrap and source units, the end of the session-driven units, the static verifier); pm-core (the pending entry, the source directory and its manifest, `origin`, the epic header's sources); pm-runtime (the entry endpoints, the listing of files). It writes its specification changes into `plan-marshall-documentation` itself, first.

**Parked:** the index gives the package the status `blocked` — it waits on use cases to be agreed in `plan-marshall-documentation` and on the packages below, not on code alone. Re-ground this spec against the agreed use cases and the specification before emitting it; this spec was generated from the package file and has had no orchestrator review beyond that.

## Deliverables

The bullets of `doc/plans/start-from-clients.adoc` § What, in its words.

1. Specification first*: the package writes the changes All Interaction in the Operator's Clients, Starting from the Clients, The Source Directory, Documents as a Source, What Is on This Machine Is Trusted, Screened Once Stored, Issues by Number or URL, Asked When Needed, and A Claimed Lesson Ends with the Landing into `plan-marshall-documentation` as part of its own work, once UC-STA-1 to UC-STA-4 and UC-STA-7 to UC-STA-9 are agreed (Specification Changes for the Clients, Packages).
2. The operator's clients only*: the bootstraps of a plan and an epic run as units of operator waits that the clients drive; the session-driven units, the entry session, and the channel `session` of answers go, with the static verification rule that allowed links-only states in them.
3. Sources as a list*: `start.sources` and `start-epic.sources`, offering only the kinds that combine with those already listed (a pull request or the local changes only with descriptions and documents, never both); the description stored by the main workflow; the source units `source-documents`, `source-issue` (number or URL, always stored as its URL, the structured fields; another repository on the operator's grant, whose _no_ goes to `source-issue.failed`; without a credential for the forge a public issue is read and another fails naming the credential), and the existing ones of lesson, local changes, pull request, and epic row, each writing into the source directory.
4. Rate limits*: a fetch of an issue or a pull request that the forge refuses for its rate limit, with or without a credential, fails the source with the cause `rate_limited` and `resets_at` from the forge's answer; it is not retried by itself, and the operator tries again or adds a credential for the forge.
5. Documents, copy or move*: the machine key `sources.documents_mode` (`copy`, `move`, or `ask`, the default); with `ask` the operator wait `source-documents.mode` with _copy_ preselected, where _always_ stores `copy` or `move` as the machine's value; otherwise straight to `source-documents.store`. A move removes a file git tracks from git through the short landing `git.land-removal`, landed by the project's landing (`landing.pull-request` or `landing.main`); with `landing.files` the removal stays uncommitted in the working tree.
6. An entry never expires*: a pending entry has no time limit; it stays among the project's open entries, offered to continue in both clients, until it is started or abandoned, or deleted with the release of its project. The pending entry record's maximum age (24 hours, `PENDING_ENTRY_MAX_AGE` of `PeriodicSweep`) and the removal of older records by the project recovery and the periodic sweep go, in the specification and in the code.
7. The source directory*: created with the entry, moved into the plan or the epic's machine store at creation; the manifest; `origin` naming the sources; the epic header listing them; `request.md` composed from them; `epic.decompose` reading them.
8. Screened once stored*: fetch, level-1 validator, store, screener over the stored copy, the verdict in the manifest; nothing but the screener reads an external source before a clean verdict or the operator's acceptance; the operator's text and the documents on the machine are never screened.
9. Fail closed*: a screener that fails, or a role `screener` with no qualified configuration, leaves the source unscreened with the cause in the operator wait `source-<kind>.unscreened` (`source-issue.unscreened`, `source-pr.unscreened`): try again, remove it, or accept it explicitly as untrusted, recorded with its reason like an accepted flagged source; a failure is never shown as `suspicious`.
10. The other sources* (UC-STA-7 to UC-STA-9): pull request, lesson, and local changes as sub-workflows storing into the source directory; an issue's and a pull request's URL stored. A pull request's changed files are stored as its head and marked untrusted unless the operator authored it, never read by a model as instructions, the plan's own gates applying to them; its title and body screened as an issue's. A lesson: `source-lesson.resolve` with the premise check; a lesson claimed meanwhile, retired, or in a damaged store back to `source-lesson.choose` with the cause; a premise that no longer holds to the operator wait `source-lesson.premise` with the evidence, where _use it anyway_ stores the lesson with the evidence for the plan, _retire_ retires it in the lesson store, and _remove_ leaves it as it is, both adding nothing. The local changes: `source-local-changes.confirm` with _not now_ back to `start.sources`, a checkout without changes failing the source with that cause; the plan takes the land lane and lands them by the project's landing.
11. The start's end*: `start.ready` refuses a landing that does not fit the sources or the project, with the reason: `landing.files` for a plan from local changes, and a machine default that does not fit the project (the landing variants are `machine-onboarding-unit`'s); `start.create` re-checks what creation needs and returns to `start.ready` with the cause where something changed meanwhile.
12. A claimed lesson ends with the landing*: the lesson of a source is claimed at the plan's creation and retired when the plan lands its changes (the merge of `landing.pull-request`, the push of `landing.main`; verdict `applied`, `by` the plan), released when it ends without landing them (handed back with `landing.files`, cancelled, abandoned, released, aborted, deleted), the lesson staying `active`.
13. An epic's sources in its ledger* (`.marshall/epics/<epic_id>/sources/`, committed and landed), so that adoption and every decomposition read them.
14. Chat while starting*: `start.sources` and `start-epic.sources` leave room for the choice Chat that `chat` adds on top of this package, and the source directory keeps its transcript (moved at the creation, deleted with an abandoned entry, never a source the plan reads); the chat itself and its one change, the description through `start.store-description`, are that package's.
15. Entries addressed like plans and epics*: created under their project (`POST /api/v1/projects/{project_id}/entries`, the kind `plan` or `epic`), addressed at the top level by id (`/api/v1/entries/{entry_id}`, its events at `/api/v1/entries/{entry_id}/events`); `POST /api/v1/entries` goes.
16. Loopback only*: the listing of files, a move, and documents outside the checkout only from the terminal client and the loopback listener.
17. Asked when needed*: posture, base branch, and outline review leave the start and become operator waits where they are first needed, skipped where the project or the machine sets them. `parallelization_scope` becomes the project key `epic.parallelization_scope` (machine default 1), which a new epic takes and which is never asked; it is raised through _Next plan_ (`plan-and-epic-control`) or the project's settings.
18. Where `entity-addressing`, `plan-instance`, or `project-units-and-invariants` have already implemented the session-driven bootstrap, this package restructures them.

**Done when** (the package's own acceptance): The specification of the changes is merged in `plan-marshall-documentation`; no unit is driven by a session and the static verifier refuses one; a test per source kind stores it in the source directory with its manifest entry; a plan and an epic start from several sources and keep them after creation; no model but the screener reads an external source before its verdict, shown by a test that fails the screening and one whose screener fails or has no qualified configuration, leaving the source unscreened until tried again, removed, or accepted as untrusted; a pull request's changed files are never read as instructions; a rate-limited fetch of an issue and of a pull request fails with `rate_limited` and `resets_at` and is not retried; a pending entry older than a day is still offered and no sweep removes it; each branch of a lesson, unavailable and a premise that no longer holds, ends as specified; each option of the start is asked where it is first needed and not at all when the project or the machine sets it, and `parallelization_scope` never; `start.ready` refuses `landing.files` for local changes; documents are asked copy or move only with `ask`; a claimed lesson is retired on a merge and on a push to the base branch and released on every other end.

**Trace starting set** (documents live in `../plan-marshall-documentation`):

* Use cases: UC-STA-1 to UC-STA-4, UC-STA-7 to UC-STA-9 and their changes, among them Screened Once Stored, What Is on This Machine Is Trusted, and A Claimed Lesson Ends with the Landing; Events per Scope (the entry's address)
* Specification: State Catalogue § Bootstrap & Lessons; Phase Workflows § Plan Source Contracts; Workflow DSL § Session-Driven Units; Store Schemas § Pending Entry Record; Timeouts § Pending Entry Record Maximum Age; Runtime Model § Periodic Sweep; Model Work § Two-Level Guard; § Epic Workflow; PM-SEC-1; Store Schemas § Lesson Record

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

- OBSERVED: the package file and its index row (`blocked`; depends on `operator-wait-and-machine-scope`, `entity-addressing`, `machine-onboarding-unit`, `project-onboarding-unit`; UC-STA-1 to UC-STA-4 and UC-STA-7 to UC-STA-9 agreed (no work package)) — read at `doc/plans/start-from-clients.adoc` and `doc/plans/README.adoc` (main at 557ee81)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/unit/` — inferred from the `Where` line
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/` — inferred from the `Where` line
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/` — inferred from the `Where` line
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`); PLAN-21 (`entity-addressing`); PLAN-46 (`machine-onboarding-unit`); PLAN-45 (`project-onboarding-unit`); UC-STA-1 to UC-STA-4 and UC-STA-7 to UC-STA-9 agreed (no work package)

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-48-start-from-clients.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
