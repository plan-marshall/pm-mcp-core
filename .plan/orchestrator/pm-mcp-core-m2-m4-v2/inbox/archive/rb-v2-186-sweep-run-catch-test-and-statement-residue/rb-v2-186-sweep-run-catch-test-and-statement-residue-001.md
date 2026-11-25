envelope_version=1
sender_type=plan
sender_id=rb-v2-186-sweep-run-catch-test-and-statement-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-25T09:11:09Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-186-sweep-run-catch-test-and-statement-residue
epic=pm-mcp-core-m2-m4-v2
pr=#609
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

# PLAN-V2-186 — the sweep's fifth closeout residue

Standing rules: r7, R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (R17 lifted; R21 binds the orchestrator, not this plan).

PR #609 merged as `3aea14c21ae1b8e69ae09c47def5ff95ee7eb657` (base merge `a6d35a06`; origin/main `c484b594` merged in before the enqueue).

## 1. Deliverables

- **D1 — the `run()` catch is unguarded (fallback branch).** Done-when: "the test of D1 fails on the mutation that reverts `:132`, **or** the decision records why no input reaches the catch with a message-less exception". **Met via the fallback branch.** Test name: none. `order: none` (no failing run; a message-less reach does not exist). Decision: the lock open, the acquisition and the attempt are inside `remove()`'s own catch (`PendingEntryRecordsSweep.java:170-181`), which catches the same five types; the candidate scan's only fault of those five is `PendingEntryStore.entryIds()`'s `UncheckedIOException` (`PendingEntryStore.java:391`) with a composed non-null message; `isResidue` catches `StoreRefusal` and `UncheckedIOException` (`PendingEntryRecordsSweep.java:455-462`), and `store.read` throws only those (`PendingEntryStore.java:344-346`). Commit `cde9f12d` adds the decision to `described()`'s Javadoc. The independent self-review confirmed the reachability verdict.
- **D2 — the test that stands apart, and the helper's wording.** Done-when: "the statements of deliverable 2 say what the code does". **Met.** Test moved with its Javadoc beside the release-fault siblings, `guardedLock`'s `release` parameter reworded to "a no-op", and `described()`'s Javadoc reworded to "returns the message, or the type label when there is none". `order: none` (statement/structure only). Commit `ebd74773`.
- **D3 — the parenthetical that is not the rule set.** Done-when: "the statements of deliverable 3 say what the code does". **Met.** `BuildOutputParser` no longer names a shared Unmeasured rule; each parser owns its own (Gradle `GradleOutputParser.java:143-146`; pyproject `PyprojectOutputParser.java:678-695`; Maven `MavenOutputParser.java:459-468, 615-626`; Jest/TAP likewise). `order: none` (statement only). Commit `79ba54c8`.
- **D4 — the arms the ADR's wording reaches wider than the code.** Done-when: "the statements of deliverable 4 say what the code does; any behaviour change of deliverable 4 has its own failing-first case". **Met.** Option (i) chosen: ADR `runtime-start/016` and the `runtime-start` guide scope the no-message naming to the fault of the removal, state that `no message` means a `null` one, that the kept texts and the I/O text print a producer-composed non-null message, that the `StoreRefusal` arm is named by its outcome code, and that a blank message is printed as it is and a cause is not carried. No behaviour change, so no failing-first case (`order: none`). Commits `4e592459` (the scoping) and `e5a5b3bf` (the self-review's F1 fix).
- **Build gate.** `verify -pl pm-runtime -am` (8497 tests) and `verify -Ppre-commit` green at `4e592459`; `verify -Ppre-commit` green at `e5a5b3bf` (337 s) and at the merge head `a6d35a06` (321 s); targeted `PendingEntryRecordsSweepTest` (23 tests) green at `4e592459` and `e5a5b3bf`. Every pushed head was tested.
- **Sonar.** 0 open new-code issues at the merge head `a6d35a06` (`confirmed`, analysis revision = head revision), quality gate `OK`, 0 hotspots.

`deliverables_done = 4` — every Done-when is met as written (D1 through its own fallback clause).

## 2. Personas and skills

- D1: `persona-module-tester` (spec `Persona: module-tester`); skills `pm-dev-java:java-core`, `pm-dev-java:java-null-safety`, `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`.
- D2: `persona-documenter` (spec `Persona: documenter`); skills `pm-dev-java:javadoc`, `pm-dev-java:junit-core`.
- D3: `persona-documenter`; skill `pm-dev-java:javadoc`.
- D4: `persona-implementer` (spec `Persona: implementer`); skills `pm-dev-java:java-core`, `pm-dev-java:java-null-safety`, `pm-dev-java:javadoc`, `pm-dev-java:junit-core`.
- Self-review and comment triage: `persona-code-reviewer`.
- `skills: none named`? No — every deliverable named skills; all were read (SKILL.md + core standards). No surface drift, so no skill added.

## 3. Hypotheses

