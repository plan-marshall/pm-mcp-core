# PLAN-149: The lesson record and its file format

epic: pm-mcp-core-m2-m4
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-149-lesson-record-and-codec.md` and is queued as one row file, `queue/PLAN-149.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r3`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R17, R18, R19. (R01 and R08 are replaced for Deliverable 1, see Standing Constraints; R12 applies only if the trace matrix shows a message to add; R14 because the record is a `core.store` format that the second plan writes under a journaled transaction; R17 because the plan runs the links check. R15 and R16 do not apply: neither `pm-workflow` nor `pm-runtime` is touched.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc` (exists; add a section); ADR directory: `pm-core/doc/adr/core-store/`; layout: workstream WS-10.
- R12 — log message holder: `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`).

## Objective

The project lesson store of PM-MCP (`<PM_MCP_BASE>/projects/<project_id>/lessons/`, Store Schemas § Lesson Record) has no record type, no file format and no reader at HEAD: only its lock key, its journal scope and its scope type exist, and no work package builds the rest (open defect "No package builds the epic ledger records, and no package builds the lesson store", part 2; operator decision 2026-10-29: cut a package ordered before its consumers `start-from-clients` (PLAN-48), `start-source-intake` (PLAN-76) and `plan-and-epic-control` (PLAN-51)). This is the first of two plans. It cuts the package `lesson-store`, creates the lesson record types and the file format (YAML-shaped front matter in a Markdown file) with a strict codec, and pins the record to the schema of the specification by a parity test. The store itself (allocation, resolver, revision, retirement, claim) is the second plan, PLAN-150 (`lesson-store-operations`).

**Which lesson store.** This is the lesson store of the PM-MCP engine, a per-project store of the machine that the daemon owns. It is NOT the lesson store of `plan-marshall` (`manage-lessons`, `.plan/local/lessons-learned/`, the `lessons-routing` inbox), which is external and never read by the engine (`PM-MIG-2`). `plan-marshall` appears here only as the origin of the ported test data (`PM-TEST-2`): the plain fixtures are derived once from its `test/plan-marshall/manage-lessons` suite and the cases of the watch items, and are committed; no test reads that checkout.

## Deliverables

