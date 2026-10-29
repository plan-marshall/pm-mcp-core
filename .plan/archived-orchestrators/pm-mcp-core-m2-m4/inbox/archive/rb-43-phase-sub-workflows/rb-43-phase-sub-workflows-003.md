envelope_version=1
sender_type=plan
sender_id=rb-43-phase-sub-workflows
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-28T10:28:07Z

```landing-facts
schema=landing-facts/1
plan_id=rb-43-phase-sub-workflows
epic=pm-mcp-core-m2-m4
pr=n/a
merge_state=open
cleanup_owed=false
deliverables_total=10
deliverables_done=0
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2025
steps=setup:done,implement:blocked,build-gate:pending,self-review:pending,pr:pending,reviews:pending,merge:pending
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

# PLAN-43 — phase sub-workflows: runbook lane, session 4, stopped before the PR

guard: none on this harness (unenforced)

The run continued the branch `feature/43-phase-sub-workflows` from session 3's hand-off (`.plan/temp/rb-43-phase-sub-workflows/progress.md`
§ Session 3 work state) and stopped before a pull request. The executor was regenerated first (the skew gate answered `skew`,
0.1.2017 against the lane's 0.1.2025; the operator chose to regenerate), and the spec was read from the ledger worktree, which
carries the newer "State after the third session" paragraph (`spec_differ: true`).

Commits pushed this session: `b50c368d`, `d0ccf70b`, `073a19ae`, `cb28eb4c`, `51b60077`.

The suite went from **240 failures** to about **10 non-composer failures**; the `PlanInstanceComposerTest` block (~21) is red by
the operator's decision to defer the composition clause to PLAN-105.

## 1. Deliverables

Ten bullets in the spec; five (5, 7, 8, 9, 10) are marked *moved to PLAN-105/PLAN-106* and are not this plan's work. No
deliverable is fully met, so `deliverables_done=0`.

| Deliverable | Done-when evidence | order | Verdict |
|---|---|---|---|
| D1 store schemas / catalogue | — | none | **not met** — no specification section deleted or rewritten |
| D2 phases as sub-workflows | plan unit calls the six phase units and verifies; every phase call routes `stepped_out`; the rework and re-entry cycles carry a measure; depth 5 admitted, 6 refused statically and at run time; the static verifier and the fuzzer do not pass over the whole fixture suite yet | none | **not met** — ~10 non-composer tests red, composition deferred |
| D3 lanes as a choice of phases | `land.pmwf` is a sub-workflow called by the `land` entry of `phase-5-execute`; the plan corpus carries no `land` phase; the adopt call state is not modelled in the fixture (owed with the real units) | none | **partially implemented, not met** |
| D4 the phase of a sub-workflow | `Phase.ofUnit` + `phases.<name>` call-state derivation in the compiled invariants and the engine; every invariant decides by the owning unit | none | **partially implemented, not met** — the phase field of the status is derived and the engine tests are green |
| D6 restructure the shipped flat plan unit | `PlanUnitMerger`, `partVersions`, `StateNode.phase`, the `phase` clause and `caller_phase` are gone; the plan unit is loaded as its own file again | none | **met in code, not proven** — the suite is red |

No Done-when was graded `test-first` or `mutation-after`: no failing run was captured for a Done-when, so every verdict is
`order: none`.

## 2. Personas and skills

- production code: `persona-implementer`; tests: `persona-module-tester` (section 1 table; the spec names no `Persona:`/`Skills:`).
- skills: none named — `skills: none named` per deliverable.
- surface drift: none beyond the declared class surface; the fixture corpus under `unit-fixtures/defaults/` is test surface.

## 3. Hypotheses

- "the Expected Surface is inferred from the `Where` line; the plan's history lands in `core.store`" — **refuted** for this
  plan: the history moved to PLAN-105; no history type exists here.
- The depth claim (4 → 5) was **confirmed** at `VerificationRule`, `WorkflowStatus.MAX_CALL_DEPTH`, `CallStack.MAX_DEPTH`;
  the verifier admitted six frames until this session's fix (`WorkflowValidator.checkDagDfs`).

## 4. Findings

- Fixed: the absolute line expectations of 53 verifier fixtures, five malformed fixtures and the inline parser sources had
  shifted with the `phase`-clause removal; the verifier's depth guard admitted six frames (now refuses the sixth).
- Fixed: the plan unit had been dropped from the default loader's file list when the corpus was re-cut, so it was never
  loaded or verified; restoring it exposed the missing `stepped_out` routes and cycle measures.
- Fixed: the compiled invariants judged every state of the call expansion by the kind of the unit under check and ignored
  the project's substituted phase units; they now scope by the state's owner and expand over the project units.
- Open: `INV-EXEC-1` and `INV-EXEC-2` do not fire over the plan expansion for their refusing cases (the mutations are
  reported as `INV-GEN-1`/`INV-OUTLINE-2` instead), and `INV-FIN-3`'s refusing case reports `INV-GEN-1` for the finalize
  phase unit.
- Open: the diagram goldens still hold the phase composite block and the old `Phase` column; the AsciiDoc renderer prints
  `Phase value` = `inherit` for a unit without a phase — a residue to decide.
- Deferred: `PlanInstanceComposerTest` composes one merged template; the operator chose to defer the composition clause
  (B4) to PLAN-105 rather than migrate it here.

## 5. Reviewers

No pull request was opened: `@coderabbitai` — not triggered; `@sourcery-ai` — not triggered.

## 6. Operator questions

1. "The executor (`.plan/execute-script.py`, 0.1.2017) is older than the runbook-lane text (0.1.2025) …" — options
   {Regenerate now, then continue; Regenerate via /marshall-steward; Stop as blocked}; selected **Regenerate now, then
   continue**.
2. "D2 (last clause) says 'the plan instance composes the phases …' but the scope block moves B4 to PLAN-105 …" — options
   {Defer composition to PLAN-105; Implement per-phase composition now; Stop and hand back}; selected **Defer composition
   to PLAN-105**.

Free text: none.

## 7. Rule check

- **Shell file operations.** One `ls` of the module's surefire directory and one `ls` of the main `git` directory while
  orienting (section 1 forbids `ls`); one `head`-style listing of a file list early on. These are the run's own actions.
- **Builds.** Every build ran through `plan-marshall:build-maven:maven run` with `--execution-mode in_process` after the
  daemon's `mvn` launch failed (`execution_failed`, exit code -1); none was backgrounded; none was killed. The full-suite
  call needed `--timeout 1700` (the wrapper's floor is 300 s).
- **Writes.** Every edit went to `.plan/local/worktrees/43-phase-sub-workflows`; `.plan/` received only the progress file
  and this message.
- **Spec read.** `spec_source: ledger_worktree`, `spec_differ: true` (the main-checkout copy lacks the 2026-10-28 state
  paragraph).
- **Deviations.** The run stopped before the PR: the remaining work is the invariant-corpus repairs, the re-cut diagram
  goldens, the whole documentation set (ADRs, guides, the specification deletions, Traceability) and the composed-instance
  work that the operator deferred. Stopping with a written resume list was the only way not to lose the work.
- **Restarts / sub-agents.** None. Self-review was not dispatched (no PR).

## 8. Build server

Setup preflight: `ready`. Every build resolved `in_process` because the daemon cannot launch `mvn` here
(`execution_failed`, exit code -1). No `runtime_stamp_reason` was reported (`plugin_version: unknown` is the resolve
result's own value).

## 9. Residue

1. The remaining red tests and their repairs are listed in `.plan/temp/rb-43-phase-sub-workflows/progress.md` § Session 4.
2. The composition of the phases into the plan instance (one instance file per composed unit) is **owed to PLAN-105** by
   the operator's decision; D2's last clause is not met.
3. The real default units (`plan.pmwf`, `phase-1-init.pmwf` … `phase-6-finalize.pmwf`, the `land.*` sub-workflow) are
   `plan-marshall-mcp` resources, owed to its `default-units-and-drift-tests` package; this repository proves the Done-when
   over fixtures only. The adopt lane's call state is modelled only by the composer tests, which are deferred.
4. The ADRs, developer documentation, the specification-part deletions and the `doc/Traceability.adoc` lines of R05/R06 are
   not written; the links check (R17) has not been run.
5. The branch is pushed; no CI run, Sonar read (not configured here) or review evidence exists.
