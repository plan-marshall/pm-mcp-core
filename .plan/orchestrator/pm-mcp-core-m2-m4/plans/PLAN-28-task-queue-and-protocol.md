# PLAN-28: Task queue and task protocol

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-28-task-queue-and-protocol.md` and is queued as one row file, `queue/PLAN-28.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Implement the work package `task-queue-and-protocol` of `doc/plans/task-queue-and-protocol.adoc`: task queue and task protocol. Serves: Roadmap Milestone 4. Where: pm-core package `core.queue`; pm-workflow package `workflow.task`. The package file is the brief; this spec adds what the orchestrator observed and the constraints of the epic.

## Deliverables

The bullets of `doc/plans/task-queue-and-protocol.adoc` § What, in its words (regenerated at 710da61, after the specification moved into this repository).

1. The task queue store: queue task records and task leases per worker generation; a generation is a worker's, issued with its job token by `worker-launch` (roadmap Milestone 4, worker generations and fencing), never a session's: the session generation record of `session-log-and-generations` is no part of the target and goes with `removed-concepts`.
2. The task protocol: offers, acknowledgement, leases, fencing, task outcomes and re-offers, and skill delivery per generation.
3. The queue task record: the queue of the machine scope at `<PM_MCP_BASE>/machine/queue/` under its own queue lock beside the per-project queue (`scope.type` `machine`; an entry's tasks in the workspace scope); the statuses `awaiting_answer` and `suspended` and the member `continuation` stored and read, the attempt end `suspended` among the ends; the behaviour of these states (the hold, the suspension and resume, the answer acknowledgement, the fallback to `asked`, the answer in the next task, the question bound) is `question-continuation`'s.
4. Pause: a task of a paused plan stays `queued` and is offered to no worker until the plan is started; a pause at once ends the attempt with `end: paused`, releases the lease, and queues the task again without counting against the re-offer budget.
5. Off track: the attempt end `off_track` re-offers the task within its budget; the member `signals[]` is stored with each entry the runtime appends, its signals and actions being `worker-activity`'s.
6. The compiled `worker-task` unit is content of the assembly; this package proves the protocol against a fixture unit.

**Done when** (the package's own acceptance): Exactly-once submit under every interleaving the specification names; a late call of a replaced generation is refused; a lease survives a restart; a task of a paused plan is offered to no worker, and a pause at once re-queues a leased task with `end: paused` without spending a re-offer, the task offered again once the plan is started; a machine-scope task is queued and leased in the machine's queue; a record with a `continuation`, a `suspended` attempt end, and `signals[]` entries round-trips.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WORK-2, PM-SKILL-4, PM-TOOL-1
- Specification: Model Work § Task Protocol; Store Schemas § Queue Task Record; MCP Tools § `pm_wait`
- Watch: Model Work; Store Schemas; MCP Tools

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in `pm-core/doc/developer/core-queue.adoc`, `pm-workflow/doc/developer/workflow-task.adoc` (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `pm-core/doc/adr/core-queue/`, `pm-workflow/doc/adr/workflow-task/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-10.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Specifications can be test input (found by PLAN-69, 2026-10-18).** A schema-parity test reads blocks of the `.adoc` specifications under `doc/specification/` (for example `WorkflowStatusSchemaParityTest` reads the JSON schema block after `== Workflow State Store` of `store-schemas/02-workflow-state-store.adoc`). Before the outline deletes or edits the implemented sections of a specification part, search the test tree for readers of that file, list each as a read file of the deliverable and state which blocks must stay byte-stable; a block a test reads is not deleted with the covered prose.
- **Checklist for `pm-workflow` code (found by PLAN-69, 2026-10-18).** (1) A refusal added for a workflow-DSL construct has exactly one owner, the parser or the verifier; every verifier refusal fixture is a tree the parser accepts, and fixture-count success criteria are written after that split. (2) A new pre-parsing layer in front of the lexer accepts at least what the lexer accepts (line endings, whitespace); add a CRLF twin fixture for every new line-oriented reader. (3) Where a printer and a reader must agree on a lexical rule, the printer calls the reader's predicate. (4) A completeness test over an enum derives the expected set from the enum at run time. (5) An exception to an invariant for one caller is scoped to that caller, with a refusing fixture from every other caller that reaches the check. (6) When a concept is generalised (start state to starting states), sweep every diagnostic message that names the old one.
- **Renumber the ADRs against `origin/main` before finalize and after every rebase (found by PLAN-14, 2026-10-18).** An ADR added to a module ADR directory (for example `pm-core/doc/adr/core-store/`) can take a number another plan took in the same directory; different slugs mean different file names, so git reports no conflict and the duplicate-number gate of the branch-cleanup step scans only `doc/adr`. List the touched ADR directories with `git ls-tree origin/main` and renumber the plan's ADRs to the next free numbers; PLAN-14 renumbered twice.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`); `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when, Trace and Depends on above are the package file's and its index row's own — regenerated from `doc/plans/task-queue-and-protocol.adoc` and `doc/plans/README.adoc` at 710da61 (branch `docs/specification-into-core`, pm-mcp-core #77), after the specification moved into this repository and the package was aligned with the changed specification; the claims below were checked before that
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/task-queue-and-protocol.adoc is unchanged since d1d58d2 (#77, no later commit touches it); its What, Trace and Done When equal the Deliverables, trace set and acceptance above
- OBSERVED: the deliverables, the trace starting set and the acceptance above are the package file's own — read at `doc/plans/task-queue-and-protocol.adoc` § What, Trace, Done When (origin/main at a8a4214, updated by cleanup on 2026-10-15)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/task-queue-and-protocol.adoc § What, Trace and Done When re-read at 238c823 equal the Deliverables, trace set and acceptance above
- OBSERVED: the index gives this package the status `open` and the dependencies `role-sets-and-model-caller`, `waits` (`session-log-and-generations` dropped by #70) — read at `doc/plans/README.adoc` § Packages
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: README.adoc row task-queue-and-protocol re-read at 238c823: open; depends on role-sets-and-model-caller, waits; matches the claim
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/` is no longer greenfield: it holds the session generations of PLAN-12 (#51) — `SessionGeneration`, `SessionGenerationStore`, `SessionGenerationCodec`, `GenerationId`, `GenerationCheck`, `GenerationCause`; the task queue joins them and reuses the fencing generation rather than defining its own — directory listing at fa58b0d on 2026-10-13 (re-grounded by cleanup)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: core/queue holds SessionGeneration, SessionGenerationStore, SessionGenerationCodec, GenerationId, GenerationCheck, GenerationCause and package-info
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/task/` exists and holds `package-info.java` today, so the package is greenfield apart from its package description — directory listing on 2026-10-09
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: workflow/task holds only package-info.java at 238c823
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line of the package file, not from a trace matrix — confirm/refute at the trace matrix of `traced-implementation` (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the Expected Surface mirrors the Where line of doc/plans/task-queue-and-protocol.adoc; listed paths exist or are declared new; packages the Where line names but the surface omitted were added by cleanup
- OBSERVED: each module that logs has one holder with its own test, which refuses an identifier used twice and one outside the range of its level (INFO 1-99, WARN 100-199, ERROR 200-299): `CoreLogMessages` (5 records), `RuntimeLogMessages` (60), `WorkflowLogMessages` (5); `PmMcpLogMessages` is deleted — read on main at 238c823 on 2026-10-16
  - verdict: contradicted | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: CoreLogMessages has 5 records, RuntimeLogMessages 60, WorkflowLogMessages 5 at 238c823 (claim said 3, 54, 3); one holder with its own test per module and PmMcpLogMessages gone still hold; claim rewritten
- HYPOTHESIS: the package adds INFO, WARN or ERROR messages; its module's log holder is deliberately NOT in the Expected Surface: a record is appended at the next free identifier and a collision is resolved on rebase, as the operator decided for the shared holder on 2026-10-09 and kept for the per-module holders on 2026-10-13 — confirm/refute the need at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: whether the package logs is decided at the trace matrix; no implementing code exists yet
- HYPOTHESIS: the package makes no HTTP call, so the two HTTP constraints bind only if the trace matrix shows one — confirm/refute at the specification sections of the trace starting set (verify-at-outline)
  - verdict: corroborated | checked_at: 238c82395f9637992612f7f0b2935f9f9d86225b | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: the specification sections of the trace starting set name no HTTP call of this package; pm-core and pm-workflow cannot depend on cui-http anyway
- Verify-first clause: before scoping, read the roadmap item this package serves for what the code already covers, list the open pull requests for one that names `task-queue-and-protocol`, and check in `doc/plans/README.adoc` that every dependency is `done`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: row task-queue-and-protocol is open in the index at 98ce971; open PRs (ci pr list): #71 (a dependabot bump) only, none names the package; doc/plans/task-queue-and-protocol.adoc is unchanged since 3323ce7; code claims of the spec were not re-read

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/` — production code
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/task/` — production code
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/queue/` — tests; exists (the session generation tests), extended
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/task/` — tests; new directory
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- HYPOTHESIS: `pm-core/doc/developer/core-queue.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-core/doc/adr/core-queue/` — ADRs of the package's design decisions; new directory if absent
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-task.adoc` — developer documentation of the package; created or extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-task/` — ADRs of the package's design decisions; new directory if absent
- OBSERVED: `doc/plans/task-queue-and-protocol.adoc` — the package file, deleted when the package is done
- OBSERVED: `doc/implementation-watch/model-work.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/implementation-watch/store-schemas.adoc` — watch document; the covered items deleted
- OBSERVED: `doc/specification/model-work/02-task-protocol.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc` — specification part; the implemented sections deleted

## Dependencies and Sequencing

- Depends on: PLAN-26 (`role-sets-and-model-caller`); PLAN-25 (`waits`)
- Overlaps with: PLAN-12 (`session-log-and-generations`) in `core.queue`; PLAN-33 (`session-as-worker`, retired) in `workflow.task`; PLAN-64 (`removed-concepts`) in `core.queue` (it removes the session generations; `GenerationId` stays only if this package reuses it); every plan through `doc/plans/README.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched — a change there is a pull request in that repository; and `plan-marshall-documentation`, where only a requirement correction or a change of `cross-cutting.adoc` or of a specification that lives there goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-28-task-queue-and-protocol.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
