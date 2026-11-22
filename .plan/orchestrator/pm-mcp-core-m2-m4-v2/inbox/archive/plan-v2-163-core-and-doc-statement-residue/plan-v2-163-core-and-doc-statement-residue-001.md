envelope_version=1
sender_type=plan
sender_id=plan-v2-163-core-and-doc-statement-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-22T13:10:29Z

## What landed

plan-v2-163-core-and-doc-statement-residue shipped as six merged pull requests (#553, #554, #555, #556, #557, #559) after the single PR #550 (189 files) was closed: the required reviewer CodeRabbit refuses diffs over 100 files.

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-163-core-and-doc-statement-residue
epic=pm-mcp-core-m2-m4-v2
pr=#553,#554,#555,#556,#557,#559
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=34475660
total_wall_seconds=96735
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2173
executor_version=0.1.2173
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:skipped,sonar-roundtrip:skipped,branch-cleanup:done,finalize-step-preference-emitter:skipped,plan-marshall:plan-retrospective:skipped,record-metrics:done,finalize-step-print-phase-breakdown:skipped,emit-landing:done,archive-plan:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
```

## Residue

- The finalize steps `automatic-review`, `sonar-roundtrip` and `branch-cleanup` were written for the closed #550 and were closed out by hand on the operator's word ("continue" after the report). Review and Sonar ran per split PR: each gate was green, and #559 had no open Sonar issue. The review and Sonar counts above are `unknown` because no finalize step recorded them for the split PRs.
- The PR titles still read "x/6" (#555 reads "3/5"); the split was 5 parts plus the follow-up #559. Part 5 (`pm-runtime`, `pm-provider-git`) was folded into #555 because `pm-runtime` does not compile without part 3's `pm-core` API changes.
- Carried forward for the assembly: `ProcessTreeTracker(long root)`, `TestCounts.Measured.tailOnly`, six accepted weakened-test removals, and the ADR 026 amendment. All are in the PR bodies.
- The links check (`trace.py`, R17) was never run: it could not run from the nested worktree. Run it from the main checkout before the docs are considered done.
