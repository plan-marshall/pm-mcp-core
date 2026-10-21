# PLAN-87: Reset the transaction journal to format version 1

epic: pm-mcp-core-m2-m4
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-87-journal-format-version-reset.md` and is queued as one row file, `queue/PLAN-87.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

The operator decided on 2026-10-13 that every versioned artifact stays at version 1 while nothing is released, and recorded this as an Open Defect of the epic to be cleaned up at the end: `TransactionJournal.FORMAT_VERSION` is 3 (`pm-core/src/main/java/de/planmarshall/core/store/TransactionJournal.java`), raised 1 to 2 by PLAN-06 (#26, owed audit events) and 2 to 3 by PLAN-12 (#51, delete mode). Every other versioned artifact is at 1 (checked on 2026-10-13 at `bed24eb`). This plan brings the journal to 1 in code, tests, fixtures, guide, ADR and specification, with the behaviour of versions 2 and 3 as the shape of version 1.

## Deliverables

1. **The constant and the document.** `FORMAT_VERSION` becomes 1; the Javadoc of the class (the document shape `{"format_version": 1, ...}` and the sentence "Version 1 had no owed events and version 2 no delete target; both are refused as outdated") is rewritten to describe one version; the reader refuses a journal of any other version with the existing named reason.
2. **No reader of the old versions.** The journal tests that pin 2 or 3 change: `TransactionJournalTest` ("a journal of version 2, which had no delete target, is refused as outdated") becomes a test that a journal of another version is refused as outdated; every fixture, writer and assertion in `TransactionJournalTest`, `JournalRecoveryTest`, `ScopeTransactionTest`, `ScopeSessionLogTest`, `PendingEntryStoreTest`, `ScopeIntegrityTest` and `EnrolmentStoreTest` that carries a journal version is found with `git grep` and set to 1.
3. **The documents.** `pm-core/doc/developer/core-store.adoc` (the passages "The journal is written in `format_version` 3", the version-2 refusal row and the test line), ADR `pm-core/doc/adr/core-store/007-delete-mode-of-the-transaction-journal.adoc` (it records the bump: record the decision that the journal's shape is version 1 instead, in the form of the ADR template and without a bump; the number of the ADR does not change), and the Store Schemas specification part that shows `"format_version"` for the journal (`store-schemas/01-format-and-session-log.adoc` and what `git grep` finds).
4. **The same sweep for the others.** `git grep` the repository for `format_version` and `FORMAT_VERSION` with a value other than 1 in code, fixtures, schemas, specification examples and ADRs; report each in the pull request body, and fix those the operator's decision of 2026-10-13 covers.

**Done when:** `TransactionJournal.FORMAT_VERSION` is 1; no file of the repository states a journal `format_version` of 2 or 3 except as the refused example of a test; the journal tests, the recovery tests and `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` are green; the pull request body lists the sweep of Deliverable 4.

**Trace starting set:**

- Requirements: PM-IMPL-2 (as PLAN-06 and PLAN-12)
- Specification: Store Schemas § Format and Session Log
- Watch: Store Schemas

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
- **Renumber the ADRs against `origin/main` before finalize and after every rebase (found by PLAN-14, 2026-10-18).** An ADR added to a module ADR directory (for example `pm-core/doc/adr/core-store/`) can take a number another plan took in the same directory; different slugs mean different file names, so git reports no conflict and the duplicate-number gate of the branch-cleanup step scans only `doc/adr`. List the touched ADR directories with `git ls-tree origin/main` and renumber the plan's ADRs to the next free numbers; PLAN-14 renumbered twice.
- **Log messages: the holder of your module, next free identifier** (operator decision 2026-10-13; pm-mcp-core #64, ADR `doc/adr/005-one-log-message-holder-per-module.adoc`). A new INFO, WARN or ERROR record goes into the holder of the module whose production code logs it, at the next free identifier of its level (INFO 1-99, WARN 100-199, ERROR 200-299, the same ranges in every holder); for this plan: none expected: a constant, its tests and its documents; the refusal of an outdated journal already has its record. A record two modules log goes to the lower one (`pm-core`); a module logs only through its own holder and `CoreLogMessages`. There are no workstream blocks and nothing is renumbered. A provider's first record creates its holder `de.planmarshall.provider.<x>.log.<X>LogMessages` (prefix `PM_PROVIDER_<X>`) with a `package-info.java`, and adds the prefix to `KNOWN_PREFIXES` in `CoreLogMessagesTest`. After a rebase, run the holder's test, which refuses an identifier used twice or outside its level's range, and move to the next free one. The same identifiers go into `doc/LogMessages.adoc` of this repository, under the holder's section.

- **Format versions stay 1 (operator decision 2026-10-13).** Nothing is released yet, so every versioned artifact (`format_version` of a store document, record, sidecar, journal, codec) stays at version 1. A format change replaces version 1 in place: no version bump, no reader for an older version, no migration. Do not raise a `FORMAT_VERSION` constant, a `format_version` in a fixture, schema or specification example, or an ADR that records a bump.

## Claim Labels

- OBSERVED: `TransactionJournal.FORMAT_VERSION` is 3 and its Javadoc says version 1 had no owed events and version 2 no delete target — `pm-core/src/main/java/de/planmarshall/core/store/TransactionJournal.java` lines 48-54 and 77 at 98ce971
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: grep of TransactionJournal.java at 98ce971: line 77 public static final int FORMAT_VERSION = 3; lines 48 and 54 describe versions 1 to 3
- OBSERVED: every other `FORMAT_VERSION` constant of `pm-core` main code is 1 — grep `FORMAT_VERSION *= *[0-9]` over `pm-*/src/main` at 98ce971
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: the grep lists AnalysisResult, QuestionRecord, ProjectStoreIdentity, AuditRecord, WaiverRecord, AuditHeadRecord, Finding, JobRecord, ProjectEnrolment and AuditIntentFile, each = 1, beside TransactionJournal = 3
- OBSERVED: the guide `core-store.adoc` line 215 says the journal is written in `format_version` 3, ADR `core-store/007` records the bump, and `TransactionJournalTest` line 367 pins the refusal of a version 2 journal — files at 98ce971
  - verdict: corroborated | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: grep at 98ce971: core-store.adoc:215, 007-delete-mode-of-the-transaction-journal.adoc:37 and TransactionJournalTest.java:367
- HYPOTHESIS: the list of tests in Deliverable 2 is complete — confirm/refute with `git grep -n 'format_version\|FORMAT_VERSION' -- pm-core/src/test` (verify-at-outline)
  - verdict: unverifiable | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: `store-schemas/01-format-and-session-log.adoc` is the only specification part that shows the journal version — confirm/refute with `git grep -n 'format_version' -- doc/specification` (verify-at-outline)
  - verdict: unverifiable | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that touches `TransactionJournal`, and re-run the two greps of the hypotheses at `main`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: 98ce971c84fefa9a4650a1e115274c257e61f3fb | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/TransactionJournal.java` — the constant and the Javadoc
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/TransactionJournalTest.java` — the version cases
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/JournalRecoveryTest.java` — journal fixtures
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ScopeTransactionTest.java` — journal fixtures
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/PendingEntryStoreTest.java` — journal fixtures
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ScopeIntegrityTest.java` — journal fixtures
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/EnrolmentStoreTest.java` — journal fixtures
- OBSERVED: `pm-core/doc/developer/core-store.adoc` — the journal passages
- OBSERVED: `pm-core/doc/adr/core-store/007-delete-mode-of-the-transaction-journal.adoc` — records the decision without a bump
- HYPOTHESIS: `doc/specification/store-schemas/01-format-and-session-log.adoc` — the journal version, if it shows one

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: computed by `corpus cross-check` at emit time; PLAN-85 `store-records-advisories` and PLAN-88 touch `core.store` and `core-store.adoc` through other passages; every plan through `doc/Traceability.adoc` only if it adds entries
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-87-journal-format-version-reset.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
