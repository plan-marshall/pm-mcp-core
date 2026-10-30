# Skills conformance audit of pm-mcp-core (main checkout, HEAD 6847c748)

Read-only. All figures come from grep / small Python scans over `src/main` and `src/test` of each module (git-tracked trees, `target/` and `.plan/` excluded). "Heuristic" marks counts built from regexes that can over- or under-count. "not measured" is stated where nothing was checked.

Modules and size (java files main / test): pm-core 327/213, pm-workflow 205/94, pm-runtime 231/171, pm-provider-git 11/14, -github 5/5, -gitlab 3/2, -http 8/3, -sonar 3/2. (`pm-providers/pm-provider-ci` is an untracked leftover directory with only `target/` and IDE files; `git ls-files` lists nothing in it. Ignored.)

Test volume (test methods, @Test+@ParameterizedTest): pm-core 1960, pm-workflow 956, pm-runtime 1653, providers 145 (git 76, github 22, gitlab 19, http 24, sonar 4).

Test dependencies in the poms (module pom `<artifactId>` scan): `cui-test-juli-logger` in core, workflow, runtime, git, github, gitlab, http (not sonar). `cui-test-generator` ONLY in pm-workflow and pm-runtime. `cui-test-mockwebserver-junit5` in pm-runtime, github, gitlab, http. `cui-test-value-objects`: in NO pom. `jspecify`: in NO pom (but jspecify 1.0.1 sits on the pm-core compile classpath transitively, via cui-java-tools: `pm-core/target` classpath file). `awaitility`, `easymock`, `assertj`, `mockito`, `hamcrest`: none.

---------------------------------------------------------------------

## 1. Skill notes (what each skill demands)

- cui-logging: CuiLogger only (`private static final CuiLogger LOGGER`), no SLF4J/Log4j/JUL/System.out; INFO/WARN/ERROR/FATAL must use a LogRecord constant of a DSL-style holder (`@UtilityClass` + nested INFO/WARN/ERROR/FATAL classes, PREFIX); DEBUG/TRACE use plain strings, never LogRecord; exception first; `%s` only; ranges INFO 1-99, WARN 100-199, ERROR 200-299, FATAL 300-399; ids "sequential within ranges"; `doc/LogMessages.adoc` complete; "all LogRecord usage must be covered by LogAsserts in tests".
- cui-testing: `cui-test-generator` mandatory for ALL test data, `@EnableGeneratorController` on test classes, `@GeneratorsSource` for parameterized tests; value objects (custom equals/hashCode, `@Value`, `@Data`, "domain data with value semantics") implement `ShouldHandleObjectContracts<T>`; not for enums, utility, infrastructure classes, builders; log tests use `@EnableTestLogger` + `LogAsserts`; Mockito/PowerMock forbidden ("use EasyMock or CUI patterns").
- cui-http-testing: HTTP tests use `@EnableMockWebServer` WITH `@MockResponseConfig` (repeatable) or `@ModuleDispatcher`/`ModuleDispatcherElement`; "do not configure MockWebServer manually"; `URIBuilder` injection, never hard-coded ports; request checks via injected `MockWebServer`.
- java-core: records for data carriers, immutability (`List.copyOf`), interface-typed collections, never catch/throw generic `Exception`/`RuntimeException`/`Throwable`, avoid volatile/double-checked locking (synchronized acceptable on Java 24+), no `Optional.get()` without check.
- java-lombok: records over `@Value`; `@Builder` for 4+ constructor parameters; `@UtilityClass` for static-method classes; no `@Data` on value types; notes its own gaps (Lombok `@NonNull` vs JSpecify).
- java-null-safety: `@NullMarked` in every `package-info.java`; `@Nullable` for nullable fields/params/record components; NEVER `@Nullable` on returns (use `Optional<T>`); NEVER `Optional<T>` for fields, parameters or record components; `Objects.requireNonNull` at API boundaries.
- javadoc: every public type/method documented; package-info with description.
- junit-core: one test class per production class (split above 400 lines by behaviour cluster); AAA without phase comments; `@DisplayName`, `@Nested` for 3+ tests, `assertAll`, `assertThrows`, messages of 20-60 chars on assertions; no `Thread.sleep` (Awaitility), no reflection; literal vs generated data by "universal vs literal-is-the-contract" discriminator; never introduce libraries without asking; 80% coverage.
- junit-integration: `*IT.java` run by Failsafe, Surefire excludes them.
- java-maintenance: not read in depth (compliance checklist/refactoring triggers); not measured.
- N/A: java-cdi, java-quarkus, junit-weld-testing (CLAUDE.md: no Quarkus, no CDI in these modules).

