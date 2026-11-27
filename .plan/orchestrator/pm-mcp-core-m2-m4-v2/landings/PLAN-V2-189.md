# Landing Analysis: PLAN-V2-189 — the outline record loses scope_estimate and added_dependencies, and the finding sources dependency_delta, fix_proof and added_lines go

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #635 (https://github.com/plan-marshall/pm-mcp-core/pull/635), merge `5f36ece2` (squash through the merge queue, parent `889239ff`), analysed on the forty-second run, 2026-11-27

> Landing record. Source: the plan's inbox message `plan-v2-189-outline-record-scope-008` (landing-facts present, `merge_state=merged`, `complete: false`, see Metrics) with seven candidate lessons (001 to 007), read against git (`git diff 5f36ece2^ 5f36ece2`: 21 files, +189/-424, `pm-core` only) by one read-only leaf (`execution-context-level-3`, R21) that was given the spec and the message as data and ran no build. The orchestrator re-read L3-1, L3-2 and L3-3 at their lines (`OutlineRecord.java:52-60`, the NPE asserts of `Outline*Test`, the pre-merge `OutlineRecordBuilderContractTest`, ADR `core-spi/002:33`, ADR `core-store/049:53-59`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 `scope_estimate` (record, schema, status) | shipped-as-specified; one benign deviation | `outline-record-v1.schema.json` `required` is `summary, overview, deliverables`; the three status members are gone (`pr_title` and the closed `metadata` stay); `OutlineRecord` is three components, enum removed, `@Builder` removed (a record under four components keeps its constructor); `OutlineCodec:298` and `OutlineRenderer:36-44`; the negative control is `DeletedMemberRefusalTest:90` (UNREADABLE, `undeclared field 'scope_estimate'`); `OutlineRecordTest:93-108` was rewritten to `change_type` and not deleted (wire-name coverage of a remaining member kept) |
| D2 `added_dependencies` and the four `outline.dependency-*` rules | shipped-as-specified | deliverable `required` has fourteen members; `AddedDependency`, the section parser, `checkDependency` and the four rule keys are gone; control `DeletedMemberRefusalTest:91`; `OutlineValidatorTest:124-136` proves the heading `**Added dependencies:**` is ordinary text (only `affected-file-intent@8` is reported); `core-spi.adoc:321-334`, `core-store.adoc:2889-2892` reworded |
| D3 the finding sources | shipped-as-specified, `FIX_PROOF` and `ADDED_LINES` removed too (the confirm-when-cutting hypothesis held) | `FindingSourceKind` ten constants equal to the schema enum; `FindingSourceKeyTest:148-172` `removedWireNames` (three names, `Optional.empty`); `RemovedSchemaMembersTest:165-198` (enum and description); `core-findings.adoc:36-39`. The spec's clause "a finding of that source is refused, naming it" is satisfiable at the schema and enum level only: `pm-core` has no finding reader that parses `source` (info) |
| Done-when 1 to 4 | met by reading | the three refusals, the round trip, no dependency rule, `change_type`/`satisfies`/`affected_files` untouched in the diff |
| Done-when builds, coverage, integration, Sonar | not shown by reading | the lane reports its runs; Sonar `0` open new issues |
| Standing constraints | conform | 21 files, no POM change, no model-facing content, schema and parity test in one commit |

## Three-Layer Analysis

### Layer 1 — Specified

All rows shipped as specified. Two deviations, both benign: the `OutlineRecordTest` unknown-value case was rewritten (not deleted), and `RemovedSchemaMembersTest.java` (+103) is outside the spec's Expected Surface (the PLAN-V2-188 precedent and the PR text name it).

### Layer 2 — Implemented

Repo-wide `git grep` (case-insensitive, outside `.plan/`) of every removed name: zero hits in `pm-workflow`, `pm-runtime`, the providers, `src/test/resources`, `doc/specification`; the remaining hits are the negative controls, the `**Added dependencies:**` fixtures, the `OutlineCodec` Javadoc and the two package-text lines (declared follow-up). Controls added: `DeletedMemberRefusalTest` (+2 rows, member named), `RemovedSchemaMembersTest` (+5 methods), `FindingSourceKeyTest` (3 cases), `OutlineValidatorTest` (1 replacement). Test methods removed: 2 (`builderBuildsOutline`, `duplicateNamesFirstLine`); narrowed: 2 (`roundTripsAndRenders` lost the Added-dependencies assertion, `recordEnumerations` the scope enum). Parameter rows: `OutlineValidatorTest` -14 rejected, -1 accepted. Not shown by reading: every build, test, coverage and integration result.

### Layer 3 — Correct

No major defect. Re-read at the lines: L3-1, L3-2 hold; L3-3 stays minor.

