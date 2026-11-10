# Landing Analysis: PLAN-V2-97 — Optional to @Nullable in pm-core

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #344 (slice A, merge `85ff213bc48e49ea595f8c53fb3397fbc500640f`), #347 (slice B, merge `7545bd26b8d0e3e773e69b51d40f007d55731f56`)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-10 from the runbook-lane
> message `rb-v2-97-optional-conversion-core-001.md` (harness opencode, model deepseek-v4.1-flash/high).
> The message is a lead; every figure below was re-read from git, the CI abstraction or the Sonar public API.
> The text of the two unresolved review threads on #344 is third-party material and was **not** ingested; only
> their locations were handed to the analysis leaf, which read those places on its own.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 fields | shipped-as-specified | `Optional` fields became `@Nullable`; `UsageAggregate.population` stays non-null (`UsageAggregate.java:63`: every construction passed `Optional.of` and `populationOf()` defaults an empty set to `MODEL_JOB`, so the empty state was unreachable); a non-record accessor returns `Optional.ofNullable`. |
| D2 parameters and record components | shipped-modified | Converted, with `EffectiveRole.idleWakeups` as `OptionalInt` (ADR 022, `EffectiveRole.java:48`). Test-first was not observable: a type-only conversion has no absent-case test that fails before the change, and the diffs show mechanical rewrites of existing tests; the narrative discloses it. `HarnessProfile.askUserTool` became non-null and `RoleAssignmentValue.java:55` gained a refusal the spec did not ask for (L3-1). |
| D3 callers, ports, owed list | shipped-modified | Callers adapted in `pm-workflow` and `pm-runtime`. The sibling-repository `git grep` could not be run (checkouts absent); the PR bodies carry the signature list, not read here. |

Facts block: `deliverables_total=3`, `deliverables_done=3`; stored formats: no schema, fixture or `FORMAT_VERSION` file is in either diffstat.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), reason: 122 files in two merges, with a null-flow question at every converted codec and store; the read must not share context with the 99 and 128 reads of the same drain. The leaf was also asked to read two named locations (`pm-core/doc/developer/core-role.adoc:272` and `LaunchComposerTest.java:180`) on its own, without third-party text.
- **Verdict:** layer 1 findings (minor); layer 2 findings (minor); layer 3 findings (minor), no NPE or stored-format change found by reading.
- Points re-read by the orchestrator before recording: `RoleAssignmentValue.java:48-58`, `EffectiveRole.java:46-58`, `IntentCodec.java:626`, `QuestionRecord.java:76-80, 414-422`, `LaunchComposerTest.java:176-195`, `ConsentGateTest.java:78, 89`, `UsageAggregate.java:63-64`, `JsonlStoreFile.java:133-135`, `EffectiveRole.java:48`, ADR 021 line 20, the ADR directory listing (019, 021, 022, no 020), `core-role.adoc:272-273`. All held. The leaf's count of 26 Done-when hits was re-counted at 25 with the narrow regex `private (final )?Optional<`; the narrative's 23 is an undercount either way. Not re-read: the remaining codec read-path points, recorded as the leaf's reading.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | The Done-when regex still finds 25 to 26 lines in 13 files at `HEAD`, all false positives (private methods returning `Optional`, one `Function` record component); it is blind to `OptionalInt`/`OptionalLong`/`OptionalDouble`, nested generics and a component split over lines. | checked | refutes | `pm-core/src/main/java` |
| L1-2 | The spec header says "one shippable unit" and one row, yet its constraint says split below 100 files from the start without a cut; the lane had to ask the operator (64 + 59 files). | checked | refutes | spec header and Standing Constraints |
| L1-3 | The PLAN-V2-110 C4 carry on `QuestionRecord` is still unmet: `questionClass`, `origin`, `sourceEntity`, `questionSet` are unannotated yet `onlyOn(…ASKED…)` lets them be null on non-ASKED events (`QuestionRecord.java:78-79, 416-421`); the narrative's Residue is accurate. | checked | confirms | `QuestionRecord.java:78` |
| L1-4 | ADR 021 Context says an `OptionalInt` "is not a field, a parameter or a record component", but `OptionalInt`/`OptionalLong` are record components; the Decision section does not decide them, and ADR 022 covers only `EffectiveRole.idleWakeups`. | checked | refutes | ADR 021 line 20 |
| L1-5 | "Tests first" cannot be satisfied literally for a type-only conversion. | checked | refutes | spec D2 |