---------------------------------------------------------------------

## 2. Per skill area

### 2.1 cui-logging (production)  - VERDICT: conforms (all modules)

Measured over `src/main` of all 8 modules:
- slf4j/log4j/jboss/lombok-log imports: 0. `java.util.logging`: 0. `System.out/err`: 0. `printStackTrace`: 0.
- `new CuiLogger`: pm-core 3, pm-workflow 3, pm-runtime 36, providers 0. All declared `private static final CuiLogger LOGGER`; no other logger variable name (0 hits for a different name or non-private-static-final form).
- INFO/WARN/ERROR calls: core 5, workflow 5, runtime 99 (+ 5 debug). Calls with a string literal as first argument: 0. `{}` placeholders: 0. `%d/%f` placeholders: 0. `LOGGER.debug/trace` with a LogRecord: 0. Multi-line `LOGGER.x(` openers with a literal: 0.
- Exception-first: all exception-bearing calls checked have the throwable first (e.g. `RuntimeDrain.java:246`, `SubprocessJobRunner.java:1443`). The one grep hit with a trailing "cause" (`CredentialStoreChoice.java:1153`) passes a String, not a Throwable (false positive).
- Holders: `CoreLogMessages` (PM_CORE), `WorkflowLogMessages` (PM_WORKFLOW), `RuntimeLogMessages` (PM_RUNTIME), each in its own `log` package, each with `package-info`. Providers: no logging at all, so no holder (consistent with CLAUDE.md "created with its first record").
- Ranges: parsed all 106 records. Out-of-range ids: 0. Duplicate ids: 0. No FATAL records (skill allows 300-399; CLAUDE.md defines only up to 299). Gaps in the sequence: RuntimeLogMessages INFO id 24 and WARN id 104 are unused (skill says "sequential"; minor).
- doc/LogMessages.adoc: 106 identifiers in code vs 106 in the doc; set difference empty both ways (checked by id, all 106, not only 5).
- Production use of the records: 14 RuntimeLogMessages records have no use in this repository (WEB_LISTENER_*, RUNTIME_READY, HELLO_TOOL_INVOKED, CORE_TOOLS_REGISTERED ...). The holder doc says the assembly `pm-mcp-server` logs them; not verified (assembly is another repository).

### 2.2 cui-logging (test side: LogAsserts coverage)  - VERDICT: partly

Skill: "All LogRecord usage must be covered by LogAsserts in tests".
- pm-core: 5/5 records referenced in a LogAsserts test (5 test classes with `@EnableTestLogger`).
- pm-workflow: 5 records used in main, 2 never referenced in any test (`PLAN_INSTANCE_VERSION_RECORDED`, `PLAN_INSTANCE_COMPOSITION_REFUSED`); 4 classes with `@EnableTestLogger`.
- pm-runtime: 81 records used in module main; 27 never referenced in module tests by constant (e.g. `LSP_STARTED`, `SERVICE_UNIT_UNINSTALLED`, `BOUNDED_VERIFICATION_FINISHED`, `RUNTIME_DRAIN_STARTED`, `RUNTIME_DRAINED`, `PROJECT_RECOVERY_FINISHED`, `PERIODIC_SWEEP_FINISHED`, `HARNESS_SELF_UPDATE_ACCEPTED`). 33 classes with `@EnableTestLogger`, 233 `LogAsserts.assert*` calls, 109 `resolveIdentifierString()`. Caveat: a test could assert the text literally without naming the constant (32 literal "PM_...-NNN" id strings exist in runtime tests); so 27 is an upper bound for "uncovered".
- Hand-made log capture instead of LogAsserts: `JudgmentEndpointTest.java:59-80,292` (own `RecordingLogHandler` on the JUL root logger, 1 file).
- Providers: pm-provider-git has 1 `@EnableTestLogger` class; github/gitlab/http/sonar none (they log nothing).

### 2.3 cui-testing: test data generation  - VERDICT: does not conform (largest deviation)

