# Landing Analysis: PLAN-V2-184 — The residue of the sweep's fourth closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
pr: #606 (https://github.com/plan-marshall/pm-mcp-core/pull/606), merge `5c060a534c57cbadf6d3c27bd49c8c0e022261a8`, 5 files

> Landing record for one shipped plan (runbook lane, opencode, `deepseek-v4.1-flash/high`). Written by the `analyze` verb
> of the thirty-sixth run (2026-11-25) from the inbox message `rb-v2-184-sweep-first-fault-message-and-statement-closeout-001.md`
> (revision 0), corroborated against git (`git show --stat 5c060a53`), the PR (`ci pr view`, `ci checks status`: 20 checks,
> overall success, SonarCloud success) and the merged code, read by one read-only level-3 leaf and re-read by the orchestrator.

## Deliverable Fidelity vs Spec

The five changed files are exactly the spec's Expected Surface; `JobLog.java`, every POM and every dependency are untouched.
The only production behaviour change is the message composed for an exception that carries none, in the one helper
`PendingEntryRecordsSweep.described` (`:337-349`).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the first fault's message, named by type in ONE place, test first | shipped-as-specified | `PendingEntryRecordsSweep.java:346-349` (`described`), used at `:132` (run catch), `:239` (`fault`, called from `:179` and `:211`) and `:252` (`faultBeside`); no first-fault `getMessage()` is left; the two arms that cannot carry a null message are dropped with the reason (`InternalFaultException.java:80` `requireNonNull`, `StoreRefusal.java:107`) |
| D1 failing run by log id | partly borne | the log `178c128b…` exists and records `aFirstFaultWithoutAMessageIsNamedByItsType` failing (23 run, 1 failure, `:853`); it does NOT hold the expected/actual text the landing message quotes (0 hits for "with no message"; only `Multiple Failures (1 failure)`) — re-read by the orchestrator |
| D2 `faultBeside` Javadoc, the guide and ADR 016 say what the code does | shipped-as-specified | `PendingEntryRecordsSweep.java:242-250`; `runtime-start.adoc:127-128,141,148-151`; ADR 016 `:55-58,65-70` |
| D3(a) the assertion cannot fail on a generated name | shipped-as-specified | `PendingEntryRecordsSweepTest.java:765-767` asserts a fixed literal; the `contains("null")` is gone |
| D3(b) one shared mock factory, no new fake | shipped-as-specified | `guardedLock` (`:799-824`) used at `:708`, `:757`, `:848`; `GuardedLock` is a record and `Release` a functional interface |
| D3(c) the test class Javadoc names the new test | shipped-as-specified | `PendingEntryRecordsSweepTest.java:93-96` |
| D4 the Maven-only `Unmeasured` outcome stated once | shipped-as-specified | `BuildOutputParser.java:109-112`; true against `core-build/011:77-78,90-91` and `TapOutputParser.java:33-38,50-52` (the CodeRabbit finding removed the wrong TAP example) |

## Three-Layer Analysis

Read on three layers by one read-only level-3 leaf (`execution-context-level-3`, `orchestrator.analyze`), by reading — no
build, no test run, no write. The orchestrator re-read at the merged head: `PendingEntryRecordsSweep.java:128-134`, `:236-254`,
`:335-350`; `PendingEntryRecordsSweepTest.java:838-862`; `BuildOutputParser.java:106-124`; `runtime-job.adoc:291`; the log
`178c128b…log:10-17`. Taken from the leaf's reading, recorded as such: the non-null-constructor reads of
`InternalFaultException`/`StoreRefusal`, the assertion-message character counts, the Gradle/pyproject Unmeasured rules.

- **Dispatch:** one read-only leaf for this landing (one of two landings of this drain, run in parallel with the leaf of 182).
- **Verdict:** layer 1 sound; layer 2 one minor, two info; layer 3 one minor, three info. No blocker, no major, nothing
  reported to the operator at once.

### Layer 1 — Specified

All four deliverables met (table above). The only deviation from the lane's statement is the failing-run evidence (L3-3).

### Layer 2 — Implemented

