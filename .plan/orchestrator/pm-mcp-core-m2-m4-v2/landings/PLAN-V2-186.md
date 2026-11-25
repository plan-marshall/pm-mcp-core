# Landing Analysis: PLAN-V2-186 — The residue of the sweep's fifth closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
pr: #609 (https://github.com/plan-marshall/pm-mcp-core/pull/609), merge `3aea14c21ae1b8e69ae09c47def5ff95ee7eb657`, 5 files (+54/-33)

> Landing record for one shipped plan (runbook lane, opencode, `deepseek-v4.1-flash/high`). Written by the `analyze` verb
> of the thirty-sixth run (2026-11-25) from the inbox message `rb-v2-186-sweep-run-catch-test-and-statement-residue-001.md`
> (revision 0), corroborated against git (`git show --stat 3aea14c2`), the PR (`ci pr view`, `ci checks status`: 20 checks,
> overall success) and the merged code, read by one read-only level-3 leaf and re-read by the orchestrator.

## Deliverable Fidelity vs Spec

The five files are exactly the spec's Expected Surface; `JobLog.java`, `runtime-job.adoc`, every POM and every dependency are
untouched. The diff of the two main-code files (`PendingEntryRecordsSweep.java`, `BuildOutputParser.java`) is comments only:
no behaviour changed.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the `run()` catch is unguarded (test, or the decision that no input reaches it) | shipped-as-specified (fallback branch) | no test; the decision stands in the `described` Javadoc, `PendingEntryRecordsSweep.java:342-345`; the leaf re-traced it and found it true (below) |
| D2 the test beside its siblings with a Javadoc; `guardedLock` wording; `described` wording | shipped-as-specified | `PendingEntryRecordsSweepTest.java:781-785` (Javadoc), `:745-779` (sibling before it), helpers after (`:813`, `:830`); `:827` "a no-op"; `PendingEntryRecordsSweep.java:338`, `:348` no longer says "composes"; the test body is byte-identical (leaf diff) |
| D3 the `BuildOutputParser` parenthetical | partly | the false parenthetical is gone; the replacement is wider than the code (L2-1, L3-1) |
| D4 the ADR and guide scoped to what the code does | shipped-as-specified (option i) | ADR `runtime-start/016:61-66`, guide `runtime-start.adoc:130-136`; every site named matches `PendingEntryRecordsSweep.java:289`, `:291`, `:403`, `:460`, `:462` |

The lane's S1 holds: the spec said five `not swept` assertions, the class has six (`PendingEntryRecordsSweepTest.java:598`,
`:621`, `:667`, `:718`, `:801`, `:892`), all through `remove`; the sixth is the new test. The lane's statement that D1 is
met "via the fallback branch" is correct: the spec's Done-when allows it.

## Three-Layer Analysis

Read on three layers by one read-only level-3 leaf (`execution-context-level-3`, `orchestrator.analyze`), by reading — no
build, no test run, no write. The orchestrator re-read at the merged head `3aea14c2`: `BuildOutputParser.java:106-120`,
`TestCounts.java:98-102` and `:136-148`, `PendingEntryRecordsSweep.java:128-134` and `:336-354`, ADR `016:55-67`, guide
`runtime-start.adoc:126-138`, and the callers of `TestCounts.from` (all five parsers, in `pm-core`). Taken from the leaf's
reading, recorded as such: the non-null messages of the producers of the D1 trace (`PendingEntryStore`, `JournalScope`,
`StoreSnapshot`, `PendingEntryCodec`, `StoreRefusal`, `InternalFaultException`), the JDK message of a lazily wrapped
`UncheckedIOException`, the assertion-message counts (36, 38, 48).

- **Dispatch:** one read-only leaf for this landing; the reason: reading merged code is the depth the dispatch rule exists for.
- **Verdict:** layer 1 sound (D3 partly); layer 2 two minors; layer 3 no blocker, no major, one minor, four info.

### Layer 1 — Specified

D1 through the fallback, D2 and D4 as specified, D3 partly. The D1 decision was re-traced independently: the lock open,
acquisition and attempt sit inside `remove()`'s own catch (`:167-181`, `:209`), whose handlers only call `fault`,
`faultBeside`, `reach` and `described`; the one fault of the five types the candidate scan raises is the listing's
`UncheckedIOException` of `PendingEntryStore.entryIds()` (`:383-393`), message composed and non-null; `isResidue` catches
`StoreRefusal` and `UncheckedIOException` (`:456-462`) and `store.read` raises only those; `InternalFaultException` and
`StoreRefusal` compose non-null messages. The decision is true at the producers read. Not read (stated by the leaf): the
`PendingEntryRecord` compact-constructor bodies, `StrictJson`'s other helpers, `WorkflowStatusCodec`'s readers.

### Layer 2 — Implemented

