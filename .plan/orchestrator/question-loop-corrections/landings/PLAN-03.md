# Landing Analysis: PLAN-03 — Build the question-loop driver and evidence store, and measure Claude Code

epic: question-loop-corrections
workstream: WS-02
pr: plan-marshall-mcp #114 (`61daf53`, merged 2026-11-05), follow-up plan-marshall-mcp #118 (`e22f6c1`); plan-marshall-documentation #144 (`2071fbc`, merged 2026-11-05)

> Landing record for one shipped plan. Written by the `analyze` verb on 2026-11-10 after verifying claims against
> ground truth (the merged diffs, the PR state through the CI abstraction, the archived plan directory
> `plan-marshall-mcp/.plan/local/archived-plans/2026-11-05-question-loop-run-follow-up-measurement`). Input mode:
> cross-repo plus on-disk plan artifacts; the plan filed no inbox message (the epic does not resolve in that
> repository's store, by the operator's decision of 2026-10-30), and no operator paste was given. Plan id
> `question-loop-run-follow-up-measurement`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Package amendment first: host split, scale revision, package open until PLAN-04's part is done | shipped-modified | `doc/plans/question-loop-follow-up.adoc` on mcp `main`: Parts at :8 and :82, hand-off list :145–158, revised scale :91–92, parallel-limit step 5a :222–230, decision record :97–109. The amendment reached `main` only with the squash of #114 (2026-11-05), after the runs; its branch order (first commit 2026-10-31 10:31) is attested by the plan's functional review and cannot be read on `main` after the squash. The record's lead sentence (:100) overclaimed and was corrected by #118. |
| 2. Maintained code for all three hosts; evidence store created before the first measured run; task-2 decisions; Codex exclusion | shipped-as-specified | 37 files under `pm-e2e/src/test/java/de/planmarshall/e2e/question/` (stub, driver, fixture, result, entry and re-aggregation, each with tests), `pm-e2e/pom.xml`; store branch `question-loop-follow-up` of `cuioss/plan-marshall-telemetry` exists at `bf129e2a` (`git ls-remote`). Codex: one Limits bullet covering every item (EV:1002). Push time of the store before the first run: attested by the plan's review, not re-read here. |
| 3. Parallel limit, run plan at that limit, re-measurement of Q1–Q7 and R1–R4 on Claude Code | shipped-modified | Parallel limit 12 published with per-level figures (EV:62–115). Q1–Q4, Q6 (consultation), Q7, R1–R3 pass; R4 small work figures only; Q5 fails on `claude-haiku-4-5` at bounds 3 and 4, passes on `claude-sonnet-5-5`. **Not run:** Q5 bound 5 (cap rule, EV:143 ff.; no operator decision recorded for it), R4 large work (operator, 2026-11-01, PK:107). Cap **replaced 60 → 45 USD by the operator on 2026-10-31** (PK:106, DV:381) — recorded with date, no reason. Spent 41.89 USD Haiku + 1.76 USD Sonnet outside the cap. |
| 4. Results in the evaluation reference; settled Claude Code values with evidence, named for `client-profile-measured-values` | shipped-modified | EV rows and harness-values rows (EV:731–750); the naming for `client-profile-measured-values` is in the package (PK:116–126), not in EV. The developer document is `doc/developer/question-loop-measurement.adoc` (new, 399 lines); `question-loop-experiment.adoc`, which the spec's Expected Surface named, was deleted by documentation #145 (2026-11-02) before this landing. The OpenCode no-inline-settings limit is stated (EV:1004) — the open defect from PLAN-01 is resolved. |

**Done when** — read item by item:
- Package Done When for Claude Code at the revised scale: **partly**. The question bound is not settled (bound 5 never ran; `3` fails on Haiku); task 1's `verify -Pintegration-tests` green (PK:170) was not met (red on `main` for unrelated causes, accepted by the operator in finalize, not recorded in the package).
- Parallel limit published with per-level figures: **met**.
- Every published figure has its source in the store: **met only under the exception of 2026-11-01**, which covers cost, rate-limit and refusal figures (PK:15); figures read from worker outputs beyond those (Q2 host-tool calls, the R1/R2 canary re-read clause, R4 token medians) have no stored source.
- Every OpenCode and Antigravity item handed to PLAN-04: **met** (PK:145–158); Q4's cross-harness continuation is named only in the EV Q4 row, not in the hand-off list.

**Realized vs declared surface.** Declared: `pm-e2e/src/test/java/de/planmarshall/e2e/`, `doc/plans/question-loop-follow-up.adoc`, `evaluation.adoc`, `question-loop-experiment.adoc`. Realized: the first three, plus `pm-e2e/pom.xml`, `doc/developer/question-loop-measurement.adoc` and `doc/DeveloperGuide.adoc`; the fourth declared path no longer exists. No collision with another plan was observed.

## Three-Layer Analysis

