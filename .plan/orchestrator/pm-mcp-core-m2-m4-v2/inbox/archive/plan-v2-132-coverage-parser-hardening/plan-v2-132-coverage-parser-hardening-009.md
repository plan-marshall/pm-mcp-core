envelope_version=1
sender_type=plan
sender_id=plan-v2-132-coverage-parser-hardening
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-12T17:41:41Z

## What landed

plan-v2-132-coverage-parser-hardening shipped as #388 (merged via the merge queue, merge commit ce3f958e).

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-132-coverage-parser-hardening
epic=pm-mcp-core-m2-m4-v2
pr=#388
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=26603095
total_wall_seconds=36970.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2106
executor_version=0.1.2106
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.functional-review.deliverables_met=4
step.functional-review.deliverables_met_with_residuals=1
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=14
step.code-review.rules_cited=5
```

## Residue

- Review/Sonar measurements: the lifecycle producer records none of `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`. `sonar-roundtrip` confirmed 0 new-code issues on the merge head (unauthenticated HTTP, `pullRequest=388`) but does not read security hotspots, so `sonar_open_new_issues` is `unknown`, not 0. All bot comments were answered (CodeRabbit 3e7d73 fixed; 2c25d7 declined under the operator ruling; sourcery refused for quota, optional).
- `archive-plan` had not yet run when this landing was written; the `steps` list therefore ends at `emit-landing`.
- Operator rulings 2026-11-12 recorded in the plan: D2 case (e) option B (a reference in element content is refused; an undeclared one in an attribute value is dropped, never expanded or resolved; ADR core-build/008 Decision 4) — a Scope Deviation Accepted; three accepted test rewrites (`JacocoReportParserTest.refusesAnInternalSubsetBehindAComment`, `CoveragePercentagesTest.readsAnEmptyPopulationAsZero`, `CoveragePercentagesTest.refusesANonFiniteRate`); repeated counters refused in JaCoCo (root/per-class) and LCOV (per-record), fixed in this plan (TASK-10/11); Istanbul metric without `total` refused plus TOTAL constant for Sonar S1192 (TASK-12), LCOV `nextLine`/`tooLong` moved into `Walk` for Sonar S3398 (TASK-13).
- `CoverageXmlReaderTest.java` and `CoverageXmlHardeningTest.java` are edited by D4 but declared by D1/D2 (functional-review finding 567afa, taken into account).
- 14 code-review advisories and 5 functional-review advisories left unfixed (decision log), e.g. ADR 005 status note/Context wording, `package-info.java:48` still naming core-build/005 alone, some untested branches.
- The local link check (`trace.py`) ran with BROKEN LINKS 0.
- 8 `candidate-lesson` messages from the retrospective sit in the epic inbox (`plan-v2-132-coverage-parser-hardening-001…008`): repeated full review rounds after each HEAD advance, a `platform_runtime subagent dispatch` `prompt_not_found` error, hand-filed Sonar findings rejected by ingest because of an extra `raw_input.issue_key`, stale build daemon.
