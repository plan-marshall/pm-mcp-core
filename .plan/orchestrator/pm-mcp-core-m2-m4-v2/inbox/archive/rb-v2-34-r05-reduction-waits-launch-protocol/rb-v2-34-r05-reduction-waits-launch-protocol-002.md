envelope_version=1
sender_type=plan
sender_id=rb-v2-34-r05-reduction-waits-launch-protocol
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-16T15:18:21Z

# Landing — runbook lane: PLAN-V2-34 (r05 reduction: waits, launch, task protocol, consultation)

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-34-r05-reduction-waits-launch-protocol
epic=pm-mcp-core-m2-m4-v2
pr=#456
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=6
review_body_findings_handled=6
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

## 1. Deliverables

Documentation plan: no test is added and no failing run is claimed, so every Done-when's evidence order is `none`.
Merge commit `a62d4db2`, PR #456, merge-group run `43893777208` `success`.

| # | Deliverable | Done-when | Evidence | Commits |
|---|---|---|---|---|
| D1 | classification + repository ADR | met | table in the PR body over every candidate (3 wait rows, 2 wait watch items, 6 launch sections, 5 launch watch items, 15 task-protocol sections, 4 task-protocol watch items, `-55`, 6 consultation sections); ADR `doc/adr/027-…` listed at `doc/adr/README.adoc:47`; its evidence rule (main head vs PR head) added after review | `560c8f3a`, `8fc21e0` |
| D2 | waits | met | rows keep anchors/values/bounds/selector; `PM-WATCH-JOB-5` to its remainder; `PM-WATCH-JOB-30` kept | `c9e8f9d8`, `12fd8f33` |
| D3 | worker launch and slots | met | 10 condensed / 36 kept of the six sections' 46 sentences, 8 textually shortened; `worker-output` kept for the #417 clause; Launch Contract untouched | `e228d461`, `12fd8f33`, `f40d2c0a` |
| D4 | task protocol and consultation | met | whole of `02` (57 condensed / 20 kept) and `05` (24 / 2) per sentence; 27 sentences textually shortened across the three parts; `PM-WATCH-DISP-56` deleted with its only inbound link; `-41/-42/-47` kept with remainders; `-55` recorded as already gone | `af0a6f32`, `12fd8f33`, `322fb301`, `f40d2c0a` |
| D5 | the check | met | `PM-WF-5` corrected (projection/composition/mint/resolve) and gains `ModelSlotScheduler`; `PM-WORK-2` gains `WorkerState`, `WorkerLifecycle`, `runtime-worker.adoc`; Traceability introduction reconciled with ADR 027; no `include::`/schema change; no test reads a file of the surface; links 0 | `64e9f330`, `12fd8f33`, `8fc21e0`, `f40d2c0a` |

