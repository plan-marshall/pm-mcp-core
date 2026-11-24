envelope_version=1
sender_type=plan
sender_id=rb-v2-181-cut-log-and-sweep-third-residue-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-24T15:19:45Z
revision=1
amended=2026-11-24T15:20:53Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-181-cut-log-and-sweep-third-residue-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#599
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

Standing rules: r7, R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 read in full at session start and applied;
R21 binds the orchestrator's analysis, not this plan. Skills read before the work (R20): java-core, java-lombok,
java-null-safety, javadoc, junit-core, cui-testing.

## 1. Deliverables

**D1 — the saturation test that separates the mutant (`pm-core`).** Met.
- 1(a) met, order `mutation-after`: test `BuildLogTextTest.cutMavenLogSeparatesTheLineSumMutantByItsFiledCount`; the
  mutant is `MavenOutputParser.java`'s `saturatedAdd(parse(Failures), parse(Errors))` back to `+` at commit `22ed9535`,
  failing run log `164b5ef2cf8a4db0a1bcbe3cf5898f49` (both assertions fail: no "further" remainder, and the bare
  "1 failing test" appears). Green at `22ed9535` with 42 tests of the class, log `f8833088c2764898bbcf051361e4c879`.
- 1(b) met (statement): the `saturatingCutLogs` bullet now says what each fixture pins — the warning-level `line-sum`
  line files no error-level count and its wrapped negative is re-saturated when it enters the visible side, while the new
  fixture reaches the floor at zero in the subtraction.
- 1(c) met (statement): the strict-summary-win test's comment now says it guards the per-execution subtraction and not the
  side the visible figure takes.
- Commits: `22ed9535`, `3bc56694`.

**D2 — the sweep's fault boundary (`pm-runtime`).** Met.
- 2(a) met, order `test-first`: test `PendingEntryRecordsSweepTest.aFaultedLockReleaseKeepsTheFirstFaultsReach` failed on
  the code before the fix at commit `22ed9535`, log `b8b108588b3248ebbb060b4922f55087` (3 assertion failures: the release
  message replaced the first one, no "may have been committed", "completed by the recovery" present). The assertions were
  strengthened during self-review and re-run against the pre-fix code: 4 assertion failures, log
  `0a8c47f797434ae88a58e63d89f9917a`.
- 2(b) met: `aCommitThatCannotWriteItsJournalLeavesTheReachUndecided` restores the permission in a `finally`, asserts the
  refusal's cause and states the root-runner skip and what then covers the text.
- Commits: `715bcf7b`, `eb4b9919`, `a1c011ed`, `e4efc406`.

**D3 — the two statements that stopped short.** Met.
- 3(a) met: `BuildOutputParser`'s Javadoc cites ADR `core-build/011:90-91` for why the cut fact stays with the Maven
  parser, instead of the near-circular clause.
- 3(b) met: the `SourceManifestCodecTest` comment and ADR `core-store/084` name the whole fold (NFC, upper, lower, NFC)
  and say both forms reach U+0390 after the final NFC.
- Commits: `5bb6f66f`, `fe1634b4`, `cbe7d06d`.

Per-head test runs against pushed heads: `22ed9535` (BuildLogTextTest, 42), `5bb6f66f` and `fe1634b4` and `cbe7d06d`
(`verify -Ppre-commit` for pm-core and pm-runtime: 5751/8494 and 5751/8494), `e4efc406` (pm-runtime 8494),
`c47e5782` (pm-core 5752, pm-runtime 8495).

## 2. Personas and skills

- D1 implementer persona (`persona-implementer`); skills `pm-dev-java:java-core`, `pm-dev-java:java-null-safety`,
  `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`.
- D2 implementer persona; skills as D1 plus `pm-dev-java:javadoc`.
- D3 documenter persona (`persona-documenter`); skills `pm-dev-java:javadoc`, `pm-documents:ref-asciidoc`,
  `pm-dev-java:junit-core`.
- Both self-review rounds and the PR triage: `persona-code-reviewer`.
- skills added: none (no surface drift; 8 files, all declared).

## 3. Hypotheses

