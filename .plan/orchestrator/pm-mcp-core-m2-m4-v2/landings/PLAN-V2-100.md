# Landing Analysis: PLAN-V2-100 — builders for the records of pm-core

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #367 (merge `23a7e51f3c411cfb2d43f4360be1b9096a21d2c1`, the last of three slices: #362 → `7d94b2f3490cb0186da3c2d84cd3267c66fe8da3`, #363 → `8fe2777ec68489abdf1ed990f4d0953c7a56ef8c`, #367 → `23a7e51f`; #367 replaced #364, closed unmerged to reset the review cycle; all three `merged` per the CI abstraction; the three diffs hold 177 files)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-11 from the runbook-lane
> message `rb-v2-100-builders-for-records-core-001.md` (revision 1: the lane amended it after the last two slices
> merged; harness opencode, model deepseek-v4.1-flash/high). The message is a lead. The three layers were read by one
> read-only leaf from the merged code at `23a7e51f` (no build); the orchestrator re-read the points marked "re-read";
> the provider figures were re-read from GitHub and the SonarCloud public API. The operator split the plan into three
> slices so the review bots could review it.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 find and annotate, per-record test | shipped | 141 records of four or more components in `pm-core/src/main` (nested, generic and private included), 140 carry Lombok `@Builder`, none below four is annotated; `PrimitiveResult` keeps a hand-written builder. 84 contract test classes hold 140 methods, one per record, none tautological. The lane's "108 added" does not add up with its own "33 before": the leaf counts 107 added `@Builder` lines. |
| D2 call sites | shipped-modified (not met as the Done-when words it) | The lane reports D2 met; the leaf found positional four-or-more-argument calls left outside codecs, the records' own factories and the same-named exemption: 5 in `pm-core`, about 20 more in `pm-workflow` and `pm-runtime`. Re-read in `pm-core`: `BuilderCheck.java:258` (`Decision`, 7 arguments) and `:261` (`IntentRecord`, 11), `RoleAssignmentProposal.java:131` (`Proposal`, 5) and `:132` (`RoleAssignmentValue`, 5), `CorpusImport.java:365` (`MissedSource`, 4). In `pm-workflow`: `TaskProtocol.java:334` (`Attempt`, 9) and `:339` (`Lease`, 4), in a file slice 3 touched. |

Facts block: `deliverables_total=2`, `deliverables_done=2`. By the Done-when's words D2 is not done, and the lane's own title "move the remaining record call sites" overclaims.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), reason: 177 files over three slices and a per-record question; no build.
- **Verdict:** specified — findings; implemented — findings; correct — no defect found by reading.

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence |
|---------|-------|---------|----------|
| L1-1 | The spec's counts were stale: at `7d94b2f3^` there were already 141 records of four or more components and 33 builders, not 123 and 16; the plan added no records. The lane's refutation is right. | checked, confirms | leaf count |
| L1-2 | "Every record carries `@Builder`" has no carve-out for a record whose builder must default components; `PrimitiveResult` keeps a hand-written one. | checked, refutes | `PrimitiveResult.java:100-169` |
| L1-3 | "Optional components are gone" is false for primitive optionals: five annotated records still carry `OptionalLong` or `OptionalInt` (`UsageAggregate.java:65-67` re-read, `TransactionJournal.Target:137`, `EffectiveRole:47`, `ProfileStages.StartContext:140`, `WorkerParameters:45`). They predate this plan. | checked, refutes | leaf |
| L1-4 | The Expected Surface names `pm-core` only while D2 and the Done-when do not say which modules; slice 3 changed 13 production files in `pm-runtime` (8) and `pm-workflow` (5) and left about 20 sites there. | checked, refutes | leaf |

**Layer 1 verdict:** findings — the spec's scope and its Done-when need a stated module list and exemption.

### Layer 2 — Implemented

- **Confirmed:** annotation completeness within `pm-core`; 84 `*BuilderContractTest` classes, 140 methods, every comparison constructor-built against builder-built (140 of 140), refusal parity tested for 122; the three visibility relaxations are private to package-private only (`ProfileValidator.Checker`, `ProfileValidator.Checker.EndpointBlock`, `SupportedVersions.Version`), used by same-package contract tests; no unexplained file; no `pom.xml` in the three merge diffs (the leaf found all 177 files `.java`; not re-listed by the orchestrator).
- **D2 gaps:** besides the sites above, `ConsultationService:172,445,464,548`, `WorkflowEngine:893,911,914`, `RoleGrader:222,359`, `ModelSlotScheduler:239,317`, `AuditHead:196` and `AuditRotation:414` construct pm-core records positionally; `CorpusImport` is a same-named exemption only for `missed()` at `:360`, not for `hashMismatch()` (re-read); five 8-argument `KindMapping` constructions in `ActivityEventMapping:86-93` are borderline codec-like.
- **Weak tests:** the canonical values are mostly null or one value repeated (`UsagePointers` 4 of 7 null; `Finding` about 25 nulls of 38; `Lease` passes one value for two components), so a swap of equal or null components cannot fail; 18 of 140 methods have no refusal assertion although several canonical constructors refuse.
- **Lane figures not borne out:** 108 added builders (107 counted); `CorpusImport` as a wholly same-named exemption.

**Layer 2 verdict:** findings — D1 delivered; D2 not met by the Done-when; tests weaker than their number.

