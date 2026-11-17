envelope_version=1
sender_type=plan
sender_id=rb-v2-31-supervisor-calls-and-pass
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-17T17:18:01Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-31-supervisor-calls-and-pass
epic=pm-mcp-core-m2-m4-v2
pr=#472
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
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=4
review_body_findings_handled=4
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

# Closing landing — PLAN-V2-31: the supervisor's inbound calls and its supervision pass

**This message supersedes the open-PR landing `rb-v2-31-supervisor-calls-and-pass-001`.** The pull request it reported
as open and deliberately unmergeable has merged, on the operator's decision of 2026-11-17, with the end-path blocker left
to the operator's follow-up plan as instructed.

Standing rules: read at revision **r7** (the spec's First Instruction names r4; r4→r7 amends R05 and R12, adds R20 and
R21). Rules this plan applied: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18,
R19, R20. R15 does not apply (no `pm-workflow` code). guard: none on this harness (unenforced).

## Merge

- Pull request **#472** merged through the merge queue at 2026-11-17 (merge group run 44134034976, conclusion success).
- **Merge commit `3d453e6c45a8b2a28f3a74155292fa6f23bc9a0c`** on `main`; the main checkout was fast-forwarded to it by
  `switch-and-pull` (4bc06ae3 → 3d453e6c).
- Head merged: `2d5068bd` — the plan's commits plus two merges of `origin/main` (`3e5ebfbf` for PLAN-V2-153's
  git-metadata member, `2d5068bd` for PLAN-V2-130/#471/#473/#474/#475/#477/#479/#480). The base moved twice with code, so
  both merges re-ran the gate; nothing was re-run beyond that.
- Cleanup: the worktree was removed with `git worktree remove` after a clean `status --porcelain`; the local branch was
  pruned (the remote branch had already been deleted by the merge queue, so `prune-local-and-remote-ref` reported
  `partial`/`remote_ref_warning` and did exactly what was left); `switch-and-pull` moved the main checkout.
  **cleanup_owed=false** — nothing remains.

## Deliverables

All five are met as written; the plan's *premise* about the end path is refuted (B1 below), which is a spec-level gap, not
a missed criterion.

