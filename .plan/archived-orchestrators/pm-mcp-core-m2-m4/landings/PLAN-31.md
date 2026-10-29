# Landing Analysis: PLAN-31 — Model slots, usage rows and learned budgets

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #164 (merged as 88ec9c7092b3f9676171cc31bc3df0d5305832f0)

> Landing record for one shipped plan. Source: inbox message `rb-31-slots-usage-and-budgets-001.md` (runbook lane,
> opencode, 2026-10-25T19:53:56Z), corroborated against git at `88ec9c70`, the PR and its checks through the `ci`
> abstraction, the SonarCloud public API for `pullRequest=164`, the build logs the message cites, and the code the
> message's open findings name. The message is a lead; every fact below marked "checked" was read from git, the PR or a
> run of mine, not taken from it.

Standing rules: r1 and R01 to R14, R16, R18 named by the landing (R15 and R17 are not in its list). R05 is named and not
applied as written: the specification sections and watch items were kept, on purpose, as the landing says.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1–D3 (model-slot store and scheduler, the usage pointers into the metrics row, the learned budgets) | shipped-as-specified | Checked: `core.store` gains `ModelSlotStore`, `ModelSlotCodec`, `MetricsRow`, `MetricsCodec`, `MetricsStore`, `Measurement`, `HarnessUsage`, `UsagePointers`, `AttemptEnd`, `BuildTimings*`, `ChangeLedger*`, `QualificationSettings*` among 24 `src/main` Java paths; `runtime.launch.ModelSlotScheduler` exists with `request`, `release(String)`, `releasePlan(String, String)`, `snapshot()`, `slotCount()` and the sealed `Admission` (`Granted`, `Queued`, `Deferred`, constant `writer_holds_plan`). |
| D4 (the minimal writers and readers) | **shipped in part** | The lane reports `deliverables_done=3` of 4. The writers exist and round-trip; the lane's own final review found two of them write outside the scope's transaction journal. Corroborated in the code at `88ec9c70`: `MetricsStore` is built over an `AtomicStoreFile` and `ChangeLedger` over a `JsonlStoreFile`, neither over a `ScopeTransaction`; `QualificationSettingsStore` has no `conflict` refusal on a stale revision (the file mentions `revision` only to increment it). The specification text the lane cites for the sidecar rule (`store-schemas/05` line 17, `13` line 61) was not re-read. |
| Docs and package retirement | shipped | Checked: ADRs `core-store/042` and `043`, `runtime-launch/005`, `core-store.adoc` and `runtime-launch.adoc` extended, `doc/plans/slots-usage-and-budgets.adoc` deleted, its index row gone, Depends-on columns of other rows cleaned. |

