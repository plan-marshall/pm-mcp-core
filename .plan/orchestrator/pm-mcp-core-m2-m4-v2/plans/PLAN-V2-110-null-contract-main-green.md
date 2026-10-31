# PLAN-V2-110: Turn the SonarCloud gate of main green again: make the null contract of the 99 files that @NullMarked exposed true

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: the red `main` of 2026-10-31 after PLAN-V2-84 (operator decision: "Fix the sites now")
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-110-null-contract-main-green.md` and is queued as one row file, `queue/PLAN-V2-110.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R07, R08, R11, R13, R14, R15, R16, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R09, R10: no specification change; R12: no message is added. R13 because the decoders in `core.store` and `core.queue` are touched: a format stays at version 1 and no reader of an older form appears. R14 because `core.store` is; R15 because `pm-workflow` is; R16 because `pm-runtime` is, its item (4) for a site that must keep a flagged shape.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the guides of the packages whose public signature or contract changes, only where the change alters what the guide says (`pm-core/doc/developer/core-store.adoc`, `core-queue.adoc`, `core-role.adoc`, `core-profile.adoc`, and those of `pm-workflow`, `pm-runtime`, the providers as they apply); ADR: **one** repository ADR in `doc/adr/` (the decision binds the whole repository; the next free number at `origin/main`, R11, after 011) that records the three fix classes below and the rejected alternative of ignoring the rules; layout: workstream WS-11.
- R12 — none (no message).

**Read the skills first (R20).** `java-null-safety`, `java-core`, `javadoc` and `junit-core` of `/home/oliver/git/plan-marshall/marketplace/bundles/pm-dev-java/skills/` (and `cui-testing` where a test changes); say in the first progress note which you read.

## Objective

PLAN-V2-84 marked every production package `@NullMarked`. Since its first merge (`9ef53d41`, 2026-10-31) the `Maven Build` of `main` fails in `sonar-build` alone, and the `0.1.0-SNAPSHOT` has not been deployed. The analysis of `main` reads **292 open issues of three rules in 99 files**: `java:S2583` x99 and `java:S2637` x44 (type BUG — 143 bugs, the reason the gate fails `new_reliability_rating`, rated 3 against 1) and `java:S2589` x149 (code smell, the same cause). `@NullMarked` tells Sonar that an unannotated value is never null, so the repository's defensive `== null` tests read as dead conditions and a `return null` or a missing initialisation reads as a violated contract. This plan makes each of the 292 sites true — a dead test is deleted, a value that really can be null is annotated `@Nullable` at its source, an absence that is returned becomes `Optional` — so `main` turns green **without** ignoring the rules. The operator chose this over the ignore of ADR 011's kind and over waiting for PLAN-V2-97 to 99 (2026-10-31).

## Deliverables

The plan is one plan with three deliverables and **at least three pull requests, one per deliverable, none of more than 100 files** (CodeRabbit refuses more; PLAN-V2-84 had to be split for it). Open the first pull request only after the whole fix of its deliverable is on its branch.

1. **`pm-core`, `core.store` and `core.queue`** (45 files, 158 issues: S2589 x85, S2583 x59, S2637 x14). The decoders and records of the durable stores; R13 and R14 bind.
2. **`pm-core`, the other packages** (30 files, 98 issues: S2589 x51, S2583 x34, S2637 x13): `core.profile` (`ProfileValidator` alone has 34), `core.role`, `core.spi`, `core.config`, `core.service`, `core.analysis`, `core.findings`.
3. **`pm-workflow`, `pm-runtime` and the providers** (24 files, 36 issues: S2637 x17, S2589 x13, S2583 x6): 7 files of `pm-workflow`, 14 of `pm-runtime`, and one each of `pm-provider-git`, `-gitlab` and `-http`.

**The fix, site by site.** For every issue decide from the code, not from the message, which class it is, and write the class in the commit message or the pull request body per file:

