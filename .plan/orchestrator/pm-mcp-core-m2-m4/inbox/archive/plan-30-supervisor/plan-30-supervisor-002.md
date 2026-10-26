envelope_version=1
sender_type=plan
sender_id=plan-30-supervisor
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-26T15:47:51Z

```landing-facts
schema=landing-facts/1
plan_id=plan-30-supervisor
epic=pm-mcp-core-m2-m4
pr=#190
merge_state=merged
cleanup_owed=false
deliverables_total=14
deliverables_done=14
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.1996
executor_version=0.1.1996
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:skipped,record-metrics:done,finalize-step-print-phase-breakdown:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

## Deliverables

14 of 14 met: 12 `met`, 2 `met_with_residuals` (D9 and D14). Every Done-when of the package
(and of the carried `PM-WATCH-CLIENT-13` item) is covered by a passing test or a verified artifact:

- D1 `WorkerState` + `WorkerStateCodec` + `JobRecordStore.writeWorker`, with the parity tests.
- D2 `WorkerParameters` (typed profile projection).
- D3 `VerificationRecord.tool` + `AuditEvent.workerStartFailed`.
- D4 the supervisor core and its generations, `ModelSlots` over the landed `ModelSlotScheduler`,
  `WorkerProtocol`, `WorkerCalls`, `WorkerTimers`, six `RuntimeLogMessages` records.
- D5 `LossDetection` (exit, ack, silence; zero_tool exempt).
- D6 `WorkerEnding`/`EndCause`/`WorkerDrainParticipant` (instructed, forced, fence, requeue, pause at
  once, drain).
- D7 `WorkerRecycling` (idle with the lease exclusion, token budget, tasks per generation, changed
  configuration, idle yields).
- D8 `StartFailureGuard`/`StartFailureState`/`StartFailureReport`/`HarnessStartSpacing`.
- D9 `agent_started`: enrolled-executable and `pm-operator` match by path and digest, fixed threshold
  1 and action `restart`, `libproc` downcall registered.
- D10 `ProfileWorkerEligibility` (the load-time stage and platform gate).
- D11 `ProbeWorkspace`, `DecoyServers`, `VerifyingBinaryCheck` and their control records.
- D12 `FixtureHarness` (a real child process), `VirtualClock`, the Done-When scenarios.
- D13 `runtime-worker.adoc` and nine ADRs.
- D14 specification/watch reduction, `doc/Traceability.adoc` for PM-WORK-3 / PM-ARCH-8 / PM-CLIENT-5,
  the plans index row and package file removed.

Test evidence: `verify -Ppre-commit` green (rewrites committed), `verify -pl pm-core -am` 3459 tests,
`verify -pl pm-runtime -am` 5374 tests at the final head; CI green on the merged head; SonarCloud gate
green with 0 open issues and 0 open code smells.

## Personas and skills

Production tasks ran under `persona-implementer`, module tests under `persona-module-tester`, the
documentation deliverables under `persona-documenter`, every review round and every PR-comment triage
under `persona-code-reviewer`. Domain skills were the plan's `java`, `java-cui` and `documentation`
sets; the spec names no per-deliverable `Skills:` line, so none was added by the spec itself.

## Hypotheses

All spec hypotheses were confirmed by the implementation: `runtime.worker` was greenfield; the
`RuntimeLogMessages` holder needed six new records at the next free identifiers; no HTTP call exists
in the package; the Expected Surface was confirmed with the additions recorded in the outline.

## Findings

- Functional review round 1: 6 blocking (D11–D14 not met, 3 false self-reports) — all fixed in four
  fix tasks; round 2 closed 14/14 with 7 advisories (2 out-of-repo residuals).
- Code review: 7 blocking across three rounds — zero_tool exit decision, off-track re-offer, declined
  probe backoff, enforced tool-set evidence, configuration-change guard clear, `START_FAILED` rebuild,
  throwing-start probe release — all fixed; final round 0 blocking, 12 advisories.
- SonarCloud: 2 blockers fixed (timer executor, reused `SecureRandom`), then 43 + 9 code smells fixed
  across three commits; final count 0.
- CodeRabbit: 24 findings — 22 fixed, 1 partial (tracker retirement needs the unwired supervision
  loop), 1 rejected (no production supervision loop exists in this slice); all 14 open threads
  resolved.

## Reviewers

`coderabbit` reviewed heads 4485d54 and 47d2bd6 (both COMMENTED, marker `final_review_risk_coverage`
on 47d2bd6). Re-triggered twice after the later pushes; the last trigger was answered with a rate
limit, so the current head carries the standing-authorization conditions: a qualifying review exists,
every finding is handled, the re-trigger was posted and refused on quota, and every other gate is
green. `sourcery` (optional) refused for diff size (150 000 characters) — recorded, not a gate.

## Operator questions

1. "How should this run proceed?" (PLAN-30 dependencies) — options {stop as blocked, base on the
   PLAN-28 branch, dependency-free carry}; selected "hold until PLAN-29 lands" (after #28 merged).
2. "The outline seam for PLAN-29" — selected "Hold the plan until PLAN-29 lands"; on the re-run,
   "Keep the ports and build now" was not re-asked; the landed launch package was used.
3. "Bring the branch up to date?" (pre-rebase gate) — selected "Yes, rebase".
4. "43 Sonar code smells remain" — selected "Clear the 43 now".
5. Free text: "Fix them of course if sensible" — cleared the last nine smells.
6. "Merge PR #190?" — selected "Merge now".

## Rule check

Standing rules r1: R01 package worked as the index says (file and row removed in the PR); R02 Java 25;
R03 no HTTP call was added; R04 no HTTP test; R05 developer docs and ADRs in the module, traceability
lines added, the sibling-documentation correction PR owed; R06 module documentation in
`pm-runtime/doc/developer/runtime-worker.adoc` and nine ADRs; R07 the one open design choice was asked
at refine; R08 only this package's row and Traceability sections touched; R09 no new confinement
exception; R10 no specification block read by a test was deleted; R11 ADR numbers are 001–009 in a new
directory; R12 six records at free identifiers with `doc/LogMessages.adoc` rows; R13 format version 1
untouched; R14 durable-protocol checklist applied to the state-write path (queue lock, complete
terminal record); R16 FFM descriptor registered in the module's reachability metadata; R18 Sonar
cleared to 0 open issues before the merge was reported ready.

Guard: none on this harness (unenforced); the run's own shell actions were kept to the one-command
rule and the executor form throughout.

## Build server

Setup preflight `ready`; every build routed to `marshalld` and settled green except one external kill:
`verify -Ppre-commit` on 019824ea was killed (`exit_code -9`) after 904 s with no verdict. It was not
blind-retried; the cause was established as an external kill (the epic records two earlier OOM kills
of the same gate on this host) and the module-scoped `verify -pl pm-runtime -am` was run instead, with
the full pre-commit gate later green twice on the same tree.

## Residue

- `pm-mcp-server` in `plan-marshall-mcp` must wire `WorkerProtocol` over `TaskProtocol` and
  `WorkerCalls` into the transport (ADR `runtime-worker/009`).
- Five links in `plan-marshall-documentation` still name the deleted watch anchors; the R05 correction
  PR there is owed and merged by the operator.
- The code review's 12 advisory findings (documentation accuracy and small code comments) remain
  recorded in `work/code-review-findings.json`; none blocks.
- The plan retrospective was not run (recorded `skipped`); the plan's metrics were recorded.
- `agent_started`'s production supervision loop is not wired in this repository (rejected CodeRabbit
  finding 5, recorded above and in the trace).