Whole diff, checked (`git diff 4a8c034c 88ec9c70`, first parent `4a8c034c` = #176): **46 paths** (34 `A`, 11 `M`, 1 `D`),
+7334 / −55: 24 `src/main` Java, 10 test Java, 12 documents (three ADRs, two developer guides, three specification parts,
the index, `Traceability.adoc`, the package file, `doc/plans/operator-wait-and-machine-scope.adoc`). The landing states no
count.

Surface delta (`inbox landing-check`, declared surface of `PLAN-31-slots-usage-and-budgets.md`, 15 entries, against the 46
realized paths): `expansion_detected`. **7 undeclared:** `doc/Traceability.adoc` (the standard companion),
`doc/plans/operator-wait-and-machine-scope.adoc` (**another plan's package file**), `doc/specification/client-profiles/01-profile-schema.adoc`,
`doc/specification/store-schemas/13-leases-integrity-testing.adoc`, `pm-runtime/.../job/SettlementStep.java`,
`pm-runtime/.../start/LeaseRecoveryStep.java` and `pm-runtime/src/test/.../job/ProbeBudgetTest.java`. **4 declared and untouched:**
the two watch files (`model-work.adoc`, `store-schemas.adoc`), `model-work/09-completion-usage-and-spend.adoc` and
`timeouts/01-job-budgets-and-waits.adoc`. The spec is terminal; its declaration is not corrected.

Done-when, clause by clause (the spec's four, as the landing tabulates them):

1. **Usage of a recorded harness output lands in the metrics row with each column — reported met, corroborated by presence**
   (`UsagePointersTest.recordedHarnessOutputLandsInTheMetricsRowWithEachColumn`, `MetricsStoreTest.roundTrip`).
2. **A missing figure is recorded unmeasured with its reason — reported met, corroborated by presence**
   (`missingFigureIsRecordedUnmeasuredWithItsReason`, `MetricsStoreTest.unmeasured`).
3. **Slots never exceed their count under concurrent claims — reported met, corroborated by presence**
   (`ModelSlotSchedulerTest.concurrentClaimsNeverExceedTheirCount`).
4. **A plan paused at once releases the slots of its workers — reported met, corroborated by presence**
   (`aPlanPausedAtOnceReleasesTheSlotsOfItsWorkers`, `releasesWaiting`, `releasesOneProjectOnly`).
   All named tests exist, as do `copiesEveryValue`, `refusedTask` and `AttemptEndParityTest`. The mutation-after claim
   (commit `45bbca25`, two build logs) is backed by what I could check: `45bbca25` is a commit in git and
   `maven-2026-10-24-100958.log` and `-101417.log` exist under the plan-less build results; I did not open the logs or
   re-run anything.
5. **Sonar (R18) — MET, checked.** `pullRequest=164`: gate `OK`, 0 open issues, new-code coverage 89.1, duplication 1.9. The
   landing's own `step.sonar-roundtrip` facts are `n/a` (the lane's `sonar` state is `not_configured`) and its text says the
   same 0 and 89.1 from the public API.
6. **Build gate — reported, not re-run.** `verify -Ppre-commit` green on the final head `ed88a520` (7498 tests). CI on #164
   is `success` over 30 checks (checked). `verify -Pcoverage` and `verify -Pintegration-tests` are not mentioned: not evidenced.
7. **Links check — not in the lane's rules and not run by it; I did not repeat it for this landing** (the earlier runs at
   `4806a304` and `4a8c034c` read only the stale-sibling `PM-WATCH-GEN-60`).

## Owed findings: the lane's third review ran after the merge

The lane ran its third self-review round **on the merged tree** and reports 7 findings open, owed as follow-ups. Three are
major; each is corroborated above except the spec lines: `metrics.json` is written outside the scope's `ScopeTransaction`
(no digest sidecar), `change-ledger.jsonl` likewise, and the qualification-settings store cannot refuse a stale revision with
`conflict`. Four are minor (the per-scope row partition of `MetricsStore`, two Javadoc and display-name mismatches,
`skip_roles` uniqueness). **No staged spec carries any of them.** Recorded as an Open Defect with candidate owners.

## Metrics and Anomalies

- Tokens: unknown (opencode measured none; `inbox landing-check` reports `missing_keys: [total_tokens]`).
- Model: `deepseek-v4.1-flash/high`, harness `opencode`, effort `high`. `steps` ends `self-review:budget-exhausted`.
- Duration: not supplied; the PR's checks span 3301 s.
- Anomalies:
  - **A third self-review after the merge.** Rounds 1 and 2 found 20 and 8 (all fixed); round 3 ran post-merge and found 7 (open).
  - **Two builds killed externally (SIGKILL), one at a learned bound, one outage of the host network**; the operator chose a re-run each time. A third operator question (CI red for a base defect) was answered in free text, "main is fixed. rebase and continue", and the lane merged `origin/main` because its rule forbids rebase. The red was the `FindingSchemaParityTest` defect of `main` after #171, the same account as the PLAN-29 and PLAN-32 landings.
  - **The sync paste was acted on**: the landing's Residue names the final signatures, as asked, and records that `releasePlan` changed during review from `releasePlan(String planId)` to `releasePlan(String projectId, String planId)`; checked in the code.
  - Rule breaks the lane reported itself: shell `ls`, `find`, `wc` and `sed` (two in-place edits), the persona skills not loaded as skills, one `git commit --amend`. The guard was `none on this harness (unenforced)`.
  - `executor_absent_from_worktree` again: every build ran in process.
  - CodeRabbit: 9, 4 and 4 inline findings plus 2 outside the diff, all fixed and 15 threads resolved; Sourcery refused (diff over 150,000 characters), optional.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `3e6d6a6e`, `2df61a45` and `d40c4691`; re-triggered after later pushes with refusals recorded; merged under the standing review authorisation. Sourcery refused, not gating.
- CI/merge: checked, `ci pr view` reads `merged`, head `feature/31-slots-usage-and-budgets`, merge commit `88ec9c7092b3f9676171cc31bc3df0d5305832f0`, first parent `4a8c034c` (#176). The lane merged under the standing review authorisation (qualifying review, every finding handled, every thread resolved); the merge-group run was not re-read.
- Sonar: read from the public API (above), gate OK, 0 open.
- Collision: PLAN-31 shared the model-slot seam with PLAN-30, which is running, and **touched none of PLAN-30's six `core/store` files** (`JobRecordStore`, `WorkerState`, `WorkerStateCodec` and their tests): the 8 shared files of the cross-check were directory containment, as recorded. `declaration-currency` over the 46 realized paths: 7 specs checked and clean, 1 could not be checked (PLAN-102). Staged specs whose declared surface the landing now changes: PLAN-34 (22 paths), PLAN-42 (26), PLAN-60 (7), PLAN-61 (30), PLAN-71 (33), PLAN-73 (28), PLAN-97 (5), PLAN-30 only through the index README and `client-profiles/01-profile-schema.adoc` (2 files), and PLAN-35 through the index README alone. **The unpredicted part:** PLAN-31 edited `doc/plans/operator-wait-and-machine-scope.adoc`, PLAN-42's own package file, and took the ADR numbers `core-store/042` and `043` and `runtime-launch/005`, so PLAN-42, 71, 34, 61 and 73 write their `core-store` ADRs from `044` and their `runtime-launch` ADRs from `006`.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-31 --status shipped`
- [x] row `pr` stamped — `#164`
- [x] row `landing` stamped — `landings/PLAN-31.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-31-slots-usage-and-budgets` (from the sender)
- [x] epic.md narrative reconciled: queue annotation for PLAN-31; `total_tokens` recurrence folded in (28th); the PLAN-30 × PLAN-31 Watch retired; the "lane in flight must merge the base" Watch retired; new Open Defect for the post-merge findings; the ADR-number drift recorded
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **PLAN-30 adapts over `ModelSlotScheduler` with the final signatures** (above); `releasePlan` takes `(projectId, planId)`.
- **Owed findings** (Open Defect): the sidecar and journal coverage of `metrics.json` and `change-ledger.jsonl`, the qualification-settings revision conflict, four minor items.
- **PLAN-42, 71, 34, 61, 73** read the new `core-store` ADR numbers and the changed package file when they start.
- **`verify -Pcoverage` and `verify -Pintegration-tests`** not evidenced (the PLAN-29 Watch lead on `pm-provider-git` stands).
- **Owed outside this repository**: the production caller of the scheduler (PLAN-30 is the first), the `pm-mcp-server` wiring, the concept and user documentation at the milestone exit.
- **`total_tokens=unknown`**: folded into the standing Open Defect.
