# Landing Analysis: PLAN-V2-181 — The residue of the cut build log's and the sweep's third closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
pr: #599 (https://github.com/plan-marshall/pm-mcp-core/pull/599), merge `31d97799a224be6c013e83ef743fbcc9b7cf9bad`, 8 files

> Landing record for one shipped plan (runbook lane, opencode, `deepseek-v4.1-flash/high`). Written by the `analyze` verb
> of the thirty-fourth run (2026-11-24) from the inbox message `rb-v2-181-cut-log-and-sweep-third-residue-closeout-001.md`
> (revision 1), corroborated against git (`git show --stat 31d97799`), the PR (`ci pr view`, `runbook_lane review-gate`)
> and the merged code, read by one read-only level-3 leaf and re-read by the orchestrator.

## Deliverable Fidelity vs Spec

All eight changed files are inside the declared Expected Surface; `JobLog.java`, every POM and every dependency are
untouched; the only production behaviour change is `PendingEntryRecordsSweep.remove` (D2(a)), as the spec's Standing
Constraints require. `BuildOutputParser` changes by Javadoc only.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1(a) the saturation test that separates the mutant | shipped-as-specified | `BuildLogTextTest.cutMavenLogSeparatesTheLineSumMutantByItsFiledCount` (`:631-661`), the lead's fixture; a trace of the mutant (`+` back at `MavenOutputParser.java:433`) gives `1 failing test`, the saturated parser `1 further failing test`; the failing run by log id (`164b5ef2…`) is the lane's statement, not re-run here |
| D1(b) the comment and the statement | shipped-as-specified | `BuildLogTextTest.java:595-604`: `filedFailures` stores an execution's first value raw (`MavenOutputParser.java:551`, `Map.merge`), the hiding overflow is the long subtraction at `:506` — the spec's wording "`int` wrap" was wrong and the lane stated the code correctly |
| D1(c) the strict summary win fixture | shipped-as-specified (comment route) | `BuildLogTextTest.java:695-697`: the comment says the fixture guards the per-execution subtraction, not the side taken |
| D2(a) the sweep's fault boundary | shipped-as-specified | `PendingEntryRecordsSweep.java:162-176` and `faultBeside` `:242-245` keep the first fault's reach, entries and message and append the release fault; test `aFaultedLockReleaseKeepsTheFirstFaultsReach` (`:693-740`); the pre-fix failing run (`b8b10858…`, `0a8c47f7…`) is the lane's statement |
| D2(b) the permission restore and the cause | shipped-modified | restore is in a `finally` in both undecided-arm tests (`:678-681`, `:736-739`); the refusal's cause is asserted only as the substring `cannot be written` (weak); the Javadoc states the root skip and that a non-root runner then covers the text, so on a root runner nothing does |
| D3(a) the `BuildOutputParser` statement | shipped-modified | cites ADR `core-build/011:90-91`; the tail clause "the one parser … that answers it with an absence here" still partly restates the premise |
| D3(b) the NFC fold statement | shipped-as-specified | `SourceManifestCodecTest.java:333` and ADR `core-store/084` name NFC, upper, lower, NFC; both forms reach U+0390 |

## Three-Layer Analysis

Read on three layers by one read-only level-3 leaf (`execution-context-level-3`, `orchestrator.analyze`), by reading — no
build, no test run, no write. The orchestrator re-read every `checked` point recorded below at its `file:line` at the
merged head: `PendingEntryRecordsSweep.java:159-176` and `:242-245`, `MavenOutputParser.java:433`, `:496-565`,
`PendingEntryRecordsSweepTest.java:641-740` and `BuildLogTextTest.java:590-715`; the NFC trace (U+0390) and the ADR
sentences were taken from the leaf's reading and are recorded as not re-read.

- **Dispatch:** one read-only leaf for this landing — reason: reading merged code to test the spec, the deliverable trace
  and the code is the depth the dispatch rule names; the one landing of this drain, so one leaf.
- **Verdict:** layer 1 findings (two spec corrections, no blocker); layer 2 findings (two minor points of D2(b) and D3(a)); layer 3 findings (minor only);
  the layers that carried a finding are named here.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The spec's premise that the wrap hiding the unfiled figure is an `int` wrap is false: it is a long subtraction (`MAX - MIN = -1`, floored to 0) | checked (re-read) | refutes | `MavenOutputParser.java:506` |
