# Landing Analysis: PLAN-19 — Profile loader and validation

epic: pm-mcp-core-m2-m4
workstream: WS-05
pr: #137 (https://github.com/plan-marshall/pm-mcp-core/pull/137), merged as squash `9c02edc` on 2026-10-22 (the final head of the branch was `a6e7ca6`)

> Landing record for one shipped plan. Lives at `landings/PLAN-19.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> reconciliation contract.

Source of the landing: inbox message `rb-19-profile-loader-001.md` (`kind: landing`, revision 1, amended 13:25Z, sender `rb-19-profile-loader`, `/plan-marshall-runbook-lane` on opencode, plan-reported). `inbox landing-check`: `complete: false`, `missing_keys: [total_tokens]`; `landing-facts` block present (`deliverables_total=4`, `deliverables_done=3`, `pr=#137`, `merge_state=merged`, `cleanup_owed=false`, `harness=opencode`, `model=deepseek-v4.1-flash`, `steps=…,self-review:budget-exhausted,…`). No candidate-lesson messages came with it.

## Deliverable Fidelity vs Spec

Read at `9c02edc`. The orchestrator ran the profile test classes of `pm-core` on `main` (`Profile*Test`, `CanonicalJsonTest`, `ShippedProfilesTest`): **90 tests, green** (14 in `ProfileLoaderTest`, 58 invocations in the refusing nest of `ProfileValidatorTest`, 8 `ProfileStagesTest`, 6 `CanonicalJsonTest`, 2 `ProfileNeutralityArchTest`, 2 `ShippedProfilesTest`). The landing's "43 fixtures" for `ProfileValidatorTest` is not reproduced: the refusal stream holds 46 `Arguments.of` rows and runs 58 invocations; no claim is weakened by the difference. Every test name the landing cites exists.

