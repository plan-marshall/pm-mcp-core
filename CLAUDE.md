# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## Project

`pm-mcp-core` holds the plain-Java engine of plan-marshall-mcp (PM-MCP): the foundation `pm-core`, the workflow
engine `pm-workflow`, the provider modules under `pm-providers`, and `pm-runtime`, the daemon's logic that needs no framework. The Quarkus assembly
`pm-mcp-server` that wires these modules into the daemon `pm-mcpd`, and all model-facing content, live in
`plan-marshall/plan-marshall-mcp`; the client contract `pm-api` and the client binaries in
`plan-marshall/pm-mcp-clients`.

The only listing of the repositories, the modules, their responsibilities and allowed dependencies is the Module
Structure Specification of the product (`doc/specification/module-structure.adoc` in
`plan-marshall/plan-marshall-documentation`); name modules from there and
never repeat the listing here. The specifications this repository implements live here, beside the code
(ADR `doc/adr/007-specifications-of-the-engine-in-this-repository.adoc`): `doc/Specification.adoc` with
`doc/specification/`, their implementation watch `doc/ImplementationWatch.adoc` with `doc/implementation-watch/`, and
`doc/Traceability.adoc`, which records what each requirement implemented here is implemented by, verified by and
documented in. The requirements, use cases, roadmap, the shared specifications (`module-structure.adoc` among them),
`implementation-watch/cross-cutting.adoc`, and the concept and user documentation live in the documentation
repository `plan-marshall/plan-marshall-documentation`; the developer documentation of a package and the architectural
decision records (ADRs) live in the module (section "Module Documentation"). The code documentation (Javadoc and
`package-info.java`) and the work package files with their index below `doc/plans/` live here as well. A plan changes
the specification, watch and traceability here in its own pull request; it opens a pull request in the documentation
only to correct a requirement or to change `cross-cutting.adoc` or a specification that lives there. Every concrete
implementation follows the project skill `traced-implementation`, which lives there with `doc-review`; the skills of
the same names here only point to them. Links here are relative, also into the documentation (sibling paths); check
them with `python3 ../plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py ../plan-marshall-documentation/doc .`
(`BROKEN LINKS 0`); the workflow `links.yml` of the documentation runs it over both trees.

## Project Setup

`../plan-marshall-documentation/doc/developer/project-setup.adoc` is the reference for how the project is organised and
worked on: the repositories and their checkouts beside each other, building, how the repositories depend on each other,
what every repository is set up with, how the work is planned as work packages, and how a change gets from a branch to
`main`. Read it before starting work. This file holds only what is specific to this repository.

## Module Documentation

The documentation that belongs to the code lives beside it, in four places:

- `{module}/README.adoc`: the module's purpose, its packages, its build and test commands, and links to its
  developer documentation and ADRs.
- `{module}/doc/developer/{package}.adoc`: the developer documentation of one package.
- `{module}/doc/adr/{package}/NNN-{slug}.adoc`: one ADR per design decision of the package, numbered per directory.
- `doc/adr/NNN-{slug}.adoc`: one ADR per design decision that concerns the whole repository; the template is
  `doc/adr/template.adoc`, the index `doc/adr/README.adoc`.

A plan writes the developer documentation of the packages it changes and one ADR per design decision it takes, in
its own pull request.

## Build

### Build Commands

- Never hard-code a build tool command; call the build by its canonical name through the commands below.
- Compile: `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "compile"`
- Quality gate: `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "verify -Ppre-commit"`
  (rewrites files: licence headers, OpenRewrite recipes, import order; review every resulting diff and commit it,
  then run the full verify again)
- Full verify: `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "verify"`
- Coverage: `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "verify -Pcoverage"`
  (minimum 80% instruction and branch coverage per module)
