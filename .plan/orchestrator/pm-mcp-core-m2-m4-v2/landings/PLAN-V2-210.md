# Landing Analysis: PLAN-V2-210 — the package texts follow the third doc-review round (and the fourth)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #636 (https://github.com/plan-marshall/pm-mcp-core/pull/636), merge `36633b9b` (squash through the merge queue, parent `6724cf6e`), analysed on the forty-second run (second pass), 2026-11-27

> Landing record. Source: the plan's inbox message `plan-v2-210-package-texts-third-doc-review-014` (landing-facts present, `merge_state=merged`, `complete: false`, see Metrics) with thirteen candidate lessons (001 to 013), read against git (`git diff 6724cf6e 36633b9b`: 14 files, +54/-54, documents only) by one read-only leaf (`execution-context-level-3`, R21) that was given the spec and the message as data and ran no build. The orchestrator re-read the leaf's L1 "partly" and the L3 points at their lines (`start-from-clients.adoc:36-37`, `finalize-pipeline-and-merge.adoc:17,36`, `job-runtime.adoc:500`, `model-work/08:154,384`).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the merge path (task 3), the barrier (task 4) | shipped-as-specified, one clause thin | `finalize-pipeline-and-merge.adoc:17-18,36-37`: `checks_pending` to `merge-evidence-wait`, first free and repeat charged (`merge_refused_re_enter`), `checks_failed` unchanged, the gate entry's `ci.pr-observe` outcomes, `head_moved` (`merge_head_moved`), `foreign_head`, the barrier's three outcomes; PHASE-83 and JOB-25 with their controls. **Thin:** a repeat that has spent its budget hands back with a gap naming the pending checks (`state-catalogue/07:130`): not stated (L1-1) |
| D2 owners of the `source-pr` start base write and of `finalize.adopt-pr` | shipped-as-specified | `start-from-clients.adoc:19,22,24,38`; `finalize-pipeline-and-merge.adoc:17`; PHASE-76 in the Watch and Tests lines |
| D3 the lines and owners 195 declined | shipped-as-specified | `queue_required` Done When, onboarding task 4, decision-engine task 1, initial-lane-router, phase-sub-workflows task 7 and Left out; the five owner-open items untouched (no hit in `doc/plans`) |
| D4 the unlinked rows | shipped-as-specified | `README.adoc:105` (forgejo depends on `ci-contract-finalize-operations`), `build-parsers-and-gate-honesty.adoc:16` the one owner |
| D5 the declined defects of 195 | shipped-as-specified; the operator reversed Q1 in part | one owner of the route (`ci-contract:15-19,32`, `finalize-pipeline:17,37`); the forgejo `behind` task and test; `commit-boundary` tests that can fail (task 2 dropped, the lift-removal task restored by the operator at finding f421e7, TASK-9); no removed plan id remains (`git grep` of 13, 59, 146, 190, 192, 193: none); the write-set sentence; `start-from-clients.adoc:22` |
| D6 the fourth round | shipped-as-specified | `ci-contract` `base_branch` and the refusal class, `epic-auto.adoc:21,36` task 7 (observes `handed_back` rows), PM-WATCH-STORE-54 gone, the reported items given no package |
| Done-when "fails at `2b4bdee8`" | not shown by reading; the lane cited the pre-fix tree for the criteria the functional review raised (lesson 001) | |

## Three-Layer Analysis

### Layer 1 — Specified

Every sub-item met except one thin clause (L1-1). Beyond the spec, justified by the Done When rule against plan ids and by bot findings: `build-parsers-and-gate-honesty.adoc` and `finalize-prune-and-land-files.adoc` edited (outside the Expected Surface), the non-blocking `checks_failed` test line, the Trace Watch of `finalize-pipeline-and-merge` gained Job Runtime, the `realized` event named to `phase-sub-workflows`.

### Layer 2 — Implemented

14 files, +54/-54. Every `link:` target and anchor the diff adds resolves (the leaf read 26 specification anchors, the watch anchors DISP-19, 37 to 39, 50, 54, PHASE-76, 82, 83, JOB-25, STORE-50 and the files named). Two claims about code are true at the head: `ProtectedPaths.of` drops declared paths (`ProtectedPaths.java:64`), and `FootprintBoundary.check` adds `mainCheckoutBreaches` unconditionally (`:51`).

### Layer 3 — Correct

No major defect. Re-read at the lines: L3-1 to L3-4 hold; L3-5 holds as a lead.

