# Landing Analysis: PLAN-V2-166 — statement-residue-closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #564 (merge `cc52de5f45ac6b2751014916322254a1ab43fbaf`, 34 files, +329/-182, after a base merge of `main` at `c0d14b96`)

> Landing record, runbook-lane form (`rb-v2-166-statement-residue-closeout`, harness opencode, model `deepseek-v4.1-flash/high`, effort high, executor 0.1.2184, standing rules r7, 3 of 4 deliverables by the lane's count). Landing message `-001` (2026-11-22T18:31:34Z, `merge_state=merged`). `landing-check`: incomplete on `total_tokens` and `plugin_version` (the two keys of every opencode landing, folded into the standing Open Defect). Stated by the lane, not re-verified: 0 unresolved threads, 5 of 5 body findings handled, Sonar 0 open, 12 630 tests green at the last head. CodeRabbit reviewed `fa7db3af` (a formal review, five findings, all dispositioned); Sourcery refused twice on its own budget (optional). The branch changed 34 files in the merge (the message says 31: the count predates the base merge).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the requirement sentences of `store-schemas/10` | shipped-as-specified for the four named sentences, plus three | the five bullets (`:40,54,56,57,61`) carry the Lock, Claim, Retirement, Maintenance and Derived-from-Untrusted sentences; schema block, kind tables and anchors untouched (the only hunks are the five bullet lines); every deleted sentence is not back (below) |
| D2 the cut-log label and the shims | shipped-modified | `tailOnly` is gone as component, accessor and builder method; `parse(Path)` yields `Unmeasured` for a cut log (`MavenOutputParser.java:196-216`); the 5-argument `from(..., tailOnly)` is gone and the 4-argument `from` stays (it is the canonical factory every parser uses: the spec's premise was wrong, the lane read it right); three new tests; **inert in this repository's production path (L3-1)** |
| D3 ADR 009's tense and the three test losses | shipped-modified | the Context is past tense; two of the three "losses" were false premises (`endpointVerificationRefusesNullClasses` was already strengthened in #555; the omitted-reference refusal existed since #457 and #169); `askerWaitReoffersTheTaskWithItsLease` and `ProcessTreeTrackerTest.dependsOnNoGroupSignal` are new |
| D4 `freeId()`, long lines, links check, outcomes | partly met | `freeId()` bounded at `MAX_ID_DRAWS` 64 with `consultation_ids_exhausted` (declared in `ConsultationRefusal` and the guide), test `seamOfTakenIdsIsRefused`; the 31 lines broken; **the links clause is not met: `BROKEN LINKS 97`**, all cross-repository or anchors of another checkout, 0 local links broken (the same 97 as PLAN-V2-164) — an unsatisfiable clause on this host; outcomes 14(d), 16, 20(a), 67(c) and the six weakened-test removals are in the landing message only |

D1 sentences not stated again (leads, not re-verified): the Retirement sentence "in every other state it changes nothing and reports the state"; the Derived-from-Untrusted enumeration of the untrusted writing-task inputs and "a start screens it as a source, the lesson review reads it marked untrusted, the clients show the mark" (they may live in `model-work/07`); the File Format restrictions (now in the guide and ADR 061).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`); no file written, no build run; spec and message passed as data.
- **Re-read by the orchestrator at `origin/main`:** `BuildOutputParser.java:141` (the daemon path calls `MavenOutputParser.parse(output)`, the String overload) and that `parse(Path)` has no production caller in `pm-core` or `pm-runtime`; `core/build/package-info.java:49-50` and ADR `core-build/007:85` still say a cut log's Maven count is the tail's alone. **Not re-read, kept as the leaf's claims:** whether `SubprocessJobRunner` passes the `JobLog.readWhole` (capped) text to the parser, the D1 bullet comparison, the mutation-run log ids and test counts, the ArchUnit rule's reach, the long-line counts.
- **Verdict:** layer 1: findings (two false premises, one unsatisfiable clause); layer 2: findings (stale statements, leads on lost sentences); layer 3: **no blocker, one major (L3-1)**, minors and leads.

### Layer 1 — Specified

| Finding | Claim | Verdict | Severity |
|---------|-------|---------|----------|
| L1-1 | D4 Done-when `BROKEN LINKS 0` (R17) | unsatisfiable on this host (97 cross-repository links the tool cannot resolve offline); `deliverables_done=3/4` is structural | minor (spec and rule defect) |
| L1-2, L1-3 | D3 "restore" the two refusal tests | false premises (they exist); the spec allowed "or name the test"; only the asker-status and lease assertions were really lost | minor |
| L1-4 | D2 "drop the 4-argument `from`" | it is the canonical factory; the 5-argument one went | minor |
| L1-6 | D4 "break the lines over 120 columns" | checkable for the diff range, but the repository has no 120-column invariant: 345 production files hold lines over 120 | lead |
| L1-8 | The generated spec file | the Write-Boundary paragraph was cut off and an unmatched code fence followed the hand-off (the orchestrator's own authoring defect; fixed in specs 166, 167 and 168 on 2026-11-22) | minor |

### Layer 2 — Implemented

| Finding | Detail | Verdict |
|---------|--------|---------|
| L2-4 | the cut-log tests `cutMavenLogReportsUnmeasured`, `cutMavenLogNeverReadsAsNoTestsRan`, `cutMavenLogKeepsAVisibleFailingCount` can fail | checked |
| L2-5 | ADR 011 is internally consistent; its file name still says tail-only (the Status records the slug); no index row lists it | checked |
| L2-6 | stale statements remain: `core/build/package-info.java:49-50` and ADR `core-build/007:85` (re-read), `job-runtime/03-parsers-and-gates.adoc:15,21` (no cut-log rule), ADR `workflow-consult/005:25-26` (no bound on the id supplier) | checked (two re-read) |
| L2-7 | ADR 009's Decision and Consequences stay in the present tense | acceptable for a superseded ADR |
| L2-8 | `dependsOnNoGroupSignal` binds the outer class by simple name; no guard on the target name (a rename of `ProcessGroupSignals` passes silently) and the nested `Member` and `Survivor` are not imported | checked |
| L2-12 | files no deliverable explains: enum-body re-indentation in `GitResolveMessagePrimitive` and `GitStageAndCommitPrimitive` (#545's code), `throws IOException` to `throws Exception` in `CoAuthoredBySourceScanTest` and `StagingSetsTest`, line re-wraps in ten more files; pre-commit recipe output, only partly separated into its own commit | checked |

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `MavenOutputParser.java:196-199`; `BuildOutputParser.java:141` (re-read) | a Maven job log over the job-log cap, cut by `JobLog.readWhole` and then handed to `parse(String)` (`cut=false`) | the daemon path still reports the tail's counts as `Measured`; a failing module in the lost head is missing and the success banner stays green; the new `Unmeasured` is as unread in this repository as `tailOnly` was. The over-cap reach into `parse(String)` is **an unverified lead** (ADR 007 and the call count, not read line by line); that `parse(Path)` has no production caller is re-read | **major** |
| L3-2 | `MavenOutputParser.java:368-371,207-209` | a cut tail whose `Results:` marker is in the lost head and whose summary line carries `[WARNING]` or `[INFO]` | the line lands in `ambiguousLines`, `visibleFailures()` is 0, no `TEST_FAILURE` is filed | minor (lead) |
| L3-3 | `MavenOutputParser.java:226-232` | a cut tail whose `[ERROR] Tests run:` line is already filed | one extra synthetic `TEST_FAILURE` diagnostic (a duplicate) | minor (lead) |
| L3-4 | `MavenOutputParser.java:373-376` | a cut log with no count lines | `Unmeasured` instead of null; a consumer that tells absent from unmeasured sees a change | minor |
| L3-5 | `ConsultationService.java:182-222` | `freeId()` checks outside the transaction; a non-random seam returns one id to two concurrent opens | the loser fails at `queue.create` after the asker moved to `consulting`; pre-existing | minor |
| L3-6 | `ProcessTreeTrackerTest` | a rename of `ProcessGroupSignals`, or a call from a nested class | the structural guard passes silently | minor |
| L3-7 | `JobLog.java:223-233` | a log shrunk then regrown to `size` between the two reads | `Complete` with a short text; pre-existing (#555) | minor |
| L3-8 | `RoleNameSingleDefinitionTest.java:48-52` | JSON `\uXXXX` escapes | the class doc says escapes are decoded, the lane's follow-up says they are not; not read | minor (lead) |

## API (for the assembly)

`TestCounts.Measured`: `tailOnly` is removed (component, accessor, builder method); the canonical constructor has five components; `TestCounts.from` keeps the four-argument overload only. `MavenOutputParser.parse(Path)`: a log over `BuildLogText.MAX_BYTES` yields `Unmeasured`, never `noTestsRan`, plus one synthetic `TEST_FAILURE` diagnostic when the tail reports failures; `parse(String)` is unchanged. `ConsultationRefusal.CONSULTATION_IDS_EXHAUSTED` (wire `consultation_ids_exhausted`) breaks an exhaustive switch without `default`; `ConsultationService.MAX_ID_DRAWS` is 64. Whether `pm-api` or the assembly enumerates the refusal names is unverified.

## Residue and its placement

No staged plan owns `pm-core` build parsing, the consult package or the stale statements; PLAN-V2-162 is running (it carries `JobLog` item 81) and is not edited. A new regular cleanup plan, **PLAN-V2-170** `cut-log-and-statement-closeout`, carries item 88: L3-1 (the cut flag in the daemon path, ask-at-refine, R07), L3-2 and L3-3, the stale statements, the lost-sentence leads, L3-5 to L3-8, the `ProcessTreeTrackerTest` guard. **Open for the operator (recorded in `epic.md`):** the links-check clause: `BROKEN LINKS 0` cannot be met on this host while 97 cross-repository links stand (166 and 164 both reported 97); the R17 wording or the tool needs the operator's word.