1. **The package file and its index row (R01), as the plan's first commit.** Write `doc/plans/lesson-store.adoc` exactly as the section *Package File* below gives it, and add the row of the section *Index Row* to the table of `doc/plans/README.adoc` (status `open`, depends on nothing). In the same file add `lesson-store` to the _Depends on_ cell of the rows `start-source-intake`, `start-from-clients` and `plan-and-epic-control` (each builds on the lesson store: the sources, the claim and its release). Run the links check (R17): `BROKEN LINKS 0`. Done when: the file exists with that text, the row is in the index, three cells name the package, and the links check is recorded in the pull request body.
2. **The record types.** In `pm-core` package `core.store`: `LessonId` (the grammar of the schema, `^[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{3,}$`, at most 64 characters; the file name `<lesson_id>.md`; a file name that does not parse is no lesson; the UTC hour prefix, which the second plan allocates against); the closed enumerations with their wire names (`category`, `status`, `provenance`, `audience`, the remedy `kind`, the source `kind`, the retirement `verdict`, `retired.lesson_state`, `golden_label`, `last_filing_failure`, the promotion `kind`); `LessonFrontMatter`, a record with every member of the `LessonFrontMatter` schema in schema order and its nested records (source, remedy, audience override, tool version override, promotion, revision, merged-from entry, claim, retired); `Lesson`, the front matter with its Markdown body. The compact constructors state the rules the specification text states beside the schema and refuse a violation with `IllegalArgumentException`: `remedy.check` set exactly for the kind `wire_check` (detail `remedy_check_mismatch`); `retired` set exactly when `status` is `retired`; `labelled_at` exactly when `golden_label` is set; `rule` set for the category `arch-constraint`; `recurrence_count` and `last_seen` only for it; `sources` and `revisions` not empty; `revision` equal to the `n` of the last `revisions` entry; `derived_from_untrusted` a primitive that the record only carries. Done when: `LessonIdTest` and `LessonFrontMatterTest` cover every rule above with one refusing case each and a passing control, and the lesson ids of the watch fixtures (`2026-09-22-12-001`, `2026-09-08-22-004`, `2026-09-13-06-002`) parse.
3. **The codec of the lesson file.** `LessonCodec` reads and writes one lesson file: the line `---`, the front matter, the line `---`, the Markdown body verbatim. The front matter is written as one JSON object in the canonical member order of the schema (a JSON object is a YAML 1.2 flow mapping, so the file is front matter in the sense of the specification and needs no YAML library; ADR, Deliverable 5) and read with `StrictJson.read` (public API of `core.store`; unknown, duplicate, missing and mistyped members refused; `format_version` 1 only (R13); an instant only in the shape `Instant.toString` writes, so what is read is written again byte for byte). A file that does not decode is not an exception for the reader: `decode` returns a sealed result, `Readable(Lesson)` or `Unreadable(reason, detail)` (no opening fence, no closing fence, front matter not an object, a refusal of the strict reader, a rule of Deliverable 2), because a file without parseable front matter lists as `present_unreadable`. `encode` refuses a lesson with missing or invalid front matter (the specification: "a write with missing or invalid front matter is rejected"). `LessonCodec` publishes its member lists as package-private constants for the parity test (the pattern of `JobRecordCodec.MEMBERS`). Done when: `LessonCodecTest` shows `decode(encode(x))` equal to `x` and `encode(decode(b))` byte-identical to `b` for canonical bytes; a body that itself contains `---` lines is kept whole; one refusing case per `Unreadable` reason, an anchor, an alias and a tag in YAML spelling (which a JSON-only reader cannot meet, shown by a block-style file) each `Unreadable`; a CRLF file is either decoded or `Unreadable`, whichever the ADR decides, with the fixture pinned.
4. **Schema parity with the specification.** `LessonFrontMatterSchemaParityTest` in `pm-core/src/test/java/de/planmarshall/core/store/` reads the first `[source,json]` block after the line starting `== Lesson Record` of `store-schemas/10-job-and-lesson-records.adoc` with `SpecificationDocument.jsonBlockAfter` (the pattern of `JobRecordSchemaParityTest`, which reads the block after `== Job Record`) and asserts, against the codec and the record types: the title `LessonFrontMatter`; the members of the root and of every nested object, in schema order, and the `required` sets; the values of every enumeration; the `lesson_id` pattern at all three places it occurs (the root, `merged_from[].lesson_id`, `retired.into`) and its `maxLength`; `format_version` const against the codec constant; the numeric and length bounds (`remedy.statement` 10 to 1000, `remedy.check` 1 to 512, `recurrence_count` and `revision` at least 1, `sources` and `revisions` `minItems` 1); the `dependentRequired` pair. Done when: the test passes at the head and fails when a member, an enumeration value or a bound of the schema block is changed without the code (shown once by the plan on a scratch copy, stated in the pull request body). **From here on the lesson schema block is test input (R10):** every plan that edits `store-schemas/10-job-and-lesson-records.adoc` keeps that block byte-stable or changes it together with the codec; `skip-on-docs-only: false` in `.github/project.yml` makes the build run for a specification-only pull request (PLAN-104), and the plan verifies at the outline that it is still set.
5. **Documentation, decisions and the reduction of the specification (R05, R06).** `package-info.java` of `core.store` is NOT edited (it lists the specification part already); every new public type has Javadoc. Developer documentation: a section "Lesson record and file format" in `pm-core/doc/developer/core-store.adoc`. ADRs in `pm-core/doc/adr/core-store/`, each the next free number of the directory renumbered against `origin/main` (R11): (a) the front matter is written as a JSON object (YAML 1.2 flow style) and read by the strict JSON reader; alternatives: a YAML library (a new dependency, which `CLAUDE.md` forbids without the operator), a hand-written reader of block YAML (the subset reader of `UnitFrontmatterReader` has no nested objects); consequence: a hand-edited file in block style is `present_unreadable`; (b) the lesson classes live in `core.store`, not in a new package, because the crash tests need the package-private `ScopeTransaction.StepHook` and `JournalScope.resolveWithoutLinks`. Specification: in `store-schemas/10-job-and-lesson-records.adoc` condense the bullet _File Format_ (it names how the front matter is written, which corrects "YAML restricted to the JSON data model" to what the code does, if the operator confirms the default below) and the validation half of _Remedy_ per R05 (a paragraph that opens "Implemented by `LessonCodec`…", links the developer guide and says "Remaining: …"); the schema block, every anchor (`#lesson-record`, `#lesson-claim`) and the Job Record block stay byte-stable; the watch items are not touched by this plan. `doc/Traceability.adoc`: `Implementation:`, `Verified by:`, `Documentation:` of `PM-IMPL-2` for the sections implemented. Done when: the guide section and both ADRs exist; the reduction leaves no anchor dangling (links check 0 broken); the lesson block compares equal to its `origin/main` version byte for byte.