**Layer 1 verdict:** findings — 4 minor, 1 info.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|-----------|--------------------|----------------------------------|---------|
| D1 | `UsageAggregate.java:63`, `ConsentStore`, `EffectiveRoleFacts` | `ConsentGateTest`, `EffectiveRoleFactsTest`, `PrimitiveResultTest` | the `Tally` extraction in `UsageAggregate` (S3776 fix) is explained only by the narrative | checked |
| D2 | codecs and ports | `LaunchComposerTest.java:176-195` (value, artifact-over-profile precedence, absence), `HarnessUsageEndpointTest` | — | checked |
| D3 | `LaunchComposer.java:362` (`orElse(0)`) | — | owed list not judgeable (PR bodies not read; checkouts absent) | checked |

- **L2-1 (minor):** "No `Optional` field, parameter or record component remains in `pm-core/src/main`" is true for `Optional<T>` and false if `OptionalInt`/`OptionalLong` count: `ProfileStages.java:139`, `WorkerParameters.java:43`, `HarnessUsage.java:126`, `JsonlStoreFile.java:135`, `PlanHistory.java:130`, `QuestionLog.java:103`, `WaiverConsumptionLog.java:53`, `WaiverLog.java:59`, `TransactionJournal.java:134`, `UsageAggregate.java:63-64`, `MetricsCodec.java:533, 643`, and the new `EffectiveRole.java:48` (pre-existing, untouched, only one sanctioned by ADR 022).
- **L2-2 (minor):** the narrative's "23 remaining hits" undercounts (25 to 26 at `HEAD`); the files it omits are `CoberturaReportParser.java:154, 159` and `JacocoReportParser.java:165`, private methods merged in #336 before this landing.
- **L2-3 (minor):** the narrative says the stored formats are "pinned by" `ConsentCodecTest.roundTrip`, the parity tests, `RoleAssignmentsStoreTest`, `MetricsStoreTest`, `IntentRecordTest`; the diffs change constructor arguments only and a round trip with schema validation does not compare bytes (`ConsentCodecTest.java:119`). By reading, the write paths are unchanged (layer 3). Whether the schemas reject a stray null member is **not shown by reading**.
- **L2-4 (minor):** `RoleAssignmentsCodec.java:219` keeps a local `Optional<EffortValue>` while its sibling `assignment()` converted its locals; test helpers keep `Optional` parameters (`RoleDigestTest.java:175`, `RoleValidationTableTest`, `RoleAssignmentValidationTest.java:105`).
- **L2-5 (minor):** `IntentCodec.java:626` `required(Optional<T>)` became `required(@Nullable T)` and 14 call sites in `IntentCodec`/`OutlineCodec` (`OutlineCodec.java:333, 372, 374, 413, 426, 492`) now write `fromWireName(…).orElse(null)`: absence goes `Optional` → `null` → throw, the round trip the spec's constraint 2 warns against; some lines exceed 120 characters.
- **L2-6 (minor, ADR coverage):** ADR 021 and 022 exist as claimed; ADR 020 does not (the index jumps 019 → 021); the design decisions with no ADR are `UsageAggregate.population` non-null, `askUserTool` non-null, the new refusal in `RoleAssignmentValue`, and the removal of `ToonObject.Builder.add(String, Optional)`. Whether 020 is reserved by an open branch is not shown.