- Tests (`{module}`): `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "test -pl {path} -am"`
  — only on the modules with tests: `pm-core`, `pm-workflow`, `pm-runtime`, and below `pm-providers/` each of
  `pm-provider-http`, `pm-provider-git`, `pm-provider-github`, `pm-provider-gitlab`, `pm-provider-sonar`
- Integration tests (`pm-core`): `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "verify -Pintegration-tests -pl pm-core -am"`
  — only on `pm-core`
- Integration tests (`pm-runtime`): `python3 .plan/execute-script.py plan-marshall:build-maven:maven run --command-args "verify -Pintegration-tests -pl pm-runtime -am"`
  — only on `pm-runtime`
- Run a build with a Bash timeout of 600000ms.
- Analyse the TOON result of every build: `status`, `errors[N]{file,line,message,category}`, `log_file`.

### Build Rules

- This repository builds no binary. Native-image metadata of a class travels with its module
  (`META-INF/native-image/de.planmarshall/<name>/`); a change to it is verified by the native build of `pm-mcpd` in
  plan-marshall-mcp against the deployed `SNAPSHOT`.
- Always build and test through Maven and JUnit; never run `javac` directly or write ad-hoc verifier classes.
- The compiler runs with `failOnWarning`: fix deprecations and warnings, don't suppress them.
- `.mvn/maven.config` passes `.mvn/settings.xml` (the organisation's package registry, no token) as global
  settings; the token is the server `plan-marshall` of `~/.m2/settings.xml`.

### Generator Artifacts

Four modules publish their test scope as a `tests`-classified test JAR, built by `verify` and deployed with the
`SNAPSHOT` like every other artifact of the repository:

- `pm-core:tests` — consumable by `pm-workflow`, `pm-runtime` and every provider module.
- `pm-provider-http:tests` — consumable by `pm-provider-github`, `pm-provider-gitlab` and `pm-provider-sonar`.
- `pm-workflow:tests` and `pm-runtime:tests` — consumable by the assembly `plan-marshall-mcp` only, because the
  dependency rules forbid `pm-runtime` and the providers to depend on the engine.

A consumer declares the artifact as a test-scope dependency of type `test-jar`; its version comes from the root
POM's `dependencyManagement`, never from a literal. The generators live in
`de.planmarshall.<module>.testing.generators`, one `TypedGenerator<T>` per type, registered by the module's factory
(`CoreGenerators`, `WorkflowGenerators`, `RuntimeGenerators`, `HttpGenerators`). The convention is recorded in
`doc/adr/012-generator-artifacts.adoc`.

## Dependencies and Versions

- Parent `de.planmarshall:pm-mcp-parent`, resolved from the organisation's registry. It supplies the Java release,
  the managed third-party versions, the plugin management, the quality-gate recipes and the deployment target.
- `pm-api` comes from `pm-mcp-clients` as a `SNAPSHOT`, named by the property `version.pm-mcp-clients` in the root
  POM; a change to it is a pull request there, and its merge deploys the `SNAPSHOT` this repository builds against.
- The coding-skill dependencies are allowed (operator decision 2026-10-30; ADR `doc/adr/010`):
  `de.cuioss.test:cui-test-generator` (test), `de.cuioss.test:cui-test-value-objects` (test),
  `org.jspecify:jspecify` (compile), `org.projectlombok:lombok` (provided and annotation processor) in
  `pm-workflow` and `pm-provider-sonar`, and `org.easymock:easymock` (test). `de.cuioss.test:cui-test-mockwebserver-junit5`
  (test) in `pm-provider-sonar` is the R04 dependency (operator decision 2026-10-09). Their versions come from the
  parent POM; a module POM carries no version literal.
- Never add a dependency or a plugin without asking the user first. The six above are the operator's answers — the
  five of 2026-10-30 and the R04 dependency of 2026-10-09 — for the coding-skill conformance family (PLAN-V2-83 to
  PLAN-V2-108); anything else still needs a decision.
- Pre-1.0: no deprecation cycles, no backward-compatibility shims.

## Dependency Rules (enforced by the build)

The enforcer executions of the root POM fail the build for a violation; `BuildGuardsIT` of `pm-core` proves each of
them with a fixture project below `src/guard-controls`:

- No module depends on Quarkus, CDI, Vert.x or an MCP library, nor on `pm-mcp-server` or a client binary module.
- `pm-core` depends on `pm-api` alone among the modules of the product, and never on JGit or `cui-http`.
- `pm-workflow` depends on `pm-core` alone among the modules of the product, and never on JGit or `cui-http`.
- A provider module depends on `pm-core` and, for the shared HTTP base, on `pm-provider-http`; the contract modules
  `pm-provider-git` and `pm-provider-http` on no other provider module.
- `pm-runtime` depends on `pm-core` and `pm-api` alone among the modules of the product, and it is the only module
  that depends on `commonmark` and LSP4J.
- The coordinates of the product come from the organisation registry: `.mvn/settings.xml` lists it before Maven
  Central, and the group id filter of `.mvn/rrf` (switched on in `.mvn/maven.config`) lets it be asked for
  `de.planmarshall` only. This check is inherited from the parent POM; its fixture here has its own `.mvn` with Maven
  Central listed first.
- No module carries model-facing content: nothing below `workflows/`, `roles/`, `bundles/` or `skills/` in a JAR.
  The modules read such content by its classpath location; test fixtures are written for the test, never copied
  from the content of the product.

## Code Standards

- Java 25, Lombok (`@UtilityClass`, `@Value`, `@Builder`), prefer records, `var` for obvious types, final fields,
  package-private over public where possible. No Quarkus, no CDI: a class that needs a framework type belongs in
  `pm-mcp-server`.
- Every package has a `package-info.java` with Javadoc; every public type is documented. The `package-info.java` of
  every production package carries `@NullMarked` (JSpecify).
- Absence is returned as `Optional<T>`, never as `null` and never as a `@Nullable` return. `Optional` is a return
  type only: a parameter, field or record component that may be absent is `@Nullable`.
- A record of four or more components carries `@Builder` (the documented rule of `java-lombok`); a smaller one keeps
  its canonical constructor. A class that holds only static methods is a Lombok `@UtilityClass`.
- Never catch or throw generic `Exception`/`RuntimeException`/`Throwable` in production code; a last-resort handler
  that a specification names carries a `@SuppressWarnings` with its reason.
- Logging: `private static final CuiLogger LOGGER = new CuiLogger(X.class);` (cui-java-tools), `%s` placeholders,
  exception first; no slf4j, log4j, `System.out`/`System.err`. INFO/WARN/ERROR messages are `LogRecord` constants in
  the holder of the module that logs them, one holder per module in its own `log` package: `CoreLogMessages`
  (`de.planmarshall.core.log`, prefix `PM_CORE`), `RuntimeLogMessages` (`de.planmarshall.runtime.log`, `PM_RUNTIME`),
  `WorkflowLogMessages` (`de.planmarshall.workflow.log`, `PM_WORKFLOW`), a provider `<X>LogMessages`
  (`de.planmarshall.provider.<x>.log`, `PM_PROVIDER_<X>`), created with its first record. Ranges in every holder:
  INFO 001–099, WARN 100–199, ERROR 200–299; a new record takes the next free identifier of its level. A module logs
  only through its own holder and that of `pm-core`. Each record is documented in `doc/LogMessages.adoc` (of this
  repository).
- JUnit 5 only (`@DisplayName`, `@Nested`, AAA, `@ParameterizedTest` for 3+ variants), on the JVM without Quarkus.
  Forbidden: Mockito, PowerMock, Hamcrest; test doubles are EasyMock, and no new hand-written fake is added. No AAA
  phase comments.
- Test data come from `cui-test-generator` (`@EnableGeneratorController`, `Generators.*`, `@GeneratorsSource`). A
  literal stays only where it is the contract — a wire format, an error or reason code, a schema or specification
  string, a golden file or a fixture path — and the test says so in its display name or a one-line comment. No
  `Random`, no `UUID.randomUUID()` as test data. The reusable generators of a module live in that module's
  generator artifact (a test JAR deployed with the `SNAPSHOT`): `pm-core`'s are usable by `pm-workflow`,
  `pm-runtime` and the providers; `pm-provider-http`'s by `-github`, `-gitlab` and `-sonar`; those of `pm-workflow`
  and `pm-runtime` by the assembly only. A test that needs a generator for another module's type takes it from that
  module's artifact.
- A class with a hand-written `equals`/`hashCode` (not a record or an enum) has a contract test through
  `ShouldHandleObjectContracts` (`cui-test-value-objects`).
- Every assertion carries a message of 20 to 60 characters that says what is checked (`junit-core`).
- Every `LogRecord` a class logs is asserted by its constant with `LogAsserts` (`@EnableTestLogger`); no hand-made
  log handler.
- HTTP tests use `@EnableMockWebServer` with `@MockResponseConfig` or `@ModuleDispatcher` and an injected
  `URIBuilder`; no hand-written `Dispatcher` (R20 replaces ADR `doc/adr/002`, which PLAN-V2-93 supersedes and whose
  migration carries the existing test classes).
- Until a plan of the conformance family has landed, the existing code of its module is not a precedent for a new
  class.
- OS-specific behaviour (Secret Service, Keychain) is tested with `@EnabledOnOs`.
- `pm-runtime` builds a test JAR with the fixtures the assembly's tests share (`TestBases`, `FakeBus`,
  `FakeSecretService`); it is deployed with the `SNAPSHOT`.

