envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:16:56Z

# Candidate lesson: an unset rule on the masked realized_footprint can prune before realization

- Source: automatic review, PR #645, CodeRabbit inline comment, hash f01e00, resolution taken_into_account
- File: pm-workflow/src/main/java/de/planmarshall/workflow/plan/PlanInstanceComposer.java:436
- Finding: when a composition event withholds realized_footprint, Unset treats the absent fact as unset and holds; CompositionRuleEvaluator.evaluate applies every rule whose condition holds with no event check, so a rule {"fact":"realized_footprint","unset":true} could prune at START, outline, plan or replan. The masking that makes disjoint indeterminate does not stop this rule.
- Disposition: noted, not changed - Unset holding on an unknown fact is the documented contract (used by the shipped integrations.sonar rule) and no shipped rule tests unset on realized_footprint. Changing it is a specification question.
- Follow-up candidate: a specification decision whether a masked fact counts as unset or unavailable, plus a test that an unset rule on realized_footprint does not prune at START.