- `Generators.*` calls in tests: pm-core 0, pm-workflow 11 (3 classes), pm-runtime 19 (9 classes), all providers 0. Total 30 calls in 12 of ~416 test classes (about 3%). Only `letterStrings` (23), `integers` (5), `longs`, `bytes`. `@GeneratorsSource`/`@TypeGeneratorSource`: 0.
- `@EnableGeneratorController`: workflow 3, runtime 10 files. pm-core and all provider poms do not even declare `cui-test-generator` (adding it = needs operator decision).
- Hand-made literals: string literals of 3+ chars in tests (non-@DisplayName, rough): core 8140, workflow 5184, runtime 5006, git 485, github 151, gitlab 90, http 124. Estimated ratio generated : literal data about 1 : 400 or worse; effectively all test data are literals.
- Counter-argument inside the skills: junit-core's discriminator says an exact literal is right where "the literal is the contract" (error codes, JSON wire formats, schema-parity, spec-defined strings). A large part of this repository (codecs, schemas, grammar, spec-derived ids) is in that class. How much would legitimately remain literal is not measured.
- `Random`/`UUID` in tests: 7 hits (`TaskProtocolInterleavingTest.java:86` seeded Random; `EnrolmentStoreTest.java:984,1131`, `SecretServiceIT.java:39`, `KeychainSecretStoreTest.java:34` UUID suffixes). `@GeneratorSeed`: 0.

### 2.4 cui-testing: value-object contracts  - VERDICT: does not conform (ambiguous scope)

- `ShouldHandleObjectContracts`: 0 uses; `cui-test-value-objects` in no pom (adding = needs operator decision).
- Skill scope: custom equals/hashCode, `@Value`/`@Data`, value semantics. Measured in main: custom `equals`/`hashCode` pairs: pm-core 7 (`ScopeTransaction.java:132`, `JsonlStoreFile.java:106`, `LoadedProfile.java:37`, `SourceFetch.java:113`, `ProfileSets.java:61` ...), pm-runtime 2, providers 1, pm-workflow 0 = 10 classes. `@Value`: 0, `@Data`: 0. Records: 367 / 208 / 229 / 31 (core/workflow/runtime/providers) = 835; records get compiler-generated equals so the skill's "custom equals" trigger does not clearly apply to them (ambiguous, see section 5).
- Existing tests do exercise equality somewhere (105 test files mention equals/hashCode), but not through the contract interface.

### 2.5 cui-http-testing (HTTP providers)  - VERDICT: partly

- All HTTP test classes use `@EnableMockWebServer`: 12 classes (runtime 8, github 2 [GitHubClientTest, InstallationTokensTest], gitlab 1, http 1). No `com.sun.net.httpserver` and no `FakeServer` left (ADR 002 done).
- `@MockResponseConfig`: 0. `@ModuleDispatcher`/`ModuleDispatcherElement`: 0. `URIBuilder` injection: 0. All 12 install a hand-written `mockwebserver3.Dispatcher` with `server.setDispatcher(new Dispatcher(){...})` (e.g. `HttpClientTest.java:68-70`, `GitHubClientTest.java:60-61`) and one (`GitHubClientTest`) also uses `enqueue`. The skill's prohibition "do not configure MockWebServer manually; use @MockResponseConfig / @ModuleDispatcher" is therefore not followed, but ADR 002 (doc/adr/002) explicitly decided "serves its canned responses from its own dispatcher".
- Non-HTTP raw sockets: `ServerSocket` in `SocketOwnerTest.java:39`, `EndpointClientTest.java:101`, `WorkerEgressProxyTest.java:98` (TCP echo, port reservation), `HttpClientTest.java:207` (free port). Not HTTP servers; outside the skill.
- pm-provider-sonar: 3 production classes, 2 tests, 4 test methods, no HTTP test, pom has neither mockwebserver nor juli. pm-provider-git: no HTTP (JGit), fine.
- Adversarial/attack-database tests from the skill: 0 (`Generators` path/header/query not used). Not required unless code reads untrusted HTTP input; applicability not assessed.

### 2.6 Forbidden libraries  - VERDICT: conforms

Mockito, PowerMock, Hamcrest, AssertJ, EasyMock, JUnit 4, TestNG: 0 files in all modules. Hand-written fakes/stubs exist (`FakeBus`, `FakeSecretService`, 28 files declaring `class Fake*/Stub*/Recording*`), which the skills accept as "CUI patterns" (no explicit skill rule on hand-made fakes).

