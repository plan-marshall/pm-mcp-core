# Landing Analysis: PLAN-V2-89 — Generated test data in the pm-runtime start, config and small packages

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #302 (merge commit `4f91c9cd`; 47 files, +3567/-2021; no `src/main` change)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-89.md`. A pasted claim is a lead, never a fact.

Source: the plan's inbox message `plan-v2-89-runtime-tests-start-and-config-001.md` arrived at 18:54Z, after this analysis was written from the archived plan (the plan wrote it after the fact; `landing-check` `complete: true`, tokens 29,937,234, wall 73,016 s, 10 advisories, `merge_mechanism=merge_queue`). It adds no contradiction: it names the same advisories (static-initialiser draws, the identifier helper copied into about 20 classes), the PLAN-V2-84-free assembly order (plan-marshall-mcp #119 first) and the cause of the missing message (the source id carried the worktree prefix, see `epic.md` Open Defects). The harness, model, effort and plugin version are the start-time stamp, not a landing-time measurement (the finalize run used plugin cache 0.1.2060 against the stamp 0.1.2041). The claims below were first read from the archived plan record
(`.plan/local/archived-plans/2026-11-06-plan-v2-89-runtime-tests-start-and-config/`: `quality-verification-report.md`, `work/verify-first-inventory.md`, the PR body); harness claude.
Corroborated: `ci pr view 302` `merged`; `ci checks status` `overall_status: success` (20 checks); Sonar `main` (analysis of `4f91c9cd`, 2026-11-06 17:32 UTC) gate OK, 0 open BUGs;
`Maven Build` and `Integration Tests` push runs of `4f91c9cd` green. The plan reports all five deliverables fulfilled, 46 of 47 declared files realized (`TestLocks.java` intentionally unchanged),
one undeclared file (`pm-runtime/README.adoc`), and that the operator fixed the assembly first.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 verify-first inventory and the seven start helpers | met | 47 classes, 370 methods, 1892 literals inventoried; no drift, scope not reduced; the helpers hold no random test data |
| D2 14 start recovery and startup tests with log assertions | met | all 19 previously unasserted `RuntimeLogMessages` records are asserted by constant (7 + 12; `ProjectRecoveryTest:360`, `StartupSequenceTest:274` read) |
| D3 9 start drain, sweep and service-unit tests | met | |
| D4 8 killed-runtime and cold-start integration tests | met in form | looks deterministic (fixed clock, protocol constants kept); the "15 tests green" claim was not re-run |
| D5 nine classes of config, web, workspace, test, log and ingest | met in form | 8 of 9 changed |

## Three-Layer Analysis (standing rule R21, r6)

Done at `4f91c9cd` by a read-only sub-agent (grep, `javap` of `cui-test-generator`; nothing built or run), then the points marked **checked** re-read by the orchestrator.

### Layer 1 — Specified
- **S1 (low).** "about 1700 string literals" matches neither the inventory (1892 in the 47 classes) nor the Claim Labels' 5006 (module-wide); the 14 assembly records are 15 in the inventory.
- **S2 (low).** `corpus` is named as scope, but the package holds only the two excluded HTTP classes (PLAN-V2-93).
- **S3 (medium).** The Done-when is satisfiable by form: a controller plus one `Generators` call, or a one-line statement. Four ITs (Bounded, ProjectRecovery, RelayVersionCheck, RuntimeDrain `KilledRuntimeIT`) carry the controller and no `Generators` call.
- **S4 (medium).** The spec carries only the PLAN-V2-88 bullet, not the boundary, helper and static-draw rules of the later landings (86, 87); those are exactly where the plan fell short. PLAN-V2-90 already carries them.
- **S5/S6 (low).** The inventory did not check the PLAN-V2-84 dependency; `pm-runtime/README.adoc` was undeclared (a necessary consequence of the `TestBases` change; R19 does not name it).

### Layer 2 — Implemented
- **I2 (medium).** Raw literals rose 1892 -> 2679 (display names and the new assertion messages inflate it; with a rough strip 1331 -> 1929). The PR body gives no before/after count.
- **I3 (medium, checked).** **Checked:** 30 occurrences of `letterStrings(3, 12)` with a `toLowerCase` in 29 files of the runtime test tree (sub-agent: 27, as private `name`/`lower`/`directory`/`hostName` helpers, e.g. `MachineDefaultsValidationTest:42`, `RuntimeLogMessagesTest:57`, `ActivityServiceTest:53`); `RuntimeGenerators` holds only
  `resolvedSkill`. No generator was added, against the spec's "put a repeated generated expression into the module's artifact" (the inventory itself said none was needed).
