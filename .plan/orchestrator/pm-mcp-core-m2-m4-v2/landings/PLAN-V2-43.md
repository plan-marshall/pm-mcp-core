# Landing Analysis: PLAN-V2-43 — The machine's settings, the automatic onboarding and the plan axes

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #462 (merge `27378186`, 2026-11-16T15:12:25Z)

> Landing record for one shipped plan, **partly delivered**. Written by `analyze` (inbox scan) from the lane's landing message
> `rb-v2-43-machine-settings-and-axes-001.md` (revision 1, amended 15:20:41Z). The lane reports 0 of 5 deliverables fully met; the
> reading below confirms that and finds the delivered half sound. A claim of the message is a lead; the numbers were re-read.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D2 (Task 1) onboarding sub-workflows in the machine scope | dropped | no unit, no aspect sub-workflow and no `onboarding-contract` fixture replay in the diff; a content search finds no code or fixture for `settings.choose` or `auto.result`; `onboard_run` appears only in `WorkflowStatusCodec`, `MachineOnboarding`, `SetupGate` (earlier plans) |
| D7 (Task 8) automatic onboarding, `change` on `auto.result` | dropped | no proposal or result model in `pm-workflow`; `auto.result` occurs only in the documentation repository |
| D8 (Task 9) the machine's settings | shipped-modified (two of seven) | `MachineConfigurationStore.writeDocumentsMode` (`:149`) and `writeCleanupDurations` (`:171`) with the revision rule; the states, consent withdrawal, provider re-verification and credential rotation are absent. Every writer that exists raises `SettingsConflict` (documents mode, cleanup, providers, consent, catalogue, role assignments); `CredentialStoreChoice` carries a revision and does not reference it (not shown) |
| D10 (Task 11) axes and overrides | shipped-modified (store half) | `overrides.json` model, `PlanOverridesCodec`, `PlanOverridesStore` under the plan transaction and revision rule; the composition half (landing variant into the finalize step list, a composed file per variant, project and machine axis-level readers) is not delivered; `doc/Traceability.adoc:726` (PM-WF-25) says the same |
| D11 (Task 12) validation of the landing | shipped-modified (engine half) | `LandingVariantValidation.validate` (`:77-86`) refuses `landing.pull-request` without a provider and `landing.main` on a protected base branch with a reason; `PlanInstanceComposer.compose(request, project)` skips `fallback` and `empty_harvest`; the call sites at a project's setup and a plan's settings are the assembly's (ADR `workflow-plan/021`) |

