# Landing Analysis: PLAN-V2-86 — Generated test data in the second half of the pm-core store tests

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #301 (merge commit `06d97ab1`; 60 files, +4533/-2385)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-86.md`. A pasted claim is a lead, never a fact.

Source: **no inbox landing message was filed.** The plan was started with a spec path as its task (plan-marshall form), so its `source_id` was no orchestrator
pointer and `emit-landing` never ran (the same cause PLAN-V2-62 recorded). The claims are read from the archived plan record
(`.plan/local/archived-plans/2026-11-06-plan-v2-86-core-store-tests-b/`: `quality-verification-report.md`, `request.md`, `solution_outline.md`,
`work/code-review-findings.json`), harness claude, model claude-sonnet-5-5, effort medium, executor 0.1.2041, bundle 0.1.2041; the PR body.
Corroborated: `ci pr view 301` `merged`; `ci checks status` `overall_status: success`; Sonar `main` (analysis of `06d97ab1`, 2026-11-06 11:28 UTC) gate OK,
0 open BUGs; `Maven Build` and `Integration Tests` push runs of `06d97ab1` green. No landing-check was possible (no message).
The plan's retrospective is candid: it names the two lost boundaries, the duplicated helpers and the static draws below, and says pm-runtime coverage did not complete in
one run, the integration-test and links-check runs are not recorded in the plan logs, and the merge proceeded on the operator's consent.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 convert the second half (58 files) of the store tests | **partly** | 51 of 58 classes carry the controller, 260 `Generators` calls (0 before), 0 `@GeneratorsSource`; two request boundaries lost (C1, C2); helpers copied (I9) |
| D2 contract test for hand-written `equals` | met in form, with a production change | `ScopeTransactionContractTest` for the nested record `PendingTarget` (S2, S3); it proves little value equality (I11) |
| Assertion messages 20 to 60 characters in the plan's classes | met | the sub-agent's scanner found 1406 assertions in scope and none without a message or outside 20-60 |

## Three-Layer Analysis (standing rule R21, r6)

Done at `06d97ab1` by a read-only sub-agent (grep and a bracket-aware scanner; nothing built or run), then the points marked **checked** re-read by the orchestrator.

### Layer 1 — Specified
- **S1 (medium).** "the second half of the 89 files": the directory held 113 files (114 now); the sort rule gives 58. The count was wrong, the rule right.
- **S2 (medium).** The spec says `ScopeTransaction.java:132` declares `equals`/`hashCode`; they sit at lines 134 and 142 of the private nested record `PendingTarget`, so D2 ("ScopeTransaction gets a contract test") was unreachable from test code.
- **S3 (medium, checked).** The Standing Constraint "no production class changes" was breached once: `ScopeTransaction.java:131` `private record PendingTarget` became `record PendingTarget` (the whole `src/main` diff, 1 line). Decided by the operator at refine
  (`request.md:102-117`), disclosed in the PR body and the commit; behaviour-neutral. The spec was never amended and no Javadoc or doc records why (C6).
- **S4 (low).** `CLAUDE.md` requires a contract test for a class with hand-written `equals`, not a record; `PendingTarget` is a record, so the rule did not force D2. Defensible, because a record with a `byte[]` component is where hand-written `equals` is risky.
- **S5 (low).** The Done-when asks for a per-class literal table; the PR body has 59 rows, and the retrospective says its absolute counts are not reproducible by an escape-aware recount. The integration-test, links-check and coverage runs it also demands are not evidenced.
- **S6 (low).** "put a repeated generated expression into the generator artifact" has no threshold: no check can confirm it.

### Layer 2 — Implemented
- **I2.** Form met, depth thin: several classes meet "at least one call" with exactly one (`ProjectResolverMovedCheckoutTest`, `QuestionLogClosingTest`, `StoreRefusalTest`).
- **I3 (low).** `@GeneratorsSource` 0 uses; 24 files hold parameterized tests on `@ValueSource`/`@CsvSource`/`@EnumSource`, mostly boundaries and wire cases; candidates `ProjectResolverMainCheckoutTest:39` and `ProjectResolverWorktreeTest:94,121`. Same gap as PLAN-V2-85.
- **I4.** Raw literals of 3 or more characters rose 4145 -> 4862 (42 of 59 classes up, 10 down; display names and new messages inflate it); the sub-agent's heuristic count (display names, comments and messages stripped) falls about 3227 -> 2710.
- **I7.** The four "every datum is a contract" statements (`PendingEntrySchemaParityTest:39`, `RecordingLockManager:28`, `SerializingLockManager:27`, `SymbolicLinks:18`) are true per datum where read.
- **I8.** No `Random`, `UUID.randomUUID` or new hand-written fake; `RecordingAuditSink` and `RecordingLockManager` stay for PLAN-V2-106. Test methods 534 -> 541, no file lower.
- **I9 (medium, partly checked).** **Checked:** 20 classes of the store test directory define their own `lower()`/`lowerName()` helper (a lowercased `letterStrings(3,12)`); the sub-agent counts 32 files carrying the expression and 5 hex/bytes helpers; no generator was added to
  `pm-core/.../testing/generators/` (it holds `CoreGenerators` and four type generators). The spec's generator-artifact rule is breached, as the retrospective says.
