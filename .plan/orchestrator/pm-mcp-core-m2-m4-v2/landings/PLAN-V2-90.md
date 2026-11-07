# Landing Analysis: PLAN-V2-90 — Generated test data in the pm-runtime job, launch, lsp, lock and audit tests

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #314 (merge commit `e8e7666c`; 69 files, +4794/-2377; test code only; #313 was closed and replaced by #314 to get a fresh review)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-90.md`. A pasted claim is a lead, never a fact.

Source: the operator's paste of the plan's own report (the plan was started with the old worktree path, so it filed NO landing message) and the archived plan `.plan/local/archived-plans/2026-11-07-plan-v2-90-runtime-tests-jobs/`. Corroborated by the orchestrator: Sonar `main` (analysis of `262059a5`, which contains `e8e7666c`) gate OK, 0 open BUGs; `Maven Build` and `Integration Tests` runs of `262059a5` (push and merge_group) green. Read at `e8e7666c` by a read-only sub-agent (script scans over the 69 files; nothing built); points marked **checked** were re-read by the orchestrator. The plan used 36.9M tokens and 24 h 46 min against a stated budget of about 1.6M.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 every class converted, no test removed | met, weakened | 53 classes, methods 590 to 592, no assertion lost; C1 to C4 |
| D1 shared generators in the module artifact | met in form, partly in substance | six generators with their own tests (18 methods) in `RuntimeGenerators`; nine classes keep an inline copy (C4) |
| D2 `AuditRotationContractTest` | not delivered, by decision | the spec's premise is false: `AuditRotation` is a final class with identity equality (`AuditRotation.java:104`); only the private record `Edges` has `equals` |
| D3 seven unasserted log records | met | asserted by constant at `SubprocessJobRunnerTest:1248,1274,1648,1601-1607`, `HarnessVerifierTest:100,182,184`, `LspClientTest:77,79,127`; the descendant-audit record by the library handler plus a count of 2 (discriminating) |
| Done-when per-class table (generator calls, literals kept) | **not met** | the PR table has "kept literals (category)" and "methods" only |
| Done-when build, coverage, integration tests, Sonar, links | not shown | `package`, not `verify`; coverage not run; links check ran after the merge |

## Three-Layer Analysis (standing rule R21, r6)

### Layer 1 — Specified
- **S1 (medium, checked).** Deliverable 2 rests on a refuted OBSERVED claim; the verify-first clause did not cover it.
- **S2 (medium).** Four of the five log constants the spec names belong to `start` (PLAN-V2-89), outside the Expected Surface; the real set was seven records.
- **S3 (medium).** Done-when satisfiable by form; the Standing Constraints carry the weakness bullets of 36, 88, 86 and 87 only, not 89 and 92. Two of 92's weaknesses recur (C3, the Done-when form).
- **S4/S5 (low).** The spec cites rules r4 (epic file r6; r5 and r6 add R21, which binds the orchestrator only); demands `-Pcoverage` and `pm-core` integration tests for a test-only `pm-runtime` plan; does not say where new helpers go.

### Layer 2 — Implemented
- **I1.** Test tree only: no `src/main`, POM or document change (54 modified, 15 added; the PR body says 66). **I2.** `ProbeBudgetTest` has the controller and no `Generators` call (dead controller; its statement is true). **I3.** Counts as in the table; `type()` and `everyReason…` moved to `@EnumSource` (equal or broader).
- **I4 (low).** `HostToolCalls`, `ProcessLiveness`, `JsonDocuments` are `public final class` with a private constructor, not `@UtilityClass` (CLAUDE.md); `README.adoc:51` and the generators `package-info.java` still say one generator per type.
- **I6 (medium).** No per-class counts of generator calls and kept literals; a script recount gives about 2090 to 1701 non-message literals in the 53 classes (heuristic). **I7 (low).** Only `verify -Ppre-commit`, `quality-gate` and `module-tests`; integration tests on an earlier tree.

