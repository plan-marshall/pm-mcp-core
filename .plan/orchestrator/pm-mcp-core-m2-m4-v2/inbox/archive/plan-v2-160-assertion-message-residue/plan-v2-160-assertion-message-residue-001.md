envelope_version=1
sender_type=plan
sender_id=plan-v2-160-assertion-message-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-21T08:34:59Z

## What landed

plan-v2-160-assertion-message-residue shipped as #532, #533, #534 (merged). Three stacked PRs opened by hand outside the finalize pipeline (177 files against the 100-file PR limit).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-160-assertion-message-residue
epic=pm-mcp-core-m2-m4-v2
pr=#532,#533,#534
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=18767374
total_wall_seconds=66458
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2173
executor_version=0.1.2173
steps=finalize-step-sync-baseline:skipped,functional-review:skipped,code-review:skipped,architecture-refresh:skipped,pre-push-quality-gate:skipped,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:skipped,plan-marshall:plan-retrospective:skipped,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:done
unresolved_review_threads=unknown
review_body_findings_total=15
review_body_findings_handled=15
sonar_open_new_issues=0
```

## Residue

- Shipping: the finalize pipeline ships one PR per branch, so the work went out as three stacked PRs by hand (A #532 scanner and pm-core, B #533 pm-runtime, C #534 pm-workflow). Steps not run: sync-baseline, functional-review, code-review, architecture-refresh, pre-push-quality-gate, preference-emitter, retrospective. A full verify and the quality gate ran per branch.
- Review: CodeRabbit reviewed A only (15 comments: 13 fixed with failing-first controls, 2 declined with a reason on the thread). B and C got no bot review (CodeRabbit rate-limited, Sourcery refused C for its size). CI and Sonar (0 open issues) were green on all three.
- Follow-ups from the declined CodeRabbit comments: (1) derive the assertion names the scanner measures from the resolved JUnit API instead of a fixed list; (2) the helper message in `FindingTest`. `unresolved_review_threads` is `unknown` because the thread state was not re-read after the replies.
- Item 32(e) of the spec: counting from the PLAN-V2-105 merge diff (#451) gives 45 reworded messages (30 in the six packages, 15 in config and corpus); that merge's message says 44. No file edited.
- Unmeasured assertion messages: pm-core 242 to 37, pm-runtime 238 to 11, pm-workflow 477 to 2 (the remainder are kept with a reason: ArchUnit reports, offender-list messages).
- Links check: run on A at sibling depth, 3 broken links in files the PRs do not touch; not rerun for B and C.
- Overlap: plan-v2-158 and plan-v2-19 touch some of the same test files.
- Extra merge commits on the stacked branches (squashed away on main): a force-push of rebased B and C was denied, so main was merged forward instead.