- **I4 (medium, partly checked).** **Checked:** `JobRecoveryStepTest:81-83` draws `BOOT`, `CREATED_AT`, `NOW` in `static final` fields. The sub-agent counts 8 such fields in 3 files (`JobRecoveryStepTest`, `StalledScopeDetectorTest:77-81`, `RecoveryStepFixtures:71`) and 26 (narrow) to about 80 (broad) instance fields in 13 classes drawn before the per-test seed is set
  (`GeneratorControllerExtension` is a `BeforeEachCallback`); `MachineConfigurationStoreTest:56-60`, `PeriodicSweepTest`, `RuntimeDrainTest`. The reported seed would not reproduce them (mechanism verified, impact not).
- **I5 (low).** `@GeneratorsSource` 2 uses (both `ColdStartComparisonTest`); no `Random`, `UUID.randomUUID`, new hand-written fake or contract test (none needed); `FakeParticipant` in `RuntimeDrainTest` is pre-existing.
- **I6.** Assertion messages: 1080 assertion calls in the changed files, none missing, none outside 20-60 characters (8 with a generated part: length unverified). Changed files only.
- **I8 (medium).** The PR body has no per-class table (generator calls, literals kept, category) and no Sonar statement; the categories live only in the class Javadoc and the inventory file.
- **I9.** Of the one-line statements sampled (four ITs, `IngestionValidatorTest`, `WorkspaceConfinementTest`) all are true per datum; `"slot-a"`/`"slot-b"` is written in `RuntimeChildProcess:420` and `RecoveryStepsKilledRuntimeIT:64` with no shared constant (low).

### Layer 3 — Correct
- **C1 (medium, checked).** `JobRecoveryStepTest:451`: `BOOT.minusSeconds(1)` became `BOOT.minusSeconds(Generators.integers(1, 86_400).next())`; a guard that treats one second before boot as not-before-boot passes. The at-boot row is kept.
- **C2 (medium, checked).** `MachineConfigurationStoreTest:165`: the stale revision was 2 against current 3 (one behind); it is now `Generators.longs(0, revision - 1).next()`, so the one-behind case is hit with probability `1/revision` and an off-by-one conflict check passes. The same weakness as PLAN-V2-86 C1/C2 (Wa72).
- **C3 (held).** `StalledScopeDetectorTest:151-156` (THRESHOLD-1, THRESHOLD, THRESHOLD+1), the `DrainTimeoutTest` CSV (max, max+1, -1) and the `ColdStartComparisonTest` tolerance edge keep their boundaries.
- **C4 (low).** `PendingEntryRecordsSweepTest` `old`/`young` moved from plus or minus 60 s to a random 1..86400; the original did not test one behind either, so no regression.
- **C5/C6 (OK).** Order tests are built deliberately (`EnrolledProjectsTest:70` prefixes `a-`/`b-`, `ProjectRecoveryTest:189` reverses the input); except `RuntimeDrainTest:124` pins phase order but names random participants, so a sorting implementation would not be caught (the original `jobs`/`web` were alphabetical anyway). Round trips keep punctuation; time-based tests use `Clock.fixed`.
- **C7 (medium).** The assembly follow-up was done before the merge: plan-marshall-mcp PR #119 (`51cc599`) declared `cui-test-generator` in the test scope of `pm-mcp-server` at 16:42Z, #302 merged at 17:01Z. The assembly uses only `TestBases` from `pm-runtime:tests`. The need is documented only in `pm-runtime/README.adoc:56-61` and the PR body, not in ADR 012 or the `CLAUDE.md` "Generator Artifacts" section (low).
- **C8 (low).** `ColdStartComparisonTest` and `StartupColdStartIT` dropped `verdict.describe` from assertion messages (the IT still logs the verdict before asserting). **C9 (pre-existing).** The root-uid vacuity of `shouldReadNoStoreOrUnit` (`StartupSequenceTest:301-328`) and the row-flag branching were already there.

## Verdict

D1 to D5 met in substance; messages and log constants fully. The "literals converted" claim cannot be read from the numbers and the PR body omits the required table. The PLAN-V2-86 weaknesses repeat: 29 copies of the same helper, draws outside the per-test seed, two lost
one-behind boundaries (C1, C2). Main is green. No hidden breaking change for the assembly.

## Ownership

- PLAN-V2-104 (assertion messages, runtime a): its `start`, `config`, `web`, `workspace`, `test`, `log` and `ingest` part is met here (bullet added; verify-first clause shrinks it). PLAN-V2-106: `RecordingProcessCall`, `RuntimeDrainTest.FakeParticipant`.
- No owner: I2, I3, I4, I8, C1, C2, C8, and the `ADR 012` / `CLAUDE.md` note about the generator need of `TestBases`. A fix plan only on the operator's word (Wa73).