- "the Gradle and pyproject Unmeasured rules differ from the sentence" — **confirmed**: Gradle `GradleOutputParser.java:143-146` (negative, skipped > total, int-range overflow), pyproject `PyprojectOutputParser.java:678-686, 693-695` (unrepresentable sum), Maven `MavenOutputParser.java:459-468, 615-626` (int range, unattributable summary).
- "an input reaches the `run()` catch with a message-less exception without `remove`'s own catch" — **refuted**: see D1 above.
- "the messages the kept and unreadable texts print are non-null" — **confirmed** at the producers: `PendingEntryStore.java:319, 326, 391, 481`; `JournalScope.resolveWithoutLinks:191`; `StoreSnapshot.readIfPresent:143, 147`. The `UncheckedIOException` reaching `PendingEntryRecordsSweep.java:458` is `StoreSnapshot`'s (read and stated); `PendingEntryCodec` refuses through `StoreRefusal`, and the `StoreRefusal` arm prints the outcome code.

## 4. Findings

- **Self-review round 1 — F1 (minor, documentation accuracy):** the new ADR/guide clause said the unreadable entry texts print the fault's message; the `StoreRefusal` arm prints the outcome code (`PendingEntryRecordsSweep.java:456-457`). Fixed in `e5a5b3bf`.
- **Self-review round 2:** nothing open in the diff.
- **CI:** green; no failing check.
- **PR comments:** no review threads (`reviewThreads: 0`), no actionable body findings (`review_body_findings_total: 0/0`). The issue comments are the two trigger comments, the CodeRabbit summary/walkthrough (reviewed, "No actionable comments"), the Sonar quality-gate comment, and the two quota notices. No finding to dispose of.
- **S1 (spec premise, info for the orchestrator):** the spec's Claim Labels say "the five `not swept` assertions"; the test class has six (`PendingEntryRecordsSweepTest.java:598, 621, 667, 718, 801, 892`). All six go through `remove`, so the D1 conclusion is unaffected. Layer-1 observation, not a code defect.

## 5. Reviewers

- **coderabbit** (required, quota_limited, review_complete_after_handling): triggered at `e5a5b3bf`; reviewed that head (`final_review_risk_coverage`: `sourceCommitId=coveredCommitId=e5a5b3bf…`, `kind=reviewed`), 0 actionable findings. The summary later carries a "Review limit reached" notice beside the same verdict — the exception the rule names. No re-trigger posted for the later merge head `a6d35a06`, because the done-exemption applies (qualifying review on record, findings none). `review-gate --required-reviewer coderabbit` reported `merge_allowed: true`.
- **sourcery** (optional): triggered; refused on quota (250,000 diff characters, next review in ~13 h). Optional, so it does not gate the merge. No qualifying review exists; the quota policy's wait applies to required reviewers only.

## 6. Operator questions

None asked. No blocker, no bent rule, no reviewer gap needed the operator. Free-text operator messages: none.

## 7. Rule check

- `guard: none on this harness (unenforced)` — the opencode harness has no pre-tool-use guard. This section therefore reports the run's own actions, not a guard log.
- **Executor only:** every script ran through `python3 .plan/execute-script.py …`; every build through the resolved `plan-marshall:build-maven:maven` canonical; every CI action through `plan-marshall:tools-integration-ci:ci`. No CI CLI, wrapper, or inline Python.
- **Shell:** one command per call; no `&&`, `;`, pipes or heredocs; file reads/searches through the file tools; the only temp writes are under `.plan/temp/` and the `NO_PLAN` CI body slot returned by `prepare-body`/`prepare-comment`.
- **Git:** staged by name only; no stash/restore/checkout/rebase/force-push; the only merge is the sanctioned base merge.
- **Write-boundary:** no file under `.plan/orchestrator/` was authored by the plan; the base merge brought `main`'s `inbox/doc-review-2026-11-24-001.md` in as an unmodified main commit. The one inbox write is this landing.
- **spec_source:** `ledger_worktree` (no `spec_differ`).
- **executor-skew:** `match` (`0.1.2183` executor / `0.1.2183` lane). Note: `resolve` reported `executor_version: 0.1.2184`; the two reads disagreed on the patch number, but both were current enough that `resolve` and every subsequent verb worked.
- **Per-head tests:** `4e592459` (targeted 23 + `verify -pl pm-runtime -am` 8497 + `verify -Ppre-commit`), `e5a5b3bf` (targeted 23 + `verify -Ppre-commit`), `a6d35a06` (merge head, `verify -Ppre-commit`).
- Restarts / killed sub-agents / re-dispatches: none. Model switches: none (`deepseek-v4.1-flash/high` throughout).

## 8. Build server

- Setup preflight: `ready` (worktree registered).
- Builds: 9, all `daemon_longpoll`; 0 `in_process_fallback`; 0 launch-fault retries; 0 killed jobs.
- The first baseline `-Dtest` run came back `indeterminate` with `test_selector_matched_nothing` (the selector also ran in `pm-core`); re-run with `-Dsurefire.failIfNoSpecifiedTests=false`, which is a corrected selection, not a fallback.
- Build timeouts: two `verify -Ppre-commit` runs exceeded the single-call wait (returned `running` with a job id) and were re-attached with `build_server wait`; both completed successfully. No daemon bound expiry.
- `runtime_stamp_reason`: none.

## 9. Residue

- None owed. Carried item 106 outcomes: (a)–(d) all adopted into deliverables 1–4.
- Recorded, not fixed (as the spec says): the anonymous-class `getSimpleName()` corner (theoretical), the lane's counts, the runbook-lane `unknown` figures (owned by E27), and the `runtime-job.adoc:291` example (owed to PLAN-V2-162).
- `cleanup_owed=false`: the worktree is removed, the local and remote branch refs pruned, and `main` switched and pulled to the merge commit `3aea14c2`.