- **I10 (medium).** 59 `static final` fields drawn from `Generators` or a drawing helper in 10 files (`WorkerStateCodecTest` 14, `QuestionRecordCodecTest` 13, `WorkflowStatusCodecTest` 9, `WaiverRecordCodecTest` 7 ...); `QuestionRecordCodecTest:67-90` values are shared by the QuestionLog*, Waiver* and WorkflowStatus* tests by static import.
  A `@GeneratorSeed` replay of one test does not reproduce them; the spec's seed constraint is contradicted and the plan recorded it (code-review finding F5). Same pattern as PLAN-V2-87 I5.
- **I11 (medium).** `ScopeTransactionContractTest` adds one positive value-equality test and the library's basic check; there is no inequality test, so an `equals` that ignores `mode`, `path` or `mustBeAbsent` would pass. The `Serializable` arm is replaced by its opposite with a documented reason.

### Layer 3 — Correct
- **C1 (high, checked).** `QuestionLogAnswerRevisionTest:63-66` (`otherRevisions`): `Generators.integers(3, 100).next()` replaced the literal 3, the first revision above the current one (2, written by `revise(...)`). A guard such as `revision > current + 1` still refuses 4 to 100, so the test
  passes with probability about 97/98; `conflictRevision` (line 103) kept its boundary as a literal. The lost boundary is unfixed on `main`.
- **C2 (high, checked).** `QualificationSettingsStoreTest:144` (`staleRevision`): the old test wrote at `current.revision() - 1`; the new one writes at `current.revision() - Generators.longs(1, current.revision()).next()`, so the one-behind write is hit with probability `1/revision`, and a check that
  tolerates one missed update passes. The same file kept 101 as a literal in `bounds()`.
- **C3 (OK).** Order-pinning tests sampled held their order (`ProjectResolverContractTest:84` keeps "zeta-project" and "alpha-project"; `ScopeIntegrityTest`, `OrderedWriteTest`, `WaiverConsumptionLogTest` order by insertion).
- **C4 (OK).** Punctuation: `QuestionRecordCodecTest:105-110` generates commas, a quote, a backslash and a question mark; `ProjectStoreIdentityCodecTest:107` and `QuestionRecordRoundTripTest:88` carry spaces, quotes and escapes. `MetricsStoreTest`, `PendingEntryCodecTest`, `TransactionJournalTest` were not read.
- **C5 (low).** No operand collision found; **C6 (low).** `PendingTarget` is package-private with its logic untouched, correct, undocumented; **C7.** The "cannot fail" check was done by reading, not by mutation.

## Verdict

D1 partly (form met; two boundaries lost; helpers copied; static draws outside the seed), D2 met in form with a one-line production change taken by the operator, the assertion-message requirement met. Main is green. The two boundary losses are real defects in the tests (C1, C2): the
PLAN-V2-85/88 weakness the spec warned against.

## Ownership

- PLAN-V2-101 (assertion messages, core store): its second-half scope is met here; its verify-first clause shrinks it (bullet added). PLAN-V2-106: `RecordingAuditSink`, `RecordingLockManager`. PLAN-V2-114 overlaps only `QuestionRecordMembersOnTheirEventsTest` (hypothesis D2).
- No owner: C1, C2 (the two lost boundaries), I9 (the generator-artifact helpers), I10 (static draws), I3, I11, S3 (a record of why `PendingTarget` is package-private). A fix plan only on the operator's word (Wa72).
- The process fault: a plan started with a spec path writes no landing message (here none, for PLAN-V2-62 one by hand). Its cause is plan-marshall's, not this epic's; see the open defect in `epic.md`.
