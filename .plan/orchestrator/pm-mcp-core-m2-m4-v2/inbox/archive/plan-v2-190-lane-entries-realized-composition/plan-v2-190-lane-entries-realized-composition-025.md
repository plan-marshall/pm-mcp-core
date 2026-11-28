envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-28T12:25:22Z

## What landed

plan-v2-190-lane-entries-realized-composition shipped as #645 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
pr=#645
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=21986793
total_wall_seconds=66504.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2184
executor_version=0.1.2184
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
step.code-review.blocking_count=0
step.code-review.advisory_count=8
step.code-review.rules_cited=8
```

## Residue

- The first merge-queue enqueue of #645 was ejected: the merge-group CI run failed on `DerivedFactStoreConcurrencyTest.twoRefreshersOfOneKey` (JDK 27 build), a race in `StoreSnapshot.readIfPresent` that this plan did not touch. It was fixed separately in PR #650 (merged as 18dc06f); #645 was then re-enqueued and merged as cf7b34b.
- `doc/Traceability.adoc` was not updated by this plan although the outline's notes name it; the plan retrospective reports it as a shipped gap.
- The retrospective also reports five documentation or ADR mismatches shipped as advisory notes (`workflow-engine.adoc` does not list the `realized` event, `workflow-plan.adoc` still says rules revision 2, ADRs 071 and 008 are marked fully superseded although their replacements keep parts of them, `implementation-watch/store-schemas.adoc` still names a removed lane entry).
- `sonar_open_new_issues=0` is read from the Sonar quality-gate comment on the merged head (0 new issues, 0 security hotspots) and from `sonar-roundtrip` (0 new-code issues, confirmed), not from a dedicated merge-gate read. The three review counts are not recorded by any lifecycle step and are written `unknown`.
- coderabbit (required) refused the PR once on quota and was re-triggered by the operator's choice; sourcery (optional) refused on size (150000 diff characters cap, 2063 changed lines). Two coderabbit inline comments were accepted or taken into account (b4c4e8, f01e00); f01e00 is a specification question for a follow-up plan.
- `emit-landing` and `archive-plan` are not in `steps` above: their outcomes did not exist when this payload was written.