| Defect | File:line | Reading | Effect | Severity |
|--------|-----------|---------|--------|----------|
| L3-1 | `OutlineRecord.java:52-60`; the deleted `OutlineRecordBuilderContractTest::builderBuildsOutline` | the canonical constructor refuses an absent `summary` and an absent `deliverables` with a `NullPointerException`; the deleted test held the only assertion of the `summary` case (`assertThrows(NullPointerException...)`), and a grep of `Outline*Test` finds NPE asserts only for `Deliverable` and the store; no test pins the `deliverables` case either | removing either `requireNonNull` fails nothing | minor |
| L3-2 | `pm-core/doc/adr/core-spi/002-content-validators-report-violations-as-values.adoc:33` | says the dependency coordinate forms `OutlineValidator` accepts (Maven, npm, PEP 508) are an implementer judgment; the validator accepts none now | a reader assumes coordinate rules exist | minor |
| L3-3 | `pm-core/doc/adr/core-store/049-intent-and-outline-records-are-revision-stores.adoc:53-59` | the refused-whole list names `capability`, `interfaces`, `key_links`, `attempt_end`; the outline now also refuses `scope_estimate` and `added_dependencies` | the list is two members short | minor |
| L3-4 | `WorkflowStatusCodec.java:177` | an old stored status with `metadata.scope_estimate*` is read, not refused: `metadata` is retained verbatim by design; the spec asks for the refusal of the outline and the finding source only | none | info |
| L3-5 | `OutlineRenderer` | no negative control on the rendered text (no `**Scope estimate:**` line); the round-trip test is rewritten to match | none | info |
| L3-6 | landing message "four tests" | the findings were four removed-assertion findings; the diff shows two methods deleted and two narrowed (the review counts a narrowed assertion as removed) | message accuracy only | info |
| L3-7 | `OutlineValidatorTest:114`, `RemovedSchemaMembersTest:30-31` | Javadoc lines over about 120 characters; the pre-commit gate decides | none | info |
| clean | `OutlineCodec` `readRecord`/`readDeliverable` required lists equal the schema `required` lists; unknown members refuse with `undeclared field`; `OutlineValidator.validate` boundary logic unchanged; `FindingSourceKind` has no switch or ordinal use; the three schemas parse and `required` is a subset of `properties` | | | clean |

**Layer 3 verdict:** findings — 7 (three minor, four info) and the clean confirmations; not shown by reading — the runs. Nothing is reported at once: no major finding.

The two declared follow-ups are confirmed exactly as stated and judged not defects: `doc/plans/phase-sub-workflows.adoc:19,40` is the package's own task description (the milestone is still a task of the package), and `phase-workflows/03:95` names `plan-marshall`'s own "surgical scope estimate" (another product's rule). `OutlineSchemaParityTest` pins the schema `required` lists by `containsAll` and does not pin the codec read-side lists (CodeRabbit notes of #635, declined there): staged, see below.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | a record of four or more components carries `@Builder`, a smaller one its constructor | conforms | `OutlineRecord` has three components and lost `@Builder`; `Deliverable` keeps it |
| CLAUDE.md Code Standards | every assertion carries a message of 20 to 60 characters | not shown by reading | the lane's scan is part of its verify |
| CLAUDE.md Module Documentation | developer documentation and ADR stamps | partly | `core-spi.adoc`, `core-store.adoc`, `core-findings.adoc` updated; ADR 049 and core-spi/002 not (L3-2, L3-3) |
| CLAUDE.md Git Workflow | merge through the queue | conforms | squash `5f36ece2`; the squash message carries the PR page body (see Residue) |

## Metrics and Anomalies

- Harness claude, model `claude-sonnet-5-5`, effort medium, plugin `0.1.2184`, executor `0.1.2184`; `total_tokens=11267325`, `total_wall_seconds=12572`. `complete: false` for one key: `unresolved_review_threads` (`unknown`: the review findings were thread-less and no thread read was made; an unobserved fact, never 0). Sonar `0` open new issues.
- The lane stopped twice on operator override: functional review and code review each blocked on the same four removed-assertion findings at head `99f905f2`; the second review could not see the first acceptance (candidate lesson 001). `pre-push-quality-gate` re-ran the whole-tree builds for 28 minutes on an unchanged tree (candidate lesson 002); build-maven was 71 percent of script time.
- The outline had not listed the deleted pinning tests, so the removed-assertion rule counted them as unaccepted (candidate lesson 003).

## Routing and Merge Behavior

- Merge: through the merge queue as `5f36ece2`; the squash commit carries the PR page body with the CodeRabbit auto-summary (candidate lesson 005).
- Seven candidate lessons (about `plan-marshall:phase-6-finalize` x3, `phase-3-outline`, `plan-retrospective`, `manage-findings`, `platform-runtime`) forwarded verbatim to the `lessons-routing` inbox of the plan-marshall repository as messages `pm-mcp-core-m2-m4-v2-061` to `-067`; they stay uncommitted in that ledger worktree until that repository's `land`.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#635`; `landing` `landings/PLAN-V2-189.md`; `plan_marshall_plan_id` `plan-v2-189-outline-record-scope`
- [x] the eight messages archived (`inbox archive`); the seven lessons forwarded first
- [x] L3-1 (the two null refusals), L3-2 and L3-3 (the two ADRs) and the codec read-side pin staged as items (e) to (g) of deliverable 4 of PLAN-V2-209, with four paths added to its Expected Surface; L3-4 to L3-7 need no action
- [x] the 189 Watch retired in `epic.md`; the Decisions entry and the Open Defects of the forty-second run written
- [x] PLAN-V2-190, 191 (hold on the outline plan released), 209 and 211 (overlap with 189 gone) re-checked for emit
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **Staged:** deliverable 4 (e) to (g) of PLAN-V2-209.
- **Owed to the marketplace (not this epic):** the seven lessons, now in `lessons-routing`.
- **Open Defect:** `unresolved_review_threads` unmeasured in this landing.
