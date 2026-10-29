# PLAN-116: The pm-runtime adapter that reads pending entry records for the engine

epic: pm-mcp-core-m2-m4
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-116-pending-entry-reader-adapter.md` and is queued as one row file, `queue/PLAN-116.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19. (R12 applies only if the trace matrix shows a message to add, see Standing Constraints; R14 and R16 apply because the plan changes `pm-runtime` and reads a `core.store` record; R09 binds the confinement of the read; R15 does not apply, no `pm-workflow` code changes; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-entry.adoc` (new, for the new package) and `pm-runtime/doc/developer/runtime-question.adoc` (extended); ADR directories: `pm-runtime/doc/adr/runtime-entry/` (new) and `pm-runtime/doc/adr/runtime-question/`; layout: workstream WS-01.
- R12 — log message holder: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

> **SUPERSEDED 2026-10-29 (operator walk, decision 13 of 18):** PLAN-76 (`start-source-intake`) replaces the file form of the pending entry, `PendingEntryStore`, `EntryId` and the port `PendingEntryReader` this plan implements; the reader of the directory form and the entry addressing of questions are item 13 of PLAN-76. This spec stays as the source of the design and is not to be emitted.

## Objective

Implement the port `core.spi.PendingEntryReader` in `pm-runtime`, over `PendingEntryStore`. The port's own Javadoc says it "is implemented in `pm-runtime`, which holds the store of a project's workspace scope; … and no implementation exists yet: until the runtime wires one, only the test doubles do" (`PendingEntryReader.java`, the third paragraph). PLAN-21 (`entity-addressing`) shipped the port and its use in `EntityAddressResolver`; PLAN-88 (#116) shipped the same kind of adapter for the question records (`QuestionLogRecordReader`) and not this one; the Open Defect "A pm-runtime adapter for `core.spi.PendingEntryReader` over `PendingEntryStore` is owed (PLAN-21) — the daemon wiring" is therefore still true. Build it the way #116 built its sibling: a read that is a read (no lock an append could wait on, nothing written, nothing repaired), confined by `WorkspaceConfinement` (R09), whose three results are values. In the same pull request record the trust assumptions of both readers, which CodeRabbit's architecture review of #116 raised and no document states.

## Deliverables

1. **The adapter `PendingEntryRecordReader`** (new class, new package `de.planmarshall.runtime.entry`, with its `package-info.java`) implementing `PendingEntryReader`. Constructed with the runtime base and the project id (the public constructor fixes the confinement root to the canonical runtime base, as `QuestionLogRecordReader` does; a package-private constructor takes the confinement for the refusal tests). `read(ReadRequest)`: the store is `new PendingEntryStore(JournalScope.workspace(base, projectId))`; the record path `store.store(entryId)` passes the confinement and must be reached by its canonical path (a rejected or non-canonical path is `Unreadable`, a value); `store.read(entryId)` returning a record is `Live` with `record.kind().wireName()`, returning empty is `Deleted`, and a `StoreRefusal` (another format version, an unreadable record, a record that states another `entry_id` than its file name) or an `UncheckedIOException` is `Unreadable` with the reason of the failure. Nothing is thrown for those. The project id is validated at construction through the one owner of the identifier rule (`LockKey.workspace`).
2. **The tests** (JUnit 5, `@DisplayName`, AAA, no Mockito; expected values derived independently of the object under test, R14 (9)): in `PendingEntryRecordReaderTest`, a live record of each kind (the cases derived from `PendingEntryRecord.Kind.values()` at run time, R15 (4) as a pattern), a deleted entry (never written; written and swept by `PendingEntryRecordsSweep`), and an unreadable one per cause (a record of another `format_version`, an unparseable file, a record stating another `entry_id`, a directory in the place of the file, a path the confinement rejects through a symbolic link out of the base, a project directory that does not exist); the file tree is byte-identical before and after every read (the read is a read), and a read while the workspace lock is held by another transaction returns without waiting (a bounded-wait case as `QuestionLogRecordReaderReadTest` has it). The records are written with the production writers (`PendingEntryStore.create` in a `ScopeTransaction`) and tampered afterwards, never hand-written from a copy of the schema.
3. **Document the decision, the new package and the trust assumptions.** (a) `package-info.java`; a developer guide `pm-runtime/doc/developer/runtime-entry.adoc` (what the package is for, the type, the three results, the invariants and failure modes, the specification sections it implements); one ADR in `pm-runtime/doc/adr/runtime-entry/` (new directory, number 001) for the home and the mapping (alternatives: the class in `runtime.start` beside `PendingEntryRecordsSweep`, which is a recovery step and not a read port; in `runtime.question`, which is the question log's package; a lookup that lists `entryIds()` first, which turns one read into a directory scan); one line in `pm-runtime/README.adoc` for the new package (R06). (b) Correct the sentence "and no implementation exists yet: until the runtime wires one, only the test doubles do" in the Javadoc of `pm-core/src/main/java/de/planmarshall/core/spi/PendingEntryReader.java` to name `PendingEntryRecordReader`, and the `doc/Traceability.adoc` lines of `PM-ARCH-4` that name the port. (c) One ADR in `pm-runtime/doc/adr/runtime-question/` (next free number) and a section of `pm-runtime/doc/developer/runtime-question.adoc` state the trust assumptions of both readers: the check of the confined path precedes the open of the file (a check-time limitation, bounded by the owner-only mode of the runtime base that `BaseDirectoryCheck` enforces), a record changed out of band that is still valid is not detected by the reader, and the ledger of what a fix would take (the workspace's digest sidecar covers a transaction's writes; `StoreSnapshot.read(store, recordedSize, recordedSha256)` can verify a file against a recorded digest, so a verifying read is possible but is a design change of both readers and of their latency). The ADR records the choice "state the assumption, do not verify" as taken for now and names the verifying read as the alternative; if the operator wants it, it is a plan of its own.
4. **State the owed rest, in the pull request body.** The CDI producer that constructs both readers for the engine is `pm-mcp-server` in `plan-marshall-mcp` (a named follow-up after the SNAPSHOT deploy, R16 (2)); nothing of the assembly is changed here. The work package `start-source-intake` (PLAN-76, staged) turns the file form `pending/<entry_id>.json` into the directory form `pending/<entry_id>/entry.json`: the adapter reads through `PendingEntryStore.read`, so only the one path accessor it confines changes; say in the pull request body which form the adapter was written against.

**Done when:** `PendingEntryRecordReader` is the only implementation of `PendingEntryReader` in product code (`git grep 'implements .*PendingEntryReader'` over `src/main`); the cases of Deliverable 2 pass beside every existing test of `runtime.question` and `runtime.start`; the package guide, the two ADRs, the README line, the Javadoc sentence and the Traceability lines are written; the owed rest is in the pull request body. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-runtime` and `pm-core` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-runtime -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** PM-ARCH-4 (stateless requests and explicit scope addressing; bullets 2 and 5), PM-SEC-2 (confinement), PM-IMPL-5 (the engine ports); `doc/specification/store-schemas/01-format-and-session-log.adoc` § Pending Entry; ADRs `pm-runtime/doc/adr/runtime-question/001` to `004` and `pm-core/doc/adr/core-store/039`; the landing of PLAN-88 and the Open Defects "A pm-runtime adapter for `core.spi.PendingEntryReader`" and "The trust assumptions of the question reader before it is wired".

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Log messages: none expected.** The adapter answers values and logs nothing. If the trace matrix shows one, it goes into `RuntimeLogMessages` at the next free identifier of its level, and the plan says so in its inbox message.
- **No change to `pm-core` code.** The one edit of `pm-core` is the Javadoc sentence of the port; `PendingEntryStore`, `PendingEntryRecord`, `EntryId` and the codec are read, not changed (PLAN-76 changes them).
- **The package name is fixed by this spec** (`runtime.entry`); a plan that finds a better home asks the operator at refine (R07), because the name decides the guide, the ADR directory and the README line.

## Claim Labels

- OBSERVED: the port has no implementation in product code — `git grep -ln PendingEntryReader` over `pm-*/src` finds the port, its `package-info.java`, `EntityAddressResolver` and test doubles (`EngineFixtures`, `EntityAddressingTest`, `EnginePortsTest`) only; the Javadoc says "no implementation exists yet" (`pm-core/src/main/java/de/planmarshall/core/spi/PendingEntryReader.java`, third paragraph)
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: git grep at a8da4104
- OBSERVED: PLAN-88 shipped the sibling and not this adapter — `pm-runtime/src/main/java/de/planmarshall/runtime/question/` holds `QuestionLogRecordReader` and `package-info.java` only; `landings/PLAN-88.md` names `QuestionRecordReader`
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: directory listing at a8da4104
- OBSERVED: the store read the adapter maps — `PendingEntryStore.read(EntryId)` returns `Optional<PendingEntryRecord>` and throws `StoreRefusal` or `UncheckedIOException` as the port's Javadoc maps them (`pm-core/src/main/java/de/planmarshall/core/store/PendingEntryStore.java:174-190`); the store is built over `JournalScope.workspace(base, projectId)` (`JournalScope.java:123`, constructor `PendingEntryStore.java:53`)
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: files read at a8da4104
- OBSERVED: the pattern to follow — `QuestionLogRecordReader` canonicalizes the base, validates the project id through `LockKey.workspace`, builds `new WorkspaceConfinement(new WorkspaceRoots(base, Set.of()))`, confines the store path and answers `Unreadable` for a rejected or non-canonical path (`pm-runtime/src/main/java/de/planmarshall/runtime/question/QuestionLogRecordReader.java:53-130`)
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: file read at a8da4104
- OBSERVED: the trust lead is not stated anywhere — a search of `pm-runtime/doc/adr/runtime-question/` and `pm-runtime/doc/developer/runtime-question.adoc` for "out-of-band", "check-time", "digest" and "replay" finds nothing; the base is held owner-only by `BaseDirectoryCheck` (`pm-runtime/src/main/java/de/planmarshall/runtime/start/BaseDirectoryCheck.java:25-28`: owned by the running user, mode `0700`, no symbolic links); the reads are unchecked snapshots (`StoreSnapshot.read(file)` with `StoreIntegrity.UNCHECKED`, `StoreSnapshot.java:56-58`) while `StoreSnapshot.read(store, recordedSize, recordedSha256)` exists (line 71)
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: grep and files read at a8da4104; the bot's statement itself stays third-party text
- HYPOTHESIS: the workspace digest sidecar covers `pending/<entry_id>.json` as `PendingEntryStore`'s Javadoc says ("the digest sidecar of the workspace covers it", line 32), so a verifying read could compare the file against the sidecar — read `DigestSidecar` and `StoreDigest` at the outline before the ADR states what a verifying read would need (verify-at-outline)
- HYPOTHESIS: the work package `start-source-intake` (PLAN-76) lands after this plan; if it lands first, the adapter is written against its directory form and the confined path is `entry.json` — confirm at the head of `main` (verify-at-outline)
- Verify-first clause: before editing, list the open pull requests for one that names `PendingEntryReader`, `PendingEntryStore` or `runtime/entry`, and re-read the port and the store at the head of `main`; a refutation of any claim above loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/entry/PendingEntryRecordReader.java` — new class, new package
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/entry/package-info.java` — new, the package does not exist yet
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/entry/PendingEntryRecordReaderTest.java` — new test class
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/spi/PendingEntryReader.java` — one Javadoc sentence corrected
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-entry.adoc` — new developer guide (the package does not exist yet)
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-entry/` — new ADR directory, one ADR (001)
- OBSERVED: `pm-runtime/README.adoc` — one line for the new package (R06)
- OBSERVED: `pm-runtime/doc/developer/runtime-question.adoc` — a section on the trust assumptions (shared bookkeeping, R19)
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-question/` — one ADR, the next free number (shared bookkeeping)

## Dependencies and Sequencing

- Depends on: nothing. PLAN-21 (`entity-addressing`, shipped) and PLAN-88 (shipped, #116) are the base.
- Overlaps with, by file: `pm-core/src/main/java/de/planmarshall/core/spi/PendingEntryReader.java` is declared by PLAN-76 (`start-source-intake`, staged: "replaced with the file form"), 1 shared entry (a Javadoc sentence here, the file-form rewrite there): whichever merges second merges the base branch (the lane's step 7) before its merge gate. `pm-runtime/README.adoc` is declared by no live plan beside the shared bookkeeping rule. PLAN-47 (`decision-engine`, staged) extends `QuestionLogRecordReader` and `runtime-question.adoc` (an additive guide section, shared bookkeeping): this plan adds a guide section and an ADR to the same package and does not edit the class (0 shared files). PLAN-46, 105, 108 and 109: no shared file. The other plans of this cluster: disjoint, no shared file.
- Adjacent to: the `core-hygiene` cluster (shared ground `core/store/`, `core/spi/`): this plan reads `PendingEntryStore` and edits one Javadoc of `core/spi`. The Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` stays untouched and owes the producer.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-116-pending-entry-reader-adapter.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
