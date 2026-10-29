# Landing Analysis: PLAN-27 — job confinement, environment and git identity

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #161 — https://github.com/plan-marshall/pm-mcp-core/pull/161 — merge commit `7903bf23470c8c36935e398d9bd22d4a07e1943a`

> Landing record written by the `analyze` verb from `rb-27-job-confinement-and-environment-001.md`
> after verifying the claims against the merged diff, the PR state and the merged tree. **This landing
> is deliberately partial**: the plan merged with its single Done-when paragraph not met as written,
> which the operator chose when asked (question 3).

## Deliverable Fidelity vs Spec

Corroborated against `7903bf23` (31 files, `+2805/−52`), CI, and the merged tree.

| Done-when clause | Verdict | Evidence |
|---|---|---|
| Linux: write outside the write set / read of `<PM_MCP_BASE>` refused | **not met** | launcher-side (`pm-exec` in `pm-mcp-clients`), verified absent there at `54bbc73` (`Os.MACOS` is process-group-only; no `sandbox_init`, no `unshare`) |
| a confined Gradle build proves the registry option | **not met** | `gradleRegistryArgument` has no caller |
| Docker socket unreachable without the opt-in | met (unit) | `JobConfinementComposerTest.withoutOptIn`, `withOptIn` |
| the job record carries level and ABI | met (unit) | `JobRecord.Confinement.SEATBELT` + schema; `LaunchReport` → post-spawn record |
| probe of base/read/write/env/PATH | met (unit) | `JobConfinementComposerTest.probeOfScript`, `harnessProbeData`; mutation-after evidence |
| macOS records `unavailable`; Seatbelt; setuid/base refusal | **not met** | launcher-side (out of repository) |
| unenforced egress gate, runtime status, `egress` field | **not met** | no gate, no status, no record field; `WorkerEgressProxy` has no production caller |
| egress outside `headless.endpoints` refused, activity + audit | met (unit) | `WorkerEgressProxyTest.refusedDestination`; mutation-after |
| JGit pin-like branch / abbreviated / two refs / mismatch | met (unit) | seven `GitIdentityTest` cases |
| fixture-remote ambiguity and the CLI git variant | **not met** | JGit uses local refs; no CLI variant in this repo |
| confinement sets, bus exclusion, daemon store, environment wiring | met (unit) | `JobConfinementTest`, `BuildDaemonStoreTest`, `JobEnvironmentComposerTest`, `SubprocessJobRunnerTest.confinementIsPassedToTheLauncher`; mutation-after ×2 |

**The in-repo share of D1–D8 is delivered and green; the enforcement half is not, because it lives in
`pm-exec` (`pm-mcp-clients`), which this plan may not change.** `deliverables_done: 0` in the facts block is
honest: it counts the one Done-when paragraph, which is unmet. The gate was green on the final head
(`verify -Ppre-commit`, 7325 tests) and on the merged head (7323), and CI was green on `c391d58c`.

Footprint: 31 files; **5 undeclared**, disclosed by the lane — `doc/Traceability.adoc`,
`doc/specification/store-schemas/10-job-and-lesson-records.adoc`, `pm-core/.../AuditEvent.java`,
`pm-core/.../JobRecord.java` and `JobDrainParticipantTest.java`. The `pm-core` pair is the shared
vocabulary D1/D7 needed (`seatbelt`, `egress_refused`); the specification part is the same change's
documentation. Under-declaration, not drift.

## Metrics and Anomalies

- Tokens: `unknown` (standing property).
- Anomalies:
  - **Executor skew handled by the operator**: setup loaded the 0.1.1996 lane text while the executor was
    0.1.1989; the operator asked for commands and chose "proceed on the digest-match evidence".
  - Two Sonar red gates during the run (27, then 2 new-code issues) were fixed in commits; the final read
    is 0 issues, gate OK, new coverage 82.6–83.0 %, no hotspots.
  - One `index.lock` race from a parallel `git add`/`git commit`, retried with no loss; one shell `grep`
    on a surefire report, re-read with the file tool.
  - `executor_absent_from_worktree` again; the daemon's 1006 s supervisory bound was hit once with all
    tests green (ninth lane with the in-process fallback).

## Routing and Merge Behavior

- Review: CodeRabbit refused on quota twice, then reviewed `ce2bb49a` (9), `4d5b555d` (3) and `3df4a2b7`
  (2); every finding fixed and every thread resolved; a re-trigger after the final push was refused on
  quota and the standing authorization carried the merge. Sourcery refused (optional).
- CI/merge: green on `c391d58c`; squash merge `7903bf23`. No conflict, no re-verify signal.
- Sonar: `step.sonar-roundtrip.open_issue_count: 0` — the lane measured the merge head itself under R18
  because its resolve read `sonar: not_configured` (`main` remains at 4, none this plan's).

## Reconciliation Actions

- [x] `PLAN-27` row → `shipped`, `pr` `#161`, `landing` `landings/PLAN-27.md`, `plan_marshall_plan_id`
      `rb-27-job-confinement-and-environment`
- [x] inbox message archived on consume
- [x] `epic.md`: landing entry; **Open Defect** for the unmet enforcement half and the `egress` field; the
      package stays **open** in `doc/plans/` (verified: the package file and its row are still there)
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **The work package is not complete and must not be marked done.** What remains is one follow-up plan,
  which cannot be staged as an in-repo plan because the enforcement is `pm-exec` work in `pm-mcp-clients`:
  the Landlock/Seatbelt launcher, the worker network namespace, the egress tunnel enforcement, the CLI git
  variant, the `egress` record field and the worker start of `WorkerEgressProxy`. Recorded as an Open
  Defect and an owed-outside entry; the operator decides where it is planned (the epic already owes a
  `pm-exec`/child-subreaper package in `pm-mcp-clients`).
- In-repo residue the lane reported, all open: worker harness writable paths / data home (PLAN-29), probe
  toolchain caches (no data source), the git-hook flag hard-coded false, no fixture-remote/CLI git test,
  `gradleRegistryArgument` with no caller.
- Owed to the milestone exit: concept/user documentation of the confinement, the job environment and the
  worker egress proxy, and the roadmap check-off.