| L1-2 | The spec LEAD that a fixture (error-level `Failures: MAX, Errors: 1` beside a warning-level second execution) separates the line-sum mutant holds | checked (re-read) | confirms | `MavenOutputParser.java:433`, `:546-555`, `BuildLogTextTest.java:639-644` |
| L1-3 | The spec LEAD that another writer's journal can appear between the attempt and the release is possible only after a lost or released lock; the fix is sound regardless, because a reach re-read after the release is not trustworthy evidence | checked (re-read) | confirms | `PendingEntryRecordsSweep.java:159-176`, `:242-245` |
| L1-4 | D2(b) presupposes that something covers the undecided report text on a root runner; the merged Javadoc says a non-root runner does, so on root nothing does | checked (re-read) | refutes | `PendingEntryRecordsSweepTest.java:641-646` |
| L1-5 | The Done-when's failing-run log ids exist only in the PR narrative; whether those runs failed is unverifiable by reading | not_shown_by_reading | none | spec § Done when |

**Layer 1 verdict:** findings — 4 checked, 1 not shown by reading. The spec's wording of L1-1 is on a shipped spec; no
staged spec repeats it, so there is nothing to correct. Persisted corpus verdicts: none — L1-2 and L1-3 name two of the
three leads of one composite `LEAD` bullet of the spec's `## Claim Labels`, and the third (root skip) is settled the other
way (L1-4), so no single corpus verdict is true of the bullet; the spec belongs to a shipped plan and is not re-read at the gate.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1(a) | `MavenOutputParser.java:433`, `:546-555` | `BuildLogTextTest.cutMavenLogSeparatesTheLineSumMutantByItsFiledCount`: separates the mutant by trace | none | checked |
| D1(b), D1(c) | `BuildLogTextTest.java:595-604`, `:695-697` | comment-only | none | checked |
| D2(a) | `PendingEntryRecordsSweep.java:162-176`, `:242-245` | `aFaultedLockReleaseKeepsTheFirstFaultsReach`: an EasyMock `LockTransaction` whose `close()` writes a journal and throws; on the pre-fix code all four assertions fail by trace | the spec's "message" is kept as a prefix and the release fault appended — the spec does not say whether the message may grow | checked |
| D2(b) | `PendingEntryRecordsSweepTest.java:650-682` | `finally` restore present; cause asserted as a substring | none | checked |
| D3(a) | `BuildOutputParser` Javadoc, comment-only | — | the tail clause partly restates the premise | checked |
| D3(b) | `SourceManifestCodecTest.java:333`, ADR `core-store/084` | — | none | checked |
| gates | — | green `verify` per module, `-Ppre-commit`, coverage and the Sonar disposition are claimed in the PR text only | not shown by reading | not_shown_by_reading |

**Layer 2 verdict:** findings — 2 (D2(b) cause assertion weak, D3(a) tail clause), gates not shown by reading.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `PendingEntryRecordsSweepTest.java:697` | the real delegate transaction is opened before `assumeFalse`; on the root skip or an early failure the delegate is never closed | a lock transaction left open in a test; the lock is opened, not acquired, so the effect is test-local | minor |
| L3-2 | `PendingEntryRecordsSweepTest.java:104` | `RELEASE_FAULT` is a free-text literal with no contract remark | CLAUDE.md asks for generated data unless the literal is the contract (it mirrors the neighbouring `AUDIT_FAULT`) | minor |
| L3-3 | `PendingEntryRecordsSweep.java:161` | `@SuppressWarnings("java:S2583")` on the whole method | hides any other S2583 hit of `remove`; the lane's account is that Sonar flagged two bugs on these lines and the suppression carries a reason | minor |
| L3-4 | `PendingEntryRecordsSweep.java:243` | a release exception with a null message | the report prints `null`; the cause and stack are dropped as the old code dropped them — no regression | info |

**Layer 3 not shown by reading:** L3-5 — the EasyMock seam's strict one-call counts for `openTransaction` and `close`
cannot be confirmed without a run; whether Sonar flagged S2583 at all (the lane says it did) is not shown here.

