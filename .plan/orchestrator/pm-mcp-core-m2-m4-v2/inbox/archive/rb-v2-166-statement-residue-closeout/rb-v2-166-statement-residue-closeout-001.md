envelope_version=1
sender_type=plan
sender_id=rb-v2-166-statement-residue-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-22T18:31:34Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-166-statement-residue-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#564
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=5
review_body_findings_handled=5
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

# Landing — rb-v2-166-statement-residue-closeout

Standing rules: read at **r7** (the spec's First Instruction names r6; the rules file's own § How to read these rules
says a starting plan picks up the current revision). Rules that bind this plan: R02, R05, R06, R07, R08, R10, R11, R13,
R17, R18, R19, R20. R20 skills read before any work: `java-core`, `java-null-safety`, `javadoc`, `junit-core`,
`cui-testing` (with their standards).

PR #564, merge commit **`cc52de5f45ac6b2751014916322254a1ab43fbaf`**, merged through the merge queue after a base merge
of `main` at `c0d14b96`.

## 1. Deliverables

**D1 — the requirement sentences the R05 reduction deleted (carried 82a). Met.** All four named sentences are back in
`doc/specification/store-schemas/10-job-and-lesson-records.adoc` (Lock, Claim, Retirement, Lesson Maintenance,
Derived-from-Untrusted), plus "no default", "whatever the verdict" and the minimal-front-matter procedure of an operator
retirement. Every other sentence the five bullets lost against `4d4ada41` was checked and is stated where the bullet's
links point (`state-catalogue/09:21,288,295`, ADR `core-store/079:40-50`, `core-store.adoc:931-944`). Schema block, kind
tables and anchors are byte-stable (R10; the schema-parity tests pass). `order: none` (prose; no test can fail on it).
Commit `9d602bdd`.

**D2 — the cut-log label and the compatibility shims (carried 82b). Met.** `order: mutation-after`.
- `TestCounts.Measured.tailOnly` and the 5-argument compatibility constructor and `from` are gone; `parse(Path)` reports
  `TestCounts.Unmeasured` for a cut log. Test `BuildLogTextTest.cutMavenLogReportsUnmeasured`; failing run commit
  `271e89b1`, log `171b0508999b4fb180d5e2f15a597643`.
- A cut log never reads as `noTestsRan`. Test `BuildLogTextTest.cutMavenLogNeverReadsAsNoTestsRan`; failing run commit
  `875b9d98`, log `fe8866d3c9e040989810dc4b8f28c81b`.
