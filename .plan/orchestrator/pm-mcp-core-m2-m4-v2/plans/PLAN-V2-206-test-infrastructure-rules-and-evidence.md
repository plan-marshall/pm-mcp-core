# PLAN-V2-206: The test infrastructure follows the project's rules, the artifact documents are true, the runtime limit constant can fail and the evidence is complete

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: PLAN-V2-126, deliverable 5 (the operator narrowed 126 to D1 on 2026-11-25; one PR per the operator's "four PRs per spec"). Operator decisions kept: D5(e) the package-private seam in `StalledScopeDetector` is allowed; `CLAUDE.md` and `.opencode/rules/plan-marshall-standing-rules.md` edits allowed.
lane: runbook

> Staged plan spec, one shippable unit of work (one PR), ready for `/plan-marshall-runbook-lane` hand-off. SELF-SUFFICIENT together with the standing rules.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R19, R20 (the drafter's candidates, confirmed by the orchestrator against the rules file at staging; an id the lane finds not applicable it says so).

## Objective

Three `pm-runtime` test helpers (`HostToolCalls`, `ProcessLiveness`, `JsonDocuments`) are `public final class` with a private constructor and only static methods, where CLAUDE.md asks for a Lombok `@UtilityClass`. `LspClientTest.stubborn` starts a server with a fixed 2 s initialize timeout and is an old flake. The `pm-runtime/README.adoc` and the generators `package-info.java` describe the artifact wrongly, and ADR 012 and CLAUDE.md omit that a consumer of `pm-runtime:tests` must declare `cui-test-generator`. No test can fail for `StalledScopeDetector.THRESHOLD`, because a constant hard-coded to 600 leaves every test green. And `verify -Pcoverage` was never run for the provider modules. This plan fixes all five.

## Deliverables

1. **The limit constant, test first (e).** `StalledScopeDetector.THRESHOLD` is `Duration.ofSeconds(LimitProperties.read(LIMITS, THRESHOLD_KEY))` (`:57`, key `:51`). `thresholdIsReadFromTheLimitsResource` (`StalledScopeDetectorTest:648`) compares it with the same read, and `readsAnAlternateResource` (`:656`) calls the reader directly, so a hard-coded constant passes.
   - Add a test that fails when the constant stops flowing from `stalled-scope.properties`.
   - The operator allows a package-private method that takes the resource, used to initialise the constant. The seam alone is not enough: a test that calls the method does not fail for a hard-coded `THRESHOLD`. The test must also load the class under an alternate resource (an isolated class loader) or otherwise make `THRESHOLD` itself depend on the resource, and the outline proves which.
   - Production change: the package-private seam only; no public type is added. `stalled-scope.properties` stays byte-stable (R10).
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **`@UtilityClass` (a).** Convert `HostToolCalls` (`:21`), `ProcessLiveness` (`:18`), `JsonDocuments` (`:21`) and report the scan count of the runtime and provider test trees for the same shape.
   - The scan finds 26 classes with a private no-argument constructor in `pm-runtime/src/test`. The ArchUnit guard fixtures under `guard/fixtures/` are deliberate violating shapes and must not be converted. `TestBases` is in the published `pm-runtime:tests` artifact (R16): do not change it without the source-compatibility check.
   - Skills: pm-dev-java:java-lombok, pm-dev-java:java-core, pm-dev-java:junit-core
   - Persona: implementer
3. **The `stubborn` flake (b).** `LspClientTest.stubborn` (`:128`) uses `WAITED_BOUND = 2 s` (`:57`), which also bounds `LspClient.start`'s initialize await. Wait for the readiness the test needs instead of a fixed 2 s, or give `initialize` its own bound, or raise it with a stated reason. The cause is a HYPOTHESIS (not shown by reading).
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
4. **The artifact documents (c).** Correct `pm-runtime/README.adoc` (the generator sentence at `:49-53` says one `TypedGenerator<T>` per runtime type the assembly builds by hand, `ResolvedSkill`) and `pm-runtime/src/test/java/de/planmarshall/runtime/testing/generators/package-info.java`: `RuntimeGenerators` registers eleven factories (words, ids, digests, keys, failures). Add a sentence to ADR 012 (Consequences) that a consumer of `pm-runtime:tests` must declare `de.cuioss.test:cui-test-generator` in its own test scope for `TestBases` (only `README.adoc:61-65` says it now), and the same in CLAUDE.md § Generator Artifacts (operator allowed). ADR 012 `:86` still says "the five generator artifacts package only their generator package": say how the two provider artifacts and the later additions follow that rule.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter
5. **The evidence (d).** Run `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and the integration tests of `pm-runtime` for the modules the PLANs of this series touched; report each result and the coverage per module (80 % instruction and branch minimum). The provider modules were never covered: run them too. Where a full run does not finish inside the host's bound (about 1500 s), give the targeted `-Dtest` and per-module runs, each with its log id, in the PR body.
   - Skills: pm-dev-java:junit-core
   - Persona: module-tester

**Done when:**
- Deliverables 1 to 5.
- A scratch constant hard-coded to its current value (600) fails the new test (log id).
- No `public final class` with a private constructor and only static methods is left among the three named classes. The scan count is in the PR body.
- `LspClientTest` passes ten consecutive runs of `-Dtest=LspClientTest` on the host (reported with log ids).
- Both documents name the right content of the artifact, and ADR 012 and CLAUDE.md carry the sentence.
- Coverage per module and the integration results are in the PR body.
- Build: `verify -Ppre-commit` (rewrites reviewed and committed), `verify` of `pm-runtime` and the provider modules, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am`.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).

## Standing Constraints

- One PR for this spec. The suites keep their test counts per class as at 3ba79c4f.
- No production change except the package-private seam of deliverable 1. No `.schema.json` or `.properties` change. No public type is added (if one is needed, it is ask-at-refine, R07).
- `pm-runtime:tests` is consumed by the assembly (R16); no published signature changes.
- Read every cited line at the head first and drop what a merge settled (name the commit). Pre-1.0: no shim. No new dependency. No more than 100 files per PR.
- A test is accepted when a scratch mutation of the thing it names fails it. CLAUDE.md test rules apply.
- Sonar dismissal only on the operator's selection, through `sonar_rest transition`.

## Claim Labels

- OBSERVED: `StalledScopeDetector.THRESHOLD_KEY` (package-private, `:51`) and `THRESHOLD` (`public static final Duration`, `:57`, initialised from `LimitProperties.read(LIMITS, THRESHOLD_KEY)`). `StalledScopeDetectorTest:648` and `:656` are the two limits tests. Read at 3ba79c4f.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: StalledScopeDetector.java:51 THRESHOLD_KEY package-private, :57 public static final Duration THRESHOLD = Duration.ofSeconds(LimitProperties.read(LIMITS, THRESHOLD_KEY)); StalledScopeDetectorTest:648 thresholdIsReadFromTheLimitsResource, :656 readsAnAlternateResource. (3ba79c4f itself not checked.)
- OBSERVED: `HostToolCalls:21`, `ProcessLiveness:18`, `JsonDocuments:21` are `public final class` with a private constructor.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: HostToolCalls.java:21, ProcessLiveness.java:18, JsonDocuments.java:21 are public final class with private constructor (:23 each); no @UtilityClass present.
- OBSERVED: `LspClientTest:57` (`WAITED_BOUND = 2 s`) and `:128` (`stubborn`).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: LspClientTest.java:57 WAITED_BOUND = Duration.ofSeconds(2); :128 void stubborn().
- OBSERVED: `pm-runtime/README.adoc` (generator sentence at `:49-53`; the `cui-test-generator` need at `:61-65`) and ADR 012 `:86`.
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: ADR 012:86 holds ('The five generator artifacts package only their generator package'), but pm-runtime/README.adoc moved: the generator sentence is at :60-62 (section 'Test artifact' :56-64), the cui-test-generator need at :66-68, not :49-53 / :61-65 (:49-53 is the build-commands/mock-server text).
- OBSERVED: `pm-core/pom.xml:144-148` includes the generators, assertions and `SchemaResource`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: pm-core/pom.xml:144 <includes>, :145 testing/generators/**, :146 testing/assertions/**, :147-148 SchemaResource.class and SchemaResource$*.class, :149 </includes>; range 144-148 covers them.
- OBSERVED (drift): the older spec cited `README.adoc:49-52` and `:61-62`; the lines moved by about one. D3 of this series edits CLAUDE.md in a different section; the two edits do not overlap.
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: Drift is not 'about one': at the head the README generator sentence is :60-62 and the cui-test-generator need :66-68 (README.adoc), i.e. moved by about 11 and 5 lines from the :49-52/:61-62 cited. The CLAUDE.md section-overlap half is not decidable by reading.
- HYPOTHESIS: the seam plus an alternate class loader is what makes a hard-coded `THRESHOLD` fail. Confirm at `StalledScopeDetector.java` and `LimitProperties.java` at the outline.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: StalledScopeDetector.java:57 THRESHOLD is a static final read at class init; LimitProperties.java (pm-core/.../core/config/, not pm-runtime) read() resolves via LimitProperties.class.getResourceAsStream, so an alternate loader must define LimitProperties too. Mechanism consistent with the hypothesis.
- HYPOTHESIS: the cause of the `stubborn` flake is the fixed 2 s initialize timeout (not shown by reading). Confirm at `LspClient.java:128` and `FixtureLanguageServer`.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: LspClient.java:128 awaits server.initialize with the instance timeout (WAITED_BOUND 2 s) so the bound is read; whether that is the cause of the stubborn flake needs runs, not reading (spec itself says not shown by reading).
- Verify-first clause: PLAN-V2-125 Deliverable 6(a) decides the same mechanism for `pm-core`. Re-read it if merged, and use the same seam.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Conditional instruction about re-reading PLAN-V2-125 D6(a) if merged; future-tense/directive, not a fact about the head.

## Expected Surface

About 12 existing files.

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/start/StalledScopeDetector.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/start/StalledScopeDetectorTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/test/HostToolCalls.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/test/ProcessLiveness.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/test/JsonDocuments.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/lsp/LspClientTest.java`
- OBSERVED: `pm-runtime/README.adoc`, `pm-runtime/src/test/java/de/planmarshall/runtime/testing/generators/package-info.java`, `doc/adr/012-generator-artifacts.adoc`, `CLAUDE.md`
- Read only (R10): `pm-runtime/src/main/resources/de/planmarshall/runtime/start/stalled-scope.properties`.

## Dependencies and Sequencing

- Depends on: none hard. #612 (PLAN-V2-126 D1) is merged. Deliverable 1 reads PLAN-V2-125 Deliverable 6(a) if it has merged.
- Overlaps with: this series' S8 (CLAUDE.md, ADR 012) and S9 (`StalledScopeDetectorTest`). Serialise S8 first (it edits ADR 012 and CLAUDE.md too); the gate decides.
- Adjacent to: PLAN-V2-125 (`LimitProperties`, `BuildLogText`) and PLAN-V2-140's `reuseForks` audit (not touched).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-206-test-infrastructure-rules-and-evidence.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Re-Ground Record of 2026-11-27 (cleanup, head 115d5987)

Binding, like every earlier record: where a claim above and this record disagree, the record is what is true at the head; the claim bullets are not rewritten. Read by read-only leaves (R21) from the head `origin/main` 115d5987; each contradicted claim is absorbed here (`rescoped: yes`).

- Claim 3: ADR 012:86 holds ('The five generator artifacts package only their generator package'), but pm-runtime/README.adoc moved: the generator sentence is at :60-62 (section 'Test artifact' :56-64), the cui-test-generator need at :66-68, not :49-53 / :61-65 (:49-53 is the build-commands/mock-server text).
- Claim 5: Drift is not 'about one': at the head the README generator sentence is :60-62 and the cui-test-generator need :66-68 (README.adoc), i.e. moved by about 11 and 5 lines from the :49-52/:61-62 cited. The CLAUDE.md section-overlap half is not decidable by reading.
