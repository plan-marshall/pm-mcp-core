# Landing Analysis: PLAN-V2-116 — The schema blocks of seven store-schema documents are files, and the parity tests read them

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #323 (squash merge commit `81b023dd`, first parent `46b144e7`; 59 files, +2811/-2343; plan-marshall lane, harness claude, `claude-sonnet-5-5`); the split-off import-sort commit is #324 (`e3a3ccc4`, 141 files)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-116.md`. A pasted claim is a lead, never a fact.

Source: the plan's landing message `plan-v2-116-schema-files-tested-records-001.md` (2026-11-09T08:21Z), filed by `emit-landing`. `landing-check` `complete: false`: `effort`, `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled` and `sonar_open_new_issues` are missing (folded into the standing Open Defect for incomplete landings). `total_tokens` is 13 907 968, `total_wall_seconds` 146 580, `effort` `unknown`.

Corroborated by the orchestrator: `ci pr view 323` reads `merged`, merge commit `81b023dd1a8a…`, head `feature/plan-v2-116-schema-files-tested-records`; `git show --stat 81b023dd` reads 59 files, +2811/-2343. **The narrative's "#323 carries 58 files" is off by one** (199 − 141 = 58; the merge commit has 59). **The narrative's parent `e3a3ccc4` is an ancestor of the first parent `46b144e7`, not the parent itself** (cosmetic). `e3a3ccc4` was read by the leaf: 141 files, +141/−282, every changed line is the move of `import org.jspecify.annotations.Nullable;` into sorted position, so it is import order and nothing else.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 move the 27 blocks | shipped-modified | 27 `*.schema.json` files: 15 in `pm-core` `core/store/`, 4 `core/findings/`, 2 `core/queue/`, 1 `core/analysis/`, 3 `core/role/qualification/`, 2 in `pm-workflow` `workflow/plan/`. The seven documents hold 1+2+6+4+2+7+5 = 27 blocks at `81b023dd^1` (counted) and every block is an include now; all 27 include paths resolve. All 27 files were compared with their blocks: text-identical and JSON-equal, each file is the block plus one final LF. The placement of **5 files deviates from the spec's hypothesis** (3 in `role/qualification`, 2 in `pm-workflow`); ADR 018 (operator decision 2026-11-07) settles it. |
| D2 eight tests read the file | shipped-as-specified | All eight load the schema through `SchemaResource` from the class path; none names `SpecificationDocument` or `pm.specification.root` any more (re-read); assertions unchanged apart from mechanical renames; `PendingEntrySchemaParityTest` stays `@Disabled` with its original reason. |
| D3 plan history parity test | shipped-as-specified | `PlanHistorySchemaParityTest` compares members and order, `required`, the events and `format_version` with the codec, `maxItems` with `MAX_CALL_PATH`, validates five written events, and has negative controls (seq − 1, version − 1, six call frames, node length 0, an uppercase outcome, an undeclared member). It can fail. |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, surface `orchestrator.analyze`) — reason: a full-ship landing is read on three layers by its own leaf, with no size qualifier; it shared no context with the other landings of the session. It wrote nothing.
- **Re-read by the orchestrator at `git show 81b023dd:<file>`:** `plan-instance-header.schema.json:90` (15 `prune_reason` values), `PruneReason.java:15-52` (13 constants, Javadoc `:17`), `composition-record.schema.json:19` (10 `fallback_cause` values), `CompositionCause.java:71-100` (9 constants, Javadoc `:73`), `workflow-plan.adoc:29,43`, `pm-core/pom.xml:22,112`, `doc/adr/018-…adoc:86-89`, the file count of the merge, the absence of `SchemaResource.java` from the diff, the list of files that still name `SpecificationDocument` (only `SpecificationDocument.java`), and `PlanHistorySchemaParityTest.java:168-181`. The other points are the leaf's.
- **Verdict:** layer 1 findings — 7 (3 minor refutations, 2 not shown); layer 2 findings — 17 (7 minor); layer 3 findings — 17 (7 minor, 5 not shown). The leaf returned 41 in all (the count includes `info` rows). No blocker and no major.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | "27 blocks in seven documents" (Verify-first) | checked | confirms | counted at `81b023dd^1`: 1+2+6+4+2+7+5. Persisted as `corroborated` (claim 2). |
| L1-2 | HYPOTHESIS: the resource package of each file is the package of its codec, below `pm-core` | checked | **refutes** | 5 of 27 lie in `core/role/qualification/` (3) and `pm-workflow` `workflow/plan/` (2); the Objective ("the modules that own their codecs") contradicts its own Claim Label and the Expected Surface never named these directories. Persisted as `contradicted` (claim 1, `rescoped: no`, the placement is settled by ADR 018). |
| L1-3 | OBSERVED: the eight tests name `SpecificationDocument` | checked | confirms | exactly the eight named, 10 `jsonBlockAfter` calls. Persisted as `corroborated` (claim 0). |
| L1-4 | "each file is the former block's text byte for byte" | checked | confirms | 27 of 27 compared. |
| L1-5 | constraint: a deviation the move reveals "is recorded and reported, never fixed" | checked | refutes | cannot be enforced for the 16 files no test reads (L2-9): the move reveals nothing there, and two real deviations sit unseen (L3-2, L3-3). |
| L1-6 | Done-when: the first migrated document is opened on GitHub and the include renders as a link | not_shown_by_reading | none | a GitHub-side observation; the PR body states it, the repository does not show it. |
| L1-7 | Done-when: `verify`, `verify -Ppre-commit`, `verify -Pcoverage` green; the parity tests that passed before pass after | not_shown_by_reading | none | no run is allowed in this analysis; CI on the PR is the evidence below. |

