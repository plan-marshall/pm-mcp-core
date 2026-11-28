# Landing Analysis: PLAN-V2-213 — the package texts follow the liveness guarantee

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #644 (https://github.com/plan-marshall/pm-mcp-core/pull/644), merge `7c307340` (squash through the merge queue, parent `8e298e47`), analysed on the forty-fourth run, 2026-11-28

> Landing record. Source: the plan's inbox message `package-texts-liveness-guarantee-003` (landing-facts present, `merge_state=merged`, `complete: false`, see Metrics) with two candidate lessons (001, 002), read against git (`git diff 7c307340^ 7c307340`: 16 files, +164/-41, documents only) by one read-only leaf (`execution-context-level-3`, R21) that was given the spec and the message as data and ran no build. The orchestrator re-read L3-1, L3-2 and L3-6 at their lines (`timeouts/02:89`, `runtime-lifecycle/02:72`, `model-slot-release.adoc:17`, `LeaseRecoveryStep.java:28,51`, `PeriodicSweep.java:50`, `timeouts/02:76-78`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1(a) plan-and-epic-control | shipped-as-specified | task 5: `awaited`, the position, the correlation ids, the hold `overdue` with its causes and acts (`state-catalogue/01:274,277`) |
| D1(b) re-planning | shipped-as-specified | task 4 `prerequisite_cycle` and PHASE-133 (`phase-workflows/08:97-106`) |
| D1(c) runtime-control | shipped-as-specified | task 6 "not responding", also while the API answers (`runtime-lifecycle/01:58-64`, LIFE-3) |
| D1(d) runtime-metrics | partly | `work.waits{kind}` and the thirteen reasons exact; the sleep-span sentence contradicts the specification (L3-1) |
| D1(e) process-and-overview | shipped-as-specified | counters with caps, the job's elapsed time against `budget_seconds`, the timeline derived on read |
| D1(f) finalize-pipeline-and-merge | shipped, one justified deviation | the CI and analysis waits are stall-watched; the staged text said the merge-queue wait is too, the package says it is NOT (stall-based deadline, `finalize.merge-queue-exit`): correct against `timeouts/01:271-275,353` |
| D2(a) awaiting-bounds | partly | built and not-built facts true; "no row is unbounded" missing (L3-3) |
| D2(b) stall-clocks-and-overdue-waits | shipped with defects | tasks 1 to 4 and the order; L3-1, L3-4, L3-6 |
| D2(c) model-slot-release | partly | task 1 and the facts true; a Left-out sentence is false (L3-2) |
| D3 index, Traceability | shipped-as-specified | README rows and edges, no cycle; Traceability rows PM-WF-8, PM-ARCH-8 (the stalled-hold row: the staged hypothesis "PM-ARCH-7" was refuted, the lane edited the right row) and PM-ARCH-7 (self-watch, clock gap, slot release) |
| D4 the lines PLAN-V2-210 left | shipped, lists still incomplete | (a) spent-repeat clause, (c) the `branch_pushed_at` clause removed, (d) `blocks` and the eligibility condition, (e) `ci.checks` owner stated open: met; (b) the watch lists: partly (L3-7) |
| Done-when | met except the two "partly" rows | 189 links resolve; no stray tool-call line remains (git grep of `</content>`, `</invoke>`, `antml`, `<parameter`, `function_calls`: none) |

## Three-Layer Analysis

### Layer 1 — Specified

All deliverables shipped; four rows are partly. No path outside the Expected Surface (16 changed, 16 declared).

### Layer 2 — Implemented