The landing was read on three layers — the spec, the deliverable trace, and the merged code — by one read-only
leaf, and the orchestrator re-read every point recorded below as `checked` before writing it here. The reading is
**by reading, no build, no test run, no write**.

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze` → `plan.effort`) — reason: 39 merged files (~21,900 lines) across two repositories, the depth the dispatch rule exists for.
- **Verdict:** layer 1 findings — 2; layer 2 findings — 7; layer 3 findings — 3 major, 4 minor. **Layer 3 carries defects** (see below; reported to the operator in the same turn).

Re-read by the orchestrator: PK:15, :100, :106, :107, :170; README of mcp `doc/plans` :108–109; `pm-e2e/pom.xml` :155–171; EV:64, :143, :335, :1002, :1004, :1019; DV:224, :381–383; `QuestionResults.java` :86–90, :245, :258–264, :852–870; `RunView.java` :96–135, :194–203; `RunReadings.java` :389–395; `QuestionLoopMeasurement.java` :225, :1165–1181; `QuestionDriver.java` :675–700. Not re-read (unverified leads, kept as the leaf's reading): the helper-thread writes at `QuestionLoopMeasurement.java:1804–1881`, `QuestionFixtures.java:666–735`, `Host.java:353`, the Q4 rotation at `QuestionLoopMeasurement.java:608–612`.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The 45 USD cap could not cover the Done When's settled question bound: the package's own estimate leaves 2.2–9.7 USD against 12–15 USD per reserve, so bound 5 and R4 large work were expected to stay not run | checked | refutes | PK:106, DV:383 |
| L1-2 | "Every published figure has its source in the store" and dropping the worker outputs are inconsistent beyond the carved exception (cost, rate-limit, refusal) | checked | refutes | PK:15; `RunReadings.java:389` |
| L1-3 | The Q5 rule measures persistence under deliberately evasive replies rather than need (leaf reading, not re-read) | checked | refutes | `QuestionFixtures.java:884–948` |
| L1-4 | Parallel-limit premise sound: rule fixed before the runs, levels 1/3/6/12 tabulated, 12 stated as highest measured | checked | confirms | EV:62–115 |
| L1-5 | The PLAN-03/PLAN-04 split is coherent in the package | checked | confirms | PK:145–158 |

**Layer 1 verdict:** findings — 2 (L1-1, L1-2), plus one unre-read lead (L1-3). No finding names a claim of a staged spec's `## Claim Labels`, so none is persisted by `corpus set-verdict`.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | PK:8, :82, :145–158 | — (document) | Package index row still titles the package "at the specified scale" (mcp `doc/plans/README.adoc:109`) | checked |
| 2 | `pm-e2e/.../question/**` | each class has a test class; `MeasurementGateTest` | `verify -Pintegration-tests` red, deviation not recorded in the package (PK:170) | checked |
| 3 | `QuestionLoopMeasurement`, `QuestionResults` | `QuestionResultsTest`, `ScaleTest` | Q3 "pass, 24 runs" on a reading that differs from the encoded criterion — 10 of 12 fresh askers were ended by the driver (EV:335 vs `QuestionResults.java:88–90`); Q5 bound 5 not run without an operator decision; cap change without a reason (PK:106) | checked |
| 4 | EV rows, DV | — (document) | DV:224 (four classes under 80 % branches, "at the time of the extraction") and EV:1019 state class coverage two ways; PR #144's body names `question-loop-experiment.adoc`, which does not exist | checked |
| — | `evaluation.adoc` Codex statement | — | one Limits bullet covering every item (EV:1002) rather than a statement per row; read as meeting task 5 | checked |