**Amendment 2026-10-29 (operator walk, decision 21, PM-WATCH-STORE-56, option 1).** The members `golden_label` and `labelled_at`, `audience_override` and `tool_version_override` are CUT: they leave the lesson schema block of `store-schemas/10` (and the sentences that describe them), the closed enumeration `golden_label`, the nested records "audience override" and "tool version override", `LessonFrontMatter`, the codec and the parity test, in this plan's pull request. Spec and code change together here because `LessonFrontMatterSchemaParityTest` is created by this plan (nothing pins the block before it). Deliverable 2 and 3 are read without those members. The two recovery acts stay in the model and are NOT cut: retiring a `present_unreadable` lesson and releasing a stale claim (`pm-operator lesson release` exists in the verb table); they are built as the first items of the package `lesson-lifecycle`. This plan also deletes PM-WATCH-STORE-56 from `doc/implementation-watch/store-schemas.adoc` (the item says the implementing plan decides and deletes it) and records the result in the specification.

**Done when:** `LessonId`, `LessonFrontMatter`, `Lesson` and `LessonCodec` exist; a lesson file round-trips; every unreadable file is a value, not an exception; the parity test pins the record to the schema; the package file and its row exist with three dependants; the second plan (`lesson-store-operations`) can start without a question about the format. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-core` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed and new classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) with the absolute paths of the sibling checkout, against the documentation checkout at its `origin/main` (fetch it first; the checkout may lag): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository):

- Requirements: PM-IMPL-2 (versioned JSON storage; the narrative stays Markdown, only the front matter is structured); PM-IMPL-7 (the sidecar and journal the second plan uses); PM-TEST-1 and PM-TEST-2 (a Ported store, its legacy mapping in test code only)
- Specification: Store Schemas § Lesson Record (Placement, File Format, Revisions, Derived from Untrusted Text, Remedy, the `LessonFrontMatter` schema); Identifiers § `lesson_id`
- Roadmap: Milestone 5, the item "Port the lesson store's storage invariants (tests derived from the original) on the redesigned lesson record" (read in the sibling checkout, which lags `origin/main`: re-read the item at the outline)

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **R01 and R08 are replaced for this plan.** The plan creates the package and finishes only its first half, so its pull request does NOT delete `doc/plans/lesson-store.adoc` and does NOT remove the row; it adds them (Deliverable 1) and adds the package name to three _Depends on_ cells, which R08 would forbid. The second plan deletes the file and the row. Everything else of R01 (branch, no draft pull request claiming the package, the trace matrix before code, the green builds) applies.
- **Default made here, to be confirmed by the operator before the plan starts:** the front matter is a JSON object, no YAML library (Deliverable 3, ADR (a)). If the operator wants a YAML library or a block-YAML reader, stop at the outline and report: it adds a dependency or a class and changes the file set (R07).
- **No existing class is edited.** Every Java file of this plan is new; `core.store` is shared ground, so the plan adds files and changes none (R19). If the trace matrix shows an existing class must change, record it in the first progress note and in the landing.
- **Log messages: none expected.** If the trace matrix shows one, it goes into `CoreLogMessages` at the next free identifier of its level (ADR `doc/adr/005-one-log-message-holder-per-module.adoc`) and the plan says so in its inbox message.
- Nothing is built here that needs the lock, the journal or the sidecar: no store file is written by this plan's production code. Those are the second plan's.

## Package File

### `doc/plans/lesson-store.adoc`

````asciidoc
= The project's lesson store
:nofooter:

link:README.adoc[Work packages of `pm-mcp-core`] » `lesson-store`

[horizontal]
Serves:: link:../../../plan-marshall-documentation/doc/roadmap.adoc[Roadmap] Milestone 5 (roadmap item _Port the lesson store's storage invariants_) and the lesson source of the plan bootstrap of Milestone 7 (`PM-WF-10`)
Where:: pm-core (package `core.store`: the lesson record, its file format, and the store under the lesson store lock)
Status and dependencies:: in the link:README.adoc#packages[index], the only place that states them

== What

* *Implements* link:../specification/store-schemas/10-job-and-lesson-records.adoc#lesson-record[Store Schemas § Lesson Record]: the project's lesson store `<PM_MCP_BASE>/projects/<project_id>/lessons/`, one Markdown file `<lesson_id>.md` per lesson with its front matter. It is the store of the PM-MCP engine; the lesson store of `plan-marshall` (`manage-lessons`) is external and never read (`PM-MIG-2`).
* *The record and the file*: `LessonId`, the enumerations, `LessonFrontMatter` and `Lesson`, the codec of the lesson file (front matter as a JSON object between two `---` lines, strict, format version 1), and the parity test against the schema block of the specification.
* *The store*: lesson id allocation (UTC hour plus a per-hour sequence) and every write as one journaled transaction under the lesson store lock, with the digest sidecar `.digests.json`; the one resolver with its four states and the three integrity states (`store_tampered`, `deleted_out_of_band`, `untracked`); the revision of a body; the retirement with its closed verdicts; the claim of a lesson by a plan, its release, and its retirement `applied` when the plan lands.
* *Left out* (cut as `lesson-lifecycle` when this package closes): the `submit` transition that writes a lesson (Phase Workflows § Lesson Creation & Body), the audience predicate and the upstream release, the `arch-constraint` recurrence, the lesson maintenance (merge, supersede, retire submits), the retention sweeps, the operator acts on a lesson (`accept-edit`, retiring a `present_unreadable` lesson, releasing a stale claim; link:../implementation-watch/store-schemas.adoc#PM-WATCH-STORE-56[PM-WATCH-STORE-56]), the `stalled_lessons` population, and the calls of `start.create-scope`, the plan end and the recovery, which are `start-source-intake`'s and `plan-and-epic-control`'s.

== Trace

Requirements stay in `plan-marshall-documentation`; the specification sections and watch documents are those of this repository. They are the starting set; the trace matrix of `traced-implementation` is built from them before code (link:README.adoc#how[How to work a package]).

* Requirements: link:../../../plan-marshall-documentation/doc/Requirements.adoc#PM-IMPL-2[PM-IMPL-2]; link:../../../plan-marshall-documentation/doc/Requirements.adoc#PM-IMPL-7[PM-IMPL-7]; link:../../../plan-marshall-documentation/doc/Requirements.adoc#PM-TEST-2[PM-TEST-2]
* Specification: link:../specification/store-schemas/10-job-and-lesson-records.adoc#lesson-record[Store Schemas § Lesson Record]; link:../specification/store-schemas/10-job-and-lesson-records.adoc#lesson-claim[§ A Claimed Lesson Ends with the Landing]; link:../specification/store-schemas/13-leases-integrity-testing.adoc[Leases, Integrity & Testing] (the lesson store's digest sidecar and lock)
* Watch: link:../implementation-watch/store-schemas.adoc[Store Schemas] (PM-WATCH-STORE-14, -17, -20, -58 and the open -56)

== Done When

A lesson is created, listed, read, revised, retired, claimed and released through the store; every lesson in the store resolves by the id it is listed under to exactly one of `found`, `absent`, `present_unreadable`, `empty_shell`, and a digest mismatch, a vanished file and an untracked file are reported as `store_tampered`, `deleted_out_of_band` and `untracked`, never as `absent`; two concurrent writers never allocate the same id and never claim the same lesson; a write stopped at any step of the transaction leaves the old or the new lesson, never a torn one; the front matter of the codec is the schema of the specification, pinned by a parity test.
````

## Index Row

Append to the table of `doc/plans/README.adoc` (after the last row), then add `link:lesson-store.adoc[`lesson-store`]` to the _Depends on_ cell of the rows `start-source-intake`, `start-from-clients` and `plan-and-epic-control`:

```text
|link:lesson-store.adoc[`lesson-store`]
|The project's lesson store: the lesson record and its file format, the store with its id allocation, resolver, revision, retirement and claim under the lesson store lock
|`open`
|nothing
|Milestones 5 and 7
```

## Claim Labels

- OBSERVED: no record type, codec or store of a lesson exists in the three modules: the only code that names the lesson store is `LockKey.lessonStore` (`pm-core/src/main/java/de/planmarshall/core/store/LockKey.java` lines 117 to 120), `LockLevel.LESSON_STORE` (`LockLevel.java` lines 31 to 32), `JournalScopeType.LESSONS` (`JournalScopeType.java` lines 34 to 35) and `JournalScope.lessons` (`JournalScope.java` lines 136 to 139); every other hit of `grep -ri lesson` over `src/main` is a guard that refuses the lesson scope (`MetricsStore.java` 55 to 63, `ChangeLedger.java` 40 to 48, `WorkflowStatusStore.java` 40 to 49, `WorkflowStatus.java` 120 to 130, `QuestionLog.java` 186 to 193, `WorkflowEngine.java` 565 and 628, `EntityAddress.java` 40 to 48), the finding source kind (`Finding.java` 408 to 429), the entity kind `lessons-review` (`EntityKindRegistry.java` 82 to 96), the step id `default:lessons-capture` (`composition-rules.json` line 63) or a Javadoc sentence — read on `origin/main` (`d95641dc`, code identical to `a8da4104`)
- OBSERVED: no production code calls `JournalScope.lessons`; only tests do (`JournalScopeTest.java` line 114, `WorkflowStatusStoreTest.java` line 162 and others), and `JournalRollForwardStep.ProjectScopes` (`pm-runtime/src/main/java/de/planmarshall/runtime/start/JournalRollForwardStep.java` lines 41 to 59) is a seam whose supplier "the per-project machine store package fills" — so the lesson scope is not yet supplied to recovery; that wiring is not this package's
- OBSERVED: the schema block of the lesson front matter is lines 236 to 379 of `doc/specification/store-schemas/10-job-and-lesson-records.adoc`, after the heading `== Lesson Record (project lesson store)` at line 225; the bullets _Placement_, _Lock_, _File Format_, _Revisions_ and _Lesson-Store Digest_ are lines 230 to 234, the bullets from _One Resolver, Four States_ to _Promotion Provenance_ lines 381 to 398, _A Claimed Lesson Ends with the Landing_ lines 401 to 407
- OBSERVED: the parity pattern to follow is `JobRecordSchemaParityTest` (`pm-core/src/test/java/de/planmarshall/core/store/JobRecordSchemaParityTest.java` lines 52 to 62: `SpecificationDocument.jsonBlockAfter("store-schemas/10-job-and-lesson-records.adoc", "== Job Record")`, member lists published by the codec as constants); `SpecificationDocument.jsonBlockAfter` is at `pm-core/src/test/java/de/planmarshall/core/SpecificationDocument.java` line 72 and takes the first `[source,json]` block after a line that starts with the marker
- OBSERVED: `StrictJson.read` and its field helpers are public API of `core.store` for the codec of any store (`StrictJson.java` lines 47 to 52, `read` at line 127); `JobRecordCodec` and `PendingEntryCodec` are the codecs to imitate; no YAML library is on the classpath of any module (`grep -ri yaml` over the POMs finds nothing; `UnitFrontmatterReader` in `pm-workflow` is a subset reader of its own)
- OBSERVED: the repository already pins specification blocks by test and runs the build for a specification-only pull request: `.github/project.yml` line 31 `skip-on-docs-only: false` (PLAN-104, #185)
- OBSERVED: the roadmap has an item for it in Milestone 5 ("Port the lesson store's storage invariants …", `doc/roadmap.adoc` line 350 of the documentation checkout at `fe91f42`, which lags `origin/main` `24eabd4e`; not re-read at the head)
- OBSERVED: the plans that need the store name the gap themselves: PLAN-48 Re-grounding Record of 2026-10-25 and 2026-10-26 ("The lesson record store has no implementation: only LockKey.lessonStore exists", "Owner of the lesson store (reader, writer, retire verb, claim, remedy, `derived_from_untrusted`)"), PLAN-76 Deliverable 8, PLAN-51 Deliverable 3 and its open question (D3 "built by start-source-intake")
- HYPOTHESIS: the new classes are named `LessonId`, `LessonFrontMatter`, `Lesson` and `LessonCodec` and live in `core.store` — confirm/refute at the trace matrix; the names are the spec text's (`LessonFrontMatter` is the schema title), the package is decided in ADR (b)
- HYPOTHESIS: a CRLF lesson file is refused as `Unreadable` — confirm/refute at the ADR (a); either answer is acceptable if pinned by a fixture
- Verify-first clause: before scoping, list the open pull requests for one that names `lesson-store` or adds a `Lesson*` class to `core.store`; re-read `doc/specification/store-schemas/10-job-and-lesson-records.adoc` at the head (the documentation session keeps merging specification pull requests; the block may have changed since 236 to 379); check that `doc/plans/lesson-store.adoc` does not exist; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `doc/specification/store-schemas/10-job-and-lesson-records.adoc` — specification part; the bullets _File Format_ and the validation half of _Remedy_ condensed; the lesson schema block and the Job Record block stay byte-stable (R10)
- HYPOTHESIS: `doc/plans/lesson-store.adoc` — the package file, new; kept by this plan, deleted by PLAN-150 (`lesson-store-operations`)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/LessonId.java` — new; the id grammar and the file name
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/LessonFrontMatter.java` — new; the record tree and the enumerations
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/Lesson.java` — new; front matter with body
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/store/LessonCodec.java` — new; the file format
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/LessonIdTest.java` — new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/LessonFrontMatterTest.java` — new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/LessonCodecTest.java` — new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/LessonFrontMatterSchemaParityTest.java` — new
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/LessonFixtures.java` — new test helper, if the text of Deliverable 2 and 3 implies one for the second plan to reuse

