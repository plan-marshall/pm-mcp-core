# PLAN-82: Close the advisories PLAN-74 left on the credential-store choice and move

epic: pm-mcp-core-m2-m4
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-82-credential-store-move-advisories.md` and is queued as one row file, `queue/PLAN-82.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

PLAN-74 (#90, `44f904e`) shipped the credential store as the operator's choice and the move between stores, and recorded four advisories it did not fix. Two of them can leave credential material in the wrong place and are the reason for this plan; the other two are an audit-trail gap and inexact specification text. They are the plan's own statements (landing message `plan-74-credential-store-choice-036`), not re-read by the orchestrator: the executing plan reads the code first and drops or re-scopes any finding the code refutes.

## Deliverables

1. **The first-recording key carry** (`CredentialStoreChoice`, `IntegrityKeyCarry`) looks only at the store the instance found; decide and implement what it does when the other store also holds an integrity key (carry, refuse, or reconcile), with the pre-existing-target test of the checklist.
2. **A stop between the first recording's switch and the key delete** leaves the integrity key in both stores; the resume (`CredentialStoreMoveResume`, `start.CredentialStoreMoveResume`) recognises and finishes this state, decided per crash window and covered by a cell of the (phase x recorded store x writer) matrix.
3. **The `credential_store_moved` audit event** (`CredentialStoreMove.EVENT`) carries a transaction id, so a re-delivered record is recognised by the reader; the audit event's schema and its documentation change together.
4. **Specification text**: the schema text of `target_held_key` (`store-schemas/12-machine-stores.adoc`, near the `credential-store-move-record` block) and the layout comment (`filesystem-layout/02-machine-runtime.adoc`, near line 86) are inexact in one corner each; correct them to what the code and ADR `runtime-credentials/004` and `006` do, after reading both.
5. **Tests and documents**: a test per cell for the first two items, the developer documentation `pm-runtime/doc/developer` for the package that holds the credentials, and one ADR per decision (`pm-runtime/doc/adr/runtime-credentials/`, next free number).

**Done when:** Each of the four items is either fixed with its test and document, or shown by the code to be no defect and recorded in the pull request body; the checklist for durable multi-step protocols of the Standing Constraints is applied to the first two; no credential value or key is written to a log.

**Trace starting set:**

- Requirements: PM-CRED-2, PM-CRED-3 (as PLAN-74)
- Specification: CLI, Coordination & Security § Credential Store as an Operator Choice; Store Schemas § Machine Stores (`credential-store-move-record`)
- Watch: none

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
- **Specifications can be test input (found by PLAN-69, 2026-10-18).** A schema-parity test reads blocks of the `.adoc` specifications under `doc/specification/` (for example `WorkflowStatusSchemaParityTest` reads the JSON schema block after `== Workflow State Store` of `store-schemas/02-workflow-state-store.adoc`). Before the outline deletes or edits the implemented sections of a specification part, search the test tree for readers of that file, list each as a read file of the deliverable and state which blocks must stay byte-stable; a block a test reads is not deleted with the covered prose.
- **Checklist for `pm-workflow` code (found by PLAN-69, 2026-10-18).** (1) A refusal added for a workflow-DSL construct has exactly one owner, the parser or the verifier; every verifier refusal fixture is a tree the parser accepts, and fixture-count success criteria are written after that split. (2) A new pre-parsing layer in front of the lexer accepts at least what the lexer accepts (line endings, whitespace); add a CRLF twin fixture for every new line-oriented reader. (3) Where a printer and a reader must agree on a lexical rule, the printer calls the reader's predicate. (4) A completeness test over an enum derives the expected set from the enum at run time. (5) An exception to an invariant for one caller is scoped to that caller, with a refusing fixture from every other caller that reaches the check. (6) When a concept is generalised (start state to starting states), sweep every diagnostic message that names the old one.
- **Checklist for durable multi-step protocols in `pm-runtime` and `core.store` (found by PLAN-74, 2026-10-18; four review rounds each found another unguarded transition of the credential-store move).** (1) A resume decides from the pair (record phase, state actually recorded), never from the phase alone, and a guard added to one phase branch gets its symmetric guard on every other branch; enumerate (phase x recorded state x writer) with a test per cell before the first review. (2) While a durable in-progress record exists, every other writer of the state it switches is refused under the same lock, and an authorization is verified against durable state, never asserted by the caller. (3) The record that drives recovery is deleted last, after every side effect (audit event, clean-up after a failure) is durable. (4) A decision with side effects (which store, which key) is decided once and persisted, never re-derived per call from a probe that can flap. (5) A copy-then-switch move has a test with a pre-populated target (an overlapping key and a target-only key) and a failure injected after the copy; the pre-existing-target hazard is checked for every kind of object the protocol copies (entries, integrity key, sidecars) in the same task. (6) A store's temp-file namespace is disjoint from its legal entry-name namespace. (7) A fix of a state-machine transition lists the specification section, the ADR, the Javadoc and the test that state it among its affected files and updates them in the same commit; ADR and test are checked against the specification text, not against the code they document. (8) The invariants of a multi-write protocol are stated per crash window, not per phase. (9) A test that asserts a file location derives the expected path from the production layout independently, never from the object under test. (10) A native binding is loaded once; only what can change at runtime (a lock state) is probed per operation.
- **Facts about `pm-runtime` (found by PLAN-20, 2026-10-18).** (1) FFM downcall descriptors (`MacSecurity`, `Posix`, the `kill(2)` descriptor of `KillBinding` and `ProcessGroupSignals`) are registered in `pm-runtime/src/main/resources/META-INF/native-image/de.planmarshall/pm-runtime-credentials/reachability-metadata.json`, not in the `plan-marshall-mcp` assembly; verify a location claim with a path query, not with a Javadoc sentence. (2) `ProjectRecovery` is public and wired by the `pm-mcp-server` assembly in `plan-marshall-mcp`: a change of its factory methods needs a source-compatible overload or a named follow-up in `plan-marshall-mcp` after the SNAPSHOT deploy; the outline says which. (3) Signal numbers differ between Linux and macOS: a table of signal names for a job killed by a signal is chosen per OS, never taken from the Linux table. (4) In `pm-runtime`, suppress a Sonar rule inline at the declaration with a stated reason beside it where the design requires the flagged shape (seen: S2142, S3077, S6539, S1075), rather than reshaping the design or excluding the rule for the file.
- **Renumber the ADRs against `origin/main` before finalize and after every rebase (found by PLAN-14, 2026-10-18).** An ADR added to a module ADR directory (for example `pm-core/doc/adr/core-store/`) can take a number another plan took in the same directory; different slugs mean different file names, so git reports no conflict and the duplicate-number gate of the branch-cleanup step scans only `doc/adr`. List the touched ADR directories with `git ls-tree origin/main` and renumber the plan's ADRs to the next free numbers; PLAN-14 renumbered twice.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`). A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: PLAN-74 shipped as #90 (`44f904e`, 67 files) and recorded the four advisories as not fixed — `landings/PLAN-74.md` and the landing message `plan-74-credential-store-choice-036`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: ci pr view 90 and git show --stat 44f904e on 2026-10-18; the advisories are quoted from the landing message
- OBSERVED: `CredentialStoreMove.EVENT` exists in `pm-runtime` and the move-record schema with `target_held_key` is at `store-schemas/12-machine-stores.adoc` line 695 ff. on `origin/main` at `44f904e`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git grep of origin/main at 683707e found CredentialStoreMove.java:104 (EVENT) and 12-machine-stores.adoc:695-718 (credential-store-move-record, target_held_key)
- HYPOTHESIS: the first-recording carry looks only at the store the instance found, and a stop between the first recording's switch and the key delete leaves the key in both stores — the plan's statement; confirm/refute at the code (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the plan's statement, not re-read by the orchestrator; verify-at-outline by the executing plan
- HYPOTHESIS: the audit event carries no transaction id, so the reader cannot recognise a re-delivered record — the plan's statement; confirm/refute at the event and its reader (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the plan's statement; verify-at-outline by the executing plan
- HYPOTHESIS: the schema text of `target_held_key` and the layout comment `02-machine-runtime.adoc:86` are inexact in one corner each — the plan's statement; confirm/refute against the code (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the plan's statement; verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that edits the credentials package, and re-read the four advisories at the code at `main`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/CredentialStoreChoice.java` — the first-recording carry
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/IntegrityKeyCarry.java` — the carry
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/CredentialStoreMove.java` — the audit event
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/start/CredentialStoreMoveResume.java` — the resume of the stop between switch and key delete
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/CredentialStoreChoiceTest.java` — cells and cases
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/start/CredentialStoreMoveResumeTest.java` — cells
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/CredentialStoreChoiceFixture.java` — fixture
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-credentials.adoc` — developer documentation; created or extended
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-credentials/` — ADRs; next free number
- OBSERVED: `doc/specification/store-schemas/12-machine-stores.adoc` — the `target_held_key` text and, if the event schema lives here, the audit event
- OBSERVED: `doc/specification/filesystem-layout/02-machine-runtime.adoc` — the layout comment near line 86
- OBSERVED: `doc/plans/README.adoc` — no row of its own

## Dependencies and Sequencing

- Depends on: nothing — PLAN-74 shipped as #90
- Overlaps with: computed by `corpus cross-check` at emit time; every plan through `doc/plans/README.adoc` and `doc/Traceability.adoc`; the plans of the same module through its log holder, which is not a declared surface
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-82-credential-store-move-advisories.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