| Finding | Claim | Severity | Evidence |
|---------|-------|----------|----------|
| L2-1 | the `described` Javadoc says "the fault text this method composes", but the method returns the message or a type label, it composes no text | info | `PendingEntryRecordsSweep.java:338` |
| L2-2 | the `BuildOutputParser` parenthetical lists the parsers' own Unmeasured rules as "a summary whose parts disagree, or a count too large for a long"; Gradle's rule is an int-range overflow, a negative or skipped greater than total, pyproject's "unrepresentable": the list is not the rule set (the hedge "where it has one" softens it) | minor | `BuildOutputParser.java:114-115`; `GradleOutputParser.java:143-146`; `PyprojectOutputParser.java:693-695` (leaf) |
| L2-3 | ADR 016 says a fault with no message is named by type and never printed as `null`; the kept and unreadable entry texts still use a raw `getMessage()` (their messages in `PendingEntryStore` are non-null by the leaf's reading, the codec's `UncheckedIOException` at `:458` was not read) | minor | ADR 016 `:55-56`; `PendingEntryRecordsSweep.java:289`, `:291`, `:399`, `:458` |

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `PendingEntryRecordsSweep.java:346-349` | a blank (`""`) message | passed through: the report ends `not swept: ` naming nothing; out of the spec (null only) | info |
| L3-2 | `:346-349` | an exception built `(null, cause)` | named by type only, the cause text is lost; out of the spec | info |
| L3-3 | `PendingEntryRecordsSweepTest.java:598`, `:621`, `:667`, `:887` | the `run()` catch (`:132`) with a message-less exception | no test reaches it (the five `not swept` assertions come through `remove`); reverting `:132` to `getMessage()` passes the suite; the lane's mutation reverted `fault()` only | minor |
| L3-4 | `PendingEntryRecordsSweepTest.java:790-796`, `:841-843` | — | the new test has no Javadoc and sits after the helpers, apart from its release-fault siblings; the `guardedLock` Javadoc "`null` to end the mock" is unclear | info |
| L2-4 | `:348` | an anonymous exception class | `getSimpleName()` is empty, the text ` with no message`; theoretical | info |

Checked and clean: no site passes `getMessage()` raw; the non-null report text is byte-identical (`described` returns the
message unchanged); the delegate is closed in a `finally` on every path and `assumeFalse` still precedes
`openTransaction`; the assertion messages are 36 to 47 characters; `@SuppressWarnings("java:S2583")` unchanged at `:166`;
no file outside the surface. Observed, owned elsewhere: the same false "unfinished TAP plan" example and the Maven-only
outcome stated three times stand in `runtime-job.adoc:291` (the orchestrator re-read the line), owned by PLAN-V2-162.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | no generic exception caught or thrown in production | conforms | `described(RuntimeException)` is a parameter type; the catch lists five named types (`:130`) |
| CLAUDE.md § Code Standards | assertion messages 20 to 60 characters | conforms | 36 to 47 (leaf count) |
| CLAUDE.md § Code Standards | EasyMock only, no new hand-written fake | conforms | `guardedLock` builds EasyMock mocks; a record and an interface only |
| CLAUDE.md § Code Standards | Javadoc, ADR and guide say what the code does | conforms with minors | L2-1, L2-2, L2-3 |
| CLAUDE.md § Code Standards | a new test fails on the code it guards | partly | the failing run exists but its text is not in the log; the `run()` catch is unguarded (L3-3) |
| CLAUDE.md § Dependencies | no dependency or plugin, 100 files, `JobLog.java` untouched | conforms | 5 files |

## Metrics and Anomalies

- Tokens: `unknown`; `plugin_version` `unknown`: the known recurrence of Open Defect 7 (owner E27), no new defect.
- Eight builds, all `daemon_longpoll`, none degraded; 12841 tests at four heads and 12860 at the merge head (lane statement,
  not re-run). The lane's rule check lists thirteen shell reads and one `&&` probe (the harness has no guard): recorded, no
  finding against the code.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `3f0a9207` with one inline finding (the TAP example), adopted in `adcc5e37` and the
  thread resolved; Sourcery (optional) refused on its own quota. Counts claimed: 0 unresolved threads, 1 review-body finding
  handled, 0 Sonar issues (`stated, not verified`; CI re-read: SonarCloud success).
- CI/merge: merged through the merge queue as `5c060a53`; 20 checks, overall success.
- Collisions: none observed with the running plans 162, 182 and 126.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#606`; `landing`; `plan_marshall_plan_id` `rb-v2-184-sweep-first-fault-message-and-statement-closeout`
- [x] landing message `-001` archived by its exact name
- [x] residue staged — see Follow-Ups
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

No blocker and no major, so nothing goes to the operator at once. L2-1, L2-2, L3-3, L3-4 and the optional L2-3, L3-1, L3-2 fit
no staged plan (none names `PendingEntryRecordsSweep`, `PendingEntryRecordsSweepTest` or the Javadoc of `BuildOutputParser`);
they are carried as item 106 into the NEW staged PLAN-V2-186 `sweep-run-catch-test-and-statement-residue` (WS-03, runbook).
The `runtime-job.adoc:291` example stays owed to PLAN-V2-162 (PR #567). Recorded, not fixed: the lane's counts of runs and
logs (`stated_not_verified`), the `unknown` figures (owner E27). Owed to the operator: the sweep and cut-log residue stream
(181, 183, 184, now 186) shrinks each round; ending it after 186 is the operator's call.