### 2.7 junit-core conventions  - VERDICT: conforms with minor deviations

- `@DisplayName` on all but 1 test class (`ModelWaitTest.java` in pm-workflow). Display names total 2411/1174/2015/184 vs 1960/956/1653/145 test methods (nested classes also carry them). `@Nested` classes: 272 / 158 / 219 / 18. `@ParameterizedTest`: 279 / 145 / 145 / 16.
- AAA phase comments (forbidden by skill): 14 lines in pm-core (`SchemaSubsetTest.java:142,145,148`, ...), 0 elsewhere.
- `Thread.sleep` in tests: 0. Awaitility: absent (and not needed). `setAccessible`: 0. `@Disabled`: 14 (core 11 hits incl. `FindingSchemaParityTest.java:142`, `WorkflowStatusSchemaParityTest.java:63`; each carries a reason; `@DisabledOnOs` counted in).
- Assertion messages: skill wants a message on assertions. Parsed all top-level `assertTrue/False/Equals/NotNull/Null/Same/Throws/InstanceOf/DoesNotThrow` calls: with trailing message: core 455/4807 (9%), workflow 139/2726 (5%), runtime 651/5028 (13%), providers 23/500 (5%). `assertAll` is widely used (1207/605/1209/38), `assertThrows` too.
- Test classes over the 400-line budget: core 25 of 181, workflow 23 of 71, runtime 24 of 143, git 1.
- One test class per production class (skill rule), name-matching heuristic (`<Name>*Test`): production classes without a matching-named test: core 152/307, workflow 125/183, runtime 85/211, git 2/10, http 4/7. Of these, never referenced by simple name in any test of the module: core 21, workflow 21, runtime 15 (e.g. `SkillResidency`, `ModelCatalogueCodec`, `RemediationClass`, `RestingWait`, `FuzzReport`, `RuntimeTokenFile`, `WebState`). Tests are organised by behaviour, not 1:1; indirect coverage is not measured (the build enforces 80%).
- Integration tests: 11 `*IT.java` in pm-core / pm-runtime with Failsafe plugin configured in both poms (junit-integration conforms; Surefire excludes not verified line by line).
- System.out in tests: 2 fixture files (`FixtureHarness.java:199`, `FixtureLanguageServer.java:79`), deliberate stdout protocol of a fixture process.

### 2.8 java-null-safety  - VERDICT: does not conform (all modules)

- `@NullMarked` in `package-info.java`: 0 of 67 production package-infos (core 20, workflow 22, runtime 20, providers 1 each x 5 = 5). JSpecify imports in main: 0. `@Nullable`/`@NonNull` (any): 0.
- Null contracts are carried by `Optional` and `Objects.requireNonNull` instead: `requireNonNull` 1212 (core), 512 (workflow), 828 (runtime), 40 (providers) = 2592 sites.
- `return null;` sites in main: core 29, workflow 25, runtime 23, providers 2 = 79 (un-annotated nullable returns; the skill wants Optional).
- `Optional` as field, parameter or record component (skill: never): typed fields: core 10, workflow 16, runtime 12, providers 17; parameters/record components (heuristic regex, includes a few generic `Function<..Optional<>>` hits): core 74, workflow 81, runtime 54, providers 15 = ~224. Examples: `ConsentGate.java:93` (`record Request(ConsentKind kind, Optional<String> projectId, ...)`), `ConsentGate.java:137` (`record Decision(boolean runs, Optional<Consent> toRecord)`), `ConsentGate.java:165` (`Optional<String> deviceId` parameter), `JobEnvironmentComposer.java:152` (two Optional parameters).
- Null as a state is used without annotation, e.g. `FencedGenerations.java:208` (`List<Fence> fences = null;`).
- Availability: jspecify 1.0.1 is already on the compile classpath of pm-core (transitive via cui-java-tools), so `@NullMarked` needs no new dependency for modules reaching it; an explicit `org.jspecify:jspecify` declaration would be a pom change = needs operator decision. Whether the static check (NullAway, Error Prone, IDE) exists: not measured.

### 2.9 javadoc / package-info  - VERDICT: conforms