**Layer 2 verdict:** findings — 6 minor, 1 not shown by reading.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `RoleAssignmentValue.java:55` | effort `VALUE` with an `EffortValue` whose `value()` is null | now throws `IllegalArgumentException` ("A named effort value must not be null"); before it was accepted and `RoleAssignmentsCodec` wrote `effort:null`, which read back as `DEFAULT`. No NPE (dereference guarded at line 51); it turns a silent corruption into a refusal, but it is a behaviour change against "same behaviour as `Optional.empty()`" and can break a sibling-repository caller. No test pins it. | minor |
| L3-2 | `EffectiveRole.java:54-58` | a caller keeping the old "null means unset" convention passes a null `idleWakeups` | the canonical constructor has no `requireNonNull(idleWakeups)`; the NPE arrives later at `LaunchComposer.java:362` (`role.idleWakeups().orElse(0)`), not at construction. | minor |
| L3-3 | `ToonValue.java:188` | `add(key, null)` with a bare null literal | ambiguous overload at compile time in sibling callers (the test casts it); output for null and the former empty is identical. | info |
| L3-4 | `RoleCatalog.java:207-209` | — | `requireNonNull` replaces `orElseThrow` (an NPE instead of `NoSuchElementException`); `base()` always supplies the three values, so unreachable. | info |
| L3-5 | `LaunchComposerTest.java:180` (checked location) | — | the assertion is meaningful and its message is 57 characters, but it was added to the unrelated test `tokenAndRecord` (a failure there is mislabelled), and the `3` is a literal at `LaunchFixtures.java:137` and in the test although the class has `@EnableGeneratorController`. | minor |
| L3-6 | `pm-core/doc/developer/core-role.adoc:272-273` (checked location) | — | lines 272-273 match the merged code of `85ff213b`; gaps: `QualificationCheck.Verdict.qualifiedForm` and the `RoleValidation.check` parameters are `@Nullable` but unlisted, `Facts.catalogue` is a config-package type that belongs in `core-config.adoc`, line 273 is one ~700-character line against the file's 120-column wrap, and the section repeats the Invariants bullet at 230-233. | minor |

Read and correct by reading: `ConsentCodec` (126-143, 206-217), `RoleAssignmentsCodec` (126-142, 195-199, 231), `MetricsCodec` (234-240, 552-554), `ConsentStore`, `PrimitiveResult` (151, guarded by the pairing check at 144), `HarnessUsage.sum`, `ScopeIntegrity`, `CorpusValidator`. A null maps to the absent member exactly as `Optional.empty()` did; same members written, none added or dropped; record `equals`/`hashCode` compare components, only the `toString` text changes. No dereference of a nullable found.

**Layer 3 not shown by reading:** whether the schemas reject a stray null member, the build, coverage and Sonar state, and the sibling-repository consumers.

**Layer 3 verdict:** findings — 4 minor, 2 info; no NPE and no stored-format change found by reading.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | `@NullMarked` on every production `package-info` | conforms | `git grep -L @NullMarked` over `pm-core` `package-info.java` returns nothing |
| CLAUDE.md Code Standards | `Optional` only as a return type | deviates | `OptionalInt`/`OptionalLong` components (L2-1), the local at `RoleAssignmentsCodec.java:219`, the test helpers |
| CLAUDE.md Code Standards | no generic catch or throw | conforms | no hit in `pm-core/src/main` at `7545bd26` |
| CLAUDE.md Code Standards | `LogRecord` constants asserted with `LogAsserts` | conforms | neither diff touches a holder |
| CLAUDE.md Code Standards | assertion messages of 20 to 60 characters | deviates | `ConsentGateTest:78, 89, 104, 117, 220`, `RoleEffectiveConfigurationTest:219`, `RoleAssignmentsStoreTest:106, 169`, one 15-character message in `EffectiveRoleFactsTest` |
| CLAUDE.md Code Standards | test data from the generators | deviates | the literal `3` (L3-5); `ConsentSchemaParityTest.java:107` a literal device id (pre-existing style) |
| CLAUDE.md Code Standards | no new hand-written fake | conforms | only a fixture method added |
| CLAUDE.md Module Documentation | one ADR per design decision | deviates | L2-6 |

## Metrics and Anomalies

