# Landing Analysis: PLAN-V2-126 — Test conformance of the runtime and the providers (Deliverable 1 only)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #612 (https://github.com/plan-marshall/pm-mcp-core/pull/612), merge `1ae69d3f78150cc7721979ac32291465c655590f`, 23 files (+114/-69), all under `src/test`

> Landing record for the part of PLAN-V2-126 that shipped: **Deliverable 1** (the tests that lost a boundary, an order or a
> diagnostic get it back). The operator narrowed the plan to D1 plus its pull request on 2026-11-25; D2 is blocked on PLAN-V2-125
> and D3 to D5 were never started; they are split into their own staged plans (see Follow-Ups). Written by the `analyze` verb of
> the thirty-seventh run (2026-11-25, second analyze) from `rb-v2-126-test-conformance-runtime-and-providers-002.md` (revision 0),
> corroborated against git (`git diff e451ed04 1ae69d3f`), the PR (`ci pr view`: merged) and the merged tests, read by one
> read-only level-3 leaf and re-read by the orchestrator. Message `-001` (the earlier partial landing, no PR) is superseded by
> `-002` and is archived with it. The first pull request (#611) was closed unmerged on the operator's word and re-created as #612.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1(a) boundaries | shipped-as-specified | `JobRecoveryStepTest` `BOOT.minusSeconds(1)` twice; `MachineConfigurationStoreTest` `revision - 1`; `HeadlessBlockTest` `2.0.0`, `0.9.9` and the inclusive `1.0.0`; `HarnessVerifierTest.unsupportedVersion`; `OutcomeDerivationTest` `declaredCode + 1` |
| D1(b) orders | shipped-as-specified | `RuntimeDrainTest` (`"aweb-"` sorts before `"jobs-"`); `GitHubCiProviderTest` and `GitLabCiProviderTest` `unsortedPaths()` (`z/`, `m/`, `a/`) |
| D1(c) provider cap | shipped-as-specified | `GitHubCiProviderTest.java:1034` `fixture.paths().size() + 1` asserts `PROVIDER_RESULT_CAP` |
| D1(d) restored diagnostics | **partial**: 32 sites in 12 classes restored (count confirmed), the rule is not fully applied | see L2-1 to L2-3 |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, `orchestrator.analyze` surface), 55 tool calls, no write. Reason: 23 test files against a 86 KB spec and a narrative whose counts have to be re-derived from the diff.
- **Orchestrator re-read:** the restored-heading count (32 `assertAll` headings carrying `detail()`, `diagnostic()`, `reason()`, `describe(...)` or `elapsed` in the diff of `e451ed04..1ae69d3f`), `GitIdentityTest.java:79` and `:227` at `1ae69d3f` (static messages), the same two sites at the commit before the split, `24371eae` (`:66` and `:187` carried `result.detail()`), and `JGitCacheRepositoryTest.java:117`. The leaf cites the parent as `20353474^`; that is `24371eae`. The leaf counts ten lost `detail()` sites in `JGitCacheRepositoryTest`; the orchestrator's pattern finds six at that parent (the others use a different message form), so the record says "at least six".
- **Verdict:** layer 1 findings (two spec flaws, minor); layer 2 findings (one major: the diagnostic rule is not met everywhere); layer 3 clean.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | "before boot edge is `BOOT.minusSeconds(1)`" at `JobRecoveryStep.java:310`; "supported range `>=1.0.0 <2.0.0`" at `HeadlessBlock.supports :200`; "stale revision edge is `revision - 1`" at `MachineConfigurationStore.java:231`; "adjacent undeclared exit code" at `OutcomeDerivation.java:123`; the provider cap at `GitHubCiProvider.java:273`; `RuntimeDrain.java:173,194,211` in registration order | checked | confirms | every site exists at `e451ed04`; the range is the test fixture's (`LaunchFixtures.java:111`), not a production constant; the strict edge of the before-boot rule is `bootTime.minusNanos(1)`, so a `minusMillis(500)` mutant survives the one-second pin |
| L1-2 | Done-when "a scratch mutation per item fails the restored test and no other" | checked | refutes | shared fixtures make "and no other" unsatisfiable (the lane's own table shows two to three tests failing per mutation) |
| L1-3 | D1(d) lists sites | checked | refutes | the spec states a general rule and then lists sites that moved in the split (`JGitOperationsTest.java:109,115,249` no longer exist) or were incomplete (`JGitCacheRepositoryTest`, `GitIdentityTest:227`, `EndpointHarnessTest`, `WeightsReaderTest`, `VerifyNotResolvePipelineTest`); executing the list leaves the rule unmet |

**Layer 1 verdict:** findings — 2 minor (L1-2, L1-3).

### Layer 2 — Implemented

| Finding | Deliverable | What | Verdict |
|---------|-------------|------|---------|
| L2-1 | D1(d) | `GitIdentityTest.java:79` (a site the spec NAMES) is still static: `assertEquals(GitOutcome.OK, result.outcome(), "the fixture commit is recorded")`; at the commit before the split it was `result.detail()` (`:66`). A failed fixture commit still reports no cause. | checked, **major** |
| L2-2 | D1(d) | `GitIdentityTest.java:227` (`"a log of an ambiguous name is refused"`, parent `:187` `result.detail()`) is not restored either; of the parent's detail sites the six restored headings cover only part. | checked, minor |
| L2-3 | D1(d) | The narrative says `JGitCacheRepositoryTest` "holds only new split tests with no parent detail() message site, so they are not relocations": refuted. At least six parent sites carried `committed.detail()` and kin (the leaf counts ten); at `1ae69d3f` they are static (`:117` `"the source repository's commit is made"` and others). Also lost and unrestored beyond the spec's list: `EndpointHarnessTest:214`, `WeightsReaderTest:70`, `VerifyNotResolvePipelineTest.unreadable`. | checked, **major** |
| L2-4 | D1(d) | The relocation paragraph miscounts: of the 11 headings labelled "relocated" only 8 are (Fixtures 2, Worktree 4, Transport 2); `JGitOperationsTransportTest` 3 and `JGitOperationsTest` 2 are additions. The 8 parent sites are genuinely restored. | checked, minor |
| L2-5 | R19 | Three files outside the Expected Surface (`TaskQueueSchemaParityTest`, `ServedModelsTest`, `ServedModelWatchTest`, from #594): gate rewrites, formatting only, disclosed, committed as `6fc5abc0`. The narrative's "no changed file is outside the Expected Surface" is not borne out; its Rule-check paragraph discloses it. | checked, minor |
| L2-6 | D1 | "no pre-existing assertion was removed or relaxed" holds for assertions; five sites narrow a drawn range to a single point (before boot 1..86400 s to 1 s, stale revision 0..revision-1 to revision-1, versions to `2.0.0`/`0.9.9`, undeclared +1..100 to +1), the spec's permitted literal edge, so interior coverage is no longer sampled. | checked, info |
| — | D1 | The mutation logs, the quoted scratch failures per site, the test counts (12 859, 457) and the green CI cannot be read by reading; each named mutation traces to a test that would fail. `JGitOperationsWorktreeTest.java:240` follows a heading-less five-assertion `assertAll` and is pattern-proven by `prunable`. | not_shown_by_reading |

The 32 restored headings were counted from the diff: `GitIdentityTest` 6, `JGitOperationsFixtures` 2, `JGitOperationsTest` 2, `JGitOperationsTransportTest` 3, `JGitOperationsWorktreeTest` 4, `AuditVerificationTest` 3, `ArgumentCheckTest` 3, `JobRefusalTest` 1, `VerifyNotResolvePipelineTest` 1, `TerminationProtocolTest` 2, `ColdStartComparisonTest` 4, `StartupColdStartIT` 1. Each heading carries a real value (not a literal).

**Layer 2 verdict:** findings — 6 (2 major, L2-1 and L2-3).

### Layer 3 — Correct

No test defect found in the changed lines: every pin can fail, messages are 20 to 60 characters, there is no AAA comment, no new hand-written double (`FakeParticipant` in `RuntimeDrainTest` is pre-existing), the `StartupColdStartIT` change adds a heading to a verdict already computed and no new time dependence. Minor notes: the four `verdict.describe(0, 300)` headings of `ColdStartComparisonTest` print counts for synthetic populations (misleading on failure; the parent's shape, acknowledged by the lane); the `@MethodSource` row `BOOT.minusSeconds(1)` at `JobRecoveryStepTest.java:457` has no contract comment; the `+1` and ascending-sort pins are one-sided (a `declared-1` mutant and a descending sort pass). Not shown by reading: passing runs, mutation logs, CI.

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `ColdStartComparisonTest.java:65` | a failing run | the heading names 0 and 300 projects for data that has none | minor |
| L3-2 | `JobRecoveryStepTest.java:457` | read | a literal row without the one-line contract comment CLAUDE.md asks for | info |
| L3-3 | `OutcomeDerivationTest.java:225`, `RuntimeDrainTest.java:69` | a `declared-1` mutant; a descending sort | both pass; the spec asked for the one side only | info |

**Layer 3 verdict:** findings — 3 (minor and info); no blocker, no major.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | Assertion messages of 20 to 60 characters (assertAll heading exempt) | conforms | added messages 26–51 characters |
| CLAUDE.md | No AAA phase comments | conforms | only reason comments added |
| CLAUDE.md | `@ParameterizedTest` for 3+ variants | conforms | `HeadlessBlockTest.supportedVersions` holds three edges in one `assertAll` with an own message each |
| CLAUDE.md | Test data from generators; a literal stays where it is the contract and says so | conforms (one row, L3-2) | each edge literal carries a comment or Javadoc |
| CLAUDE.md | JUnit 5, EasyMock, no new hand-written fake; no production change | conforms | all 23 files under `src/test` |
| spec D1(d) | Restore the diagnostic of every assertion whose failure message was the result's detail, reason, diagnostic or measured time | **deviates** | L2-1 to L2-3 |
| spec R19 | Edit only files of the Expected Surface | deviates (disclosed) | L2-5 |

## Metrics and Anomalies

- Tokens `unknown`, `plugin_version` `unknown`: the known recurrence (Open Defect 7); the landing-check reports `complete: false` on exactly those two keys. `sonar_open_new_issues=0` is the lane's claim.
- Anomalies: the first PR (#611) was closed unmerged on the operator's "retry now" word to obtain a `kind=reviewed` marker from CodeRabbit after a quota refusal; #612 is the landing. CI on #612 failed once in `pm-core` because main itself failed `ShippedProfilesTest` on the pm-api SNAPSHOT (`usage.model`); fixed on main by #594. Not caused by D1.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed #611 at `8070bb56` and #612 at `69a1e10c`, no actionable comments; Sourcery refused on hard quota. Re-read through `review-gate --pr-number 612`: `unresolved_review_threads: 0`, `review_body_findings_total: 0`, `merge_allowed: true` (matches the landing; nothing to dispose).
- CI/merge: merged through the merge queue as `1ae69d3f`.
- Sonar: the landing's `0` (quality gate `ok`) is the lane's measured claim; the read-side re-read answers `undecidable` after the merge (`head_revision_unavailable`), so it is not re-verified and not contradicted.

## Reconciliation Actions

- [x] row `status` → `shipped` (the plan as narrowed by the operator: D1 plus its PR), `pr` `#612`, `landing` `landings/PLAN-V2-126.md`, `plan_marshall_plan_id` `rb-v2-126-test-conformance-runtime-and-providers`
- [x] messages `-001` and `-002` archived by their exact names
- [x] D2 to D5 staged as their own plans (see Follow-Ups); the unrestored diagnostics placed in a staged plan

## Follow-Ups

- **The operator's cut of 2026-11-25 (126 narrowed to D1 plus its pull request) is carried out:** the row is `shipped` for what shipped; D2 to D5 are staged as four plans, one pull request each ("four PRs per spec"), with the operator's recorded decisions: PLAN-V2-203 `generators-for-the-repeated-test-helpers` (D2, WS-11, **blocked on PLAN-V2-125**, hard, unemittable until it merged; about 93 files, at the 100-file cap: the outline cuts by module family), PLAN-V2-204 `http-tests-one-routing-shape-and-the-sonar-boundaries` (D3, shared routing helper in `pm-provider-http:tests`, which widens the contents rule of ADR 012 and CLAUDE.md; the five `pm-runtime` routing classes cannot reach that JAR, see the open question), PLAN-V2-205 `test-statements-draws-and-pairs` (D4), PLAN-V2-206 `test-infrastructure-rules-and-evidence` (D5, with the package-private seam in `StalledScopeDetector`).
- **L2-1 to L2-3 (the unrestored diagnostics) are folded into PLAN-V2-205 as its Deliverable 5**, with the sites read at `1ae69d3f` and their parents at `24371eae` named, the rule stated as the rule and not as a list (the list of the spec was the flaw, L1-3), and the surface extended in the same edit (five test files, read back declarative, 35 entries).
- L1-2 (the Done-when "and no other") and L3-1 to L3-3 are minor and are recorded here as history; L3-1 (the `describe(0, 300)` headings) and L3-2 (the uncommented `@MethodSource` row) are one-line fixes that fit PLAN-V2-205's scan.
- The Carried Open Items of PLAN-V2-126 that no deliverable owns by number (items 63, 66, 69, 76 and the bullets of PLAN-V2-99, 66, 124, 140, 136, 81 and 123) are NOT carried into 203 to 206 (drift found by the drafter); they are an open question to the operator in `epic.md`, recommendation: one leftovers plan.
