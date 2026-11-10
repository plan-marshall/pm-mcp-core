# PLAN-05: Fix the defects of the question-loop measurement code before PLAN-04 runs on it

epic: question-loop-corrections
workstream: WS-02

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-05-fix-question-loop-measurement-code.md` and is queued as one row file, `queue/PLAN-05.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Fix the defects that the landing analysis of PLAN-03 (2026-11-10) found by reading in the question-loop measurement
code that `plan-marshall-mcp` #114 put into `pm-e2e/src/test/java/de/planmarshall/e2e/question/`, before PLAN-04 runs
that code on OpenCode and Antigravity with up to 12 workers at once. The fixes make the run records safe under parallel
runs, make a missing worker output a stated absence instead of a pass, make the fresh half of Q3 able to fail, end every
paid worker on any exit of the measuring JVM, and require the harness selection to be named. Staged on the operator's
word of 2026-11-10. The plan runs in `plan-marshall-mcp`; it runs no measurement and spends no harness budget.

## Deliverables

Each deliverable comes with a deterministic test in the normal `test` phase of `pm-e2e` that fails on the code as merged
in #114 and passes after the fix; no test starts a harness.

1. **Run records under parallel runs.** `QuestionResults.recordRun` / `append` (`QuestionResults.java:245`, `:260–264`)
   append to `runs.jsonl` without a lock while up to 12 runs record at once (`QuestionLoopMeasurement.java:1181`). The
   append is serialised (as `QuestionStub.append` already is under its `eventLock`), so no two records interleave in one
   line. Check at outline whether the helper threads that write into the run record (`idleWarmWorker`, `serveVotes`,
   `answerConsultations`, about `QuestionLoopMeasurement.java:1804–1881`) mutate the same `ObjectNode` the main thread
   writes; if so, fix that too.
2. **A missing worker output is an absence, not an empty output.** `RunView.readJsonLines` returns an empty list for a
   missing file (`RunView.java:124–127`) and `evidenceMissing` checks only the stub log (`:196–202`), so a re-aggregation
   from the evidence store — which keeps no worker outputs, by the operator's decision of 2026-11-01 — passes Q2's
   no-host-tool and no-prose-question clauses and the R1/R2 re-read clause on no evidence. Such a clause reads as not
   re-derivable (a stated state of the item, with the count it rests on), never as a pass; a live run is unchanged.
3. **Q3's fresh half can fail.** A fresh asker's stop is any `worker_exited` of its generation after the ask
   (`QuestionResults.java:859–870`), and the driver writes that event for every exit it causes itself
   (`QuestionDriver.java` teardown on superseding launch and close), so a fresh asker that never ends its turn counts as
   stopped. Only an end of turn the worker made itself counts as its stop. The stored Q3 invocation
   (`Q3-2026-10-31T182529318934Z-b9aed54b`) is then judged again by re-aggregation, and the result is stated in the
   package's decision record and the Q3 row of `evaluation.adoc`; whether that re-judgement is published as a changed
   verdict is the operator's decision at refine, because the package says the criteria are not changed after the runs
   (one recorded exception exists, Q6 on 2026-11-02).
4. **Every worker ends with the measuring JVM.** `QuestionDriver.terminate` kills descendants only when the launched
   process outlives the 10 s grace (`QuestionDriver.java:675–689`), so the child of a parent that exits promptly (OpenCode
   `run --standalone`) keeps running; the only shutdown hook continues stopped trees (`:699`), so running paid workers
   survive SIGINT or SIGTERM to the JVM. Descendants are ended with their parent, and the shutdown hook ends every live
   worker. A run that throws any other `RuntimeException` (`QuestionLoopMeasurement.java:1165–1176` catches only
   `InterruptedException`, `SetupFailure`, `IOException` and `IllegalStateException`) still has its run record written
   and the result of `measure()` still written.
5. **The harnesses of a gated run are named.** `pm.experiment.hosts` defaults to `claude,opencode,antigravity`
   (`QuestionLoopMeasurement.java:225`), so a gated run without it launches OpenCode on the operator's global data home and
   Antigravity with all tools. A gated run without the property is refused with a message naming it; the developer
   document and the package's run command say so.

