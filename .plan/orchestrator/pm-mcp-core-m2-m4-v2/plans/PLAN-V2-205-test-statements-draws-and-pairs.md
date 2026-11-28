# PLAN-V2-205: The test statements are true, the draws are under the seed, the controllers have a draw and the pairs that must differ cannot be equal

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: PLAN-V2-126, deliverable 4 (the operator narrowed 126 to D1 on 2026-11-25; one PR per the operator's "four PRs per spec")
lane: runbook
> Staged plan spec, one shippable unit of work (one PR), ready for `/plan-marshall-runbook-lane` hand-off. SELF-SUFFICIENT together with the standing rules.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R14, R16, R19, R20 (the drafter's candidates, confirmed by the orchestrator against the rules file at staging; an id the lane finds not applicable it says so).

## Objective

The generator conversion of the `pm-runtime` and provider tests was accepted by form: "a controller plus one `Generators` call". Statements that "every datum is a contract" are false where the fixture draws. Several classes enable the controller and draw nothing. Static and field draws happen before the seed. Pairs that must differ are drawn independently, so on equal draws `replace()` and the project-first resolution pass without discriminating. This plan makes each statement true per datum or removes it, gives each controller a draw, moves the draws under the seed, makes the pairs unequal by construction, and delivers the per-class table the earlier plans never produced.

## Deliverables

1. **Scan first: the per-class table.** Before and after, per class: statement classes, true per datum, controllers without a draw, static and instance draws moved. Put the counting command in the PR body. The scan covers all 26 files of `pm-runtime/src/test` that carry "every datum" (3ba79c4f), not only the nine below.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
2. **Statements (a).** Make each true per datum or remove it, and draw what is not a contract.
   - `WorkerSupervisorLaunchTest` (`:42`) and `WorkerDrainParticipantTest` (`:43`) say every datum is a contract, while `SupervisorTestFixture` draws the project, plan, sha and generation ids.
   - `EnrolmentFileCheckTest` says it over `someone-else` (`:133`, `:223`) and `link.json` (`:115`, `:211`).
   - `ActivityEventMapperTest` (`:37`) calls the tool names the wire contract while `toolName`, `argumentValue` are generated (`:74-76`).
   - `DbusConnectionTest` (`:35`) says the unique name's numeric part is generated while `:73` asserts `":1.7"`.
   - `RemotePolicyTest`.
   - `GitHubCiProviderTest:86` and `GitLabCiProviderTest` (`OWNER = "cuioss"`).
   - `WorkerGenerationContractTest:79-80` builds task ids as `"qt-" + integers(1, 99)` and should use `RuntimeGenerators.queueTaskIds()`.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:javadoc
   - Persona: module-tester
3. **Controllers without a draw (b), and draws before the seed (c).**
   - `BoundedVerificationKilledRuntimeIT`, `ProjectRecoveryKilledRuntimeIT`, `RelayVersionCheckKilledRuntimeIT`, `RuntimeDrainKilledRuntimeIT`, `ProbeBudgetTest`, `RemotePolicyTest`, `UnservedWantTest`, `GitHubOperationTest`, `SonarProviderTest` each get the draw the Javadoc promises, or lose the annotation.
   - Static finals at `JobRecoveryStepTest:84-86`, `StalledScopeDetectorTest:76-83`, `RecoveryStepFixtures:68`, and the static `@MethodSource` `UnservedWantTest.refusals()` (`:35`).
   - Instance-field initialisers in `MachineConfigurationStoreTest` (`:58-60`) and about a dozen more classes.
   - The outline proves with one scratch test whether a `@GeneratorSeed` replay reproduces an instance-field draw, and moves only what it does not.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
4. **Pairs that must differ (d), round trips (e) and the readiness comment (f).**
   - `FileSecretStoreTest:66-70`, `SecretServiceStoreTest:68-71`, `KeychainSecretStoreTest:69-72`, `SecretServiceIT:70-73` draw first and second secret and the global and project value independently. Draw them with `Generators.uniqueValues` or as `a` and `a + suffix`.
   - `JGitSourceRepositoryTest:105-106` (`text()` is `letterStrings(3, 30)`; the old tests carried punctuation in a CSV, an overlay and a JSON item) keeps its punctuation in the round trip. `JobLogTest` and the audit torn lines are decided case by case.
   - `EndpointHarnessReadinessTest` opens its own `ServerSocket` (`:50`) and has `@EnableTestLogger` already: add only the one-line comment that names the closed-port race and the ADR 017 decision.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
5. **The diagnostics PLAN-V2-126's Deliverable 1 left unrestored (folded 2026-11-25 from `landings/PLAN-V2-126.md`, layer 2 L2-1 to L2-3; the rule is the one of that deliverable: every assertion whose failure message was the result's `detail()`, `reason()`, `diagnostic()` or measured time says it again).** Read at `1ae69d3f`, parents at `24371eae`: `GitIdentityTest.java:79` (a site the spec of 126 NAMED; `"the fixture commit is recorded"` was `result.detail()`) and `:227` (`"a log of an ambiguous name is refused"` was `result.detail()`), `JGitCacheRepositoryTest.java` (at least six parent sites carried `committed.detail()` and kin, the lane's narrative said none; e.g. `:117` `"the source repository's commit is made"` is static now), `EndpointHarnessTest.java:214` (`"the enrolled program ANSWERED"` was `outcome.detail()`), `WeightsReaderTest.java:70` (`"the listing pointer is read"` was `reading.detail()`) and `VerifyNotResolvePipelineTest` `unreadable()` (had `refusal.detail()`). Restore them as `assertAll(heading, ...)` headings that carry the value, as the 32 sites of #612 do, and count the sites by the command the PR body states (the rule, not the list; the list above is the evidence of the landing). A scratch change of each restored site fails it with the diagnostic in the message (log ids). **Order:** `mutation-after`.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester

**Done when:**
- Deliverables 1 to 5; the restored diagnostics of Deliverable 5 are counted by the PR body's command and each fails with its diagnostic under a scratch change.
- The per-class table, before and after, is in the PR body with its counting command.
- The pairs of deliverable 4 cannot be equal by construction. A scratch mutation (`replace()` keeps the first value; project-first resolution reversed) fails the paired test (log ids).
- Each statement that stays is true for every datum it covers.
- The suites keep their test counts per class as at 3ba79c4f.
- Build: `verify -Ppre-commit`, `verify` of `pm-runtime` and the provider modules touched, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am`. Use per-module runs with log ids if a full run exceeds the host's bound.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).

## Standing Constraints

- Substance, not form: a conversion is accepted when a scratch mutation of the thing the test names fails it.
- No behaviour change, no production change. A class may gain tests; none is removed.
- `pm-runtime:tests` is consumed by the assembly (R16): `TestBases`, `FakeBus`, `FakeSecretService` and `RuntimeGenerators` signatures are not changed.
- Assertion messages belong to PLAN-V2-103 to 105; a line touched for another reason carries a message of 20 to 60 characters.
- A literal stays only where it is the contract, and the test says so in its display name or a one-line comment.
- Read every cited line at the head first and drop what a merge settled (name the commit). No more than 100 files per PR. Pre-1.0: no shim.
- OS-specific behaviour (Secret Service, Keychain) is tested with `@EnabledOnOs`.
- Sonar dismissal only on the operator's selection, through `sonar_rest transition`.

## Claim Labels

- OBSERVED: "Every datum of this test is a contract" at `WorkerDrainParticipantTest:43` and `WorkerSupervisorLaunchTest:42`. 26 files of `pm-runtime/src/test` carry "every datum" at 3ba79c4f.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: WorkerDrainParticipantTest:43 and WorkerSupervisorLaunchTest:42 'Every datum of this test is a contract'; git grep -il 'every datum' pm-runtime/src/test = 26 files (the capitalised 'Every datum', case-insensitive).
- OBSERVED: `SupervisorTestFixture` draws the ids from `RuntimeGenerators` (`:99-106`).
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: SupervisorTestFixture draws ids from RuntimeGenerators (projectIds, planIds, sha256Digests, jobIds) but at :103-110 at the head, not :99-106 (file changed after 3ba79c4f); substance holds.
- OBSERVED: the four killed-runtime ITs and `ProbeBudgetTest` carry the controller annotation and no `Generators.` call. `RemotePolicyTest`, `UnservedWantTest`, `GitHubOperationTest`, `SonarProviderTest` have no `Generators.` call.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: BoundedVerificationKilledRuntimeIT, ProjectRecoveryKilledRuntimeIT, RelayVersionCheckKilledRuntimeIT, RuntimeDrainKilledRuntimeIT, ProbeBudgetTest, RemotePolicyTest, UnservedWantTest, GitHubOperationTest, SonarProviderTest each have @EnableGeneratorController once and zero 'Generators.' references.
- OBSERVED: `FileSecretStoreTest:66-70`, `SecretServiceStoreTest:68-71`, `KeychainSecretStoreTest:69-72`, `SecretServiceIT:70-73` draw with independent `letterStrings(1, 20)`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: FileSecretStoreTest:66-70, SecretServiceStoreTest:68-71, KeychainSecretStoreTest:69-72, SecretServiceIT:70-73 each draw the secrets/values with independent Generators.letterStrings(1, 20).next().
- OBSERVED: `EndpointHarnessReadinessTest` has `@EnableTestLogger` (`:32`) and a `ServerSocket` (`:50`); only the comment is open.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: EndpointHarnessReadinessTest.java:32 @EnableTestLogger, :50 new ServerSocket(0, 1, ...); no comment naming the closed-port race or ADR 017 present (grep), so only the comment is open.
- OBSERVED: `StalledScopeDetectorTest:76-83` static finals, `JobRecoveryStepTest:84-86`, `RecoveryStepFixtures:68`, `MachineConfigurationStoreTest:58-60`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: StalledScopeDetectorTest:76-83 static finals (LAST_PROGRESS :76-77, PROJECT/PLAN_ID/EPIC_ID :78-80 drawn via name(); :83 STATE is a literal), JobRecoveryStepTest:84-86 BOOT/CREATED_AT/NOW, RecoveryStepFixtures:68 COPY_RULE, MachineConfigurationStoreTest:58-60 instance-field draws.
- OBSERVED (drift): `ActivityEventMapperTest` generates `toolName` (`:74`) while its Javadoc calls the tool names the wire contract (`:37`).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: ActivityEventMapperTest Javadoc :36-37 'the tool names and the measured usage numbers are the wire contract' while :74 toolName = Generators.letterStrings(3, 12).next() and :76 argumentValue are drawn.
- HYPOTHESIS: instance-field draws before the seed are not reproduced by a `@GeneratorSeed` replay (mechanism read by an earlier landing, impact never shown). Confirm with one scratch test at the outline.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Needs a scratch test run (does a @GeneratorSeed replay reproduce instance-field draws); the library behaviour is outside the checkout and no run is allowed.
- HYPOTHESIS: `SupervisorEnvironmentTest:36` ("the task id and both digests are generated") and the other `EnrolmentFileCheckTest` literals are as the landing of PLAN-V2-91 says. Confirm at those files.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: SupervisorEnvironmentTest.java:36 says 'The task id and both digests are generated' (draw() :58-59 draws one task id and one digest reused for three members, so 'both' is loose); EnrolmentFileCheckTest:46 'Every datum ... is a contract' over literals someone-else (:133,:223) and link.json (:115,:211). PLAN-V2-91 landing text not opened (.plan).
- Verify-first clause: D1 (#612) changed many test files; re-read every site. A refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: D1 1ae69d3f changed 23 files; pm-runtime test files were also changed after 3ba79c4f (SupervisorTestFixture, ProbeBudgetTest, HarnessEnrolmentStoreTest and others), and the cited sites were re-read: only SupervisorTestFixture's lines moved (claim 1).

## Expected Surface

About 40 existing files; about 50 if the scan finds more statement classes.

- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerSupervisorLaunchTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerDrainParticipantTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorTestFixture.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/ActivityEventMapperTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerGenerationContractTest.java`
- OBSERVED (folded 2026-11-25, Deliverable 5, read at `1ae69d3f`): `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/GitIdentityTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitCacheRepositoryTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointHarnessTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/WeightsReaderTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/VerifyNotResolvePipelineTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/enrolment/EnrolmentFileCheckTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/dbus/DbusConnectionTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/FileSecretStoreTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/SecretServiceStoreTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/KeychainSecretStoreTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/SecretServiceIT.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/start/BoundedVerificationKilledRuntimeIT.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/ProjectRecoveryKilledRuntimeIT.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/RelayVersionCheckKilledRuntimeIT.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/RuntimeDrainKilledRuntimeIT.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/JobRecoveryStepTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/StalledScopeDetectorTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/RecoveryStepFixtures.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/ProbeBudgetTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/config/MachineConfigurationStoreTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointHarnessReadinessTest.java`
- OBSERVED: `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/RemotePolicyTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/UnservedWantTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitSourceRepositoryTest.java`, `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/GitHubOperationTest.java`, `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/GitHubCiProviderTest.java`, `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/GitLabCiProviderTest.java`, `pm-providers/pm-provider-sonar/src/test/java/de/planmarshall/provider/sonar/SonarProviderTest.java`
- HYPOTHESIS: the remaining statement classes of the 26-file scan and the instance-field classes (about a dozen), and `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobLogTest.java` (round trips), all under `pm-runtime/src/test/java/de/planmarshall/runtime/`. Sweep with the table's counting command at the outline.

## Dependencies and Sequencing

- Depends on: none hard. #612 (PLAN-V2-126 D1) is merged.
- Overlaps with: this series' S7 (the same helper copies in some files), S8 (`GitHubCiProviderTest`, `GitLabCiProviderTest`, the endpoint tests) and S10. Serialise S7, S8, then this plan where one class is edited. The gate decides.
- Adjacent to: the fakes (PLAN-V2-136), PLAN-V2-103 to 105 (messages), PLAN-V2-123 (the runtime parity tests; not touched), PLAN-V2-124 (the lock tests; not touched).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-205-test-statements-draws-and-pairs.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Re-Ground Record of 2026-11-27 (cleanup, head 115d5987)

Binding, like every earlier record: where a claim above and this record disagree, the record is what is true at the head; the claim bullets are not rewritten. Read by read-only leaves (R21) from the head `origin/main` 115d5987; each contradicted claim is absorbed here (`rescoped: yes`).

- Claim 1: SupervisorTestFixture draws ids from RuntimeGenerators (projectIds, planIds, sha256Digests, jobIds) but at :103-110 at the head, not :99-106 (file changed after 3ba79c4f); substance holds.
