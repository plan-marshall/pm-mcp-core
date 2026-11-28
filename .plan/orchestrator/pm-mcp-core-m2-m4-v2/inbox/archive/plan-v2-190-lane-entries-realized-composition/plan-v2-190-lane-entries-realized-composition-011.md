envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:16:33Z

# Candidate lesson: outline missed a positional-constructor consumer (PlanInstanceRecordsTest)

- Source: Q-Gate 3-outline, consumer_sweep_completeness, hash 73213b, resolution taken_into_account
- File: pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceRecordsTest.java
- Finding: deliverable 2 removes CompositionInputs.changeType, but the test calls new CompositionInputs(...) positionally and was not in Affected files.
- Fix: added to deliverable 2 Affected files (drop the changeType argument).