- Packages with production code: every one has a `package-info.java` (computed over all dirs with `.java` files in `src/main/java`; missing: none). Count of package-info: core 20, workflow 22, runtime 20, git/github/gitlab/http/sonar 1 each. (ADR 008 documents package-infos of type-less packages.)
- Public types without a Javadoc block directly above (heuristic, annotations skipped): core 1/660, workflow 0/231, runtime 1/367, providers 0/38. Both hits are false positives (a `//` comment between Javadoc and annotation: `JsonlStoreFile.java:177`, `SubprocessJobRunner.java:169`). Public methods without Javadoc (non-@Override, heuristic): 3 in runtime (`JobEnvironmentComposer.java:152`, `JobConfinementComposer.java:94,140`; the same comment-between pattern is likely), 0 elsewhere.
- Test packages: no `package-info.java` in `src/test` (core 0, runtime 0, workflow 1, git 1); nothing requires it.
- Javadoc build errors/warnings: not measured (compiler runs failOnWarning; not run here).

### 2.10 java-core / java-lombok / java-maintenance  - VERDICT: java-core conforms with one deviation, java-lombok partly, java-maintenance not measured

java-core:
- Generic exceptions: `throw new Exception/RuntimeException/Throwable/Error`: 0 in all modules. `throws Exception/Throwable`: 1 (`MacSecurity.java:305`, `T call() throws Throwable`). Generic catches, all in pm-runtime, 17 sites: `catch (RuntimeException _)` 7 (`WeightsReader.java:81`, `AnswerSchema.java:126`, `EndpointHarness.java:143`, `EndpointVerification.java:222,255,282`, plus `WorkerSupervisor.java:318`), `catch (RuntimeException | Error ...)` 6 (`SubprocessJobRunner.java:1118,1164,1406,1415,1441,1720`), `catch (Throwable t)` 4 (`MacSecurity.java:313`, `Posix.java:38`, `KillBinding.java:72`, `SystemProcessExecutables.java:164`). 6 of them carry an OpenRewrite `/*TODO: Catch specific not RuntimeException ...` marker. pm-core, pm-workflow, providers: 0.
- Immutability: records 835 total; `List/Set/Map.copyOf` 409 sites; `Collections.unmodifiable*` 64 core, 43 workflow, 15 runtime, 1 providers (views rather than copies, e.g. `RoleAssignmentProposal.java:104`; some wrap a fresh copy, `ActivityEventMapping.java:51`).
- Concurrency: `volatile` 8 (runtime), `synchronized` 61 runtime / 10 workflow; skill allows `synchronized` on Java 24+ (baseline is Java 25, ADR 001); the 8 `volatile` were not individually checked against "single shutdown flag only".
- `Thread.sleep` in main: 1 (`SubprocessJobRunner.java:1219`).
- `@SuppressWarnings`: 6 core, 2 workflow, 32 runtime, all but 1 Sonar `java:S...` rules (S1181 x8, S2142 x5, S3776 x4, S3077 x4, S5443 x3, S107 x3 ...). `// TODO` markers: 6 in runtime, all the generic-catch markers above.
- Old-style `instanceof` cast: core 11, runtime 6 (heuristic).

java-lombok:
- `@UtilityClass`: core 49, runtime 26, providers 2, workflow 0. Classes that are static-only with a hand-written `private X()` constructor instead (heuristic scan: no instance members): core 22 (`SecretRedactor.java:31`, `EarsForm.java:34`, `RoleSetLoader`, `RoleAssignmentValidation`, `ClarifiedRequestRenderer`, `OutlineRenderer` ...), workflow 32 (`HeaderSchemaCheck`, `InstancePruner`, `PlanProcessStates`, `HeaderJson`, `CompositionRuleEvaluator`, `WorkflowLexer` ...), runtime 7 (`AnswerSchema`, `FreeTextDerivation`, `BuildDaemonStore`, ...), git 3 (`GitIdentity`, `LinkedWorktrees`, `WorktreeShaDigest`), sonar 1 (`SonarProvider`) = ~65 candidates.
- `@Builder`: core 16, runtime 1, others 0. Records with 4+ components: core 123, workflow 64, runtime 49, providers 15 = 251, of which 16 carry `@Builder`; the skill table says "4+ constructor parameters: @Builder". Largest: `Finding` 38 components, `SupervisorEnvironment` 22, `FinalizeStepDescriptor` 21. Records with 7+ components: 49 / 16 / 16 / 4.
- `@Getter` 4, `@Value`/`@Data`/`@Setter`/`@Delegate`/`@SneakyThrows`: 0. Lombok `@NonNull`: 0.
- pm-workflow's pom has no `lombok` dependency at all (core, runtime, providers github/gitlab/git/http have it; sonar has none either), so 32 workflow utility classes cannot use `@UtilityClass` without a pom change = needs operator decision.

