envelope_version=1
sender_type=plan
sender_id=rb-27-job-confinement-and-environment
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-24T06:25:17Z

```landing-facts
schema=landing-facts/1
plan_id=rb-27-job-confinement-and-environment
epic=pm-mcp-core-m2-m4
pr=#161
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=0
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.1996
executor_version=0.1.1996
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced).

# PLAN-27 job-confinement-and-environment — landing

Merged as `7903bf23470c8c36935e398d9bd22d4a07e1943a` (PR #161), branch
`feature/27-job-confinement-and-environment`, base `main`. Standing rules: r1,
R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17,
R18 (R15 not named by the spec). The standing rules were read in full at session
start, revision r1 (2026-10-22).

## 1. Deliverables

The spec has one Done-when paragraph, not one per deliverable. It is **not met as
written**: the Linux Landlock / macOS Seatbelt enforcement, the worker network
namespace, the egress tunnel enforcement and the CLI git variant are `pm-exec`
work in `pm-mcp-clients`, which this plan may not change and which does not
implement them (verified at `pm-mcp-clients` HEAD `54bbc73`: `Os.MACOS` is "process
group only"; no `sandbox_init`, no `unshare`, no `CONNECT`). The `egress` field of
the job record is not implemented here either. `deliverables_done=0` follows from
that one un-met Done-when; the in-repo share of D1-D8 is implemented and green.

| Clause | Verdict | Test / evidence | Order |
|---|---|---|---|
| Linux: write outside the write set / read of `<PM_MCP_BASE>` refused | **not met** | launcher-side (`pm-exec`), no run here | none |
| Confined Gradle build proves the registry option | **not met** | `gradleRegistryArgument` has no caller; the append belongs to the verified template (answered on PR thread) | none |
| Docker socket unreachable without the opt-in | met (unit) | `JobConfinementComposerTest.withoutOptIn`, `withOptIn`; `JobEnvironmentComposerTest.containerEngine` | none |
| Job record carries level and ABI | met (unit) | pre-existing `LaunchReport` → post-spawn record; `JobRecord.Confinement.SEATBELT` + schema | none |
| Probe: base/read/write/env/PATH | met (unit) | `JobConfinementComposerTest.probeOfScript`, `harnessProbeData`; `JobEnvironmentComposerTest.Probe.path` | mutation-after (D2/D5: credential predicate disabled at `dd2ffca0`, `credentialVocabulary` failed, `maven-2026-10-23-231236.log`; restored) |
| macOS run records `unavailable`; Seatbelt; setuid/base refusal | **not met** | launcher-side (out-of-repo) | none |
| Unenforced egress gate, runtime status, `egress` field | **not met** | no gate, no status, no record field; `WorkerEgressProxy` has no production caller | none |
| Egress outside `headless.endpoints` refused, activity + audit | met (unit) | `WorkerEgressProxyTest.refusedDestination` | mutation-after (allowlist bypassed at `dd2ffca0`; failed, `maven-2026-10-23-231256.log`; restored) |
| Pin-like branch / abbreviated / two refs / mismatch (JGit) | met (unit) | `GitIdentityTest.branchNamedLikeThePinnedCommit`, `abbreviatedCommitId`, `twoRefs`, `mismatch`, `logAmbiguous`, `abbreviatedStartPointWithExistingBranch`, `logAnnotatedTagId` | mutation-after (ambiguity guard removed at `dd2ffca0`; `branchNamedLikeThePinnedCommit` and `twoRefs` failed, `maven-2026-10-23-231449.log`; restored) |
| Fixture-remote ambiguity and the CLI variant | **not met** | JGit uses local refs; no CLI variant in this repo | none |
| Confinement sets, bus exclusion, daemon store, environment wiring | met (unit) | `JobConfinementTest`, `JobConfinementComposerTest`, `BuildDaemonStoreTest`, `JobEnvironmentComposerTest`, `SubprocessJobRunnerTest.confinementIsPassedToTheLauncher` (raw `ARGV:` line) | mutation-after (base denial removed at `dd2ffca0`, `writeSet` failed, `maven-2026-10-23-231054.log`; digest 16→15, `digestShape` failed, `maven-2026-10-23-231506.log`; restored) |

Commits: `e0181f8f` (D1-D7), `8e4ff9f8` (D8), `dd2ffca0` (docs), `3649c07a`
(self-review 1), `a0b8dbb1` (self-review 2), `2cbc9f04` (self-review 3 docs),
`187a0fe3`, `ce2bb49a`, `105f59c7`, `0c9b63ea`, `4d5b555d`, `c8862580`,
`0db6c521`, `3df4a2b7` (merge of origin/main), `c391d58c` (review fixes).
Gate: `verify -Ppre-commit` green on the final head (7325 tests,
`maven-2026-10-24-055549.log`); `verify` green on the merged head (7323 tests,
`maven-2026-10-24-053543.log`); CI green on `c391d58c`.

## 2. Personas and skills

Per step: `persona-implementer` for production code, `persona-module-tester` for
the unit tests, `persona-code-reviewer` for the three self-review rounds and the
PR-comment triage, `persona-documenter` for the developer docs/ADRs. The spec
declares no `Skills:`/`Persona:` lines: `skills: none named`. Surface drift:
`pm-core` (`AuditEvent`, `JobRecord`) and the store-schema document were touched
without a declared surface, because D1/D7 need the `seatbelt` value and the
`egress_refused` record; recorded as `skills added: none, reason: shared
vocabulary of pm-core required by D1/D7`.

## 3. Hypotheses

- Expected Surface inferred from `Where` — **confirmed in part**: the job package
  exists, but D8 also needed `pm-provider-git` (the spec's cleanup note already
  listed it). Evidence: `pm-provider-git/.../GitOperations.java:26`.
- The package adds log messages; the holder is not in the surface — **refuted**:
  no new LogRecord was needed. Evidence: no change under `runtime/log`.
- The package makes no HTTP call — **refuted**: the worker egress proxy is an
  HTTP `CONNECT` proxy (not an outbound `cui-http` client), in `WorkerEgressProxy`.
  No `cui-http` dependency was added; `pm-runtime` does not use it here.

## 4. Findings

Self-review round 1 (18 items): fixed the daemon-store mismatch, wired the
environment composer, made the default deny the per-user runtime dir, fixed the
rootless socket rule, the remote-socket leak, digest dedupe, docs, probe env,
PATH, git-hook flag, endpoint parsing and the TODO. Round 1 left the worker
harness paths, probe caches, fixture-remote/CLI git and the data-home owner open.
Round 2 (12 items): fixed the rootless opt-in regression, `ci` XDG over-grant,
container variables per kind, the absent-object pin, the probe anchor, ADR
alternative 2, the TODO and added the runner-argv test. Round 3 (13 items): fixed
the last three false docs; the rest are open (worker harness paths/data home —
PLAN-29; probe toolchain caches — no data source; hook-flag plumbing; the `egress`
field and the proxy's production caller; fixture-remote/CLI git). Self-review
state: **budget-exhausted** (3 rounds, open items reported, never "clear").

CI/Sonar: two Sonar red gates during the run (27 then 2 issues) were fixed in
`187a0fe3`/`4d5b555d`-era commits; final read at the merged head: 0 open new-code
issues, gate OK, new coverage 82.6-83.0%, 0 hotspots. PR comments: 12 review
threads, all fixed or answered and resolved (9 in round 1, 3 in round 2, 2 in
round 3; the Gradle-append and probe-cache threads were answered with reasons).

## 5. Reviewers

- **coderabbit** (required, quota-limited): refused on quota at 01:05, 01:32,
  03:16; after the 90-minute wait it reviewed `ce2bb49a` (9 findings), then
  `4d5b555d` (3 findings), then `3df4a2b7` (2 findings). Every finding was fixed
  and its thread resolved. Re-triggered after the final push `c391d58c`; refused
  on quota at 06:12:29. Standing review authorization applies: a qualifying
  review exists on a PR head (`3df4a2b7`, marker `final_review_risk_coverage`,
  kind `reviewed`), every finding is handled, the re-trigger after the last push
  is recorded, and every other gate is green on `c391d58c`.
- **sourcery** (optional, quota-limited): refused at 01:06 (budget of 250k diff
  characters, 20h); no gate role.

## 6. Operator questions

1. "The project's executor is generated against 0.1.1989, but the 0.1.1996 lane
   text was loaded; the 0.1.1989 script has no `executor-skew`. How should I
   proceed?" Options: regenerate then resume / stop as blocked / proceed on the
   stale executor. Selected: none — free-text answer "give me the commands to
   execute"; commands supplied.
2. "`executor-skew` cannot compare versions (empty `MARSHALL_VERSION`, flat lane
   install). Proceed on the digest-match evidence?" Options: proceed / stop.
   Selected: "Proceed on the digest-match evidence".
3. "Deliverable 7 and the refusal half of D1 live in `pm-exec`, which this plan
   may not change and which does not implement them. How should I proceed?"
   Options: implement the in-repo share and report launcher-side as not met /
   stop as blocked / pause until `pm-exec` lands. Selected: "Implement in-repo
   share, report launcher-side as not met".

Free text: "i ran all three, continue"; "as expected?"; "give me the commands to
execute".

## 7. Rule check

- One shell `grep` was run against a surefire report (rule: file reads only with
  the file tools); the result was re-read with Grep and the run continued.
- One parallel `git add` raced a `git commit` and produced `index.lock`; the
  commit was retried after the add, no state was lost.
- Every build was in-process (`executor_absent_from_worktree`), the routing was
  recorded, no `killed` job occurred.
- `guard: none on this harness (unenforced)`; no guard-log line exists for the
  run, so "all kept" is not claimed.

## 8. Build server

Setup preflight: `ready`. Every routed build fell back to `in_process` with
`executor_absent_from_worktree` (the lane's structural case: worktree creation
supplies no executor and `prepare_execute` is never run). No `killed` job. The
daemon's supervisory bound was hit once on `verify -Ppre-commit` at 1006 s with
all 5463 tests green; the gate canonical was re-run with a raised bound and
finished green.

## 9. Residue

- The plan's package stays open: its Done-when needs the `pm-exec` Landlock /
  Seatbelt / network-namespace / egress enforcement, the `egress` record field,
  the worker start of `WorkerEgressProxy`, and the CLI git variant.
- Open self-review items: worker harness writable paths/data home (PLAN-29); probe
  toolchain caches (no data source in `ProbeCandidate`); the git-hook flag is
  hard-coded false; no fixture-remote/CLI git test; `gradleRegistryArgument` has
  no caller; the package file and its index row were not removed (the package is
  not complete).
- Owed to the milestone exit: concept/user documentation of the confinement, the
  job environment and the worker egress proxy; the roadmap check-off.
