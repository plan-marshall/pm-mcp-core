# Landing Analysis: PLAN-V2-140 — the verify duration measured and cut, the test design audited

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
pr: #403 (merge `91a131f9`), `merged` through the merge queue, no label

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-13 from the messages
> `plan-v2-140-verify-duration-and-test-design-001.md` to `-005.md` of the plan lifecycle (harness claude, model
> claude-sonnet-5-5/medium, executor 0.1.2114). The landing message is a lead. The three layers were read by one
> read-only leaf from git at `origin/main` `62bf986d` (no build, no test run); the orchestrator re-read the points marked
> "re-read"; the provider figures were re-read from the SonarCloud public API and `gh pr view 403`.

## Deliverable Fidelity vs Spec

The spec file holds five deliverables; the lifecycle's outline took the operator's decision at refine (the test-design rework, the spec's Open Item 3, "as a further plan" at about 70%) in as a sixth, and the landing counts seven. The spec file was not updated for that decision.

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 baseline | partly | ADR 024 (`baseline-runs`, lines 31-72): five runs on a shared 16-CPU host through the build daemon: 845 s loaded, 654 s idle, 650 s idle for `verify -Ppre-commit`, 1278 s for `verify -Pcoverage`, 138 s for `-DskipTests` (loaded). **The 22 minutes of the request are the coverage lane, not the gate** (the gate is 650 s, 79 % of it the test phase). Gaps: one loaded run gives the whole non-test split, no separate compile, test-compile and test phase runs, plain `verify` and the integration lane unmeasured, per-class times from the coverage lane only, variance from one idle pair 3 s apart. |
| D2 ranked analysis | met | 18 rows with seconds, cause and decision (ADR 024 lines 228-388); row 18 dropped because #396 deleted the class. |
| D3 build-level fixes | met | one change, measured: `reuseForks=true` in the root `pluginManagement` (`pom.xml:173-186`, re-read: 14 lines, the surefire plugin only; no plugin, dependency, profile or property is added, so the CLAUDE.md rule "never add a dependency or a plugin without asking" is not touched); runs 6 to 8: 256, 243, 247 s. `-T` and `forkCount` were considered and not measured. |
| D4 test-level fixes and retention table | narrow | only `LspClientTest` was changed in this deliverable; the rows kept are kept with reasons; **no test was removed, merged or moved and the retention table is empty** (`doc/adr/024-...adoc:663-668`, re-read: "none"; ADR 025 lines 461-466). The operator's conditions "no test duplication" and "quality must not fall" were not exercised: zero duplicates found is plausible, but nothing was achieved by merging. |
| D5 result and findings | met | final runs 235, 239, 229 s (median 235 s), coverage lane 123 s, per module; F1 to F14 and the target design S1 to S9 in ADR 025. |
| D6 test-design rework (added at refine) | narrow | three `QuestionLogRecordReader*Test` classes moved from `TestBases` to `@TempDir`, method counts 5, 20 and 9 before and after, `runtime-question.adoc` corrected; everything else kept with a reason or recorded as an operator lead. |

**The title of #403 ("cut the verify to 600 s and rework the test design") overclaims the second half.** The first half is true and measured: the gate fell from 650 s to about 235 s (minus 64 %), the coverage lane from 1278 s to 123 s, with the per-module coverage unchanged (`pm-core` 92.73/85.76 against 92.72/85.73, the others identical; no module below its baseline after the rebase).

## Three-Layer Analysis

Read at `62bf986d` by one read-only leaf (`execution-context-level-3`, 27 tool uses); the orchestrator re-read the `pom.xml` lines, the empty retention table, the `LspClientTest` bound and the `LspClient.start` await, and the pull request body.

### Layer 1 — Specified

- The spec's premise ("22 minutes, cut it in every module") was not true for `verify -Ppre-commit`; its own HYPOTHESIS (line 105) already doubted that test execution was the main cost. The measurement refuted both, and the title takes the target (600 s) for the result (about 235 s).
- **The root cause is in the parent POM and the spec could not reach it:** `pm-mcp-parent` 0.1.1 sets `reuseForks=false` (parent pom line 242), which starts one JVM per test class; the constraints forbade a parent change, so the only lever was a local override.
- D4 asks for "no duplication" and merges but forbids rewriting untouched classes, which forced a findings-only outcome.

### Layer 2 — Implemented

As the table above. Spot-checks of ADR 024/025 claims all hold (`ProbeBudget.VERSION_SECONDS` 15 at `ProbeBudget.java:32` with no clock seam, `TerminationProtocol.GRACE_PERIOD_SECONDS` 5, `GitLabClient.MAX_PAGES` 50, `-T1` in `.mvn/maven.config`, 79 `*BuilderContractTest` in `pm-core`, 44 `*RecordsTest` in `pm-runtime`, 15 `TestBases` users). Small drift after the ADR snapshot: `*SchemaParityTest` 21 and 3 (ADR 18 and 2), generator tests 5 and 4 (ADR 4 and 3), `*RecordsTest` in `pm-workflow` 56 (ADR 55). ADR 024 line 440 lists "system properties" for three classes that set `Locale.setDefault` and restore it. ADR 025 F9 is accurate (`RecordingAuditSink` in `pm-core` `core.store` and `pm-runtime` `lock`, `RecordingLockManager`, `RecordingProcessCall`); a further twin, `RecoveryStepFixtures.RecordingAuditSink`, is not named there and is named by PLAN-V2-136.

### Layer 3 — Correct

No bug in the three `QuestionLogRecordReader*Test` classes (the `@TempDir` field is per instance and works with `@Nested`; `toRealPath()` kept; the symlink the Read test creates is not followed by the cleanup; no socket is used). Findings:

- **`LspClientTest.java:57` sets `WAITED_BOUND` to 2 s, and the same bound limits the `initialize` await of `LspClient.start`** (`LspClient.java:128`, re-read). The fixture JVM needs 0.3 to 0.5 s idle and about 1.3 s under load (the ADR itself measured a load of 38), so the margin is about 0.7 s; a slow host fails `timeout()` outside its `assertThrows` and makes `startFailures` pass for the wrong reason. Before #403 both had 5 s of headroom; the saving was about 6 s of 235 s.
- **Shared JVM, shared static state.** With `reuseForks=true` the classes of one module share a JVM (JUL handler of `@EnableTestLogger`, default locale, the process-global JGit reader). The evidence that nothing leaks is eight green runs of the final configuration; the audit was a content search. The locale setters restore state. Failsafe is untouched (still one fork per IT in the parent) and surefire is skipped under `-Pintegration-tests`; the coverage lane was measured green; CI behaviour is not measured.
- Test counts: runs 6 to 9 count 10 200 tests against 10 239 in run 4, and the ADR explains only the 50 lost between 10 200 and 10 150 (#396, #397); the other 39 are unexplained (a measurement note, no test is known missing).
- **The pull request body states 289 s for the pushed tree (10 227 tests) and about 235 s in its summary; ADR 024 reports 235 s measured on `00feac45`, not on the shipped tree, and records no 289 s** (re-read). Neither explains the other; the likeliest reading is a loaded host or the rebased tree, not stated.

## Conformance to the Project's Own Rules

Review: CodeRabbit left one summary issue comment and no thread, Sourcery refused on quota (optional); the plan's own functional review (3 met, 4 met with residuals, 0 not met) and code review (2 advisories) ran twice, because a documentation-correction commit after the first round advanced HEAD and the pre-merge barrier refused (`merge_candidate_not_gated`); both reviews were re-run on the new HEAD. The spec file is stale against the landing (five deliverables against seven); an operator decision at refine is not recorded in the ledger spec.

## Unverified leads

- CI behaviour with `reuseForks=true`; plain `verify` and the integration lane (not measured); parallel options `-T` and `forkCount` (not measured).
- The 39 tests between run 4 and run 6; the 289 s against 235 s.
- The outline of the lifecycle was not read (the sixth and seventh deliverables are known from ADR 025 and the landing message).

## Metrics and Anomalies

`total_tokens` 22 806 764 (includes cache reads and sub-agent transcripts), `total_wall_seconds` 63 150 (about 17.5 h), harness claude, claude-sonnet-5-5/medium, plugin and executor 0.1.2114. One envelope dispatch was killed by the weekly API limit (HTTP 429) and left no boundary row; its resumed return has none either; 14 of 14 `execution_log` rows record `total_tokens=unmeasured`. The branch needed a manual rebase after `baseline_drift` twice (lesson 1). Review counts: unresolved threads 0, body findings 0 of 0, Sonar `pullRequest=403` 0 open and gate `OK` (re-read), `main` open 83 at the last read.

## Routing and Merge Behavior

One pull request through the merge queue. Four `candidate-lesson` messages: three routed to plan-marshall's `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-028` to `-030` (the drift-recovery loop has no rebase step, so a conflicting drift cannot clear and loop 2 repeats loop 1; no dispatch-boundary row for finalize or cancelled dispatches and no returned usage; a review-prompted correction commit does not invalidate the review steps, so the stale review state went through five finalize steps until the merge barrier); the fourth (the `trace.py` link check of `CLAUDE.md` does not run from a plan worktree, six leaf dispatches re-found it) concerns this repository's `CLAUDE.md` and is placed in PLAN-V2-137.

## Reconciliation Actions

Row PLAN-V2-140 `running` -> `shipped`, `pr` `#403`, `landing` `landings/PLAN-V2-140.md`, `plan_marshall_plan_id` `plan-v2-140-verify-duration-and-test-design`; the five messages archived; Watch entry in `epic.md`; decision log; resume anchor. A new staged plan, PLAN-V2-141 `test-design-rework`, was cut (see Follow-Ups).

## Follow-Ups

Placed 2026-11-13 under the operator's rule "every leftover goes into a staged plan, or a regular cleanup plan when none fits":

1. **New plan PLAN-V2-141 `test-design-rework`** (WS-11, runbook lane): the operator's "eventually rework the test design" had no plan; ADR 025's kept findings F1 (the generator-based builder check), F5 (`@Nested` form of the question-log and project-resolution tests), F7 and S7 (shared fixtures, `TestBases` to `@TempDir`) and the empty retention table. Runs after 136, 125, 126, 101 to 105 and 137 (same test trees). Ask-at-refine: fixtures in `pm-core:tests` (a change of ADR 012; recommendation one fixture class per module, about 60%), `toBuilder` on `ProjectEnrolment` (recommendation add, about 65%), how far the builder check goes (about 80%).
2. **PLAN-V2-114 item 19:** the `ProbeBudget` clock seam (a production change for the tests; recommendation add, about 60%).
3. **PLAN-V2-126:** the `LspClientTest` bound and the static-state audit of `pm-runtime`; **PLAN-V2-125:** the same audit for `pm-core` and `pm-workflow`, the ADR 025 counts, the 39 tests.
4. **PLAN-V2-137 item 3:** the `trace.py` command in a form that holds from any worktree (recommendation the `CLAUDE.md` form from the common git directory, about 70%).
5. **Not carried by any plan of this repository (told to the operator):** the parent POM's `reuseForks=false` (cause of the 650 s) and the forked `pre-commit` lifecycles (root 13.5 s) live in `pm-mcp-parent`; the operator may take them up there. The measurement of plain `verify` and the integration lane and the parallel options (`-T`, `forkCount`) are lever lists in ADR 024 with no owner.
