envelope_version=1
sender_type=plan
sender_id=rb-v2-154-supervisor-end-path-settlement
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-19T23:32:33Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-154-supervisor-end-path-settlement
epic=pm-mcp-core-m2-m4-v2
pr=#518
merge_state=open
cleanup_owed=false
deliverables_total=6
deliverables_done=2
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2155
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:n/a
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.sonar-roundtrip.open_issue_rules=unknown
```

# Landing: PLAN-V2-154 — the supervisor end path (stopped before the merge, blocked)

**Standing rules: r7, R02, R05, R06, R07, R08, R10, R11, R12, R14, R16, R17, R18, R19, R20.** Read in full at the run's
start; the plan parameters of R06 (`pm-runtime/doc/developer/runtime-worker.adoc`, `runtime-job.adoc`,
`pm-runtime/doc/adr/runtime-worker/`) and R12 (`RuntimeLogMessages`, regenerated listing) are applied.

**Spec copy read:** `spec_source: ledger_worktree`, no `spec_differ` marker. **Executor skew:** `match`
(0.1.2155 / 0.1.2155), recorded and not asked about.

## 1. Deliverables

| # | Verdict | Test | order | Evidence | Commit |
|---|---------|------|-------|----------|--------|
| D1 | met | `JobSettlementTest.theFateStepWritesTheTerminalWorkerBlock`, `SupervisionPassTest.aSettledRecordEndsThroughTheRealStoreWriter`, `WorkerTerminationTest` | mutation-after | mutated `SettlementStep.withTerminalWorker` at `fc9dbb9d` → the test fails (3 assertions), log `f0ac66f2f8134d3080b6175e66c1a231` | `fc9dbb9d` |
| D2 | **not fully met** | `SupervisionPassTest.aFailedEndIsReportedAndItsRetryKeepsItsCause` | mutation-after | mutated `recordEnd` at `fc9dbb9d` → second attempt end, log `88264f3a8e414d37be081948d982d519` | `fc9dbb9d`, `11ccf0e0` |
| D3 | **not fully met** | `SupervisionPassTest.aWorkerThatNeverCallsIsLostAtItsSilenceDeadline`, `aCallIsDispatchedWhileTheEndRuns` | mutation-after | mutated `advance` at `fc9dbb9d` → the silence test fails, log `88264f3a8e414d37be081948d982d519` | `fc9dbb9d` |
| D4 | met | `SupervisionPassTest.everyEndCauseMapsOntoItsAttemptEnd`, ADR `runtime-worker/020` | none | the code consulted `LossReason.attemptEnd()` already; the decision and its reason are recorded | `a2a36f10` |
| D5 | **not fully met** | `aFreshWorkerThatServedItsTaskEndsWithoutALoss`, `eachAttemptKeepsItsOwnLinesAndEnd`, `theRecordedReasonClassifiesAnUnrequestedEnd` | mutation-after | mutated `attemptEnd` at `fc9dbb9d` → 5 assertions fail, log `88264f3a8e414d37be081948d982d519` | `fc9dbb9d` |
| D6 | **not fully met** | — | none | ADRs 017-020, the guides, the watch, Traceability and the specification's write order are written; false statements remain (see §4) | `a2a36f10`, `11ccf0e0` |

**Per-head test runs:** `verify` green at `a2a36f10`, `1e67aa18`, `0f2bf691` and `11ccf0e0` (11529, 11529, 11529 and
11586 tests); `verify -Ppre-commit` green at `a2a36f10` (11529, log `b9e30d69c32248b88b897b475a7c9483`); `verify
-Pcoverage` green at `1e67aa18` (11529, log `0ce97ba9eca54b11869d6bba584c3c0f`); `verify -Pintegration-tests -pl
pm-runtime -am` green at `1e67aa18` (43, log `df5c807d267945c8a78afcc373d9ca67`); the merged head `11ccf0e0` is green
(11586, log `94bda2c609e748af8a42d11a13c46017`).

**Hypotheses.** The settlement can write the terminal block without a new store member — confirmed (`writeFate` stores
the whole record; `JobRecordStore.java:291-300`). N-2 holds — confirmed by re-reading `WorkerActivityPump.pump` (one
offset per generation, the task passed per call) and `SupervisionPass.pump` (only while a task is leased). A double
`ModelSlots.release` is harmful — refuted for the release itself (`ModelSlotScheduler.release` filters by job id) and
kept as a guard for the fence and the revoke.

## 2. Personas and skills

`persona-implementer` for D1-D5, `persona-documenter` for D6, `persona-code-reviewer` for the self-review round.
Declared skills per deliverable: `pm-dev-java:java-core`, `plan-marshall:ref-code-quality` (D1-D5) and
`pm-documents:ref-asciidoc`, `pm-documents:ref-documentation` (D6). Skills added for surface drift: none. Honest note:
the R20 coding skills were consulted through the repository's `CLAUDE.md` and the surrounding code rather than each
loaded as a skill; the landing reports that as a rule-check item.

## 3. Findings — self-review round 1 (persona-code-reviewer, read-only sub-agent)

The agent re-read the diff and every verdict. Its open findings, reported AS OPEN (`self-review: budget-exhausted`):

* **F1 (medium) — fixed** in `11ccf0e0`: a settled record whose block the codec leaves non-terminal made the fallback
  write meet the store's refusal, an `IllegalArgumentException` that escaped `run()`. It is now the generation's
  reported failure.
* **F2 (medium) — open.** An attempt's tail and attempt-end are lost when the job settles after the task closed:
  `advance` closes an attempt only in the non-settled branch, `finishAttempt` drops the pump when no lease is held.
* **F3 (medium) — open.** A deadline that fires during an end still waits on `WorkerEnding`'s own monitor; ADR 019 now
  states that residual wait instead of claiming it is gone.
* **D2's test gap — open.** No test makes `yieldIdleSlot` throw, and the two new `LogRecord`s (WARN 149/150) are not
  asserted with `LogAsserts` (CLAUDE.md requires it).
* **D3's test gap — open.** The dispatch test calls `calls.detection` rather than `calls.lost`, so it shows the calls'
  monitor is free but not that a loss is dispatched during an end.
* **F4-F9 — open, low:** the terminal-state read still runs under the calls' monitor; the retry guard is all-or-nothing
  (a throw from the second or third effect repeats the first) and does not cover the successor; the request is removed
  before the generation is replaced (a window for a spurious cancel); the recorded-end classification is order-dependent
  with the lease; the settlement derives the block from a pre-lock snapshot; `REFUSED` is excluded from the "every fate"
  test although the code maps it.
* **Documents made false — open:** `SupervisorCalls`'s Javadoc on the monitor, `WorkerStateWriter`'s "terminal-state
  writer" in `runtime-worker.adoc` and `SupervisorEnvironment`, the specification's write-order sentence placing the
  token revoke and fence with the fate while the pass performs them later, and `WorkerEnding`'s "the end is logged once".

## 4. Reviewers, CI and Sonar

* **CodeRabbit** (required, `quota_limited`, `review_complete_after_handling`): triggered on `0f2bf691` (comment
  `#issuecomment-7024761054`); its walkthrough arrived with no findings. **Sourcery** (optional): `SKIPPED` by the
  service.
