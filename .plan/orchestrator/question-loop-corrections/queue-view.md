<!-- GENERATED FILE — never hand-edit. Rendered from this epic's ledger (status.json, resume_anchor.md, queue/*.json) by `orchestrator regenerate-view --slug question-loop-corrections`. On a merge conflict in this file, do not merge it by hand: merge the source files, run `orchestrator regenerate-view --slug question-loop-corrections`, and `git add` the result. -->

# Queue view: Question-loop verification: corrections and follow-up

## START HERE

**Resume anchor**: Emitted 2026-10-27 in the runbook lane: PLAN-01 and PLAN-02 (file-disjoint, no overlap prompt open), awaiting operator-confirmed launch. NEXT: on the operator's word record launched for both with the queue --transition verb. PLAN-03 waits for the landing of PLAN-01 and PLAN-02 and the operator's run budget. pm-mcp-core-m2-m4 PLAN-42 and PLAN-73 are shipped; PLAN-73 landed only the library core, so re-check at the landing of PLAN-02 what of question-continuation remains.
**Phase**: orchestrating
**Queue** (staged, in order):
1. PLAN-01 (WS-01)
2. PLAN-02 (WS-02)
3. PLAN-03 (WS-02)

## Ordered Queue

| # | Plan | Workstream | Status | Surface (expected) | Lane |
|---|------|------------|--------|--------------------|------|
| 1 | PLAN-01 | WS-01 | staged | ../plan-marshall-documentation/doc/developer/question-loop-experiment.adoc; ../plan-marshall-documentation/doc/discussions/open-questions.adoc; ../plan-marshall-documentation/doc/specification/evaluation.adoc; doc/specification/client-profiles/01-profile-schema.adoc; doc/specification/client-profiles/02-placeholders.adoc; doc/specification/model-work/01-roles-and-configuration.adoc; doc/specification/model-work/06-envelopes-questions-deadlines.adoc | runbook |
| 2 | PLAN-02 | WS-02 | staged | ../plan-marshall-mcp/doc/plans/README.adoc; ../plan-marshall-mcp/doc/plans/question-loop-follow-up.adoc; doc/plans/; doc/plans/README.adoc; doc/plans/question-continuation.adoc | runbook |
| 3 | PLAN-03 | WS-02 | staged | ../plan-marshall-documentation/doc/specification/evaluation.adoc; ../plan-marshall-mcp/doc/plans/question-loop-follow-up.adoc; ../plan-marshall-mcp/pm-e2e/src/test/java/de/planmarshall/e2e/; doc/specification/client-profiles/01-profile-schema.adoc; doc/specification/model-work/01-roles-and-configuration.adoc | plan-marshall |
