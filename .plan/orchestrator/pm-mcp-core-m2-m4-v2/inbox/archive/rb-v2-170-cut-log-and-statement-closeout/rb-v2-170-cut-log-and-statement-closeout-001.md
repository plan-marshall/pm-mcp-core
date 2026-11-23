envelope_version=1
sender_type=plan
sender_id=rb-v2-170-cut-log-and-statement-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-22T22:51:25Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-170-cut-log-and-statement-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#570
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=4
review_body_findings_handled=4
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

# Landing: PLAN-V2-170 — the closeout of the cut build log

Standing rules: r7, R02, R05, R06, R07, R08, R10, R11, R13, R17, R18, R19, R20 read in full before the first edit.
Spec read from the shared ledger worktree (`spec_source=ledger_worktree`, no `spec_differ` marker). `executor-skew`
verdict `match` (0.1.2183 on both sides at start; 0.1.2184 at landing). Harness opencode, model
`deepseek-v4.1-flash/high`, effort high.

## Deliverables

1. **The cut flag in the daemon path (88a, L3-1) — met.** The hypothesis held: `JobLog.readWhole` cuts a log over
   `JobLog.MAX_BYTES` and hands the tail to `BuildOutputParser`, which called the never-cut `parse(String)`. The
   operator's refine answer ("Pass the cut fact") is implemented: `JobLog.Complete.cut()` (`length > MAX_BYTES`),
   `SubprocessJobRunner` passes it through `OutputParser.parse(job, output, outputCut)`, `BuildOutputParser` gives it to
   the new `MavenOutputParser.parse(String, boolean cut)`, and a cut job log reports `TestCounts.Unmeasured`.
   Tests: `MavenOutputParserTest.cutTextReportsUnmeasured`, `JobLogTest.overTheCap`/`atTheCap` (`cut()`),
   `BuildOutputParserTest.cutMavenLogContradicts`, `SubprocessJobRunnerTest.parserReceivesTheCutFact` (over-cap log,
   real `JobLog`) and `parserReceivesTheRecord` (small log → `false`). Order `mutation-after`: the cut forwarded as
   `false` fails `cutMavenLogContradicts` (log `e9e4918d23104c598afcd1c90e7e724d`), the hand-off as `false` fails
   `parserReceivesTheCutFact` (`48aa779e803544c1b7aa712f8ed79349`), the hand-off as `true` fails
   `parserReceivesTheRecord` (`d1ff0965081f4e2cb7026bb37bf7ab37`). Commit `b07b4694`, refinement `c8e9b4e6`.
2. **The failing count of a cut tail (88b) — met.** A cut log counts the failing tests of a marker-less summary, files
   each failure once, and keeps a failure whose execution reported only a passing summary. The figure is the larger of
   the tail's summary side and its per-class lines, reduced by the `[ERROR]` count lines the collector already filed;
   the synthetic message says "further" when it is a remainder. ADR `core-build/011` states absent vs. unmeasured.
   Tests: `cutMavenLogCountsAnAmbiguousFailingSummary`, `cutMavenLogFilesTheFailingCountOnce`,
   `cutMavenLogCountsAPerClassLineAndItsSummaryOnce`, `cutMavenLogFilesTheFailuresItsErrorLineDoesNotReport`,
   `cutMavenLogFilesNoSecondFindingForItsErrorCountLine`, `cutMavenLogFilesNoSecondFailureForAFiledPerClassLine`,
   `cutMavenLogKeepsAFailureWhoseSummaryIsPassing`, plus the retained `cutMavenLogReportsUnmeasured` and
   `cutMavenLogNeverReadsAsNoTestsRan`. Order `test-first` for the first four (failing run
   `a43b8abfe57342bb9a6d5b835d2b39d4` at base `f0afe6c6`), `mutation-after` for the counting rule itself
   (`5dc25d25d2a546e98ea92d0be8f39640`, `e8142faecae1466b8463a75a5ad5fb44`). Commit `b07b4694`, refinements
   `809c2927`, `25d97884`, `c8e9b4e6`.
3. **The statements that still said tail-only, and the lost sentences (88c) — met.** The `core.build` `package-info`,
   ADR `core-build/007`, `job-runtime/03-parsers-and-gates.adoc` and the developer guides say unmeasured; ADR
   `workflow-consult/005` states the `MAX_ID_DRAWS` bound, the `consultation_ids_exhausted` refusal and the queue lock;
   ADR `core-build/011` is renamed to `011-a-cut-build-log-reports-unmeasured.adoc` and every link follows;
   `store-schemas/10` restores the Retirement sentence and the Derived-from-Untrusted enumeration with links to the
   state catalogue. No schema block, kind table or anchor changed (R10); the schema-parity tests are green. Order
   `none` (documentation); links check below. Commit `296994f5`, refinements `809c2927`, `c8e9b4e6`.
