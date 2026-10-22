# PLAN-102: Name a package in plan-marshall-mcp for the native build of the daemon

epic: pm-mcp-core-m2-m4
workstream: WS-04

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-102-native-build-package-in-plan-marshall-mcp.md` and is queued as one row file, `queue/PLAN-102.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

PLAN-84 (#135, `0bf64f7`) reported the native build of `pm-mcpd` against the deployed `SNAPSHOT` of `pm-mcp-core` as not run, because no plan owns it: it is the only check of the `kill` downcall descriptor with `options.captureCallState` and of the Keychain and Secret Service paths, and no package of `plan-marshall-mcp` names it. The operator decided on 2026-10-22 that a plan in `plan-marshall-mcp` owns it. **This plan works in the repository `plan-marshall-mcp`, not here**: it writes that repository's package file and index row, and repairs the cross-repository links of its index that point at packages done here or in `pm-mcp-clients`. Documents only; the native build itself is the package's work.

## Deliverables

1. **The package `native-build-verification`**: add `doc/plans/native-build-verification.adoc` to `plan-marshall-mcp` with exactly the text of the section *Package File* below, and its row to the table of `doc/plans/README.adoc` as the section *Index Row* gives it (after the last row). Correct a link or an anchor that does not resolve and say so in the pull request body.
2. **The dangling cross-repository links of that index.** Today the index links `job-runner`, `operator-and-question-stores`, `project-machine-store`, `stalled-scope-detection` of `pm-mcp-core` (all done or absorbed: a done package leaves the table and its file is deleted) and `api-contract`, `integration-layers`, `operator-enrolment-verbs`, `operator-harness-and-roles-verbs` of `pm-mcp-clients` (moved to `doc/plans/implemented/`). Re-read the rows against `origin/main` of both repositories, and drop each done package from the _Depends on_ cell that names it, as PLAN-93 did here; a dependency that is not done stays. Say in the pull request body which links were dropped.
3. **The links check, recorded**: the documentation repository's `trace.py` over both trees from the worktree (the absolute-path command of the Standing Constraints), `BROKEN LINKS 0`.

**Done when:** `doc/plans/native-build-verification.adoc` and its row exist in `plan-marshall-mcp`, every link of the index resolves on `origin/main` of the three sibling repositories, the links check reports `BROKEN LINKS 0` and the run is recorded in the pull request body. The pull request touches only `doc/plans/` of `plan-marshall-mcp`.

**Trace starting set:** the `pm-mcp-core` `CLAUDE.md` ("Native-image metadata … is verified by the native build of `pm-mcpd` in plan-marshall-mcp against the deployed `SNAPSHOT`"); the landing of PLAN-84 (`landings/PLAN-84.md`); the Open Defects and Watches of the epic.

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Java 25 is the baseline.** Write for Java 25 and use its language and library features where they fit; no fallback for an older release. The release comes from the parent POM and is not overridden here.
- **HTTP calls go through `cui-http`.** No direct `java.net.http` or other HTTP client. The build forbids `cui-http` in `pm-core` and `pm-workflow`, so an HTTP call belongs in `pm-runtime` or a provider module, behind an interface the foundation declares.
- **HTTP tests use `cui-test-mockwebserver-junit5`.** Every test that involves HTTP runs against it; no hand-made fake server. The operator's statement is the consent `CLAUDE.md` requires for adding it as a test-scope dependency.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package, as reworded by #19): the usual plan-marshall flow — a branch, the commits of the plan, a pull request opened at finalize. No draft pull request claims the package; before starting, list the open pull requests for one that names it. The pull request removes the package's row from the index and deletes its package file within its commits, and drops the package from the _Depends on_ column of every row that names it. Build the trace matrix of `traced-implementation` before code, deliver the remainder the roadmap item leaves, and finish with `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` green.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository: `doc/specification/`, `doc/implementation-watch/`, `doc/Traceability.adoc`, and the log message reference `doc/LogMessages.adoc`. This plan's one pull request carries the code, the tests, Javadoc and `package-info.java`, the developer documentation and ADRs, the deletion of the implemented specification sections and watch items, a correction of a specification statement it proves wrong, and the `Implementation:`, `Verified by:` and `Documentation:` lines of the requirements it implements in `doc/Traceability.adoc`. It opens a pull request in `plan-marshall-documentation` only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there; merging that one is the operator's. The concept and user documentation of the slice and the roadmap check-off are written there at the milestone exit: list them in the pull request body as **Owed to the milestone exit**.
- **Module documentation (operator decision 2026-10-11).** Documentation of the code lives in its module, in this plan's pull request, not in `plan-marshall-documentation`: the developer documentation of each package this plan changes in the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`) (create or extend it: what the package is for, its main types and how they work together, how to use and extend it, invariants and failure modes, links to the specification sections it implements), and one ADR per design decision taken — every operator decision and every choice between alternatives — in `{module}/doc/adr/{package}/` as `NNN-{slug}.adoc`, the next free number of that directory, with the sections of `doc/adr/template.adoc` (Status, Date, Context, Decision, Alternatives considered, Consequences, Source) or, before that template exists, those same sections. A decision binding the whole repository goes to `doc/adr/`. The module's `README.adoc` gets one line only when this plan adds a new package; it is otherwise left alone. Layout: workstream WS-04.
- **Settled design: a refused project path is a value.** The confinement check that landed with #19 lives in `pm-core` package `core.store` (`PathConfinement`, `WorkspaceRoots`, `ConfinementOutcome`) and `pm-runtime` package `runtime.workspace` (`WorkspaceConfinement`). A refusal is returned as `ConfinementOutcome` carrying `path_traversal_rejected`; no checked exception type exists or is added. No consumer is wired to it yet (`pm-provider-git` included): a package that performs a file or repository operation for a project adopts the check itself.
- **Do not leave a design choice open that changes the file set.** Where two readings of a deliverable differ in the files they create, ask the operator at refine instead of deferring the choice to implementation.
- **Files shared by every plan.** In `doc/plans/README.adoc` remove only this package's own row (and its name from the _Depends on_ column); in `doc/Traceability.adoc` add only the sections of the requirements this package implements. Neither is a declared surface.
- **Specifications can be test input (found by PLAN-69, 2026-10-18).** A schema-parity test reads blocks of the `.adoc` specifications under `doc/specification/` (for example `WorkflowStatusSchemaParityTest` reads the JSON schema block after `== Workflow State Store` of `store-schemas/02-workflow-state-store.adoc`). Before the outline deletes or edits the implemented sections of a specification part, search the test tree for readers of that file, list each as a read file of the deliverable and state which blocks must stay byte-stable; a block a test reads is not deleted with the covered prose.
- **Checklist for `pm-workflow` code (found by PLAN-69, 2026-10-18).** (1) A refusal added for a workflow-DSL construct has exactly one owner, the parser or the verifier; every verifier refusal fixture is a tree the parser accepts, and fixture-count success criteria are written after that split. (2) A new pre-parsing layer in front of the lexer accepts at least what the lexer accepts (line endings, whitespace); add a CRLF twin fixture for every new line-oriented reader. (3) Where a printer and a reader must agree on a lexical rule, the printer calls the reader's predicate. (4) A completeness test over an enum derives the expected set from the enum at run time. (5) An exception to an invariant for one caller is scoped to that caller, with a refusing fixture from every other caller that reaches the check. (6) When a concept is generalised (start state to starting states), sweep every diagnostic message that names the old one.
- **Checklist for durable multi-step protocols in `pm-runtime` and `core.store` (found by PLAN-74, 2026-10-18; four review rounds each found another unguarded transition of the credential-store move).** (1) A resume decides from the pair (record phase, state actually recorded), never from the phase alone, and a guard added to one phase branch gets its symmetric guard on every other branch; enumerate (phase x recorded state x writer) with a test per cell before the first review. (2) While a durable in-progress record exists, every other writer of the state it switches is refused under the same lock, and an authorization is verified against durable state, never asserted by the caller. (3) The record that drives recovery is deleted last, after every side effect (audit event, clean-up after a failure) is durable. (4) A decision with side effects (which store, which key) is decided once and persisted, never re-derived per call from a probe that can flap. (5) A copy-then-switch move has a test with a pre-populated target (an overlapping key and a target-only key) and a failure injected after the copy; the pre-existing-target hazard is checked for every kind of object the protocol copies (entries, integrity key, sidecars) in the same task. (6) A store's temp-file namespace is disjoint from its legal entry-name namespace. (7) A fix of a state-machine transition lists the specification section, the ADR, the Javadoc and the test that state it among its affected files and updates them in the same commit; ADR and test are checked against the specification text, not against the code they document. (8) The invariants of a multi-write protocol are stated per crash window, not per phase. (9) A test that asserts a file location derives the expected path from the production layout independently, never from the object under test. (10) A native binding is loaded once; only what can change at runtime (a lock state) is probed per operation.
- **Renumber the ADRs against `origin/main` before finalize and after every rebase (found by PLAN-14, 2026-10-18).** An ADR added to a module ADR directory (for example `pm-core/doc/adr/core-store/`) can take a number another plan took in the same directory; different slugs mean different file names, so git reports no conflict and the duplicate-number gate of the branch-cleanup step scans only `doc/adr`. List the touched ADR directories with `git ls-tree origin/main` and renumber the plan's ADRs to the next free numbers; PLAN-14 renumbered twice.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: none: documents only. A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.
- **Sibling checkouts from the plan worktree (found by PLAN-72, 2026-10-17).** The plan runs in `.plan/local/worktrees/{plan}/`, three directories deeper than the main checkout, so `../plan-marshall-documentation` (as `CLAUDE.md` and the links of the package file write it) does not resolve there. Run the links check with the absolute paths of the sibling checkout, `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py /home/oliver/git/plan-marshall-documentation/doc .`, and record the run so the functional review finds it.

## Package File

````asciidoc
= The native build of the daemon against the deployed engine
:nofooter:

link:README.adoc[Work packages of `plan-marshall-mcp`] » `native-build-verification`

[horizontal]
Serves:: link:../../../plan-marshall-documentation/doc/roadmap.adoc[Roadmap] Milestone 4 (the job runtime and the credential stores of `pm-mcp-core` run in the daemon)
Where:: pm-mcp-server (the native build of `pm-mcpd`), pm-e2e (the native layout)
Status and dependencies:: in the link:README.adoc#packages[index], the only place that states them

== What

* *Run it*: `verify -Pnative,integration-tests` with `GRAALVM_HOME` and `JAVA_HOME` on a GraalVM 25 installation, against the `SNAPSHOT` of `pm-mcp-core` that its merge deployed, and against the client binaries of `pm-mcp-clients` (the native layout of `pm-e2e` needs them, otherwise it is skipped and the build output says so).
* *The native-image metadata of the engine*: `pm-mcp-core` modules carry their metadata (`META-INF/native-image/de.planmarshall/<name>/`) and name this build as its check; the build proves that it resolves every class of the modules the daemon uses.
* *The `kill` downcall*: the native check of the descriptor with `options.captureCallState` that `pm-runtime` (`ProcessGroupSignals`, `KillBinding`) binds through the foreign function interface, by signalling a real process group in a native test; it is the only check of that descriptor.
* *The credential paths*: the Keychain downcalls (macOS) and the Secret Service client (Linux) of `pm-runtime` load in the native binary and complete a round trip where the host allows it, and are skipped with their reason where it does not.
* *The report*: each check is recorded `passed`, `failed`, or `skipped` with its reason, with the revision of the `SNAPSHOT` it ran against; a failure is a pull request in `pm-mcp-core`, not a workaround here.

== Trace

Requirements, specification sections, and watch documents stay in `plan-marshall-documentation` (those of the engine in `pm-mcp-core`) and are only referenced here. They are the starting set; the trace matrix of `traced-implementation` is built from them before code (link:README.adoc#how[How to work a package]).

* Requirements: link:../../../plan-marshall-documentation/doc/requirements/01-architecture.adoc#PM-ARCH-3[PM-ARCH-3]
* Specification: link:../../../pm-mcp-core/doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc[Job Runtime § Process Termination & Cancellation Protocol]; link:../../../pm-mcp-core/doc/specification/credentials-and-enrolment/01-provider-credentials.adoc[Credentials & Enrolment Specification]
* Watch: link:../../../pm-mcp-core/doc/implementation-watch/job-runtime.adoc[Job Runtime]

== Done When

The native build of `pm-mcpd` ran against the named `SNAPSHOT` and its report lists the four checks above; every `failed` check has a pull request in `pm-mcp-core`; the epic of `pm-mcp-core` has the report.
````

## Index Row

````asciidoc
|link:native-build-verification.adoc[`native-build-verification`]
|The native build of the daemon against the deployed engine: the metadata of the engine modules, the `kill` downcall, the Keychain and Secret Service paths
|`open`
|nothing
|Milestone 4
````

## Claim Labels

- OBSERVED: no package of `plan-marshall-mcp` names the native build as a check of the engine — `git grep -i` of `doc/plans` at `origin/main` for `captureCallState`, `downcall` and `native build of` finds nothing; `integration-layers` mentions `verify -Pnative,integration-tests` only as its local test command
  - verdict: corroborated | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git grep -n -i -e captureCallState -e downcall -e 'native build of' origin/main -- doc/plans in plan-marshall-mcp (origin/main 38bd66d): no hit; integration-layers.adoc names the profile in its Done When only
- OBSERVED: the `pm-mcp-core` `CLAUDE.md` names the native build of `pm-mcpd` in `plan-marshall-mcp` as the check of native-image metadata, and `plan-marshall-mcp` `CLAUDE.md` gives the command `verify -Pnative,integration-tests` with `GRAALVM_HOME` and `JAVA_HOME` — read at `9732401` and at `origin/main` `38bd66d`
  - verdict: corroborated | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-mcp-core CLAUDE.md Build Rules at 9732401: 'a change to it is verified by the native build of pm-mcpd in plan-marshall-mcp against the deployed SNAPSHOT'; plan-marshall-mcp CLAUDE.md line 65 gives the command
- OBSERVED: the index of `plan-marshall-mcp` links packages that no longer exist — `pm-mcp-core`: `job-runner`, `operator-and-question-stores`, `project-machine-store`, `stalled-scope-detection` (no file under `doc/plans/` at `9732401`); `pm-mcp-clients`: `api-contract`, `integration-layers`, `operator-enrolment-verbs`, `operator-harness-and-roles-verbs` (moved to `doc/plans/implemented/`) — `doc/plans/README.adoc` at `origin/main` `38bd66d`; the local checkout of `pm-mcp-clients` may lag, so the plan re-reads `origin/main`
  - verdict: corroborated | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: links extracted from plan-marshall-mcp doc/plans/README.adoc at origin/main 38bd66d; os.path.exists on pm-mcp-core doc/plans at 9732401: ['job-runner', 'operator-and-question-stores', 'project-machine-store', 'stalled-scope-detection'] missing; on the local pm-mcp-clients checkout: ['api-contract', 'integration-layers', 'operator-enrolment-verbs', 'operator-harness-and-roles-verbs'] missing and present under implemented/
- HYPOTHESIS: this plan can be launched from the checkout of `plan-marshall-mcp` with the absolute path of this spec in the ledger worktree of `pm-mcp-core` (the lane takes a spec outside its repository) — confirm/refute at the first step of the lane (verify-at-outline); if it refuses, the operator copies the spec into that repository's `.plan/temp/`
  - verdict: unverifiable | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the package depends on nothing (the native build of the daemon exists today) and serves Milestone 4 — confirm/refute against the index of `plan-marshall-mcp` and its roadmap items (verify-at-outline)
  - verdict: unverifiable | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before writing, `git fetch` the three repositories, list the open pull requests of `plan-marshall-mcp` for one touching `doc/plans/README.adoc`, and re-read the index at `origin/main`; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 9732401d6041fcaf45319877b7147debca5b950e | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: plan-marshall-mcp origin/main 38bd66d read on 2026-10-22; its open pull requests were not listed (the ci abstraction has no repository selector, an open lead of the epic): the plan lists them

## Expected Surface

- HYPOTHESIS: `plan-marshall-mcp/doc/plans/native-build-verification.adoc` — new package file in the repository `plan-marshall-mcp`, not in this one
- HYPOTHESIS: `plan-marshall-mcp/doc/plans/README.adoc` — the new row and the dropped dependencies, in the repository `plan-marshall-mcp`

This plan touches no path of this repository; the entries above name another repository, so the reader of this section cannot anchor them here, and the plan is sequenced rather than paired by the disjointness gate (deliberate).

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: none in this repository
- Adjacent to: the native build itself, which the new package owns; the open pull requests of `plan-marshall-mcp`, unknown here

## Hand-Off Command

Launch it from the checkout `/home/oliver/git/plan-marshall-mcp` (the plan writes that repository):

```text
/plan-marshall-runbook-lane plan=/home/oliver/git/pm-mcp-core/.plan/local/worktrees/_orchestrator/.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-102-native-build-package-in-plan-marshall-mcp.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
