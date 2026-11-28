envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:15:47Z

# Candidate lesson: weakened-test finding for removed component (WorkflowParserRefusalTest)

- Source: Q-Gate 6-finalize, functional-review, hash f2a1aa, resolution accepted (operator, 2026-11-27)
- File: pm-workflow/src/test/java/de/planmarshall/workflow/dsl/WorkflowParserRefusalTest.java
- Finding: 1 assertion gone from InstanceHeaderReading readsHeader (changeType null assertion); negative control changeTypeRefusedBySchemaCheck added.
- Disposition: accepted - the removed assertion pinned the deleted changeType component; the new negative control proves the refusal.
- Observation: the detector counts the removed assertion but not the added negative control that replaces it.
