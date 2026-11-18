# Landing Analysis: PLAN-V2-145 — The verification-record gate brought to the specification

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #490 (`f4356cfc`, merged through the merge queue 2026-11-18, runbook lane, opencode, `rb-v2-145-harness-eligibility-gate`)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message `rb-v2-145-harness-eligibility-gate-001.md`) after verifying claims against git, the PR and one read-only three-layer leaf; a pasted claim is a lead, never a fact.

## Deliverable Fidelity vs Spec

Three of three deliverables met (`deliverables_done=3`).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the five checks in the gate | shipped-as-specified | `ProfileWorkerEligibility`: missing `paths`, trials below the minimum (`MINIMUM_FAULTS_TRIALS = 10`, `:78`), a `faults` stage without trials, no confinement or an enforced tool set without isolation, another digest than the loaded profile's (`profile.digest()`), an `egress` stage recorded `unenforced` without the acceptance flag; the confinement is paired with the operating system (`CONFINEMENTS`, `:81`: linux `landlock`, darwin `sandbox`, re-read); `HarnessProfile` and `ProfileValidator` are not in the diff; 22 tests in `ProfileWorkerEligibilityTest` |
| D2 watch item, Traceability, ADR | shipped-as-specified | PM-WATCH-CLIENT-13 in `harness-profiles.adoc` and every test name it cites exists; the PM-CLIENT-5 gate block; ADR `runtime-worker/006` amended (its false consequence corrected); ADR 015 is the next free number |
| D3 what is not wired is named | shipped-as-specified | `runtime-worker.adoc` § What Is Not Wired names the assembly wiring, the loader, the acceptance and the stage runner |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`), a leaf of its own for this landing; the orchestrator re-read the points marked "re-read".
- **Verdict:** layer 1 findings (1); layer 2 findings (minor); layer 3 clean at blocker and major (one conformance deviation, a documented default).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | a verification record can state `egress: unenforced` | checked | refutes | the stage status set is closed (`passed`, `failed`, `not_run`) and the specification names no carrier: `01-profile-schema.adoc:463` lists `results` for `events` and `usage` only, the claim rule at `:465` names an `egress` recorded `unenforced`; the lane read the marker from `results.enforcement` (residue, owner PLAN-V2-147/148 or the package `job-confinement-and-environment`) |
| L1-2 | the Expected Surface lists every file the plan edits | checked | refutes | the watch-count rows in `ImplementationWatch.adoc` and `Specification.adoc` and the two specification backlinks were omitted (the PR reports them as surface deltas) |

The hypotheses of the spec match `main`: `CanonicalJson.digest`, `LoadedProfile.digest()`, the digest compare at `ProfileLoader.java:165`, `Platform.confinement` a free string (confirms).

**Layer 1 verdict:** findings — 2.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D1 | `ProfileWorkerEligibility.java` | `aMissingPathsStageRefuses`, `nineTrialsRefuse`, `aFaultsStageWithoutTrialsRefuses`, `noConfinementRefuses`, `anEnforcedToolSetWithoutIsolationRefuses`, `anotherDigestRefuses`, `unenforcedEgressWithoutAcceptanceRefuses`, `aForeignConfinementRefuses` (each would fail on the old gate, by reading) | the two `_Implementation watch:_` backlinks, the watch counts (14, right) | checked |
| D2 | docs | links check | none | checked |
| D3 | docs | — | none | checked |

Minor findings that qualify the lane's report (leaf, not all re-read):

- `ProfileWorkerEligibilityTest.java:168-183`: the OS pairing is only refused both ways (re-read: `darwinWithLandlock` refused as `NO_CONFINEMENT`); no test shows darwin with sandbox accepted (`:263` uses darwin in another test the orchestrator did not read), so dropping the darwin entry of `CONFINEMENTS` (`:81`) leaves the refusal tests green.
- `:213`: the claimed half of the order test cannot fail by record order (the loop iterates the claim list and the scenario has one missing stage); only the incidental half can, and the failing run also failed partly for the isolation requirement.
- `ProfileWorkerEligibility.java:219`: the `!endpoint` guard on the confinement check and the digest check for endpoint profiles have no test (no test builds an endpoint document).
- `:55`: the merged class has 22 tests, the narrative says 21 green at `298e4314` and 22 at the failing run; the runs themselves are not readable (not shown by reading).
- ADR 015 `:70` says "the operator chose" the acceptance as an input; the lane's operator-question record (§ Operator questions) shows the question was asked and answered, so the ADR sentence is supported by the lane's own record, not by anything in the diff (not shown by reading).

**Layer 2 verdict:** findings — 5 (minor).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `ProfileWorkerEligibility.java:324` (re-read) | an `egress` stage with no `enforcement` result | `unenforced(stage)` is `!ENFORCED.equals(results.getOrDefault("enforcement", ENFORCED))`: absence is read as enforced, the one absent-input default that makes a harness eligible; documented in the Javadoc and the ADR, equal to the specification's plain `passed` | minor (conformance deviation of the standing constraint "no default makes a harness eligible"; the residue of L1-1) |
| L3-2 | `ProfileWorkerEligibility.java:36`, `runtime-worker.adoc:327` | — | an unwrapped Javadoc line over 120 characters; "the sandbox `sandbox`" repeats the word | info |

**Layer 3 not shown by reading:** the failing and green runs, Sonar, the links check. Confinement pairing, trial boundary (null, 9, 10), digest comparison, closed-set status handling: no defect found. No generic catch, no new dependency, no new fake, assertion messages 24 to 58 characters.

**Layer 3 verdict:** findings — 2 (minor and info).

### Conformance to the Project's Own Rules

Eleven rows: ten conform, one deviates (L3-1), `@NullMarked` on `package-info.java` not shown (the diff does not touch it).

## Metrics and Anomalies

- Tokens: `unknown` (harness opencode); `plugin_version` `unknown`.
- Anomalies: CodeRabbit refused three times on quota (22:04, 23:35 and an escalated "full review" at 01:07) and accepted at 02:39, with 90-minute waits between every trigger as the policy requires; Sourcery refused (diff budget). A first worktree was created with the wrong branch prefix and removed before any edit. The base merge brought #486's `QualificationSchemaParityTest.java:104`, which `verify -Ppre-commit` rewrites; the lane reverted it in its worktree and left it to the qualification landing (now PLAN-V2-114 item 48). Two non-green builds (a wrong `-Dtest` selector, a `@Nullable` placement), one 300 s floor expiry re-run at 900 s. Rule breaks named by the lane: four shell `grep` calls, one empty-stdin `python3 -` heredoc; guard `none` on this harness (unenforced).

## Routing and Merge Behavior

- Review: CodeRabbit reviewed `2228fd3d` (2 findings, both fixed in `cb71f1af`); Sourcery refused. `review-gate --pr-number 490`: unresolved threads 0 (matches), body findings total 2 (matches); `review_body_findings_handled` 2 **re-verified**: the progress file holds 2 `finding:` lines.
- CI/merge: through the merge queue as `f4356cfc`; one `origin/main` merge before the enqueue.
- Sonar: `sonar_open_new_issues=0`, gate passed, 0 hotspots on the lane's read at `cadd068d` (analysis = head); two `java:S6213` issues on the first head fixed in `2228fd3d` (not re-read here).
- Parallelization: ran beside PLAN-V2-96 with the accepted 5-file overlap (`EligibilityRefusal`, `EligibilityVerdict`, `ProfileWorkerEligibility`, `RuntimePlatform`, `SupervisorEnvironment`): no collision observed. Realized footprints (`git diff --name-only` of the three merge commits against their first parents, read in this run): PLAN-V2-96 and 145 share `doc/Traceability.adoc` and `pm-runtime/doc/developer/runtime-worker.adoc` (shared bookkeeping and a guide, both additive); none of the five predicted code files was touched by PLAN-V2-96, so the declared overlap was an over-declaration of PLAN-V2-96's `pm-runtime/.../worker/` directory claim (a `HYPOTHESIS` entry), not a collision. All three landings share `doc/Traceability.adoc`.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#490`
- [x] row `landing` stamped `landings/PLAN-V2-145.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-145-harness-eligibility-gate`
- [x] `epic.md`: Watch for the residue; PLAN-V2-114 item 49
- [x] resume anchor, view regenerated

## Follow-Ups

- L1-1 and L3-1 (the carrier of `egress: unenforced` and the absent-input default): the verification runner's plans PLAN-V2-147 and 148 (the stage model and the writer), recorded in the Watch; the specification names no carrier yet.
- Layer-2 test gaps and the ADR wording: PLAN-V2-114 item 49.
- The assembly wiring of the gate: owed to `plan-marshall-mcp`.