java-maintenance: not measured.

---------------------------------------------------------------------

## 3. Per module verdicts

Legend: C = conforms, P = partly, N = does not.

| Area | pm-core | pm-workflow | pm-runtime | git | github | gitlab | http | sonar |
|---|---|---|---|---|---|---|---|---|
| cui-logging prod | C | C | C | C (no logging) | C (none) | C (none) | C (none) | C (none) |
| log test coverage (LogAsserts) | C (5/5) | P (3/5) | P (about 54/81) | C (n/a) | n/a | n/a | n/a | n/a |
| cui-testing generators | N (0) | N (3 classes of 71) | N (9 of 143) | N | N | N | N | N |
| value-object contracts | N | N (no custom equals) | N | N | N | N | N | N |
| cui-http-testing | n/a | n/a | P (8 classes, own Dispatcher) | n/a | P | P | P | N/untested (no HTTP test, 4 tests) |
| forbidden libs | C | C | C | C | C | C | C | C |
| junit-core style | C (14 AAA comments, 9% assert msgs) | C | C | C | C | C | C | P (4 tests) |
| null safety (JSpecify) | N | N | N | N | N | N | N | N |
| javadoc / package-info | C | C | C | C | C | C | C | C |
| generic Exception/Throwable | C | C | N (17 catches, 1 throws) | C | C | C | C | C |
| lombok | P (22 hand utility classes; 123 big records) | N (no Lombok dep, 32 utility classes) | P (7 utility classes) | P (3) | C | C | C | P (no Lombok dep, 1) |

---------------------------------------------------------------------

## 4. Deviations ranked by size

| # | Deviation | Count | Tag |
|---|---|---|---|
| 1 | Test data are literals, not `cui-test-generator` (12 of ~416 test classes use `Generators`, 30 calls; pm-core and the providers lack the dependency) | ~404 classes, ~19k string literals | needs decision (dependency in pm-core and 5 provider poms = operator decision; how much stays literal per the junit-core discriminator = design decision) |
| 2 | No JSpecify: 0 of 67 production package-infos `@NullMarked`; 0 `@Nullable`; 79 `return null;` sites, ~224 `Optional` parameters/record components and 55 `Optional` fields in conflict with the skill | 67 package-infos + ~358 sites | needs decision (adding `@NullMarked` itself is mechanical, jspecify is already on the classpath, but the Optional-in-records/params policy and an explicit dependency declaration need a decision) |
| 3 | `@Builder` rule: records and classes with 4+ parameters without builder | 235 records (251 with 4+ components, 16 with `@Builder`) | needs decision (skill vs records; CLAUDE.md prefers records) |
| 4 | Hand-written static-only classes instead of `@UtilityClass` | ~65 classes (core 22, workflow 32, runtime 7, git 3, sonar 1) | mechanical for core/runtime/git (Lombok present); pm-workflow (32) and sonar (1) need a Lombok dependency = needs decision |
| 5 | No value-object contract tests | 10 classes with custom equals/hashCode (+ 835 records if the skill is read broadly) | needs decision (dependency `cui-test-value-objects` + scope of "value object") |
| 6 | HTTP tests configure `mockwebserver3.Dispatcher` by hand, no `@MockResponseConfig`/`@ModuleDispatcher`/`URIBuilder` | 12 test classes | needs decision (ADR 002 chose own dispatchers; skill says annotations) |
| 7 | Log records without a LogAsserts check | workflow 2, runtime up to 27 (upper bound, constant not referenced in tests) | mechanical |
| 8 | Generic catches in production code | 17 sites in pm-runtime (7 RuntimeException, 6 RuntimeException\|Error, 4 Throwable) + 1 `throws Throwable` | needs decision per site (several are last-resort handlers of the job runner; OpenRewrite TODO markers exist at 6) |
| 9 | Assertions without message | about 91% of ~13,000 assertions (core 4352, workflow 2587, runtime 4377, providers 477 without) | mechanical but very large and low value; arguably a skill-vs-practice tension |
| 10 | Test classes over the 400-line budget | 73 (core 25, workflow 23, runtime 24, git 1) | mechanical (split by behaviour) |
| 11 | `@Disabled` tests | 14 (each with reason) | needs decision (schema parity tests waiting on the spec) |
| 12 | AAA phase comments in tests | 14 lines in pm-core | mechanical |
| 13 | `RecordingLogHandler` instead of LogAsserts | 1 (`JudgmentEndpointTest.java`) | mechanical |
| 14 | Gaps in log id sequences | 2 (RuntimeLogMessages INFO 24, WARN 104) | needs decision (ids may be reserved/retired; renumbering breaks docs/traceability) |
| 15 | `ModelWaitTest` without `@DisplayName` | 1 | mechanical |
| 16 | `Collections.unmodifiable*` views where copies are expected | 123 sites (heuristic, some wrap copies already) | needs decision (per-site review) |
| 17 | pm-provider-sonar: 4 tests, no HTTP test, no juli/mockwebserver dependency | 1 module | needs decision (module is a stub with 3 classes) |

