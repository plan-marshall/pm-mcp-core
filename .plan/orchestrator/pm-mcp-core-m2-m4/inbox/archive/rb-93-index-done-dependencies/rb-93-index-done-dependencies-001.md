envelope_version=1
sender_type=plan
sender_id=rb-93-index-done-dependencies
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-22T05:43:34Z

```landing-facts
schema=landing-facts/1
plan_id=rb-93-index-done-dependencies
epic=pm-mcp-core-m2-m4
pr=#136
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
```

guard: none on this harness (unenforced)

## Deliverables

### D1 — the three done dependencies: met

- Edit: `doc/plans/README.adoc` (worktree `chore/93-index-done-dependencies`, commit `39c977d`). `profile-loader` cell now `nothing`; `job-confinement-and-environment` keeps only `profile-loader`; `runtime-control` keeps `supervisor`, `task-queue-and-protocol`, `machine-onboarding-unit` (the consent store) and the operator-answer clause.
- Done-when clauses 1 and 2 (no cell names a package `done` in its own index; every link into `pm-mcp-clients` and `plan-marshall-mcp` resolves): met — verified by `ci repo file read` at each repository's `main`: `pm-mcp-clients` (`9525dd4`) marks the three packages `done` with files under `doc/plans/implemented/`; `plan-marshall-mcp` (`2c63b25`) marks `question-loop-verification` `open` and its file exists (`1a39312`); the three dead `doc/plans/<name>.adoc` links are gone and the remaining three targets resolve.
- Test: none — documentation change. order: `none`; failing run: none captured.
- Commit: `39c977d`.

### D2 — every other cross-repository cell: not fully met

- Work: read both sibling indexes at `origin/main`; the only remaining cross-repository package cell is `plan-marshall-mcp` / `question-loop-verification`, `open` there, and its link already names `doc/plans/question-loop-verification.adoc`, so no edit was needed. No second commit.
- Test: none. order: `none`; failing run: none captured.
- Commit: `39c977d` (the D1 edit; D2 produced no diff).
- **Done-when clause 3 — `trace.py … reports BROKEN LINKS 0` — not met as written.** The command lives in the sibling `plan-marshall-documentation` checkout, outside the executor's reach and outside this repository's boundary. Asked with the question tool; the operator selected "Skip it, report clause not met". Per the selected option this leaves D2 not fully met.

### test-compile

No `test-compile` ran against any pushed head. There was one pushed head, `39c977d`. The repository's CI skipped the Maven build and integration jobs for this docs-only diff (`check-changes` success, `build`/`test` skipped, `build / conclusion` success), and the operator selected no local Maven run.

## Personas and skills

- Documentation work: `persona-documenter` — loaded only at the end of the run, after the edit (see Rule check); the persona shell carries no independent rules.
- Self-review: `persona-code-reviewer` in a sub-agent, one round, reporting only.
- D1 skills: none named. D2 skills: none named.
- Surface drift: none — the only file touched is the spec's Expected Surface `doc/plans/README.adoc`.

## Hypotheses

- HYPOTHESIS "no other cell of the index names a package that is `done` in its own repository" — **confirmed**. The only other cross-repository package cell is `doc/plans/README.adoc:93` (`plan-marshall-mcp` / `question-loop-verification`), and that row in `plan-marshall-mcp/doc/plans/README.adoc` (`2c63b25`) reads `open`.
- OBSERVED claims corroborated by the same reads: `pm-mcp-clients` `main` (`9525dd4`) lists `profile-documents`, `exec-read-denied-paths` and `installation-layout` `done` under `doc/plans/implemented/`; `pm-mcp-clients` `main` has no `doc/plans/profile-documents.adoc` (`ci repo file read` answered `path_absent`).
- Verify-first clause: the open-PR listing showed `#126` (PLAN-89, the same file) already merged as `c45a968`, and the worktree base contained it; `#135`, `#129` and `#71` do not touch `doc/plans/README.adoc`.