16 files, +164/-41, all under `doc/`. 189 added or changed `link:` targets checked, 0 broken (the six `Requirements.adoc#PM-*` anchors resolve through `include::`). 30 citations sampled across the new and the edited packages: 28 exact, one wrong (L3-2), one imprecise (L3-6). The three new package files had trailing `</content>` and `</invoke>` lines after execute (found by the code review's first round, removed in `6e4a9cf7`); seven review rounds then repeated the scan by hand (lesson 002).

### Layer 3 — Correct

No major defect. Re-read at the lines: L3-1, L3-2 and L3-6 hold.

| Defect | File:line | Reading | Effect | Severity |
|--------|-----------|---------|--------|----------|
| L3-1 | `stall-clocks-and-overdue-waits.adoc` task 2, `runtime-metrics.adoc` task 2 | **re-read:** both say "Open: under which wait reason a sleep span is attributed", but the specification STATES it: "attributed as a pause in the wait slices" (`timeouts/02:89`, `runtime-lifecycle/02:72`, the Clock-gap tests, LIFE-4); the real tension is that `paused` is defined as the operator's pause alone (`job-runtime/01:171`, `metrics.schema.json:123`); the Tests omit two LIFE-4 guard parts (the pause attribution, a deadline due within the gap processed by its own rule) | a code plan passes the package's Done When and fails the guard | minor |
| L3-2 | `model-slot-release.adoc:17` | **re-read:** "the build slots, which are lease-store slots already released by `LeaseRecoveryStep`" is FALSE: it releases only on caller-supplied `AbandonmentEvidence`, default `NONE` "so the step never releases" (`LeaseRecoveryStep.java:28,51`), slot releases are `JOBS_AND_WORKERS` (`:33`), `JobRecoveryStep:238` skips a settled job; the specification applies the end-fate rule to "a build or model slot" (`store-schemas/13:17`), STORE-63 covers the model slot only | the build slot's twin has no owner and no package says so; task 1 lists `LeaseRecoveryStep` among its paths | minor |
| L3-3 | `awaiting-bounds.adoc` task 2, Tests | "no row is unbounded" omitted; `operator` and `resident` have no bound by design, so "one of the closed list" does not carry it | the registry test could pass an unbounded row | minor |
| L3-4 | `stall-clocks-and-overdue-waits.adoc` task 1 | `state` listed as new; `StalledHold` and `planStalled` carry it | over-extension | info |
| L3-5 | four packages | the CI-wait record (expected duration, last observed change, `Retry-After` deferral) has no owner: open notes in two packages (stall-clocks Left out, finalize task 3), none in the two that consume it (process-and-overview task 4, stall-clocks tasks 1 and 3); the README shows stall-clocks as depending on nothing | a code plan for task 3 starts with an unmade decision | minor |
| L3-6 | stall-clocks task 4 | **re-read:** `PeriodicSweep.INTERVAL` is 3600 s (`PeriodicSweep.java:50`) and the heartbeat threshold is 60 s (`timeouts/02:76-78`): a heartbeat stamped once per pass and judged by its own stamps is late 59 minutes in 60; "`PeriodicSweep` is the one stamp point built" overstates (no stamp exists, a grep finds none) | specification drift the package carries without comment | minor |
| L3-7 | Done When of start-from-clients, epic-auto, project-onboarding-unit, model-slot-release | watch items anchored to linked sections not named or excluded: Lesson Record (STORE-14, 15, 16, 19, 20, 34, 58, CLI-4), Operator Local Configuration (EXT-29), Model Slots (DISP-31), the setup state machine (EXT-27, 28, 29, 33, 34) | items stay open that a test covers, or are never rewritten | minor |
| L3-8 to L3-12 | plan-and-epic-control Tests; model-slot-release; awaiting-bounds task 1; finalize; re-planning task 4 | `observe_now` "at once" against "within the provider's re-poll cadence" (`state-catalogue/01:277`); the package says a later admission reaps the slot (`ModelSlotScheduler.reap`, ADR 005) while STORE-63's hazard says no evidence releases it; the seven bound sources are not listed; the merge-queue deviation of D1(f) is not in the lane's advisories; `prerequisite_cycle` is already in `mcp-tools/04:214` ("joins" is stale) | wording | info |
| clean | | values 3, 10, 3600, 60, 30 and the seven bound sources; the six anchors; the thirteen wait reasons; PHASE-133; DSL-49 and DSL-1/4/5 pointers; LIFE-2, 3, 4 and RUN-2, 8; no cycle in the README graph; the "not built" claims (no hit in `src/main`) | | clean |

**Layer 3 verdict:** findings — 12 points (six minor, six info); not shown by reading — advisory 6 (a stale commit hash in a decision-log entry, in the lane's own plan log). No major finding, nothing reported at once.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| Project Setup, Planning the Work | a package text states tasks and a Done When with anchored watch items | partly | L3-1, L3-3, L3-7 |
| Standing rules (this plan) | documents only | conforms | no Java, no specification, no schema |
| CLAUDE.md Git Workflow | merge through the queue | conforms | squash `7c307340`; the `ci repo` merge-message setting the documents prescribe was not applied (an organisation setting the plan's start does not authorise): the trailer is on the branch commits, not guaranteed on the squash |

## Metrics and Anomalies

- Harness claude, model `claude-sonnet-5-5` (effort never stamped, `unknown`), plugin `0.1.2184`, executor `0.1.2184`; `total_tokens=18721760`, `total_wall_seconds=13404` (3 h 43 min). `complete: false` for five keys, all unobserved facts recorded `unknown`, never 0: `effort`, `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`, `sonar_open_new_issues` (CI skipped `sonar-build` on a documents-only pull request; the first sonar-roundtrip failed closed and halted, a later firing passed as "no surface").
- Metrics slips recorded by the lane: the execute round-2 boundary with 0 tokens, a wrong code-review round-1 duration, one approximated usage row.

## Routing and Merge Behavior

- Merge: through the merge queue as `7c307340`.
- Two candidate lessons forwarded verbatim to the plan-marshall `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-079` and `-080` (001 `phase-6-finalize` sonar-roundtrip on a documents-only pull request, 002 `pm-documents:ref-asciidoc` `asciidoc validate` does not flag stray tool-call markup), uncommitted in that ledger until its land.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#644`; `landing` `landings/PLAN-V2-213.md`; `plan_marshall_plan_id` `package-texts-liveness-guarantee`
- [x] the three messages archived (the two lessons forwarded first)
- [x] every minor point and the advisories staged as the NEW PLAN-V2-215 (documents only, 4 deliverables, 12 paths): the sleep span, the heartbeat and the first task of the stall clocks; the build slot; the awaiting bound and the CI-wait record; the Done When lists. The liveness CODE plans are staged after 215 has merged
- [x] the Decisions entry and the Open Defects written; resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **Staged:** PLAN-V2-215.
- **Specification:** the sleep span's wait reason (`paused` defined as the operator's pause against "attributed as a pause"), and the heartbeat's 60-second threshold against the 3600-second sweep, are the specification owner's; recorded as owner-open questions L2 (re-stated: the specification already says "pause", the open point is widening `paused` or a reason of its own) and L6.