The Done-when is met for two clauses only: two changes against one revision (documents mode, cleanup durations, `overrides.json`: the
first recorded, the second refused with `conflict` and the current value) and a landing variant refused with its reason where it does
not fit a project. The onboarding run, the automatic run, the settings branches and the rotated credential are not met.

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`); the orchestrator
re-read the points marked "re-read". By reading, no build, no test run, no write.

- **Dispatch:** one read-only leaf, own context — reason: 29 changed files across `pm-core`, `pm-runtime` and `pm-workflow` (stores, codec, composer, ADRs), read against the spec and the project rules.
- **Re-read by the orchestrator:** the `@Test` counts of five test classes; `doc/plans/machine-onboarding-unit.adoc` and its `README.adoc:84-86` row; `MachineConfigurationStore.java:186-204` (`transact`); `LandingVariantValidation.java:36-95`; `PlanInstanceComposer.java:125-150`. The remaining `checked` points (the `PlanOverridesCodec` escapes, the CleanupDuration bounds, the parity tests, the conformance rows) stand as the leaf's reading.
- **Verdict:** layer 1 findings (4); layer 2 findings (5); layer 3 findings (8, all minor); conformance: one deviation.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The premise "the composition side of D10 shipped earlier" holds for what Traceability records as implemented (axis binding from the nearest level, the `plan_default`/`merge_by_operator` prunes, the `override` event, `overrides.json` in the plan-facts digest). The lane's contradiction is right only for the landing variant composed into `phase-6-finalize`, the per-variant composed files and the axis-level readers (`Traceability.adoc:726`: "Not implemented yet"). The package file's own Task 11 is more careful than the spec ("not yet built" for those) | checked | confirms (partly) | `doc/Traceability.adoc:726`; `doc/plans/machine-onboarding-unit.adoc:25` |
| L1-2 | R01 ("the last of the three removes the package file and its index row") cannot be met by a partial delivery: at the merge `doc/plans/machine-onboarding-unit.adoc` exists untouched and its row is still `open` | checked (re-read) | confirms | `doc/plans/README.adoc:84-86` |
| L1-3 | The package file says "Where: pm-workflow (the onboarding's sub-workflows ... the settings)" while the State note makes the real units `plan-marshall-mcp` resources: the spec is internally inconsistent about where D2/D7/D8 live | checked | confirms | `machine-onboarding-unit.adoc:8` |
| L1-4 | The Done-when branches for D2, D7 and the settings states (re-check, withdrawn consent asked again, rotated credential, `change` on `auto.result`) are not satisfiable in this repository without a proposal vocabulary and the units; two-changes-against-one-revision, the writers and the landing refusal are satisfiable here | checked | confirms | `machine-onboarding-unit.adoc:42` |

**Layer 1 verdict:** findings — 4

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D8 writers | `MachineConfigurationStore.java:149,171`; `transact` `:193-203` throws `SettingsConflict` with code, status 409 and the current value | `MachineSettingsWritersTest.documentsModeFirstChangeWins` (`:116`), `cleanupFirstChangeWins` (`:134`) can fail | the lane says **9** tests; there are **8** (`@Test` count, re-read) | checked (re-read) |
| D10 store | `PlanOverridesStore.java:93` (one write per lock transaction `:103`), `PlanOverridesCodec` | `PlanOverridesStoreTest.firstChangeWins` (`:134`; 6 tests, as claimed), `PlanOverridesSchemaParityTest` (3, as claimed) | the lane says **6** tests in `PlanOverridesCodecTest`; there are **5** (re-read) | checked (re-read) |
| D11 engine half | `LandingVariantValidation.java:77-86`, `PlanInstanceComposer.java:131-139` | `LandingVariantValidationTest` (4), `PlanInstanceComposerTest.Landing` (3, `:1165-1206`) | the composer-level tests exercise only `landing.pull-request`; `landing.main` on a protected branch is tested in `LandingVariantValidationTest` only, not through `compose(request, project)` | checked |
| ADR 069 premise | merged text of `core-store/069` Alternative 3 agrees with the code | - | the "false premise corrected in round 1" is not visible in the single merge diff | not shown by reading |
| package file | `doc/plans/machine-onboarding-unit.adoc` untouched though Tasks 9, 11 and 12 are partly landed: the next plan reads stale task text | - | stale | checked (re-read) |

Files the diff touched that no deliverable explains: none (29 files; spec pointer notes in `state-catalogue/14` and `workflow-dsl/09` and the ADR 018 row are classification updates). No POM, dependency or `LogRecord` change; none of the new classes logs.

Not shown by reading: the mutation results at `edcac425`, `9804c6a1`, `c4fc31cd` (the commits exist as ordinary feature commits), the total of 10,765 tests, the CodeRabbit/Sonar state at each head.

**Layer 2 verdict:** findings — 5

### Layer 3 — Correct

The revision check and the write are one transaction under the machine-config lock, and the write is temp file plus `ATOMIC_MOVE` (`AtomicStoreFile.java:81-130`): no check-then-write gap within a JVM (re-read `transact`). Unrelated members are copied through and a partial `cleanup` member reads with per-member schema defaults (`MachineConfigurationCodec.java:535-546`). Durations below 7 days and a reminder below 1 are refused on write and read.

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `PlanInstanceComposer.java:147` | a caller that uses `compose(request)` (the overload without a project) | an unfit landing variant is bound unseen: the opt-in check is the forget-the-call fail-open that ADR `workflow-plan/021` Alternative 3 rejects for the setup code (re-read) | minor |
| L3-2 | `LandingVariantValidation.java:41` | `Project(boolean pullRequestProvider, boolean baseBranchProtected)` has no unknown state; a caller maps a failed provider lookup to `false` | `landing.main` passes on a protected base branch; "protected" is whatever the caller reports (re-read) | minor |
| L3-3 | `PlanInstanceComposer.java:131` | a waiver or replan composition for a running plan while the base branch has become protected | refused with `invariant_failed` for a variant that was valid at the plan's start (re-read) | minor |
| L3-4 | `PlanOverridesCodec.java:165,191,311,360,432` | harness values and integration names are validated with `RoleName.matches` although `HarnessId` exists and documents that the grammars stay apart | a change of `HarnessId` does not reach `overrides.json` | minor |
| L3-5 | `PlanOverridesStore.java:63` | a corrupt or foreign-version `overrides.json` | `read()` and so `write()` throw `StoreRefusal`; the store offers no repair path, so the operator cannot set a value again | minor |
| L3-6 | `PlanOverridesCodec.java:439` | a model or base-branch name of 128 code points with astral characters | `String.length()` counts UTF-16 units where the schema's `maxLength` counts code points; the value is refused | minor |
| L3-7 | `PlanOverridesSchemaParityTest.java:83` | the axis variant pattern and the role/harness/integration name pattern drift from the schema | the parity test compares the provider and device patterns only, so the drift goes unseen | minor |
| L3-8 | `MachineConfigurationStore.java:189-191` | the Javadoc of `transact` documents `@param compose`, the parameter is `composer`; the untyped `Function<..., Object>` with an `(Object)` cast weakens the conflict-value contract | doc defect, no behaviour | minor |

**Layer 3 not shown by reading:** a real two-writer race and the cross-JVM lock exclusion (the conflict tests are sequential); seed reproducibility (`PlanOverridesStoreTest:56` calls a static generator outside the per-test controller); any downstream overflow of a cleanup duration (no upper bound).

**Layer 3 verdict:** findings — 8 (all minor)

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md | records of 4+ components carry `@Builder`; `@NullMarked`; `Optional` only as a return; no generic `Exception` catch | conforms | `Snapshot`, `Document`, `Replacement`, `CleanupDurations`, `PlanOverrides` carry `@Builder`; the catches are `IOException`, `IllegalArgumentException` (and `IAE\|NPE` at `PlanOverridesCodec:280`, a smell, not a forbidden generic) |
| CLAUDE.md | no new dependency, no POM change; logging only through the holder | conforms | the diff stat lists no `pom.xml`; no new logging |
| CLAUDE.md | JUnit 5, EasyMock only, no new hand-written fake; no AAA comments; assertion messages 20-60 characters | conforms | new tests reuse `TestLocks`, `RecordingLockManager`, `AuditSinkDoubles`; every sampled assertion carries a message (the leaf counted by eye) |
| CLAUDE.md | the last plan of a package removes its file and index row | deviates | package file and `README.adoc:84-86` remain, correctly for a partial delivery; the task text is stale |
| CLAUDE.md | full verify, coverage, Sonar | not_shown_by_reading | needs a run |

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none). `landing-check` `complete: false` for `total_tokens` only (`plugin_version` was supplied: `0.1.2135`).
- Model `deepseek-v4.1-flash/high`, harness `opencode`, guard none (unenforced). Steps: `self-review:budget-exhausted` (three rounds, 18 + 8 + 2 findings, no fourth round), not `clear`.
- Anomalies: the lane admits shell file reads throughout and one `python3` heredoc edit (breaches of the lane's shell rule, unenforced on this harness), a commit trailer corrected before push, two `LspClientTest` timeouts under concurrent load (green in isolation), a stale `target/test-classes` fixture; builds waited up to about 26 minutes in the machine queue.
- Surface: no collision observed with the plans in flight; the planned overlap with PLAN-V2-82 (2 files) is still open.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `054b8d1c` with 10 findings, all handled (5 fixed in `1ed10e32`, 2 answered as owed to code generation / a reader registration, others documented); its review is the review at any head (standing authorization). Sourcery (optional) refused, hard quota. **Re-read against the provider:** `unresolved_review_threads` 0 = 0; `review_body_findings_total` 10 = 10; `review_body_findings_handled` 10 re-verified from the progress file (10 `finding:` lines). The post-merge `review-gate` reports 0 handled for want of those lines in the provider.
- **False claims, minor:** the lane states 9 tests in `MachineSettingsWritersTest` (8 found) and 6 in `PlanOverridesCodecTest` (5 found). The lane also states "the spec says implemented" contradicts Traceability; the package file does not say that (L1-1).
- CI/merge: merge queue, `build` green on every head, merge-group success; branch deleted.
- Sonar: `sonar_open_new_issues=0` against the measured 0 open issues and 0 hotspots to review on `pullRequest=462`; matches.

## Reconciliation Actions

- [x] row `status` -> `shipped` (partial: the package `machine-onboarding-unit` stays `open`)
- [x] row `pr` stamped `#462`
- [x] row `landing` stamped `landings/PLAN-V2-43.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-43-machine-settings-and-axes`
- [x] epic.md: queue annotation for 43; Watch for the dependents; Open Defect for the layer-3 minors
- [x] remainder staged as PLAN-V2-153; layer-3 minors routed into PLAN-V2-114 item 36
- [ ] resume anchor and `queue-view.md` (written at the end of the drain)

## Follow-Ups

- **PLAN-V2-153 `machine-onboarding-remainder`** (staged): D2, D7, the settings states of D8, the composition half of D10, the call-site contract of D11, and the correction of the package file's task text; it removes the package file and its index row (R01) when done.
- **PLAN-V2-114 item 36**: L3-1 to L3-8.
- **Dependents of PLAN-V2-43** (PLAN-V2-17, 21, 22, 24, 69, 73, 74, 133 name it in prose): each needs a different delivered half; the gate re-reads which one at each emit. PLAN-V2-133 reads `sources.documents_mode`, which this landing wrote.
- Owed by the lane's own residue and not new: the schema-vocabulary code generation and a reader registration for `CODEC_BACKED` (the two answered CodeRabbit findings).