- Tokens: `unknown` (opencode measured none; unobserved, not zero). `plugin_version` `unknown`.
- Duration: not supplied.
- Anomalies: the host `/tmp` tmpfs filled during `verify -Pcoverage` (1060 environment-caused failures, two retries; the coverage bound was raised to 1800 s); the 122-file change was split into two pull requests on the operator's choice; two `push --force-with-lease` calls were issued on fast-forward updates (the lane forbids forced pushes; recorded by the lane as a deviation); slice B was built with shell `cp` of the changed paths.

## Routing and Merge Behavior

- Review: **the landing's review numbers are contradicted by the provider.** The landing claims `unresolved_review_threads=0`, `review_body_findings_total=3`, `review_body_findings_handled=3`. `runbook_lane review-gate` on the merged heads reads: #344 `unresolved_review_threads: 2`, `review_body_findings_total: 4`, `review_body_findings_handled: 0`; #347 `0`, `1`, `0`. The two unresolved threads on #344 are CodeRabbit threads on `pm-core/doc/developer/core-role.adoc:272` and `pm-runtime/src/test/java/de/planmarshall/runtime/launch/LaunchComposerTest.java:180`, opened 2026-11-09T21:29:37Z on commit `526ace44` (a later head than the ones the lane's own threads were resolved on, `89292829`), together with a second review body of the same minute. The lane's progress file records only two `finding:` lines (both threads resolved on the first head). **False claim**: the merge ran under the standing review authorization, which requires every finding handled, while the second review's findings were never dispositioned. `review_body_findings_handled` is not reproducible by the provider and is **not re-verified**: the two `finding:` lines cover two of the five findings the provider shows.
- CI/merge: #344 and #347 merged through the queue; queue state read from each PR: `pr_state: merged`.
- Sonar: the landing claims `sonar_open_new_issues=n/a` ("a project without Sonar") and the narrative reports "0 open new-code issues". The public API read gives #347 0 open issues and #344 **1 open issue** (`java:S5778`, MAJOR, `RoleAssignmentsStoreTest.java:153`, created 2026-11-09T20:09:14Z), gate OK on both. The `n/a` is wrong for a project with SonarCloud, and "0 open" is contradicted for #344: the lane named S5778 among the issues it fixed on #344.
- Surface collisions: none reported; the concurrently running 99 touched `pm-runtime` and the providers, 97's callers in `pm-runtime` were adapted in the same files family without a reported conflict.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#344, #347`
- [x] row `landing` stamped `landings/PLAN-V2-97.md`
- [x] row `plan_marshall_plan_id` stamped `v2-97-optional-conversion-core`
- [x] `epic.md`: the Open Defect for missing landing facts counts this landing; new Open Defect "the landing of PLAN-V2-97 claimed no unresolved review threads and no Sonar issue; the provider shows two unresolved threads and one issue on #344"; the Optional-conversion residue entry
- [x] folds: L2-3, L2-4, L3-1, L3-2 tests and L3-5 into PLAN-V2-125 and 126; the assertion messages are owned by PLAN-V2-101 and 102
- [x] resume anchor and `queue-view.md` regenerated

## Follow-Ups

- L2-4 (test helpers), L2-3 (the byte pin) and the two missing refusal tests (L3-1, L3-2): folded into PLAN-V2-125; L3-5 into PLAN-V2-126; both Expected Surfaces extended in the same edit.
- The two unresolved threads on #344 and the S5778 issue (`RoleAssignmentsStoreTest.java:153`) are open on a merged pull request: the Sonar issue is a candidate for the residue plan below; the thread text still needs the extraction route (reader leaf and `validate_struct`) before anything is written from it.
- L1-3 (`QuestionRecord`), L2-1 (the `OptionalInt`/`OptionalLong` components and ADR 021's silence), L2-5 (`IntentCodec` round trip), L2-6 (missing ADRs), L3-1, L3-2, L3-6 and the S5778 issue: code, documentation and decision residues; recorded as one Open Defect with the PLAN-V2-99 and 128 residues. No plan is staged without the operator's word.