**Layer 1 verdict:** findings — 7 (L1-2 and L1-5 refute a premise or a constraint; none blocks the shipped work).

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | the 27 files, the 7 documents | n/a (a move) | `DerivedFactsCodec.java:67`, `LessonCodec.java:89`: Javadoc lines only ("schema block" → "schema file"), a consequence of D1 | checked, confirms |
| D2 | the eight tests | the eight tests, unchanged assertions | `SpecificationDocument.object/array` now delegate to `SchemaResource` (`:111-131`) and the class has **no caller left** in any module (`jsonBlockAfter`, `root`, `parse`, `object`, `array` dead); kept for `adoc-reading-retired` (PLAN-V2-122) | checked, confirms |
| D2 (cont.) | narrative: "the two `fail()` casts moved to `SchemaResource`, covered by `SchemaResourceTest`" | `SchemaResourceTest.Casts` (4 tests: `assertSame` and `assertThrows(AssertionFailedError)`, `:105-165`) | `SchemaResource.java` is **not in the diff**; its casts existed since PLAN-V2-115. What changed: `SpecificationDocument` delegates, and `SchemaResourceTest` gained the `Casts` tests. The coverage claim holds; nothing moved. | checked, refutes (wording) |
| D3 | `PlanHistorySchemaParityTest` | see above | the outcome-pattern probe covers capitals only (`notWord = word.toUpperCase`, `:177`); the schema pattern string is never compared with `PlanHistoryLine.OUTCOME_PATTERN` (private, `:48`). A codec pattern loosened to accept `1a`, `_x` or `a-b` passes. `core-store.adoc:2468` says every bound is proved "with the value on the bound accepted and the one beyond it refused", including the outcome pattern, which is more than the test proves. | checked, refutes (minor) |
| plan | 16 of the 27 files are read by no test and no code | none | assessment-record, review-bot-registry, review-participation, decision-calibration-record, judge-check-record, qualification-record, build-timings, fact-cost-calibration, fact-module-discovery, failure-envelope, gap-record, owed-item, plan-overrides, policy-counter, composition-record, plan-instance-header. In scope (a move only), but the Done-when "every block is an include of a schema file" is met with no parity guard. The leaf found no duplicate member, no `required` outside `properties` and no unresolved `$ref` in them. | checked |
| plan | disabled order checks | `FindingSchemaParityTest.componentsInSchemaOrder` (until `security-audit-and-code-scanning`), `WorkflowStatusSchemaParityTest.membersEqualSchema` (until `process-and-overview`) and the whole `PendingEntrySchemaParityTest` are `@Disabled`; this predates the landing | the member order of finding-record and status, and the whole pending-entry schema, are guarded by no running test | checked |
| plan | files the diff touched that no deliverable names | — | `doc/plans/schema-files-other-specifications.adoc` (another package's Tasks rewritten to cite ADR 018, its "block whose codec does not exist" rule changed, the link to the deleted plan file dropped): **undeclared surface**, a consequence of ADR 018; `doc/Traceability.adoc` (additive; nothing records the 27 files); the guides, README, ADR 018 and its index row (declared bookkeeping). | checked |

Not shown by reading: the green runs the narrative lists, the two reviews closed by operator override at `521454af` (CodeRabbit participated, Sourcery refused on diff size), `verify -Pcoverage`, and whether the `format: date-time` of `history-line.schema.json:10` is asserted by the validator in `writtenLinesAreValid`.

**Layer 2 verdict:** findings — 17 (all minor or info; the largest is that 16 schema files have no reader).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `SchemaResource.text` (`pm-core/src/test/…/SchemaResource.java:53`) | a missing resource, malformed UTF-8, an IO error | read clean: try-with-resources, a named failure for `null`, a strict decoder, `parse` refuses empty, trailing and duplicate-member documents | info |
| L3-2 | `PruneReason.java:17` (re-read) | the Javadoc says "the set equals the `prune_reason` enumeration of the header schema, in its order" | the enum has **13** constants, `plan-instance-header.schema.json:90` lists **15** and adds `no_code_delta` and `no_production_delta`; a header carrying either reason is valid for the schema but refused by the types (`HeaderSchemaCheck` reads its enumerations from the type). No test reads the file, so nothing sees it. | minor |
| L3-3 | `CompositionCause.java:73` (re-read) | the Javadoc says `FallbackCause` equals the `fallback_cause` enumeration, in its order | the enum has **9** constants, `composition-record.schema.json:19` lists **10** and adds `operator_path` (between `merge_by_operator` and `local_only`); a log line with `operator_path` validates against the schema, the type has no constant for it | minor |
| L3-4 | `pm-workflow/doc/developer/workflow-plan.adoc:29,43` (re-read) | "the thirteen prune reasons of the header schema" and "the enumerations equal those of the specification, in its order" | two of the three stated equalities fail (15 vs 13, 10 vs 9); the landing inserted a paragraph directly above the unchanged claim, so it ships next to a false statement. The narrative's "enum-count claims" understates it: the schema admits values the codec refuses. | minor |
| L3-5 | `doc/adr/018-…adoc:86-89` (re-read) | Consequences name `schema-files-machine-stores` as a package that "will place" a codec-less block | that package is already `done` (`doc/plans/README.adoc`); the sentence is stale | minor |
| L3-6 | `pm-core/pom.xml:22,112` (re-read) | the comment says the schema tests read the specification root | after the landing no schema test reads it; the property is still passed to surefire at `:112`; stays until PLAN-V2-122 | minor |
| L3-7 | `PlanHistorySchemaParityTest.java:129` | `undeclaredMemberIsViolation` appends to the written line by substring on the last `}` | valid, the codec writes an object with no trailing LF; no stream is opened by the tests | info |
| L3-8 | `SchemaResourceTest.java:37` | the class Javadoc line is 148 characters (the file's other lines stay within 120) | a style check may rewrite it; whether the gate accepts it is untested | info, not shown |
| L3-9 | JAR contents of `pm-core` and `pm-workflow` | 27 new JSON resources | the nearest rule (no `roles/`, `workflows/`, `bundles/`, `skills/`) does not apply; licence-header, native-image or JAR-content checks need a `verify` run | info, not shown |

**Layer 3 not shown by reading:** the build and test runs, `-Pcoverage` (80 per cent per module for `pm-core` and `pm-workflow`), the `date-time` format assertion, the native-image effect of the new resources.

**Layer 3 verdict:** findings — 17 in the leaf's count (9 rows above, the rest `info` rows folded), all minor or info; no blocker or major defect. **L3-2 to L3-4 are the one substantive finding: the schema files are ahead of the codec by two enumeration values, and no test sees it.**

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md | JUnit 5 only, `@DisplayName`, no Mockito/Hamcrest | conforms | the new and rewritten tests |
| CLAUDE.md | assertion messages of 20 to 60 characters | conforms | the new and rewritten ones (scripted check by the leaf; the mechanical renames keep their messages) |
| CLAUDE.md | generated data, a literal only where it is the contract and said so | conforms | `gate_failed`, `retry2` carry a comment (`:168`) naming the schema pattern as the contract |
| CLAUDE.md | no AAA phase comments, no `Random`/`UUID.randomUUID` | conforms | none in the added test code |
| CLAUDE.md | `package-info.java` with `@NullMarked`; no generic catch | conforms | no new Java package; production Java changes are two Javadoc lines |
| CLAUDE.md | no dependency or plugin added | conforms | no `pom.xml` in the diff |
| CLAUDE.md | no model-facing content in a JAR | conforms | the directories are `de/planmarshall/{core,workflow}/…`, none is `roles/` or `skills/` |
| CLAUDE.md / ADR 016 | schemas are files of the implementing module, read by class-path location | deviates | 25 of 27 lie in the module of their codec; the codec-less ones are placed by ADR 018; 3 in `role/qualification` and 2 in `pm-workflow` differ from the spec's wording and the `pm-workflow` ones are read by no test |
| CLAUDE.md | every public type documented, Javadoc accurate | deviates | `PruneReason.java:17`, `CompositionCause.java:73` state an equality that does not hold |
| Spec Done-when | `verify`, `-Ppre-commit`, `-Pcoverage` | not_shown_by_reading | CI evidence below |

## Metrics and Anomalies

- Tokens: 13 907 968 (landing fact); wall-clock 146 580 s; `effort` `unknown`.
- Anomalies: the pull request was first 199 files and was split (the 141-file import-sort commit went out as #324, label `skip-bot-review`); both reviews (functional and code) were closed by operator override at `521454af` because the same `SpecificationDocument.java` weakened_test finding recurred; Sourcery (optional) refused on diff size; Sonar was not scanned by the lane (no credentials), the PR's SonarCloud check passed; `plan-marshall:plan-retrospective` skipped by the operator.

## Routing and Merge Behavior

- Review: CodeRabbit (required) participated; Sourcery (optional) refused; the two lane reviews were closed by operator override (above).
- CI/merge: merged through the merge queue (`step.branch-cleanup.merge_mechanism=merge_queue`), merge commit `81b023dd`; `cleanup_owed=false`.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#323`
- [x] row `landing` stamped `landings/PLAN-V2-116.md`
- [x] row `plan_marshall_plan_id` stamped `plan-v2-116-schema-files-tested-records` (from the landing's `plan_id`)
- [x] layer-1 verdicts persisted on the spec (claim 0 `corroborated`, claim 1 `contradicted` with `rescoped: no`, claim 2 `corroborated`)
- [x] `epic.md`: findings recorded as one Open Defect (Wa85); the incomplete landing folded into the standing Open Defect
- [x] resume anchor, `queue-view.md`, committed in the ledger worktree

## Follow-Ups

No fix plan is staged; the operator decides. Candidates: (1) correct the schema or the codec for `prune_reason` (+`no_code_delta`, `no_production_delta`) and `fallback_cause` (+`operator_path`), then fix the Javadoc of `PruneReason`, `CompositionCause` and `workflow-plan.adoc:29,43`; (2) a parity test for the 16 unread schema files and for the two `pm-workflow` files (the move reveals no deviation where no test reads the file); (3) the outcome-pattern probe of `PlanHistorySchemaParityTest` (compare the pattern string, probe digits-first and hyphen) and the `core-store.adoc:2468` sentence; (4) ADR 018 Consequences line, `pm-core/pom.xml:22` (retired with PLAN-V2-122). The `SpecificationDocument` class has no caller left and is retired by PLAN-V2-122.