Shared bookkeeping, not surface (R19): `doc/plans/README.adoc` (the new row and three cells), `doc/Traceability.adoc`, the developer guide `pm-core/doc/developer/core-store.adoc` (additive section) and the ADR directory `pm-core/doc/adr/core-store/` (both exist).

## Dependencies and Sequencing

- Depends on: nothing live. `JournalScope.lessons`, `LockKey.lessonStore` and `StrictJson` are shipped; no running or staged plan creates a lesson class.
- Blocks: PLAN-150 (`lesson-store-operations`) (the second plan of the package); through it PLAN-48 (`start-from-clients`), PLAN-76 (`start-source-intake`) and PLAN-51 (`plan-and-epic-control`), which add `Depends on` the package (the orchestrator records it on their rows and specs; Deliverable 1 does it in the index).
- Overlaps with: no Java file of any live or staged plan (all Java entries are new files; `core.store` is shared ground and no plan declares a `Lesson*` class). The specification part `store-schemas/10-job-and-lesson-records.adoc` is declared by PLAN-48, PLAN-51 and PLAN-76 (staged, they wait for the package) and, in the Job Record region only, by PLAN-119 and PLAN-140 (staged; a different region of the file, byte-disjoint hunks): the lesson block is the part this plan pins. PLAN-46, PLAN-105, PLAN-108 and PLAN-109 share no file with this plan (PLAN-105 adds `PlanHistory*` classes to `core.store` and an ADR to `core-store/`: renumber, R11).
- Adjacent to: the engine's recovery (`pm-runtime` `JournalRollForwardStep.ProjectScopes`), which does not yet supply the lesson scope and stays untouched; `plan-marshall`'s own lesson store, external and never read; the roles and the knowledge search that will read lessons, later.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-149-lesson-record-and-codec.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