4. **The guards that passed silently and the races (88d) — met.** `ProcessTreeTrackerTest` finds its target class and
   imports both nested records (mutation: dropping `ProcessGroupSignals` from the import fails it,
   `e350dc7c47124993bde60dbfdaa4e74b`); the role-name guard decodes `\uXXXX` and its test fails on the escaped
   spelling before the fix (`test-first`, `a43b8abf…`); `JobLog.readWhole` compares the bytes read with the length and
   re-reads the size after the reads (mutation: the size-only check fails `shrinkingLogIsTruncated`,
   `5e3cdadc4ebb4b9793a90610dd7fcc4a`); `ConsultationService` draws its id inside the recording transaction and counts
   recorded consultation ids (mutation: the outside-`queue.read` form fails both tests,
   `9212da1555614a0e931fdef210946ecd` and `b5191c0228f549c18b7fc08d35b1f54b`). Commit `fd1877dc`, refinements
   `809c2927`, `8ec09726`, `c8e9b4e6`.

Every pushed head was built: `fd1877dc` (module-tests + verify), `809c2927` (both), `8ec09726` (both), `1c7980aa`
(module-tests), `c8e9b4e6` (both), merge head `f2ba18d5` (both, 12 663 tests). `25d97884` is the one head whose local
run was red (the new over-cap test exceeded the 20 s settle bound); `8ec09726` fixed it. Final: `module-tests`
12 663 tests (`4e0ba64dbe7a40078e6d8736d4ce3df8`), `verify` 12 663 tests with compile/lint/test
(`f16e9209d82043c89ce003e24556333b`).

## Personas and skills