---------------------------------------------------------------------

## 5. Ambiguities and tensions between skills and CLAUDE.md

1. Generated vs literal data: cui-testing says generators are mandatory for ALL test data and every class gets `@EnableGeneratorController`; junit-core says generated data only for universal contracts and exact literals where the literal is the contract. CLAUDE.md just says "cui-test-generator for test data" and its test commands list pm-core without the dependency present in the pom. The two skills disagree on scope.
2. Value objects: cui-testing demands `ShouldHandleObjectContracts` for classes with custom equals/hashCode or `@Value`/`@Data`; java-lombok says use records instead of `@Value`. 835 records have compiler-generated equals, which the skill does not name. Unclear whether records are "value objects" for the contract test. CLAUDE.md is silent.
3. java-lombok: "immutable data carrier = record" and "4+ constructor parameters = @Builder" collide for records with 7 to 38 components; the skill pairs `@Builder` with records but a 38-component record is arguably a design smell, not a builder case. CLAUDE.md says "prefer records" and lists `@Builder`.
4. java-null-safety: "no Optional in parameters/record components" vs 224+ such uses; CLAUDE.md does not mention JSpecify at all. Also the Lombok skill itself records as a "known gap" the choice between Lombok `@NonNull` and JSpecify.
5. cui-http-testing vs ADR 002: the skill forbids manual MockWebServer configuration and wants `@MockResponseConfig`/`@ModuleDispatcher`; ADR 002 (accepted, operator rule) prescribes `@EnableMockWebServer` plus the class's own dispatcher without a shared helper. The ADR states "Rationale not recorded".
6. cui-testing says Mockito/PowerMock forbidden, "use EasyMock"; CLAUDE.md forbids Mockito, PowerMock, Hamcrest and adds no EasyMock; the repository uses hand-written fakes (28 files) and no EasyMock; junit-core forbids introducing libraries without asking. The hand-written-fake route is not addressed by any skill.
7. cui-logging allows FATAL 300-399 and says ids "sequential"; CLAUDE.md defines only INFO/WARN/ERROR ranges and "next free identifier", which tolerates gaps (RuntimeLogMessages 24 and 104). The skill says holder docs at `doc/LogMessages.adoc`; CLAUDE.md puts one repository-level file there (consistent).
8. junit-core: "one test class per production class" vs the repository's behaviour-clustered tests (152/307 core classes have no name-matching test); the 80% coverage gate (CLAUDE.md) is the actual rule enforced.
9. java-core says avoid `volatile`/`synchronized` on Java 21-23 and allows `synchronized` on 24+; the baseline is Java 25 (ADR 001), so 71 `synchronized` sites are acceptable per skill.
10. Skill demands messages of 20-60 characters on every assertion; CLAUDE.md demands "AAA" only. 91% of assertions lack a message; fixing that is mechanical but the value is questionable.
11. Dependencies: adding `cui-test-generator`, `cui-test-value-objects`, `jspecify`, `lombok` (pm-workflow, sonar) to poms is forbidden by CLAUDE.md without asking; marked "needs decision" throughout.

Not measured: java-maintenance compliance checklist; Javadoc tool errors/warnings; whether `@EnableTestLogger` is present on every test class that triggers a logging production path; per-site justification of the 8 `volatile` fields; adversarial/attack-database HTTP tests applicability; SonarCloud findings; the assembly repository (pm-mcp-server) which consumes the 14 unused-here RuntimeLogMessages records.
