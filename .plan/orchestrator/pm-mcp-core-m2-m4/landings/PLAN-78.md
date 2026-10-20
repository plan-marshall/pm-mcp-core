# Landing Analysis: PLAN-78 — Give the new specification obligations an owner

epic: pm-mcp-core-m2-m4
workstream: WS-10
pr: #111 (https://github.com/plan-marshall/pm-mcp-core/pull/111), merged as squash `72aec5c` on 2026-10-20

> Landing record for one shipped plan. Lives at `landings/PLAN-78.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `rb-78-spec-obligation-owners-001.md` (`kind: landing`, sender `rb-78-spec-obligation-owners`, runbook lane on opencode, model `deepseek-v4.1-flash`, plan-reported; `landing-check`: `complete: false`, `missing_keys: [total_tokens]`). Corroborated by the orchestrator against `ci pr view` and `ci pr queue-state --pr-number 111` (`merged`, `72aec5cb…`, merge-group run 39389548422 `success`), `git show --stat 72aec5c` (17 files, +90 −42, all `doc/plans/`), a word-level `git diff 993a7e7 72aec5c` of every amended package file, a term search of `doc/plans` at the base and at the merged head, `trace.py` run by the orchestrator on `main` at `72aec5c`, the lane's progress file (`.plan/temp/rb-78-spec-obligation-owners/progress.md`) and the commits it names. The harness session store was not read. Tokens, wall clock, the operator's answers and the lane's own gate decisions are plan-reported.

## Deliverable Fidelity vs Spec

The landing counts fifteen deliverables (`deliverables_total=15`, `deliverables_done=14`); it reports D13 as not met as written and gives no row for the lane deliverable T. The spec counts D1–D15 plus T.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 Seatbelt and the worker egress proxy | shipped-as-specified | `job-confinement-and-environment` 6→8 bullets; `Seatbelt`, `egress` and the anchors `macos-confinement`, `worker-egress` appear in no `doc/plans` file at `993a7e7` and in the package at `72aec5c` |
| D2 Git identity check | shipped-modified | Owner is `job-confinement-and-environment`, chosen by the operator (plan-reported); the spec proposed the package that builds `pm-provider-git`, which has no work package. `PM-WATCH-GEN-59`, `git_identity_mismatch` and the anchor `git-identities` are in that package only |
| D3 Update evidence and the profile stages | shipped-as-specified | `profile-loader` 3→4; `update_evidence`, `extension_isolation`, `egress` found in `profile-loader` and `worker-launch` |
| D4 Worker launch | shipped-as-specified | `worker-launch` 4→5; `writing skill`, `harness_update_unverified`, the anchors `job-prompt`, `skill-delivery` |
| D5 `agent_started` in the queue | shipped-as-specified | `task-queue-and-protocol` 6→7 |
| D6 `agent_started` in supervision | shipped-as-specified | `supervisor` 5→6, owner argued from its `Where` (`runtime.worker`); the term is in three files (`supervisor`, `task-queue-and-protocol`, `worker-activity`) |
| D7 Navigation figures | shipped-modified | `worker-activity` 6→9 and `operator-reports` 9→10; `operator-reports` is outside the declared surface (a hint). Two figures of the same specification section are named by no package: `Most expensive turns` and `Largest tool results` (`model-work/09-completion-usage-and-spend.adoc:144-145`, inside `#navigation-figures`), absent from `doc/plans` at `72aec5c` |
| D8 Comparison footprint | shipped-as-specified | `supervision-scenarios`, owner argued from `workflow.scenario`; `comparison footprint` in that file only |
| D9 Remainder of #84 | shipped-modified | `review-simplify` ladder is a bullet of `phase-sub-workflows` (`settle-commit` found there). The attestation was already a bullet of `runtime-control` (8→8): only its Trace and Done When grew (the diff adds the `machine-state-records` link and the `release_unattested` / archive-mismatch / `sha256`-shown-only clause, nothing else). The truncation rule is listed as **No owner found** by operator decision |
| D10 Behavioural conformance harness | shipped-as-specified | `supervision-scenarios`; `Control First`, `Combined Pressure`, `Read Matches` found in that file only |
| D11 Intent record, epic intent, #97/#98 parts | shipped-modified | `phase-sub-workflows` 6→10; `chat-plan-revision` 4→5; `worker-activity` for skill residency; `epic-decomposition` gets the epic-intent bullet and loses its review half to the new package file `epic-review.adoc` (operator decision); `start-source-intake` untouched (it is at 12 bullets) |
| D12 `job-runner` step 3a | shipped-as-specified (disposition only) | Listed in the PR body under **Owed to a staged plan** (PLAN-84); no file expected |
| D13 Conservation and band | shipped-modified | The word-level diff shows no removed word in any amended package file except the closing bracket of an extended link in Trace; no line of a § What bullet changed in the eleven amended files. Exceptions, all from the split: the six review bullets moved verbatim to `epic-review.adoc` (checked line by line), the title, Serves, Where, Trace and Done When of `epic-decomposition`, and one reference in each of `chat.adoc:17` and `epic-resource-and-release.adoc:17`. No package is above 12: the maximum is 10 (`operator-reports`, `phase-sub-workflows`). The plan reported itself "not met as written" for the split's exceptions; the orchestrator reads it as met with the declared exceptions |
| D14.1 Agreement per topic | shipped-as-specified | `role-qualification` 5→6; `topicResult` and `PM-WATCH-DISP-48` found there only |
| D14.2 `pm-provider-http` | shipped-as-specified (disposition only) | Listed as owed to PLAN-86 |
| D14.3 Owed outside, drafts | shipped-as-specified (disposition only) | The five items and the two `DRAFT` use cases are in the PR body, which is the squash message of `72aec5c` |
| D15 Constraints and non-goals | shipped-as-specified | `phase-sub-workflows`: `builder-check`, `outline-satisfies`, `PM-WATCH-PHASE-110`, `PM-WATCH-PHASE-111`, `CN-` found; (4) role-skill wording and (5) corpus item are owed to `plan-marshall-mcp` |
| T Lane test proof | shipped-modified | See Metrics and Anomalies: T3 met, T4 not met as written, T5 not clear, T6 reported "no module touched" |

