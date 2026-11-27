# PLAN-V2-203: The repeated test helpers become generators of the module artifacts: words, ids, versions, the CI fixture and the git names

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: PLAN-V2-126, deliverable 2 (the operator narrowed 126 to D1 on 2026-11-25; D1 shipped as #612). Re-ground records of 2026-11-17, 11-19 and 11-23 are binding over older text; counts re-swept at 3ba79c4f.
lane: runbook

> Staged plan spec, one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off. Unemittable until PLAN-V2-125 merged. SELF-SUFFICIENT together with the standing rules.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R07, R10, R16, R19, R20 (the drafter's candidates, confirmed by the orchestrator against the rules file at staging; an id the lane finds not applicable it says so).

## Objective

The same helpers are copied into dozens of test classes where the module artifact should hold them. A lower-cased `letterStrings` word, the `"plan-"` and `"project-"` prefixes and the `major.minor.patch` version expression sit in the `pm-runtime` tests, although `RuntimeGenerators` offers `words()`, `shortWords()`, `planIds()` and `projectIds()`. The two CI provider tests hold the same `Fixture` record. A `word()` helper is copied into 12 classes. Several tests draw a commit id or a repository name by hand. This plan moves each into the artifact that serves its consumers, and consumes the word generator PLAN-V2-125 adds to `pm-core:tests`.

## Deliverables

1. **The generators, test first.** Each new generator has its own test that draws many times (item (c)).
   - `RuntimeGenerators.versions()` for the version expression.
   - A pull-request fixture generator in `CiGenerators`: title, body, branch, head sha, three paths, and for GitLab the target project id.
   - A commit-id generator and a repository-name generator in `GitGenerators`.
   - A forge-side name generator in `CiGenerators`.
   - The generator tests `FailingExitCodeGeneratorTest`, `PlanIdGeneratorTest` and `WordGeneratorTest` draw once; they are exercised over many draws.
   - `RuntimeGenerators.words()` keeps every method and signature (R16: the assembly consumes the artifact). Moving or delegating it is ask-at-refine (R07).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **pm-runtime copies replaced.** The inline words, prefixes and version expressions use the existing generators, the new `versions()` and the pm-core word generator. Counting commands go in the PR body with before and after numbers.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
3. **Providers.** Declare the `pm-provider-ci` `test-jar` in the POMs of `pm-provider-github` and `pm-provider-gitlab` (the consumption CLAUDE.md names; no third-party dependency). Delete the two `Fixture`, `fixture()`, `branch()`, `path()` copies. Replace the `word()` copies of the provider classes (and `GitIdentityTest`, `JGitOperationsFixtures`, `JGitCacheRepositoryTest`, `GlabCommandsTest` commit ids and names) with the generators. The git and forge generators stay in their own artifacts because the two modules do not depend on each other.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer

**Done when:**
- Deliverables 1 to 3.
- The counts before and after are in the PR body. At 3ba79c4f: 68 lines in 52 files combine `letterStrings` with `toLowerCase` in `pm-runtime/src/test`. 39 lines in 22 files carry a `"plan-` or `"project-` literal (command: `git grep -nE '"(plan|project)-' -- pm-runtime/src/test`). The version expression is in 13 files (list in Claim Labels). `static String word()` is in 12 classes (7 provider, 5 runtime). The 65-file union of the three runtime patterns is the replacement population.
- The two provider `Fixture` copies are gone. Each new generator has a test. The suites keep their test counts per class as at 3ba79c4f.
- A scratch mutation of one converted site (for example a version that is not `major.minor.patch`) fails its test where the format is the contract (reported).
- Build: `verify -Ppre-commit` (rewrites reviewed and committed), `verify` of `pm-runtime`, `pm-core`, `pm-provider-ci`, `pm-provider-git`, `pm-provider-github`, `pm-provider-gitlab`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am`. Use per-module runs with log ids if a full run exceeds the host's bound.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).

## Standing Constraints

- The word generator of `pm-core:tests` (PLAN-V2-125) must be on main first. If it is not, stop and report; do not add it here.
- Read every cited line at the head first and drop what a merge settled (name the commit). D1 (#612) changed many test files; lines moved.
- `pm-runtime:tests` is consumed by the assembly (R16): no existing `RuntimeGenerators` signature changes; `TestBases`, `FakeBus`, `FakeSecretService` stay as they are.
- A repeated expression goes into the module's artifact, not into a private method per class. A generated word is a datum; a literal stays only where it is the contract.
- A class may gain tests; none is removed. No behaviour change. No new dependency except the named `test-jar`. No more than 100 files per PR: the union is about 93 files, so the outline cuts by module family (pm-runtime, providers) if the sweep passes 100.
- Pre-1.0: no shim. CLAUDE.md test rules apply. Sonar dismissal only on the operator's selection, through `sonar_rest transition`.

## Claim Labels

- OBSERVED: `CoreGenerators` (`pm-core/src/test/java/de/planmarshall/core/testing/generators/CoreGenerators.java`) registers `roleSkills`, `projectEnrolments`, `endpointProfiles`, `leaseRecords` and `commands`, and has no word generator at 3ba79c4f. PLAN-V2-125 is not merged.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: CoreGenerators.java:34-55 registers exactly roleSkills, projectEnrolments, endpointProfiles, leaseRecords, commands; no word generator in pm-core/src/test/.../testing/generators (no PLAN-V2-125 commit in git log).
- OBSERVED: `RuntimeGenerators` has `resolvedSkills`, `sha256Digests`, `words`, `shortWords`, `jobIds`, `planIds`, `projectIds`, `queueTaskIds`, `questionIds`, `hmacKeys`, `failures` (eleven factories, no `versions()`).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: RuntimeGenerators.java:30-83 has resolvedSkills, sha256Digests, words, shortWords, jobIds, planIds, projectIds, queueTaskIds, questionIds, hmacKeys, failures = 11 factories; no versions().
- OBSERVED: `static String word()` is in `ValueTypesTest:48`, `RefusingFSTest:41`, `GitHubClientTest:153`, `GitLabClientTest:163`, `GlabCommandsTest:62`, `JsonTest:176`, `SonarClientTest:170`, `HuggingFaceSourceRepositoryTest:160`, `LfsBatchClientTest:169`, `EndpointClientTest:111`, `EndpointHarnessTest:118`, `EndpointVerificationTest:140`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: git grep 'static String word()' finds exactly the 12: ValueTypesTest:48, RefusingFSTest:41, GitHubClientTest:153, GitLabClientTest:163, GlabCommandsTest:62, JsonTest:176, SonarClientTest:170, HuggingFaceSourceRepositoryTest:160, LfsBatchClientTest:169, EndpointClientTest:111, EndpointHarnessTest:118, EndpointVerificationTest:140.
- OBSERVED: the `Fixture` record, `fixture()`, `branch()`, `path()` are at `GitHubCiProviderTest:785-806` and `GitLabCiProviderTest:550-572`. `pm-provider-github` and `pm-provider-gitlab` POMs declare the `pm-provider-http` and `pm-core` test-jars and no `pm-provider-ci` test-jar.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: GitHubCiProviderTest:785-806 and GitLabCiProviderTest:550-572 hold Fixture record, fixture(), branch(), path(); pm-provider-github and -gitlab pom.xml declare test-jar of pm-provider-http (:35) and pm-core (:46) only, no pm-provider-ci test-jar.
- OBSERVED: the 40-hex commit id is at `JGitCacheRepositoryTest:81` (static `commitId()`) and `GlabCommandsTest:67`. The name expression `uniqueValues(strings("ghijklmnopqrstuvwxyz", 4, 12))` is at `GitIdentityTest:65`, `JGitOperationsFixtures:73`, and similar at `WorktreeShaDigestTest:88` and `GitStageAndCommitPrimitiveFixtures:70`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: JGitCacheRepositoryTest:81 static commitId() (40 hex), GlabCommandsTest:67 sha() 40 hex; ghijklmnopqrstuvwxyz name expr at GitIdentityTest:65, JGitOperationsFixtures:73, GitStageAndCommitPrimitiveFixtures:70; WorktreeShaDigestTest:88 uses the similar a-z uniqueValues(strings(...,1,10)).
- OBSERVED: version expressions at `ProviderVerificationTest:77`, `EndpointVerificationTest:119`, `EgressJobTest:199,205`, `GitMetadataJobTest:367,373`, `WorkerJobTest:202`, `HarnessEnrolmentStoreTest:286`, `HarnessVerifierTest:255`, `HeadlessBlockTest:134`, `LaunchComposerTest:78`, `VerifyingBinaryCheckTest:53`. D1 moved the lines of the spec's older numbers.
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: Two sites moved: GitMetadataJobTest version expr is at :385,:391 (not 367,373); HarnessEnrolmentStoreTest version() is at :320-321 (:286 is a @DisplayName). The other ten sites hold (ProviderVerificationTest:77 ... VerifyingBinaryCheckTest:53); lines moved after 3ba79c4f.
- OBSERVED (drift): `RelayVersionCheckTest:69` and `HarnessEnrolmentStoreTest:105` are named by the older spec and the pattern did not match them at 3ba79c4f. Re-check at the outline.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: RelayVersionCheckTest:68-71 builds its version with "%d.%d.%d".formatted(...), not the integers(..).next()+"." pattern, so it does not match; HarnessEnrolmentStoreTest:105 is 'var client = harnessId();', no version expression. Drift as stated.
- HYPOTHESIS: new generator classes in `CiGenerators`, `GitGenerators`, `RuntimeGenerators` and their tests. Confirm the names at the outline.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Future-tense statement about new generator classes; CiGenerators, GitGenerators, RuntimeGenerators and the three named generator tests exist, but the new generators/tests are what the plan will add.
- Verify-first clause: re-read each file at the head. D1 changed `HarnessVerifierTest`, `HeadlessBlockTest` and others.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: git show --stat 1ae69d3f (D1 #612) lists HarnessVerifierTest.java (22 lines) and HeadlessBlockTest.java (15 lines) among its 23 changed files; re-read at head done for every site of claims 2-6.

## Expected Surface

About 93 files: 65 runtime test files, 7 provider test classes, 2 Fixture copies, 4 git test files, 2 POMs, about 10 generator files and tests, 3 generator tests.

- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/testing/generators/RuntimeGenerators.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/testing/generators/FailingExitCodeGeneratorTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/testing/generators/PlanIdGeneratorTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/testing/generators/WordGeneratorTest.java`
- OBSERVED: `pm-providers/pm-provider-ci/src/test/java/de/planmarshall/provider/ci/testing/generators/CiGenerators.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/testing/generators/GitGenerators.java`, `pm-providers/pm-provider-github/pom.xml`, `pm-providers/pm-provider-gitlab/pom.xml`
- OBSERVED: `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/GitHubCiProviderTest.java`, `pm-providers/pm-provider-github/src/test/java/de/planmarshall/provider/github/GitHubClientTest.java`, `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/GitLabCiProviderTest.java`, `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/GitLabClientTest.java`, `pm-providers/pm-provider-gitlab/src/test/java/de/planmarshall/provider/gitlab/GlabCommandsTest.java`, `pm-providers/pm-provider-ci/src/test/java/de/planmarshall/provider/ci/ValueTypesTest.java`, `pm-providers/pm-provider-http/src/test/java/de/planmarshall/provider/http/JsonTest.java`, `pm-providers/pm-provider-sonar/src/test/java/de/planmarshall/provider/sonar/SonarClientTest.java`
- OBSERVED: `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/GitIdentityTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitOperationsFixtures.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitCacheRepositoryTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/RefusingFSTest.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/corpus/HuggingFaceSourceRepositoryTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/corpus/LfsBatchClientTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointClientTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointHarnessTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointVerificationTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/credentials/ProviderVerificationTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/EgressJobTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/GitMetadataJobTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/WorkerJobTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/launch/HarnessEnrolmentStoreTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/launch/HarnessVerifierTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/launch/HeadlessBlockTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/launch/LaunchComposerTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/VerifyingBinaryCheckTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/lock/DerivedFactStoreLockTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/lock/FileLockManagerTwoProcessIT.java`
- HYPOTHESIS: the remaining of the 65-file union of the `letterStrings`, `"plan-` and version patterns under `pm-runtime/src/test` (re-sweep at the outline with the counting commands above), and new generator classes with tests under the three `testing/generators` packages.

## Dependencies and Sequencing

- Depends on: PLAN-V2-125 (hard). It adds the word generator to `pm-core:tests`. This plan is unemittable until PLAN-V2-125 merged.
- Overlaps with: PLAN-V2-125 (`BuildLogText`, `LimitProperties`, generators), PLAN-V2-141 (the assembly check of the artifacts) and this series' S8 to S10 (different test files; S8 edits `SonarClientTest`, `EndpointHarnessTest`, the provider routing tests). Serialise S7 before S8 where the same test class is edited.
- Adjacent to: the fakes (PLAN-V2-106 and 136), the record builders (PLAN-V2-108), the assertion messages (PLAN-V2-103 to 105).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-203-generators-for-the-repeated-test-helpers.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Re-Ground Record of 2026-11-27 (cleanup, head 115d5987)

Binding, like every earlier record: where a claim above and this record disagree, the record is what is true at the head; the claim bullets are not rewritten. Read by read-only leaves (R21) from the head `origin/main` 115d5987; each contradicted claim is absorbed here (`rescoped: yes`).

- Claim 5: Two sites moved: GitMetadataJobTest version expr is at :385,:391 (not 367,373); HarnessEnrolmentStoreTest version() is at :320-321 (:286 is a @DisplayName). The other ten sites hold (ProviderVerificationTest:77 ... VerifyingBinaryCheckTest:53); lines moved after 3ba79c4f.
