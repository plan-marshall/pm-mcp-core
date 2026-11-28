envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:15:53Z

# Candidate lesson: weakened-test finding for removed rules (CompositionRuleEvaluatorTest)

- Source: Q-Gate 6-finalize, functional-review, hash 886d55, resolution accepted (operator, 2026-11-27)
- File: pm-workflow/src/test/java/de/planmarshall/workflow/plan/CompositionRuleEvaluatorTest.java
- Finding: 7 assertions gone from Families changeTypeExempt, codeChangeTypeControl, footprintDisjoint, footprintIndeterminateControl, footprintWithCodeControl (removed rules report-change-type and footprint_disjoint); realized_footprint no_code_delta cases added.
- Disposition: accepted - deliberate removal of two composition rules; replacement cases added.
