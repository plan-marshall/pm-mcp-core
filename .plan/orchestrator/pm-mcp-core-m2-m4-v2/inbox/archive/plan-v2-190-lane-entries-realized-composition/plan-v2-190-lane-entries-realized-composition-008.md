envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:16:15Z

# Candidate lesson: outline missed a positional-constructor consumer (InstanceHeaderRecordsTest)

- Source: Q-Gate 3-outline, consumer_sweep_completeness, hash 4800bb, resolution taken_into_account
- File: pm-workflow/src/test/java/de/planmarshall/workflow/ast/InstanceHeaderRecordsTest.java
- Finding: deliverable 2 removes InstanceInputs.changeType, but the test calls new InstanceInputs(...) positionally and was not in Affected files.
- Fix: added to deliverable 2 Affected files (drop the changeType argument).
- Observation: same shape as the other record-component consumer misses of this outline.
