# Landing Analysis: PLAN-V2-195 — the work package texts follow the two doc reviews, and the mechanisms without an owner get one

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #623 (https://github.com/plan-marshall/pm-mcp-core/pull/623), merge `2b4bdee8` (squash through the merge queue), analysed on the fortieth run, 2026-11-26

> Landing record. Source: the lane's inbox message `rb-v2-195-package-text-edits-from-the-doc-reviews-001` (landing-facts present, `merge_state=merged`; `complete: false`, see Metrics), corroborated against git (`git show --stat 2b4bdee8`: 16 files, 127 insertions, 44 deletions, all under `doc/plans/`), the ci abstraction (`pr view`: `state: merged`, `merge_commit_sha 2b4bdee8…`; `review-gate`) and the lane's progress file, and read by one read-only leaf, whose layer-3 points the orchestrator re-read at their lines. Standing rules r7 (R21 binds this analysis). Documents only: no Java, no build.

## Deliverable Fidelity vs Spec

Sixteen files, exactly the declared Expected Surface (13 existing packages, `doc/plans/README.adoc`, the two new packages `commit-boundary.adoc` and `ci-contract-finalize-operations.adoc`); no file the spec does not name and none of the 16 unexplained by a deliverable (leaf; the 16-file count re-read in `git show --stat`).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 phase and finalize packages | shipped-as-specified, with two notes | `phase-sub-workflows.adoc:15` "seven phase units", the call `phases.sanity-check` between `execute` and `finalize`, README `:73` "The seven phases" (both re-read); `re-planning.adoc:16` triggers `infeasible`, `merge_conflict`, `ceiling` only, the removed names inside the removal statement (re-read); `finalize-prune-and-land-files.adoc:15` rewritten around the event `realized` and the `no_production_delta` prune (re-read); `finalize-pipeline-and-merge.adoc` `:20` no pre-wait cap gate, `:21` `pr_title`, `:22` `coderabbit` record (leaf only). Notes: `worker-activity`'s "re-check PM-WATCH-DISP-68" Done-When line is not edited (the Watch bullet already names it); task 3 of `finalize-pipeline-and-merge` gained a dependency on `ci-contract-finalize-operations` that the spec text does not list (it is in the index row) |
| D2 worker, job and onboarding packages | shipped-as-specified, one wording fault | `worker-activity.adoc:18` the closed set `repeating`, `no_progress`, `failing_again`, `edit_churn`, `agent_started`, `over_size` and `outside_footprint` "do not exist" (re-read); `job-confinement-and-environment.adoc` the new task "Build Egress and Build Read Set" `:24` and the narrower write set `:15` (`:15` re-read: **L3-1**); `project-onboarding-unit.adoc:17, :37` the recording build and `build_credential_files` (leaf only) |
| D3 owners of the remaining mechanisms | shipped-as-specified, one widening | `initial-lane-router.adoc:19, :20` the light gate and the tie rule; `build-parsers-and-gate-honesty.adoc:16` the build-neutral derivation, one owner in the package texts; `epic-resource-and-release.adoc:17` adds "staged, never parked, landing analyses done, no re-cut pending" to the spec's launchable rule (the leaf finds it in `model-work/08:264`; leaf only) |
| D4 new packages and the index | shipped-modified | both packages exist with the Tasks / Trace / Done When shape and index rows (`README.adoc:276, :306`, status `open`); beyond the spec text: `ci-contract-finalize-operations` tasks 2 and 3 (`ci.merge`/`ci.enqueue`, the `merge.route-refusal` class) go further than the spec's `ci.pr-observe`, `ci.merge`, `ci.enqueue`, and the Forgejo `behind` mapping is carved out to `forgejo-provider` (`:8`, `:19`) — **L3-2, L3-3** |
| Done-when (a)–(e) | met as the lane reports | (a) mutation-after run on three files (artifact `mutation-after.md`, listed, not opened); (b) one owner per mechanism — **not true for the merge route, L3-2**; (c) no removed name named as built — re-read by `git grep` at `2b4bdee8`: `scope_estimate`, `added_dependencies`, `over_size`, `outside_footprint`, `base_advanced`, `downstream_limit`, `base_overlap_min_files`, `finalize.review-cap-check` occur only inside removal statements, `init.light-gate`, `outline.light-regate`, `required_checks`, `checks_visible`, `consultation_ids` and "six phase" occur nowhere; (d) index and files agree — leaf: 71 rows × 5 cells, every package has a row, no cycle, 489 link/xref targets of the changed files resolve (leaf only), one exception **L3-6**; (e) `verify -Ppre-commit` — not shown by reading |
| Done-when (f) Sonar 0 open new-code findings | not met as written, waived | **L1-1** below |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: 16 package texts read against the specifications they cite and against each other (owners, index cells, links, removed names) is the depth the Dispatch Decision Rule reserves for a leaf; the spec, the lane's narrative and the doc-review message were passed as data copies, and the leaf opened no `.plan/` path. The orchestrator re-read the layer-3 points L3-1 to L3-6 (`job-confinement-and-environment.adoc:15` and `job-runtime/02-confinement-and-environment.adoc:18-19`; `ci-contract-finalize-operations.adoc:16, 17, 32` and `finalize-pipeline-and-merge.adoc:17, 37`; `forgejo-provider.adoc:15, 33` and `README.adoc:102-105`; `commit-boundary.adoc:16, 17, 32`; `project-onboarding-unit.adoc:17` and `README.adoc:303`; `start-from-clients.adoc:22`), the Sonar skip rule (`.github/project.yml:27-35`) and the layer-2 lines named above; points marked "leaf only" were not re-read and are unverified leads.
- **Verdict:** layer 1 findings — 1 (minor); layer 2 clean (4 shipped-modified notes); **layer 3 findings — 8 (one major, L3-2; five minor; two info) and 2 not shown by reading.**

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 (minor) | Done-when "Sonar: 0 open new-code findings at the head" is attainable for a `doc/plans`-only pull request | checked (re-read) | refutes | `.github/project.yml:27-35`: only `doc/log-messages/*.adoc` and `**/src/**` count as code; every other documentation file skips the build and the Sonar leg, so the read is `undecidable` (`analysis_revision_unavailable`). The spec carried a clause its own change cannot satisfy; the operator waived it twice |
| none | the spec's cited lines at the plan's base looked as the spec says; its three HYPOTHESES (index rows inside the surface, one owner of the build-neutral derivation, the `create-pr` owner) agree with the merged text | checked (leaf), confirms | confirms | `phase-sub-workflows.adoc:15`, `finalize-prune-and-land-files.adoc:15`, README `:73` (re-read), `build-parsers-and-gate-honesty.adoc:16` |

