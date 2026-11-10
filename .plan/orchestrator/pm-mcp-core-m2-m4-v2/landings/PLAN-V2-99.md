# Landing Analysis: PLAN-V2-99 — Optional to @Nullable in pm-runtime and the providers

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #342 (providers, merge `145f5916490cc1b7514e502b37397a82857990c3`), #343 (runtime, merge `1dcb0c88264a012b95f54757850f932ce5de3e81`); the combined attempt #337 was closed unmerged by the operator's split decision

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-10 from the runbook-lane
> message `rb-v2-99-optional-conversion-runtime-and-providers-001.md` (harness opencode, model deepseek-v4.1-flash/high).
> The message is a lead; every figure below was re-read from git, the CI abstraction or the Sonar public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 fields | shipped-modified | The spec's "runtime 12 / providers 17 fields" does not hold at `main`: one real field, `ProcessTreeTracker.rootStartedAt` (now `@Nullable Instant`, `ProcessTreeTracker.java:119`); the 17 provider "fields" are record components. No test pins the nullable state (L2-2). |
| D2 parameters and record components | shipped-modified | Every `Optional` parameter and component of `pm-runtime` and `pm-providers` main is converted (HEAD scan: no `Optional<T>` field, parameter or component remains). Five `OptionalInt`/`OptionalLong` components were kept, undisclosed (L3-2). The lane's own verdict is "met in form, not fully met as written": test-first not observed, no per-private-signature test. About half of the provider absence tests cannot fail (L2-1). |
| D3 callers, ports, owed list | shipped-modified | Callers and tests adapted; no `pm-core` file in either diff. "Owed outside: none" rests on a `git grep` of the changed provider types only; the changed public runtime types (`ProviderDefault`, `WorkerStart`, `JobRequest.WrapperBuild/HostToolBuild`, `LaunchRequest`, `WorkerStartRequest`, `WorkerLaunch`, `CredentialAccount`, `SubprocessJobRunner.EnvironmentComposer.ofWorker`, `HarnessesSidecarCheck.Result`) were not grepped (L2-3). |

Facts block: `deliverables_total=3`, `deliverables_done=2`: consistent with the narrative's own "D2 not fully met as written" (the "D1-D3 met" framing is the narrative's, not the facts block's).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), reason: 128 files in two merges across two module sets, with a null-flow question at every converted site; the read must not share context with the 97 and 128 reads of the same drain.
- **Verdict:** layer 1 findings (minor); layer 2 findings (minor, one not shown); layer 3 findings (minor), no NPE or format change found by reading.
- Points re-read by the orchestrator before recording: the five kept `OptionalLong`/`OptionalInt` components (`AuditIndex.java:69`, `CredentialStoreAspect.java:130`), `Response.java` compact constructor at `1dcb0c88^1` (no null check on `etag`, `next`, `resetAt`), `HttpClient.java` before (`Optional.of(etag)`) and after (`send("GET", pathOrUri, null, etag)`), the ReviewComment/ReviewThread record declarations (`GitHubClient.java:133, 147`), and the missing-message assertions at `CredentialStoreAspectTest.java:293` and `EnrolmentsTest.java:803`. All held. Not re-read: the remaining 20 or so points, recorded as the leaf's reading.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | The Done-when regex `private (final )?Optional<` matches 38 private methods returning `Optional` and 0 fields, parameters or components at `HEAD`; "finds only listed sites" is satisfiable only by listing the returns. | checked | confirms | spec line 41 |
| L1-2 | The Done-when is scoped to `pm-runtime/src/main` though the Objective and D2 cover `pm-providers`; it cannot match `OptionalInt`/`OptionalLong`. | checked | refutes | spec line 41 |
| L1-3 | The premise "runtime 12 / providers 17 fields" is refuted at `main` (one field; the 17 are record components). | checked | refutes | `ProcessTreeTracker.java:123` (base) |
| L1-4 | The spec cites the "split below 100 files" lesson yet is written as one unit; the conversion commit is 111 files and the landing 128, which produced the closed #337 and the forced split. | checked | refutes | spec line 52 |
| L1-5 | The constraint "make the absence a type, not another annotation" (PLAN-110) conflicts with D1/D2 `@Nullable`; the landing does not say how it was settled for the kept `OptionalInt`/`OptionalLong`. | checked | refutes | spec line 50 |
| L1-6 | "Tests first" is satisfiable for the runtime (a null into an `Optional` parameter raised a `NullPointerException`) but nothing in the Done-when gates it. | checked | confirms | spec line 38 |

