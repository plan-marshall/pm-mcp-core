envelope_version=1
sender_type=plan
sender_id=rb-v2-136-easymock-for-fakes-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-13T20:31:13Z
revision=1
amended=2026-11-13T20:36:11Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-136-easymock-for-fakes-residue
epic=pm-mcp-core-m2-m4-v2
pr=#411
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.2118
executor_version=0.1.2118
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=5
review_body_findings_handled=5
sonar_open_new_issues=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

# PLAN-V2-136 — the residue hand-written doubles replaced by EasyMock

Merged as PR #411, merge commit `6aa55a56`. The spec was read from the ledger worktree (`spec_source:
ledger_worktree`; no `spec_differ` marker). Standing rules r7 (R02, R06, R07, R08, R11, R17, R18, R19, R20) read before
the first edit. The `executor-skew` check answered `skew` (executor 0.1.2114 vs lane 0.1.2118); the operator selected
"regenerated, continue" and the re-check answered `match` at 0.1.2118.

## 1. Deliverables

Every conversion's test order is `none` (a refactor; the unchanged tests are the guard; no fail-first run is claimed).
The guard run for each module is `test -pl <module> -am`; the branch base was `origin/main` at `39a75f56` (the merge
base moved to `64cec092` later; the plan merged it and the final gate ran at 10363 tests).

- **D1 — pm-core audit sink and anchors: met.** Commit `f0491d07`. Scan `RecordingAuditSink|RecordingAnchor|
  FailingPredecessorAnchor -- pm-core/src` empty. `test -pl pm-core -am`: 4821 at base and 4821 green after. Every
  converted assertion carries a message.
- **D2 — pm-workflow engine and scenario ports: met.** Commits `ea6c36da` and `93d871df` (the second is the
  `DefaultPlanRunTest` call-site follow-up, see §9). Scan of the six names empty. `test -pl pm-workflow -am`: 7320 at
  base and 7320 green.
- **D3 — pm-workflow model and task fixtures: met.** Commit `bead7a1d`. Scan `class (Qualifications|Guard|Paused)`
  empty. Same module run (order 2 and 3 share it), 7320 green.
- **D4 — pm-runtime audit sinks: met.** Commit `04385147`. Scan `RecordingAuditSink -- pm-runtime/src` empty.
  `test -pl pm-runtime -am` 7137 at base and green; `verify -Pintegration-tests -pl pm-runtime -am` 43 green. H5
  confirmed: the child JVM of `LockHolderProcess` resolved EasyMock from `java.class.path`.
- **D5 — pm-runtime worker doubles: met.** Commit `deec1a30`. Scan of the eight names empty. `test -pl pm-runtime -am`
  7137 green. H4 confirmed: the `order` list's entries come from `andAnswer`.
- **D6 — pm-runtime steps, process calls, stub, keyrings: met.** Commit `fc87c87d`. Scan of the four names empty and
  `start/RecordingProcessCall.java` deleted. `test -pl pm-runtime -am` 7137 green. **H1 confirmed**: `createMock(
  Process.class)` compiled and ran on Java 25, so `StubProcess` has no K10.
- **D7 — the closed list, the scan and the record: met, with the count corrected by the spec's verify-first clause.**
  Commit `9f741617` (ADR 026 and the index), corrected by `e8ed5fbf`, `e4f2df64`, `f2de6d3e`, `ab33a42a`. The
  closed-list scan at the branch base returns **13** declarations, not the 14 the spec names:
  `ProcessTreeTrackerTest.RecordingSignals` was replaced by an EasyMock `ProcessGroupSignals` mock by #390
  (`1283710f`) before the base. The 13 are exactly K1–K9 (`FakeBus`, `FakeSecretService`, `FakePmExec`,
  `RecordingLockManager`, `FakeOsStore`, `FakeRepository`, `FakeLfs`, two `RecordingSignals`, two `FakeTime`,
  `RecordingLocks`, `FakeParticipant`) and no other line; the fixture scan returns nothing; ADR 026 holds the next free
  number at `origin/main`. The spec's *Verify-first* clause ("re-run the scan and the counts above") is what produced
  the 13, and ADR 026 states the drift and its cause. On that reading the deliverable is met; a reader who insists on
  the literal 14 would mark the count clause not met as written, and that is the one honest caveat of this landing.
- **Plan-level Done-when:** met. No `pom.xml` in the diff; no `@Test`/`@ParameterizedTest`/`@RepeatedTest`/`@Nested`
  line in the Java diff (`git diff -G` empty); the guides no longer name a removed double (the `*.adoc` search returns
  only ADRs, which the spec classes as history and does not edit); the per-module method counts equal the base; the PR
  body carries the per-class table.

## 2. Personas and skills

- Personas: `persona-implementer` (the fixture refactors' design), `persona-module-tester` (every conversion),
  `persona-code-reviewer` (the three self-review rounds and the PR-comment triage). The spec declares no `Persona:` line
  for any deliverable, so section 1's table selected them.
- Domain skills read (the spec's R20 list): `pm-dev-java-cui:cui-testing`, `pm-dev-java:junit-core`,
  `pm-dev-java:java-core`, `pm-dev-java:java-lombok`, `pm-dev-java:javadoc`. The deliverables name no `Skills:` line:
  `skills: none named`.

## 3. Hypotheses

- **H1 (class mock of `java.lang.Process` on Java 25): confirmed.** The first `createMock(Process.class)` compiled and
  its tests ran; `SubprocessJobRunnerTest.StubProcess` is converted, not K10.
- **H2 (a flag a test sets before the call is expressible without a hand-written holder): confirmed.** `refusing`,
  `grant`, `waitingRole`, the pause set and the slot/token snapshots are read in `andAnswer` (the pause port answers
  per plan id from a `Set`).
- **H3 (no capture read while another thread appends): confirmed.** `DerivedFactStoreLockTest` reads after both
  threads join; `FileLockManagerTest` reads the capture in single-threaded tests; the only cross-thread sink, the child
  JVM's, is a nice mock that records nothing it reads. The fixture's new `eventSink` capture is read after synchronous
  calls on the test thread.
- **H4 (the `SupervisorTestFixture` `order` list keeps its assertion): confirmed.** `WorkerEndingTest:117` still
  asserts `List.of("fence", "release", "write")`; the entries come from `andAnswer`.
- **H5 (the child JVM resolves EasyMock from `java.class.path`): confirmed** by the green
  `FileLockManagerTwoProcessIT`.

## 4. Findings

- Self-review round 1: 14 findings. Fixed in `e8ed5fbf`: the ADR count claims, `TaskFixtures.Paused` per-plan
  semantics (a `Set`-backed mock, not one flag), `PeriodicSweepTest`'s per-step report, 20–60-character messages on every
  converted assertion, the `events::add` sink in `SupervisorTestFixture` (now an EasyMock capture sink), the
  `ModelFixtures`/`AuditSinkDoubles`/guide Javadoc corrections.
- Self-review round 2: fixed the ADR count wording (`e4f2df64`), then the scan-vs-list distinction (`f2de6d3e`).
  Two module ADRs (`runtime-start/003`, `workflow-engine/006`) still name a removed double in a present-tense sentence;
  declined by the spec's rule that ADRs record history and are not edited.
- CodeRabbit (review at `f2de6d3e`, qualifying marker `coveredCommitId=f2de6d3e`): 5 findings, all fixed in
  `ab33a42a` and every thread replied and resolved — the scan's name-matching limitation, the retained F1–F3 excluded
  from the conversion claim, the published-surface claim restated with the consumer check as an open item, the fixture
  guide's separate typed lists, and the 18-character assertion message.
- SonarCloud (public API, R18; `resolve` reports the provider unconfigured): 3 new-code issues at `f2de6d3e`
  (`ProcessCallDoubles` S6213 method name, two S1117 hidden fields in `SupervisorTestFixture`), fixed in `f6d80822`;
  re-read at `ab33a42a` and `254434bf`: 0 open. The quality gate is OK at both heads.
- Self-review round 3 found one wording item (the ADR's "thirteen" conflated scan-matched keeps with the whole list);
  fixed in `f2de6d3e`'s successor and re-read. The 3-round limit is spent; no round-4 dispatch.

## 5. Reviewers

- **coderabbit (required, quota-limited, review_complete_after_handling): reviewed.** A qualifying review with the
  `final_review_risk_coverage` marker (`coveredCommitId=f2de6d3e`, `kind=reviewed`) is on record; its 5 findings are
  handled; no re-trigger was posted for the later heads (`f6d80822`, `ab33a42a`, `254434bf`) under the done exemption.
  Trigger history: `@coderabbitai review` at 19:18Z (review published 19:28Z), a second trigger after the Sonar fix was
  answered "Already reviewed the last commit", and the review-limit notice (41 minutes) named the next included review;
  no quota wait was needed because the qualifying review exists.
- **sourcery (optional): refused** — "your pull request is larger than the review limit of 150,000 diff characters".
  Recorded; optional, so not gating, and no option to move it to required exists.

## 6. Operator questions

1. "The lane's executor-skew check answered `skew` (executor 0.1.2114 vs lane 0.1.2118) … How do you want to proceed?"
   Options: "Regenerate then resume (Recommended)", "Stop here, report blocked". Selected: **"regenenerated,
   continue"**. The executor was regenerated to 0.1.2118 and the re-check answered `match`.
No free-text operator messages beyond that selection. A question tool was available.

## 7. Rule check

- guard: none on this harness (unenforced). No guard log line exists for this run, and "all kept" is not claimed.
- Shell: one command per call, no pipes, no command substitution, no loops. Repository files were read and searched
  with the file tools. Two deliberate deviations, both named here: (a) `git grep` was used for the spec's own
  Done-when scans (the spec writes those commands verbatim; the lane otherwise forbids git's grep subcommand); (b) one
  `git rm --cached` was issued for a deletion and immediately corrected with `git add` + `git rm` so the file left the
  index and the disk together. No `git stash`, `restore`, `checkout --`, `rebase` or forced push; the base was brought
  in with a merge commit carrying the trailer.
- Builds: every build was resolved `daemon_longpoll` (each `[BUILD-SERVER] resolved build` line named it);
  `in_process_fallback: 0`, `degraded: 0`, no launch-fault retry, no killed job. Two builds returned a live job that was
  re-attached with `build_server wait` (`b806…`, `122f…`), never backgrounded.
- Reads outside the repository: the build-server job logs the lane directs the run to open, the sibling documentation
  checkout for the R17 links check, and the public SonarCloud API (R18). No credential, config or environment dump was
  read.
- `spec_source=ledger_worktree` (no `spec_differ`). `executor-skew`: `skew` → operator regenerated → `match`.
- CI actions went through `ci`; no `gh`/`glab` was called directly.

## 8. Build server

Setup preflight: `ready`. Every build ran on the daemon (`daemon_longpoll`) — 30 build calls in the run, including the
compile-failure reruns and the two re-attached jobs; `in_process_fallback: 0`. No fallback, no degradation and no
launch-fault retry has an entry. The `runtime_stamp_reason` was absent; `plugin_version` is `unknown` because the
resolve could not read it.

## 9. Residue

- **D7 count:** the spec names 14 declarations; the branch base holds 13 (`ProcessTreeTrackerTest.RecordingSignals` was
  converted by #390 before the base). The spec is read-only and not edited; ADR 026 records the drift.
- **Commit history:** D2 is two commits (`ea6c36da`, `93d871df`) because the `DefaultPlanRunTest` call site was left
  out of the first staging; the spec says "each deliverable is one commit". Pushed history cannot be rewritten without a
  forced push, which the lane forbids. A further `fix(test)` commit (`e8ed5fbf`), the ADR wordings (`e4f2df64`,
  `f2de6d3e`, `ab33a42a`), the Sonar fix (`f6d80822`), the pre-commit rewrites (`aa614c79`) and the base merge
  (`254434bf`) sit on top.
- **Surface delta (reported, not declared):** `ProcessCallDoubles` (a static factory that builds EasyMock expectations;
  ADR 026 names the shape as allowed), `WorkflowEngineRecordsTest` and `DefaultPlanRunTest` (both named the removed
  doubles), and `doc/adr/README.adoc` (the ADR index, R19 bookkeeping).
- **Two module ADRs** (`pm-runtime/doc/adr/runtime-start/003`, `pm-workflow/doc/adr/workflow-engine/006`) name a
  removed double in a present-tense sentence. The spec's Done-when treats ADRs as history and does not edit them.
- **Test-local sink lambdas** (`events::add`, `audits::add`, `event -> {}`) remain in tests that feed the lock manager,
  the start-failure guard, the credential move and the job runner. None is in the decision table and none is on a
  converted double's own call path; ADR 026's definition covers the shape, so a later scan by role would find them.
- **Consumer check:** the assembly checkout `plan-marshall-mcp` was not searched. ADR 026 states that the `pm-runtime:tests`
  jar packages the converted package-private classes, so a same-package consumer could reference one; "owed: none" is a
  claim about the declarations.
- **Landing facts provenance:** `plugin_version=0.1.2118` is the installed bundle's `dist-manifest.json` version (the
  same install stamp the executor-skew check reads); the `resolve` runtime-info seam returned `unknown` for that field.
  `total_tokens` stays `unknown`: this harness exposes no token measurement to the lane, and the landing rule forbids a
  guess and forbids `n/a` where the value is an unread total. `inbox landing-check` therefore reports
  `complete: false` naming `total_tokens` alone; that is a truthful failed read, not an omission.
- No base worktree was created (no Done-when required a base-sha failing run), so `cleanup_owed=false`; the plan
  worktree and the branch refs are removed and `main` is updated to the merge commit.