| Finding | Claim | Severity | Evidence |
|---------|-------|----------|----------|
| L2-1 | `BuildOutputParser` says each parser "states it beside its count scan": only Jest's has a Javadoc statement of its rule; Gradle (`GradleOutputParser.java:143-145`), pyproject (`:678-695`) and TAP (`:462-471`) state none, Maven has an inline comment on `parse` only | minor | `BuildOutputParser.java:115-116` (re-read); the leaf read the four parsers |
| L2-2 | the `described` Javadoc `@return` says "the type label", the code returns `<SimpleName> with no message`, and a blank message is returned as is (stated in ADR and guide, not in the Javadoc) | info | `PendingEntryRecordsSweep.java:347-353` (re-read) |

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `BuildOutputParser.java:115-116` | a reader of "no rule is shared between the parsers" | false: every parser's count passes `TestCounts.from` (`TestCounts.java:138-147`: negative, beyond the `int` range, or a total beyond it is `Unmeasured`), called at `GradleOutputParser.java:156`, `JestOutputParser.java:227`, `MavenOutputParser.java:635`, `PyprojectOutputParser.java:695`, `TapOutputParser.java:468` (re-read); `TestCounts.java:100-101` itself says "by its own rules", so the two statements pull apart | minor |
| L3-2 | `PendingEntryRecordsSweep.java:343`, `:130-132` | a reader of the catch | the decision "the `run` catch needs no guard" stands on a private method's Javadoc, not at the catch, and nothing tests it (a future producer change is caught by `described` itself: no behaviour risk) | info |
| L3-3 | ADR `016:61-66` | the three earlier additions each carry "amended 2026-11-24 by `PLAN-V2-n`" (`:51`, `:55`, `:56`) | this one names the plan only in an aside, with no amended stamp | info |
| L3-4 | `runtime-start.adoc:135-136` | the inserted block | an orphan short line "A fault of" before "another type leaves `run` uncaught" (re-read) | info |
| L3-5 | `PendingEntryRecordsSweepTest` display name | the literal `IllegalStateException with no message` is the report contract, the display name does not say so (CLAUDE.md asks it); pre-existing, the move did not introduce it | info |

Boundaries checked and clean: the diff of the main code is comment lines only; the moved test is unchanged in body, with 36
to 48 character assertion messages; EasyMock only, no new fake, no phase comments; no new dependency; no file outside the
surface; the three 184 L2-3 rows (`:289`, `:291`, `:399`→`:403`, `:458`→`:462`) and 184 L3-1, L3-2 are now stated correctly.
Theoretical, recorded not fixed (leaf, info): an `IllegalArgumentException` with a null message out of the codec path would
reach `StoreRefusal.unreadable`'s `requireNonNull` and escape `run()` as a `NullPointerException`; no producer does this.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | Javadoc, ADR and guide say what the code does | deviates (minor) | L2-1, L3-1 |
| CLAUDE.md § Code Standards | assertion messages 20 to 60 characters | conforms | 36 to 48 (leaf count) |
| CLAUDE.md § Code Standards | EasyMock only, no new hand-written fake, no AAA comments | conforms | `GuardedLock` and `Release` pre-existing |
| CLAUDE.md § Code Standards | no generic exception caught or thrown in production | conforms | the five named types |
| CLAUDE.md § Dependencies | no dependency or plugin, 100 files, `JobLog.java` untouched | conforms | 5 files |

## Metrics and Anomalies

- Tokens: `unknown`; `plugin_version` `unknown`: the known recurrence of Open Defect 7 (owner E27), no new defect.
- Nine builds, all `daemon_longpoll`; one `indeterminate` (`test_selector_matched_nothing`, re-run with
  `-Dsurefire.failIfNoSpecifiedTests=false`); two `verify -Ppre-commit` runs outlasted one call and were re-attached. Counts
  (8497 tests, 23 targeted, 337 s and 321 s) are the lane's statement, not re-run.
- The lane's two executor-version reads disagreed on the patch number (`0.1.2183` against `0.1.2184`); both worked: recorded.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `e5a5b3bf` with 0 actionable findings; Sourcery (optional) refused on its quota. Counts
  claimed: 0 unresolved threads, 0 review-body findings, 0 Sonar issues (`stated, not verified`; CI re-read: overall success).
- CI/merge: merged through the merge queue as `3aea14c2` (2026-11-25 09:00Z), after `c484b594` was merged in; 20 checks, overall
  success. That merge precedes the pm-api SNAPSHOT `-228` (11:19Z), so it never met the profile-schema break below.
- Collisions: none with the running plans 162, 126 and 185.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#609`; `landing`; `plan_marshall_plan_id` `rb-v2-186-sweep-run-catch-test-and-statement-residue`
- [x] landing message `-001` archived by its exact name
- [x] residue staged — see Follow-Ups
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

No blocker and no major, so nothing goes to the operator at once on this landing. L2-1, L2-2, L3-1 to L3-5 fit no staged plan
(none names `BuildOutputParser`'s Javadoc, `PendingEntryRecordsSweep`, ADR `runtime-start/016` or the guide; 126 names
`BuildOutputParser` for tests only); they are carried as item 107 into the NEW staged PLAN-V2-187
`build-output-parser-statement-and-sweep-wording-residue` (WS-03, runbook, 2 deliverables, wording only). Recorded, not fixed:
the null-message corner of the codec path (above), the anonymous-class `getSimpleName()` (184 L2-4), the lane's counts
(`stated_not_verified`), the `unknown` figures (owner E27), the `runtime-job.adoc:291` example owed to PLAN-V2-162.
Owed to the operator: the sweep and cut-log residue stream (181, 183, 184, 186, now 187) has shrunk to wording of a
parenthetical; ending it after 187 is the operator's call, recommendation: yes.