Check-first (T1) corroborated for what it is: at `993a7e7` none of the 18 anchors and terms the landing's table names appears in `doc/plans` (`git grep` per term: 0 files; `release_unattested` is the exception, already in `runtime-control`), and each appears at `72aec5c`. The progress file records one check-first grep over all anchors (line 38), not one per deliverable; the per-deliverable "failing run" column of the landing is that one result applied to each row.

The landing's "before" counts for two packages are one too low: `supervision-scenarios` is 5→7 (reported 4→6) and `chat-plan-revision` 4→5 (reported 3→4), counted the same way as the other twelve (bullets of § What at `993a7e7` and `72aec5c`). The increments are right and the PR body (the squash message) carries the wrong absolute numbers.

Surface delta (`inbox landing-check`): `unmeasured` (the message supplied neither list). The orchestrator's own count against the spec's Expected Surface: declared 14 entries, realized 17 files; added: `chat.adoc`, `epic-resource-and-release.adoc`, `epic-review.adoc`, `operator-reports.adoc`; declared and untouched: `start-source-intake.adoc`. The added files are the split's consequences and the hinted `operator-reports`.

## Metrics and Anomalies

- Tokens: `unknown` (the opencode harness measured none). Recorded as unobserved, not zero. This is the one missing key of `landing-check`.
- Wall clock: not reported.
- Gate: not run. `build-classify` answered `unknown_verb` because the deployed executor of this checkout maps `runbook_lane` to plugin `0.1.1961` (only `resolve`), while the lane skill read on opencode is newer and calls `build-classify` (the verb exists in `0.1.1969` and `0.1.1971`). The lane followed its rule (no workaround, asked), the operator selected "docs-only by inspection" (plan-reported). Instead: `git diff --check`, `git status --porcelain` (only `doc/plans/*.adoc`), `trace.py` and CI's required checks (31 checks, 0 failing on `a80da5f`, plan-reported; the merge-group run is `success`).
- Link check: the orchestrator ran `trace.py` on `main` at `72aec5c`: `BROKEN LINKS 0`, `WATCH ITEMS NOT REFERENCED FROM REQ/SPEC []`.
- T3: met (see D13). T4: the spec's lane text asked for `verify -Ppre-commit`, `verify` and `verify -Pcoverage` on the head; the same spec's Standing Constraint says the Maven gates have nothing to build. The two disagree; the orchestrator wrote the T4 text without reconciling it, so the unmet clause is partly the orchestrator's. T5: self-review stopped at its three-round limit with the PR body and landing evidence open (reported `budget-exhausted`, not clear). T6: no module touched, so no test count or coverage.
- Guard: none on this harness (unenforced); the lane's rule check says so.
- One question was aborted by the harness and asked again (plan-reported).

