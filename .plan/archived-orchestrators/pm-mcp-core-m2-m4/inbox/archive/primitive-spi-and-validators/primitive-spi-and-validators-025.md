envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-10T07:00:31Z

## What landed

primitive-spi-and-validators shipped as #18 (merged, squash commit abaf95d on main).

```landing-facts
schema=landing-facts/1
plan_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
pr=#18
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=5
total_tokens=8809647
total_wall_seconds=35521.0
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,plan-marshall:plan-retrospective:done,record-metrics:done,emit-landing:done,archive-plan:unknown
step.finalize-step-sync-baseline.action=noop
step.finalize-step-sync-baseline.upstream_commit_count=0
step.create-pr.pr_number=18
step.sonar-roundtrip.count_status=confirmed
step.sonar-roundtrip.new_code_issue_count=0
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_state=merged
step.branch-cleanup.cleanup_owed=false
step.record-metrics.total_tokens=8809647
step.record-metrics.total_wall_seconds=35521.0
step.record-metrics.any_phase_missing_end_time=false
```

## Residue

- **Deliverable 6 is not done.** The documentation pull request plan-marshall/plan-marshall-documentation #20 (branch feature/primitive-spi-and-validators, commits f0cb9bc and 013b6ab) is open and unmerged. It names pm-mcp-core #18. Its `links / links` check and its rendering were not looked at; PM-WATCH-TOOL-6, PM-WATCH-DISP-18 and PM-WATCH-STORE-1 were never read. It lists every `PrimitiveRegistrationException.Reason` constant in a table that nothing compares with the enum.
- **`total_tokens` is overstated.** The recorded 8809647 is the value `record-metrics` stored and is transcribed as recorded. The orchestrator's own sum of observed dispatch usage is 6131992 (phases 2-4 1430029, execute 1768275, finalize dispatches 2933688), before main-context attribution. Cause: `end-phase` added an explicit total to a finalize row that already held one; see the decision log entry of record-metrics.
- **`archive-plan` is written `unknown` in `steps`** because it runs after this message and its outcome cannot be read here.
- **Gates on the merged head.** `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` last ran locally on 3105d88, four fix commits before the merged head df4ea82. CI ran its integration-test job green and SonarCloud reported 99.2% new-code coverage on df4ea82; the package row in `doc/plans/README.adoc` was set to `done` before the three review fix rounds.
- **Reviewer change for this plan only.** `cuioss-review-bot` reviewed the first commit only (no push trigger) and was moved to `optional_bots` for this plan by operator decision; #18 merged on CodeRabbit's participation and Sourcery's approval.
- **Shared identifier block of WS-03 untouched.** The package added no log message: no record in `PmMcpLogMessages`, no change to `doc/LogMessages.adoc`.
- **Roadmap item is partial.** Not implemented: `OutlineValidator` rules that need the working tree, task derivation or build extensions; registry checks that need the closed gate list or the keyword list; binding of free parameters. `PM-WF-6` also names validators for `request.md` and pull request bodies; the code has none.
- **Open question.** CodeRabbit named `!==` as the nested-table delimiter; `!===` was implemented and `!==` is ordinary text. Not checked against the AsciiDoc documentation during the run.
- **Candidate lessons.** 24 `candidate-lesson` messages from this plan are in the inbox (001-018 from lessons-capture, 019-024 from the retrospective).
