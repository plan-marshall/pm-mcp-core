# Landing Analysis: PLAN-V2-183 — The residue of the sweep's and the cut log's fourth closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
pr: #603 (https://github.com/plan-marshall/pm-mcp-core/pull/603), merge `e935ab2f1799f52383f6ac8683925c6d9600e22d`, 5 files

> Landing record for one shipped plan (runbook lane, opencode, `deepseek-v4.1-flash/high`). Written by the `analyze` verb
> of the thirty-fifth run (2026-11-24) from the inbox message `rb-v2-183-sweep-test-and-statement-residue-closeout-001.md`
> (revision 0), corroborated against git (`git show --stat e935ab2f`), the PR (`ci pr view`, `ci checks status`: 20 checks,
> overall success, SonarCloud success) and the merged code, read by one read-only level-3 leaf and re-read by the orchestrator.

## Deliverable Fidelity vs Spec

All five changed files are inside the declared Expected Surface plus the R06 pair the spec names (the developer guide
`runtime-start.adoc` and ADR `runtime-start/016`); `JobLog.java`, every POM and every dependency are untouched. The only
production behaviour change is `PendingEntryRecordsSweep.RemovalState.faultBeside` (D2(a)), as the spec's Standing
Constraints require. `BuildOutputParser` changes by Javadoc only.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1(a) delegate opened after the assumption, closed in a `finally` | shipped-as-specified | `PendingEntryRecordsSweepTest.java:700` (`assumeFalse`), `:702` (`openTransaction`), `:738-740` (`finally { delegate.close(); }`); the double close of the normal path is a no-op by the `LockTransaction.close()` contract (leaf read `LockTransaction.java:42-43`) |
| D1(b) `RELEASE_FAULT` literal | shipped-as-specified | `PendingEntryRecordsSweepTest.java:104`: `releaseFault = Generators.letterStrings(10, 40).next()`; no `RELEASE_FAULT` reference remains (leaf grep); `AUDIT_FAULT` (`:102`) stays a labelled literal, not named by the spec |
| D1(c) the cause by the journal path | shipped-as-specified | `PendingEntryRecordsSweepTest.java:665-666`: `reason.contains("Store cannot be written: " + workspace.journalFile())` |
| D2(a) the null release message named by type | shipped-as-specified | `PendingEntryRecordsSweep.java:249-254`: `getClass().getSimpleName() + " with no message"` when `getMessage()` is null; call site `:180` passes `e`; test `aReleaseFaultWithoutAMessageIsNamedByItsType` (`:752-790`) asserts the type and the absence of `null`; the failing run by log id (`2a4744a5…`) is the lane's statement, not re-run |
| D2(b) the `@SuppressWarnings("java:S2583")` | shipped-as-specified (kept, reason stated) | `PendingEntryRecordsSweep.java:160-165`: the comment says the method is the smallest scope that covers the flagged conditional and why (`@SuppressWarnings` cannot annotate a catch clause, block or statement); Sonar at the head reports 0 open issues |
| D3 the `BuildOutputParser` statement | shipped-as-specified | `BuildOutputParser.java:109-111` states the reason once, checkable against ADR `core-build/011` lines 77-78 and 90-91 (re-read: "the flag is passed to the Maven count scan alone"; "a parser … takes the flag through `readLog` … when a plan touches it") |

## Three-Layer Analysis

