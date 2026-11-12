envelope_version=1
sender_type=plan
sender_id=rb-v2-66-process-tree-sweep-pid-reuse
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-12T21:25:00Z
revision=1
amended=2026-11-12T21:26:35Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-66-process-tree-sweep-pid-reuse
epic=pm-mcp-core-m2-m4-v2
pr=#390
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.2114
executor_version=0.1.2114
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-66 — the PID-reuse window of the process-tree sweep and the unreadable start instant

Merge commit `1283710f68feab933f7caaf4aa9da09c10ab2e4d`, PR #390. Branch
`fix/v2-66-process-tree-sweep-pid-reuse`, base `main`.

Standing rules read: **r7** (the spec's "r4" is stale; the file is authoritative). Rules this plan named:
R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19, R20.

Spec read: `spec_source=ledger_worktree` (the shared ledger worktree's copy); no `spec_differ` marker.
`executor-skew`: **match** (executor 0.1.2106, lane 0.1.2106) at session start; the landing-time executor is 0.1.2114.

## 1. Deliverables

Every `Done when` clause, as written:

| Done-when | Order | Verdict | Test / evidence | Commit |
|---|---|---|---|---|
| "the table of Deliverable 1 passes **and fails** when `pidReused` is changed to answer `false` for an unreadable instant" | `mutation-after` | met | `SubprocessJobRunnerPidReuseTest` (5 tests: own unreadable, current unreadable, equal, differ, handle-destroy-once). Green at `3b61f590` (5/5, `maven-2026-11-12-100936.log`). Failing output at `3b61f590` with `pidReused` mutated to `!current.isEmpty() && !own.isEmpty() && …`: 5 run, 3 failed (`ownInstantUnreadable:117`, `currentInstantUnreadable:125`, `unreadableInstantEndsThroughTheHandle:159`), log `c1e7b34dd1224f5db3877810609775bc.log`; mutation reverted immediately | `3b61f590` |
| "the ADR states the JDK reading and the choice for lead (1)" | `none` | met | ADR `runtime-job/017-sweep-through-the-process-handle.adoc`: the reading with `jdk-25-ga` permalinks for all four OpenJDK files, the handle choice, and the lead (2) answer with its cost | `13e032bb` (extended `db590e44`, `2f88c35f`, `05cefeab`, `2a626b24`, `277c5476`) |
| "if the sweep signals through handles, `ProcessTreeTrackerTest` passes beside every existing test of `runtime.job`" | `none` | met | `ProcessTreeTrackerTest` rewritten to the handle seam (strict doubles enforce `destroy`→`destroyForcibly`, the survivor's command is asserted, every test asserts no numeric signal); 145-test targeted run green, then the whole `runtime.job` suite inside 10141/10150-test full runs | `62a91c86` (hardened `db590e44`) |
| "the guide and the Javadoc state the chosen behaviour" | `none` | met | `ProcessTreeTracker` class and `sweep()` Javadoc, `runtime-job.adoc` § Descendants and § Termination, `ProcessGroupSignals` Javadoc, ADR 008/013 pointers | `13e032bb`, `db590e44` |
| "**Sonar:** the SonarCloud search of the pull request lists no open issue … and its quality gate is green, before the pull request is reported ready" | `none` | met | public API on PR 390: `issues/search?pullRequest=390&resolved=false` → `total: 0`; `qualitygates/project_status` → `OK` (new coverage 100 %, duplication 0 %, 0 hotspots). Re-read after every push | — |
| "**Build:** `verify -Ppre-commit` …, then `verify` of `pm-runtime` …, `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-runtime -am` are green" | `none` | met | `verify -Ppre-commit` green (10141 tests at `db590e44`, 10205 on the 067aef00 merge, 10150 on the 44c08b5d merge); `verify -pl pm-runtime -am` hit the daemon bound (443 s) → split form: `test-compile` green and `test` green (10141); `verify -Pcoverage` green at `--timeout 2050`; `verify -Pintegration-tests -pl pm-runtime -am` green (43 tests) | `db590e44` … `c9fc22e3` |
| "**Links:** run the links check (R17)" | `none` | met | `BROKEN LINKS 3`, all three naming `plan-marshall-documentation/doc/specification/client-profiles/04-judgment-endpoint.adoc`, deleted in the sibling by PLAN-V2-129 — the sibling's own links, the Done-when's carve-out; no broken link names a file this change touches | `db590e44` |

Per-head test runs on the PR's heads: `db590e44` (10141), `2f88c35f` (10141), `05cefeab` (10141),
`2f88c35f`→`0cbdc96d` merge (10205), `277c5476` (10205), `c9fc22e3` merge (10150). A head that was not tested was never
a green head.

**Deliverables:** D1 met, D2 met, D3 met — `deliverables_done=3`.

## 2. Personas and skills

- Production code (ProcessTreeTracker, SubprocessJobRunner, ProcessGroupSignals): `persona-implementer` standards —
  applied from the repository's conventions and R20 (JSpecify `@NullMarked`, generated test data, EasyMock doubles).
- Unit/module tests: `persona-module-tester` standards — generated data, EasyMock doubles (a strict mock where order is
  the contract), no AAA comments, an assertion message on every assertion.
- Documentation (ADR, guide, Javadoc): `persona-documenter` standards.
- Self-review: `persona-code-reviewer`, loaded by the dispatched sub-agent as a standard.
- The spec declares **`skills: none named`** (its deliverables carry no `Skills:`/`Persona:` rows); no skill was added
  for surface drift — every touched file is `pm-runtime`'s own source, tests and documentation.

## 3. Hypotheses

- The JDK's `destroyProcess` passes the handle's start time to `destroy0` and the native refuses a PID that moved —
  **confirmed**: `ProcessHandleImpl.destroyProcess` (calls `destroy0(pid, startTime, force)`) and
  `ProcessHandleImpl_unix.c` `Java_java_lang_ProcessHandleImpl_destroy0` (`start = isAlive0(pid)`; `kill` only when
  `start == startTime || start == 0 || startTime == 0`). The reading also shows what the hypothesis overstated: the check
  and the `kill` are two system calls, so the handle form **narrows** the window to them rather than closing it. The
  residual window is recorded as accepted in ADR 017.
- "A platform that reports no start instant records no member" — **confirmed**: `Descendants.system()` drops a member
  whose `startInstant()` is empty (`ProcessTreeTracker`), so the sweep has nothing to signal there. Linux reads
  `/proc/<pid>/stat` field 22 and macOS `kinfo_proc.p_starttime`; both supported platforms report start instants.
  CodeRabbit's review sharpened this further: an empty instant also arises on Linux/macOS through the exit race between
  the handle lookup and the info read, and only `PROBE`/`LLM` jobs carry a tracker — both are now in the ADR.
- The verify-first check found no open PR touching `ProcessTreeTracker` or `SubprocessJobRunner`.

## 4. Findings

- **Self-review round 1** (dispatched sub-agent, read-only): F1 the `pidReused` seam was undeclared and absent from the
  ADR — fixed (ADR records it, Source names the file); F3 the JDK reading had no artifact — fixed (`jdk-25-ga`
  permalinks); F5 a false Javadoc sentence — fixed; F6 two documents still claiming the sweep goes through
  `ProcessGroupSignals` — fixed (ProcessGroupSignals Javadoc, ADR 008, ADR 013); F8 the survivor's command was
  unasserted — fixed; F9 the escalation order was relaxed — fixed (strict mock); F10 a dead generated PID — fixed;
  F11 `Member` equality in the survivor filter — fixed (PID-keyed). **F4 refuted**: `ProcessHandleImpl.info()` is a fresh
  native read on every call, not cached on the handle, so the survivor command is read at sweep time.
- **Self-review round 2**: `self-review: clear`.
- **PR findings** (CodeRabbit, 2 threads, all minor documentation accuracy), each replied to with its commit and resolved:
  - `coderabbit inline inline-1` — an unreadable instant also arises in an exit race on Linux/macOS, and only
    `PROBE`/`LLM` jobs carry a tracker; then two further precision rounds on the two causes and on what each platform can
    produce with real handles. Fixed in `2f88c35f`, `05cefeab`, `2a626b24`, `277c5476`.
  - `coderabbit inline inline-2` — the JDK narrows, not closes, the PID-reuse window, and the signal-permission bound is
    `kill(2)`'s (same user or matching real/saved UID, widened by `CAP_KILL`), not a blanket "refuses across users".
    Fixed in `2f88c35f`, `05cefeab`; CodeRabbit marked it addressed.
- No CI failure and no build failure that was not a daemon bound expiry.

## 5. Reviewers

| Reviewer | Kind | Outcome | Head reviewed | Trigger / wait history |
|---|---|---|---|---|
| `coderabbit` | required (`quota_limited`, `review_complete_after_handling`) | reviewed | `db590e44` at 18:08:01Z (COMMENTED review; `final_review_risk_coverage` names `db590e44`, `kind=reviewed`); later merge-head reviews reported "no actionable comments" with Minimal risk | quota refusals 13:39, 15:19, 16:29; triggered again 18:01; reviewed 18:08. No re-trigger after later heads — the `review_complete_after_handling` exemption: the review is on record and every finding is handled. |
| `sourcery` | optional | refused once, then a review check on a later merge push returned SUCCESS | — | quota refusal 13:39 ("3 days and 20 hours"); optional, never gating; a later automatic run passed with no findings. |

## 6. Operator questions

1. Asked: "D1 row 2 unreadable-instant test cannot be produced on Linux without a seam" — options: "Add a
   package-private seam (Recommended)", "Land 3 rows, report D1 not fully met", "Stop and report blocked". **Selected:
   "Add a package-private seam (Recommended)"**. The seam is `SubprocessJobRunner.pidReused(Launched, LongFunction)`; the
   production path is unchanged (`ProcessHandle::of`).

No free-text operator messages.

## 7. Rule check

- `guard: none on this harness (unenforced)` — nothing was enforced, so no guard log lines exist for this run; this is
  not a claim that every rule was kept.
- Deviations the run itself made:
  1. **Reviewer quota policy, step 5**: the escalated CodeRabbit trigger (`@coderabbitai full review`) was posted at
     16:28:59Z, 69 m 40 s after the 15:19:19Z refusal — before the 90-minute floor. Recorded at the time, corrected
     afterwards (the next trigger waited the full 90 minutes and produced the review).
  2. **R17** prescribes running the sibling's `trace.py` directly; it ran exactly as written, outside the project
     executor, because the standing rule names that path and no executor notation exists for it.
  3. **Persona skills** were not loaded through the `skill` tool for the implementation steps; their standards were
     applied from the repository conventions and R20 (the self-review sub-agent did load `persona-code-reviewer`).
  4. Builds used the spec's own targeted variants (`-pl`, `-Pcoverage`, `-Pintegration-tests`, `--timeout`) of the
     resolved canonicals, as its Build clause requires.
- Everything else was kept: one command per shell call, file reads through the file tools, temp files only under
  `.plan/temp/`, commits staged by name with the resolved trailer, no `git stash`/`restore`/`checkout --`/`rebase`, no
  forced push, no `.plan/` state written outside the allowed paths.
- `executor-skew` verdict: **match** (recorded in the landing rather than asked about).
- Spec read: `spec_source=ledger_worktree`; no `spec_differ` marker.

## 8. Build server

- Setup preflight on the worktree: **ready** (registered, version 1).
- Mechanism: every successful build ran `daemon_longpoll`; none ran in-process.
- Bounds and fallbacks:
  - `verify -pl pm-runtime -am`: routed run hit the daemon's supervisory bound (443 s, `timeout_source: learned`) — not a
    red build. Continued with the split form: `test-compile` green, then `test` green (10141 tests).
  - `verify -Pcoverage`: first attempt hit the `daemon_default` 1800 s bound; re-run with `--timeout 2050` and green. No
    launch fault, so no launch-fault retry.
- No `killed` job.
- `runtime_stamp_reason`: none in either resolve.

## 9. Residue

- **Spec-layer finding for the orchestrator (R21 layer 1):** the Objective's premise that signalling through the handle
  "closes the window" is imprecise. The JDK checks the start time and then calls `kill(2)`; the reuse window is narrowed
  to those two adjacent system calls, not removed. ADR 017 records the residual window as accepted with its bound. A
  follow-up spec that speaks of a *closed* window should be corrected.
- **Shared entries, named:** `SubprocessJobRunner.java` (PLAN-V2-42's file) gains the package-private `pidReused`
  overload; `SubprocessJobRunnerTest.java` (PLAN-V2-42) has one edited test (`failingSweepDoesNotAbortTheSettlement`
  injects the failure through the member's handle, since the sweep no longer signals numerically). Both are recorded in
  ADR 017.
- **Configuration gap:** `.plan/marshal.json` has no Sonar block, so `resolve` answers `sonar: not_configured` for a
  project SonarCloud analyzes. The read was taken through the public API per R18. The landing facts carry the measured
  `0` (rendered with `sonar-facts --state configured`), not the config's `not_configured`.
- The optional Sourcery reviewer remains on a hard quota for this operator for ~3 days; it does not gate.
- `total_tokens=unknown` is a recorded gap, not a measured zero: this harness exposes no token count to the agent, and
  the runbook lane records no per-phase metrics. The consumer's completeness check reports it as one missing key.
  `plugin_version=0.1.2114` is the install stamp's version (`executor-skew`'s `lane_version`), which is the plan-marshall
  bundle version the landing-time resolve's runtime-info seam reported as `unknown`.