| # | Deliverable | Verdict | Evidence |
|---|-------------|---------|----------|
| 1 | Read and validate the whole profile; a failing profile is refused as a whole | met | `ProfileValidator` (1129 lines), `JsonDocument` (bounded reader), `JsonPointer`, `CanonicalJson`; `ProfileValidatorTest` refusal stream with document and JSON Pointer asserted; the decimal bound of the CodeRabbit finding is in `JsonDocument.java` 143 (precision and scale 1000) |
| 2 | Shipped, machine and project sets; record, digest, HMAC sidecar, `profile_unverified`, `verified_on_other_build`, project profile detection | met | `ProfileLoader`, `ProfileSets`, `ProfileSet`, `LoadedProfile`, `EnrolledHarness`, `VerificationRecord`; `ProfileLoaderTest` (14 methods: `machineProfileAddsHarness`, `missingRecordFallsBack`, `editedAfterRecordFallsBack`, `failingHmacFallsBack`, `projectDetectionDiffersRefused`, `otherBuildInsideRange`, …) |
| 3 | Build test: every shipped profile validates | met | `ShippedProfilesTest` (2 tests) over `ShippedProfiles`; the `pm-api` SNAPSHOT must carry pm-mcp-clients#161, which is on `origin/main` there (`725f62a`, read with `git -C`) |
| 4 | Update and isolation declarations; the stages `isolation`, `egress`, `usage` record their values | **met except one clause** | `ProfileStages` and `ProfileStagesTest` (8); signer, `mcp_config.exclusive` and `supported_versions` rules in `ProfileValidatorTest`. The clause "for each hardening rule that concerns the parts read here … a fixture profile": the **Claims rule** (the record's stage and platform gate on load) is not implemented, as the landing says |

The Claims gate is `PM-WATCH-CLIENT-13` (`doc/implementation-watch/client-profiles.adoc` line 88, narrowed by this pull request): "The load-time stage/platform gate belongs to the launch path (`worker-launch`, not yet decided there)". **No staged spec carried it**: `grep CLIENT-13` over `plans/` finds PLAN-19 and PLAN-61 (token usage) only. It is carried into PLAN-30 `supervisor` (the orchestrator first put it into PLAN-29; the operator moved it). `deliverables_done=3` of 4 is therefore correct, and the landing's "declared open in PM-WATCH-CLIENT-13" is true but had no owner.

Documentation and trace, read in the merged tree: `pm-core/doc/developer/core-profile.adoc` (200 lines), four ADRs `core-profile/001` to `004`, `doc/Traceability.adoc` +33 lines, the row and file `profile-loader` removed, the spec sections and watch item PM-WATCH-CLIENT-10 deleted, `PM-WATCH-CLIENT-13` narrowed. The links check over both trees reads `BROKEN LINKS 1`: `roadmap.adoc` line 242 → `pm-core/doc/developer/core-queue.adoc`, in the local documentation checkout, which lags `origin` by 10 commits; the target is not in this pull request's diff, so it is not #137's and is not re-checked at `origin`.

## Surface delta (measured by the orchestrator)

Declared 8 entries (3 of them HYPOTHESIS), realized 38 files, **added 3** outside every declared entry (35 sit inside the declared directories and files): `doc/Traceability.adoc` (the shared document the spec names as not declared), `doc/plans/question-continuation.adoc` and `pm-workflow/doc/adr/workflow-plan/007-rules-13-and-14-left-to-their-packages.adoc` (dangling-link fixes). No declared entry went untouched. `inbox landing-check` reports `surface_delta: unmeasured` (the message supplied neither side). The landing's claim that `doc/plans/token-usage.adoc` grew the surface is **not corroborated**: that file is in no diff of the merge (last change `acb5bba`) and no document names `profile-loader` any more.

## Metrics and Anomalies

- `total_tokens`: not in the message (opencode; the twelfth landing with it).
- **CI on the final head `a6e7ca6` is red, and the pull request merged anyway.** `ci checks status`: `overall_status: failure`; failing: `build / conclusion`, `build / sonar-build`, `SonarCloud Code Analysis`. The SonarCloud quality gate reads `ERROR`: `new_reliability_rating` 3 (threshold 1) and `new_coverage` 74.9 % (threshold 80); security, maintainability, duplication and hotspots are OK. The landing reports "Blocked on Sonar — Leave it open; you fix Sonar" and "Merge now, gaps accepted": the operator chose the merge (plan-reported, six operator questions recorded in the message); this analysis records it as an operator-accepted red gate, not as a lane fault. `build (25)`, `build (26)`, quarkus-alignment, integration tests and dependency review are green.
- **176 SonarCloud issues are open on #137, and all 176 are on `main` now** (293 open on `main` in all; 118 before). By rule: `S1192` x123 (132 issues sit in `ProfileValidator.java`, 113 of them duplicated string literals), `S6353` x12, `S6213` x8, `S5778` x8, `S3776` x4 (`ProfileValidator` 356, 564, 724, 976), `S135` x3, `S3358` x3, `S6218` x2 (**the two BUG-type issues that fail the reliability rating**: records holding an array in `LoadedProfile.java` 31 and `ProfileSets.java` 46), `S7467` x2, and eleven singles. Files: `ProfileValidator` 132, `VerificationRecord` 9, `ProfileLoaderTest` 7, `SupportedVersions` 6, `ProfileValidatorTest` 5, `ProfileLoader` 4, `ProfileStagesTest` 3, then one or two each in six more. Coverage of the package on `main` per file: `LoadedProfile` 61.9 %, `ShippedProfiles` 65.1 %, `ProfileValidator` 80.4 % (132 uncovered conditions), the rest above 80 %. The local `verify -Pcoverage` (3023 tests, plan-reported) passed the per-module 80 % rule while the new-code figure of the gate did not.
- `S4032` fell from 22 to **21** on `main`: `core.profile` holds code now.
- Reviews: CodeRabbit reviewed `5698910` (12 findings: 9 inline, 3 nitpicks; fixed in `f10dbe9`); the final head `a6e7ca6` was not reviewed (quota, plan-reported, operator accepted). 9 inline threads: 8 resolved, **1 unresolved** (`CanonicalJson.java` 143, the decimal scale bound): answered "Fixed in f10dbe9", and the bound is present in `JsonDocument.java` 143. The landing's "every thread was answered and resolved" is true of the answers, not of the resolved flag. Sourcery skipped (diff over 150,000 characters).
- Self-review `budget-exhausted` at the 3-round limit, plan-reported (operator chose to proceed).
- Rule breaks (plan-reported, not re-run): the cross-repository `pm-api` pull request was handled with `gh` directly (the lane's `ci` executor governs this repository only); after the operator's "Merge now, gaps accepted", `gh pr merge --admin` was attempted directly and the repository refused it; the merge then went through after the operator fixed the organization restriction. `git merge origin/main` was used (allowed); no rebase, no force push. The operator's ruling of the same day: the `gh` calls are fine, now and later; they are recorded, not defects.

## Routing and Merge Behavior

Merged as a squash; first-parent chain `0bf64f7` (#135), `9732401` (#129), `9c02edc` (#137). No collision with a running plan (none was running). `corpus declaration-currency` over the 38 footprint paths: the declared surface of 15 staged and parked specs contains a footprint path beyond the shared index; apart from the shared `doc/plans/README.adoc`, the code and ADR paths of the new package sit in PLAN-61, 66 and 73 (26, 26 and 18 code paths each, declared as hypotheses), PLAN-97 and PLAN-98 (18 each, my own hypothesised surface for the endpoint harness and judgment endpoint), and the shared documents (the profile specification and watch) in PLAN-26, 29, 30, 46, 58, 60, 71, 103. With the earlier 31, **39 staged and parked specs hold stale claims** (0 blocking): the 8 new are PLAN-26, 29, 30, 50, 73, 97, 98, 103. The ones that matter for code are PLAN-61, 66, 73, 97 and 98, whose claims about `core.profile` as greenfield are refuted by this merge.

## Reconciliation Actions

- Row `PLAN-19`: `shipped`, `pr=#137`, `landing=landings/PLAN-19.md`, `plan_marshall_plan_id=rb-19-profile-loader`; message `-001` archived.
- The Claims gate (PM-WATCH-CLIENT-13) carried into PLAN-30 `supervisor` (section *Carried From PLAN-19*, claim stamped; the operator moved it there from PLAN-29).
- PLAN-94 extended by the 176 issues of the profile package (Deliverable 9) and by the new-code coverage of the package; Deliverable 7 corrected (21 `S4032`, not 22).
- Open Defects: twelfth landing without `total_tokens`; PLAN-19 merged on a red SonarCloud gate; the unresolved review thread is noted in this record only.
- Dependents unblocked by this merge: PLAN-25 `waits`, PLAN-26 `role-sets-and-model-caller`, PLAN-27 `job-confinement-and-environment`, PLAN-61 `token-usage`, PLAN-66 `worker-activity`, PLAN-73 `question-continuation` (all `staged`; none emittable before `cleanup` re-grounds them).

## Follow-Ups

- `cleanup` before the next emit: 39 specs hold stale claims.
- Owed to the milestone exit, in `plan-marshall-documentation`: the concept and user documentation of the profile slice and the roadmap check-off (the landing lists them).
- `ProfileStages.usage` compares one turn, not the turns of a run (plan-reported residue); `token-usage` (PLAN-61, Deliverable 11) names "the turns of a warm worker" in its parsing fixtures, so it is the likely owner: `cleanup` confirms it when it re-grounds PLAN-61.