- **D1 `SupervisorCalls`** — every `WorkerCalls` method implemented (`SupervisorCalls.java`), one test per call. The
  acknowledgement refinement (`LossDetection.acknowledged` on a heartbeat, and none armed by a warm worker's first call)
  is the one deviation from the letter and is recorded in ADR 014. Commits 285b591a, 8a18940f, ab1849ec, e3867aeb,
  a987dd83, 9ae1b999. order: `none`.
- **D2 `SupervisionPass`** — one clock reading per pass, no thread, no timer; pumps, detects, completes ends, yields.
  Commits 3c399198, 118d788f, b99b45ce, 9ae1b999. order: `none`.
- **D3 the five scenario groups** — `SupervisorCallsTest` (18) and `SupervisionPassTest` (8), green inside the whole
  module run on the merged head (10 958 tests). Fail-capability shown by mutating the committed fix three times (jobs
  `e6705d35…`, `a4d0e0d2…`; all reverted). Commits 278d17cd, 673f2dae, 089a1081. order: `none`.
- **D4 ADR + guide + Traceability** — ADR `runtime-worker/014` (next free number, re-checked against `origin/main` after
  both merges), the two guide passages, the `PM-WORK-3`/`PM-WORK-7` lines. Commits e3420dd4, 45c80fcb, 7b2be925.
- **D5 owed rest** — in the PR body and the guide; the work packages `worker-activity` and `question-continuation` stay
  open and nothing was removed.

## The review round (CodeRabbit, head 2ecac207, reviewed 16:27Z)

The required reviewer is quota-limited (`quota_limited`, `review_complete_after_handling`). It refused twice on quota
(11:36Z, 13:10Z, 14:40Z) with its own notice naming a minutes-long capacity window; the lane's 90-minute floor was
honoured between attempts and the escalated trigger (`@coderabbitai full review`, permitted once when no review exists)
produced the review. No review was skipped.

Its review carried **4 actionable findings**; three were fixed and one is the blocker:

1. **Major — `run()` had no `finally`, so a throwing generation skipped the idle yield and the generations after it.**
   Fixed in `9ae1b999`: the loop is wrapped in `try { … } finally { recycling.yieldIdleSlot(); }`. Nothing is caught.
2. **Major — the settled-record end write (B1).** Not fixed, by the operator's decision; answered on the thread with the
   recorded reason.
3. **Minor — `finishAttempt` removed the pump before appending, so a throwing `finish` re-read the log from offset 0.**
   Fixed in `9ae1b999`: the pump is read, finished, and only then removed.
4. **Minor — `lost()` stored a loss and called `ending.end` for a generation the pass had already forgotten.** Fixed in
   `9ae1b999`: it now runs through `known(...)`, which excludes terminal generations.

All four threads were answered (a commit sha for the three, a stated reason for the blocker) and resolved;
`review-gate` on the merged head reports `unresolved_review_threads=0`, `review_body_findings_total=4`,
`review_body_findings_handled=4`, `merge_allowed: true`. The later head raised no re-trigger obligation: the reviewer holds
a qualifying review (commit 2ecac207) and is marked `review_complete_after_handling`. Sourcery (optional) is on a hard
7-day quota and was not obtained; the GitHub check shows it skipped.

## ⛔ The blocker (B1), as merged

The pass completes an end only once the job record is settled, and `WorkerEnding.exited` writes the terminal worker state
through `JobRecordStore.writeWorker`, which refuses a settled record. The settlement writes the fate and carries the worker
block the record held, so the store's contract requires the end path's write *before* the fate — while the fate is what
tells the pass the exit happened. Against the landed writer the first pass over an ended worker throws, the generation
never becomes terminal, later passes throw again, and each retry repeats the pre-write side effects. The fixture's mock
`WorkerStateWriter` hides it. Recorded in ADR 014, the guide and the PR body; the operator's follow-up owns the trigger and
the write order.

## Other open items (unchanged by this round)

1. A `fresh` worker's normal end is recorded `LOST` (`EndCause.TASK_DONE` has no production caller); a worker that dies
   holding the lease records `exited_without_submit`, one whose task closed first records no attempt end.
2. `harnessEnrolled` clears only the roles the registry holds on the harness — narrower than Deliverable 1.
3. The start-failure count is owed to a follow-up: the pass is the only reader of the settled record.
4. Owed cleanups: `forget` on registry retirement for the pass's pump map and finished-attempt set and the calls' maps;
   `AgentStartedDetector`'s process trees; ADR `runtime-worker/012`'s `answerDelivered` entry.
5. The `waitOpened` finally's exceptional path is untested (`WorkerRecycling` is final).

## Personas, hypotheses, findings

Personas: `persona-implementer` (production), `persona-module-tester` (tests), `persona-code-reviewer` (self-review rounds
and the PR-comment triage). The spec declares no `Persona:`/`Skills:` lines (`skills: none named`); R20's coding skills
were loaded and applied. Hypotheses: the `queueTaskId` reading and the record-only exit observation are **confirmed**; the
four-file surface is confirmed with the one recorded seam addition (`StartGuard.firstCall`, an R19 surface delta).

Self-review ran four rounds (the operator added a fourth); every code-level finding was fixed, every spec-level one is
reported open above. PR-comment triage: four findings, three fixed in `9ae1b999`, one answered with the recorded reason.

## Rule check

- **Quota policy, as applied.** Three refusals, the 90-minute floor honoured between each attempt (11:36→13:10→14:40), the
  escalated trigger posted once when no review existed, then the review. No review was skipped; the PR was not merged
  without it.
- **Merging.** Every §7 condition held on the final head `2d5068bd`: the required reviewer's qualifying review with every
  finding handled, no unresolved thread, `verify -Ppre-commit` green (job `93a1e265…`), CI green (run 44132209061),
  Sonar 0 confirmed with the gate OK at 88.7 % coverage, and `review-gate` `merge_allowed: true`. The merge queue — not a
  local merge — produced the merge commit.
- **Operator decisions.** (1) The B1 blocker: "ship as specified, report the blocker" (asked twice; the first answer was
  invalidated by the operator's "re-ask"). (2) The self-review round limit: "continue with a fourth round". (3) The merge
  instruction of 2026-11-17, which this landing executes.
- **Base merges.** Two, both followed by a full gate on the merged tree; no other work was re-run.
- **One environment-dependent failure** was seen and cleared: `DiagnosticCollectorRedactionTest` (pm-core, from main's
  #475) timed out once under load in the merged gate, passes in isolation, and the gate re-run was green. It is not this
  plan's test.
- **Routed builds**: all `daemon_longpoll`; one bound expiry (`timeout_used_seconds 461`) and queue waits earlier in the
  run; no degraded fallback, no `killed` job. No shell file operations, no `cat`/`grep`/`find`/`sed`, no pipe or `&&`.
  `guard: none on this harness (unenforced)`, so nothing was enforced and "all kept" is not claimed.
- `executor-skew` at start: `match` (0.1.2135). The spec read: `spec_source=ledger_worktree`, no `spec_differ`.
- Mailbox: `no_mailbox` at all three reads (setup, before self-review, before the enqueue) — not an empty mailbox, and
  nothing was delivered.

## Residue

Nothing owed on this plan's branch or worktree (`cleanup_owed=false`). The blocker and the five open items above stand,
all recorded in ADR `runtime-worker/014`, the developer guide and the pull request body. `total_tokens` and
`plugin_version` remain `unknown` — this lane has no metrics store, and the runtime-info seam did not report the bundle
version — so the landing is honest rather than complete on those two keys.