- The visible failing count stays evidence (CodeRabbit's major finding). Test
  `BuildLogTextTest.cutMavenLogKeepsAVisibleFailingCount`; failing run commit `25374e48`, log
  `b7f423b5324f432885d03743e7934859`.
- Commits `271e89b1`, `875b9d98`, `25374e48`, `702c8a3f`.

**D3 — ADR 009's tense and the three test losses (carried 82c, 82d). Met.** `order: mutation-after`.
- ADR 009's Context is in the past tense (commit `9bd1898a`; prose, itself `order: none`).
- `ProfileStagesTest.endpointVerificationRefusesNullClasses` (the case #555 renamed, settling commit `480a43d4`) asserts
  the refusal and its message; failing run commit `9bd1898a`, log `67a3c91a288645db87fffb45539514c9`.
- The omitted-reference refusal is covered by `ScenarioReplayRunnerTest.shouldRefuseTheSubmitWithoutTheReference`
  (failing run commit `9bd1898a`, log `38cd83c8d4b64cb1af7ebaa04cb26d03`) and
  `ConsultationServiceTest.submitValidatesConsultationIds` (same mutation, log `a13d56ae22de4ef990bf577d7fe6a910`).
- The asker-status and lease assertions are restored in `ConsultationServiceTest.askerWaitReoffersTheTaskWithItsLease`;
  failing run commit `9bd1898a`, log `fe345e35a21c4d8eb0ccbab610b96674`.
- The group-signal loss is covered by the structural `ProcessTreeTrackerTest.dependsOnNoGroupSignal` (ArchUnit: no
  dependency on `ProcessGroupSignals`); failing run commit `9bd1898a`, log `5fdd4c3602f64370ac5413f3e56feb61`.
- Commit `9bd1898a`.

**D4 — the unbounded loop, the long lines, the links check and the outcomes (carried 82e, 82f). NOT fully met as
written.** The bound, the line breaks and the outcomes are met; the links clause is not.
- `freeId()` is bounded at `MAX_ID_DRAWS` (64) and refuses `consultation_ids_exhausted`; `order: mutation-after` with
  `ConsultationServiceTest.seamOfTakenIdsIsRefused`, failing run commit `846446a9`, log
  `43d17dff23f2490ea404602c13a618f8` (the old loop timed out after 5,000 ms).
- The 31 lines that #553–#556 added over 120 columns are broken; measured in characters before (31) and after (0).
  `order: none`.
- **Links check: not met as written.** Run from the main checkout as R17 and the Done-when state, it reports
  `INLINE SCHEMA BLOCKS 0` and `BROKEN LINKS 97` — every one a cross-repository
  `https://github.com/plan-marshall/pm-mcp-clients/...` link plus three anchors of that checkout, which the tool cannot
  resolve offline; **0 local links are broken**, and the one link this plan adds (ADR `core-store/075`) resolves. The
  clause asked for `BROKEN LINKS 0`, which this host cannot produce (PLAN-V2-164's landing hit the same 97). Reported
  as not met, not narrowed.
- Outcomes 14(d), 16, 20(a), 67(c) and the six weakened-test removals are recorded in § Residue below.
- Commits `846446a9`, `6949ab1d`, `bef4d37c`, `138d5964`, `fa7db3af`, `b572166a`, `702c8a3f`.

Per-head test runs: `verify` green on `138d5964` (12,013 tests, log `a4a28bc2450b440cb43ee04985899b28.log`), on
`fa7db3af` after the base merge (12,629), and on `702c8a3f` (12,630, log `586f58b690ff46dea4f85b356027f55c.log`);
`verify -Ppre-commit` green before each of those heads and its rewrites committed.

## 2. Personas and skills

- Production code (D2, D4): `persona-implementer`. Tests: `persona-module-tester`. PR triage and the self-review:
  `persona-code-reviewer`. Documentation (D1, D3's ADR, the guides): `persona-documenter`.
- Per deliverable: `skills: none named` — the spec declares no `Skills:` line. The R20 skill set above was read and
  applied. `skills added: none` (no surface drift outside the declared modules).

## 3. Hypotheses

- D1: the R05 reduction deleted requirement sentences of `store-schemas/10` — **confirmed**, `4d4ada41..HEAD` diff of the
  five bullets (`doc/specification/store-schemas/10-job-and-lesson-records.adoc:40,54,56,57,61` at the head before the
  fix).
- D2: the LEAD that the assembly does not read `tailOnly` — **not established and moot**: the operator chose the
  explicit absence (R07), which removes the flag, so nothing can read it (decision recorded below).
- D3: ADR 009's Context was present tense though superseded — **confirmed**,
  `doc/adr/009-no-build-skip-for-a-specification-only-pull-request.adoc:14-28` at the pre-fix head.

## 4. Findings

Self-review (two rounds, `persona-code-reviewer` sub-agent, read-only):

1. `ReservedKeywordsSpecificationParityTest` `throws Exception` + double blank line — **refuted as the lane's edit**: a
   second independent `verify -Ppre-commit` run reproduced the exact shape (`138d5964`), so it is the parent POM's recipe
   output; the lane committed the gate's output.
2. ADR 011's "never `noTestsRan`" guarantee untested — **fixed** (`875b9d98`, new case + mutation run).
3. The cut path scanned a log and discarded the result — **fixed** (`875b9d98`), then **re-broken deliberately** when
   finding (5) needed the scan's visible failing count (`25374e48`, ADR 011 states why).
4. ADR 028's caller count — **fixed** (`6949ab1d`).
5. ADR 011's slug — **recorded** in the ADR's Status.
6. `freeId()`'s check-then-act race across the transaction — **pre-existing**, out of this plan's surface (observation).
7. The guide-link nit and the commit-subject grouping — preferences, recorded, no change.

CodeRabbit at `fa7db3af` (5 findings, all dispositioned; `review-gate` reads 0 unresolved threads and 5/5 handled):
- inline `spec:54` `stalled_lessons` population — **no change**: the specification states the requirement, ADR
  `core-store/067` records the missing producer, and carried item 82a names this wording for restoration.
- inline `MavenOutputParser:270` cut log losing the failing-test contradiction — **fixed** in `25374e48` (major).
- inline `JobLog:210` short content read — **pre-existing** (#555's read logic), recorded as a follow-up.
- inline `workflow-consult.adoc:52` duplicated refusal list — **no change**, developer prose, `ConsultationRefusal` is
  the single definition.
- outside-diff `RoleNameSingleDefinitionTest` JSON `\u` escapes — **pre-existing** gap of #555's scanner, recorded as a
  follow-up.

SonarCloud: one new-code smell (`java:S6126`, the concatenation in the new case) — **fixed** in `702c8a3f` with a text
block. Re-read on `702c8a3f`: 0 open, 0 hotspots.

## 5. Reviewers

- **coderabbit** (required, `quota_limited`, `review_complete_after_handling`): reviewed `fa7db3af` — a formal
  `COMMENTED` review with that commit id and the `final_review_risk_coverage` marker naming it. 5 findings, all
  dispositioned, 0 unresolved threads; `review-gate --required-reviewer coderabbit` → `merge_allowed: true,
  all_handled`. Triggers: `@coderabbitai review` at setup and again after the base merge; the second was answered
  "already reviewed the last commit" (incremental bot), and the flag's done-exemption applies, so no further trigger was
  posted for the later heads.
- **sourcery** (optional, `quota_limited`): refused on its own review budget twice ("250,000 diff characters for the
  last 7 days; another review in about 20 hours"). Optional, so not re-tried.

## 6. Operator questions

1. "Deliverable 2 — what should a cut build log's test count report?" Options: make it `Unmeasured` (recommended) /
   keep `Measured` and consume the flag / keep it because the assembly reads it. Selected **"Make it Unmeasured"**.
2. "May the 5-argument `TestCounts.Measured(int,int,int,int,Map)` constructor be dropped?" Options: drop (recommended) /
   keep. Selected **"Drop it"**.
3. "The Done-when requires the R17 links check, which runs the sibling documentation repository's `trace.py` by path, so
   the lane's executor rule bends. Run it?" Options: run (recommended) / skip. Selected **"Run trace.py as R17 states"**.

No free-text operator messages. No other question was needed.

## 7. Rule check

- **Executor only**: bent once, by the operator's selection above — `trace.py` of `plan-marshall-documentation` was run
  by path for the links check (R17 and the Done-when require it; the project's executor map has no such script). Every
  other script went through `.plan/execute-script.py`, every build through the `build` map, every CI action through
  `plan-marshall:tools-integration-ci:ci`.
- **Harness guard**: `guard: none` on this harness (unenforced). No guard log exists for this run, and this report does
  not claim "all kept" from one.
- **Shell file operations**: this run issued one `ls` at the start (before the rule was re-read in full), one `head` and
  two `grep`s over build logs outside the repository (the `log_file` the build result names), and one shell redirect of a
  `git diff` into `.plan/temp/`. All other reading and searching used the file tools. The two `-pl` builds that hit a
  stale `~/.m2` `pm-core` were wrong invocations re-run with `-am`; they were not launch faults.
- **Builds**: 30 invocations, **every one** `mechanism=daemon_longpoll` (routed); 0 `in_process_fallback`; 0 degraded
  results; 0 launch-fault retries; 0 killed jobs. Nine were mutation runs that were meant to fail.
- **Spec read**: `spec_source: ledger_worktree`, no `spec_differ` marker (the two copies agreed).
- **`executor-skew`**: `match` at setup (executor 0.1.2183 = lane 0.1.2183). The landing-time `resolve` reads
  `executor_version 0.1.2184`, so the executor was regenerated by another session during the run; no `unknown_verb` or
  argparse rejection occurred.
- **`.plan/` writes**: `.plan/temp/` (progress, drafts, the diff for the line measurement), the CI body slots the
  `prepare-*` verbs returned, and the `.plan/orchestrator` files the base merge carried from `main`. No spec or ledger
  file was edited.
- **Commit trailer** on every commit; **one PR**; **no dependency or plugin**; **no stored datum**; the branch's 31
  changed files stay under the 100-file bound.
- **Restarts**: none; no sub-agent was killed; the self-review was two sequential rounds.

## 8. Build server

- Setup preflight: `ready` (enrolled, version 1).
- Per mechanism: `daemon_longpoll` 30, `in_process_fallback` 0. No fallback, no retry, no `queued_behind`, no
  `directory_busy`, no bound expiry.
- `runtime_stamp_reason`: absent (the stamp was readable except `plugin_version`, which is `unknown` in the resolve
  result itself).

## 9. Residue (the carried outcomes)

- **14(d)** — no caller of `ActivityJournal.delete` exists under `src/main` of this repository. Whether
  `plan-marshall-mcp` calls it while a second process holds the key is outside this repository's surface: a Watch.
- **16** — `ProductCorpusValidationTest` **skips** in the plan worktree (`pm.product.root` defaults to
  `${maven.multiModuleProjectDirectory}/../plan-marshall-mcp`, absent below the worktree; the full verify log reads
  "Tests run: 1 … Skipped: 1") and **runs green** with `-Dpm.product.root=/home/oliver/git/plan-marshall-mcp` (1 run, 0
  skipped, log `maven-2026-11-22-164051.log:84`).
- **20(a)** — ADR `core-id/001`'s 23 classes and 25 literals are the pre-refactor history; at the head one literal
  (`ShortName.TEXT`) remains with 24 carriers (the 23 replaced copies plus `IntegrationName`), and the five `{0,31}`
  spellings the ADR names exist unchanged; the guard `RoleNameSingleDefinitionTest` holds.
- **67(c)** — the `SchemaValidation` thread-safety claim rests on the one static registry never being modified after
  construction; no test proves concurrent validation. ADR 028 said "fifteen calling classes"; the test tree has fourteen
  (`CorpusImportTest` consumes `ItemGeneratorTest.violations` and calls no helper) and the ADR is corrected here.
- **The six weakened-test removals** PLAN-V2-163 called accepted: (1)
  `ProfileStagesTest.endpointStagesWithoutClassesRefused` → `endpointVerificationRefusesNullClasses`, which asserts the
  message; (2) five `verify(signals)` pins of `ProcessTreeTrackerTest` → the structural `dependsOnNoGroupSignal`; (3)
  `ScenarioReplayRunnerTest.shouldAcceptTheSubmitThatNamesTheReference`'s asker-status and lease assertions → restored in
  `ConsultationServiceTest.askerWaitReoffersTheTaskWithItsLease`; (4) `UnitGraphCycleScaleTest`'s uncapped assertions →
  the capped case (65,536, ADR `workflow-verify/041`); (5) `PlanHistorySchemaParityTest`'s hyphenated outcome →
  `PlanHistoryTest.hyphenatedOutcomeRoundTrip` and the parity cases at `:236-251`; (6) `JudgeCheckTest.tokenSplitRefused`
  → still present at `:239`.
- **Open follow-ups this run records, not fixes**: (a) `JobLog.readWhole` never compares the bytes read with `size`, so
  a shrunken-then-regrown log can read `Complete` with a short text (CodeRabbit, pre-existing #555 logic); (b) the
  `RoleNameSingleDefinitionTest` scanner does not decode JSON `\uXXXX` escapes before comparing grammar spellings
  (CodeRabbit, pre-existing); (c) `freeId()`'s free-id check still runs outside the transaction, so two concurrent opens
  can draw one id and the loser fails after the asker moved to `consulting` (pre-existing).
- **Inbox**: `mailbox_state: no_mailbox`, `delivery_state: never_delivered` at all three reads — an absent mailbox, not
  an empty one. No item was delivered, so no disposition line exists to block the enqueue.
- Nothing else is owed by this plan.