## Publishing Only to the Organisation's Registry

The product is proprietary. Artifacts go to the GitHub Packages registry of the organisation `plan-marshall`
(`https://maven.pkg.github.com/plan-marshall/pm-mcp-core`) and nowhere else, never to Maven Central or another
registry. The modules are deployed as `SNAPSHOT` versions on every merge to `main` and are never released. The
deployment target, its pin and the build check that guards it come from the parent POM; `pm.repository` in the root
POM names this repository. No workflow receives a Sonatype or GPG credential. Never weaken any of this, and never
add a deployment target, without the user's explicit decision.

## Work Packages

The work of this repository is planned as work packages in `doc/plans/`, one file per package, with the index
`doc/plans/README.adoc`, the only place that states the status of a package and what it depends on (also packages
of the other repositories). A package is ready when it is open and depends on no package still listed (in another repository: on none not done); the roadmap milestone it serves says for which exit it
is needed, not when it may start. The plan files hold the tasks only: requirements, specifications and watch
items are linked, those of this repository by relative paths, those of `plan-marshall-documentation` by relative
paths that assume the repositories checked out beside each other. A package is worked in the usual plan-marshall flow
of Project Setup, _Planning the Work_ (branch, commits, pull request at finalize); within those commits its row leaves the
index and its package file is deleted.

## Git Workflow

The way from a branch to `main` is in Project Setup (_From a Branch to `main`_). The hard rules: `main` is protected by
rulesets and merges go through the merge queue, so nothing is pushed to `main`; never merge without the user's word;
commits end with `Co-Authored-By: plan-marshall <noreply@cuioss.de>`.

CI: reusable workflows of `cuioss/cuioss-organization`, pinned by full SHA with a version comment; configuration in
`.github/project.yml`.

## Temporary Files

Use `.plan/temp/` for ALL temporary and generated files (covered by `Edit(.plan/**)` permission — avoids permission prompts).

## Tool Usage

- Use proper tools (Edit, Read, Write) instead of shell commands (echo, cat)
- Never use Bash for file operations (find, grep, cat, ls) — use Glob, Read, Grep tools instead
