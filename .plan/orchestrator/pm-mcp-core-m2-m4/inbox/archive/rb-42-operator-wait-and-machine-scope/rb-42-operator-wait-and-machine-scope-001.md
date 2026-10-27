envelope_version=1
sender_type=plan
sender_id=rb-42-operator-wait-and-machine-scope
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-27T12:51:20Z

```landing-facts
schema=landing-facts/1
plan_id=rb-42-operator-wait-and-machine-scope
epic=pm-mcp-core-m2-m4
pr=#199
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2017
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

# PLAN-42 operator-wait-and-machine-scope — landing

Standing rules: r1, R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18.
Spec read from the ledger worktree (`spec_source: ledger_worktree`), `spec_differ: true` — the main-checkout copy was
older by the PLAN-34/PLAN-66 note, the ADR numbers and the runbook adaptation; the ledger copy was used.

## 1. Deliverables

**D1 — Workflow DSL § Waiting for the Operator and § Machine Scope — met.** Code: `AwaitedEvent.OPERATOR_ACT`,
`EngineEvent.OperatorAct`, `WorkflowEngine` routing, Rule 10 `OPERATOR_LINKS_APART`, parser/printer/AST question shape.
Docs: companion PR #203. Evidence order `none` (green at HEAD; no failing output clause).

**D2 — The operator wait — met.** `OperatorWaitTest.restsWithoutWork` (empty `active_job`, `active_jobs`,
`active_tasks`, no task request; `TimeElapsed` refused; status unchanged), `otherPlansRunOn` (another plan ends
`merged`), `operatorParametersReachTheBinder`; `WaiverDeclineTest.declinedLeavesTheWaiverWait` and
`declinedRequestRoundTrips`; `AdditionalRulesTest` Rule 10 cases. Order `none`. Commit `e21e1788`, fixes `46905b1b`,
`9cb1aef0`, `24a15865`, `53408379`, `7ad7e133`, `ac2566d2`.

**D3 — The setup gate — met at library level.** `SetupGateTest` (9 tests: `machine_not_onboarded` for the three starts,
missing step and both clients, 409, open onboarding/operator verbs, complete and reconfigure, done step, unknown step,
absent list). The callers (`start-from-clients`, `project-onboarding-unit`) are unbuilt packages and listed as owed.
Order `none`.

**D4 — The machine scope — not fully met.** Met: `JournalScopeType.MACHINE`, `JournalScope.machine`,
`LockKey.machineScope`, `machine/mcp/status.json`, phase `machine`, the machine arms of the stores, `onboard_run` /
`onboard_steps` in the codec, `MachineScopeStoreTest`, the resident graph check (`MachineResidentUnitTest`). **Not met:**
the resident unit `onboarding.pmwf` compiled into the binary — this repository carries no model-facing content below
`workflows/` (CLAUDE.md); the unit is owed to `plan-marshall/plan-marshall-mcp`. The graph check is proven on a written
fixture unit. Also open: the machine's condition backoff/hold is the runtime's and was not in this package's code.

**D5 — Removed deadlines and fallbacks — met.** `QuestionClassRegistry.defaults()` has the specification's outcomes only,
`QuestionEvent` has no expiry, `EngineEvent.QuestionResolved` no longer names a deadline, the waiver wait has no
`expired`; `WaiverDeclineTest` covers the `declined` route. Order `none`.

**D6 — Schema parity — met.** `WorkflowStatusSchemaParityTest.phasesEqualSchema` and `scopeTypesEqualSchema` run without
`@Disabled`; 2 run, 0 skipped. Order `none`.

### Builds (head `e5a52e13` for the code PR and `b471a202` for the docs PR)

- `verify -Ppre-commit` green 7903; `verify` green 7903
- `verify -Pcoverage -pl pm-core -am` green 3581; `verify -Pcoverage -pl pm-workflow -am` green 5729
  (the full `verify -Pcoverage` reactor exceeded the host's 1105 s bound, so the per-module runs are reported)
- `verify -Pintegration-tests -pl pm-core -am` green 13
- CI green on `e5a52e13` and `b471a202`; SonarCloud PR #199 and #203 each quality gate passed, 0 open issues.

### Bookkeeping

Package file and index row removed, names dropped from every `Depends on` cell (docs PR #203, merged as `7383e045`). The
R17 links check reports **BROKEN LINKS 10**, all pre-existing sibling-documentation anchors on our
`doc/implementation-watch/` (`PM-WATCH-CLIENT-13`, `PM-WATCH-DISP-33/43/48/64/65`), none in this diff. The Done-when's
`BROKEN LINKS 0` is therefore not met as written.

## 2. Personas and skills

The spec declares no `Skills:` or `Persona:` lines: **skills: none named**. The engineering personas were applied as
standards per step: `persona-implementer` (production code), `persona-module-tester` (unit tests),
`persona-code-reviewer` (both self-review rounds and every PR comment), `persona-documenter` (docs and ADRs).

## 3. Hypotheses / claim labels

- OBSERVED claim 0 — **contradicted**: the package bullet 2 still names the shipped packages; the re-grounding's later
  reading was applied (Rule 10 and the shipped dependencies).
- OBSERVED claim 1 — **corroborated** in part and re-scoped: the index row was open and depended on nothing; PLAN-25
  shipped.
- OBSERVED claim 2 — **corroborated**: `VerificationRule.SESSION_DRIVEN_LINKS(10)` existed and was changed to
  `OPERATOR_LINKS_APART` (`VerificationRule.java:98` before the change).
- HYPOTHESIS — the machine store lands in `core.store` **confirmed**: `JournalScope.machine` and the store arms are in
  `de.planmarshall.core.store`.
- HYPOTHESIS — Expected Surface inferred from `Where` **confirmed for the kept surface**; `workflow/setup` holds the
  gate, `pm-core/src/main/java/de/planmarshall/core/store/` the machine store.

## 4. Findings

- Self-review round 1 (sub-agent, code-reviewer persona): F3 operator wait had no end path; F4 `decideStep`
  over-refusal; F13/F16 onboarding parsing; F14 machine terminal; F7 machine stores untested; F6 waiver decline
  representation; F9 doc drift. All fixed in `64ab0e89` and `6a39dd8d`.
- Self-review round 2: F3 residuals (payload), F4 under-refusal, ADR 013/engine doc now false, codec members, array
  leniency, machine terminal. All fixed in `6a39dd8d`, `9cb1aef0`, `46905b1b`.
- CodeRabbit on #199: 5 actionable findings — operator link parameters, link guard, reconfigure writer, setup-gate
  caller, binder silent discard — plus 2 nitpicks. Parameters and binder fixed; guard and gate caller answered as the
  offering layer's / unbuilt callers' work; reconfigure writer answered as the external onboarding unit; constants
  unified. All threads resolved.
- CodeRabbit on #203: 12 documentation findings (relative spec links, gate reason split, shipped role-qualification
  links, “not implemented” qualifier, scope/store lists, `OperatorAct` summary, skipped step, malformed list, ADR
  sentence). All fixed in `b471a202`; threads resolved.
- Sourcery: refused the code PR at 150,000 diff characters (optional); reviewed the docs PR, no findings.

## 5. Reviewers

- **coderabbit** (required, quota-limited): reviewed `c0101cdd` and `53408379`; reviewed the docs PR `2810abdb`; on
  `7ad7e133`/`24a15865` it reported “already reviewed”/rate-limited; re-triggered after every push, refused on quota
  (one included review per hour). Standing authorization applied on #199 (qualifying review, every finding handled,
  re-trigger recorded, other gates green) and on #203 (qualifying review `2810abdb`, 12 findings fixed, re-trigger at
  `b471a202` rate-limited, CI and Sonar green). Both PRs merged through the merge queue.
- **sourcery** (optional): refused #199 on size; reviewed #203 with no findings.

## 6. Operator questions

1. Executor-skew `could_not_check` (executor 0.1.2017, lane version underivable) — options: continue as-is, regenerate,
   stop. **Selected: continue the run as-is.**
2. Required reviewer blocked by PR size (CodeRabbit 106 files > 100; Sourcery > 150k chars) — options: split docs into a
   second PR, merge without CodeRabbit, stop. **Selected: split docs into a second PR.**
No free-text operator messages were used as authorisation.

## 7. Rule check

- **guard: none on this harness (unenforced)** — no pre-tool-use guard was installed, so “all kept” is not claimed.
- Shell file operations issued by the run (read-class included): one `sed` extraction and one `truncate` to regenerate
  the AsciiDoc diagram golden from the test output, one `ls` and one `stat` for size checks, and `date` for timestamps.
  These are the only departures from the file-tool rule; everything else was Read/Grep/Glob/Edit/Write. No guard refusal
  occurred.
- Every script ran through `.plan/execute-script.py`; every build through the resolved Maven command; every CI action
  through `ci`. The routed-build attempt that lost `--project-dir` was replaced by explicit `in_process` builds; no
  source was changed to silence an error.
- Git: no `stash`, `restore`, `checkout --`, `rebase`, forced push, or `branch -D`. The code branch was merged (twice)
  with `origin/main` and the `Co-Authored-By` trailer. The main checkout was moved only by `switch-and-pull`.
- `.plan/` writes: `progress.md`, the landing file, the `ci` body slots and the `docs.patch`/`revert` scratch files under
  `.plan/temp/`; the spec and `.plan/orchestrator/` were never edited by the run (the merge commits carry main's own
  ledger updates).
- Restart: the server restarted once during a CI wait; the lane text was re-read at `2026-10-27T10:16:52+01:00` and the
  run resumed at head `24a15865`, re-running the CI wait. No sub-agent was killed.
- Spec read: `spec_source: ledger_worktree`, `spec_differ: true`.

## 8. Build server

- Setup preflight: **ready** (`registered: true`).
- Every build ran with `--execution-mode in_process` after the first routed attempt (`auto`) re-ran the inner command
  without `--project-dir` and built the main checkout; results report `mechanism: in_process_fallback`. No build was
  killed, and no routed build was backgrounded.
- Runtime stamp: `model` deepseek-v4.1-flash/high, `effort` high, `harness` opencode, `plugin_version` unknown,
  `executor_version` 0.1.2017. No model switch during the run.

## 9. Residue

- D4's compiled resident unit `onboarding.pmwf` (and its aspect sub-workflows) owes to `plan-marshall/plan-marshall-mcp`;
  the machine's condition backoff/hold is runtime work.
- D1/D3's production callers (`start-from-clients`, `project-onboarding-unit`) are unbuilt; the gate is the library entry
  they call.
- Rule 10's link guard is evaluated by the offering layer that owns the scope's facts; the engine refuses an unknown
  link and a submission a binder cannot carry, and no facts port exists in this package.
- The operator-wait link parameters reach a parameter-aware binder; a caller that installs only the two-argument binder
  gets `UnsupportedOperationException` rather than a silent discard.
- R17 `BROKEN LINKS 10`: pre-existing sibling anchors, not in this diff.
- The docs were split into PR #203 at the operator's direction (R05 departure), merged as `7383e045`.