Per-head targeted runs of the tests the tables cite (the spec's build clause replaces `verify -Ppre-commit`):

- pm-workflow suite, 85 tests — green at `322fb301` (log `e13aa545…`) and at `f40d2c0a` (log `809ab1b5…`).
- pm-runtime suite, 56 tests — green at `322fb301` (log `ebf929df…`) and at `f40d2c0a` (log `f135e66f…`).
- CI ran the full Maven build on the head (`build / build (25)`, `(26)`, `sonar-build` all `SUCCESS`) and the merge
  group passed; the head's merge-group run is `success`.

## 2. Personas and skills

- `persona-documenter` for the specification, watch and ADR edits; `persona-code-reviewer` for the two self-review
  rounds and for triaging every PR comment.
- `skills: none named` — the spec declares no `Skills:`/`Persona:` lines; `resolve` reports `skills_note: none named`.
- No surface drift: only the declared files plus the shared bookkeeping of R08/R19 were touched.

## 3. Hypotheses

- **Refuted (spec claim):** `bounded-wait`'s floor/cap cell *is* enforced by `ProfileValidator`
  (`pm-core/.../profile/ProfileValidator.java:1102`), not only by `ConnectionSettings:56`; the row is `implemented`.
- **Refuted:** `RestingWait` carries `PM-WF-6`'s resting wait, not the model wait of `PM-IMPL-4`.
- **Confirmed:** `PM-WATCH-JOB-30` unbuilt (`wait_reason` only in `MetricsStoreTest:437`; `provider_rate_limit` nowhere).
- **Confirmed:** the #172 six-line note is absent at the head; nothing was written in its place.

## 4. Findings

- **Self-review round 1** (independent reader, `persona-code-reviewer`): 8 findings, all real, all fixed
  (`12fd8f33`, `fa46f2d0`) — `05` untouched; the wait selector deleted; `weights` made required; the
  `job_scope_mismatch` statement dropped; a false fence-precedence sentence added; Traceability misattribution;
  uncovered read-only clauses deleted; ADR Source not as links.
- **Self-review round 2** (after the operator's Q12 instruction): 12 findings. Fixed in `f40d2c0a`: the
  per-worker-data-home condition, "in both clients", the configuration-digest cross-reference, the wrong guide for
  `skills_not_loaded`, and `PM-WORK-2` in Traceability. Corrected in the PR body: the count and line figures (the
  "22 %/18 lines" claim was wrong and is withdrawn). Accepted and stated: the floor's reach — every section now
  carries its developer-guide link and 27 sentences were textually shortened; the remaining `condensed` rows are the
  single statements the sections point at.
- **CodeRabbit** (required reviewer, reviewed `322fb301`, 6 findings): five fixed in `8fc21e0` — Traceability
  introduction, the ADR's evidence rule, the later-call refusal, the fence order, the enrolment fields; the sixth (the
  `consultation_ids` submit check has no production caller) answered as a pre-existing implementation gap outside a
  documentation-only plan (see §9). Threads: 4 auto-resolved, 2 resolved after their replies.
- **Sourcery** (optional): reviewed and approved on `322fb301`; no finding.

## 5. Reviewers

| Reviewer | Required | Result | Head | History |
|---|---|---|---|---|
| coderabbit | yes (`quota_limited`, `review_complete_after_handling`) | **reviewed, 6 actionable comments, all handled** | `322fb301` | refused on quota at 08:11 (attempt 1), 09:49 (2), 11:20 (3, escalated trigger); attempt 4 at 14:27 accepted (`Review finished`, 14:27:26) |
| sourcery | optional | reviewed and approved | `322fb301` | triggered 08:11:53, approved 14:12:43 |

Standing review authorization was not needed: the qualifying review exists and its findings are all handled
(`review-gate`: `all_handled`).

## 6. Operator questions

none asked. Free-text operator messages: one, at 14:5x — the orchestrator's instruction that the Q12 departure was
not accepted, to complete the per-sentence table for `02` and `05`, apply the floor, correct the `05` statement,
re-run, merge `main`, and follow the quota policy from attempt 4. It was followed item by item.

## 7. Rule check

- **Spec read:** `spec_source: ledger_worktree`, no `spec_differ`.
- **Executor skew:** `match` (executor `0.1.2135`, lane `0.1.2135`).
- **Guard:** `guard: none on this harness (unenforced)` — nothing was enforced and no guard-log line exists.
- **The run's own actions:** two early shell file-search calls (`grep -n`, `git grep`) before the shell rule was
  applied to them — read-only, no file touched; every later search used the file-search tool. All four builds were
  daemon-routed (`daemon_longpoll`), with queue waits of 8 s to 609 s while other plans held machine slots, no
  fallback, no retry, no `killed` job. Six thread replies were published through a pending review owned by the acting
  account and its two stuck review ids were submitted (`ci pr submit-review`) as the tool instructs.
- **Restarts / re-dispatches:** two self-review sub-agents (rounds 1 and 2), both completed in one round each.
- **Base tree:** none created; `cleanup_owed=false`.
- **Worktree removed** with `git worktree remove` (no force) after a clean `status --porcelain`; branch and remote ref
  pruned; main switched and pulled to `a62d4db2`.

## 8. Build server

- Setup preflight `ready`; `build-routing` `auto` (`executor_in_worktree: present`).
- 8 routed builds (4 suites × 2 heads), all `mechanism=daemon_longpoll`; fallbacks none, retries none, `killed` none.
  Queue waits up to 609 s were the machine slot cap held by other plans, not a fault.
- `plugin_version` resolved to `unknown` (the runtime-info seam reports none on this harness).

## 9. Residue

- **`consultation_ids` not enforced (pre-existing, layer-3 defect, reported not fixed).**
  `ConsultationService.validateSubmit` (`pm-workflow/.../consult/ConsultationService.java:376`) is tested
  (`ConsultationServiceTest:203`) but no production path calls it: `TaskProtocol.submit` closes on the skills check
  alone. The specification's refusal is therefore not enforced. Out of scope for this documentation-only plan; it
  needs a staged fix and is named here for the orchestrator.
- **Honest account of the Q12 numbers.** The tables enumerate every sentence of `02` (77 rows) and `05` (26 rows) and
  the six `04` sections (46 rows). `condensed` includes sentences that were already the single statement and only
  needed their section's developer-guide link (R05 r7's form); **27 sentences were actually shortened or merged**
  (14 + 8 + 5). Measured diffs: `02` +17/−26, `04` +13/−16, `05` +6/−6. The earlier "22 %/18 lines" figure was wrong
  and is withdrawn in the body.
- **Sibling record:** the documentation sibling's implementation-watch index still counts 61 items for
  `model-work.adoc` against 60, and its `GAPS WATCH PM-WATCH-DISP` still lists 55 and 56 (`BROKEN LINKS 0`).
- **Pre-existing doc drift noticed:** `pm-runtime/doc/developer/runtime-launch.adoc` § Owed still says the launch
  sections carry an "Implemented by" pointer, false since #286 and contradicted by ADR 027; it is outside this plan's
  surface and was not touched.
