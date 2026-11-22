envelope_version=1
sender_type=plan
sender_id=rb-v2-162-runtime-worker-and-job-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-22T20:23:56Z
revision=1
amended=2026-11-22T20:52:15Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-162-runtime-worker-and-job-residue
epic=pm-mcp-core-m2-m4-v2
pr=#567
merge_state=open
cleanup_owed=false
deliverables_total=5
deliverables_done=0
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:partial,build-gate:partial,self-review:budget-exhausted,pr:done,reviews:pending,merge:pending
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.sonar-roundtrip.open_issue_rules=unknown
```

Standing rules: r7, R02, R05, R06, R07, R08, R10, R11, R13, R14, R15, R16, R17, R18, R19, R20 (the spec names r6 at
emit; the rule file moves a plan to the revision it reads at start).

guard: none on this harness (unenforced).

# Landing report (amended, revision 2) — PLAN-V2-162, partial, PR #567 open

Head `a1dc6827`. Deliverables 1, 2, 3 and 5 are delivered in part; deliverable 4 is now delivered in part (two of
its five major defects). No deliverable has every Done-when met as written, so `deliverables_done = 0`.

## Deliverables

| D | Verdict | Evidence |
|---|---|---|
| D1 | not met as written | 56(a)-(g), 49(a)(b)(c)(e)(f), 75(a) decision; 49(d) split off to PLAN-V2-147/148. Mutation-after `3d8c1de7e7ec408888a4c59cf3e74e5b`. Commit 5656904e. |
| D2 | not met as written | 55(b) partial, 55(c), 19 seam; 55(a)(d)(e)(f) and 30 open. Mutation-after `8ab24591759d40aaa64b7c8a51638a69`. Commit bbb78dbd. |
| D3 | not met as written | 42(e), 41(c), 41(e); 41(a)(b)(d)(f)(g) and 42(a)(c)(d)(f)(h) open. Mutation-after `3d8c1de7e7ec408888a4c59cf3e74e5b`. Commit e7198a2d. |
| D4 | not met as written | 48(a) the supervisor's own end path is handed in (`QualificationService.harness`), 48(c) no worktree for a zero-tool role; 48(b), 48(c)'s release and 48(d)(e) open. Mutation-after `maven-2026-11-22-204211` (`WorkerGradingHarnessTest.aZeroToolRoleCreatesNoWorktree`). Commit a1dc6827. |
| D5 | not met as written | 81, 44(b), 85; 44(a)(c)(d)(e) and 50 open. Mutation-after `8bf2097307e0477c91995956d2f784a8`. Commit c2a8220a. |
| Per-head test runs | met | `test -pl pm-core` 5647 green (67 s); `test -pl pm-runtime` 2756 green (794 s) at `4f121caa`; the D4 head's targeted runs 20 and 13 green. |

## Personas and skills

`persona-implementer` (production), `persona-module-tester` (tests), `persona-documenter` (docs),
`persona-code-reviewer` (self-review). Spec skills: none declared per deliverable; the First Instruction's
`java-core`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing` were read. `skills added: none`.

## Hypotheses

HYPOTHESIS (42/55/19 anchors moved): refuted for the lines this run edited — re-read at `origin/main` (`49bc807a`).
OBSERVED (56(a) no production caller): confirmed, and now wired.

## Findings

Self-review round 1 (sub-agent): 12 findings, 8 fixed (5 assertion-message violations, the pre-commit rewrites,
six false statements, the spacing release at the end path), 4 open (the spacing wait under the supervisor monitor,
the pre-existing supervisor→guard lock order, `answerDelivered` naming no task, the repeated WARN on unreadable task
facts). `self-review: budget-exhausted`.

## Reviewers

- coderabbit (required, quota-limited): triggered on `4f121caa` and again on `a1dc6827`; both reads show
  `NEUTRAL skipping`. No qualifying review exists → not mergeable.
- sourcery (optional): `SKIPPED`.
- CI: the checks read reports only the two bots (`mechanism: seed_only`), so no build check is readable for either head.

## Operator questions

1. startFailed caller → **In WorkerSupervisor**.
2. never-connected workers → **Refused launches count**.
3. ProbeBudget seam → **Add the clock seam** (implemented as a budget override; recorded as a defect).
4. HarnessStartSpacing owner → **Wire it in WorkerSupervisor**.
5. "The run reached its self-review round limit with PR #567 open and partial… how should it proceed?" →
   **Continue the remaining scope**.

## Rule check

executor-skew `match`; `spec_source: ledger_worktree`, no `spec_differ`; guard none (unenforced, so no guard log).
Rule breaks this run: 3 shell file operations in the main checkout early (one `wc -l`, one `ls` of two directories,
one `grep` of a job log). Builds are listed below.

## Build server

Setup preflight `ready`; `build-routing` `auto`. Mechanisms: one `daemon_longpoll` (bound expiry 647 s), the rest
`in_process_fallback` after the bound expiries. No `killed` job. Builds: routed `verify` (647 s timeout);
in-process `verify -Ppre-commit` (learned 431 s timeout, 9400 passed); `test -pl pm-core,pm-runtime` (750 s timeout);
`test -pl pm-runtime` (352 s timeout, then 794 s green); `test -pl pm-core` green; targeted runs of every changed test
class.

## Residue

Item 30; 41(a)(b)(d)(f)(g); 42(a)(c)(d)(f)(h); 44(a)(c)(d)(e); 48(b)(c-release)(d)(e); 50(a)(c); 55(a)(d)(e)(f); 58;
61; 75(b)-(f). The full `verify` did not complete locally inside the host bounds. The PR is open and unmerged; no
qualifying review and the spec's Done-when is not met.