Persisted verdicts: none — the findings name no claim of a staged spec of this epic (the spec is this plan's own, shipped).

**Layer 1 verdict:** findings — 1 (minor).

### Layer 2 — Implemented

Every deliverable traces to merged text (table above); the test is the Done-when read by the lane (mutation-after for three representative lines, `pre-commit` green); the 16 files are all explained. The narrative's claims checked against the diff: "forgejo-provider records no dependency on ci-contract-finalize-operations" **true** (`README.adoc:102-105`: dependencies `nothing`, re-read), "the seven-phase statement" **true**, "removed names appear only in removal statements" **true** (re-read by grep), "one orchestrator-level duplication remains" **not shown by reading** (it is the ledger spec of PLAN-V2-192, which the leaf did not open; the orchestrator corrected it in this run, see Reconciliation).

**Layer 2 verdict:** clean; four shipped-modified notes (table above).

### Layer 3 — Correct

A package text is read as the claim it is; the defects are in what the texts say about owners, routes and rules.

| Defect | File:line | Triggering reading | Effect | Severity |
|--------|-----------|--------------------|--------|----------|
| L3-1 | `job-confinement-and-environment.adoc:15` | "a `writer` worker writes only the plan worktree and `.marshall/local/temp/`; `read_only` and chat workers write no project path; a `write_paths` entry inside the project root is never theirs" | the antecedent of "theirs" is ambiguous (the `read_only` and chat workers, or the writer). Read as the writer's, it contradicts `job-runtime/02-confinement-and-environment.adoc:19` ("stays writable for the build jobs and `writer` workers of the project"), while `:18` limits the writer to its worktree and temp: **the specification is itself in tension, which is the lane's own §9 finding**; the leaf rated the package major, the re-read makes it a wording fault of the package (minor) and a specification matter for the documentation repository | minor |
| L3-2 | `ci-contract-finalize-operations.adoc:16, 17, 32`; `finalize-pipeline-and-merge.adoc:17, 37` | the `merge.route-refusal` routing (`behind`, `conflict` re-entering the gate) and the dispatch (`ci.enqueue` on a `queue_required` refusal) appear as tasks and as Done-When tests of **both** packages | the plan's own Done-when "every mechanism has exactly one named owner" does not hold for this mechanism: two packages carry the same state and the same tests, and `finalize-pipeline-and-merge.adoc:17` ("the `behind` route of `merge.route-refusal`") only half-separates them from the provider's. A lane that builds both builds the route twice | **major** |
| L3-3 | `forgejo-provider.adoc:15, 33`; `README.adoc:102-105` | `ci-contract-finalize-operations.adoc:8, :19` hand the Forgejo `behind` mapping to `forgejo-provider`'s "CI-contract task" | `forgejo-provider.adoc:15` names no `ci.pr-observe`, no `behind`; its Done When `:33` tests nothing of it; its row says dependencies `nothing`: the mapping is owned by assertion only | minor |
| L3-4 | `finalize-pipeline-and-merge.adoc:17, 37`; `start-from-clients.adoc:22` | the doc-review message of 2026-11-25 (core #622, merged before #623) | no package carries the `checks_pending` route to `finalize.merge-evidence-wait`, the "charges no retry budget" guard, the barrier outcome `baseline_red_required` or the `adopt` plan's base-branch writes (`start-from-clients.adoc:22` still says the base branch is asked in `init.options`, unconditionally). The lane declined the round by the operator's selection; the items are owed | minor |
| L3-5 | `commit-boundary.adoc:16, 17, 32` | tasks 2 and 3 | they build nothing of their own (the code is PLAN-V2-190's and PLAN-V2-193's) and `:32` states "the lift removal stated" — a test that cannot fail; `:16` names `entry_baseline` under a Store Schemas store that does not hold it and "ADR 006" unqualified (leaf only for the last two) | minor |
| L3-6 | `commit-boundary.adoc:16, 17`; `project-onboarding-unit.adoc:17`; `README.adoc:303` | "(PLAN-V2-193, staged)", "(PLAN-V2-190, staged)", "(PLAN-V2-146, staged)", and a dependency cell "PLAN-V2-190 … (a correction plan, not a package row)" (all re-read) | the plan's own constraint says a package text states the tasks, never the statuses; ledger plan ids and statuses in package files are stale on the day that plan merges, and an index cell naming a plan does not resolve as a package id once 190 lands | minor |
| L3-7 | `operator-reports.adoc:16` | "no `epic.overlap_found`" agrees with this repository's specification; the documentation checkout (local `origin/main` `88ac0f13`, 2026-11-21) still lists it | whether the documentation main dropped it cannot be read here | info |
| L3-8 | `build-parsers-and-gate-honesty.adoc:16` | `phase-workflows/03:259` says the build-neutral derivation also serves the push freshness gate of `git.commit_and_push` | no package names that consumer; the light gate and the sanity-check phase each sit in two packages without a "Left out" line stating the split | info |

**Layer 3 not shown by reading:** the `verify -Ppre-commit` runs at `4799e53f` and `2a8a1d9e` (12963 tests), the mutation-after artifact (listed, not opened), the review bots' behaviour, and the leaf's links check for one `domain-descriptor` slug.

**Layer 3 verdict:** findings — 8 (L3-2 **major**, L3-1, L3-3 to L3-6 minor, L3-7, L3-8 info); not shown by reading — 2. **L3-2 is reported to the operator at once and is not fixed inline.**

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Work Packages | one file per package plus the index; Tasks / Trace / Done When | conforms | the two new packages follow `forgejo-provider.adoc` |
| CLAUDE.md | documents only, no Java | conforms | 16 files under `doc/plans/` |
| `ref-asciidoc` | link and anchor targets exist, tables balanced | conforms (leaf) | 489 targets, the new anchors resolve, the index is 71 rows × 5 cells |
| Project Setup § Planning the Work | package files hold tasks only, each task has its proof | deviates | L3-5, L3-6 |
| Project Setup § Planning the Work | a rule found while planning goes into the specification first | deviates (partly) | L3-1: the tension is in `job-runtime/02:18` versus `:19` and the package took a side without a specification change |
| CLAUDE.md Git Workflow | commit trailer `Co-Authored-By: plan-marshall` | conforms (leaf) | the merge message ends with it |
| CLAUDE.md Build | `verify -Ppre-commit` green | not_shown_by_reading | the lane reports green at two heads |

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none; recorded as an unobserved fact, not zero). Model `deepseek-v4.1-flash/high`, effort high, harness opencode, `plugin_version unknown`, executor 0.1.2184 (lane text `0.1.2183`: `executor-skew match` per the lane). The drain-completeness check is `complete: false`: missing `total_tokens`, `plugin_version`, `sonar_open_new_issues` (Open Defect). `surface_delta: unmeasured`.
- Self-review `budget-exhausted` after three rounds (the operator chose "Proceed to PR and gates"; the round-3 fixes of `4799e53f` were not confirmed by a fourth round).
- Evidence gap: the base worktree at `e451ed04` answered `executor_in_worktree: absent`, so the lane cites no base-run evidence for "fails at `e451ed04`"; the leaf re-read the lines at the base instead (L1 confirms). `guard: none on this harness (unenforced)`.
- The lane reports two of its own shell-rule breaks (a `git ls-files … | grep …` pipe); no other.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) refused at 20:58Z, the lane held 90 minutes and re-triggered at 22:40Z; reviewed `4799e53f` (4 comments handled), `1e2f7e6b` (2 handled), then `2a8a1d9e` with no actionable comments; Sourcery (optional) refused on `hard_quota`, later approved. Landing facts: 0 unresolved threads, 6 body findings, 6 handled. Re-read at 2026-11-26T08:16Z by `review-gate --pr-number 623`: 0 unresolved threads, 6 body findings (`merge_allowed: false`, `body_findings_undisposed`: the provider cannot see dispositions) — **no difference** in the two numbers it can reproduce. `review_body_findings_handled`: **6 of 6 re-read from the lane's progress file** (`finding:` lines 42-47: inline-1 `315483a8`, inline-2 to -4 `900535b9`, inline-5 `315483a8`, inline-6 answered with a stated reason as a specification finding); the provider alone answers 0. The claim that CodeRabbit agreed on inline-6 is third-party text and was not fetched.
- CI/merge: green on `2a8a1d9e` (18 checks, the build and Sonar legs skipped as docs-only); merged through the merge queue as `2b4bdee8` (`pr view`: `state: merged`, `merge_commit_sha 2b4bdee8…`). `cleanup_owed=false`.
- Sonar: `sonar_open_new_issues=unknown`, never 0 (`undecidable`, `analysis_revision_unavailable`, the project's CI never analyses a docs-only PR; the gate-status read is 404). The operator selected "Merge with the Sonar condition waived" twice (the second after a review of the current head arrived). Not re-measured; recorded as the lane's measured claim and the operator's waiver.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-195 --status shipped`
- [x] row `pr` stamped `#623`; row `landing` stamped `landings/PLAN-V2-195.md`; row `plan_marshall_plan_id` stamped `rb-v2-195-package-text-edits-from-the-doc-reviews` (from the sender)
- [x] epic.md: Decisions entry of the fortieth run; Open Defects for L3-2 and the minors; the incomplete landing recorded as an Open Defect (`complete: false`)
- [x] PLAN-V2-192's spec corrected in the same act (the build-neutral derivation has one owner, `build-parsers-and-gate-honesty`; wording only, the Expected Surface unchanged)
- [x] the third doc-review round staged: PLAN-V2-209 and PLAN-V2-210 (see `epic.md`)
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **L3-2 (major) — reported to the operator at once; no plan staged on this finding.** The orchestrator's recommendation: fold L3-2 to L3-6 and L1-1's lesson into the already staged PLAN-V2-210 as a fifth deliverable, because the plan edits the same package files (`finalize-pipeline-and-merge.adoc`, `README.adoc`, `forgejo-provider.adoc`) and the same constraint. L3-4 is already deliverable 1 and 2 of PLAN-V2-210; L3-1's specification half goes to the documentation repository (below).
- **Specification drift for the documentation repository** (the lane's §9 and L3-1; this repository's specification is the operator's to change, the package is not): `workflow-dsl/06-reusable-sub-workflows.adoc` still says "six phases"; `job-runtime/02-confinement-and-environment.adoc:18` and `:19` disagree on whether a project-root `write_paths` entry is a writer's; the documentation checkout still lists `epic.overlap_found` (L3-7). Recorded as Open Defects; no inline edit.
- L3-8 and L3-7: info, recorded, no action.
- The five owners PLAN-V2-195 left open stay the operator's questions (the CI contract at the act, `finalize.land-message`, surfacer selection, refine `evidence_class`, `FIX` to `fixed`), each with its recommendation.
