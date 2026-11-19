# Landing Analysis: PLAN-V2-114 — analysis-findings-fixes

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #512 (merge `3b566897`, parent `bb8456d8`, 23 files, +146/−71)

> Landing record, runbook lane (`rb-v2-114-analysis-findings-fixes`, harness opencode, model `beam-free/high`: a free model the operator
> had selected by mistake; the operator switched the model for the following runs). Drained from
> `inbox/rb-v2-114-analysis-findings-fixes-001.md` on 2026-11-19. `inbox landing-check`: `complete: false`, missing `total_tokens` and
> `plugin_version` (both `unknown`); `surface_delta` unmeasured (neither a declared nor a realized surface supplied).
> The lane stopped four times and asked the operator to continue; the landing says `deliverables_done=4` of 5.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 (a) message order, (b) YAML sentence, (c) "YAML 1.2 library" wording | shipped-as-specified | `LessonFrontMatter.java:152` swapped; `LessonFrontMatterTest:246-251` asserts "format version 2, not 1" and fails on the old message; the sweep finds no occurrence left at `3b566897` |
| D2 (a) `EffectiveRole.idleWakeups` | not delivered (rescoped by the lane) | true that it is `OptionalInt` (`EffectiveRole.java` at `3b566897`, re-read), and the binding Re-ground record says "already resolved"; but Carried Item 1 (operator-settled 2026-11-10) still asks to convert the `OptionalInt` components to `@Nullable Integer`, and Item 4 a `requireNonNull` test: the spec contradicts itself, the lane followed the binding record and recorded no reason |
| D2 (b) `SubprocessJobRunner.latest` | shipped-modified | non-null field and constructor `requireNonNull` (`:1844`, `:1877`); `store` is still `null` at five test sites (`SubprocessJobRunnerTest` 1563, 1609, 1656, 1829, 1920); no test pins the new refusal; an S2637 suppression added with an unchecked comment |
| D2 (c) `QuestionRecord` | shipped-as-specified in the main code | four `@Nullable` components, `requireAsked` adapted; no new test, the annotation-versus-accessor choice recorded nowhere |
| D3 (a) identifier redaction, (b) lock winner pinned | shipped-as-specified | `DerivedFactStore.java:280-283` (re-read); two new `DerivedFactRefreshTest` cases; `DerivedFactStoreLockTest:207-208`; the scratch-mutation failing run is not reported |
| D3 (c), (d), (e) | shipped-as-specified | ADR 056 clause, spec sentence split, ADR 063 linked |
| D4 renumbering | shipped-modified | lesson pair now 075/076, no duplicate number; references updated; the guide's plain-text citations (`core-store.adoc` ~824-845) were not turned into links |
| D5 (b) blank body | shipped-as-specified | `LessonStore.java:163`, `:377`; a deleted test (`citesPersistedEmptyShell`) is not replaced |
| D5 (c) untrusted citations | **shipped-modified, widened** | see L3-1 |
| D5 (d) DEL | shipped-modified | `title` only; a construction test, not the round trip the spec demanded |
| D5 (a), (f) | not delivered, true "already fixed" | the code was right at `bb8456d8`; (a) still has no leading-zero case for `unclaimed()` (`LessonResolverTest:454-465` tests 999 against 1000 only) |
| D5 (e), (g), (h), (i) | not delivered | no change in the diff |
| Carried Open Items 1-57 | **untouched**, no adopt/split/drop reason recorded | the spec says the outline records one per item |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: a full-ship landing is read by its own leaf with no size qualifier; the leaf wrote nothing. Extra scrutiny was asked because the lane ran on the free model.
- **Re-read by the orchestrator** (all held): `LessonStore.java:725-759` (`isUntrusted` returns `true` for `Unreadable`, `PresentUnreadable`, `StoreTampered`, `DeletedOutOfBand`, `Untracked` and `Absent`, and keeps `default -> false`); `store-schemas/10-job-and-lesson-records.adoc` line 59 at `3b566897` (the mark is set when a cited lesson "is `true`"); `EffectiveRole.java` at `3b566897` (still `OptionalInt idleWakeups`); the lane's progress file `.plan/temp/rb-v2-114-analysis-findings-fixes/progress.md` (8 lines, ends "D5: not started", no `finding:` line); the merge diffstat; the review gate.
- **Not re-read, recorded as unverified leads:** that Jackson leaves U+007F unescaped and SnakeYAML rejects it (reasoned, not run); why Sonar raised S2637 on `RunningJob`; the full suite and the parity tests; whether the D3(b) mutation proof was run; whether the assembly dereferences the now-`@Nullable` `QuestionRecord` accessors; the leaf's other file:line readings (`QuestionLog`, `SubprocessJobRunner`, the ADR/guide lines).
- **Verdict:** layer 1 one major (process, and the spec's own contradiction), two minor; layer 2 findings; layer 3 one major, five minor, two info. No blocker.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | D2(a) "already `OptionalInt`" is true, but Carried Item 1 (operator-settled) and the binding Re-ground record contradict each other; the lane took the record | checked | refutes the lane's silence, not its reading | `EffectiveRole.java:50`, spec Carried Open Items 1 and 4 |
| L1-2 | D5(a) "no new case needed" | checked | refutes | `LessonResolverTest:454-465`: removing `normalized` from the comparator fails nothing |
| L1-3 | D5(c): the spec's default reads "unreadable states" and lists `Absent`; `Untracked` is in neither | checked | refutes the lane's widening | spec D5(c) |
| L1-4 | D5(d): DEL written, then read as unreadable | not shown by reading | n/a | the spec asked for the failing round trip first; the lane wrote none |

Persisted by `corpus set-verdict`: none; no finding names a labelled claim of a staged spec that this landing refutes.

**Layer 1 verdict:** findings — 4

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | as above | yes | cosmetics: lines over 120 characters (`LessonCodec.java:71`, `:824`, ADR 061, `core-store.adoc:745`); `JsonModelResolver` Javadoc now begins "A YAML library whose…" | checked |
| D2 | as above | none for `latest`'s constructor refusal; none for `QuestionRecord` | spec's "constructor-contract case" not borne | checked |
| D3 | as above | (a) yes; (b) asserts the winner, no failing run | the scratch-mutation report | checked |
| D4 | 063→075, 064→076 | n/a | guide links not made | checked |
| D5 | b, c, d code | `citesAbsentLesson` flipped, its `@DisplayName` still says "stays false"; no test for `PresentUnreadable`, `StoreTampered`, `DeletedOutOfBand`, `Untracked`, unreadable staged bytes or `EmptyShell` | none (no unexplained file) | checked |
| report | n/a | n/a | the PR body: "pm-core: 5305 tests green" although `pm-runtime` changed; "D5 includes sequence normalization (already in #289)" although that is no change; no Sonar result named; no undone item named | recorded |

**Layer 2 verdict:** findings — 5

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `LessonStore.java:743-759` (re-read) | a lesson revision citing a lesson that is absent, untracked or tampered | the revision is marked untrusted permanently ("never turns back"); `store-schemas/10:59` says only a cited lesson that "is `true`" marks it, and the spec made the item R07 ask-at-refine: the landing says "Operator questions: none asked"; no spec edit, no ADR | major |
| L3-2 | `LessonFrontMatter.java:147`, `LessonCodec.java:212` | a DEL in any front-matter string other than `title` | still written, then read back as `PresentUnreadable`; the writer's root cause is untouched | minor |
| L3-3 | `SubprocessJobRunner.java:1875` | the `RunningJob` constructor | the S2637 suppression is unsubstantiated: the file had none before, no null can reach the assignment now; it pre-empts the S2637/S2583/S2589 read the spec reserved; the real defect, `store` = null at five test sites, stays | minor |
| L3-4 | `LessonStore.java:163`, `:377`; `LessonFrontMatter.java:147` | blank body, DEL in a title | contract changes with no ADR, guide or spec statement, none asked | minor |
| L3-5 | `LessonStore.java:733-734`, `:757` (re-read) | a new `LessonResolution` variant; a malformed `Kind.LESSON` ref | `default -> false` is dead today and defeats the exhaustiveness check, so a future variant is silently trusted; a malformed ref fails open (`cited.isEmpty() -> false`) | minor |
| L3-6 | `LessonStore.java:739-740` | a cited lesson deleted in the same transaction | reads as clean; CodeRabbit's outside-diff finding; the lane's replies say "documented out of scope in the PR" and the PR body has no such text; the stale-digest overwrite of ADR 056 is credited to PLAN-V2-111, which has shipped | minor |
| L3-7 | PR #512 body | reading it | presents D5 as delivered, names none of the undone items | info |
| L3-8 | landing message | "14 threads resolved", "3 body findings handled" | neither count could be reproduced; the progress file holds no `finding:` line | info |

**Layer 3 not shown by reading:** the full and targeted test runs, the parity tests, the Sonar gate (the lane says `sonar_open_new_issues=0`, `ok`).

**Layer 3 verdict:** findings — 8 (no blocker)

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| spec R07 | ask at refine before a behaviour change the operator has not accepted | **deviates** | L3-1, L3-4; "none asked" |
| spec Carried Open Items | the outline adopts, splits off or drops each item with a recorded reason | **deviates** | none recorded for 57 items |
| CLAUDE.md § Code Standards | no generic exception, assertion messages 20-60 characters, `@ParameterizedTest` for 3+ variants | not shown by reading | the diff's tests were not read for message lengths |
| CLAUDE.md § Code Standards | a test that can fail on the old code | **deviates** (partly) | D2(b), D2(c), most D5(c) arms |
| CLAUDE.md § Work Packages / Module Documentation | an ADR per design decision, in the plan's own PR | **deviates** | the widened rule, blank body, DEL, the `QuestionRecord` choice: no ADR |
| CLAUDE.md § Git Workflow | commit trailer | not read | |

## Metrics and Anomalies

- Tokens: `unknown`. Duration: not in the message. Model: `beam-free/high`, not the model the lanes of this epic had run on; the operator switched it after these stops.
- Anomalies: four stops where the lane asked the operator to continue; the self-review round was not dispatched ("time"); one `execution_failed` from a doubled `--project-dir`; the lane reports its own use of shell file operations; the progress file says "D5: not started" although the landing lists three D5 commits.

## Routing and Merge Behavior

- Review: coderabbit required, sourcery optional. `runbook_lane review-gate --pr-number 512 --required-reviewer coderabbit` on the merged head reads `unresolved_review_threads: 0` and `review_body_findings_total: 3`: both equal the landing's claim. `review_body_findings_handled=3` is **not re-verifiable**: the gate reads `handled: 0` and `undisposed` for `inline-1` to `inline-3` (`merge_allowed: false`, `body_findings_undisposed`) and the progress file holds no `finding:` line.
- CI/merge: merged as `3b566897`. Sonar: `sonar_open_new_issues=0`, quality gate ok claimed; the leaf read CI as green for build, integration tests and Sonar (`sonar-build` skipped); not re-read, so it stays the lane's claim.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#512`; `landing` stamped; `plan_marshall_plan_id` `rb-v2-114-analysis-findings-fixes`
- [x] epic.md: the narrative entry, the Watch "landed", the decision on the successor plan
- [x] the open remainder and the 57 Carried Open Items staged as PLAN-V2-156 (the leftovers rule; no staged plan owns the lesson store)

## Follow-Ups

- Every open point of this record is PLAN-V2-156's deliverable or carried item; none is unowned.
- PLAN-V2-155's predecessor "item 35 of PLAN-V2-114" is now item 35 of PLAN-V2-156 (it was not done in 114).
