# PLAN-V2-107: Publish a generator artifact per module so the other modules reuse its domain generators

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-107-generator-artifacts.md` and is queued as one row file, `queue/PLAN-V2-107.json`,
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

Rules that apply to this plan: R02, R06, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12 to R16: no behaviour change.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the module READMEs ('Test artifact'); ADR directory: `doc/adr/` (repository-wide); layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

The migration of about 400 test classes to `cui-test-generator` (PLAN-V2-85 to PLAN-V2-93) will create typed generators for domain types such as findings, profiles, store records and queue tasks. Without a shared place every test class, and every module, would build its own. The operator decided on 2026-10-30: **each module that provides generators that might be useful to others creates and deploys a generator artifact that the other modules use.** `pm-runtime` already builds a test JAR (`TestBases`, `FakeBus`, `FakeSecretService`) that the assembly's tests consume; this plan makes that the pattern for every module whose generators are reusable, and runs before the test migration so the migration uses it. The dependency rules decide who may consume which artifact: `pm-core`'s generators are usable by `pm-workflow`, `pm-runtime` and every provider (all depend on `pm-core`); `pm-provider-http`'s by `pm-provider-github`, `-gitlab` and `-sonar` (they depend on it for the shared HTTP base); the generators of `pm-workflow` and `pm-runtime` are usable by the assembly in `plan-marshall-mcp` only, because `pm-runtime` may not depend on `pm-workflow` or the reverse.

## Deliverables

1. **The mechanism (the build is the test).** Read how `pm-runtime` builds and deploys its test JAR (the `test-jar` goal of the jar plugin, the `tests` classifier, the deployment with the `SNAPSHOT`) and apply the same, unchanged, to `pm-core`, `pm-workflow` and `pm-provider-http`. No new plugin and no new deployment target (CLAUDE.md: the organisation's registry only); if the parent POM's plugin management does not cover the goal, stop and report it through the inbox message. `BuildGuardsIT` gets a fixture proving that a test-scope dependency on `pm-core:tests` is allowed for `pm-workflow`, `pm-runtime` and the providers and that a dependency of `pm-runtime` on the tests of `pm-workflow` (or the reverse) is still refused.
2. **The package convention.** Each artifact holds one package `de.planmarshall.<module>.testing.generators` (with `package-info.java`, `@NullMarked`, documented) with one `TypedGenerator<T>` per domain type, named `<Type>Generator`, and one factory class per artifact (`CoreGenerators`, `WorkflowGenerators`, `RuntimeGenerators`, `HttpGenerators`) that registers them for `@GeneratorsSource`. The artifacts hold generators and the fixtures that build on them, no test of their own and no fake (the fakes are PLAN-V2-106's).
3. **pm-core: the inventory and the first generators (test first: each generator has a test that it yields valid, distinct values).** Find the domain types of `pm-core` that tests of other modules build by hand (`git grep -l 'import de.planmarshall.core' -- 'pm-workflow/src/test' 'pm-runtime/src/test' 'pm-providers/*/src/test'`, then the constructors called) and the fixture classes of `pm-core`'s own tests that other modules would use (`IntentFixtures`, `OutlineFixtures`, `ProfileFixtures`, `QuestionLogFixture`, `ProjectResolverFixture`, `VerificationResults` and the like). Move or convert the reusable ones into the `pm-core` artifact as typed generators; a fixture used only inside `pm-core` stays where it is until PLAN-V2-85 to PLAN-V2-87 touch it.
4. **pm-workflow, pm-runtime and pm-provider-http.** The same inventory for each: the generators of types that another module or the assembly would use; `pm-runtime`'s existing test JAR gets the `testing.generators` package beside `TestBases` (its fakes stay for PLAN-V2-106). A module with nothing reusable publishes no artifact (say so in the landing, with the inventory result); `pm-provider-git`, `-github`, `-gitlab` and `-sonar` publish none unless the inventory finds a reusable generator.
5. **Consumption and documentation.** The consuming modules declare the artifact as a test-scope dependency (type `test-jar`, version from the parent or the reactor, no literal), and at least one test of each consumer uses a generator from it. CLAUDE.md § Build lists the artifacts and who consumes them; each module README gets a section 'Test artifact'; one repository ADR (`doc/adr/NNN-generator-artifacts.adoc`, next free number, R11) records the convention, the consumption matrix above and the rule: **a test that needs a generator for a type of another module takes it from that module's artifact; a new generator for a type of this module goes into this module's artifact, not into a test class.**

**Done when:** the `tests` artifact of `pm-core`, `pm-workflow`, `pm-runtime` and `pm-provider-http` (where the inventory found a reusable generator) is built by `verify` and deployed with the `SNAPSHOT` on merge, like the existing runtime test JAR; `BuildGuardsIT` proves the allowed and the forbidden dependency; every generator has its test; the consumers use at least one; the ADR, CLAUDE.md and the READMEs agree. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `cui-testing` (generators, `TypedGenerator`, `@GeneratorsSource`); `junit-core`; R20; the repository `CLAUDE.md` (Build, Dependency Rules, Publishing Only to the Organisation's Registry); the existing `pm-runtime` test JAR; decision 'generator artifacts' in `epic.md`.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **The registry rule stands:** the artifacts deploy to the organisation's GitHub Packages registry as `SNAPSHOT` versions, like every other artifact of this repository, and nowhere else (CLAUDE.md); no release, no Maven Central.
- **Dependency Rules stand:** no module gains a dependency on a module it may not depend on; `pm-core` and `pm-workflow` keep their enforcer rules (no JGit, no `cui-http`, no Quarkus).
- **No behaviour change in production code;** the plan adds generators and fixtures and moves test helper classes.
- **R19 is replaced:** the Expected Surface lists the POMs and the new packages; the fixture classes it moves are named at the outline and are listed in the landing as the surface delta.

## Claim Labels

- OBSERVED: `pm-runtime` builds a test JAR (`TestBases`, `FakeBus`, `FakeSecretService`) that is deployed with the `SNAPSHOT` and used by the assembly's tests (CLAUDE.md, Code Standards)
- OBSERVED: fixture classes in `pm-core` tests — `IntentFixtures`, `OutlineFixtures`, `QuestionLogFixture`, `ProjectResolverFixture` (store), `ProfileFixtures` (profile), `ProductCorpus`, `SpecificationDocument` — and `VerificationResults` in the runtime tests (found with `git ls-files`)
- OBSERVED: the allowed consumption follows the Dependency Rules of CLAUDE.md (`pm-workflow` depends on `pm-core` alone, `pm-runtime` on `pm-core` and `pm-api` alone, the providers on `pm-core` and `pm-provider-http`)
- HYPOTHESIS: the parent POM already manages the jar plugin's `test-jar` goal for `pm-runtime`; confirm with `help:effective-pom` of `pm-runtime` at the outline
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/pom.xml`
- OBSERVED: `pm-workflow/pom.xml`
- OBSERVED: `pm-runtime/pom.xml`
- OBSERVED: `pm-providers/pm-provider-http/pom.xml`
- OBSERVED: `pm-providers/pm-provider-github/pom.xml`
- OBSERVED: `pm-providers/pm-provider-gitlab/pom.xml`
- OBSERVED: `pm-providers/pm-provider-sonar/pom.xml`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/BuildGuardsIT.java`
- OBSERVED: `CLAUDE.md` (Build)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/testing/generators/` (new package)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/testing/generators/` (new package)
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/testing/generators/` (new package)
- HYPOTHESIS: `pm-providers/pm-provider-http/src/test/java/de/planmarshall/provider/http/testing/generators/` (new package)
- HYPOTHESIS: `doc/adr/NNN-generator-artifacts.adoc` (new), `doc/adr/README.adoc` (index row)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`cui-test-generator` and `cui-test-value-objects` in the POMs) and PLAN-V2-84 (it edits the same package-infos).
- PLAN-V2-85 to PLAN-V2-93 (the test migrations) depend on this plan: they use the artifacts and add generators to them.
- Overlaps: the POMs with PLAN-V2-83 (a dependency), nothing else.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-107-generator-artifacts.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