**Layer 2 verdict:** findings — 7 (integration-tests deviation unrecorded; Q3 criterion reading changed and recorded only as open; Q5 bound 5 without decision; cap change without reason; level-1 reproduction verdict differs from the smoke group (23/24, "extend to 10" vs "pass") with no recorded acceptance; coverage stated two ways; stale index row title).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `QuestionResults.java:260–264` (static `recordRun` :245) | Parallel runs (up to 12 threads) appending run records over the JDK's single-write size | `runs.jsonl` appended without a lock; interleaved lines make `RunView.readJsonLines` throw, so the item cannot be aggregated | major |
| L3-2 | `RunView.java:124–127`, `:196–202` | Re-aggregation from the store, where worker outputs are not kept | A missing output file reads as empty and `evidenceMissing` checks only the stub log, so Q2's no-host-tool / no-prose-question clauses and the R1/R2 re-read clause pass vacuously; the live `result.json` is unaffected | major |
| L3-3 | `QuestionResults.java:859–870` | A fresh asker that never ends its turn | Its stop is any `worker_exited` after the ask, which the driver's own teardown produces, so the fresh half of Q3 cannot fail | major |
| L3-4 | `QuestionLoopMeasurement.java:1165–1176` | A `RuntimeException` other than `IllegalStateException` inside a run | The run record is lost and `measure()` skips writing the result | minor |
| L3-5 | `QuestionDriver.java:675–689`, `:699` | A parent that exits promptly (OpenCode `run --standalone`); SIGINT/SIGTERM to the JVM | Descendants killed only after the 10 s grace; the shutdown hook only continues stopped trees, so running paid workers survive a JVM abort | minor |
| L3-6 | `QuestionLoopMeasurement.java:225` | A gated run without `pm.experiment.hosts` | Defaults to all three harnesses, launching OpenCode on the global data home and Antigravity with all tools (#118 fixed only the documented command) | minor |
| L3-7 | `QuestionLoopMeasurement.java:1804–1881` (not re-read) | Helper threads writing the run record | Unsynchronised mutation of a Jackson `ObjectNode` | minor |

**Layer 3 not shown by reading:** whether run records above the single-write size occur in practice; the real orphan behaviour of harness children; whether surefire passes an empty gate property; the green builds and 573 tests the PR claims. The measurement gate itself holds by reading (`QuestionLoopMeasurement.java:131–132`, `MeasurementGateTest.java:37–95`).

**Layer 3 verdict:** findings — 3 major, 4 minor. None affects a published Claude Code verdict except L3-3 (the fresh half of Q3, which EV:335 already discloses as a reading); L3-1, L3-2, L3-5 and L3-6 bear on PLAN-04, which runs two more harnesses on the same code.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| plan-marshall-mcp `CLAUDE.md` § Testing | JUnit 5, `@DisplayName`, `@Nested`; no Mockito/PowerMock/Hamcrest | conforms | sampled test classes (leaf) |
| plan-marshall-mcp `CLAUDE.md` § Testing | Test data from `cui-test-generator` | deviates | `pm-e2e/pom.xml` has no such dependency; literal and `SecureRandom` names in tests (leaf; not re-read) |
| plan-marshall-mcp `CLAUDE.md` § Build | 80 % coverage per module | deviates (by operator decision) | `QuestionLoopMeasurement$Session` excluded by name (`pm-e2e/pom.xml`, decision 2026-10-31) |
| plan-marshall-mcp `CLAUDE.md` § Code Standards | `package-info.java` with Javadoc per package | conforms | all five packages carry one |
| plan-marshall-mcp `CLAUDE.md` § Code Standards | Lombok `@UtilityClass` for static-only classes | deviates | private constructors in `QuestionResults`, `Scale`, `QuestionEvidence` (leaf; not re-read) |
| plan-marshall-mcp `CLAUDE.md` § Dependencies | No dependency added without asking | conforms | #114 adds none to `pm-e2e/pom.xml` |
| package task 1 | Gate: no normal build starts a harness | conforms | `MeasurementGateTest.java:37–95` |

## Metrics and Anomalies

- Tokens: 61,877,333 total (5-execute 46.9 M, 6-finalize 10.3 M); harness spend 41.89 USD (Haiku, cap 45) + 1.76 USD (Sonnet, outside the cap).
- Duration: 139 h wall (1 h 47 m worked); 2026-10-30 20:44 to 2026-11-05 15:46.
- Model `claude-sonnet-5-5` (self-reported), bundle 0.1.2036, harness Claude Code; measured client Claude Code 2.1.316.
- Anomalies: the cap was replaced mid-plan (60 → 45 USD, 2026-10-31); the plan was archived over an indeterminate foreign-PR gate by the operator; four lessons recorded (`2026-11-05-15-001` to `-004`).

## Routing and Merge Behavior

- Review: code review 4 firings (2 loop-backs), clean at 39 files with 13 advisory; functional review 6 firings (1 loop-back to execute), 10 of 12 deliverables met and 2 met with residuals (D6 smoke checks of the other hosts not run; D11 raw outputs not stored), **3 false self-reports**, all three corrected afterwards by #118 (the run-command property, the "unchanged" criterion sentence, the decision record's lead sentence). Review thread numbers: not re-verified (plan-marshall lane, no landing-facts block).
- CI/merge: CI red only in `WebAppIT` (`pm-mcp-server`, untouched by the PR); 2 findings accepted by the operator. #114 merged 2026-11-05 15:11 UTC; documentation #144 merged after it, as its test plan required. No rebase conflict.
- Sonar: 4 new-code issues confirmed on the head, 3 filed for triage; the SonarCloud check passed at the final head per the plan's record.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-03 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-03 --field pr --value "plan-marshall-mcp#114"`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-03 --field landing --value landings/PLAN-03.md`
- [x] row `plan_marshall_plan_id` stamped — `question-loop-run-follow-up-measurement`
- [x] epic.md narrative reconciled (PLAN-03 annotation, decisions, the inline-settings defect resolved, watches)
- [x] PLAN-04 spec: claim 1 re-scoped (the package is amended), Expected Surface `question-loop-experiment.adoc` → `question-loop-measurement.adoc`, verdicts persisted
- [x] Watch opened: leftover documentation worktree `plan-marshall-documentation-question-loop-follow-up` (upstream gone)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- Layer-3 defects L3-1 to L3-6 (L3-7 as a verify-first clause) — staged as PLAN-05, which lands before PLAN-04 (operator, 2026-11-10).
- Claude Code items not run: Q5 bound 5 and R4 large work — folded into PLAN-04 as its deliverable 4 (operator, 2026-11-10); PLAN-04's Expected Surface is unchanged by the fold.
- Layer-2 record gaps in the package (integration-tests deviation, cap change reason, Q3 reading, stale index row title) — a direct pull request in `plan-marshall-mcp` the operator opens (operator, 2026-11-10); an Open Defect until merged.
