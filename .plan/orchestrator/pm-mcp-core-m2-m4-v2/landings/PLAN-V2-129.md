# Landing Analysis: PLAN-V2-129 — the code of the six deferred mechanisms is deleted

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #396 (merge `58b921af`) and #397 (merge `8fb23c4e`), both `merged` through the merge queue; the original #393 (133 files) was closed unmerged

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-12 from the message
> `plan-v2-129-deferred-code-removal-001.md` of the plan lifecycle (harness claude, model claude-sonnet-5-5/low, executor
> 0.1.2106). The message is a lead. The three layers were read by one read-only leaf from git at `origin/main` `00feac45`
> (no build, no test run); the orchestrator re-read the points marked "re-read"; the provider figures were re-read from
> the SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 capability specs, EARS and the deferred members of the intent record | partly | code, schema and fixtures are done and `non_goals` is 0 to 20; **the Done-when clause "the criterion equals its file at member level" is not shown by `IntentSchemaParityTest`**, which compares the top-level members (`:114-123`) and the criterion kinds (`:153-177`) and never the criterion members or the `verified_by` enum, and the plan left the test unchanged (F2). |
| D2 interfaces and key links of an outline deliverable | met | `OutlineSchemaParityTest` is stronger (a `deliverableMembers` test); the empty `OutlineValidator` grep is not shown by the diff. |
| D3 `system_one`, the judgment vote, the remote form | met in code | `EndpointProfile`, `HarnessEnrolment`, `ProfileValidator`, `RoleUse`, the four guards and the package documents changed; `EndpointProfileValidatorTest` has a refusal case for each deleted member; the loopback rules (https refused, port range, missing executable or identity) are still asserted. |
| D4 navigation figures and the metrics `attempt_end` | met | the metrics `AttemptEnd` is gone, `LossReason` is re-pointed to the queue enum, `MetricsSchemaParityTest` asserts no deviation; `AttemptEndParityTest` is deleted and the queue enum stays pinned by `TaskQueueSchemaParityTest:156`. |
| D5 the members already gone, the two stale class documents | met | `GradingOffer.runs` returns `runsPerItem()`; both Javadoc passages corrected. |
| D6 deletion safety and the documentation sweep | partly | the five ADRs and the package file are deleted and `doc/plans/README.adoc:24-27` shows the package `done`; two statements stay stale (`IntentSubmission.java:19`, ADR `core-store/049:18`); R06 asked for one new ADR per decision taken and the diff adds none (the check-7 relocation is a design decision). |
| Not in the declared surface | explained | `doc/adr/023` (amended), `runtime-guard.adoc`, `runtime-launch.adoc`, eight `*BuilderContractTest` files, `EndpointProfileGenerator` and its test, `EndpointRequestTest`, `EndpointFixtures`, `HarnessEnrolmentRecordsTest`, `PlanTokensTest` and two specification text edits (`hypermedia-format/02`, `phase-workflows/08`): follow-ups of the deletions. |

"133 files" is consistent (the two parts list 138 entries, five of them in both). Not shown by reading: the 80 % per-module coverage and the full local runs (the narrative says `verify -Pcoverage` and the integration tests ran locally on the pre-fix head only and in CI on all three pull requests; the whole-repo `verify -Ppre-commit` with 10033 tests ran on the final head).

## Three-Layer Analysis

Read at `00feac45` (head of `origin/main`, identical to the working tree) by one read-only leaf (`execution-context-level-3`, 85 tool uses); the orchestrator re-read F2, F3, F1 and the old writer at their `file:line`.

### Layer 1 — Specified

**F3 (major, re-read): the spec's deletions break the reading of every stored record, and it never names that.** The old writer wrote `"capability": null` on every criterion (`IntentCodec` before `58b921af`, `:245-247`, re-read), `interfaces` and `key_links` on every deliverable of `outline.json`, and `attempt_end` on every metrics row; the readers end in `default -> throw undeclared(field)` (`IntentCodec.java:283,308,347,376,397`, `OutlineCodec.java:250,281,306`, `MetricsCodec.java:440`, re-read). Every `intent`, `outline` and `metrics` file written before the landing, and findings with the source `capability_tests`, is therefore refused whole. The format version stays 1 and there is no migration, which R13 and the pre-1.0 rule allow; but the spec's Standing Constraints list four intended behaviour changes and this fifth is in none of them and in no sentence of the narrative. Whether any deployed store exists is not shown. F1 (minor, re-read): the premise that the specification "already states the reduced record" is false in one place: `doc/specification/store-schemas/06-task-and-references.adoc:74` still names the rendered headings `**Interfaces:**` and `**Key links:**` of `outline.md` (the line says they are "never read back"), which `OutlineRenderer` no longer emits; `phase-workflows/02:88` is correct. F2 above (the Done-when that its own test cannot show).

