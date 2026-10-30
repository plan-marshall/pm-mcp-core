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
> Origin: the skills conformance audit of 2026-10-30 (`skills-audit-2026-10-30.md` in the epic directory), taken at `origin/main` `6847c748`, and the operator's decisions of the same day (Decisions in `epic.md`). The plan family is PLAN-V2-83 to PLAN-V2-106 (workstream WS-11, with PLAN-V2-95 and PLAN-V2-96 in WS-05); it runs first, except PLAN-V2-96, which follows PLAN-V2-31.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R08, R11, R14, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12, R13, R15, R16: no behaviour, specification, message or format change; R14 because several catches guard durable multi-step protocols of `pm-runtime`.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none; ADR directory: none (the ADR of PLAN-V2-83 decides); layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

About 65 production classes hold only static methods and declare a private constructor by hand where `java-lombok` and CLAUDE.md want `@UtilityClass` (core 22, workflow 32, runtime 7, git 3, sonar 1; Lombok is available in `pm-workflow` and `pm-provider-sonar` after PLAN-V2-83). And 17 sites in `pm-runtime` (11 of them are this plan's; six belong to PLAN-V2-46 and one `throws` to PLAN-V2-67) catch `RuntimeException`, `RuntimeException | Error` or `Throwable`, plus one `throws Throwable`, where CLAUDE.md forbids a generic catch or throw in production code (six carry an OpenRewrite `TODO` marker).

## Deliverables

1. **`@UtilityClass` for the static-only classes.** Find the candidates (no instance member, hand-written `private X()`); the audit lists, among others, `SecretRedactor`, `EarsForm`, `RoleSetLoader`, `RoleAssignmentValidation`, `ClarifiedRequestRenderer`, `OutlineRenderer` (core), `HeaderSchemaCheck`, `InstancePruner`, `PlanProcessStates`, `HeaderJson`, `CompositionRuleEvaluator`, `WorkflowLexer` (workflow), `AnswerSchema`, `FreeTextDerivation`, `BuildDaemonStore` (runtime), `GitIdentity`, `LinkedWorktrees`, `WorktreeShaDigest` (git), `SonarProvider` (sonar). Replace the constructor by the annotation; a class whose private constructor does something else, or that is a nested holder, is left and listed. Static imports and callers are unchanged.
2. **The generic catches of `pm-runtime` that no other plan owns.** PLAN-V2-46 (`runtime-endpoint-residues`) replaces the six `catch (RuntimeException _)` of `AnswerSchema`, `EndpointHarness`, `EndpointVerification` and `WeightsReader`, and PLAN-V2-67 settles the `throws Throwable` of `MacSecurity.Downcall` (S112); this plan takes the rest (`WorkerSupervisor.java:318`, the six `RuntimeException | Error` clauses of `SubprocessJobRunner`, and the `Throwable` catches of `MacSecurity.java:313`, `Posix.java:38`, `KillBinding.java:72`, `SystemProcessExecutables.java:164`). Per site decide and record in the pull request body: (a) replace by the specific exceptions the call can throw; (b) where the site is a last-resort handler that must not let a job runner die (several are in `SubprocessJobRunner`: lines 1118, 1164, 1406, 1415, 1441, 1720), keep the catch of `RuntimeException`, name it in this spec's constraint below, and add a `@SuppressWarnings` with the Sonar rule and the reason beside it; (c) the `MethodHandle.invokeExact` downcalls (`MacSecurity.java:305` and `313`, `Posix.java:38`, `KillBinding.java:72`, `SystemProcessExecutables.java:164`) are the one case where `Throwable` is the library's own contract: keep it, with the reason. The six OpenRewrite `TODO` markers are resolved by the decision, not left.

**Done when:** `git grep -n -E 'private [A-Z][A-Za-z0-9]*\(\) *\{' -- '*/src/main/java/*.java'` finds only the constructors listed with a reason; `git grep -n -E 'catch \((RuntimeException|Throwable|Exception)' -- 'pm-runtime/src/main/java/*.java'` finds only the sites listed in the pull request body with their reason; the six `TODO: Catch specific` markers are gone; every changed catch has a test of the failure it handles. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `java-lombok`, `java-core` (SKILL.md and standards); R20; CLAUDE.md (Code Standards: never catch or throw generic `Exception`/`RuntimeException`); the audit `skills-audit-2026-10-30.md` §§ 2.10 and 4 (items 4 and 8).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **No behaviour change** except where a generic catch hid a defect: such a case is fixed with its own test and named in the landing.
- **The job runner's last-resort handlers are the operator's call to keep:** the plan keeps them only where the spec of the job runner (Job Runtime § Failure) requires that the runner survives; the pull request body quotes the sentence.
- **A class annotated `@UtilityClass` is final and static-only:** callers that mock or subclass it (none expected; the repository has no mocking library) are reported.

## Claim Labels

- OBSERVED: static-only classes with a hand-written private constructor — core 22, workflow 32, runtime 7, git 3, sonar 1 (heuristic scan, `skills-audit-2026-10-30.md` § 2.10); `@UtilityClass` already in core 49, runtime 26, providers 2
- OBSERVED: generic catches in `pm-runtime` — `catch (RuntimeException _)` 7 (`WeightsReader.java:81`, `AnswerSchema.java:126`, `EndpointHarness.java:143`, `EndpointVerification.java:222,255,282`, `WorkerSupervisor.java:318`), `catch (RuntimeException | Error ...)` 6 (`SubprocessJobRunner.java:1118,1164,1406,1415,1441,1720`), `catch (Throwable t)` 4 (`MacSecurity.java:313`, `Posix.java:38`, `KillBinding.java:72`, `SystemProcessExecutables.java:164`), `throws Throwable` 1 (`MacSecurity.java:305`)
- HYPOTHESIS: the line numbers moved when PLAN-V2-84 landed — find each site again with the command in the Done-when
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: the static-only classes found by the scan of Deliverable 1 (about 65 files below `pm-core/src/main/java/`, `pm-workflow/src/main/java/`, `pm-runtime/src/main/java/` and `pm-providers/*/src/main/java/`)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/KillBinding.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/MacSecurity.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/Posix.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerSupervisor.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SystemProcessExecutables.java`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/AnswerSchema.java` (Deliverable 1 only; its catches are PLAN-V2-46's)
- HYPOTHESIS: the tests of the changed catches

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (Lombok in `pm-workflow` and `pm-provider-sonar`) and PLAN-V2-84 (it edits the same production classes and their line numbers).
- Overlaps by class with `PLAN-V2-46` (`runtime-endpoint-residues`: `AnswerSchema`, `EndpointHarness`), `PLAN-V2-67` (`MacSecurity`) and `PLAN-V2-66` (`SubprocessJobRunner`); this plan runs first, those read the changed files.

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