| Defect | File:line | Reading | Effect | Severity |
|--------|-----------|---------|--------|----------|
| L1-1 | `finalize-pipeline-and-merge.adoc:17,37` | the spent-repeat hand-back with a gap naming the pending checks (`state-catalogue/07:130`) is absent | the package owns a route the text does not describe | minor |
| L3-1 | `start-from-clients.adoc:36-37` | **re-read:** the Watch line names only PM-WATCH-DISP-38; task 5 links Plan Source Contracts (PHASE-88, PHASE-76, PHASE-92) and Plan Overrides; the start side of PHASE-76 has no test line | a plan can close the package with PHASE-76's start guard unowned | minor |
| L3-2 | `epic-auto.adoc:35` | the Model Work items are exact; the Store Schemas items anchored to `epic-queue-row` (STORE-53, STORE-55) and to `local-config` are not named | the "Open:" STORE items are never deleted or rewritten | minor |
| L3-3 | `finalize-pipeline-and-merge.adoc:36` | **re-read:** JOB-25 only; task 4 links Gate Honesty Verification whose items include PM-WATCH-JOB-17 (`job-runtime.adoc:500`), JOB-8, JOB-39, and the Six-Stage Execution Contract | JOB-17 is not deleted or rewritten when the barrier outcomes are built | minor |
| L3-4 | `commit-boundary.adoc:26,30` | the Job Runtime watch link was removed; PM-WATCH-JOB-48 (`job-runtime.adoc:704`) is anchored to Protected Paths | the Done When Watch scope shrank to Phase Workflows | minor |
| L3-5 | `epic-auto.adoc:21` | "the archived status keeps `branch_pushed_at`": **re-read** `model-work/08:154,384`: both name `branch_pushed_at` only as the plan's last recorded push; no line of the specification that was read says the archived status keeps it | a clause without a source | info (lead) |
| L3-6 to L3-9 | `decision-engine.adoc:15,37`; `phase-sub-workflows.adoc:40`; `project-onboarding-unit.adoc:18,37`; `finalize-pipeline-and-merge.adoc:37` | `blocks` "from what waits on it" is looser than `model-work/06:134` (the scope's runnable work); "touching no mapped path" is broader than "changed no path"; the merge-queue question is asked only for an eligible project (`domain-extensions/04:167`); "deciding neither" reads as if it applied to four outcomes, the specification says it of the unobservable one only (`state-catalogue/07:41`) | wording | info |
| L3-10 to L3-14 | `README.adoc:105,303`; `commit-boundary.adoc:16`; `finalize-pipeline-and-merge.adoc:18` | the `realized` row names `phase-sub-workflows` (defensible, no cycle); the forgejo dependency points at a later milestone (the edge was asked for); the new lift-removal task does not list the `references.schema.json:29` wording and two Traceability rows (a lead); `ci.checks` has no package that builds it (a lead) | none now | info |
| clean | README rows and package texts agree (`README.adoc:105,177,271,273,277,301,303,307,309`); no removed correction-plan id, no stored-`blocks` claim, no "written by the server" for a derived field remains; the owner-open and reported items are untouched | | | clean |

**Layer 3 verdict:** findings — 15 points (five minor, ten info); not shown by reading — nothing (no build). No major finding, nothing reported at once.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| Project Setup, Planning the Work | a package text states tasks and a Done When with anchored watch items | partly | L3-1 to L3-4 |
| Standing rules (this plan) | documents only | conforms | no Java, no specification, no schema |
| CLAUDE.md Git Workflow | merge through the queue | conforms | squash `36633b9b` (the squash-message step of branch-cleanup did not run: no `ci repo command` exists, per the lane) |

## Metrics and Anomalies

- Harness claude, model `claude-sonnet-5-5` (self-reported, `refreshed: false`), plugin `0.1.2184`, executor `0.1.2184`; `total_tokens=29871160` (a floor), `total_wall_seconds=41435` (11 h 30 min); finalize used 78 percent of the tokens because every post-push review finding re-ran the steps that depend on the head (lessons 011 to 013). `complete: false` for five keys, all unobserved facts, never 0: `effort`, `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`, `sonar_open_new_issues` (Sonar produced no analysis for a documents-only pull request; the first sonar-roundtrip firing failed and the operator chose "record unknown and proceed").
- Review: CodeRabbit findings f421e7 and 94fbb8 (a package text said existing code already did what it inverts or does unconditionally), ff1860 (a sibling package builds the `realized` event), and the code-review findings d28099 and dc789b (Done When said no watch item anchored although a task links an anchored section), 1a9f6c (`blocks` is derived): all fixed in the pull request.
- Operator decisions during the plan: Q1 drop commit-boundary tasks 2 and 3 (the lift-removal task restored later), Q2 consultations under phase-sub-workflows "Left out" without a plan id, Q3 keep the README diagram-generator comment, Q4 the epic-auto dependency on `ci-contract-finalize-operations`; the CodeRabbit quota wait resolved by review-bot recovery for this plan only.

## Routing and Merge Behavior

- Merge: through the merge queue (`merge_mechanism=merge_queue`) as `36633b9b`; `cleanup_owed=false`.
- Thirteen candidate lessons: seven about `plan-marshall:*` components (001 phase-6-finalize, 008 to 010 script-failure clusters of `automatic-review`, `manage-architecture`, `manage-findings`, 011 and 012 phase-6-finalize, 013 automatic-review) forwarded verbatim to the plan-marshall `lessons-routing` inbox (`pm-mcp-core-m2-m4-v2-068` to `-074`, uncommitted in that ledger until its land). Six about package-text authoring (002 to 007: derived or stored fields, Done When watch lists after a task gains a link, "already implemented" claims read without the code path, "no package builds X" without a search of the siblings) are the orchestrator's staging practice: recorded in `epic.md` Decisions, not forwarded.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#636`; `landing` `landings/PLAN-V2-210.md`; `plan_marshall_plan_id` `plan-v2-210-package-texts-third-doc-review`
- [x] the fourteen messages archived (the seven lessons forwarded first)
- [x] L1-1, L3-1 to L3-4, L3-5 and the wording and lead items staged as deliverable 4 of the new PLAN-V2-213 (same package family); L3-10 to L3-14 need no action
- [x] the Decisions entry (staging practice from lessons 002 to 007) and the Open Defects of the forty-second run (second pass) written
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **Staged:** deliverable 4 of PLAN-V2-213.
- **Owner-open items unchanged** (held in `epic.md`): the CI contract at the act, `finalize.land-message`, surfacer and retrospective-aspect selection, refine `evidence_class`, FIX to `fixed`; the findings write seam and the git branch-sync-state probes (reported, not owned).
- **Specification drift to report, not edit** (unchanged since 195): `workflow-dsl/06-reusable-sub-workflows.adoc` "six phases"; `job-runtime/02` item 2 against section 1 of the 2026-11-24 doc review.
