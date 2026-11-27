# PLAN-V2-204: The HTTP tests: one routing shape, the static answers on the annotation, the Sonar boundaries pinned, the log records asserted and the hand-made handler gone

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: PLAN-V2-126, deliverable 3 (the operator narrowed 126 to D1 on 2026-11-25). Operator decisions kept (landing of 126): D3(a) a shared routing helper in `pm-provider-http:tests`, overriding the "no helper" default; `CLAUDE.md` and `.opencode/rules/plan-marshall-standing-rules.md` edits allowed; four PRs per spec.
lane: plan-marshall

> Staged plan spec, one shippable unit of work (one PR), ready for `/plan-marshall` hand-off. SELF-SUFFICIENT together with the standing rules.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R04, R05, R06, R11, R16, R19, R20 (the drafter's candidates, confirmed by the orchestrator against the rules file at staging; an id the lane finds not applicable it says so).

## Objective

Twelve test classes each hold a hand-written `new ModuleDispatcherElement` with the same `routes`/`on`/`response`/`json` skeleton. Seven of them use no `@MockResponseConfig`, although ADR 017 says a static response goes through it. `SonarClientTest` draws outside the seed and leaves the page and pull-request boundaries unasked. `EndpointHarnessTest` asserts none of the records `EndpointHarness` logs. `SubprocessJobRunnerTest.LoggedWatch` is a hand-made log handler. Documents still cite ADR 002 as live. This plan converts the static answers, adds the shared helper the operator chose, pins the Sonar edges, asserts the records, and corrects the documents.

## Deliverables

1. **The helper and the decisions, first.** A shared routing helper in `pm-provider-http:tests` for `-github`, `-gitlab`, `-sonar` and `-http`.
   - It widens the contents rule of that JAR (`testing/generators/**` only, CLAUDE.md § Generator Artifacts, ADR 012). Amend ADR 012, ADR 017 (Alternatives Considered, which rejected a shared helper) and CLAUDE.md. The `pm-provider-http/pom.xml` `includes` (`:99-100`) widens. `pm-provider-sonar` declares no `pm-provider-http` test-jar today and needs it (named by CLAUDE.md).
   - The five runtime classes cannot take the helper: `pm-runtime` depends on `pm-core` and `pm-api` alone, and `pm-core` has no `cui-test-mockwebserver-junit5` dependency. ASK-AT-REFINE (R07): a module-local helper in the `pm-runtime` test tree (recommended, about 60 % sure; note that `pm-runtime:tests` then publishes it) or the five classes keep their skeleton.
   - The helper has its own test.
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java-cui:cui-http-testing
   - Persona: implementer
2. **Routing converted.** Static answers go to `@MockResponseConfig`; a `ModuleDispatcherElement` stays for generated, paged or request-dependent answers; the PR body lists per class which answers are which.
   - Counts at 3ba79c4f: `new ModuleDispatcherElement` in 12 classes, one each. `@MockResponseConfig` in `GitHubCiProviderTest` (60), `GitLabCiProviderTest` (40), `HttpClientTest` (5), `GitLabClientTest` (3), `GitHubClientTest` (2). Seven classes have none: `InstallationTokensTest`, `SonarClientTest`, `HuggingFaceSourceRepositoryTest`, `LfsBatchClientTest`, `EndpointClientTest`, `EndpointHarnessTest`, `EndpointVerificationTest`.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java-cui:cui-http-testing
   - Persona: module-tester
3. **The Sonar boundaries.** Move `SonarClientTest.unreadablePages()` (`:416`, a static `@MethodSource`) under the seed. Pin `total == 500` (one full page, no second request), `total == MAX_PAGES * PAGE_SIZE` (complete at page 20) and one above it. Pin pull request 0 and 1 (`SonarClient.java:88`, `pullRequest < 1`; the draws at `SonarClientTest:122` and `:232`).
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
4. **The records and the handler.**
   - Assert `WORKER_LAUNCH_REFUSED` (`EndpointHarness.java:79,87`), `ENDPOINT_ATTEMPT_ENDED` (`:94`) and `ENDPOINT_NOT_READY` (`:116`, already asserted by `EndpointHarnessReadinessTest`) by their constants under `@EnableTestLogger` in `EndpointHarnessTest`.
   - Replace `SubprocessJobRunnerTest.LoggedWatch extends Handler` (`:234`) by `LogAsserts`, keeping an await on the asynchronous record (an await on the assertion, not a handler).
   - Skills: pm-dev-java-cui:cui-logging, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
5. **Documents.** Cite ADR 017 where `provider-github.adoc:233`, `provider-gitlab.adoc:223`, `provider-http.adoc:103,142` and `runtime-corpus.adoc:107` cite ADR 002 as live. Correct the future tense "PLAN-V2-93 supersedes" in ADR 010 (`:38`, `:93`, `:132`), `CLAUDE.md:206` and `.opencode/rules/plan-marshall-standing-rules.md:210`. Amend ADR 017's Context (`HttpClientTest`, `GitHubClientTest`, `GitLabClientTest` and `LfsBatchClientTest` already keyed on method and target before PLAN-V2-93) by the repository's amendment convention in `doc/adr/README.adoc`, or write a new ADR if deliverable 1 changes the decision. Number as the next free number of `doc/adr/` at the head of main when the outline is written.
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter

**Done when:**
- Deliverables 1 to 5.
- The number of `new ModuleDispatcherElement` and of classes without `@MockResponseConfig`, before and after, is in the PR body with the per-class reason.
- The Sonar edges fail against a scratch client that reads one page too many, one too few, or accepts pull request 0 (log ids). A scratch change of each of the four asserted records fails its test.
- `extends Handler` over `src/test` is empty. The dispatcher grep `setDispatcher|new Dispatcher|extends Dispatcher` is empty at 3ba79c4f except in ADR 017's prose, and stays so.
- The suites keep their test counts per class as at 3ba79c4f.
- Build: `verify -Ppre-commit`, `verify` of `pm-provider-http`, `-github`, `-gitlab`, `-sonar` and `pm-runtime`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am`. Use per-module runs with log ids if a full run exceeds the host's bound.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).

## Standing Constraints

- One PR for this spec (operator: four PRs per spec; this is the D3 PR). Under 100 files.
- HTTP tests use `@EnableMockWebServer` with `@MockResponseConfig` or `@ModuleDispatcher` and an injected `URIBuilder`; no hand-written `Dispatcher` (CLAUDE.md).
- The `pm-provider-http:tests` JAR keeps a stated contents rule after the widening; ADR 012 and CLAUDE.md say it in the same PR.
- No production HTTP code changes. No new third-party dependency; the only POM additions are the `test-jar` of the repository's own module that CLAUDE.md names.
- Read every cited line at the head first and drop what a merge settled (name the commit).
- A converted test is accepted when a scratch mutation of the thing it names fails it, not when a grep is empty.
- Pre-1.0: no shim. CLAUDE.md test rules apply. Sonar dismissal only on the operator's selection, through `sonar_rest transition`.

## Claim Labels

- OBSERVED: the counts of deliverable 2, read at 3ba79c4f with `git grep -c`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: git grep -c: 'new ModuleDispatcherElement' 1 in each of 12 classes (GitHubCiProvider, GitHubClient, InstallationTokens, GitLabCiProvider, GitLabClient, HttpClient, SonarClient, HuggingFace, LfsBatch, EndpointClient, EndpointHarness, EndpointVerification tests); @MockResponseConfig 60/40/5/3/2 in GitHubCi/GitLabCi/HttpClient/GitLabClient/GitHubClient tests; the other seven have none.
- OBSERVED: `SonarClient.MAX_PAGES = 20` (`:54`), `PAGE_SIZE = 500` (`:51`), the loop at `:94`, and `pullRequest < 1` at `:88`. `SonarClientTest`: `readsTwoPages` `:255`, `stopsAtTheCap` `:277` (total at `:279`), `unreadablePages` `:416`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: SonarClient.java:51 PAGE_SIZE=500, :54 MAX_PAGES=20, :88 'if (pullRequest < 1)', :94 loop; SonarClientTest readsTwoPages :255, stopsAtTheCap :277 (total :279), unreadablePages :416; draws at :122 (integers(1,100000)) and :232 (integers(-100,0)).
- OBSERVED: `pm-provider-http/pom.xml:99-100` includes only `de/planmarshall/provider/http/testing/generators/**`, `pm-core/pom.xml:144-148` includes generators, assertions and `SchemaResource`, and `pm-core/pom.xml` has no mockwebserver dependency (`pm-runtime/pom.xml:96` has it).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: pm-provider-http/pom.xml:99-100 includes only .../http/testing/generators/**; pm-core/pom.xml:144-148 includes generators, assertions, SchemaResource; grep mockwebserver: pm-core/pom.xml none, pm-runtime/pom.xml:96 present.
- OBSERVED: `pm-provider-sonar/pom.xml` declares `pm-provider-http` (`:28`) but no test-jar of it.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: pm-provider-sonar/pom.xml:28 declares pm-provider-http without type test-jar; its only test-jar is pm-core (:37-38).
- OBSERVED: ADR 002 is cited at `provider-github.adoc:233`, `provider-gitlab.adoc:223`, `provider-http.adoc:103,142`, `runtime-corpus.adoc:107`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: ADR 002 (doc/adr/002-cui-http-...) linked at provider-github.adoc:233, provider-gitlab.adoc:223, provider-http.adoc:103 and :142, runtime-corpus.adoc:107, all as repository ADR 002 without a superseded mark.
- OBSERVED: `SubprocessJobRunnerTest.java:234` `LoggedWatch extends Handler` is the only `extends Handler` outside prose.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: git grep 'extends Handler' outside .plan: only pm-runtime/.../job/SubprocessJobRunnerTest.java:234 'private static final class LoggedWatch extends Handler'.
- HYPOTHESIS: ADR 017's Context says a dispatcher "answers whatever request reaches it". Confirm at `doc/adr/017-http-tests-through-mock-response-config-and-module-dispatcher.adoc`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: doc/adr/017-http-tests-through-mock-response-config-and-module-dispatcher.adoc:17 'Such a dispatcher answers whatever request reaches it, ...' (in the Context).
- HYPOTHESIS: the helper's package and name (under `pm-providers/pm-provider-http/src/test/java/de/planmarshall/provider/http/testing/`). Decided at the outline.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: A future decision of the outline (helper package and name); only the directory pm-provider-http/src/test/java/de/planmarshall/provider/http/testing/ exists (holds generators/ and AssertionMessageScanTest), no helper yet.
- Verify-first clause: D1 (#612) and any merge since changed test lines. Re-read every site.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: D1 1ae69d3f changed GitHubCiProviderTest and GitLabCiProviderTest (stat), and EndpointHarness.java changed since 3ba79c4f (its cited lines 79,87,94,116 still hold at head); the cited sites were re-read at head.

## Expected Surface

About 30 existing files plus about 3 new (the helper, its test, an ADR).

- OBSERVED: `pm-providers/pm-provider-http/pom.xml`, `pm-providers/pm-provider-sonar/pom.xml`, `pm-providers/pm-provider-http/src/test/java/de/planmarshall/provider/http/HttpClientTest.java`
- OBSERVED: `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/GitHubCiProviderTest.java`, `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/GitHubClientTest.java`, `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/InstallationTokensTest.java`, `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/GitLabCiProviderTest.java`, `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/GitLabClientTest.java`, `pm-providers/pm-provider-sonar/src/test/java/de/planmarshall/provider/sonar/SonarClientTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/corpus/HuggingFaceSourceRepositoryTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/corpus/LfsBatchClientTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointClientTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointHarnessTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointVerificationTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/SubprocessJobRunnerTest.java`
- OBSERVED: `pm-providers/pm-provider-github/doc/developer/provider-github.adoc`, `pm-providers/pm-provider-gitlab/doc/developer/provider-gitlab.adoc`, `pm-providers/pm-provider-http/doc/developer/provider-http.adoc`, `pm-runtime/doc/developer/runtime-corpus.adoc`
- OBSERVED: `doc/adr/017-http-tests-through-mock-response-config-and-module-dispatcher.adoc`, `doc/adr/010-the-coding-skills-and-the-accepted-exception.adoc`, `doc/adr/012-generator-artifacts.adoc`, `doc/adr/README.adoc`, `CLAUDE.md`, `.opencode/rules/plan-marshall-standing-rules.md`
- HYPOTHESIS: a new helper and its test under `pm-providers/pm-provider-http/src/test/java/de/planmarshall/provider/http/testing/`, and a new ADR in `doc/adr/`.

## Dependencies and Sequencing

- Depends on: none hard. #612 (PLAN-V2-126 D1) is merged.
- Overlaps with: this series' S7 and S9 on `SonarClientTest`, the provider tests and the endpoint tests, and PLAN-V2-125 (the generators). Serialise S7, then this plan, then S9, where one class is edited; the gate decides.
- Adjacent to: PLAN-V2-128 (the CI-contract cases of `GitHubCiProviderTest` and `GitLabCiProviderTest`) and PLAN-V2-103 to 105 (messages).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-204-http-tests-one-routing-shape-and-the-sonar-boundaries.md"
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
