envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:16:26Z

# Candidate lesson: outline missed a positional-constructor consumer (PlanInstanceHeaderRecordsTest)

- Source: Q-Gate 3-outline, consumer_sweep_completeness, hash ccd489, resolution taken_into_account
- File: pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceHeaderRecordsTest.java
- Finding: deliverable 2 removes CompositionInputs.changeType, but the test calls new CompositionInputs(...) positionally at 2 sites and was not in Affected files.
- Fix: added to deliverable 2 Affected files (drop the changeType argument at both calls).