**Done when**: each of the five deliverables has a test that fails on #114's code and passes on the fix, `verify`,
`verify -Ppre-commit` and `verify -Pcoverage` of `plan-marshall-mcp` are green with the measurement gate off (the
`pm-e2e` coverage bar as the package decided on 2026-10-31), `MeasurementGateTest` still passes, the developer document
`question-loop-measurement.adoc` describes the changed behaviour, and the open-finding read of Sonar at the head is
stated (`unknown` for a failed read, never `0`).

## Standing Constraints

- Test-scope code of `pm-e2e` only, under `plan-marshall-mcp`'s `CLAUDE.md`; no production module changes, no new
  dependency without the operator's word.
- No harness is started and no budget is spent: every test runs on recorded or constructed stub logs and run records.
  The stored evidence on the branch `question-loop-follow-up` of `cuioss/plan-marshall-telemetry` is read, never
  rewritten; a re-aggregation result is written to a new place, never over a stored one.
- The criteria of the items are not changed, except as the operator decides under deliverable 3, recorded with its date
  and reason in the package's decision record.
- Commits end with `Co-Authored-By: plan-marshall <noreply@cuioss.de>`; no tool footer; no pull request merged without
  the operator's word.
- An open Sonar finding is fixed in a pushed commit, or dismissed only on the operator's selection.

## Claim Labels

- OBSERVED: the five defects are at the lines named in the deliverables on `plan-marshall-mcp` `main` (`3d01811`),
  re-read by the orchestrator on 2026-11-10 — `landings/PLAN-03.md` § Layer 3, L3-1 to L3-6.
- HYPOTHESIS: the helper threads of deliverable 1 write into the same run record the main thread writes (leaf reading,
  not re-read by the orchestrator) — confirm or refute at `QuestionLoopMeasurement.java` § `idleWarmWorker`,
  `serveVotes`, `answerConsultations` (verify-at-outline).
- HYPOTHESIS: a run record larger than one atomic write occurs in practice (Q6 and R4 records carry many events) —
  not needed for the fix, which serialises regardless; the test constructs one.
- Verify-first clause: before scoping, read the package file and the developer document at `main` of both
  repositories; if the operator's direct pull request for the package's four record gaps (see Dependencies) has landed,
  build on it.

## Expected Surface

- OBSERVED: `../plan-marshall-mcp/pm-e2e/src/test/java/de/planmarshall/e2e/question/` — the measurement code and its tests
- HYPOTHESIS: `../plan-marshall-mcp/doc/plans/question-loop-follow-up.adoc` — the run command (deliverable 5) and the decision record (deliverable 3) (verify-at-outline)
- HYPOTHESIS: `../plan-marshall-documentation/doc/developer/question-loop-measurement.adoc` — the changed behaviour (verify-at-outline)
- HYPOTHESIS: `../plan-marshall-documentation/doc/specification/evaluation.adoc` — the Q3 row, only if the operator publishes a re-judgement (verify-at-outline)

## Dependencies and Sequencing

- Depends on: PLAN-03 (landed 2026-11-05).
- Precedes: PLAN-04, which runs this code on two more harnesses; PLAN-04 waits for this plan.
- Overlaps with: PLAN-04 on every path above — sequenced, never concurrent. The operator's direct pull request for the
  package's four record gaps (2026-11-10) also touches `question-loop-follow-up.adoc`; whichever lands second rebases.
- Adjacent to: `client-profile-measured-values` (`pm-mcp-core`), which consumes the settled values and is untouched.

## Hand-Off Command

Run in the `plan-marshall-mcp` checkout (the code is that repository's), on `main`. The epic lives in `pm-mcp-core`, so
the pointer names the ledger copy of the spec from the sibling checkout:

```text
/plan-marshall task="implement ../pm-mcp-core/.plan/local/worktrees/_orchestrator/.plan/orchestrator/question-loop-corrections/plans/PLAN-05-fix-question-loop-measurement-code.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
This epic does not resolve in the store of `plan-marshall-mcp`, so this plan files no inbox message: it reports through
its PR and its final report, and the orchestrator analyzes the landing from those.