`Persona:` and `Skills:` lines: none named by the spec (`skills: none named`). Applied as standards: `java-core`,
`java-null-safety`, `javadoc`, `junit-core`, `cui-testing` (the spec's R20 list, read before the outline); the
implementer persona for the production code, the module-tester persona for the tests, the documenter persona for the
guide/ADR/specification edits, and the code-reviewer persona for every self-review round and the PR triage. Skills
added for surface drift: none — the extra files (`pm-runtime/doc/developer/runtime-job.adoc`, ADR `runtime-job/015`,
the seven lambda-arity tests, `ConsultTestLocks`) are the package documentation and mechanical consumers of a changed
seam, recorded here as undeclared surface rather than as new domains.

## Hypotheses

- That a job log over the cap reaches `parse(String)` already cut: **confirmed** (`JobLog.java:228` `capped`,
  `SubprocessJobRunner.java:1577-1582`, `BuildOutputParser.java:141`; `Complete.length` is the file's size).
- That an ambiguous summary line of a cut tail files no failure and that a filed `[ERROR]` count line gets a synthetic
  one more: **confirmed** (`MavenOutputParser.java` `visibleFailures`/`withVisibleFailures` at the base).
- The two "possibly lost" sentences: **confirmed missing** from `store-schemas/10` (Retirement bullet and
  Derived-from-Untrusted bullet) and restored with links.
- That `\uXXXX` is not decoded by `RoleNameSingleDefinitionTest`: **confirmed** (`unescape` handled only
  `n t r b f`), fixed by decoding and pinned by a test.

## Findings

Self-review round 1 (code-reviewer sub-agent): 14 findings — a blocker (cut-tail double count of per-class plus
summary failures), three majors (the runner hand-off untested, the public two-arg shim, a false spec clause), and ten
minor/nit items (ADR `runtime-job/015` stale, an untrue consultation sentence, over-claimed Javadocs, a partial filed
count, a fixture shape, a dead method, a truncation message, an anchor, a fixture). Round 2: no blockers, two new
minors (the synthetic message semantics, the one-directional cut guard) and a `@Builder` nit. Round 3: nothing open.
All were fixed in `809c2927`, `25d97884` and `8ec09726` except F5 (transactional placement), which round 2 accepted as
an evidence statement and round 3 then closed with the deterministic lock-state test (`consultationIdIsDrawnUnderTheQueueLock`).

CodeRabbit (required): two inline threads (a shrink after the content read; the id reserved by a failed creation) and
two review-body findings (a per-class `[ERROR]` duplicate; failures of an execution with a passing summary). All four
are fixed in `c8e9b4e6`, each with its disposition line; both threads were answered and resolved:
`coderabbit inline inline-1 — c8e9b4e6`, `coderabbit inline inline-2 — c8e9b4e6`,
`coderabbit minor minor-1 — c8e9b4e6`, `coderabbit additional additional-1 — c8e9b4e6`.

Sonar: `java:S6218` (array component of the new `Segment` record) and `java:S1905`/`java:S2184` on the byte-count
arithmetic were fixed in `1c7980aa` and `b7f81588`; the head reports 0 open new-code issues.

## Reviewers

- **coderabbit (required, quota-limited, `review_complete_after_handling`)** — reviewed at `1c7980aa`
  (COMMENTED, 2026-11-22T21:49:29Z); every finding handled in `c8e9b4e6`; its marker-bearing summary names that
  head as covered. The re-trigger on `b7f81588` met the plan quota ("next included review available in 46 minutes")
  and no second review exists; the review on record is the review at the later heads, per §7. `review-gate` with
  `--required-reviewer coderabbit` answered `merge_allowed: true` on `f2ba18d5`.
- **sourcery (optional)** — refused on quota (250 000 diff characters used; next in 16 h 31 m). Optional, not gating;
  no trigger is owed.

## Operator questions

One question, asked before implementing deliverable 1: "Deliverable 1 (carried item 88a, the major): how do I close
the cut-flag gap in the daemon path?" with the options "Pass the cut fact (Recommended)" and "Record only, no
behaviour change". The operator selected **"Pass the cut fact (Recommended)"**. No free-text operator message was
received.

## Rule check

- `guard: none on this harness (unenforced)` — the opencode resolve reports no tool-call guard, so no guard log line
  exists for this run; the count below is the run's own record.
- Read-class file operations issued through the shell (rule kept by the file tools, not by this run): `grep` 9 calls
  and `sed -n` 4 calls, plus two compound calls with `;`/pipes (`sed … | cut` and `head …; grep -c …`) used to read
  line ranges and counts. Two of those used a pipe, which §1 forbids. Recorded as the run's breaches; none wrote a
  file or changed a repository file.
- Every executor call ran as `python3 .plan/execute-script.py …` from the main checkout; every build used the resolved
  `build` commands (the project's quality-gate canonical is `verify`, so the plan's `verify -Ppre-commit` maps to that
  resolved canonical and the pre-commit profile itself was not run locally); the links check ran as R17 prescribes.
- `.plan/` writes: `.plan/temp/` (progress, mutation logs' identifiers, this landing), the `ci` body slots returned by
  `prepare-body`/`prepare-comment`, and this one inbox message. No spec or orchestrator file was edited.
- Git: stage-by-name commits, no stash/restore/rebase/forced push; the base was merged (never rebased) when it moved.
- Spec read facts: `spec_source=ledger_worktree`, no `spec_differ` marker; `executor-skew` verdict `match` at start
  (0.1.2183 both sides). Lane text re-read at session start only; no compaction or restart occurred.
- The lane contract was re-read in full at the session start; no restart or compaction happened afterwards.

## Build server

Setup preflight `ready`; every build routed (`requested=auto`, `resolved=routed`, `mechanism=daemon_longpoll`,
`in_process` 0) — 26 routed builds over the run. No fallback and no retry: the two failed ad-hoc selector runs
(`032c728f…` indeterminate selector, `9bbceff6…` compile against a stale installed `pm-core`) were iteration runs
before the watcher gate, not routing degradations, and the failing heads were real test failures. No `killed` job.

## Links check (the operator's clause)

`BROKEN LINKS 3253`, of which **local 6** (all in untouched `doc/plans/`) and **3247 cross-repository/external**;
`INLINE SCHEMA BLOCKS 0`.

## Carried items — outcomes

88a adopted into D1; 88b into D2; 88c into D3; 88d into D4. Recorded and not changed: the enum-body re-indentation
and `throws Exception` of #545's code (the parent POM's pre-commit output, a Watch); the links-check clause (the
operator's); outcomes 14(d), 16, 20(a), 67(c) not re-checked (as the spec records); #564's API changes remain the
epic's Watch.

## Watch for the assembly (`plan-marshall-mcp`)

`SubprocessJobRunner.OutputParser` gained `boolean outputCut` and `MavenOutputParser` gained
`parse(String, boolean cut)`; the assembly's `OutputParser` lambda must add the parameter after this `SNAPSHOT`
deploys (ADR `runtime-job/015` records the amendment). #564's own API changes stay the epic's Watch.

## Owed to the milestone exit (R05)

Concept and user documentation of the slice and the roadmap check-off in `plan-marshall-documentation`; no
specification there changes.

## Residue

- The failing-count figure of a cut tail is an approximation by design: the larger of the summary side and the
  per-class lines. It never loses a visible failure and never files a filed line twice; a tail whose executions mix
  severities could over-count by one block, which the evidence nature of the diagnostic allows (ADR `core-build/011`).
- `Sourcery` (optional) is rate-limited; it can review after 16 h 31 m if the operator wants it.
- The singular/plural cadence of the synthetic message ("1 further failing tests") is carried as a known nit.
- The merge head carried PLAN-V2-167's closeout from `main`; the merge was clean and the full gate ran on the merge
  head. PLAN-V2-162 (which also touches `JobLog`) was still not landed.
