# Landing Analysis: PLAN-V2-163 — core-and-documentation-statement-residue

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #553 `cb992f27` (1/6, 32 files), #554 `f87a5f98` (2/6, 30 files), #555 `480a43d4` (3/5, 84 files), #556 `1339e53f` (4/6, 35 files), #557 `e8a585f3` (6/6, 9 files), #559 `16264668` (follow-up, 1 file); the single PR #550 (189 files) was closed because the required reviewer CodeRabbit refuses a diff over 100 files

> Landing record, plan-marshall lane (`plan-v2-163-core-and-doc-statement-residue`, harness claude, model `claude-sonnet-5-5`, effort `unknown`, plugin 0.1.2173, 34 475 660 tokens, 96 735 s, standing rules r6). One landing message (`plan-v2-163-core-and-doc-statement-residue-001.md`, 2026-11-22T13:10:29Z, `merge_state=merged`). The message states `deliverables_total=7`; the spec lists 5 (the count is not reconcilable from the PRs, which are cut by module, not by deliverable). `unresolved_review_threads`, `review_body_findings_*` and `sonar_open_new_issues` are `unknown`: the finalize steps `automatic-review`, `sonar-roundtrip` and `branch-cleanup` were written for the closed #550 and closed by hand on the operator's word. Review and Sonar numbers are therefore recorded as not re-verified, not as a difference. Four majors and several minors below; no stored datum changed for existing data.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 ADR and specification statements (13, 17, 18, 21, 37, 39, 57) | shipped-modified | Done in #554 to #557, with residue: 21(a) names `07-loop-back-diagrams-verification.adoc:116` and it still reads `"decisions":[]`; 39(b) left ADR 009's Context in the present tense; the cycle-cap ADR took a number that exists twice (C1). |
| D2 build-log bounds, Cobertura, parser tests (11, 15, 16) | shipped-modified | Done in #553 with differential tests against the ported regex and hostile-line timeouts; 11(c) introduced a regression (C4); 11(b) put `tailOnly` on `TestCounts.Measured` where nothing reads it (C5). |
| D3 guides, Javadoc, small defects (14, 20, 22, 26, 31, 33) | shipped-modified | Done in #553, #555, #556; 20(b) and 20(c) are behaviour changes (reserved role keywords, role names of the envelope), 20(b) with a keyword set that drifted (C2). |
| D4 guard tests, redactor follow-up (23, 24, 43) | shipped-as-specified | `SecretRedactor` reflowed only, `SubprocessJobRunner` Javadoc and constructor call only; 24(d) left lines over 120 columns. |
| D5 overrides, machine writers, landing check (36) | shipped-modified | Done in #555 and #556; 36(a) "decide" was decided as "the landing check applies to `start` only" (C3), with no operator word recorded. |
| Added items 60, 64, 67, 70 | shipped-modified | Done in #555 and #557; 64(a) had already been settled by #551 (`LessonStore` has no `default` arm) and is not credited; 64(b) deleted normative sentences (C7); 67(c) has no outcome. |

