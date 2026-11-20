# Landing Analysis: PLAN-V2-155 — q12-condensation-second-pass

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #524 (squash merge `d8dca108`, 6 files, +67/-61)

> Landing record, plan-marshall form run as a runbook lane row (`rb-v2-155-q12-condensation-second-pass`, harness claude, model `claude-sonnet-5-5`, 6 deliverables by the lane's count: 4 met, 2 met with residuals). One landing message (`-008`, 2026-11-20T13:19Z, `merge_state=merged`) and seven candidate-lesson messages (`-001` to `-007`).
> `inbox landing-check`: `complete: false`, missing `effort`, `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`, `sonar_open_new_issues` (all `unknown`). The review state was read directly instead (below).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the definition, the targets and two baselines in the PR body | shipped-as-specified | the PR body baselines (02 30 956, 05 7 001, 04 42 451 at `origin/main`; 29 911, 6 701, 42 055 at `a62d4db2^`) match `wc -c` of `git show`; the self-set targets are 29 500, 6 600, 41 800. The body names `origin/main` as `2f4faeb1` where the parent of the merge is `2bcdbd8f` (sizes identical). |
| D2 the three parts, sentence by sentence | shipped-modified | all three below the spec's floor and below the self-set targets (02 29 429, 05 6 592, 04 41 450); no `[[…]]`, `[#…]` or `===` anchor missing in any part. The sentence-level pass is thin: line-based counts 140/119/118 (02), 190/187/181 (04), 29/28/26 (05); 8 whole sentences removed over three parts, the bytes come from clause trimming. |
| D3 the table of what was cut | shipped-as-specified | arithmetic holds against the ledger start (02: 4+127=131, 05: 2+25=27, 04: 4+141=145); the 02 baseline of 128 in D1 differs from the ledger's 131, which the PR explains. Four removed rows verified against the diff (04-3, 04-6, 04-111, 04-119). |
| Carried item 35 (a)-(g) | shipped-modified | (a) the not-built marks at `02:70`, `05:55`; (b), (c), (d), (f) done; (e) decided with a reason but not as specified (the `unproven` and three `pm_build` clauses of PM-WATCH-JOB-5 stay, no test names them); (g) the 04 table is in the PR body only; the documentation index count refuted, no PR needed. |
| Done-when: Sonar | not met, waived | CI skips build and Sonar on a documents-only pull request; the operator waived it (landing residue); the PR's Test Plan box is unchecked. |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (general-purpose, the three layers in one brief; it wrote nothing and ran no build). Its measurements (bytes, anchors, sentence counts, the cut table's arithmetic, the lines of the code) are the leaf's; the orchestrator re-read none of them.
- **Verdict:** no blocker. Layer 1 mostly met; layer 2 no requirement, refusal, ordering or fencing rule lost, one kept sentence overstates the code, three small clauses lost; layer 3 the landing's three owed points are real.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | the three targets and baselines are in the PR body and match git | checked | confirms | `wc -c` of the three parts at `a62d4db2^`, `d8dca108^1`, `d8dca108` |
| L1-2 | each part ends below its target and has fewer sentences | checked | confirms (thin) | bytes met; sentences 02 128→127, 04 145→141, 05 27→25 by the lane's `measure.py`, which is uncommitted and not reproducible |
| L1-3 | no anchor missing that another document links to | checked | confirms | `[[…]]`, `[#…]`, `===` compared over the three commits |
| L1-4 | item 35 (e): reduce PM-WATCH-JOB-5 to the `provider_rate_limit` clause and the wait population | checked | refutes (partly) | the guard keeps the `unproven` and three `pm_build` clauses because no test names them |
| L1-5 | the Sonar Done-when | checked | refutes | never run; waived by the operator |

**Layer 1 verdict:** findings — 2 minor, rest info.

### Layer 2 — Implemented