### Layer 2 — Implemented

Conformance: no generic catch added, no new log record (`PM_RUNTIME-39` removed from the holder and from `doc/log-messages/pm-runtime.adoc`, no record left without a logger), new tests carry assertion messages of 20 to 60 characters, use `Generators`, no AAA comments. Deviations (minor): added lines over 120 characters, mostly Javadoc, in `IntentRecord.java` (191), `RoleUse.java` (148), `EnrolmentCheck.java` (140), `RoleGraderTest.java` (154), `MetricsStoreTest.java` (136); not enforced by the build. **F4 (minor):** the plan's "review fix" for check 7 adds `IntentAcceptance.nonGoalRefusal` (`:183`, `:280`), a refusal on `submit:refine` for a record without non-goals; spec A4 said nothing would enforce it; the change is grounded in `phase-workflows/01:480` and tested (`IntentRecordTest`), and it is a scope addition beyond the four listed behaviour changes. **Deleted tests**, each checked: `CapabilitySpecTest`, `CapabilitySpecBuilderContractTest`, `EarsFormTest`, the ten `Judgment*`/`SystemOne*` test classes and `JudgmentTestTls`, `NavigationFiguresTest` and the three navigation builder-contract tests, `TokenLaneCredentialTest` and `AttemptEndParityTest` asserted deleted behaviour only; the shrunk tests (`EndpointProfileValidatorTest`, `HarnessEnrolmentStoreTest`, `LaunchComposerTest`, `HarnessVerifierTest`, `AgentStartedDetectorTest`, `EndpointHarnessTest`, `OutboundCallsArchTest` with its non-vacuity check, `QualificationValueTest`, `RoleGraderTest`, `RoleCompositionTest`, `MetricsStoreTest`, `IntentRecordTest`) lost remote or judgment cases only, and the replaced assertions are stronger. **Coverage lost: one item** — nothing proves that a reader refuses (or ignores) a record that still carries a deleted member (F3), and `IntentSchemaParityTest` has no criterion-member and `VerifiedBy` assertion (F2). The 31 weakened-test findings (109 accepted removal rows) were closed by operator override at the functional review (the narrative).

### Layer 3 — Correct

No defect in the deleted logic (F7): the loopback validator, the enrolment guards and `RoleUse` match the spec (`vote` and `decision` now require `read_only`, matching `model-work/01:214`); check 7 is preserved for `submit:refine`. **Dangling names: none** (checked clean at the head by a word-boundary search): `CapabilitySpec`, `CapabilityIndex`, `EarsForm`, `CapabilityLink`, `CapabilityChange`, `CAPABILITY_TESTS`, `Judgment*`, `SystemOne*`, `JUDGMENT_STAGES`, `Navigation*`, `SkillResidency`, `TokenLaneCredential`, `SecretRegistry`, `SCREENER_RUNS`, `JUDGMENT_TASK_*` and `judgmentTaskCompleted` remain only in history notes (`doc/adr/009:27`, `runtime-credentials.adoc:60-61`, `BuilderCheckTest.java:118` as a negative assertion of the string `capability_index`, `doc/adr/023`); `META-INF/native-image` names no deleted class; no `*.schema.json` of the three modules names `interfaces`, `key_links`, `locality`, `credential_key` or `capability_tests`; every relative link in this repository resolves except three in `ActivityJournal.java:227,230,287` that predate the landing; the retired ADR numbers are recorded in `core-queue.adoc:85` and `runtime-endpoint.adoc:24`. **F5 (minor):** `ProfileStages.EndpointVerification` (`:693-706`) keeps judgment-era logic: `stages.contains(CLASSES) != (classes != null)` with `stages` able to equal only `ENDPOINT_STAGES`, so a null `classes` throws `IllegalArgumentException("The classes stage is present exactly when the stages name it")`, a stale message, and the Javadoc `@throws NullPointerException` (`:693`) is false; the `stages` component is redundant. **F6 (minor):** `RoleTopicsTest.java:32-33` still says a judgment role is graded on decision and question alone. **The six advisories the narrative names, verified:** `IntentSubmission.java:19` ("never the record or its checks") is false since check 7 depends on the entry point; `IntentSchemaParityTest` coverage is real (F2); the `ProfileStages` message is real (F5); `pm-workflow/doc/adr/workflow-model/002:26` still lists "the screener's repeated runs" among what `RoleGrader` does; `RoleTopicsTest` Javadoc is real (F6; the Traceability line shows nothing stale); `pm-core/doc/adr/core-store/049:18` still says a criterion bound to a capability is folded before the capability specs are written. **F9 (major lead, cross-repository, runtime effect not shown):** the plan's deletion-safety sweep searched type names, so wire-field names escaped it: the client contract `pm-mcp-clients/pm-api/.../intent/OutlineResource.java` (`:60-61,85-86,205-206,228-229`) still carries `interfaces`, `key_links` and `missing_key_link`, with the `pm-api` test `IntentContractTest.java:351` and the mock scenarios `fully-onboarded.json:1050` and `running-work.json:1392`, and `plan-marshall-mcp/doc/design/shared/intent-and-outline-texts.adoc:229` names `intent.outline.key_links`; the `OutlineRecord` that `pm-core` writes no longer has them, so the two sides drift if any code maps one to the other (the leaf found no mapper in `plan-marshall-mcp`; newer commits there were not fetched). `client-profiles/04-judgment-endpoint.adoc` exists in no repository at `origin/main` (the specifications moved to `pm-mcp-core`); links to it remain at `doc/specification/credentials-and-enrolment/03-audit-and-outbound-https.adoc:43` (dead before the landing: part 1 removed only the sibling bullet) and in the documentation repository's `your-agent-is-mine.adoc:89` (a pinned-SHA link, which still resolves); `doc/plans/harness-renames.adoc:16` still names `JudgmentEndpoint` conditionally; the `judgment-votes` package row (`doc/plans/README.adoc:234-235`) plans a mechanism whose protocol is deleted.

