envelope_version=1
sender_type=plan
sender_id=rb-v2-08-native-build-parsers-jvm
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-02T09:41:58Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-08-native-build-parsers-jvm
epic=pm-mcp-core-m2-m4-v2
pr=#277
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

Standing rules: r4, R01 … R13, R17, R18, R19, R20 (all read; the plan's own list).

Merge commit `685fdb385ab9ef7847b5e6e9ada596017764ffb6`; PR #277 (which supersedes #273, closed unmerged under the reviewer quota policy).

## 1. Deliverables

**D1 — the result model and the counts that may not be guessed: met.**
`Diagnostic` (file, line, column, message, category, severity and the collected stack trace), `TestCounts` (sealed:
`Measured` with an invariant-checked total and frozen per-runner map, `Unmeasured` with no count accessor), `ParsedBuild`
(five components: diagnostics, status, success marker, counts, verdict), the compiled `JvmCategoryPatterns` table, and
the shared `DiagnosticCollector`/`JvmFileLocation`/`BuildLogText` helpers.
Evidence, order `mutation-after`: `DiagnosticTest`, `TestCountsTest`, `ParsedBuildTest`, `JvmCategoryPatternsTest`,
`BuildLogTextTest`, `JvmFileLocationTest`, `BuildLogFixturesTest`; mutation (c) below replaced `Unmeasured` with a measured
zero and failed four fixtures. Commits `ddadbbca`, `ca5d67d3`, `2f7c8799`, `e01eb874`, `8741b2b3`, `6a8f04cc`.

**D2 — the Maven parser: met.** `MavenOutputParser`: the `[ERROR]`/`[WARNING]` scanner over the compiled table, the
failure trailer dropped whole against a full-match table, stack-trace collection, deduplication across the reactor
summary and the per-module output, the Surefire and Failsafe summaries summed over every block with the per-runner
attribution and the parallel-build rule, and the positive `No tests to run.` marker for the verdict.
Evidence, order `mutation-after`: `MavenOutputParserTest` over 23 fixtures (35-case roster); mutations (a) (b) (c) (d)
(f) below. Commits `3f3146c3`, `ca5d67d3`, `e01eb874`.

**D3 — the Gradle parser: met.** `GradleOutputParser`: compiler error locations, the failing tasks, the severity derived
from the category, the `N tests completed` blocks summed over every block and anchored to the whole line, and Gradle's own
filter statement for the verdict.
Evidence, order `mutation-after`: `GradleOutputParserTest` over 9 fixtures (16-case roster); the deliberate
two-test-task fixture pins the summing. Commits `554928c8`, `ca5d67d3`, `e01eb874`.

**D4 — the OpenRewrite log parser: met.** `OpenRewriteLogParser`: the structured `WARN` finding lines, classification by
the identifier, the domain state as an argument, and the fail-closed verdicts `observed`, `not_observed`,
`domain_inactive`, `parse_error` (a reworded verb, a blank capture, a colon-less identifier and an out-of-range location
are all `parse_error`).
Evidence, order `mutation-after`: `OpenRewriteLogParserTest` over 16 fixtures (57-case roster over two suites); mutation
(e) below. Commits `3d80ef74`, `ca5d67d3`, `e01eb874`.

**D5 — documentation and decisions: met, with one recorded deviation.**
`pm-core/doc/developer/core-build.adoc`, ADR `core-build/001` (the five components) and `002` (the five deliberate
changes), one line in `pm-core/README.adoc`, the condensed § Native Build Output Parsers (the three implemented table rows
leave, the counts rules stay binding, the anchor stays), the package file's delivered bullets reduced, and the
`PM-IMPL-6` (new) / `PM-TEST-2` (extended) trace entries. `PM-WATCH-JOB-10` and `-18` are **deleted**; `PM-WATCH-JOB-11`
is **kept** (condensed with its guard updated) because the staged sibling spec PLAN-V2-28 links it and the npm and
pyproject parsers still owe its guard — the one place this deliverable departs from a literal reading of "delete -10, -11,
-18", recorded here.
Evidence, order `none`: the files themselves plus the links check (R17) `BROKEN LINKS 0`. Commits `66b2cd03`, `3d00d3d1`,
`97d02126`, `f4bd0a46`.

**Requirement-level Done-when clauses** (the spec's `Done when:` paragraph, clause by clause):

| Clause | Verdict | Order | Evidence |
|---|---|---|---|
| Each diagnostic form of the three modules is a JUnit test whose expected values equal the original's, pin recorded in the fixture directory | met | `none` | `ported/build/PROVENANCE.adoc` records `plan-marshall@7b7e08f088b6c1b333147aeb2c636325da31b0b6`; `BuildLogFixtures.PIN` enforces it; rosters close 35 / 16 / 57 / 4 cases. |
| Each deliberate change has its own fixture asserting the corrected behaviour (reworded trailer filed, trailer dropped, unknown `[ERROR]` `unrecognized`, unmeasured on unusable summaries, zero count is no `no_tests_ran`) | met | `mutation-after` | Six mutations at `66b2cd03`, each reverted by hand with `git status` clean: (a) `UNRECOGNIZED` rule removed → 6 failures, job log `92626c1acee448ee9125c0841dffa5aa`; (b) trailer table disabled → 7 failures, `c70cc9dec3004feaae0ca36f625707c4`; (c) `Unmeasured` → measured zero → 4 failures, `0333f4727dd5488ba0bdc0edf0f9ba2a`; (d) no-tests marker dropped → 1 failure, `282ce793d3954bafaf2f7ee4c0961d66`; (e) OpenRewrite drift ignored → 1 failure, `e07e96fba19b4ca5b2235291c6a970a9`; (f) counts reduced to the last block → 7 failures, `5179645869f543b2b3f5c8d5c0abdec9`. |
| A multi-module Maven log is aggregated over every summary block | met | `mutation-after` | `maven-reactor-three-modules` (112 + 1034 + 57 = 1203), `maven-surefire-failsafe` (724 + 7), `maven-failsafe-zero` (318 + 0, `failsafe: 0`); mutation (f) (1201 expected, 57 seen). |
| The four OpenRewrite verdicts are each shown | met | `none` | `rewrite-observed`, `rewrite-corpus`, `rewrite-short-banner`; `rewrite-not-observed`, `rewrite-dryrun-advisory`, `rewrite-empty`; `rewrite-domain-inactive`; `deliberate-rewrite-parse-error`, `deliberate-rewrite-colonless-identifier`, `deliberate-rewrite-colonless-template`. |
| No test runs Python or reads the sibling checkout | met | `none` | No process start, no `python`, no sibling path in the test tree; the fixtures are class-path resources. |
| The developer guide and the ADRs are written | met | `none` | the guide and the two ADRs; `BROKEN LINKS 0`. |
| Sonar: the pull request's open issues are none (or dismissed), gate green | met | `none` | read at the public API (R18): 0 open issues on the head `4b0a2cde` and on every head since the fixes; quality gate passed; **25 issues** on the first analysis were fixed in code, none dismissed. |
| Build: `verify -Ppre-commit`, then `verify`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am` | met | `none` | all four green on the merge head `4b0a2cde` (708 s / 418 s / 714 s / 92 s), and on every earlier head; job ids in the build-server section. |
| Links (R17) | met | `none` | `trace.py` over the documentation checkout: `BROKEN LINKS 0`; one pre-existing gap from `main` (`PM-WATCH-STORE-56` referenced but undefined) is not this plan's. |

Per-head test runs: `verify` (all modules) at `f4bd0a46`, `3a5edb92`, `2f7c8799`, `03a9dcca`, `6a8f04cc`, `4b0a2cde`;
`module-tests` narrower runs (`test -pl pm-core`) at each of the iteration heads; the project publishes no separate
`module-tests`/`test-compile` canonical, so the gate canonical is the run.

## 2. Personas and skills

Production code: `persona-implementer`; tests: `persona-module-tester`; every self-review round and the PR-comment triage:
`persona-code-reviewer`. The spec declares no `Skills:`/`Persona:` lines (`skills: none named`), so R20's coding skills
were read as standards: `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing`
(`cui-http-testing` not applicable — no HTTP). No surface drift: every touched file is in the spec's Expected Surface or
is shared bookkeeping (`doc/Traceability.adoc`, `doc/ImplementationWatch.adoc`, `doc/Specification.adoc`, the ADR
directory of a new package).

## 3. Hypotheses

* *HYPOTHESIS: no test of the repository reads `doc/specification/job-runtime/03-parsers-and-gates.adoc` as input* —
  **confirmed**: a search over `pm-*/src/test` finds only Javadoc references, no reader of that file.
* *HYPOTHESIS: the parsers need no library beyond the JDK and `cui-java-tools`* — **confirmed**: the code uses
  `java.util.regex` and Lombok only; no dependency was added.
* *Verify-first clause* (open pull requests naming the package or the package path): checked before starting; no open PR
  named `build-parsers-and-gate-honesty` or touched `pm-core/.../core/build/`.
* The spec's Claim Labels name `plan-marshall@b1609ec3e`; the sibling checkout's head at read time was
  `7b7e08f088b6c1b333147aeb2c636325da31b0b6`, which is what the fixtures derive from and record.

## 4. Findings

*Self-review round 1* (15 statements, 9 findings): three broken watch links; the Traceability `[#PM-IMPL-7]` anchor left
on the new PM-IMPL-6 section; the spec paragraph declaring the trailer drop a deliberate change (it is the original's);
inconsistent deliberate-change counts across five documents; the loader accepting a fixture with no expectation;
assertion messages over the 20–60 rule; a wrong roster reason; the undocumented loss of the original's `failed` count for
a quiet log; and the ANSI strip missing from the `String` overloads. **All fixed** (`ca5d67d3`, `97d02126`, `f4bd0a46`).
*Round 2*: the package-info listing four of five changes; the `noTestsRan` contract sentence; the stale watch ids;
ADR 001's fact count; the severity-source claim; the `@throws` claims; `@UtilityClass` on the test helpers; the
`rewrite-empty` roster reason. **All fixed** (`f4bd0a46`).
*Round 3*: two documentation clauses ("other three components", "deleted from the watch document"). **Fixed.**
*CodeRabbit review 1 (head `2f7c8799`, 20 findings)*: 15 fixed in `e01eb874` (OSC stripping, count overflow in both
parsers, `TestCounts.from` range checks, negative per-runner counts, location overflow, whole-line anchoring of the
Gradle summaries and marker, OpenRewrite blank-capture / overflow / colon-less identifier, `noTestsRan` documentation,
roster reason and mapping checks); 5 declined with the reason that they are the pinned original's behaviour
(`JvmCategoryPatterns` root `:compileJava` and the bare `FAILED`, the Windows drive letter, `[ERROR]` as a severity
source, `[ERROR]` as a status marker) — answered in PR comment 00:30:17Z.
*CodeRabbit review 2 (head `03a9dcca`, 10 findings)*: all fixed in `8741b2b3` (+ gate formatting `6a8f04cc`) — `Optional`
returns for absent values (`toInt`, `digits`, `runnerCounts`, `extractTestCounts`, `result`, the per-line classifier), the
long-sum wrap in `TestCounts.from` and in both parsers' sums, the reverse-roster suite-path check, the ESC-backslash OSC
terminal case, and the colon-less fixture with the pinned wording.
*Sourcery* (optional): refused both PRs as larger than its 150,000-diff-character limit; optional, not a gate.
*CI / Sonar*: 25 Sonar issues on the first head, all fixed in code (two refactor commits) and 0 since; the quality gate
went red once on duplication (3.1 % > 3 %) and was fixed by sharing `DiagnosticCollector` and the ported-test base.

## 5. Reviewers

| Reviewer | Required | Outcome | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes (quota-limited) | reviewed twice, then refused on quota | `2f7c8799` (00:23:35Z), `03a9dcca` (04:38:08Z) | triggered on #273 at 11:11:18Z (refused 13:05:55Z), escalated `@coderabbitai full review` once (refused 16:58:10Z), #273 closed unmerged and #277 opened (18:29), then six 90-minute waits with a re-trigger each (refusals 18:29:44Z, 21:03:22Z, 01:51:49Z, 05:08:27Z, 06:45:48Z, 09:11:51Z) |
| sourcery | no | refused (diff size) | — | triggered on both PRs; optional |

**Standing review authorization** (section 7) on the merge head `4b0a2cde`: coderabbit is `quota_limited`; a qualifying,
marker-bearing review exists on heads of this PR (`sourceCommitId`/`coveredCommitId` `2f7c8799…` and `03a9dcca…`,
`kind=reviewed`); every finding from both reviews is fixed in a pushed commit or answered with a reason; it was
re-triggered after the last push and the refusal recorded; every other gate holds on the head. No review thread is open —
the provider surface reports `reviewThreads: 0`, so every finding is answered in the PR comments instead of a thread.

## 6. Operator questions

1. Asked (question tool) after the quota policy's steps 1 and 2 were spent, with the review surfaces re-read in the same
   step: "PR #277 is fully green except for the required CodeRabbit review (0 reviews, quota exhausted, standing
   authorization inapplicable)… how should the run proceed?" — options: *wait 90 minutes and re-trigger* (Recommended) /
   *leave #277 open and stop* / *merge now without the review*. **Selected: "Wait 90 minutes, then re-trigger again."**
   That is the policy the run then followed until the reviews arrived.
   No other question-tool call was made. The operator typed no free-text message.

## 7. Rule check

* **Guard**: `guard: none on this harness (unenforced)` — the resolve result reported `guard: none`, so nothing was
  enforced and no claim of "all kept" is made.
* **Shell / file tools**: no shell file operation was issued for reading or searching repository files (Read/Grep/Glob
  only); one redirect was used to write a patch under `.plan/temp/` (see the residue item). One accidental command,
  `python3 .plan/execute-python-missing`, was issued by mistake and failed immediately with "No such file"; it changed
  nothing.
* **Executor**: every build went through `plan-marshall:build-maven:maven`; every CI action through
  `plan-marshall:tools-integration-ci:ci`; every other script through `.plan/execute-script.py`. One exception, recorded:
  the links check ran `trace.py` directly, which R17 prescribes verbatim.
* **Git**: no `stash`, `restore`, `checkout -- <path>`, `rebase` or forced push. The pre-commit profile rewrote ten files
  of the freshly merged other modules (formatting only); they were reverted with `git diff` + `git apply -R` because they
  are outside this plan's Write-Boundary.
* **Spec read**: `spec_source: ledger_worktree`, no `spec_differ` marker at the start and none at landing-time resolve.
* **executor-skew**: `match` (executor and lane both 0.1.2041).
* **`.plan/` state**: only `.plan/temp/` (progress file, landing payload, the gate-noise patch) and one inbox message were
  written; no file under `.plan/orchestrator/` was touched.
* **Builds**: the setup preflight answered `ready`; every build ran routed (`daemon_longpoll`), no fallback, no retry, no
  `killed` job.
* **Restarts / re-dispatches**: none; the session ran continuously. Three self-review sub-agents were dispatched (rounds
  1–3), each read-only.

## 8. Build server

Setup preflight `ready` (the worktree is enrolled); every build routed, `daemon_longpoll`, with no `in_process_fallback`
and no launch-fault retry. Build count by mechanism: `daemon_longpoll: 34` (compile iterations included),
`in_process_fallback: 0`. Gate builds on the merge head `4b0a2cde`: quality gate `536d4a5b089e437b8c2f180bfde7c874`
(708 s), `verify` `650c14958fe3449897aaa76b10d0970b` (418 s), coverage `7bdd6f4d8c13430ea5c3f40a06df4842` (714 s),
integration tests `1f1c0514ef8b45c19b0b62e7c907a73d` (92 s). No `runtime_stamp_reason` was reported (the stamp's only
unread fact is `plugin_version: unknown`, as the resolve result states).

## 9. Residue

1. **Pre-existing on `main`, not fixed here**: the links check reports `REFERENCED BUT UNDEFINED WATCH IDS
   ['PM-WATCH-STORE-56']` — a reference landed by another plan's merge; it is outside this plan's surface.
2. **The five declined CodeRabbit findings** are the pinned original's behaviour, each needing its own deliberate-change
   record and fixture before it can change: the Gradle root-project `:compileJava` category, the bare `FAILED` substring,
   the Windows drive letter in `JvmFileLocation`, `[ERROR]` as a severity source and `[ERROR]` as a status marker. They are
   candidates for a follow-up plan.
3. **Shared limitations recorded for the wiring plan**: the port loses the original's `failed > 0` signal for a quiet log
   (ADR 002, row 2) and the stack-trace collector keeps the original's last-block-attaches-to-the-last-diagnostic
   behaviour (ADR 002).
4. `plugin_version` is `unknown` (the platform-runtime seam did not report it).
5. The pre-commit profile rewrites the formatting of the freshly merged provider modules on every run in this tree; the
   run left them as `main` has them, and the split is recorded above.