### Layer 3 — Correct

- The builders call the canonical constructors, so compact-constructor validation and the null refusals run; generic records are covered (`JobRunner.StartRequest`); sampled call-site conversions are argument-for-argument faithful (order, nulls, defaults).
- **Defaults:** a record with a secondary convenience constructor (`EndpointVerification`, `EndpointLaunch`, `Stage`, `Endpoint`, `TransactionJournal`) gets a builder over the canonical constructor only, so an omitted defaulted component is `null`, not the default; the plan's own conversions pass the defaults explicitly (`EndpointVerification.java:105-117`) and no wrong default was found, but the builder tests do not cover defaults.
- **Overload:** `ProjectEnrolment` has a component named `build`, so Lombok generates `build(BuildSpec)` beside the terminal `build()`; the call site `Enrolments.java:481` reads `.build(enrolment.build())...build()`.
- Not shown by reading: whether Lombok copies the JSpecify `@Nullable` onto builder parameters; pass counts, the gate rewrites, `verify`, coverage and the integration tests.

**Layer 3 verdict:** no defect found by reading; test strength and one overload-readability item.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | a record of four or more components carries `@Builder` | deviates in part | 140 of 141; `PrimitiveResult` (sound reason, a design choice) |
| CLAUDE.md Code Standards | every assertion carries a message of 20 to 60 characters | conforms | 506 assertions in the 84 added files, none without a message or outside the range |
| CLAUDE.md Code Standards | JUnit 5 only; no Mockito, PowerMock, Hamcrest; no AAA comments | conforms | keyword scans |
| CLAUDE.md Code Standards | test data from `cui-test-generator`; a literal stays only where it is the contract and the test says so | deviates | 55 of 84 files use no generator; 448 literals, 312 of them in those files, without a contract note |
| CLAUDE.md Dependencies | never add a dependency | conforms | all 177 files are `.java` |
| spec | no call site outside codecs and factories passes four or more positional arguments | deviates | see Layer 2 |

## Metrics and Anomalies

- Tokens: `unknown`; `plugin_version` `unknown`; executor `0.1.2088`. The landing-check reports `complete: false` (`total_tokens`, `plugin_version`), folded into the standing Open Defect on runbook-lane landings.
- One daemon bound expiry (502 s), answered with the split form; one unrelated flake (`LspClientTest.stubborn`).
- Lane rule deviations it stated: `git grep` early in the run, `git checkout <branch> -- <paths>` and `git switch --force` to assemble the split branches, one sub-agent `awk` line-length check.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed #362, #363 and #367 at the heads it could (13 nitpicks on #362 fixed in `2736d426`, one on #363 answered, one on #367 fixed in `0b10ad0d`); Sourcery refused on its budget. The operator chose to split the plan into reviewable PRs, then told the lane to wait out CodeRabbit's rate limits (four 90-minute windows and one close and reopen). `review-gate` re-read: 0 unresolved threads and 0 review-body findings on #363 and #367.
- **Sonar: a difference.** The landing carries `sonar_open_new_issues=n/a` and says the provider is not configured; the public API reads **open new-code issues on every slice: 16 on #362, 41 on #363 and 14 on #367, all `java:S5778` in the contract tests**, with the quality gate `OK` on each. `n/a` is an answered absence, but this project has Sonar, so the figure is a difference from the provider, not a rounding: the spec's Done-when ("no open issue other than one dismissed with its reason") is not met on the final PR. The lane's residue note names S5778 nitpicks as remaining in constructor-argument lambdas of the new tests.
- CI/merge: all three PRs `merged`, merge commits as above.
- Surface collisions: none observed with PLAN-V2-108 (it covers `pm-workflow`, `pm-runtime` and the providers), 127 and 53. PLAN-V2-123 § 54 says `IntentRecord` has no `@Builder`, which is stale (the lane's residue item; not read here). PLAN-V2-137 (staged) may take the cross-module call sites that this plan left in `pm-workflow` and `pm-runtime` if the operator wants them in one plan.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#367`
- [x] row `landing` stamped `landings/PLAN-V2-100.md`
- [x] row `plan_marshall_plan_id` stamped `v2-100-builders-for-records-core`
- [x] the incomplete landing facts fold into the standing Open Defect on runbook-lane landings; a Watch records the residue
- [x] message archived after the disposition was persisted
- [ ] residue plan: not staged; put to the operator (Follow-Ups)

## Follow-Ups

- **Residue of PLAN-V2-100, awaiting the operator's word:** the five `pm-core` sites (`BuilderCheck`, `RoleAssignmentProposal`, `CorpusImport.hashMismatch`) and the about 20 cross-module sites (`TaskProtocol`, `ConsultationService`, `WorkflowEngine`, `RoleGrader`, `ModelSlotScheduler`, `AuditHead`, `AuditRotation`), 14 open Sonar S5778 issues on #367 (and the earlier slices' counts), the 18 missing refusal assertions, null-heavy fixtures and the literal data without a contract note, and the `OptionalLong` / `OptionalInt` components of five annotated records. A single residue plan could take this and PLAN-V2-137's call-site deliverable together; it is a residue plan by the operator's rule (runbook lane), and the call-site part overlaps PLAN-V2-137 Deliverable 1, which should then name the extra sites.