**Layer 1 verdict:** findings — 5 minor.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|-----------|--------------------|----------------------------------|---------|
| D1 | `ProcessTreeTracker.java:119` | none for the nullable state (the cited `survivorWithoutCommand` is a D2 record component) | — | checked (refutes the evidence claim) |
| D2 | the converted records and parameters | runtime: the old compact constructors rejected null for `Survivor`, `CredentialAccount`, `Lines`, `Verdict`, `ProviderDefault`, `Result`, `HostToolBuild`, `WaitAgainResult`, `JobConfinementComposer`, so those tests can fail; providers: see L2-1 | the `ResponseGenerator` change in the published `pm-provider-http` test JAR is not flagged in the narrative | checked |
| D3 | callers adapted | — | the owed list covers provider types only (L2-3) | checked |

- **L2-1 (minor):** the provider absence tests that cannot fail: `AbsenceContractsTest` of `pm-provider-http` `responseWithoutOptionalParts` (line 29; the base `Response` compact constructor has no null check on `etag`, `next`, `resetAt`), of `pm-provider-github` (`ReviewComment` line 32, `ReviewThread` line 40; the records declare no compact constructor), of `pm-provider-git` (`repositoryInfoWithoutHeadOrBranch` 44, `mainWorktreeWithoutName` 55, `refUpdateWithoutOldOrNewId` 67). Only `resultWithoutValue` (http, line 40), `failedGitResultWithoutValue` and `localOnlyRemotePolicyHasNoOrigin` can fail. The mapper in `GitHubClient.java:459-468` and the HTTP missing-ETag mapper (`HttpClient.java:214-227`) have no absent-case test; these are the review nitpicks the lane left unapplied. The narrative's "absent-case tests exist for the public record components" is not borne out for these.
- **L2-2 (minor):** no test pins the nullable `ProcessTreeTracker.rootStartedAt` (unreadable start at construction or at refresh); `ProcessTreeTrackerTest` covers a changed start only (`:146`).
- **L2-3 (not shown):** whether `plan-marshall-mcp` consumes the changed public runtime types; the sibling checkouts are outside the reading.
- **L2-4 (not shown):** the "mutation-after" run: commit `1f6bac90` is the 111-file bulk conversion with no contract test file; the one demonstrated failing run is a job output not readable here.
- **L2-5 (info):** every file the diffs touched is signature fallout (generator, `SonarClientTest`, `FakePmExec:174`, 12 developer guides); none unexplained.

