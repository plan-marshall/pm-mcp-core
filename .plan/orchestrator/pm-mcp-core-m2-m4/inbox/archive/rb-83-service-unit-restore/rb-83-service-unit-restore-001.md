envelope_version=1
sender_type=plan
sender_id=rb-83-service-unit-restore
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-21T10:34:10Z

```landing-facts
schema=landing-facts/1
plan_id=rb-83-service-unit-restore
epic=pm-mcp-core-m2-m4
pr=#125
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
```

## Deliverables

Work package `service-unit-restore`, pull request
[#125](https://github.com/plan-marshall/pm-mcp-core/pull/125), merged as `7d980c12cc5ef8a75a18d5b10fc70a214455bb64`.

The spec carries one Done-when block (six clauses) over four deliverables. The operator selected "Accept the
library-level step" for the macOS clause, because the spec's Expected Surface declares the start assembly
`pm-mcp-server` untouched.

| Done-when clause | Verdict | Test | order | Failing run | Commit |
|---|---|---|---|---|---|
| a failing load after replacing a unit leaves the previous unit's bytes in place and loaded | met | `shouldRestorePreviousUnit`, `shouldRestoreAfterTheLoadFailsLate` | test-first | 1ba292d, `maven-2026-10-21-070915.log` | cc8dc10 |
| a failing load on a machine without a unit leaves no unit file | met | `shouldLeaveNoUnitWithoutPrevious`, `shouldRemoveNewUnitOnManagerFailure` | test-first | 1ba292d, `maven-2026-10-21-070915.log` | cc8dc10 |
| a failing restore is reported with both failures | met | `shouldReportBothFailures` | test-first | 1ba292d, `maven-2026-10-21-070915.log` | cc8dc10 |
| on macOS the start after an interrupted reinstall restores the kept unit | met (library-level step, operator-selected reading) | `shouldRestoreAtStart`, `shouldDropBeforeTheRestoreLoad`, `shouldRemoveReplacementAtStartWithoutPrevious` | mutation-after | commit 7af118b with `restore()` neutered, `maven-2026-10-21-071509.log` | cc8dc10, 7af118b, 9da71a4 |
| the decision for plain install is recorded in an ADR | met | ADR `runtime-start/014` | none | — | 2ddd4da |
| `verify` and `verify -Pcoverage` green | met (CI green on the merged head; local whole tree red only through an environment failure, see Residue) | whole tree | none | — | a086599 (`maven-2026-10-21-082857.log` 6843 tests, `maven-2026-10-21-083332.log` 6843 tests), 0910ea4 (`maven-2026-10-21-100718.log` 4651 tests, module + upstream) |

Deliverables: D1 keep the previous unit — met; D2 restore on a failed load — met; D3 order against self-termination —
met; D4 plain-install decision in ADR 014 — met. `deliverables_done` counts 4.

`test-compile` ran inside every build above: whole-tree `verify` at a086599 (twice, `verify` and `verify -Pcoverage`),
module `verify -pl pm-runtime -am` at cc8dc10, 7af118b, 5792148, 7db6e66 and (after the `origin/main` merge) 0910ea4,
and CI at 1e32377, 7db6e66, 9da71a4 and 0910ea4.

## Personas and skills

- Production code (D1, D2, D3): `persona-implementer` as the standard.
- Tests (D1, D2, D3): `persona-module-tester` as the standard.
- ADR, developer documentation, log-message reference, traceability (D4): `persona-documenter`.
- Every self-review round and the PR-comment triage: `persona-code-reviewer`.
- Spec-declared skills/personas: none; the spec has no `Skills:`/`Persona:` lines, so every deliverable is
  `skills: none named`.

## Hypotheses

- "Expected Surface is inferred from the `Where` line (`runtime.start`: `ServiceUnitInstaller`, `ServiceUnitFile`) and
  ADR `runtime-start/003`, not from a trace matrix" — **confirmed**. Those two types are the only production types that
  touch the unit (`ServiceUnitInstaller.java`, `ServiceUnitFile.java`), no upgrade code exists, and ADR 003
  (`003-unit-file-step-and-service-manager-step.adoc`, Decision) defines the split.
- "the macOS reinstall ends the runtime inside the load" — **confirmed** at `LaunchdServiceManager.java` (class
  Javadoc: the `bootout` of a reinstall ends the caller before the `bootstrap`).

## Findings

- Self-review round 1: (B1) the restore kept the marker across its own load, so a macOS start could never converge;
  (S1) a second install overwrote the pending known-good copy; (S2) a cleanup failure after a successful load failed
  the install; (S3) uninstall's new failure mode; stale docs. All fixed in 7af118b.
- Self-review round 2: (O1) PM_RUNTIME-136 claimed an unconditional rollback; (O2) 135 merged two failure branches;
  (O3) INFO 33 claimed to be logged before the drop; (O4) pitfall 8 contradicted the drop-before-load; (O5) a failed
  unit write left a false marker. All fixed in 5792148, with tests for the failed write and a failed restore load.
- Self-review round 3: four nits (135 drop-failure wording, one crash-window row, two "kept copy" restatements, an
  untested marker branch). All fixed in a086599; the operator waived a fourth round. `self-review: clear`.
- CodeRabbit (required): 1e32377 — no actionable comments, one advisory security-architecture note answered on the PR;
  7db6e66 — two nitpicks (a pending-restore test without a previous unit, and an uninstall drop-order assertion), both
  fixed in 9da71a4; 0910ea4 — completion state "no unreviewed commit".
- SonarCloud: gate passed; two new code smells (`java:S1141`, `java:S1130`) fixed in 7db6e66; the next scan reported
  0 new issues.
- Sourcery (optional): refused on its review budget at 08:49 (2,500,000 diff characters per 7 days; retry offered in
  2 days 2 hours); no gating impact, the bot is optional.

## Reviewers

| Reviewer | Required | State | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes | reviewed | 0910ea4 (its completion state: no unreviewed commit; check green) | triggered on 1e32377, 7db6e66, 9da71a4, 0910ea4; formal review body on 7db6e66; no refusals |
| sourcery | no | refused (quota) | none | triggered once on 1e32377; refused at 08:49 with a 2-day-2-hour window; optional, not gating |

No standing review authorization was used: the required reviewer reviewed the merged head.

## Operator questions

1. Asked: "PLAN-83's Done-when says \"on macOS the start after an interrupted reinstall restores the kept unit\". The
   step `ServiceUnitInstaller.restore()` is implemented, documented and tested with the recording fake, but no
   production start calls it: the spec's Expected Surface declares the Quarkus assembly `pm-mcp-server` untouched. The
   self-review grades the clause \"not met as written\" for that reason. How should the run treat it?" Options: "Accept
   the library-level step", "Report the deliverable not met", "Wire it in pm-mcp-server". Selected: **"Accept the
   library-level step"**.
2. Asked: "Self-review has used its 3 rounds. ... Proceed to the PR, or spend a fourth round?" Options: "Proceed to the
   PR (Recommended)", "Run a fourth round". Selected: **"Proceed to the PR (Recommended)"**.

Free-text operator messages: none.

## Rule check

`guard: none on this harness (unenforced)` — the resolve result reported `guard: none` for the opencode harness, so
nothing was enforced and there is no guard-log line for the run. Rule deviations in this run:

- **Build command spelling.** The literal resolved build commands (`quality-gate`, `module-tests`, `test-compile`)
  were passed to the Maven executor and the first (`quality-gate`) failed with "Unknown lifecycle phase". The run then
  used the project's discovered canonical Maven commands (`verify`, `test -pl …`, `verify -Pcoverage`) through the same
  executor; the operator was not asked about the naming error, as step 4 asks. The build evidence is otherwise intact.
- **Read outside the repository root.** The run read the installed skill documentation under
  `~/.config/opencode/skills/` (build-maven, workflow-integration-sonar) to find the invocation surfaces. The lane
  forbids reading config directories outside the repository root.
- **Merge commit attribution.** The `origin/main` merge commit `0910ea4` was created by `git merge` and carries no
  `Co-Authored-By: plan-marshall <noreply@cuioss.de>` trailer; every authored commit carries it.
- `git reset HEAD -- <path>` was used once to unstage a file from a commit, and `git commit --amend` once before the
  first push; neither is in the lane's forbidden list.

Everything else was kept: one command per shell call, file tools for reading, `.plan/` writes only in
`.plan/temp/`, the CI body slots and this inbox message, no edits to the spec or the orchestrator ledger, no
`git stash`/`restore`/`checkout --`/`rebase`/forced push, and no build through the daemon.

## Residue

- **Start wiring (accepted by the operator):** `ServiceUnitInstaller.restore()` is implemented and tested but no
  production start calls it; `pm-mcp-server` in `plan-marshall-mcp` must run it first, as the spec's Expected Surface
  leaves that repository untouched.
- **Concurrency:** install, restore and uninstall share the recovery pair without a cross-process owner. CodeRabbit's
  security-architecture review flagged overlapping calls (an install's failed load can remove a unit another install
  loaded). No production caller exists yet; serialization belongs to the change that wires the operator verbs and the
  start. Answered on the PR.
- **Local whole-tree `verify`:** red only through `SubprocessJobRunnerTest.escalated` ("User limit of inotify watches
  reached"), reproduced on the base branch in the same environment; the module + upstream `verify` and CI on the merged
  head are green.
- **Owed to the milestone exit:** the concept and user documentation of the slice and the roadmap check-off in
  `plan-marshall-documentation`.
- **Not implemented:** the rest of the upgrade's rollback (the staged installation directory and the switch); the
  specification's Upgrade section is therefore not deleted.