- **(a) The test is dead by construction.** The value is non-null because the code before it made it so (a canonical constructor or `Objects.requireNonNull` already refused null, the value came from a method that cannot return it, it is a record component that the constructor checked). **Delete the dead test and the branch it guarded.**
- **(b) The null is real, but the source is not annotated.** The value comes from a JSON decode, a `Map.get`, an `orElse(null)`, a parameter whose contract accepts null, or a field that is null in some state. **Annotate the source `@Nullable`** (JSpecify, `org.jspecify.annotations.Nullable`; a type-use annotation after the package or enclosing type, `byte @Nullable []` for an array, a `@Nullable` on a record component) so the test is live; keep the test. Absence that a method **returns** is not annotated — see (c).
- **(c) A method returns null, or a field is left uninitialised (S2637).** Absence that a method returns is `Optional<T>` (CLAUDE.md, R20): change the return type, the `return null` or `: null` and every caller; do not annotate a `@Nullable` return. A field flagged "not initialized in this constructor" is initialised, or is `@Nullable` where null is a real state of the object. A site that must keep the flagged shape because an external type forces it (an override of a JDK or library method with a null contract, a `CompletableFuture<Void>` completion) carries an inline `@SuppressWarnings("java:S2637")` with the reason beside it, never `NOSONAR`; list each in the pull request body.

**Behaviour does not change** except where (c) changes a signature. A public method that today refuses null with an exception keeps refusing it with the same exception type and message: where the refusal test is dead for Sonar but real for a caller outside `@NullMarked` code, the parameter is annotated `@Nullable` (b) and the test stays, or the refusal is expressed as `Objects.requireNonNull(x, "x")` only where the existing tests already pin a `NullPointerException`. A deleted test (a) is covered by the existing tests; add a test where a (b) annotation or a (c) change adds an observable state (an `isEmpty()` assertion of the new `Optional`, the null case of a newly `@Nullable` parameter). No format version moves (R13); no ignore of a rule, no `NOSONAR`, no `sonar.issue.ignore` property is added.

4. **The ADR** (`doc/adr/NNN-…`, the next free number after 011 at `origin/main`): the three classes, the evidence each needs, the rule that absence is returned as `Optional`, and the alternatives considered — ignoring the rules as ADR 011 does for `java:S4449` (rejected by the operator on 2026-10-31: it would hide the dead tests of every later class), and waiting for PLAN-V2-97 to 99.

