# PLAN-V2-94: Make the static-only classes @UtilityClass and remove the generic catches of pm-runtime

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-94-utility-classes-and-generic-catches.md` and is queued as one row file, `queue/PLAN-V2-94.json`,
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

Rules that apply to this plan: R02, R06, R08, R11, R14, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12, R13, R15, R16: no behaviour, specification, message or format change; R14 because several catches guard durable multi-step protocols of `pm-runtime`.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none; ADR directory: none (the ADR of PLAN-V2-83 decides); layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

About 63 production classes hold only static methods and declare a private constructor by hand where `java-lombok` and CLAUDE.md want `@UtilityClass` (core 22, workflow 32, runtime 5, git 3, sonar 1 at the head of 2026-11-15; Lombok is in every module the list touches; two of the 63, `SecretRedactor` and `ProfileValidator`, are held out, see the section State at the emit). And 18 catch sites in `pm-runtime` (12 of them stay with a reason, six belong to PLAN-V2-46 and one `throws` to PLAN-V2-67) catch `RuntimeException`, `RuntimeException | Error` or `Throwable`, plus one `throws Throwable`, where CLAUDE.md forbids a generic catch or throw in production code (six carry an OpenRewrite `TODO` marker).

## Deliverables

1. **`@UtilityClass` for the static-only classes.** Find the candidates (no instance member, hand-written `private X()`); the audit lists, among others, `RoleSetLoader`, `RoleAssignmentValidation`, `ClarifiedRequestRenderer`, `OutlineRenderer` (core), `HeaderSchemaCheck`, `InstancePruner`, `PlanProcessStates`, `HeaderJson`, `CompositionRuleEvaluator`, `WorkflowLexer` (workflow), `AnswerSchema`, `FreeTextDerivation`, `BuildDaemonStore` (runtime), `GitIdentity`, `LinkedWorktrees`, `WorktreeShaDigest` (git), `SonarProvider` (sonar). Replace the constructor by the annotation; a class whose private constructor does something else, or that is a nested holder, is left and listed. Static imports and callers are unchanged, except the five files that statically import the nested holders of `WorkflowLogMessages` (see the State section). The full list of the 63 is in the State section.
   - Skills: pm-dev-java:java-lombok, pm-dev-java:java-core, pm-dev-java:java-null-safety
   - Persona: implementer
2. **The generic catches of `pm-runtime` that no other plan owns.** PLAN-V2-46 (`runtime-endpoint-residues`) replaces the six `catch (RuntimeException _)` of `AnswerSchema`, `EndpointHarness`, `EndpointVerification` and `WeightsReader`, and PLAN-V2-67 settles the `throws Throwable` of `MacSecurity.Downcall` (S112); this plan takes the rest (`WorkerSupervisor.java:318`, the six `RuntimeException | Error` clauses of `SubprocessJobRunner`, and the `Throwable` catches of `MacSecurity.java:313`, `Posix.java:38`, `KillBinding.java:72`, `SystemProcessExecutables.java:164`). Per site decide and record in the pull request body: (a) replace by the specific exceptions the call can throw; (b) where the site is a last-resort handler that must not let a job runner die (several are in `SubprocessJobRunner`: lines 1118, 1164, 1406, 1415, 1441, 1720), keep the catch of `RuntimeException`, name it in this spec's constraint below, and add a `@SuppressWarnings` with the Sonar rule and the reason beside it; (c) the `MethodHandle.invokeExact` downcalls (`MacSecurity.java:305` and `313`, `Posix.java:38`, `KillBinding.java:72`, `SystemProcessExecutables.java:164`) are the one case where `Throwable` is the library's own contract: keep it, with the reason. The six OpenRewrite `TODO` markers are resolved by the decision, not left.
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-dev-java:junit-core
   - Persona: implementer

**Done when:** `git grep -n -E 'private [A-Z][A-Za-z0-9]*\(\) *\{' -- '*/src/main/java/*.java'` finds only the constructors listed with a reason; `git grep -n -E 'catch \((RuntimeException|Throwable|Exception)' -- 'pm-runtime/src/main/java/*.java'` finds only the sites listed in the pull request body with their reason; the six `TODO: Catch specific` markers are NOT this plan's (they belong to PLAN-V2-46; the clause is dropped); a catch that this plan changes has a test of the failure it handles (none has to change at the head of 2026-11-15, see the State section). **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `java-lombok`, `java-core` (SKILL.md and standards); R20; CLAUDE.md (Code Standards: never catch or throw generic `Exception`/`RuntimeException`); the audit `skills-audit-2026-10-30.md` §§ 2.10 and 4 (items 4 and 8).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **No behaviour change** except where a generic catch hid a defect: such a case is fixed with its own test and named in the landing.
- **The job runner's last-resort handlers are the operator's call to keep:** the plan keeps them only where the spec of the job runner requires that the runner survives (Job Runtime has no section "Failure": cite § Outcome Derivation, "Write order at job end" (`01-pipeline-and-subprocesses.adoc:196,206`) and § Process Termination & Cancellation Protocol (:116)); the pull request body quotes the sentence it relies on per site.
- **A class annotated `@UtilityClass` is final and static-only:** callers that mock or subclass it (none expected; the repository has no mocking library) are reported.

## Claim Labels

- OBSERVED: static-only classes with a hand-written private constructor — core 22, workflow 32, runtime 7, git 3, sonar 1 (heuristic scan, `skills-audit-2026-10-30.md` § 2.10); `@UtilityClass` already in core 49, runtime 26, providers 2
- OBSERVED: generic catches in `pm-runtime` — `catch (RuntimeException _)` 7 (`WeightsReader.java:81`, `AnswerSchema.java:126`, `EndpointHarness.java:143`, `EndpointVerification.java:222,255,282`, `WorkerSupervisor.java:318`), `catch (RuntimeException | Error ...)` 6 (`SubprocessJobRunner.java:1118,1164,1406,1415,1441,1720`), `catch (Throwable t)` 4 (`MacSecurity.java:313`, `Posix.java:38`, `KillBinding.java:72`, `SystemProcessExecutables.java:164`), `throws Throwable` 1 (`MacSecurity.java:305`)
- HYPOTHESIS: the line numbers moved when PLAN-V2-84 landed — find each site again with the command in the Done-when
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: the static-only classes found by the scan of Deliverable 1, listed in the State section (63 less the two held out, plus five import files; about 67 files below `pm-core/src/main/java/`, `pm-workflow/src/main/java/`, `pm-runtime/src/main/java/` and `pm-providers/*/src/main/java/`)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/KillBinding.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/MacSecurity.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/Posix.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerSupervisor.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SystemProcessExecutables.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/AnswerSchema.java` (Deliverable 1 only; its catches are PLAN-V2-46's)
- OBSERVED (added at the emit): `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SystemProcessExecutables.java` — the `@SuppressWarnings("java:S1181")` of the `catch (Throwable t)` at :164 (the only D2 edit that is certain)
- OBSERVED (added at the emit): the five files that import `WorkflowLogMessages.INFO/WARN/ERROR` statically — `pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceStore.java`, `.../workflow/unit/DefaultUnitLoader.java`, `.../workflow/unit/ProjectUnitLoader.java`, and the tests `pm-workflow/src/test/java/de/planmarshall/workflow/unit/DefaultUnitLoaderTest.java`, `ProjectUnitLoaderTest.java`
- HYPOTHESIS: the tests of the changed catches (none expected)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (Lombok in `pm-workflow` and `pm-provider-sonar`) and PLAN-V2-84 (it edits the same production classes and their line numbers).
- Overlaps by class with `PLAN-V2-46` (`runtime-endpoint-residues`: `AnswerSchema`, `EndpointHarness`), `PLAN-V2-67` (`MacSecurity`) and `PLAN-V2-66` (`SubprocessJobRunner`); this plan runs first, those read the changed files.

## State at the emit

**State at the emit (2026-11-15, `origin/main` `87e14af3`; re-grounded by a read-only leaf, re-read at `SystemProcessExecutables.java:158-166`, `JobSettlement.java:217-225`, the five static imports, the four endpoint `TODO` files, `SecretRedactor.java:228`), binding over any contradicting older text of this spec:** PLAN-V2-83 and 84 have landed and Lombok is a dependency and an annotation processor of every module the plan touches (`pm-core`, `pm-workflow`, `pm-runtime`, `pm-provider-git`, `pm-provider-sonar`), so no dependency decision is needed. **D1: 63 static-only classes** with an empty `private X() {}` and no `@UtilityClass` (all `final`, none with a static initialiser, none subclassed, reflected or instantiated, no test uses `getDeclaredConstructor` on them): `pm-core` 22 (`config/RoleAssignmentProposal`, `config/RoleAssignmentValidation`, `id/ShortName` (constants-only), `profile/CanonicalJson`, `JsonDocument`, `JsonPointer`, `ProfileStages`, `ProfileValidator`, `ShippedProfiles`, `queue/SecretRedactor`, `role/RoleArtifactValidator`, `RoleDigests`, `RoleSetLoader`, `RoleValidation`, `ShippedRoles`, `role/qualification/ConstantBaseline`, `JudgeCheck`, `Scoring`, `spi/validation/BuilderCheck`, `store/ClarifiedRequestRenderer`, `IntentAcceptance`, `OutlineRenderer`), `pm-workflow` 32 (`diagram/` AsciiTableRenderer, InstanceAsciiTableRenderer, InstanceMermaidRenderer, WorkflowDiagramGenerator; `dsl/` InstanceHeaderReader, UnitFrontmatterReader, WorkflowLexer; `engine/` EntityIdGrammar, LoopBackBudget, WaitSelection; `finalize/` CloneDetector, FinalizePipelineValidator, SimplifyStep; `log/WorkflowLogMessages` (constants-only holder); `plan/` CompositionRuleEvaluator, HeaderJson, HeaderSchemaCheck, InstancePruner, PlanProcessStates; `question/QuestionContinuation`; `setup/SetupGate`; `verify/` DeclarationBounds, ExecuteInvariants, FinalizeInvariants, GeneralInvariants, InitInvariants, LaneEntryIds (constants-only), OutlineInvariants, PlanInvariants, RefineInvariants, RemediationInvariants, SubWorkflowInvariants), `pm-runtime` 5 (`config/MachineDefaultsValidation`, `endpoint/AnswerSchema`, `job/BuildDaemonStore`, `launch/FreeTextDerivation`, `launch/HarnessEventVerification`), `pm-provider-git` 3 (`GitIdentity`, `LinkedWorktrees`, `WorktreeShaDigest`), `pm-provider-sonar` 1 (`SonarProvider`, constants-only). `EarsForm` is gone (#396). `@UtilityClass` is already on core 73, runtime 25, workflow 5, providers 2. **Held out of this plan:** `SecretRedactor` (PLAN-V2-143 is running on it and leaves the annotation to a later plan; the conversion is carried to PLAN-V2-59, which edits the class next) and `ProfileValidator` (PLAN-V2-63 converts it in its own pull request; its constructor at :261-262 sits beside the constants 63 edits). So 61 classes. **The nested holders `INFO`, `WARN`, `ERROR` of `WorkflowLogMessages` are annotated like those of `CoreLogMessages` and `RuntimeLogMessages`; the five files that statically import them switch to plain imports** (Lombok's documented limitation for static imports of `@UtilityClass` members; `pm-core` and `pm-runtime` already use plain imports): `PlanInstanceStore`, `DefaultUnitLoader`, `ProjectUnitLoader` and the tests `DefaultUnitLoaderTest`, `ProjectUnitLoaderTest`. Left with their constructors and listed in the pull request body with the reason: the nested holders `CorpusValidator.Schemas` (:498), `GradingOffer.ExpectedTokens` (:118), `GitHubOperation.Permission` (:67), `CloneDetector.Tokenizer` (:167), and the four genuine builders `PrimitiveResult.Builder`, `StoreDigest.Builder`, `ToonValue.Builder`, `PlanFacts.Builder`; not candidates (instance members): `AsciiDocTableRenderer`, `InstanceAsciiDocTableRenderer`, `MermaidRenderer`, `WorkflowPrinter`, `PlanFacts`, `CorpusValidator`. After D1 the Done-when regex matches those eight (plus `ProfileValidator` and `SecretRedactor` until their plans merge: list them as held out). The test-side static-only classes (`HostToolCalls`, `ProcessLiveness`, `JsonDocuments`) are PLAN-V2-126 D5, out of scope. **D2 is almost entirely settled in the code:** 18 catch sites and one `throws Throwable` in `pm-runtime` main (nothing in core, workflow or the providers; no `catch (Exception`, no `throws Exception`, no `throw new RuntimeException/Exception`). Six are PLAN-V2-46's (`AnswerSchema:127`, `EndpointHarness:136`, `EndpointVerification:248,287,311`, `WeightsReader:82`; each with its `TODO: Catch specific`; 46's Expected Surface says "two catches" for `EndpointVerification`, its D3 text names all three) and the `throws Throwable` of `MacSecurity:305` (S112) is PLAN-V2-67's. The other twelve each carry a `// cui-rewrite:disable InvalidExceptionUsageRecipe` marker and an in-code reason: `WorkerSupervisor:325` (`RuntimeException`, releases the slot and rethrows; no `@SuppressWarnings`, S1181 does not apply; keep with its reason, or restructure to `boolean started; try { … } finally { if (!started) release }`, behaviour identical, optional), `SubprocessJobRunner:1145,1209,1458,1467,1493,1788` (`RuntimeException | Error`, each with `@SuppressWarnings("java:S1181")` and a reason), `JobSettlement:224` (`RuntimeException`, `@SuppressWarnings` at :219 and a Javadoc naming it the last-resort handler of Job Runtime § Outcome Derivation, Write order at job end: settled, not new or unowned), and the `Throwable` catches of the `invokeExact` downcalls `MacSecurity:313`, `Posix:38`, `KillBinding:72` (each with `@SuppressWarnings`) and `SystemProcessExecutables:164` (the only one WITHOUT the `@SuppressWarnings("java:S1181")`: add it with the reason comment, as at the other three). So D2 is: that one annotation, the optional `WorkerSupervisor` restructure, and the pull-request-body list of the twelve sites with the sentence each relies on. The Job Runtime specification has no section "Failure": cite § Outcome Derivation (Write order at job end, `01-pipeline-and-subprocesses.adoc:196,206`) and § Process Termination & Cancellation Protocol (:116). The six `TODO: Catch specific` markers are PLAN-V2-46's and not a Done-when clause of this plan. **Size:** 61 classes + 5 import files + 1 (`SystemProcessExecutables`) + 0-1 (`WorkerSupervisor`) = about 67-68 files, under the 100-file cap, one pull request. **Overlaps:** PLAN-V2-143 (running): `SecretRedactor` only, held out; PLAN-V2-63: `ProfileValidator` only, held out; staged plans that edit a D1 class by another hunk (the 94 hunk is the annotation and the constructor only): `ProfileStages` (147, 135, 114), `SecretRedactor` (135, 59), `JudgeCheck`, `RoleArtifactValidator`, `RoleDigests`, `BuilderCheck` (114), `HarnessEventVerification` (148), `AnswerSchema` (46), `MacSecurity` (67); the later plan reads this tree. PLAN-V2-101 (core/store tests) and 125 and 126 (tests): none.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-94-utility-classes-and-generic-catches.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