## Findings

- Self-review round 1: no defect findings. F1 (Done-when clause 3 unmet by operator decision) — carried as disclosure in the PR body and here. F2 (the sub-agent could not re-resolve the sibling repositories without leaving the repository root) — answered by the run's `ci repo file read` evidence above. F3 (commit subject names D1 and D2 although D2 produced no diff) — closed, the body explains the no-change conclusion. F4 (`profile-loader` becomes ready) — closed, that is the index's own readiness rule and the objective's intent.
- CI: green at `39c977d` (`build / conclusion`, `build / gate`, `build / config`, `build / check-changes`, `integration-tests / conclusion`, `dependency-review`, CodeRabbit check).
- PR comments: no review threads and no actionable findings. The only non-trigger comments are bot quota notices (CodeRabbit rate limit at `19:59:01Z`; Sourcery budget refusal) — handled as reviewer status, not as findings.

## Reviewers

- `coderabbit` (required) — **reviewed** the current head `39c977d`. Triggered `21:29:48Z` after the 90-minute quota wait (refusal `19:58:35Z`, next trigger not before `21:28:35Z`); the bot answered "Review triggered/Review finished"; summary at `23:00:41Z` reported "No actionable comments", merge risk minimal, coverage commit `39c977d`. Escalated once with `@coderabbitai full review` at `23:00:23Z`; full review finished `23:01:51Z` with no actionable comments. No threads to resolve.
- `sourcery` (optional) — **refused** on a hard quota (3 days and 2 hours), so no review; optional and not gating.

## Operator questions

1. "PLAN-93's Done-when requires `python3 ../plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …`. The runbook-lane rules forbid calling a script directly and forbid reading outside the repository root…" — options: run the exact command / skip it, report clause not met / you run it; **selected "Skip it, report clause not met"**.
2. "The installed `runbook_lane` script registers only `resolve`; the skill's `build-classify` verb … is absent — a surface/naming error that looks like a stale install … How do you want the build gate handled?" — options: run full maven verify / treat as docs-only, no Maven / stop; **selected "Treat as docs-only, no Maven"**.
3. "State of PR #136 at head 39c977d: … verify: PENDING … test-compile: PENDING …" — options: run full maven verify first / merge now with verify pending / do not merge; **selected "Merge now with verify pending"**.

Free-text operator messages: none.

## Rule check

guard: none on this harness (unenforced) — there is no guard log for this run and nothing was enforced by a tool-call guard.

Rules not fully kept:

- **Personas** — `persona-documenter` was not loaded before the documentation edit; it was loaded only at the end of the run, and it is a shell with no independent rules, so it changed nothing. The work was performed without its standard loaded.
- **Build gate** — `verify` was never run on the head, and no `test-compile` ran against the pushed head `39c977d`. This was the operator's explicit selection #2, and the merge proceeded on selection #3, which named the exact gap.
- **Done-when** — clause 3 (`trace.py … BROKEN LINKS 0`) is not met as written, by operator selection #1.

Every other section 1 rule was kept: no direct build wrapper or test binary was invoked, every script went through `.plan/execute-script.py`, CI went through `plan-marshall:tools-integration-ci:ci`, one command per shell call, files read with the file tools, no writes under `.plan/` outside `.plan/temp/` and the `ci` body slots, no edit to the spec or the orchestrator tree, and the branch was merged into `main` only through the merge queue.

## Residue

- The plan's Done-when clause requiring `trace.py` `BROKEN LINKS 0` remains unverified; the operator chose to skip it rather than call the sibling repository's script directly. Clauses 1 and 2 were verified by `ci repo file read` instead.
- `verify` and `test-compile` remain unrun for `39c977d`; CI's required checks were green, with the Maven build skipped by the repository's own docs-only path rule.
- The optional reviewer Sourcery produced no review (hard quota, 3 days 2 hours).