Counts: the spec title still reads "20 carried items", the list holds 24 (60, 64, 67, 70 added later), and the PR bodies say 24.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze` surface); no file written, no build run, no `trace.py` run; the spec was passed as data from a file outside the repository. Reason: six PRs of 190 files in four modules and documents are the depth the dispatch rule exists for.
- **Re-read by the orchestrator at `origin/main` (`16264668`):** the two ADR file names of `workflow-verify/041` (both exist), `RoleArtifactValidator.java:76-83` against `ReservedKeywords.java:32-38`, `PlanInstanceComposer.java:143-152`, `JobLog.java:262-282`. **Not re-read, kept as the leaf's claims (unverified leads):** the B-list of behaviour changes beyond those four, the weakened-test list, the C5 and C7 line numbers and the carried-item outcome table, the link scan (66 `.adoc` files, 0 broken targets; the leaf's own script, not `trace.py`).
- **Verdict:** layer 1: findings (settled-before items not credited, a false premise of R15); layer 2: findings (an unborne claim about six removals, three small test losses); layer 3: findings (one design decision taken without the operator, two majors); no blocker.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | 64(a) (`LessonStore.isUntrusted` `default` arm) was settled by #551 before this plan ran | checked | refutes | `LessonStore.java` at main has no `default ->`; #555 adds Javadoc only and does not name #551 |
| L1-2 | 39(b): ADR 009's Status already reads "Superseded by 015; premise retired by 016" | checked | confirms | `doc/adr/009-...adoc:15-24` Context is still present tense; the item asked for the tense change and the plan left the file untouched |
| L1-3 | The spec says R15 binds but "the plan touches no `pm-workflow` class" | checked | refutes | #556 changes 9 `pm-workflow` main classes |

**Layer 1 verdict:** findings — 3 (minor).

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | `RoleArtifactValidator.java:76-83`, `ReservedKeywords.java:32-38`, `core-store.adoc` | tests sample five keywords (`role, state, consult, workflow, from`); no parity test between the two sets | the cycle-cap ADR duplicates a number (C1) | checked |
| D2 | `CoberturaReportParser.java:171-177`, `MavenOutputParser.java:196-198`, `JobLog.java:262-282` | `BuildLogBoundsTest`, `CoberturaReportParserTest`, `windowEndingInItsOnlyLineEndIsKept` passes only because its overshoot exceeds the report length (C4) | `TestCounts.Measured.tailOnly` is set and nothing in this repository reads it | checked |
| D3 | `EnvelopePacker.java`, `ConsultationService.java:94-110`, `WorkerSupervisor.java:305-330` | `ConsultationService` double-replay test | `freeId()` loops forever if its supplier keeps returning taken ids (the Javadoc says so) | checked |
| D5 | `PlanInstanceComposer.java:147`, `PlanOverridesCodec.java` | waiver and replan cases only; nothing pins the `override` or `config_adopted` case | none | checked |

**Unborne claim.** The message says the six accepted weakened-test removals are "all in the PR bodies"; none of the six bodies lists them. From the diffs the leaf found six candidates: `ProfileStagesTest.endpointStagesWithoutClassesRefused`, five `verify(signals)` pins of `ProcessTreeTrackerTest`, `ScenarioReplayRunnerTest.shouldAcceptTheSubmitThatNamesTheReference` (the direct `validateSubmit` calls, the omitted-reference refusal, the asker-status and lease assertions), the uncapped assertions of `UnitGraphCycleScaleTest`, `PlanHistorySchemaParityTest` (hyphenated outcome now accepted), `JudgeCheckTest.tokenSplitRefused`. The last three are replaced by stronger tests; the first three carry a small real loss. Which six the plan means is not shown.

**Layer 2 verdict:** findings — 4 (the weakened-test claim, the three losses, the one-sided boundary test, `tailOnly`).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| C1 | `pm-workflow/doc/adr/workflow-verify/041-the-cycle-enumeration-is-capped.adoc` and `041-the-reserved-words-are-one-set.adoc` | two ADRs numbered 041 in one directory (R11 renumbers against `origin/main`; #551 landed first) | the link `ReservedKeywords.java:24` and `workflow-ast.adoc:16` mean the reserved-words ADR, `WorkflowValidator.java:2958` and `UnitGraph.java:60` mean the cap ADR; `workflow-verify.adoc:480` says "ADRs 001 to 041" while 042 exists | major |
| C2 | `pm-core/.../role/RoleArtifactValidator.java:76-83`, `ShippedRoles.java:133-136` | a role artifact or shipped role file named `phase`, `decide` or `inherit` | the copied set lists them; `ReservedKeywords.ALL` deliberately does not (its Javadoc says they stay usable): the verifier accepts the name, `RoleArtifactValidator` refuses `schema_invalid`, `ShippedRoles` throws `IllegalStateException`; the new Javadoc claims "the set the workflow verifier refuses", false at main; no parity test; 20(b) was ask-at-refine and no operator word is recorded | major |
| C3 | `pm-workflow/.../plan/PlanInstanceComposer.java:147` (`event != CompositionEvent.START`) | an `override` or `config_adopted` event that binds a landing variant (the `LANDING` axis, `PlanOverrides.java:101`) onto a protected base branch | the landing check is skipped for every event but `start`; ADR `workflow-plan/021` records it as a Consequence, which reopens the fail-open path the ADR's own Alternative 3 closes; 36(a) said "decide" and the decision is the operator's (R07), not recorded | major (design) |
| C4 | `pm-runtime/.../job/JobLog.java:271` | a log over the cap by fewer bytes than its first line | the head read is `min(FIRST_LINE_MAX_BYTES, size - MAX_BYTES)`; a first line that does not end inside it is dropped and the reading becomes `Unreported`; before, the whole 16 MiB head was searched | minor |
| C5 | `TestCounts.java:57`, `MavenOutputParser.java:196-198` | a cut Maven log | `tailOnly` is set and read by no code of this repository; `population()` still answers `MEASURED`, so the contradiction check item 11(b) worried about is unchanged; a 5-argument compatibility constructor and a 4-argument `from` overload remain against "no shims" | minor |
| C6 | `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc:116` | the intent example | still `"decisions":[]` although #427 refused `decisions`; item 21(a) named exactly this line | minor |
| C7 | `doc/specification/store-schemas/10-job-and-lesson-records.adoc` (Lock, Claim, Retirement, Maintenance, Derived-from-Untrusted bullets) | the R05 reduction of #555 | requirement sentences were deleted, not pointed to a place that states them: `completely_covered` also requires `covering_clause` and a concrete `covering_input`, whitespace-only counts as missing, `into` set exactly for `merged` and `superseded`; a cited lesson that does not exist reads as no evidence; two concurrent writers never allocate the same `lesson_id`; the `stalled_lessons` harvester wording. `covering_clause` survives only in the schema JSON and ADR 079. Schema blocks and kind tables are byte-stable (R10); item 64(b) asked "one statement plus a link per bullet" | major (spec) |
| C8 | `SchemaValidation.java:25-26`, ADR 028; items 14(d), 16 | 67(c) ("if cheap" sub-items), 14(d), 16's "verify at outline: ProductCorpusValidationTest ran or skipped" | no outcome recorded; no change in the range | minor |

**Behaviour changes beyond a stated item** (all others of the leaf's 16-row list were asked for by the item): B2 `tailOnly` (11(b), with the spec's "a result-type change is the operator's word", not recorded), B5 the reserved role names (C2), B9 (C3), B11 `PlanHistoryLine.OUTCOME_PATTERN` and `history-line.schema.json` now admit a hyphen in an outcome (70(g) "align": the stored line grammar is widened, existing lines stay valid, format version stays 1), B12 `ProfileStages.EndpointVerification` loses its `stages` component and a null `classes` throws `NullPointerException` (18(b) "decide"), B13 `HarnessUsage.fromEndpoint(@Nullable Long, @Nullable Long)` (60(d)), B14 `ProcessTreeTracker(long root)` (named in the PR bodies; B12 and B13 are named in none and not in the landing message). B15 `WorkerSupervisor.startWorker` now also releases the slot on an `Error`. These API changes concern the assembly `plan-marshall-mcp`.

**Layer 3 not shown by reading:** (a) whether #553's seven CodeRabbit comments and the reviews of #554 to #557 were dispositioned, and the Sonar state of #554 to #557; (b) whether the hostile-line tests fail on the old code (no failing-run log id is given); (c) the equivalence of the rewritten regex logic (`executionHeaderRunner`, `findingMatch`, `isSummaryLineAcrossTerminators`) with the ported expressions, which the differential tests evidence; (d) whether the assembly reads `tailOnly`, constructs `EndpointVerification` with `stages`, calls `HarnessUsage.fromEndpoint` or builds `ProcessTreeTracker`; (e) whether any shipped or stored role is named `phase`, `decide` or `inherit`; (f) whether a real log is over the cap by fewer bytes than its first line; (g) the file-system behaviour of the 255-byte slug limit; (h) lead L1: the cycle cap bounds the cycles found, not the depth-first steps (`UnitGraph.java:~395`), so a strongly connected unit with exponentially many index-restricted paths that close few cycles could still stall; (i) lead L2: item 20(a)'s counts (23 classes, 25 literals) and the `{0,31}` sweep of ADR core-id/001 were not found as text in the pm-core docs.

**Layer 3 verdict:** findings — 8 (C1 to C3 and C7 major, C4 to C6 and C8 minor); not shown by reading — 9. No blocker.

### Carried items

Neither the landing message nor any PR body carries a per-item outcome table, which the plan's own Standing Constraint demands. The leaf checked each item against main; items with no outcome sentence anywhere: 14, 16 (second half), 18, 20, 21, 24, 31, 33, 37, 39, 43 (b to d), 57, 64 (c to e), 67 (c to e), 70 (partial). That is a process gap; the code carries the corrections for a to d of most of them. Items whose check found a residue are C1 to C8 above (13, 11, 20, 36, 21, 64, 67, 14, 16, 39).

### Conformance to the Project's Own Rules

- R11 (ADR renumbering against `origin/main`): broken, C1.
- R10 (schema blocks and kind tables byte-stable): held; the bullet prose of `store-schemas/10` lost requirements, C7.
- R13 (lesson format version): no lesson format code touched.
- R17 (links check): never run; the leaf's own scan of the added lines found 0 broken targets in 66 files. It must run from the main checkout.
- No Mockito or Hamcrest in the added code; no generic catch or throw; no new `LogRecord`, so `doc/LogMessages.adoc` needs no change (grepped in the read files only). A few added lines are 121 to 124 bytes (byte count, multi-byte characters inflate it).
- PR size: 32, 30, 84, 35, 9 and 1 files, all under the 100-file bound CodeRabbit enforces.

## Residue

Folded into STAGED plans on the leftovers rule, each surface extended in the same act:

| Residue | Owner |
|---------|-------|
| C1 duplicate ADR number (renumber the cycle-cap ADR to 043 and every link), C2 keyword copy plus a parity test, C3 the landing check of non-`start` events (operator decision R07), C6 the `decisions` example, L1 the cap and the search steps | PLAN-V2-161 item 80 |
| C4 `JobLog` first line | PLAN-V2-162 item 81 |
| C5 `tailOnly`, C7 the deleted requirement sentences, ADR 009 Context (39b), the three small test losses, `freeId()`, lines over 120, the links check, the missing outcomes (14d, 16, 67c) | PLAN-V2-166 (new, staged: no staged plan owns `pm-core` and documentation statements) |
| The assembly hand-off: B2, B12, B13, B14, B15 and the history-line grammar B11 | Watch recorded in `epic.md` (the assembly is a sibling repository) |
