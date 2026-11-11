# Landing Analysis: PLAN-V2-108 — builders for the records of pm-workflow, pm-runtime and the providers

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #359 (merge `af3a8c485637288ae20e92fd22aa9ab4914c42fe`, state `merged` per the CI abstraction)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-10 from the runbook-lane
> message `rb-v2-108-builders-for-records-workflow-runtime-providers-001.md` (harness opencode, model
> deepseek-v4.1-flash/high). The message is a lead. The three layers were read by one read-only leaf from git at
> `af3a8c48` (no build, no test run); the orchestrator re-read the points marked "re-read" at their `file:line`;
> the provider figures were re-read from GitHub (`ci pr view`, `runbook_lane review-gate`) and the SonarCloud
> public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 find and annotate, per-record test | shipped | Every record of four or more components in `src/main` of `pm-workflow`, `pm-runtime` and the providers carries `@Builder`, except `PlanFacts`, which keeps its hand-written `Builder` (`PlanFacts.java:183`, `:351`, re-read); none below four components is annotated. 126 `@Builder` lines were added for 127 newly annotated records; 105 test classes were added and every newly annotated record has a builder test. |
| D2 call sites | shipped-modified (not met as the Done-when words it) | The lane reports two positional calls remaining. The leaf found 17 in `src/main` outside codecs and the records' own factories, and the orchestrator re-read the sites in `ModelCaller`, `LaunchComposer`, `ActivityRecorder`, `WaitSelection`, `JudgmentClient`, `EndpointEnrolment` and `ProviderVerification`: each passes four or more positional arguments that are not same-named variables. The spec's Done-when has no exemption for same-named calls, which its Deliverable 2 text has. |
| Done-when: PR body gives per-record counts | shipped | The body lists them per module and record (read through `ci pr view`); the figures differ slightly from the leaf's count (runtime 54 added builder lines against 53 claimed; provider 37 against 19, the 19 matching `pm-provider-git` alone). |

Facts block: `deliverables_total=2`, `deliverables_done=2`. The lane reads Deliverable 2's own exception into the Done-when; read by the Done-when's words, D2 is not done. The disagreement is a spec-wording contradiction, and it also shows the lane's "two remain" count is false.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), reason: 238 changed files across three modules and a per-class question; no build.
- **Verdict:** specified — findings; implemented — findings; correct — no defect found by reading.

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence |
|---------|-------|---------|----------|
| L1-1 | The Done-when says every record carries `@Builder`; `PlanFacts` has a hand-written `Builder` and `builder()` the spec never considered, so it cannot hold literally. | checked, refutes | spec Done-when; `PlanFacts.java:183`, `:351` (re-read) |
| L1-2 | Deliverable 2 exempts same-named calls; the Done-when does not. The two are inconsistent. | checked, refutes | spec Deliverable 2 and Done-when (read) |
| L1-3 | The OBSERVED counts are stale: providers 15 across five modules against 22 or 23 across six (the lane says 22, the leaf counts 23; not re-counted by the orchestrator), builders 16 in core and 1 in runtime against 33 in core and 0 in runtime. Workflow 64 and runtime 49 hold. | checked, refutes | leaf count at `af3a8c48` |
| L1-4 | "Optional components are gone" cannot be confirmed: `CredentialStoreAspect.Transition` (annotated by this PR) still carries an `OptionalInt` component; the Depends-on line names PLAN-V2-83, 98 and 99 while the premise names 97 to 99. | checked, refutes | leaf read (`CredentialStoreAspect.java:131`) |
| L1-5 | `pm-core` still has 108 records of four or more components without `@Builder`; whether a sibling plan covers them is not shown by this reading. | not_shown_by_reading | — |

**Layer 1 verdict:** findings — the Done-when needs the exemption stated, `PlanFacts` named, and the counts corrected.

### Layer 2 — Implemented

- **Confirmed:** annotation completeness within scope (`GitOperations.java:274` among the cases); 105 added test classes, 209 test tokens (the lane says 191); null-refusal tests mirror the compact constructors; test call sites are untouched except four whitespace edits; the conversions pair with removed positional calls (273 builder chains, 257 paired, the 5 unpaired read by hand and faithful).
- **Refuted — the "two calls remain" claim:** re-read at `af3a8c48`: `ModelCaller.java:95` (10 positional arguments to `ModelQueue.Enqueue`), `:116` (5, `Close`), `:117` (4, `Closed`); `LaunchComposer.java:250` (11, `PromptRequest`); `ActivityRecorder.java:92` (8, `OffTrackReport`); `WaitSelection.java:60` (4, `ModelWait.Progress`); `JudgmentClient.java:160`, `:163`, `:166` (4, `EndpointClient.Response`; the leaf also lists `:195` and `:200`, not re-read); `EndpointEnrolment.java:70`, `:74` (4, `Refreshed`; `:96` and `:103` not re-read); `ProviderVerification.java:218`, `:242` (4, `Verification`). The leaf counts 17 sites in total.
- **Weak test:** `WorkerGenerationRecordsTest.java:85` compares `toString()`, which prints `generationId`, `role` and `lifecycle` only (`WorkerGeneration.java:110`); `equals` compares the id alone (`:100`). It proves 3 of 18 components, and the class Javadoc (`:33`) wrongly says it proves the other seventeen (re-read). The risk is low because Lombok cannot mis-wire a builder, but the stated justification is false.
- **Files no deliverable explains:** `JournalRecoveryTest`, `OrderedWriteTest` (pm-core), `ModelCallerTest`, `TaskProtocolReOfferTest` (pm-workflow): whitespace-only gate rewrites. No non-Java file is in the diff.
- **Surface delta:** declared surface is the three `src/main` trees; the realized footprint is 238 files, 109 of them tests (not declared, expected for the per-record tests); the remaining difference comes from the unresolved provider glob `pm-providers/*/src/main/java/`. No `pom.xml` and no file outside the three modules and their tests changed, except the two `pm-core` test files above.