### Layer 3 — Correct
- **C1 (medium, checked).** The fixture version range is `>=1.0.0 <2.0.0`. Old `HeadlessBlockTest.supportedVersions` pinned `1.4.2`, `2.0.0`, `0.9.9`; the new test draws `above = integers(2,99) + "." + minor()` and `below = "0." + minor()` (`HeadlessBlockTest:105-107`), and `HarnessVerifierTest:154` draws `integers(2,9)` for the major. An implementation that accepts `2.0.0` passes (probability of drawing it about 1 in 10^5, and 1 in 3,600). The recurring lost-boundary weakness.
- **C2 (low).** `OutcomeDerivationTest`: declared code 2 and undeclared 3 became `declared = 1..100`, `undeclared = declared + 1..100`, so the adjacent code is rare. Other boundaries kept (`HolderLivenessTest`, `AuditRotationTest` limits, `TerminationProtocolTest` `Integer.MAX_VALUE + 1L`).
- **C3 (medium, checked in part).** The detail was the failure message and is now static: old `ArgumentCheckTest:158-159,170-171` had `refusal.detail()`; the new lines `:162,:176` do not. Also `ArgumentCheckTest:283,454`, `AuditVerificationTest:135,193,657`, `JobRefusalTest:90`, `VerifyNotResolvePipelineTest:132`, `TerminationProtocolTest:393-416`. Same defect as Wa75 C2.
- **C4 (medium, checked in part).** Nine classes keep an inline `letterStrings(...).toLowerCase(Locale.ROOT)`: `DerivedFactStoreLockTest:91` and `FileLockManagerTwoProcessIT:75` both write `"project-" + letterStrings(2,10).toLowerCase`, though `RuntimeGenerators.shortWords()` is 2 to 10 and `CoreGenerators` has `projectEnrolments()`; also `ArgumentSchemaValidatorTest:78`, `WorkerEgressProxyTest:60`, `VerifyNotResolveAdversarialTest:289`, `WorkerJobTest:105`, `AuditTrailFixture:92`, `SubprocessJobRunnerTest:1966`; the `major.minor.patch` helper is copied in `HarnessEnrolmentStoreTest:294`, `WorkerJobTest:200`, `HarnessVerifierTest:265`.
- **C5 (OK).** No `static final` or instance field filled by `Generators` outside `@BeforeEach` (the 86/87/89 weakness did not recur); `Instant.now()` and `JobId.random` residue is disclosed.
- **C6 (low).** Order tests are built deliberately; round trips lost punctuation in `JobLogTest` and the audit torn lines (allowed by the spec); no false per-datum contract claim found.
- **C7 (low).** The new generator tests draw once (`FailingExitCodeGeneratorTest`, `PlanIdGeneratorTest`, `WordGeneratorTest`). **C8 (OK).** 1699 assertion calls, none without a message, none outside 20 to 60 characters.
- **C9 (OK, pre-existing).** `LspClientTest.stubborn` keeps its 2 s initialize timeout (old `:96`, new `:121`); the flake is not introduced, and not fixed.

## Verdict

D1 (conversion), D3 (log records) and test-only scope are met, the static-field weakness did not recur and the diagnostics of the log records are good. Not met: the per-class table, the verification evidence, and the three recurring weaknesses (lost boundary C1, lost diagnostics C3, copied helpers C4). Main is green. No hidden breaking change: test code only.

## Ownership

- PLAN-V2-91 (runtime tests, workers and endpoints) and PLAN-V2-93: bullets for lost diagnostics and the version-range boundary (added). PLAN-V2-104/105 (assertion messages, runtime): the `job`, `launch`, `lsp`, `lock`, `audit` packages are met (verify-first shrinks them). PLAN-V2-106 (EasyMock for fakes): reads `FakePmExec`, `RecordingAuditSink`, `LockHolderProcess`.
- No owner: C1, C2, C3, C4, I4, I6, I7 and the `stubborn` flake. A fix plan only on the operator's word (Wa77). Five findings about `plan-marshall:*` components stay in the plan's retrospective report, not here.
