# PLAN-V2-137: Finish PLAN-V2-108: the 17 positional record calls, the WorkerGeneration builder test, and a message on the 82 assertThrows

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: the three-layer analysis of PLAN-V2-108 (`landings/PLAN-V2-108.md`: layer 2 "the lane's two-calls claim refuted", the `WorkerGenerationRecordsTest` finding and layer 3 "82 messageless `assertThrows`"), the merged pull request #359 (`af3a8c485637288ae20e92fd22aa9ab4914c42fe`), the lane message `inbox/archive/rb-v2-108-builders-for-records-workflow-runtime-providers/rb-v2-108-builders-for-records-workflow-runtime-providers-001.md`, and the operator's word of 2026-11-10 to stage the residue plan (runbook lane, residue)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-137-records-builders-residue.md` and is queued as one row file, `queue/PLAN-V2-137.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (the current one; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R07, R08, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05: no specification section changes and no requirement is implemented, so `doc/Traceability.adoc` is not edited; R09: no confinement; R10: no specification part is edited; R11: no ADR is written; R12: no message is added; R13: no stored format changes; R14, R15, R16: no protocol, DSL or runtime behaviour changes, the builders call the canonical constructors.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none changes (no package is added, no behaviour is described differently); say so in the pull request body. ADR: none (no design decision; the plan completes a decision PLAN-V2-108 already took).
- R12 — none (no message).

**Read the skills first (R20).** `java-lombok`, `java-core`, `java-null-safety`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-108 (#359) gave every record of four or more components in `pm-workflow`, `pm-runtime` and the providers a `@Builder` and moved the positional call sites. The analysis of its landing found three things left undone: (1) the landing reported two positional four-argument calls remaining while 17 sites in seven production files remain outside the codecs and the records' own factories, so the spec's Done-when ("no call site outside codecs and factories passes four or more positional arguments to a record constructor") is not met; (2) `WorkerGenerationRecordsTest` compares `toString()`, which prints three of the record's eighteen components, so the test proves less than its Javadoc says; (3) the 105 test classes PLAN-V2-108 added hold 82 `assertThrows` calls without a message, against the rule that every assertion carries one of 20 to 60 characters. This plan closes the three.

The change is small and mechanical: seven production files (call sites only), one test strengthened and the 82 messages. No behaviour changes, no `@Test`, `@ParameterizedTest`, `@RepeatedTest` or `@Nested` line is added or removed except where Deliverable 2 states otherwise, no POM, plugin or dependency changes.

## Deliverables

Three small deliverables, one commit each. The test order is `none` for Deliverable 1 and 3 (a refactor and a message: the guard is the unchanged tests) and `test-first` for Deliverable 2 (the strengthened assertion must fail on a component the builder does not wire: show it by a temporary mutation of one `.component(...)` argument and a revert, recorded in the progress note).

1. **The 17 positional calls become builder calls (order 1).** Move these calls to the record's builder; each passes four or more positional arguments that are not all same-named variables: `pm-workflow/.../model/ModelCaller.java` (`new ModelQueue.Enqueue(...)` at `:95`, `ModelQueue.Close` at `:116`, `ModelCallResult.Closed` at `:117`), `pm-runtime/.../launch/LaunchComposer.java` (`PromptComposer.PromptRequest` at `:250`), `pm-runtime/.../worker/ActivityRecorder.java` (`WorkerProtocol.OffTrackReport` at `:92`), `pm-workflow/.../engine/WaitSelection.java` (`ModelWait.Progress` at `:60`), `pm-runtime/.../endpoint/JudgmentClient.java` (`EndpointClient.Response` at `:160`, `:163`, `:166`, `:195`, `:200`), `pm-runtime/.../endpoint/EndpointEnrolment.java` (`Refreshed` at `:70`, `:74`, `:96`, `:103`), `pm-runtime/.../credentials/ProviderVerification.java` (`Verification` at `:218`, `:242`). The line numbers are those of `af3a8c48`; re-run the scan at the branch base and convert what it finds, not what this list says: a call that the head already converted is dropped, a call the scan finds beyond the list is converted too and named in the landing. The exemption stays: a call whose arguments are all variables named like the components (`CompiledInvariants:480`, `OffTrackDetector:90`), the codecs and parsers, and the records' own factories and instance methods stay positional. **Done when:** the leaf's scan (a parse of every `src/main` record of four or more components, comparing positional constructor calls with four or more arguments, outside codecs, parsers and the record's own methods and outside the same-named exemption) returns no site; the lane describes the scan it ran in the progress note and the pull request body lists the converted calls per record; `test -pl pm-workflow,pm-runtime -am` is green with the method count of the branch base.
2. **`WorkerGenerationRecordsTest` proves all eighteen components (order 2, test first).** Replace the `toString()` comparison (`WorkerGenerationRecordsTest.java:85`; `WorkerGeneration.toString()` prints `generationId`, `role` and `lifecycle` only, `WorkerGeneration.java:110`, and `equals` compares the generation id alone, `:100`) by a comparison that reads every record component of the constructed and the built value (the record's accessors through `WorkerGeneration.class.getRecordComponents()`, or eighteen explicit accessor assertions; the outline chooses and says which) and fails when one component differs; correct the class Javadoc (`:30-34`) so that it says what the test proves. The method count of the class stays; the assertion keeps a message of 20 to 60 characters. **Done when:** the test fails when the builder call omits any one component (shown for one component by the temporary mutation above) and passes unchanged otherwise; the Javadoc names the mechanism the test uses.
3. **A message on the 82 `assertThrows` (order 3).** Give every `assertThrows` of the 76 test classes PLAN-V2-108 added (Expected Surface lists them; 82 calls at `af3a8c48`) a message argument of 20 to 60 characters that says what is refused, as the other 209 assertions of those classes do and as `OrderedWriteTest.java:329` does. Re-run the count at the branch base: `git grep -c 'assertThrows(' -- <the 76 files>`; every occurrence ends with a message when the plan is done. Where a class holds a literal that is a contract (the component name a refusal names), the one-line note R20 asks for stays or is added. **Done when:** a scan of the 105 added classes finds no `assertThrows` without a message argument and no message outside 20 to 60 characters; `test -pl pm-workflow,pm-runtime,pm-providers/pm-provider-git,pm-providers/pm-provider-http -am` is green with the method count of the branch base; no `*RecordsTest` class changes in any other line.

**Done when (plan):** Deliverables 1 to 3; no `pom.xml` in `git diff --stat`; the method count of each module equals the base; every converted test line carries its message. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed, in their own commit where they touch files outside the plan's scope), then per-module `test -pl <module> -am` runs of `pm-workflow`, `pm-runtime` and the two providers (a full verify exceeds the host's budget: per-module runs with `--timeout`) and `verify -Pcoverage`. **Links:** the R17 check with the absolute paths, after `git fetch` of the documentation checkout, reports `BROKEN LINKS 0` for the files this plan touches (none are documentation, so this is a confirmation run).