**Layer 2 verdict:** findings — the annotations and tests are delivered, but the lane's "two remain" claim is false and Deliverable 2's Done-when is not met; one test proves less than it says.

### Layer 3 — Correct

- The builder conversions are faithful: no chain drops or reorders a component, builders call the canonical constructor so compact-constructor refusals are unchanged, no `@Builder.Default`, `@Singular` or `toBuilder` was added, and no `@Value` or `@Data` entered the diff. The only chains with omitted components pre-exist in files the PR touched.
- **Rule deviation:** 82 `assertThrows` calls in the added tests carry no message (the orchestrator re-counted 82 `assertThrows` occurrences in the added test files and read `GitOperationsRecordsTest.java:75` as one such call), against the project rule that every assertion carries a message of 20 to 60 characters. The other 209 assertions carry one in range.
- Not shown by reading: the pass counts, `verify`, coverage, the Javadoc of the generated builder classes, and native-image metadata.

**Layer 3 verdict:** no defect found by reading; one rule deviation (82 messageless `assertThrows`), build and test results not shown by reading.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | a record of four or more components carries `@Builder` | deviates in part | `PlanFacts` (hand-written builder, reasonable); 108 `pm-core` records are out of this plan's scope |
| spec | no call outside codecs and factories passes four or more positional arguments | deviates | 17 sites, see Layer 2 |
| CLAUDE.md Code Standards | every assertion carries a message of 20 to 60 characters | deviates | 82 messageless `assertThrows` |
| CLAUDE.md Code Standards | JUnit 5 only; no Mockito, PowerMock, Hamcrest; no AAA comments | conforms | keyword scan |
| CLAUDE.md Dependencies | no dependency or plugin added | conforms | no non-Java file in the diff |
| CLAUDE.md Code Standards | test data from `cui-test-generator` | conforms | a few contract literals carry no one-line note |

## Metrics and Anomalies

- Tokens: `unknown`; `plugin_version` `unknown`; executor `0.1.2088`, lane skew `match`. The landing-check reports `complete: false` (`total_tokens`, `plugin_version`), folded into the standing Open Defect on runbook-lane landings.
- One daemon bound expiry (first whole-reactor run at the 300 s default, re-run with an explicit bound); queue waits of 15 to 50 minutes; no fallback to in-process, no `killed` jobs.
- Lane deviations it stated itself: one sub-agent ran a no-op `true` through the shell, another one read-only shell `grep`, a third wrote 12 empty placeholder files into the main checkout (removed, nothing committed).
- One flaky failure at the merge head (`LspClientTest.stubborn`, a 2 s fixture-server timeout), green on rerun.

## Routing and Merge Behavior

- Review: required reviewer CodeRabbit refused every head (234 files exceed the limit of 100); Sourcery refused (diff over 150,000 characters). The operator, asked through the lane, chose "Merge on the recorded evidence": an explicit, recorded bend of the required-reviewer rule. Provider re-read: `review-gate` reports 0 unresolved threads and 0 review-body findings (`merge_allowed: true`); the SonarCloud search of `pullRequest=359` lists 0 open new-code issues and the quality gate is `OK`. All equal to the facts block; no false claim among the four numbers (`review_body_findings_handled` is 0 of 0).
- CI/merge: #359 `merged`, merge commit `af3a8c48`; `mergeable` and `merge_state` read `unknown` from the abstraction after the merge, so the facts block's `merge_state=merged` rests on `state: merged` and the merge commit.
- Surface collisions with the epic's plans: PLAN-V2-100 (running, builders for `pm-core`) touches none of the three trees; the two `pm-core` test files that 108 changed are whitespace-only. The staged conformance plans PLAN-V2-101 to 105, 125 and 126 (assertion messages and test conformance) now start from the 82 messageless `assertThrows` in 108's 105 new test classes: they should include these classes. PLAN-V2-124 and 129 were already sequenced clear of 108's files.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#359`
- [x] row `landing` stamped `landings/PLAN-V2-108.md`
- [x] row `plan_marshall_plan_id` stamped `v2-108-builders-for-records-workflow-runtime-providers`
- [x] the incomplete landing facts fold into the existing Open Defect on runbook-lane landings (`epic.md`); a Watch records the residue
- [x] message archived after the disposition was persisted
- [x] resume anchor and `queue-view.md` regenerated
- [ ] residue plan: not staged; put to the operator (see Follow-Ups)
- [ ] layer-1 verdicts on the spec's claims: not persisted, the plan has shipped and its spec leaves the queue

## Follow-Ups

- **Residue of PLAN-V2-108 (one residue plan, runbook lane by the operator's rule for residue and small plans), awaiting the operator's word:** move the 17 remaining positional call sites; replace `WorkerGenerationRecordsTest`'s `toString` comparison with a check that proves all 18 components and correct its Javadoc; give the 82 messageless `assertThrows` a message; state the call-site exemption in the Done-when and name `PlanFacts` in the spec's wording. The `pm-core` records without `@Builder` are not part of it unless the operator says so.
