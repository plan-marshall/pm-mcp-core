# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## Project

`pm-mcp-core` holds the plain-Java engine of plan-marshall-mcp (PM-MCP): the foundation `pm-core`, the provider
modules under `pm-providers`, and `pm-runtime`, the daemon's logic that needs no framework. The Quarkus assembly
`pm-mcp-server` that wires these modules into the daemon `pm-mcpd`, and all model-facing content, live in
`plan-marshall/plan-marshall-mcp`; the client contract `pm-api` and the client binaries in
`plan-marshall/pm-mcp-clients`.

The only listing of the repositories, the modules, their responsibilities and allowed dependencies is the Module
Structure Specification of the product (`doc/specification/module-structure.adoc` in
`plan-marshall/plan-marshall-mcp`, later in `plan-marshall/plan-marshall-documentation`); name modules from there and
never repeat the listing here. All project documentation (requirements, specifications, implementation watch,
roadmap, concept, developer and user documentation) lives there, not in this repository. Every concrete
implementation follows the project skill `traced-implementation`, which lives there with `doc-review`; the skills of
the same names here only point to them.

## Build

- Compile: `./mvnw compile`
- Quality gate: `./mvnw verify -Ppre-commit` (rewrites files: licence headers, OpenRewrite recipes, import order;
  review every resulting diff and commit it, then run the full verify again)
- Full verify: `./mvnw verify`
- Coverage: `./mvnw verify -Pcoverage` (minimum 80% instruction and branch coverage per module)
- Integration tests: `./mvnw verify -Pintegration-tests`
- This repository builds no binary. Native-image metadata of a class travels with its module
  (`META-INF/native-image/de.planmarshall/<name>/`); a change to it is verified by the native build of `pm-mcpd` in
  plan-marshall-mcp against the deployed `SNAPSHOT`.
- Always build and test through Maven and JUnit; never run `javac` directly or write ad-hoc verifier classes.
- The compiler runs with `failOnWarning`: fix deprecations and warnings, don't suppress them.
- `.mvn/maven.config` passes `.mvn/settings.xml` (the organisation's package registry, no token) as global
  settings; the token is the server `plan-marshall` of `~/.m2/settings.xml`.

## Dependencies and Versions

- Parent `de.planmarshall:pm-mcp-parent`, resolved from the organisation's registry. It supplies the Java release,
  the managed third-party versions, the plugin management, the quality-gate recipes and the deployment target.
- `pm-api` comes from `pm-mcp-clients` as a `SNAPSHOT`, named by the property `version.pm-mcp-clients` in the root
  POM; a change to it is a pull request there, and its merge deploys the `SNAPSHOT` this repository builds against.
- Never add a dependency or a plugin without asking the user first.
- Pre-1.0: no deprecation cycles, no backward-compatibility shims.

## Dependency Rules (enforced by the build)

The enforcer executions of the root POM fail the build for a violation; `BuildGuardsIT` of `pm-core` proves each of
them with a fixture project below `src/guard-controls`:

- No module depends on Quarkus, CDI, Vert.x or an MCP library, nor on `pm-mcp-server` or a client binary module.
- `pm-core` depends on `pm-api` alone among the modules of the product, and never on JGit or `cui-http`.
- A provider module depends on `pm-core` and, for the shared HTTP base, on `pm-provider-ci`; the contract modules
  `pm-provider-git` and `pm-provider-ci` on no other provider module.
- `pm-runtime` depends on `pm-core` and `pm-api` alone among the modules of the product, and it is the only module
  that depends on `commonmark` and LSP4J.
- No module carries model-facing content: nothing below `workflows/`, `roles/`, `bundles/` or `skills/` in a JAR.
  The modules read such content by its classpath location; test fixtures are written for the test, never copied
  from the content of the product.

## Code Standards

- Java 25, Lombok (`@UtilityClass`, `@Value`, `@Builder`), prefer records, `var` for obvious types, final fields,
  package-private over public where possible. No Quarkus, no CDI: a class that needs a framework type belongs in
  `pm-mcp-server`.
- Every package has a `package-info.java` with Javadoc; every public type is documented.
- Never catch or throw generic `Exception`/`RuntimeException` in production code.
- Logging: `private static final CuiLogger LOGGER = new CuiLogger(X.class);` (cui-java-tools), `%s` placeholders,
  exception first; no slf4j, log4j, `System.out`/`System.err`. INFO/WARN/ERROR messages are `LogRecord` constants in
  `PmMcpLogMessages` of `pm-runtime` (prefix `PM_MCP`), each documented in `doc/LogMessages.adoc` of the
  documentation.
- JUnit 5 only (`@DisplayName`, `@Nested`, AAA, `@ParameterizedTest` for 3+ variants), on the JVM without Quarkus.
  Forbidden: Mockito, PowerMock, Hamcrest. Test data: cui-test-generator; log assertions: cui-test-juli-logger.
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

## Git Workflow

`main` is protected by rulesets and merges go through the merge queue; direct pushes to `main` are not allowed.
Branch, commit, push, open a pull request, wait for the checks, answer and resolve every review comment. Do not
merge without the user's word. Commits end with `Co-Authored-By: plan-marshall <noreply@cuioss.de>`.

CI: reusable workflows of `cuioss/cuioss-organization`, pinned by full SHA with a version comment; configuration in
`.github/project.yml`.