**Layer 2 verdict:** findings — 2 minor, 2 not shown by reading.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `HttpClient.java:122` | `get(path, etag)` called with a null `etag` | before: `Optional.of(etag)` raised a `NullPointerException`; now a null `etag` silently becomes an unconditional `GET` (no `If-None-Match`). The parameter is non-null by contract, so this is a lost fail-fast, not a type-only change; the reviewer reply "type-only" was imprecise here. | minor |
| L3-2 | `AuditIndex.java:69`, `CredentialStoreAspect.java:130`, `TerminationProtocol.java:147`, `HarnessEnrolment.java:291`, `AuditChainVerification.java:106` | — | five `OptionalInt`/`OptionalLong` record components were kept, undisclosed, against "`Optional` is a return type only"; ADR 022 (landed later, in #347) treats `OptionalInt` as a value type but is scoped to `EffectiveRole.idleWakeups`. No ADR in this plan. | minor |
| L3-3 | `SubprocessJobRunner.java:573` | a worker launch | a leftover round trip `isEmpty() ? Optional.empty() : Optional.of(...)` that is fed through `orElse(null)`; the PLAN-110 constraint asked to remove such round trips. Harmless. | info |
| L3-4 | ten touched assertions | — | no message of 20 to 60 characters: `CredentialStoreAspectTest:293`, `ProviderVerificationTest:149`, `EnrolmentsTest:803`, `HarnessesSidecarCheckTest:117,118,137`, `WorkerRecyclingTest:130,151,286`, `WorkerSupervisorLaunchTest:121`. | minor |

Read and correct by reading: `ProcessTreeTracker` (`rootStartedAt` is a private final `@Nullable Instant` assigned once; `Objects.equals(…orElse(null), rootStartedAt)` keeps null equals null), `MachineConfigurationCodec` (writer conditions and reader mapping unchanged, stored bytes identical by reading), `LaunchComposer` and `WorkerLaunch.mcpConfig`, `GitResult.ok` and `Result.ok` (the `requireNonNull` guard is kept), `JGitOperations.worktreeRemove` (main worktree rejected before the `requireNonNull`), `AuditVerification` (reasoned S6218 suppression holds), `CredentialStoreChoice` (enum reference comparison equivalent), `Enrolments.Rebaseline` (the compact constructor still rejects null `file`, `current`, `settled`).

**Layer 3 not shown by reading:** L3-5 the serialised or logged forms of `ReviewComment`/`ReviewThread` (a null component prints `null`, not `Optional.empty`), which the assembly serialises; L3-6 build, coverage, integration and Sonar results, and byte identity of the stored forms, which need a run.

**Layer 3 verdict:** findings — 2 minor, 1 info (no NPE or format change found), 2 not shown by reading.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | `Optional` only as a return type | deviates | five `OptionalInt`/`OptionalLong` components (L3-2); no `Optional<T>` field, parameter or component remains |
| CLAUDE.md Code Standards | absence is returned as `Optional`, never a `@Nullable` return | conforms | only generated record accessors are `@Nullable` |
| CLAUDE.md Code Standards | `@NullMarked` package-info | conforms | no package added |
| CLAUDE.md Code Standards | no generic catch | conforms | `MachineConfigurationCodec.java:272` catches `IllegalArgumentException|NullPointerException`, both specific |
| CLAUDE.md Code Standards | `LogRecord` constants asserted with `LogAsserts` | conforms | no log call added |
| CLAUDE.md Code Standards | assertion messages of 20 to 60 characters | deviates | L3-4 (ten touched assertions) |
| CLAUDE.md Code Standards | test data from the generators | deviates | the new `OptionalAbsenceContractsTest` and four `AbsenceContractsTest` classes draw nothing from the generators and carry unlabelled literals; also the PLAN-V2-91 Wa83 constraint |
| CLAUDE.md Code Standards | no new hand-written fake | conforms | `FakePmExec:174` adapted only |
| CLAUDE.md Code Standards | records of four or more components carry `@Builder` | not_shown_by_reading | owned by PLAN-V2-108 |
| spec Standing | one ADR per design decision | deviates | no ADR from this plan for the kept `OptionalInt`/`OptionalLong` or the nullable-parameter API change |
| spec Standing | no format change (R13) | conforms | by reading; byte identity needs a run |
| spec D3 | `git grep` of the consumers | not_shown_by_reading | L2-3 |

## Metrics and Anomalies

- Tokens: `unknown` (opencode measured none; unobserved, not zero). `plugin_version` `unknown`.
- Duration: not supplied.
- Anomalies: the host `/tmp` ran out of inodes during the run (routed builds to `.plan/temp`); three builds hit the daemon bound (1083 s twice, 300 s once) and were re-run; `safe-merge` was attempted on the operator's selection and refused because `main` requires the merge queue, then the queue merged #342. A seed-sensitive `pm-core` `CoverageItemTest` failure (`coverage item name must not be blank`) appeared once on #343 and passed on rerun; it is in a module this plan does not own.

## Routing and Merge Behavior

- Review: the facts block carries none of the four review and Sonar numbers (`landing-check` lists `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`, `sonar_open_new_issues` as missing). `runbook_lane review-gate` on the merged heads: #342 `unresolved_review_threads: 0`, `review_body_findings_total: 3`; #343 `0` and `1`. The narrative's counts (3 inline threads on #342, 1 on #343) agree. `review_body_findings_handled` is **not re-verified**: the progress file has no `finding:` lines.
- CI/merge: #342 and #343 merged through the queue; queue state read from each PR: `pr_state: merged`.
- Sonar: the facts block carries `step.sonar-roundtrip.open_issue_count=0`; the public API read gives 0 open issues, gate OK on both `pullRequest=342` and `pullRequest=343`: equal.
- Surface collisions: none reported. The concurrently running 128 changed `pm-provider-ci/-github/-gitlab`; 99's #342 changed `GitHubClient.java` in the review-comment mapper and 128 did not touch that file.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#342, #343`
- [x] row `landing` stamped `landings/PLAN-V2-99.md`
- [x] row `plan_marshall_plan_id` stamped `v2-99-optional-conversion-runtime-and-providers`
- [x] `epic.md`: the Open Defect for missing landing facts counts this landing; a new Open Defect holds the Optional-conversion residues (L3 items of 97, 99 and 128) awaiting the operator's word
- [x] folds: L2-1, L2-2 and the generator literals into PLAN-V2-126; the assertion messages are owned by PLAN-V2-103 to 105
- [x] resume anchor and `queue-view.md` regenerated

## Follow-Ups

- L2-1, L2-2 and the literal test data: folded into PLAN-V2-126, Expected Surface extended in the same edit.
- L3-1 (`HttpClient.get` with a null `etag`), L3-2 (the five kept `OptionalInt`/`OptionalLong` components and their missing ADR), L3-3: code or decision residues; recorded as one Open Defect with the PLAN-V2-97 residues, no plan staged without the operator's word.
- L3-4: owned by PLAN-V2-103 to 105 (assertion messages of the runtime and provider tests).
