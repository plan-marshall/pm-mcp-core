# Landing Analysis: PLAN-V2-92 — Generated test data in the provider module tests

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #312 (merge commit `20353474`; 51 files, +2609/-1056; four `pm-provider-ci` production files change imports only)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-92.md`. A pasted claim is a lead, never a fact.

Source: the plan's landing message `plan-v2-92-providers-tests-005.md` (08:23Z; `landing-check` `complete: false`, only `effort` missing; 26,120,657 tokens, 46,443 s, 10 deliverables, 8 met and 2 met with residuals, code review 0 blocking and 3 advisory) and four candidate lessons (`-001` to `-004`). PLAN-V2-92 was emitted with the epic-relative path, so this is the first plan of the stretch that filed its own landing message.
Corroborated by the orchestrator: `ci pr view 312` `merged`; Sonar `main` (analysis of `20353474`, 2026-11-07 08:19 UTC) gate OK, 0 open BUGs; the `Maven Build` and `Integration Tests` runs of `20353474` (push and merge_group) green. The reading was done at `20353474` by a read-only sub-agent (diffs of `4f91c9cd..20353474`, scripts over the 35 changed test files; nothing built); the points marked **checked** were re-read by the orchestrator.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 controller and `Generators` (or a contract statement) in every class | met, four in form | `RemotePolicyTest`, `UnservedWantTest`, `GitHubOperationTest`, `SonarProviderTest` carry the controller and no `Generators.` call; the statements are multi-line Javadoc |
| D1 test count same or higher, no assertion removed | met | none decreased; `EndpointTest` 4 to 5, `SonarIssueKeyTest` 3 to 5 |
| D1 PLAN-V2-62 fixes (labels, `forkWithoutPushIsNotWritable` discriminates) | met | `GitHubCiProviderTest:844-862` primes `push:false` then `push:true`; per-constant "(contract: …)" Javadoc |
| D1 repeated helper goes to the generator artifact | **not met** | C1 |
| D1 punctuation round trip, per-datum contract statement | partly met | C4, C5 |
| D2 `GitOperationsContractTest` | met | `ShouldHandleObjectContracts<GitCredential>`; the spec names `GitOperations`, the record is the nested `GitCredential` (S4) |
| Operator extension: `pm-provider-ci:tests`, `pm-provider-git:tests`, ADR 012, CLAUDE.md, READMEs, ADR provider-ci/005 | met | POMs copy the `pm-provider-http` pattern (only `testing/generators/**`); root `dependencyManagement` has both; neither JAR has a consumer in this repository yet |
| Done-when `verify -Pcoverage` | **not run** | the plan landed without it (PR body says so) |
| Done-when Sonar | met by a public-API read, not a scan | the lane had no credentials; main is green now |

## Three-Layer Analysis (standing rule R21, r6)

### Layer 1 — Specified
- **S1 (medium).** The spec never names `pm-provider-ci` (four classes converted) nor the operator's 2026-11-06 scope extension; "test code only" contradicts the extension. The extension lives only in the plan's `solution_outline.md`.
- **S2 (low).** The OBSERVED counts were stale: "0 `Generators` calls" is false (four classes already used it), and the file counts at `4f91c9cd` were github 6, gitlab 3, http 11.
- **S3 (medium).** The Done-when is satisfiable by form (a per-class statement where the constraint demands it per datum; "calls `Generators`" by one trivial call; no literal-reduction criterion). It produced `RemotePolicyTest`.
- **S5 (medium).** `verify -Pcoverage` and the `pm-core`/`pm-runtime` integration tests were in the Done-when though the plan changes neither module; no Sonar credentials in the lane. Coverage was never run.
- **S4/S6 (low).** `GitOperations` vs the nested record; `@GeneratorsSource` has no stated trigger and is used nowhere.

### Layer 2 — Implemented
- **I1.** Four classes with the controller and no `Generators` call (above). **I2.** Method counts: no decrease; the PR table understates the new generator tests (4 and 6 methods, "2 tests" in the PR body). **I3.** The `pm-provider-ci/src/main` diff is moved import lines only (checked), disclosed. **I4.** Test JARs as above. **I5.** The contract test overrides `shouldImplementSerializable` to assert "not Serializable" (deliberate).
- **I6.** `forkWithoutPushIsNotWritable` now discriminates. `APP_JWT = "jwt"` is labelled opaque, not contract (residual).

