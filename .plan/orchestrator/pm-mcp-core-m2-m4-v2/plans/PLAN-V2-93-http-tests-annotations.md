# PLAN-V2-93: Convert the twelve HTTP test classes to @MockResponseConfig / @ModuleDispatcher and add the first HTTP test of pm-provider-sonar

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-93-http-tests-annotations.md` and is queued as one row file, `queue/PLAN-V2-93.json`,
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

Rules that apply to this plan: R02, R03, R04, R06, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R05, R07, R09, R10, R12 to R16: no behaviour change.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none; ADR directory: `doc/adr/` (repository-wide); layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

All twelve HTTP test classes use `@EnableMockWebServer` but install a hand-written `mockwebserver3.Dispatcher`; `cui-http-testing` forbids configuring the server by hand and prescribes `@MockResponseConfig` or `@ModuleDispatcher` with an injected `URIBuilder`. The accepted ADR `doc/adr/002` chose the own dispatcher ("rationale not recorded"). The operator chose on 2026-10-30 to migrate and to supersede that ADR. `pm-provider-sonar` has no HTTP test at all.

## Deliverables

1. **Supersede ADR 002.** A new ADR (next free number of `doc/adr/`, R11) records that the HTTP tests configure the server through `@MockResponseConfig` / `@ModuleDispatcher` and a `URIBuilder`, why the own dispatchers of ADR 002 are given up (the skill's rule; the ADR had recorded no rationale), and what a test does that needs a stateful dispatcher (a `ModuleDispatcherElement` of its own, still the annotation route). ADR 002's status becomes Superseded with a pointer; the index `doc/adr/README.adoc` follows.
2. **Convert the twelve classes (test first: the classes are their own guard).** `GitHubClientTest`, `InstallationTokensTest` (github), `GitLabClientTest` (gitlab), `HttpClientTest` (http), `HuggingFaceSourceRepositoryTest`, `LfsBatchClientTest` (runtime `corpus`), `EndpointClientTest`, `EndpointHarnessTest`, `EndpointVerificationTest`, `JudgmentEndpointRemoteTest`, `JudgmentEndpointTest`, `JudgmentVerificationTest` (runtime `endpoint`). Each loses its `setDispatcher(new Dispatcher(){...})`, takes its canned responses from `@MockResponseConfig` (repeatable) or a `ModuleDispatcherElement`, gets its base URI from the injected `URIBuilder`, and checks requests through the injected server. The TCP echo and port reservation tests (`SocketOwnerTest`, `WorkerEgressProxyTest`, `HttpClientTest` line 207 for a free port) are not HTTP servers and stay.
3. **Generated data and log assertions in the same classes.** Test data of the twelve classes come from `cui-test-generator` (the exemption of R20 for wire formats applies: a canned JSON body is a contract); `JudgmentEndpointTest` and `JudgmentEndpointRemoteTest` replace their hand-made `RecordingLogHandler` on the JUL root logger by `@EnableTestLogger` and `LogAsserts`.
4. **The first HTTP test of `pm-provider-sonar`.** The module has three production classes and four tests and none uses HTTP. Add a test through the annotations for each request the production code sends (read the three classes at the outline; if none sends a request, say so in the landing and add the test for the one that reads a response). The test dependency `cui-test-mockwebserver-junit5` comes from PLAN-V2-83.

**Done when:** `git grep -n 'setDispatcher\|new Dispatcher' -- '*/src/test/java/*.java'` finds nothing; `git grep -c '@MockResponseConfig\|@ModuleDispatcher'` covers the twelve classes; no hand-made log handler remains in the module tests; `pm-provider-sonar` has an HTTP test; ADR 002 is Superseded and the new ADR is written; the test method count of every class is the same or higher. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `cui-http-testing` (SKILL.md and standards: `@EnableMockWebServer`, `@MockResponseConfig`, `@ModuleDispatcher`, `URIBuilder`); `cui-testing`; R03, R04, R20; ADR `doc/adr/002`; the audit `skills-audit-2026-10-30.md` § 2.5.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Test code only.** No production class changes (a production change this plan finds necessary is recorded in the landing message and left to the owning plan); no assertion is weakened or removed to make a conversion pass, and the number of test methods per class is the same or higher.
- **Generated data, with the exemption of the rule R20:** a literal stays where it is the contract (a wire format or JSON document under test, an error or reason code, a schema or specification string, a golden file, a path of a fixture). Every class of the plan ends with `@EnableGeneratorController` and at least one `Generators.*` call, or with a one-line comment above the class saying that every datum is a contract; the pull request body lists per class the number of generator calls, the number of literals kept and the category of each kept literal.
- **Seeds:** a failing generated test must be reproducible: the lane uses the seed reporting of `cui-test-generator` (`@GeneratorSeed` only on a test that needs a fixed value), and never `Random` or `UUID.randomUUID()` for test data.
- **No production change:** the HTTP clients are not touched; a client defect the conversion shows is reported through the inbox message.
- **Adversarial tests** of the skill (path, header and query attack databases) are out of scope unless a client reads untrusted HTTP input; the plan states in the landing whether any does.

## Claim Labels

- OBSERVED: the twelve classes use `@EnableMockWebServer` and a hand-written `Dispatcher`; `@MockResponseConfig`, `@ModuleDispatcher`, `URIBuilder`: 0 uses; `com.sun.net.httpserver` and `FakeServer`: none (ADR 002 done) (`skills-audit-2026-10-30.md` § 2.5)
- OBSERVED: `JudgmentEndpointTest.java` (lines 59 to 80 and 292) and `JudgmentEndpointRemoteTest.java` define their own `RecordingLogHandler`
- OBSERVED: `pm-provider-sonar` has 3 production classes, 2 tests (4 methods) and neither mockwebserver nor juli in its POM
- HYPOTHESIS: a handful of the canned dispatchers keep state between requests (a login, then a call); they become a `ModuleDispatcherElement` — find them at the outline
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/GitHubClientTest.java`
- OBSERVED: `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/InstallationTokensTest.java`
- OBSERVED: `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/GitLabClientTest.java`
- OBSERVED: `pm-providers/pm-provider-http/src/test/java/de/planmarshall/provider/http/HttpClientTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/corpus/HuggingFaceSourceRepositoryTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/corpus/LfsBatchClientTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointClientTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointHarnessTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointVerificationTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/JudgmentEndpointRemoteTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/JudgmentEndpointTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/JudgmentVerificationTest.java`
- HYPOTHESIS: `pm-providers/pm-provider-sonar/src/test/java/` — the first HTTP test (new file)
- HYPOTHESIS: `doc/adr/NNN-http-tests-use-the-annotations.adoc` (new)
- OBSERVED: `doc/adr/README.adoc` (index row, additive)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (the `cui-test-mockwebserver-junit5` dependency for `pm-provider-sonar`, the generator dependency for the providers) and PLAN-V2-84.
- Disjoint from PLAN-V2-89 to PLAN-V2-92 by construction: they exclude these twelve classes.
- Overlaps with PLAN-V2-12 (`ci-contract-module`) and PLAN-V2-62 (the CI contract) only if they edit the provider HTTP tests; the orchestrator pairs by class.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-93-http-tests-annotations.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