**Done when:** for each pull request, the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no `java:S2583`, `java:S2589` or `java:S2637` and no new issue of another rule, and the report states `sonar_open_issues` and the rules left open (`unknown` for a failed read, never `0`); the three pull requests are merged; the ADR is written; `verify -Ppre-commit` (its rewrites reviewed and committed), `verify`, `verify -Pcoverage` and both `verify -Pintegration-tests` runs are green on each pull request (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported); the links check (R17) is run and reports no count above the baseline of the Watch in `epic.md`. **After the last merge** (the orchestrator's check at `analyze`, not the lane's): the open issues of `main` for the three rules read **0**, the gate of `branch=main` reads `OK`, the `Maven Build` of that push is green and `deploy-snapshot / deploy` ran.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **No ignore, no `NOSONAR`, no property.** The root `pom.xml` is not edited. An inline `@SuppressWarnings("java:S…")` with the reason beside it is allowed only for the (c) case of an external type and is listed in the pull request body.
- **The lane re-reads Sonar before it edits.** The 292 issues are the analysis of `main` at `0b2a92ad`; PLAN-V2-107 or PLAN-V2-94 may have landed since and changed lines. The plan's population is the list `issues/search?componentKeys=plan-marshall_pm-mcp-core&branch=main&resolved=false&rules=java:S2583,java:S2589,java:S2637` at the head of `main` when it starts; a file that dropped out is not touched, a file that entered is added to its deliverable and named in the landing.
- **A `@Nullable` that spreads is a finding, not a licence.** Annotating a source (b) can make a caller's unchecked dereference an issue of another rule; follow the annotation to its callers, check each, and say in the pull request body how many callers were checked. Do not widen the plan to unrelated files: a file outside the list that must change for a (b) or (c) change is named in the landing.
- **Public signatures:** the pull request body lists every public or package-visible signature that changes (the (c) `Optional` returns above all) under "Owed outside this repository", after searching the sibling `plan-marshall-mcp` for callers, as PLAN-V2-84 did.
- **Where the Sonar open-issue read says `sonar: not_configured`** (the lane's resolve reports it for this project), the lane reads the public search of the pull request (R18) and a dismissal is not possible; a site it cannot fix is a blocker reported through the inbox message, not a silent pass.

## Claim Labels

- OBSERVED: the `Maven Build` of `main` is green at `03f182c9` and `f8abbfc5` and fails at `9ef53d41`, `45f16f3c`, `b03cefcd` and `37fb864f`, each time in `sonar-build` alone; `deploy-snapshot / deploy` ran at the first two and is skipped at the others
- OBSERVED: `branch=main` gate at the analysis of `0b2a92ad` (2026-10-31 16:36 UTC) reads `ERROR`, one failing condition, `new_reliability_rating` actual 3; the facets of `issues/search?branch=main&resolved=false` read 308 open issues, 143 BUG and 165 CODE_SMELL, with `java:S2589` 149, `java:S2583` 99, `java:S2637` 44 and 16 issues of other rules (the 13 older ones and three more)
- OBSERVED: the 292 issues of the three rules lie in 99 files, none in a test file: `pm-core` 75 files (256 issues), `pm-runtime` 14 (21), `pm-workflow` 7 (12), `pm-provider-git`, `-gitlab`, `-http` one each; the largest is `core/profile/ProfileValidator.java` with 34
- OBSERVED: the first failing push (`9ef53d41`) is the merge of D1 of PLAN-V2-84, which changed `package-info.java` files only, so the cause is the annotation and not a change of code
- HYPOTHESIS: most S2583/S2589 sites are class (a) or (b) and few need a behaviour decision — confirm/refute at the first file of each deliverable by reading the caller chain (verify-at-outline); if class (b) is the majority the `@Nullable` spread of Standing Constraints is the main cost
- HYPOTHESIS: deleting a dead test or annotating a source changes no behaviour a test observes — confirm/refute by the unchanged test suite; a test that fails after a change is a finding about the class chosen, not a licence to change the test
- Verify-first clause: before editing, list the open pull requests for one that touches a file below, and re-read the issue list and the gate of `main` (PLAN-V2-107 and PLAN-V2-94 run beside the start of this plan and edit test jars and production classes); a refutation loops back to re-scope

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/ActivityEntry.java` — S2583 x1, S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/ActivityEntryCodec.java` — S2583 x7
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/ActivityEventMapping.java` — S2637 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/ActivityJournal.java` — S2589 x5
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/NavigationFigures.java` — S2589 x6
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/QueueTaskId.java` — S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/QueueTaskRecord.java` — S2583 x1, S2589 x12
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/QueueTaskRecordCodec.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/SecretRedactor.java` — S2589 x6
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/AuditCodec.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/AuditIntentFile.java` — S2637 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/CapabilitySpec.java` — S2583 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ChangeLedgerCodec.java` — S2583 x2, S2637 x3
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/EnrolmentCodec.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/EnrolmentStore.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/EntryId.java` — S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/IntentAcceptance.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/IntentRecord.java` — S2583 x2, S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/JobId.java` — S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/JobRecord.java` — S2583 x2, S2589 x14
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/JobRecordCodec.java` — S2583 x3, S2589 x1, S2637 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/JobRecordStore.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/JournalScope.java` — S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/MetricsCodec.java` — S2583 x3, S2637 x3
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/MetricsRow.java` — S2583 x2, S2589 x11
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ModelSlotCodec.java` — S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ModelSlotStore.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/OutlineCodec.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/OutlineRenderer.java` — S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryCodec.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/PendingEntryStore.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ProjectEnrolment.java` — S2583 x1, S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/QualificationSettingsCodec.java` — S2583 x4, S2589 x1, S2637 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/QuestionLog.java` — S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/QuestionRecord.java` — S2583 x2, S2589 x9
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/QuestionRecordCodec.java` — S2583 x2, S2589 x3
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/StrictJson.java` — S2637 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/UsageAggregate.java` — S2583 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/UsagePointers.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WaiverRecord.java` — S2583 x1, S2589 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WaiverRecordCodec.java` — S2583 x2, S2589 x2, S2637 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WorkerBudget.java` — S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WorkerState.java` — S2583 x2, S2589 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WorkerStateCodec.java` — S2583 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatusCodec.java` — S2583 x5
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/analysis/AnalysisResult.java` — S2589 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/analysis/AnalysisResultCodec.java` — S2583 x1, S2637 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/config/ConsentCodec.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/config/DefaultAssignment.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/config/RoleAssignmentValue.java` — S2583 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/config/RoleAssignmentsCodec.java` — S2583 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/findings/Finding.java` — S2589 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/findings/FindingSourceKey.java` — S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/EndpointProfile.java` — S2589 x1, S2637 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` — S2583 x2, S2589 x30, S2637 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/VerificationRecord.java` — S2583 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/EffectiveRole.java` — S2637 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/HarnessProfile.java` — S2637 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/RoleArtifactValidator.java` — S2583 x2, S2589 x1, S2637 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/RoleRefusal.java` — S2583 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/RoleValidation.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/CorpusImport.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/CorpusJson.java` — S2637 x3
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/ImportedItem.java` — S2589 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/ImportedItemCodec.java` — S2583 x1, S2589 x4
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/ItemGenerator.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/JsonlRowReader.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/SourceOverlayCodec.java` — S2583 x4
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/JudgeCheckCodec.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/QualificationRecordCodec.java` — S2583 x1, S2637 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/Scoring.java` — S2583 x1
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/TokenTotal.java` — S2583 x2, S2589 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/service/AuditEvent.java` — S2589 x2
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/spi/PrimitiveRegistry.java` — S2583 x4, S2589 x4
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/spi/validation/SelfReviewSubmission.java` — S2583 x1, S2589 x1
- OBSERVED: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/RefusingFS.java` — S2637 x1
- OBSERVED: `pm-providers/pm-provider-gitlab/src/main/java/de/planmarshall/provider/gitlab/GlabCommands.java` — S2589 x1
- OBSERVED: `pm-providers/pm-provider-http/src/main/java/de/planmarshall/provider/http/Json.java` — S2637 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/audit/AuditVerification.java` — S2637 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/corpus/LfsBatchClient.java` — S2637 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/CredentialAccount.java` — S2589 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/CredentialStoreChoice.java` — S2589 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/dbus/DbusConnection.java` — S2637 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/AnswerSchema.java` — S2589 x2, S2637 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/EndpointVerification.java` — S2589 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/Enrolments.java` — S2589 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java` — S2637 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/WorkerEgressProxy.java` — S2583 x1, S2589 x1, S2637 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/LaunchComposer.java` — S2583 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/PromptComposer.java` — S2589 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/LossDetection.java` — S2637 x1
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/ProfileWorkerEligibility.java` — S2583 x1, S2589 x2, S2637 x1
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/consult/ConsultationService.java` — S2637 x1
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/UnitFrontmatterReader.java` — S2589 x1, S2637 x3
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanFacts.java` — S2637 x1
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanProcessStates.java` — S2583 x1
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskProtocol.java` — S2583 x1, S2637 x2
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/task/WaitRequest.java` — S2589 x1
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/verify/RefineInvariants.java` — S2583 x1

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directories and developer guides of existing packages as additive bookkeeping, and the new repository ADR in `doc/adr/` (R06).

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 and PLAN-V2-84 (both shipped). Nothing else.
- **Runs before the rest of the conformance family that edits the same classes.** PLAN-V2-85 to 93 (test migrations), PLAN-V2-94 (utility classes, generic catches), PLAN-V2-97 to 99 (`Optional` to `@Nullable`) and PLAN-V2-100 and 108 (builders) edit the production classes and tests of these 99 files and the signatures (c) changes; none of them is emitted before this plan has landed. The orchestrator records this as a sequencing decision in `epic.md`; their specs are not edited for it.
- Overlaps with: PLAN-V2-107 (generator artifacts; test JARs and POMs) — none by file. PLAN-V2-94 — by file, see the gate; it is held behind this plan.
- Adjacent to: PLAN-V2-46 (`runtime-endpoint-residues`: `AnswerSchema.java` is in D3 for S2637 and in PLAN-V2-46 for S3776 and S1135), PLAN-V2-81 and PLAN-V2-82 (they edit `core.config` / `PlanInstanceComposer`); the later of two plans that touch the same file rebases.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-110-null-contract-main-green.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