Read on three layers by one read-only level-3 leaf (`execution-context-level-3`, `orchestrator.analyze`), by reading — no
build, no test run, no write. The orchestrator re-read the `checked` points that carry a finding or a deliverable at the
merged head: `PendingEntryRecordsSweep.java:128-134`, `:156-182`, `:205-212`, `:243-254`, `:306-310`;
`PendingEntryRecordsSweepTest.java:100-106`, `:660-667`, `:696-702`, `:736-740`, `:780-790`; `BuildOutputParser.java:106-126`;
ADR `core-build/011` lines 77-78 and 90-91. Not re-read (taken from the leaf's reading, recorded as such): the
`FileLockManager` and `LockTransaction` internals (`FileLockManager.java:139`, `:175-183`, `:216-234`), the statistical estimate
of the `null` substring collision, the hand count of the new assertion messages beyond the three re-read.

- **Dispatch:** one read-only leaf for this landing — reason: reading merged code to test the spec, the deliverable trace and
  the code is the depth the dispatch rule names; the one landing of this drain, so one leaf.
- **Verdict:** layer 1 sound (info only); layer 2 one minor (a Javadoc overclaim); layer 3 two minors (a weak assertion, a
  duplicated mock setup) and the confirmed first-fault `null` path. No blocker, no major, nothing reported to the operator at once.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The D3 premise holds: ADR `core-build/011` passes the flag to the Maven count scan alone; the rewritten Javadoc states exactly that | checked (re-read) | confirms | `BuildOutputParser.java:109-111`, ADR lines 77-78, 90-91 |
| L1-2 | The D1(a) premise is true but narrower than it reads: on the skip path the delegate had acquired nothing, so nothing leaked; the real leak was a failure after the acquire, which the `finally` now covers | checked (leaf; code re-read at `:700-702`, `:738-740`) | confirms | `PendingEntryRecordsSweepTest.java:702` |
| L1-3 | The Done-when's test outcomes (the new test failing before, green on a non-root runner, a clean root skip, verify) | not_shown_by_reading | none | spec § Done when |

**Layer 1 verdict:** sound; 2 checked (both confirm), 1 not shown by reading. Persisted corpus verdicts: none — the spec
belongs to a shipped plan and is not re-read at the gate.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1(a) | `PendingEntryRecordsSweepTest.java:700-740` | the same test; the root skip happens before any transaction is opened | none | checked |
| D1(b) | `:104`, used at `:715`, `:730` | — | none | checked |
| D1(c) | `:665-666` | the refusal's journal path is asserted; a different refusal with the same words no longer satisfies it | none | checked |
| D2(a) | `PendingEntryRecordsSweep.java:249-254`, `:180` | `aReleaseFaultWithoutAMessageIsNamedByItsType`: the old string append gives `…release failed: null`, which fails `:785` and `:787` by trace | the Javadoc of `faultBeside` (`:243-247`) says "so that the report never prints `null`" — broader than the code (see L2-1) | checked |
| D2(b) | `PendingEntryRecordsSweep.java:160-165` | — | none | checked |
| D3 | `BuildOutputParser.java:109-111` | statement only | the Javadoc still states the Maven-only `Unmeasured` outcome three times (`:109-111`, `:115-117`, `:124`); the spec asked only to drop the tail clause (L2-3) | checked |
| gates | — | green `verify`, `-Ppre-commit`, the failing run and the root skip are claimed by the lane with log ids; CI shows 20 checks green and SonarCloud success | log ids not re-run | not_shown_by_reading (CI re-read: success) |

| Finding | Claim | Severity | Evidence |
|---------|-------|----------|----------|
| L2-1 | The `faultBeside` Javadoc "so that the report never prints `null`" is broader than the code: the first fault's message still reaches the report as `null` (L3-3) | minor | `PendingEntryRecordsSweep.java:243-247`; `:179`, `:211`, `:132` |
| L2-2 | The lane's claim that the first-fault path still feeds a nullable message into `Fault` is borne out; the diff does not record the residue (only the landing message does) | info | `PendingEntryRecordsSweep.java:179`, `:211`, `:309` |
| L2-3 | D3 de-duplicates the premise but the Javadoc still states the Maven-only outcome three times | info | `BuildOutputParser.java:109-126` |

**Layer 2 verdict:** one minor (L2-1), two info; every deliverable traces to code and to a test that can fail; no file the
spec does not explain.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `PendingEntryRecordsSweepTest.java:787` | `assertFalse(reason.contains("null"))`; the reason embeds the generated project id (3 to 12 lower-cased letters) through the report | a rare false failure when the generated id contains the letters `null` (about 1e-5 a run); a tighter check (equals on the release part) has no such chance | minor |
| L3-2 | `PendingEntryRecordsSweepTest.java:766-779` | the mock setup of the guarded `LockTransaction` and `LockManager` repeats `:704-720` of the sibling test (about 14 lines) | duplication; a Sonar duplication-gate effect is not shown by reading (the gate was green) | minor |
| L3-3 | `PendingEntryRecordsSweep.java:132`, `:179`, `:211`, `:309` | a message-less `IllegalStateException` (or any listed type) from `acquire`, the commit or the lock open reaches `state.fault(e.getMessage(), …)` or the `run()` catch | the report prints `pending entry directories not swept: null`; in `faultBeside` a null `first.message()` also prints `null; the lock's release failed: …` | minor (carried by the lane, unfixed by the spec's own constraint) |

**Layer 3 not shown by reading:** whether Sonar's S2583 flags the line-178 condition (`attempted == null`), the line-179
condition, or both, so the comment's "smallest scope" claim holds only for the condition it names; the test outcomes; the
Sonar duplication gate.

**Layer 3 verdict:** findings — 3 (all minor), no blocker, no major, nothing reported to the operator at once. Boundaries
checked and clean: a blank message prints an empty tail (outside the spec: "no message" means null); an anonymous subclass
gives `getSimpleName() == ""`, so the text is ` with no message` (a cosmetic corner, not a defect of the spec); the
`finally` close is idempotent; a failed assertion leaves no transaction open and the outer `finally` restores `0700`.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | no generic `Exception`/`RuntimeException`/`Throwable` caught or thrown in production | conforms | `faultBeside(Fault, RuntimeException)` only takes the multi-catch variable; the catch lists five named types (`PendingEntryRecordsSweep.java:174-175`) |
| CLAUDE.md § Code Standards | assertion messages of 20 to 60 characters | conforms | the new messages count 38 to 50 (leaf hand count; three re-read) |
| CLAUDE.md § Code Standards | test data from `cui-test-generator`, a literal only where it is the contract | conforms | `releaseFault` generated; `Store cannot be written: ` is the refusal's message contract; `AUDIT_FAULT` is a labelled pre-existing fixture |
| CLAUDE.md § Code Standards | a refusal test asserts the reason, not only the type (R20) | conforms | `:665-666` asserts the journal path |
| CLAUDE.md § Code Standards | EasyMock only, no new hand-written fake; no AAA comments | conforms | `createMock(LockTransaction.class)`, `createMock(LockManager.class)` |
| CLAUDE.md § Code Standards | Javadoc, ADR and guide say what the code does | deviates | `faultBeside` Javadoc (`:243-247`, L2-1); the ADR `016`, the guide and the test statements are scoped to the release fault and conform |
| CLAUDE.md § Dependencies | no dependency or plugin added; no more than 100 files; `JobLog.java` untouched | conforms | 5 files, no POM, no `JobLog.java` |
| CLAUDE.md § Build | `verify`, pre-commit, coverage, Sonar | not_shown_by_reading | CI: 20 checks success, SonarCloud success; the local runs are the lane's statement |

## Metrics and Anomalies

- Tokens: `unknown` (the runtime seam reports none for the runbook lane); `plugin_version` `unknown`. The drain-completeness check
  reports `complete: false` with `missing_keys` = `total_tokens`, `plugin_version`: the known recurrence of Open Defect 7
  (owner E27), no new defect.
- Duration: not stated by the landing.
- Anomalies: 13 builds, all `daemon_longpoll`, none degraded; the failing run of the test of D2(a) ran on the uncommitted tree
  of `d5ce5164` (the lane discloses that the ledger row records no worktree sha); the quality-gate canonical is plain `verify`,
  so the lane issued `-Ppre-commit` as an additional call; `executor-skew` `match` at the start, a newer executor at landing.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed head `a1c969f1` with no actionable comment; Sourcery (optional) refused on its own
  review budget. Counts claimed by the landing: 0 unresolved threads, 0 review-body findings, 0 Sonar issues; CI re-read at
  analysis time shows SonarCloud success and CodeRabbit success; the review-gate numbers were not re-read separately
  (`stated, not verified`, no difference expected: 0 of 0).
- CI/merge: merged through the merge queue as `e935ab2f` (parent `a00db8de`); 20 checks, overall success.
- Collisions: none observed with the running plans 162, 135 and 182 (PR #604 of 182 is open; no shared file).

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-183 --status shipped`
- [x] row `pr` stamped (`#603`)
- [x] row `landing` stamped — `landings/PLAN-V2-183.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-v2-183-sweep-test-and-statement-residue-closeout` (the sender)
- [x] epic.md narrative reconciled: the recurrence of the `unknown` figures needs no new line (the Open Defect "Runbook-lane landings carry `total_tokens` and `plugin_version` as `unknown`" says so); the landing message `-001` is archived by its exact name
- [x] residue staged — see Follow-Ups
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

No blocker and no major, so nothing goes to the operator at once. The minor and info points (L2-1, L2-3, L3-1, L3-2, L3-3 and
the lane's carried first-fault path) fit no staged plan: PLAN-V2-125 and 126 own the test conformance of other classes and are
at or over their size, and neither names `PendingEntryRecordsSweepTest`, `PendingEntryRecordsSweep` or `BuildOutputParser`.
They are carried as item 104 into the NEW staged PLAN-V2-184 `sweep-first-fault-message-and-statement-closeout` (WS-03,
runbook). Recorded, not fixed: the lane's counts of runs and logs (`stated_not_verified`), the report's added
`; the lock's release failed: …` clause on the release-fault path only, the `unknown` figures (owner E27), the open Sonar
question of line 178 (not shown by reading; the gate is green).
