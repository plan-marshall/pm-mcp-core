# PLAN-14: Operator and question stores of the scopes, per-project machine store and project resolution

epic: pm-mcp-core-m2-m4
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-14-operator-and-question-stores.md` and is queued as one row file, `queue/PLAN-14.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `operator-and-question-stores` of `doc/plans/operator-and-question-stores.adoc`: operator and question stores of the scopes, the per-project machine store and project resolution. The package is the merge of the former `operator-and-question-stores` and `project-machine-store` that PLAN-75 (#85) made on 2026-10-17: both are in `pm-core`, five declared files were shared, and the sequencing gate ordered them anyway. Serves: Roadmap Milestone 3. Where: pm-core packages `core.store`, `core.config`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/operator-and-question-stores.adoc` § What at `c281f6c`, in its words; the first four are the former `operator-and-question-stores`, the last two the former `project-machine-store`. The spec of the absorbed row PLAN-15 is superseded by this one.

1. The append-only stores of waivers and waiver consumptions of a plan (`waivers.jsonl`, `waiver-consumptions.jsonl`), and of question records of the plan, epic, and workspace scopes and of an entry (`pending/<entry_id>/questions.jsonl`, moved into the new scope at its creation).
2. Waiver requests are no store of their own: they are read and written as `waiver_requests[]` of the scope's `mcp/status.json` through the workflow state store, with the closed outcomes `waived`, `raised`, `declined`, and `cancelled` and the `answered` act; no request has a time limit or an `expired` outcome.
3. The question record: a decision is a set of questions named by its first `question_id`; the events `asked`, `revised`, `adjudicated`, `delivered`, `answered`, `withdrawn`, and `cancelled`; the `decision` member on the first question of the set; a `revised` line per question of the set for a change, each line naming its revision; the channels `web`, `cli`, and `quorum`; no deadline and no expiry member. The store holds the record's own invariants: a question is closed by exactly one `answered`, `withdrawn`, or `cancelled` line, a further answer is refused `already_answered`, and an answer to an older revision `conflict`; the validation of an answer's content against its part is the engine's.
4. The module of the question store is settled in the trace matrix: this package names pm-core `core.store`, the Javadoc of the engine port `QuestionRecordReader` names pm-runtime, and the module structure places the question records in pm-workflow `workflow.question`.
5. The per-project machine store below `<PM_MCP_BASE>/projects/<project_id>/` and the state-free repository directory `.marshall/local/`.
6. The project resolution rule every request of a client that names a checkout path rests on.

**Done when** (the package's own acceptance): Round-trip and append-only tests per store; a changed record is a new line and the last readable line of its key is current; a revision appends a `revised` line for every question of its set; a second closing line is refused `already_answered` and an answer to an older revision `conflict`; an entry's question file is moved into the new scope at its creation and read there; a waiver request round-trips through `waiver_requests[]` with each outcome. Resolution fixtures: a worktree, a nested checkout, a moved checkout and an unenrolled directory each resolve as specified; nothing with state is written below the repository.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-IMPL-2, PM-IMPL-7, PM-ARCH-1, PM-SEC-2
- Specification: Store Schemas § Plan Instance, Waivers & Questions; Filesystem Layout § Per-Project Machine Store; § Project Resolution; § Repository & Workspace
- Watch: Store Schemas; Filesystem Layout

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in `pm-core/doc/developer/core-store.adoc` and `pm-core/doc/developer/core-config.adoc` (create or extend them: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `pm-core/doc/adr/core-store/` (and `pm-core/doc/adr/core-config/` for `core.config`) as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/operator-and-question-stores.adoc` § What, Trace, Done When on `origin/main` at `c281f6c` (the file PLAN-75 #85 rewrote: six bullets, `Where` naming `core.store` and `core.config`)
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: doc/plans/operator-and-question-stores.adoc re-read at c281f6c: What holds six bullets, Where names core.store and core.config, Trace and Done When as in the Deliverables above
- OBSERVED: the index gives this package the status `open` and the dependencies nothing — read at `doc/plans/README.adoc` § Packages on `origin/main` at `c281f6c`; the rows of `project-machine-store` and of every package that named it are gone
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: README.adoc row operator-and-question-stores re-read at c281f6c: open, depends on nothing; no row project-machine-store remains
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/` exists and holds 58 files, the store substrate of PLAN-06 and its successors, among them `PathConfinement`, `WorkflowStatus`, `WorkflowStatusCodec` and `WorkflowStatusStore`; none of the new types the Expected Surface names (`Waiver*`, `Question*`, `ProjectResolver`, `ProjectStore*`) exists — listing of `origin/main` at `c281f6c`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: ls of pm-core core/store at c281f6c: 58 files incl. PathConfinement, WorkflowStatus, WorkflowStatusCodec, WorkflowStatusStore; no Waiver*, Question*, ProjectResolver or ProjectStore* file
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/config/` holds `package-info.java` and nothing else, so `core.config` is greenfield apart from its package description — `git ls-tree` of `c281f6c`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git ls-tree of c281f6c under core/config: package-info.java only
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file and the class-level surfaces of the two former specs, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan; surface merged from the two former specs, each checked against the code at 238c823
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (60), `WorkflowLogMessages` (5) — `static final LogRecord` counted on `origin/main` at `c281f6c`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: static final LogRecord counted at c281f6c: CoreLogMessages 5, RuntimeLogMessages 60, WorkflowLogMessages 5
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis: whether the package logs is decided at the trace matrix; no implementing code exists yet
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the trace sections name no HTTP call (checked at 238c823 for both former packages) and are unchanged in substance at c281f6c (claim 8); pm-core and pm-workflow cannot depend on cui-http anyway
- OBSERVED: the specification sections of the trace starting set did not change in substance since the specs of both former packages were last grounded at `238c823`: `store-schemas/14-plan-instance-waivers-questions.adoc` is untouched, `filesystem-layout/03-project-store.adoc` and `02-machine-runtime.adoc` changed only the label of links into the credentials specification (5 lines each way) — `git diff 238c823..c281f6c`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git diff 238c823..c281f6c: store-schemas/14 untouched; filesystem-layout/03 and /02 changed 5 lines each, word diff shows only the link label (CLI, Coordination & Security -> Credentials & Enrolment)
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `operator-and-question-stores`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: index row open and not blocked at c281f6c; open PRs 71, 86 and 87 (ci pr view): none names operator-and-question-stores or project-machine-store

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatus.java` — Deliverable 2, `waiver_requests[]` as a typed member (shared hot file; the other reading keeps the raw member and leaves it unchanged — ask the operator at refine); class-level surface drafted by cleanup on 2026-10-17, replaces the directory entry
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatusCodec.java` — Deliverable 2 (shared hot file)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatusStore.java` — Deliverable 2 (shared hot file)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryStore.java` — Deliverable 1, the entry's question file moved into the new scope (shared with `start-from-clients`, which owns the directory form `pending/<entry_id>/`)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/package-info.java` — Deliverable 1, package description
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/spi/QuestionRecordReader.java` — Deliverable 4, the Javadoc that names the module of the question store
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WaiverRecord.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WaiverRecordCodec.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WaiverLog.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WaiverConsumptionRecord.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WaiverConsumptionCodec.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WaiverConsumptionLog.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WaiverRequest.java` — Deliverable 2, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/WaiverRequestOutcome.java` — Deliverable 2, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/QuestionRecord.java` — Deliverable 3, new (if Deliverable 4 settles on `pm-workflow` `workflow.question`, every `Question*` file moves there)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/QuestionRecordCodec.java` — Deliverable 3, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/QuestionLog.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/QuestionRefusal.java` — Deliverable 3, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/QuestionEvent.java` — Deliverable 3, new
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/QuestionChannel.java` — Deliverable 3, new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusCodecTest.java` — Deliverable 2 (shared hot file)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusStoreTest.java` — Deliverable 2 (shared hot file)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java` — Deliverable 2 (shared hot file)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryStoreTest.java` — Deliverable 1
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/WaiverRecordCodecTest.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/WaiverSchemaParityTest.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/WaiverLogTest.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/WaiverConsumptionLogTest.java` — Deliverable 1, new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/WaiverRequestTest.java` — Deliverable 2, new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/QuestionRecordCodecTest.java` — Deliverable 3, new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/QuestionSchemaParityTest.java` — Deliverable 3, new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/QuestionLogTest.java` — Deliverable 3, new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/QuestionLogInvariantsTest.java` — Deliverable 3, new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/EntryQuestionMoveTest.java` — Deliverable 1, new
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/core-store.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-store/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/operator-and-question-stores.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/store-schemas.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc` — specification part; the implemented sections deleted
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/PathConfinement.java` — Deliverable 2, the linked-worktree recognition is private: the resolver widens it or duplicates it (shared hot file; PLAN-03 worked on it); class-level surface drafted by cleanup on 2026-10-17, replaces the directory entries (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/config/package-info.java` — Deliverable 1, package description of the first class in `core.config` (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/ProjectResolver.java` — Deliverable 2, new (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/ProjectResolution.java` — Deliverable 2, new (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/ProjectStoreIdentity.java` — Deliverable 1, new (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/ProjectStoreIdentityCodec.java` — Deliverable 1, new (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/ProjectStore.java` — Deliverable 1, new (the layout of the machine store directories may live in `core.config`: ask the operator at refine) (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ProjectResolverTest.java` — Deliverable 2, new (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ProjectResolverFixtures.java` — Deliverable 2, new (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ProjectStoreIdentityCodecTest.java` — Deliverable 1, new (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ProjectStoreIdentitySchemaParityTest.java` — Deliverable 1, new (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ProjectStoreTest.java` — Deliverable 1, new (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ProjectStoreNoStateInRepositoryTest.java` — Deliverable 1, new (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/LinkedWorktreeConfinementTest.java` — Deliverable 2 (its fixtures write linked worktrees; shared hot file) (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/PathConfinementTest.java` — Deliverable 2 (shared hot file) (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/doc/developer/core-config.adoc` — developer documentation of the package; created or extended (from the former `project-machine-store`)
- HYPOTHESIS: `pm-core/doc/adr/core-config/` — ADRs of the package's design decisions; new directory if absent (from the former `project-machine-store`)
- OBSERVED: `doc/implementation-watch/filesystem-layout.adoc` — watch document; the covered items deleted (from the former `project-machine-store`)
- OBSERVED: `doc/specification/filesystem-layout/01-repository-workspace.adoc` — specification part; the implemented sections deleted (from the former `project-machine-store`)
- OBSERVED: `doc/specification/filesystem-layout/03-project-store.adoc` — specification part; the implemented sections deleted (from the former `project-machine-store`)

## Dependencies and Sequencing

- Depends on: nothing — PLAN-75 (`work-package-redistribution`), which merged `project-machine-store` into this package, shipped as #85 (`c281f6c`) and the index row depends on nothing
- Overlaps with: computed by `corpus cross-check` at emit time; PLAN-01, 03, 06, 09, 11, 12, 13 (shipped) in `core.store`; PLAN-20 (`job-runner`) and PLAN-69 (`unit-frontmatter-and-types`), running, below; PLAN-31 (`slots-usage-and-budgets`) in `core.store`; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Overlaps accepted by the operator at the emit of 2026-10-17 (overlap prompt; `corpus cross-check` at emit: 5 declared files shared with PLAN-20 and 6 with PLAN-69, the README row counting as one of each, so 4 and 5 besides it; PLAN-74 shares the README row only): with PLAN-20 the `core-store` ADR directory, the guide `pm-core/doc/developer/core-store.adoc` and `core.store` `package-info.java`; with PLAN-69 `WorkflowStatus`, `WorkflowStatusCodec`, `WorkflowStatusCodecTest`, the ADR directory and the guide. PLAN-20 and PLAN-69 each already claim the ADR number 029 in `pm-core/doc/adr/core-store/` (PLAN-20 also 030): take the next free number of the directory at the time of the commit and again after a rebase, and extend the guide in a section of its own. Deliverable 2 widens `WorkflowStatus` for `waiver_requests[]` only if the operator chooses the typed member at refine; the other reading keeps the member raw and leaves the three shared files unchanged, which removes most of the overlap with PLAN-69. #83 (spend-removal) changed `WorkflowStatus` on `main`: read it at the branch point, not from this spec.
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-14-operator-and-question-stores.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