- The line-sum mutant is killable by a filed error-level line — confirmed (`MavenOutputParser.java:546-555` stores an
  execution's first value raw, `:611-613` answers false on the wrapped sum).
- The lock's release can fail with another writer's journal present — confirmed by construction in the new test (the
  release answer writes the journal, then throws); the old code re-read it and answered owed
  (`PendingEntryRecordsSweep.java:161-166` before the fix).
- A root runner skips the undecided-arm test — confirmed and stated in the test's Javadoc.
- One spec premise is imprecise: the hiding wrap at `:506` is a long-subtraction overflow (`MAX_VALUE - MIN_VALUE` →
  `-1`), not an `int` wrap. The comment states what the code does; the spec wording is noted under Residue.

## 4. Findings

- Self-review round 1: 12 findings; fixed 1 (the `line-sum` bullet named the wrong mechanism — the wrap is re-saturated at
  `addFailures`, only the filed fixture reaches the floor), 2 (class Javadoc "one test each"), 3 (the release test now
  asserts the first fault's message and the exact seam, not just "the release message is not first"), 4 (ADR 084 line
  width and a correction marker), 8 (the lock-manager seam is an EasyMock mock rather than a lambda). Items 5, 6, 7, 9,
  10, 11 were recorded as observations. Commit `3bc56694`, `eb4b9919`, `fe1634b4`.
- Self-review round 2: two Info nits (ADR 084 line width, the unverified lock-manager expectation), both closed in
  `cbe7d06d` and `a1c011ed`; nothing open.
- Sonar, first CI run: two `java:S2583` bugs on the new fault-boundary lines (the analyzer does not model the
  try-with-resources close). Fixed with an inline `@SuppressWarnings("java:S2583")` and a stated reason (R16(4)); the
  re-analysis reports 0 new issues.
- CodeRabbit: no actionable comments; merge risk Minimal. Sourcery (optional): refused on its own hard quota.

## 5. Reviewers

- `coderabbit` (required, quota-limited, review_complete_after_handling): first trigger `13:00:54Z` — refused ("Review
  limit reached"). Quota policy step 1: re-triggered no sooner than 90 minutes after the refusal, at `14:35:22Z` on
  `e4efc406`; the review finished at `14:40:37Z` with "No actionable comments were generated in the recent review" and
  `kind=reviewed` naming `e4efc406`. The later merge head `c47e5782` is covered by that review under the
  `review_complete_after_handling` rule, and `review-gate` on it is clean.
- `sourcery` (optional): triggered `13:01:15Z`, refused at `14:39:10Z` on its own hard quota (1 day 17 hours). Optional, so
  no re-trigger and no gate.

## 6. Operator questions

none asked. No free-text operator message was needed or received.

## 7. Rule check

- `guard: none on this harness (unenforced)` — the resolve result reported `guard: none`, so no rule is reported as "kept"
  on the strength of a guard log. The run's own actions were checked by hand.
- Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker — the ledger copy was read, and the main checkout's
  copy did not differ.
- `executor-skew`: `match` (executor `0.1.2183`, lane `0.1.2183`).
- No shell file operation in this run: every file was read or searched with the file tools; shell calls were executor
  invocations, `git` subcommands (`status`, `diff`, `log`, `rev-list`, `fetch`, `merge`, `worktree remove`), `date`, and
  the `ci` surface. No `&&`, `;`, pipes, heredocs, loops, `python3 -c`, `sleep`, or `git grep`.
- Direct reads outside the repository: the build job logs the executor named (`~/.plan-marshall/marshalld/job-logs/`) and
  the two evidence-log paths in the PR body; no credential file, config directory or environment dump.
- Restarts: none. Sub-agents: one `persona-code-reviewer` session, two rounds, no kill, no re-dispatch beyond the reuse
  for round 2.
- Pre-commit rewrites: the profile rewrote `FreshnessReconcile.java` and its test on the earlier heads; those rewrites are
  not this plan's and were reverted with file tools (never `git restore`). `main` has since fixed them (`#598`), and the
  final head's gate ran clean with no rewrite.
- Every commit ends with the resolved trailer; the PR body carries no attribution footer.

## 8. Build server

- Setup preflight: `ready`. The routing check answered `auto` (worktree executor present) and every build ran routed:
  23 builds, all `daemon_longpoll`, no `in_process_fallback`, no `degraded`, no launch-fault retry, no `killed` job.
- Gate runs on the final head `c47e5782`: `verify -Ppre-commit -pl pm-core -am` 5752 tests, log
  `5eb2aff156ce450eb2856aedcb69e2e9`; `-pl pm-runtime -am` 8495 tests, log
  `ad91eee45ed14d49bf9fcb1720d8d945`. The pre-commit lifecycle ran (licence, OpenRewrite, import order,
  `check-javadocs-pre-commit`) and produced no diff.
- No `runtime_stamp_reason`; `plugin_version` is `unknown` because the runtime seam returned no value. `inbox
  landing-check` therefore reports `complete: false` on `total_tokens` and `plugin_version` alone — neither is measurable
  in this run, and both are written as `unknown` rather than guessed.
- Targeted runs only: `-Dtest` selections on `BuildLogTextTest`, `PendingEntryRecordsSweepTest`, `SourceManifestCodecTest`
  and `BuildOutputParserTest`, plus the two module gates.

## 9. Residue

- Two Sonar `java:S2583` findings were introduced and fixed in this run (suppression with reason, R16(4)).
- Spec premise: deliverable 1(b) calls the hiding wrap an `int` wrap; the code's is a long-subtraction overflow at
  `MavenOutputParser.java:506`. The comment states the long overflow; the spec's wording should be corrected in the next
  spec that touches it.
- Standing Constraints say "the report text of the sweep does not change", while D2(a) asks the release fault to be
  reported. The implementation appends a clause to the fault message on that path only; the report's own arms (the
  `report()` switch and its wording) are untouched.
- CodeRabbit's title check passed with a note that the title is accurate but not specific about the production change; no
  action taken.
- No follow-up is owed: taken items 101(a), 101(b) and 101(c) are all adopted here.