### Layer 3 — Correct
- **C1 (medium, checked).** `GitHubCiProviderTest:720-742` and `GitLabCiProviderTest:528-558` carry the same `Fixture` builder, `fixture()`, `branch()`, `path()` and the fork-owner helper; both modules could take `pm-provider-ci:tests` and neither declares it. The 40-hex commit id is written inline in `JGitCacheRepositoryTest:81` and `GlabCommandsTest:67`, and three git classes call the static `JGitCacheRepositoryTest.commitId()`; `uniqueValues(strings("ghij…",4,12))` is identical in `JGitOperationsTest:76` and `GitIdentityTest:65`. Same weakness as 86, 87, 89.
- **C2 (medium, checked).** About 21 assertions used `result.detail()` as the failure message (old `JGitOperationsTest:79,85,191,289,310,326`; `GitIdentityTest` 14); the new ones carry a static message, so a failing git operation no longer reports the provider reason. The code review's `WorktreeShaPortedTest` advisory is the same defect. Semantics unchanged.
- **C3 (low, checked).** `UnservedWantTest.refusals()` is a static `@MethodSource` that draws a commit id outside the per-test seed. No `static final` or instance field is filled by `Generators` in the 35 files (the 86/87/89 weakness did not repeat).
- **C4 (medium, checked).** `RemotePolicyTest:23-26` says every URL row is grammar; the `cuioss/x.git` path and the owner and repository names are not. `GitHubCiProviderTest`/`GitLabCiProviderTest` call `cuioss`/`pm` a contract because static `@MockResponseConfig` paths name them, a convenience.
- **C5 (low).** `JGitSourceRepositoryTest` data went from "Is the sky blue?" to `letterStrings(3,30)`: the CSV, overlay and JSON items lost commas, quotes, spaces. `JsonTest` keeps its escapes.
- **C6 (low).** No new boundary lost (`0`, `007`, 21/22 nesting, key edges kept). `reportsProviderResultCap` (`GitHubCiProviderTest:947`) draws `size + integers(1,5000)`, so `changed_files == listed + 1` is not pinned (pre-existing).
- **C7 (low).** Two "changed paths keep their order" tests use 3 random paths; a sorting implementation passes about one run in six.
- **C8 (low, pre-existing).** `ForgeUrlTest.refusesInvalidForge:362-367` hits the mismatch check first, so the pattern check is not isolated; the code review flagged it.
- **C9 (OK).** Assertion messages: none outside 20 to 60 characters in the 35 files. **C10.** Coverage not run; production unchanged, the 80% result should hold, unproven for the branches in `ForgeUrl`, `Endpoint`, `Json`. **C11.** The PR body calls the digest length 56 a boundary; it is a helper shift. `TestKeys.PAIR` is unseeded (documented).

## Verdict

D1 and D2 and the operator extension are met; the assertion-message rule and the test-method counts hold; the static-field weakness of 86, 87, 89 did not repeat and the PLAN-V2-62 test now discriminates. Not met: the shared-helper rule (C1), the coverage run, and diagnostics were lost in about 21 assertions (C2). Main is green. No hidden breaking change.

## Ownership

- PLAN-V2-93 (HTTP tests): carries the boundary/helper/seed bullet; the four HTTP classes it converts are untouched here. PLAN-V2-103 (assertion messages, workflow and providers): the provider part is met (bullet added at its outline). PLAN-V2-106 (`EasyMock for fakes`): reads the provider fakes.
- No owner: C1, C2, C3, C4, C5, C6, C7, C8 and the coverage run (S5, C10). A fix plan only on the operator's word (Wa75).
- Candidate lessons `-001` to `-004` concern plan-marshall components (`manage-execution-manifest`, `manage-solution-outline`, `phase-5-execute`, `plan-marshall`), not this store: routed to plan-marshall's lessons inbox only if the operator asks.
