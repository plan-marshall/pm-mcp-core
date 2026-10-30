# PLAN-V2-88: Generated test data in the pm-workflow tests

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-88-workflow-tests.md` and is queued as one row file, `queue/PLAN-V2-88.json`,
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

Rules that apply to this plan: R02, R06, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03 and R04: no production HTTP and no HTTP test in this plan unless Deliverable 4 says so; R05, R07, R09, R10, R12 to R16: no behaviour, specification, message or store change; R19 is replaced by the Standing Constraint below.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none (test-only); ADR directory: none; layout: workstream WS-11.
- R12 — log message holder: none (no message is added).

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

The audit found that effectively every test of pm-workflow builds its data from literals (about 5000 string literals) while `cui-testing` makes generated data mandatory and `cui-test-generator` is available after PLAN-V2-83. The operator chose on 2026-10-30 to migrate all tests. This plan migrates all 94 test classes of `pm-workflow/src/test/java/de/planmarshall/workflow/` (`verify`, `task`, `plan`, `engine`, `scenario`, `dsl`, `model`, `unit`, `finalize`, `consult`, `diagram`, `ast`, `question`, `setup`, `log`, `hypermedia`, `classification`), test code only. 

## Deliverables

1. **Generated test data in every test class of the scope (class by class, no test removed).** For each test class in all 94 test classes of `pm-workflow/src/test/java/de/planmarshall/workflow/` (`verify`, `task`, `plan`, `engine`, `scenario`, `dsl`, `model`, `unit`, `finalize`, `consult`, `diagram`, `ast`, `question`, `setup`, `log`, `hypermedia`, `classification`): enable the generator controller, replace every literal that is not a contract (see the Standing Constraints) by `Generators.*` data (`letterStrings`, `integers`, `longs`, `bytes`, `fixedValues`, `@GeneratorsSource` for the parameterized tests with three or more variants), and keep the assertions equivalent. Test first in the sense that the existing tests are the guard: run the class before and after, same method count. Start with the helper and fixture classes of the scope, because the other classes call them. **Reuse before you write:** where a typed generator for a domain type already exists in a generator artifact (PLAN-V2-107: `CoreGenerators` of `pm-core`, `HttpGenerators` of `pm-provider-http`, the module's own), use it; a new generator for a type of this module goes into this module's generator artifact, not into a test class, and a type of another module is taken from that module's artifact.
2. **Test hygiene and log assertions of the module.** `ModelWaitTest` gets its `@DisplayName` (the one class of the module without). The two `WorkflowLogMessages` records that no test references, `PLAN_INSTANCE_VERSION_RECORDED` and `PLAN_INSTANCE_COMPOSITION_REFUSED`, are asserted with `LogAsserts` by constant (`@EnableTestLogger`); the module has no other uncovered record.

**Done when:** every test class of the scope enables the generator controller and calls `Generators`, or carries the one-line statement that every datum is a contract; the pull request body has the per-class table (generator calls, literals kept, category); the number of test methods per class is the same or higher; both records are asserted by constant; `ModelWaitTest` has its display name. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `cui-testing`, `junit-core` (SKILL.md and standards, in particular the literal-or-generated discriminator); R20; ADR of PLAN-V2-83; the audit `skills-audit-2026-10-30.md` §§ 2.2 to 2.4 and 2.7.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Test code only.** No production class changes (a production change this plan finds necessary is recorded in the landing message and left to the owning plan); no assertion is weakened or removed to make a conversion pass, and the number of test methods per class is the same or higher.
- **R19 is replaced for this plan:** the Expected Surface names the test packages (a mass migration touches every class of them); the orchestrator pairs this plan by directory with every other plan that edits a test of these packages, so it runs at the front of the epic, before the plans that edit the same tests.
- **Generated data, with the exemption of the rule R20:** a literal stays where it is the contract (a wire format or JSON document under test, an error or reason code, a schema or specification string, a golden file, a path of a fixture). Every class of the plan ends with `@EnableGeneratorController` and at least one `Generators.*` call, or with a one-line comment above the class saying that every datum is a contract; the pull request body lists per class the number of generator calls, the number of literals kept and the category of each kept literal.
- **Seeds:** a failing generated test must be reproducible: the lane uses the seed reporting of `cui-test-generator` (`@GeneratorSeed` only on a test that needs a fixed value), and never `Random` or `UUID.randomUUID()` for test data.

## Claim Labels

- OBSERVED: `Generators` in 3 of 71 workflow test classes (11 calls), `cui-test-generator` already a dependency; about 5184 string literals; `ModelWaitTest.java` without `@DisplayName`; 2 of 5 `WorkflowLogMessages` records never referenced in a test (`skills-audit-2026-10-30.md` §§ 2.2, 2.3, 2.7)
- OBSERVED: `pm-workflow` has no hand-written `equals`/`hashCode` outside records, so no contract test is owed
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/task/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/model/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/finalize/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/consult/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/ast/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/question/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/setup/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/log/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/hypermedia/` (directory; R19 replaced)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/classification/` (directory; R19 replaced)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies), PLAN-V2-107 (`generator-artifacts`: the shared domain generators) and PLAN-V2-84 (`null-safety-nullmarked`: it changes signatures and tests of the same classes).
- Siblings of the family (disjoint packages, run in parallel): PLAN-V2-85 to PLAN-V2-92 each own other test packages.
- Overlaps: by directory with every plan that edits a test of these packages (R19 replacement): the orchestrator pairs them and runs this plan first.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-88-workflow-tests.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