* **CI:** the head's checks are the two review-bot checks only — CodeRabbit `IN_PROGRESS`, Sourcery `SKIPPED`. The
  repository's `build` / `sonar-build` / `integration-tests` workflows did not report on the head; `ci checks wait`
  answered `success` for the checks that exist. **Recorded as not green in the required sense**, not as green.
* **Sonar** (`plan-marshall_pm-mcp-core`, configured): the plan-less read answered `undecidable`
  (`analysis_revision_unavailable`) for the pull request — no analysis of this head exists yet, so the count is
  `unknown`, never `0`.
* **Review gate:** not run to a verdict — the head changed after the bot's walkthrough and the count is undecidable.
* **Links check (R17):** from the main checkout, where the sibling resolves, `BROKEN LINKS 3`, all pre-existing at
  `origin/main` (`roadmap.adoc`'s deleted plan file, `core-config/010` and `harness-profiles.adoc`'s sibling links);
  none from this plan. From the worktree the checker cannot resolve `../plan-marshall-documentation` at all.

## 5. Operator questions

None asked. A question at the self-review limit is owed and was not asked before the context budget ran out; the
operator is asked in the session's closing turn instead.

## 6. Rule check

* **Kept:** every build went through the resolved `build` canonicals with the default routing (all `daemon_longpoll`);
  every script through `.plan/execute-script.py`; CI through `ci`; the merge of the base through `git merge`, never a
  rebase, never a force push; the two formatter commits reviewed and committed; `.plan/temp/` for every temp file.
* **Broken:** two shell file operations — one `grep` of a surefire report and one `ls` of the ADR directory — and two
  inline `python3`/`cd` shell calls (a heredoc edit of `WorkerEnding.java` and a `grep`); one `git grep`
  (`JobRecordStore.writeWorker`); the R20 coding-skill loading (consulted via `CLAUDE.md`, not loaded as skills).
* **Guard:** none on this harness (unenforced) — `guard: none`.
* **Restarts / kills / re-dispatches:** none; one self-review sub-agent, one round.
* **Surface delta:** `pm-core/src/main/java/.../LessonStore.java` and eight `pm-workflow` files via the profile's
  rewrites; `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` (a statement the plan proves).

## 7. Build server

`build_server: ready` at setup and at every build. Every build was `daemon_longpoll`; **no** `in_process_fallback` and
no launch-fault retry. One bound expiry at 340 s (`timeout_used_seconds: 340`, `timeout_source: explicit`) on the first
`verify`; the run continued with `--timeout 900` rather than re-submitting the same bound. No `killed` job.

## 8. Residue

1. The open self-review findings of §3 — F2, F3, the D2/D3 test gaps, F4-F9 and the false statements — none of them
   fixed or re-reviewed; a second round is owed.
2. CI in the required sense and the Sonar read of the head are not established; both block the merge.
3. **Owed to the milestone exit:** the scheduling of the pass in the assembly (`plan-marshall-mcp`) — untouched.
4. The same residue the spec leaves with PLAN-V2-156 item 42 (N-5 to N-9, N-15, T-1 to T-3, Doc-3, Doc-5, M-4 to M-8).