## Standing Constraints

- **No behaviour change.** A builder call passes exactly the arguments, in the component order, of the positional call it replaces; no component is dropped, defaulted or reordered (Lombok builders default an unset component to `null`, `0` or `false` silently, so every converted chain names every component). The record, its canonical constructor and its builder annotation are not edited.
- **Not here:** the 108 spec's Done-when and counts (the plan shipped; the landing report records them), the `pm-core` records without `@Builder` (108 records, out of PLAN-V2-108's scope and owned by PLAN-V2-100's decision, not by this plan), `PlanFacts` (keeps its hand-written builder), the low-severity test-data notes of the 108 landing (`CompositionRequestRecordsTest`, `PlanInstanceRecordsTest`, `InstancePrunerRecordsTest`, `DiffSummaryRecordsTest`: empty collections for several same-typed components), and the assertion messages of untouched assertions (PLAN-V2-101 to 105).
- **No new hand-written double, no dependency or plugin is added.**
- **Where the project analyses its pull requests with Sonar** (read it over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` and transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker. The merge gate reads the open-issue count at the head (`0` confirmed, or every remaining issue dismissed); an `undecidable` read blocks.

## Claim Labels

All lines read at `af3a8c485637288ae20e92fd22aa9ab4914c42fe` (the merge commit of #359) through `git show`; the 17-site count is the read-only leaf's parse and the orchestrator re-read ten of the sites.

- OBSERVED: `ModelCaller.java:95` passes ten positional arguments to `ModelQueue.Enqueue`, `:116` five to `ModelQueue.Close`, `:117` four to `ModelCallResult.Closed`; `LaunchComposer.java:250` eleven to `PromptComposer.PromptRequest`; `ActivityRecorder.java:92` eight to `WorkerProtocol.OffTrackReport`; `WaitSelection.java:60` four to `ModelWait.Progress`; `JudgmentClient.java:160`, `:163`, `:166` four each to `EndpointClient.Response`; `EndpointEnrolment.java:70`, `:74` four each to `Refreshed`; `ProviderVerification.java:218`, `:242` four each to `Verification` — read in `src/main` of `pm-workflow` and `pm-runtime`
- HYPOTHESIS: `JudgmentClient.java:195` and `:200`, `EndpointEnrolment.java:96` and `:103` pass four or more positional arguments as the leaf's parse reports; the orchestrator did not re-read them — confirm or refute by reading those lines (verify-at-outline)
- OBSERVED: `WorkerGeneration.toString()` prints `generationId`, `role` and `lifecycle` (`WorkerGeneration.java:110`) and `equals` compares `generationId` alone (`:100`); `WorkerGenerationRecordsTest.java:85` compares `toString()` and its Javadoc (`:30-34`) says the comparison proves the other seventeen components
- OBSERVED: the 76 test classes of the Expected Surface hold 82 `assertThrows(` occurrences at `af3a8c48`, none with a message argument (the orchestrator re-counted the 82 occurrences and read `GitOperationsRecordsTest.java:75`; the leaf reports that all 82 lack the message) — confirm each by the scan of Deliverable 3 (verify-at-outline)
- HYPOTHESIS: the leaf's parse (comments and strings stripped, balanced parentheses, nested and generic records included) found every positional site; a site the parse cannot see (a call through a method reference or a reflective construction) may exist — the scan of Deliverable 1 at the branch base settles it (verify-at-outline)
- Verify-first clause: before editing, list the open pull requests for one that touches the files of the Expected Surface (PLAN-V2-101 to 105, 114, 125, 126, 129 and the plans that edit `ModelCaller`, `WaitSelection`, `JudgmentClient` may), re-read the files at the head, re-run the three scans and the counts above, and drop what has changed; a refutation loops back to re-scope.

## Expected Surface

Code and tests (R19: per file; every file OBSERVED at `af3a8c48`):

Production call sites (D1, modified):
- `pm-workflow/src/main/java/de/planmarshall/workflow/model/ModelCaller.java`, `pm-workflow/src/main/java/de/planmarshall/workflow/engine/WaitSelection.java`
- `pm-runtime/src/main/java/de/planmarshall/runtime/launch/LaunchComposer.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/ActivityRecorder.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/JudgmentClient.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/EndpointEnrolment.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/ProviderVerification.java`
- OBSERVED, read not edited: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/CompiledInvariants.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/OffTrackDetector.java` (the same-named calls that stay)

The strengthened test (D2, modified): `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerGenerationRecordsTest.java`

The 76 test classes whose `assertThrows` get a message (D3, modified; all `*RecordsTest.java` unless named):
- OBSERVED, modified (D3): `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/`: `GitOperationsRecordsTest.java`
- OBSERVED, modified (D3): `pm-providers/pm-provider-http/src/test/java/de/planmarshall/provider/http/`: `ResponseBuilderTest.java`, `ResultRecordsTest.java`
- OBSERVED, modified (D3): `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/`: `CredentialStoreAspectRecordsTest.java`, `CredentialStoreCheckRecordsTest.java`, `CredentialStoreMoveRecordRecordsTest.java`, `ProviderVerificationRecordsTest.java`
- OBSERVED, modified (D3): `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/dbus/`: `DbusMessageRecordsTest.java`
- OBSERVED, modified (D3): `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/`: `EndpointClientRecordsTest.java`, `EndpointEnrolmentRecordsTest.java`, `EndpointOutcomeRecordsTest.java`, `EndpointRequestRecordsTest.java`, `JudgmentVerificationRecordsTest.java`
- OBSERVED, modified (D3): `pm-runtime/src/test/java/de/planmarshall/runtime/enrolment/`: `EnrolmentsRecordsTest.java`
- OBSERVED, modified (D3): `pm-runtime/src/test/java/de/planmarshall/runtime/job/`: `JobConfinementComposerRecordsTest.java`, `JobEvidenceRecordsTest.java`, `JobRequestRecordsTest.java`, `LaunchReportRecordsTest.java`, `PmExecLauncherRecordsTest.java`, `ProbeCandidateRecordsTest.java`, `VerifiedJobRecordsTest.java`, `WorkerStartRecordsTest.java`
- OBSERVED, modified (D3): `pm-runtime/src/test/java/de/planmarshall/runtime/launch/`: `ContractEnvelopeRecordsTest.java`, `HarnessEnrolmentRecordsTest.java`, `HeadlessBlockRecordsTest.java`, `LaunchPromptRecordsTest.java`, `LaunchRequestRecordsTest.java`, `ModelSlotSchedulerRecordsTest.java`, `PromptComposerRecordsTest.java`, `WorkerLaunchRecordsTest.java`
- OBSERVED, modified (D3): `pm-runtime/src/test/java/de/planmarshall/runtime/start/`: `ServiceUnitFileRecordsTest.java`, `StalledScopeDetectorRecordsTest.java`
- OBSERVED, modified (D3): `pm-runtime/src/test/java/de/planmarshall/runtime/worker/`: `ActivitySignalRecordsTest.java`, `LossSignalRecordsTest.java`, `SupervisorEnvironmentRecordsTest.java`, `WorkerStartRequestRecordsTest.java` (and `WorkerGenerationRecordsTest.java`, listed above)
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/ast/`: `StateBehaviorRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/consult/`: `ConsultationFactRecordsTest.java`, `ConsultationRequestRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/`: `InstanceAnnotationsRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/engine/`: `ModelWaitRecordsTest.java`, `RestingWaitRecordsTest.java`, `WorkflowEngineRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/finalize/`: `CloneCandidateRecordsTest.java`, `FinalizeStepDescriptorRecordsTest.java`, `SimplifyStepRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/hypermedia/`: `AnswerFactRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/model/`: `ModelJobRecordsTest.java`, `ModelQueueRecordsTest.java`, `RoleUseRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/plan/`: `BoundArgumentRecordsTest.java`, `ComposedUnitRecordsTest.java`, `CompositionInputsRecordsTest.java`, `CompositionRecordRecordsTest.java`, `CompositionRequestRecordsTest.java`, `CompositionRuleRecordsTest.java`, `DiffSummaryRecordsTest.java`, `PlanInstanceHeaderRecordsTest.java`, `PlanInstanceRecordsTest.java`, `PlanProcessStatesRecordsTest.java`, `PrunedElementRecordsTest.java`, `UnitReferenceRecordsTest.java`, `WaiverRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/question/`: `QuestionContinuationRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/setup/`: `MachineOnboardingRecordsTest.java`, `SetupGateRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/task/`: `OffTrackSignalRecordsTest.java`, `SecurityAuditRecordsTest.java`, `WaitRequestRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/unit/`: `UnitFindingRecordsTest.java`, `VerificationContextRecordsTest.java`
- OBSERVED, modified (D3): `pm-workflow/src/test/java/de/planmarshall/workflow/verify/`: `DeadStateRecordsTest.java`, `DeclarationBoundsRecordsTest.java`, `InvariantViolationRecordsTest.java`, `ViolationRecordsTest.java`

Not surface (R19): `doc/`, `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, every `pom.xml`, every record declaration, `pm-core`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-108 (shipped, #359, `af3a8c48`: it added the builders and the tests this plan finishes) and PLAN-V2-83, 97, 98, 99 (shipped).
- Runs before PLAN-V2-101 to 105 (the assertion-message plans: 101 stages a scanner test that 102 to 105 extend, and it would fail on the 82 messageless `assertThrows` of the 108 test classes if this plan has not given them messages; the messages of Deliverable 3 are therefore this plan's, and 101 to 105 skip those 76 classes). The orchestrator adds this plan to the `Depends on` of PLAN-V2-101 when it is emitted.
- Overlaps at file level are decided by the disjointness gate at emit, from the Expected Surface above: the plans that touch the seven production files (the gate reads them) and the plans that touch the 76 test files (PLAN-V2-125 and 126 name the runtime and provider test directories; PLAN-V2-129 and 114 name some of them). No order is forced here beyond the one above.
- Not in conflict by declaration: running PLAN-V2-100 (`pm-core` builders: it touches none of these files), running PLAN-V2-127 (`BuildOutputParser` and its test) and PLAN-V2-53 (a new `OutboundCallsArchTest` and its fixtures under `pm-runtime/src/test/.../guard/`).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-137-records-builders-residue.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