## Routing and Merge Behavior

- Reviewers: none reviewed `a80da5f`. `cuioss-review-bot` and `coderabbit` are required by the lane; the PR carried the label `skip-bot-review`, set from this spec's Standing Constraint "Review bots are suppressed (operator, 2026-10-17)". CodeRabbit's own comment (07:28:19Z) says the review was skipped for that label; Sourcery's (07:28:14Z) is the quota refusal. `ci pr comments`: 0 reviews, 0 review threads, 2 comments. The lane asked, naming the gap, and the operator selected "Merge without the reviews (rule-bending)" (plan-reported, not visible in the PR). This standing constraint is in PLAN-78's spec only (searched in `plans/`); the specs of PLAN-64, 81, 82 and 88 do not carry it, so their lanes will trigger both required reviewers.
- CI/merge: merged by the merge queue as `72aec5c`; `in_queue: false`; the lane's worktree is gone and no branch `*78-spec*` remains locally or remotely, so `cleanup_owed=false` holds.
- Collision with the plans that ran beside it (PLAN-81, PLAN-88, both code, still running): none; this plan touched `doc/plans/` only. Open pull requests at the start: #71 (dependabot) only, as the lane recorded.
- Breaking for another repository: none; documents only.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-78 --status shipped`
- [x] row `pr` stamped — `#111`
- [x] row `landing` stamped — `landings/PLAN-78.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-78-spec-obligation-owners` (the sender, per the runbook rule)
- [x] epic.md: the Open Defect "New specification obligations that no package file owns" moved to `settled.md`; new Open Defect for the specs behind the amended package files; new Open Defect for the incomplete landing; the opencode watch and the executor watch updated
- [x] the landing carries no candidate lesson
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **Specs behind the package files.** The spec's own Dependencies said the specs of the plans of the amended packages are regenerated after this plan lands. Staged: PLAN-27, 28, 29, 30, 34, 35, 66. Parked: PLAN-19, 43, 53, 58, 68. `epic-review` is a new package file with no plan of its own; its half was part of PLAN-53's scope. `cleanup` flags the specs whose declared surface moved; PLAN-53 needs the split and a row for `epic-review`. The operator decides when.
- **Unowned remainders, for the operator to keep or place:** the two figures of `model-work/09` (`worker-activity` has room, 9 bullets), the truncation rule (`hypermedia-format/01` rule 4, operator decision: no owner), `UC-PRJ-4` and `UC-PLN-6` (drafts). Owed outside this repository: the five items of D14.3 and D15 (4) and (5), to `plan-marshall-mcp` and `pm-mcp-clients`.
- **The stale executor** is the cause of the unmet gate and will meet PLAN-81 and PLAN-88 at their own gate step. Regenerate the executor before they reach it (outstanding for the operator since PLAN-72, now also for `runbook_lane`).
- **The roadmap check-off** is not owed by this plan.
- Retire nothing for the first opencode runbook landing: the watch "Non-Claude-Code runs need the extended landing verification" keeps its entry, with this landing as evidence (counts wrong in two places, a gate not run, a rule-bending merge with the operator's selection).