**Layer 3 verdict:** findings — 4 (three minor, one info), no blocker, no major, nothing reported to the operator at once.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | no generic `Exception`/`RuntimeException`/`Throwable` in production | conforms | `PendingEntryRecordsSweep.java:169-170` lists five named types |
| CLAUDE.md § Code Standards | assertion messages of 20 to 60 characters | conforms | the new messages are 25 to 51 characters |
| CLAUDE.md § Code Standards | EasyMock only, no new hand-written fake | conforms | `createMock(LockTransaction.class)`, `createMock(LockManager.class)` |
| CLAUDE.md § Code Standards | no AAA phase comments | conforms | none in the new tests |
| CLAUDE.md § Code Standards | test data from `cui-test-generator`, a literal only where it is the contract | deviates | `RELEASE_FAULT` (L3-2); the log lines carry the contract remark |
| CLAUDE.md § Dependencies | no dependency or plugin added; no more than 100 files | conforms | 8 files, no POM |
| CLAUDE.md § Build | `verify` per module, pre-commit, coverage, Sonar | not_shown_by_reading | a run or the Sonar UI would show them |

## Metrics and Anomalies

- Tokens: `unknown` (the runtime seam reports none for the runbook lane).
- Duration: not stated by the landing; the CodeRabbit refusal (13:00Z) and the 90-minute quota wait (re-trigger 14:35Z) decided the wall time.
- Anomalies: 23 builds, all `daemon_longpoll`, none degraded; the pre-commit profile rewrote `FreshnessReconcile` and its test on earlier heads (not this plan's, reverted by file tools; `main` fixed them in #598); the final head's gate ran clean.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) refused at `13:00:54Z`, was re-triggered at `14:35:22Z` on `e4efc406` and reviewed with no actionable comments (`review_complete_after_handling`; the merge head `c47e5782` is covered under that rule); Sourcery (optional) refused on its own quota. The re-read at `2026-11-24T16:21Z` (`runbook_lane review-gate --pr-number 599`): `unresolved_review_threads: 0`, `review_body_findings_total: 0`, `review_body_findings_handled: 0`, `merge_allowed: true` — the same as the landing claims; no post-merge record explains a difference because there is none. `review_body_findings_handled` is 0 of 0, so the progress file needed no re-read.
- CI/merge: merged through the merge queue as `31d97799` (squash; first parent `a2323502`).
- Sonar: the landing claims `sonar_open_new_issues=0` and quality gate `ok` after two `java:S2583` bugs were fixed by a suppression; this analysis did not re-read the provider (stated, not verified).
- Collisions: none observed with the running plans 162, 135 and 182.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-181 --status shipped`
- [x] row `pr` stamped (`#599`, already on the row)
- [x] row `landing` stamped — `landings/PLAN-V2-181.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-v2-181-cut-log-and-sweep-third-residue-closeout` (the sender)
- [x] epic.md narrative reconciled: the recurrence of the `unknown` figures needs no new line (Open Defect "Runbook-lane landings carry `total_tokens` and `plugin_version` as `unknown`" says so); the landing message `-001` is archived by its exact name
- [x] residue staged — see Follow-Ups
- [x] resume anchor updated; `queue-view.md` regenerated and committed with the row change

## Follow-Ups

No blocker and no major, so nothing goes to the operator at once. The minor and info points (L3-1, L3-2, L3-3, L3-4, the weak cause assertion of
D2(b), the D3(a) tail clause) fit no staged plan: PLAN-V2-125 and 126 own the test conformance of other classes and are at or over their size,
and neither names `PendingEntryRecordsSweepTest`, `PendingEntryRecordsSweep` or `BuildOutputParser`. They are carried as item 103 into the NEW staged
PLAN-V2-183 `sweep-test-and-statement-residue-closeout` (WS-03, runbook). Recorded, not fixed: the spec's `int` wrap wording (L1-1; shipped spec, no
staged spec repeats it), the lane's run and log counts (`stated_not_verified`), the report text of the sweep (`; the lock's release failed: …` is added on that path only — an assembly match on the old wording on this path would not match, the other arms are unchanged).
