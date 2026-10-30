# PLAN-V2-06: Replace the hand-written fakes and recorders with EasyMock doubles

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-06-easymock-for-fakes.md` and is queued as one row file, `queue/PLAN-V2-06.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
>
> Origin: the skills conformance audit of 2026-10-30 (`skills-audit-2026-10-30.md` in the epic directory), taken at `origin/main` `6847c748`, and the operator's decisions of the same day (Decisions in `epic.md`). The plan family is PLAN-V2-83 to PLAN-V2-108 (workstream WS-11, with PLAN-V2-95 and PLAN-V2-96 in WS-05); it runs first, except PLAN-V2-96, which follows PLAN-V2-31.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12 to R16: test-only.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none; ADR directory: none; layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

`cui-testing` forbids Mockito and PowerMock and says to use EasyMock or the CUI patterns; the repository has about 28 hand-written fakes, stubs and recorders (`FakeBus`, `FakeSecretService`, `FakePmExec`, `RecordingAuditSink`, `RecordingLockManager`, `RecordingProcessCall`, `FailingAuditSink`, `FailingCreateLocks` and the fixture classes of `engine`, `model`, `scenario`, `task` and `worker`) and no mocking library. The operator decided on 2026-10-30 (decision 11) to replace them with EasyMock; the dependency is allowed and added by PLAN-V2-83.

## Deliverables

1. **Inventory and the rule.** List every class under `src/test/java` (and the shared test JAR of `pm-runtime`) whose name starts with `Fake`, `Stub`, `Recording` or `Failing` or that is a fixture holding such a class: `git grep -l -E '(class|record|interface) (Fake|Stub|Recording|Failing)' -- '*/src/test/java/*.java'`. For each, decide and record in the pull request body: replaced by an EasyMock double (the default), or kept because it is a real in-memory implementation that many tests share and an EasyMock double would only restate it — in that case the entry names the reason, and the operator is told in the landing (the decision was 'replace the fakes').
2. **Replace in `pm-core`, `pm-workflow` and the providers (test first: the tests are the guard).** Convert the recorders and failing sinks (`RecordingAuditSink`, `RecordingLockManager`, `FailingAuditSink`, `FailingCreateLocks`, the fixtures of `engine`, `model`, `scenario`, `task`) to EasyMock mocks and captures (`Capture`, `expectLastCall`), keeping each test's assertion intact.
3. **Replace in `pm-runtime`.** The same for `FakePmExec`, `RecordingProcessCall`, `RecordingAuditSink` (runtime `lock`), `SupervisorTestFixture`, `RecoveryStepFixtures` and the fixtures of the endpoint tests.
4. **The shared test JAR.** `pm-runtime` builds a test JAR (`TestBases`, `FakeBus`, `FakeSecretService`) that the assembly's tests in `plan-marshall-mcp` use. Converting these changes that JAR's API: the pull request body lists every removed or changed class under **Owed outside this repository**, and the plan prepares the matching change for `plan-marshall-mcp` (the lane may not edit another repository: the change goes into the pull request body as a patch and into the operator's list); pre-1.0, no shim, so the SNAPSHOT consumers break until that pull request merges.

**Done when:** `git grep -l -E '(class|record|interface) (Fake|Stub|Recording|Failing)' -- '*/src/test/java/*.java'` finds only the entries kept with a reason; EasyMock is the only test-double library; all tests of the touched classes pass with the same method count; the owed list for `plan-marshall-mcp` is in the pull request body. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `cui-testing` (SKILL.md and standards: test doubles, EasyMock); `junit-core`; R20; decision 11 in `epic.md`; the audit `skills-audit-2026-10-30.md` § 2.6 and § 5 item 6.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **EasyMock is allowed by the operator (2026-10-30);** no other library, no Mockito, no PowerMock, no Hamcrest.
- **Test code only,** except the shared test JAR's API in `pm-runtime`, which is test code that other repositories consume (Deliverable 4).
- **R19 is replaced:** the Expected Surface names the test packages that hold the fakes; the plan runs after the test migrations and before the assertion-message plans.

## Claim Labels

- OBSERVED: no mocking library in any POM; hand-written doubles under `src/test/java`: `FailingAuditSink`, `RecordingAuditSink`, `RecordingLockManager` (core `store`), `FakeSecretService`, `FakeBus` (runtime `credentials`), `FakePmExec` (`job`), `RecordingAuditSink` (`lock`), `RecordingProcessCall` and `RecoveryStepFixtures` (`start`), `SupervisorTestFixture` (`worker`), `FailingCreateLocks` (workflow `consult`), the `EngineFixtures`, `ModelFixtures`, `ScenarioFixtures`, `TaskFixtures` (`skills-audit-2026-10-30.md` § 2.6, 28 files by the audit's scan)
- HYPOTHESIS: some of these (`FakeBus`, `FakeSecretService`) implement enough behaviour that an EasyMock double is longer than the fake; Deliverable 1 decides each
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/dbus/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/lock/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/start/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/consult/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/model/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/task/` (directory; R19 replaced)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (EasyMock), PLAN-V2-107 (the generator artifacts: the shared test JAR is published in that shape), PLAN-V2-85 to 93 (the test migrations), PLAN-V2-97 to 99 (they change signatures of the doubled types).
- PLAN-V2-101 to 105 (assertion messages) depend on this plan.
- Overlaps by directory with the test migrations; it runs after them.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-06-easymock-for-fakes.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