- Ten kept sentences of `04` Model & Effort and Launch Prompt were checked against `LaunchComposer` and `PromptComposer` (slots and `renderModel`, effort narrowing, `effort_narrowed_from`, inline agent, the job-private directory, the result schema, `prompt_digest`, the prefix order, the shared prefix, the skill index, `prompt_composition_failed`): all hold.
- The re-offer rule excludes `awaiting_answer` (`02:75`); "`job_ended` first after the job's own end" is restored in § Fencing; the `lost` row (`02:97`) is textually identical to its parent's, with four causes.
- **L2-1 (major, a statement):** `04` Launch Prompt: "A skill that does not resolve, renders empty or changed its digest fails the launch" overstates the code: the empty check covers the state, writing and domain skills only (`PromptComposer.java:114`), the digest check the role skills only (`:92`).
- **L2-2 (minor, lost clauses):** `04` lost the offer half of "when it composes the offer or the launch … so no role can leave the writing skill out"; `02:75` weakened "when the budget is spent … the next `pm_wait` is answered `end`"; `05` lost the actor of "the server validates the list" and "which may differ from the asker's".
- **L2-3 (info):** two behaviours of the code no part states (an effort with no accepted lower value is dropped, `LaunchComposer.narrow`; the zero_tool server-fixed prompt and its refusal, `PromptComposer.java:55-62`, test `zeroToolPrompt`).

### Layer 3 — Correct

- **L3-1 (major, a stale document):** `pm-runtime/doc/developer/runtime-worker.adoc:643` and `:647` (Failure Modes) say the start-failure guard counts a refused launch or start failure, while `:540-544` and the new Owed bullet of `runtime-launch.adoc` say `startFailed` has no production caller; `:226-233` is probably stale since #518 (`WorkerTermination.java:65` writes `EndReason.TASK_DONE`, `EndCause.TASK_DONE` has no producer).
- **L3-2 (major, a stale document):** `doc/specification/workflow-dsl/02-units-jobs-decisions.adoc:145` (item 4) and `:147` (item 6) state three causes of `lost` (no `class_mismatch`), while `02-task-protocol.adoc:97` states four and cites item 6 as its authority.
- **L3-3 (minor, created by this PR):** `02:60` (Acknowledge) and `02:78` (Closed Task Outcomes) lost their guide link to hold the byte target; the PR discloses it but breaks its own ADR 027 claim that every `===` section keeps one.
- **L3-4 (info):** the links check run from the main checkout finishes in about 1.3 s: `BROKEN LINKS 3`, `INLINE SCHEMA BLOCKS 0`; the three links (`roadmap.adoc:250`, `core-config/010…:62`, `harness-profiles.adoc:13`) are outside `model-work` and pre-existing. The run from a plan worktree reports 3 090 (depth alone), as R17 warns; not tested from a worktree here.

### Review State

6 review threads, 6 resolved, 5 outdated, 0 unresolved; Sourcery approved; the CodeRabbit check is NEUTRAL and the final head `4a3f1e2a` carries no CodeRabbit review (its one included review per hour was spent on an earlier head; the five issues it found there were fixed). SonarCloud never analysed the pull request.

## Metrics and Anomalies

`total_tokens` 20 614 638, `total_wall_seconds` 16 655 (about 4 h 38 min), plugin 0.1.2173. One execute envelope was killed by the weekly API limit (HTTP 429) and resumed. The branch was rebased once onto a moved main. Two finalize inline-fix commits were refused with `no_sanction` and committed by the orchestrator.

## Routing and Merge Behavior

Merged through the merge queue (`merge_mechanism=merge_queue`). `cleanup_owed=false`. The seven candidate lessons from the retrospective concern `plan-marshall:workflow-integration-git`, `plan-marshall:phase-6-finalize` (two), `doc-review`, `plan-marshall:execution-context`, `traced-implementation` and `plan-marshall:plan-marshall`; all seven were forwarded to the `lessons-routing` inbox of the plan-marshall repository (`pm-mcp-core-m2-m4-v2-035` to `-041`) and archived here.

## Reconciliation Actions

- Row PLAN-V2-155 transitioned to `shipped`; `pr` #524, `landing`, `plan_marshall_plan_id` `rb-v2-155-q12-condensation-second-pass` (the lane's id), `lane` stays `runbook`.
- Residue placed: L2-1 to L2-3 and L3-1, L3-3 and the item 35 (e) remainder into PLAN-V2-162 as carried item 58; L3-2 into PLAN-V2-161 as carried item 59.
- The operator's Q12 floor question stays open: the pass met the byte floor with 8 sentences removed.

## Follow-Ups

- Operator: whether the Q12 floor (fewer sentences) is met by a pass that removed 8 whole sentences over three parts; the lane's `measure.py` is not committed.
- Not filed: the `trace.py` links check from a plan worktree (lesson `-003`, now in lessons-routing).
