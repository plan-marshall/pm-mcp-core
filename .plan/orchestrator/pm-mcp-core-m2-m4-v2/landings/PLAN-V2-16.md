# Landing Analysis: PLAN-V2-16 — The lesson store: allocation, resolver, revision, retirement and claim

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
pr: #284 (merge commit `70bc999f`, `70bc999fbd92cf978a4718aa9e94a498160ead88`; 44 files, +5116/-102); the follow-up #289 (head `ae2647c2`) is open and not merged

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-16.md`. Written by the `analyze` verb after verifying
> claims against ground truth (actual code, artifacts, PR state); a pasted claim is a lead, never a fact.

Source: inbox message `rb-v2-16-lesson-store-operations-001.md` (runbook lane, harness opencode, model
`deepseek-v4.1-flash/high`, effort high, executor 0.1.2041, standing rules **r5**, R01–R14, R17–R20; the spec named r4). Corroborated
against `origin/main` (the merge diff of `70bc999f`), `ci checks status --pr-number 284` (`overall_status: success`) and
`ci pr view` of #289 (`open`, `mergeable`, checks green).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — allocation and create | shipped | `LessonStore` (create, allocate), `LessonStoreTest$Creation/$Allocation/$RoundTrip` |
| D2 — the resolver and its four states | shipped | `LessonResolution`, `LessonResolverTest` over 9 YAML and 6 headerless ported fixtures |
| D3 — revision and retirement | shipped | `LessonStore.requireValid` owns the retirement rules; `LessonRevisionTest`, `LessonRetirementTest` |
| D4 — claim, release, end | shipped | `LessonClaimTest` over the table, 7 states × plans A and B |
| D5 — crash windows and concurrency | shipped | `LessonStoreCrashTest` (every `Step`, roll-forward pinned) |
| D6 — documentation, ADRs, reductions, package file | shipped, two clauses not met | the links check (lane: 361 broken links after the rebase, all in the sibling); PLAN-V2-24's open question (the lane may not edit the ledger; corrected by the orchestrator on 2026-11-04) |

**The merged tree is not the lane's final tree.** PR #284 was merged by another session after rebasing the branch onto a moved
`main`; the operator chose that the lane reset to the published rebase and re-apply its missing commit. The merged `70bc999f`
therefore lacks the second review's fixes, which sit in the open follow-up #289: leading-zero sequence comparison, the id in a
successful outcome, a persisted blank lesson counting for the untrusted mark, three Sonar complexity findings. The row is shipped
on #284; #289 is a Watch (the operator's merge).

Surface delta: undeclared `ScopeTransaction` members (`lockTransaction`, `auditSink`, `targetPaths`, `targetContent`, lines 192 to
228) and 9 lines of `package-info.java` — the spec said "existing classes edited: none expected"; the lane reports it. The
"ten reformatted provider files" the lane mentions are not in the 44-file diff (not shown by reading why).

## Metrics and Anomalies

- Tokens `unknown`, `plugin_version` `unknown`: the open defect in `epic.md`, twelfth recurrence.
- The spec was read at **r4** and the lane ran under r5; the file is now r6 (the sub-agent clause binds the orchestrator only).
- Self-review `clear` after one round (12 findings, the lane reports all fixed or recorded). CodeRabbit (required) reviewed
  `647c0d4a` (7 findings) and `9d658c06` (4 findings); eight of ten threads resolved, two stay open because their ids never
  appeared in `ci pr comments`. Sourcery refused #284 on its diff limit. Two operator questions were answered; one free-text
  "continue" after a two-day gap.
- Sonar: 31 open issues at `476af0cf`, fixed to 0 new on `9d658c06` (lane-stated). `main` today: no issue in the lesson files;
  the two S2637 bugs of PLAN-V2-110 remain (PLAN-V2-112).
- The build server went unreachable during the two-day gap: three builds fell back in-process (`in_process_fallback`); the
  gate builds on the merged head are lane-reported green, not re-run here.

## Routing and Merge Behavior

- Merged by the operator's own session, not by the lane's merge-queue call (a deviation from the lane's flow, disclosed).
- Collision check: ran beside 110, 111, 08, 28, 87, 88; no collision in the diff.
- Two ADR files numbered 063 and two numbered 064 exist in `pm-core/doc/adr/core-store/` (see C-ADR below): R11 asked the
  second-merged pair to renumber.

## Three-Layer Analysis (standing rule R21, r6)

Done at the merge commit `70bc999f` by a read-only sub-agent (the `Lesson*` production classes, `ScopeTransaction`, the codec,
specification 10, ADRs 061 to 067, the six test classes, and the follow-up #289 as a local ref), then the points marked
**checked** re-read by the orchestrator. Nothing was built or run. What only a run could show is marked "not shown by reading".

### Layer 1 — Specified: the PLAN-V2-11 findings are resolved; the surface and the reductions were not honest

- **S1 (medium, checked).** "Existing classes edited: none expected" is refuted by `ScopeTransaction` (`ScopeIntegrity.verify`
  needs the `LockTransaction`); the new members have no `ScopeTransactionTest` case.
- **S2 (resolved, checked).** PLAN-V2-11's S3 (who enforces the retirement rules): `LessonStore.requireValid` (761–776) owns
  them, and the specification's Retirement bullet says so. `LessonFrontMatter.Retired` still enforces none of it, so a decoded
  file with `completely_covered` and no clause is read without complaint (low).
- **S3 (resolved, low).** `empty_shell` is "no non-whitespace character"; the code uses `String.isBlank()` (`LessonStore.java:743`),
  i.e. `Character.isWhitespace`, so a body of only U+00A0 or U+2007 is `Found`, which the text does not say.
- **S4 (medium, checked).** `EmptyShell` is a dead end: `revise`, `claim`, `retire`, `release` and `end` act on `Found` only,
  no operator act is built for it, and nothing forbids `create` or `revise` with a blank body — the store can produce a state it
  cannot leave.
- **S5 (medium, checked).** D6 asked for the bullets of `store-schemas/10` condensed to "Implemented by `LessonStore`" /
  "Remaining"; the merged specification has no such text (wording edits only), so the R05 reduction was not done. The lane
  reports D6's other clauses as met.
- **S6 (low, checked).** D6 says the row leaves the index and no cell names `lesson-store`; the diff adds a `lesson-store` row to
  the done table (`doc/plans/README.adoc:342`), which follows the table's convention and contradicts the spec and ADR 067.
- **S7 (low).** D6's "BROKEN LINKS 0" and its sibling carve-out ("an item deleted in the sibling") do not fit renamed engine
  paths; "no new broken link against the base" is the testable form. D6's "PLAN-V2-24/22/21 no longer list the store" cannot be
  met by a lane whose Write-Boundary forbids the ledger — a spec defect, not a lane defect.

### Layer 2 — Implemented as specified: D1 to D5 met; one claim true but incomplete

- **I1 (checked by the reader's reading).** The tests assert real values: exact bytes plus the sidecar digest and journal
  absence (`LessonStoreTest`), every `Step` with a roll-forward pin (`LessonStoreCrashTest`), 7 states × plans A and B
  (`LessonClaimTest`). The lane's "mutation-after" failing runs are not shown by reading.
- **I2 (medium, not shown by reading).** "The codec escapes every non-ASCII character" is true (`ESCAPE_NON_ASCII` on the front
  matter writer) but incomplete: Jackson does not escape U+007F, SnakeYAML's printable set rejects it, `LessonFrontMatter` has no
  control-character check and `encode` does not verify the round trip, so a title with DEL would be written and read back as
  `PresentUnreadable`. The `RoundTrip` test string (`LessonStoreTest:448`) omits U+007F.
- **I3 (low, checked).** "The lock-free read never decides" is not fully true: `get(LessonId)` (217–227) answers
  `DeletedOutOfBand` from the sidecar without a lock; the lane admits it and says the documents were corrected.
- **I4 (low, checked).** The locked `get` recovery path is not crash-tested; the watch deletions are thinly covered
  (PM-WATCH-STORE-18's guard "reach computable" has one assertion on `retired.by`, `LessonClaimTest:361`).
- **I5 (medium, checked).** CLAUDE.md conformance: `LessonResolution.StoreTampered` (line 207, checked) has the record component
  `Optional<StoreDigest> current` — `Optional` is a return type only. About 76 assertion calls in the six `Lesson*Test` classes
  carry no literal message (for example `LessonRetirementTest` 154–157, 170–172, 182; `LessonClaimTest` 198, 266, 284, 346) and
  four are outside 20 to 60 characters (`LessonStoreTest:175`, `LessonResolverTest:455,470`, `LessonRetirementTest:314`; sampled).
  `LessonFixtures` is hand-written, not a `TypedGenerator` of the generator artifact; id and plan-id literals carry no contract
  comment; `LessonStoreCrashTest` loops over `Step.values()` where `@ParameterizedTest` is expected. Clean: no `@Nullable` method
  return, `@Builder` on `NewLesson` and the large records, no generic catch, no `Mockito`/`Hamcrest`/`Random`/`UUID`.

### Layer 3 — Implementation correctness: no data-corrupting defect; four honest-contract holes, three of them left by the rebase

- **C1 (medium, checked).** Sequence comparison by digit count: `LessonStore.java:308-312` (the `unclaimed()` comparator,
  `thenComparingInt(value().length())`) and `690-693` (`isHigher`). `LessonId.PATTERN` is `[0-9]{3,}`, so a hand-placed
  same-hour `…-0001` beside `…-999` lists first and `allocate` takes it as the highest and draws `0002`; no id collision could be
  constructed, the effect is a wrong order and two spellings of one number. Fixed only in #289.
- **C2 (medium).** The outcome id comes from the front matter (`Claimed`, `Released`, `Retired`, `Revised` return
  `lesson.frontMatter().lessonId()`), while the ported fixtures deliberately state a legacy id different from the file name
  (`LessonResolverTest:161-178`): the outcome names another id than the listed one. Fixed only in #289.
- **C3 (medium, checked for the cause).** A blank body is persisted by `create` and `revise`: a claimed lesson revised to a
  blank body becomes an `EmptyShell` that `claims()` (337–348) does not list and that `release` and `endClaimApplied` answer
  `Unavailable(EMPTY_SHELL)` — the claim can never end. #289 touches the untrusted-mark reading of blank lessons only.
- **C4 (medium, checked).** The untrusted mark fails open: `citesUntrustedLesson` (713–733) treats a cited lesson that is
  `PresentUnreadable`, `StoreTampered`, `DeletedOutOfBand` or `Absent` as not untrusted (only `Found` is read); #289 adds
  `EmptyShell`. Monotonicity holds (`frontMatter.derivedFromUntrusted() || arg || cites`, 388-389).
- **C5 (low).** Claim, release, end and the repeat cases are correct in the code read; decisions use the durable
  `claim.plan_id`; two replaces of one path in one transaction throw, so there is no silent lost update. A same-transaction
  `claim` after `create` reads disk and gives `Absent` (undocumented); `claim` and `end` act on `upstream_pending` while
  `unclaimed()` lists `active` only. `allocate` and `create` do not recover a pending journal (a stale journal makes `commit`
  throw before staging); an hour rollover between `allocate` and `create` throws `IllegalArgumentException`; `list()` re-loads the
  sidecar per item (O(n²)).
- **C-ADR (medium, checked).** `pm-core/doc/adr/core-store/` holds two files numbered `063` and two numbered `064` (the
  derived-facts pair of #283 and the lesson pair of #284); the bare-number references `056:61`, `DerivedFactStore.java:48`
  (derived facts) and `core-store.adoc:824` (lessons) are ambiguous. PLAN-V2-114 Deliverable 4 renumbers the lesson pair.
- **Checked and fine:** the integrity states (`verified()` hashes and decodes the same bytes; a mismatch maps to Tampered, else
  `DeletedOutOfBand`; `unclaimed()` requires `VERIFIED`); no id reuse after an out-of-band delete; resources closed; the staged
  file is never a lesson (`LessonId.fromFileName` requires the `.md` suffix and the id grammar).

### Verdict

D1 to D5 shipped and the tests assert real values. **No data-corrupting defect**, but the merged tree carries C1 and C2, whose
fix sits in the unmerged #289, and C3 and C4 which #289 does not close. Three of the findings of PLAN-V2-11 (S3, empty-shell
wording, the round trip C1/I1) are resolved; the DEL character (I2) is the round trip's remaining hole. Owners: C1, C2 — #289
(the operator merges it); C3, C4, I2, S2, I3 to I5, S5, S6 and the ADR numbers — PLAN-V2-114 (Deliverables 4 and 5, waiting for
#289); S1 and S7 — nobody (specification wording). They go to the operator, and become a staged fix-plan spec only on the
operator's word (the operator's decision of 2026-11-04 "Other findings: create a fix plan" is read as covering these; undo by
removing Deliverable 5).