## Conformance to the Project's Own Rules

See Layer 2. Neither review bot reviewed the change: #393 (133 files) was refused by CodeRabbit (over its 100-file limit) and Sourcery (over 150 000 diff characters), the operator chose to split, and #396 and #397 were rate-limited or size-refused; the review-gate counts in the message are `unknown` (no finalize step records them). So the only reviews of this 7 300-line deletion are the plan's own functional and code review (six and eight advisories, in the plan's decision log) and the operator's override of 31 weakened-test findings.

## Unverified leads

- Whether any deployed store holds `intent`, `outline` or `metrics` files written before the landing (F3).
- The assembly (`plan-marshall-mcp`, `pm-mcp-server`) and the client contract: the sibling checkouts' `origin/main` refs lag (`plan-marshall-mcp` local `bea0eff`, a dry-run fetch shows `e2707f6` ahead; `plan-marshall-documentation` likewise); consumers in newer commits are not shown.
- The 80 % per-module coverage, the local `verify -Pcoverage` (pre-fix head only) and the Sonar gate beyond the figures below.

## Metrics and Anomalies

`total_tokens` 27 786 353 (a lower bound: several dispatch-boundary rows carry zero or unmeasured usage), `total_wall_seconds` 54 749 (about 15.2 h), harness claude, claude-sonnet-5-5/low, plugin 0.1.2106; `merge_state` and `cleanup_owed` `unknown` (branch-cleanup recorded no typed facts; branches and the worktree observed removed); the retrospective step `plan-marshall:plan-retrospective` was skipped, so no candidate lessons exist; `surface_delta` unmeasured. Sonar over plain HTTP: `pullRequest=393`, `396` and `397` each 0 open issues, gate `OK`; the last analysis of `main` read at `1283710f` (83 open; 86 at `a4c2fa86`).

## Routing and Merge Behavior

Two PRs (#396, #397) through the merge queue after the operator's split of #393; no `candidate-lesson` message (the retrospective was skipped), so nothing is routed to `lessons-routing`.

## Reconciliation Actions

Row PLAN-V2-129 `running` -> `shipped`, `pr` `#396, #397`, `landing` `landings/PLAN-V2-129.md`, `plan_marshall_plan_id` `plan-v2-129-deferred-code-removal`; message `plan-v2-129-deferred-code-removal-001` archived; Watch entry in `epic.md`; decision log.

## Follow-Ups

Placed 2026-11-12 under the operator's rule "every leftover goes into a staged plan":

1. F3 (the refusal of every stored record, ask-at-refine, recommendation keep the refusal and say so ~65%, with a test per codec), F5, the stale statements (`IntentSubmission.java:19`, ADR `core-store/049:18`, ADR `workflow-model/002:26`, `store-schemas/06:74`, `harness-renames.adoc:16`, the dead link at `credentials-and-enrolment/03:43`, the `judgment-votes` row) -> PLAN-V2-114 item 18.
2. F2 (the criterion-member and `VerifiedBy` parity assertions), F6 and the over-long lines -> PLAN-V2-125.
3. F9 (the client contract `OutlineResource` and the `plan-marshall-mcp` design text still carry the deleted wire fields): a finding for `pm-mcp-clients` and `plan-marshall-mcp`, outside this repository, no plan of this epic carries it; reported to the operator (a change to `pm-api` is a pull request in `pm-mcp-clients`).
