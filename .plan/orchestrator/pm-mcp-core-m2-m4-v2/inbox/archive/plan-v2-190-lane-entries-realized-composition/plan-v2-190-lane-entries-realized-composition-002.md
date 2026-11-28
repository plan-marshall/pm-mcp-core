envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:15:42Z

# Candidate lesson: weakened-test finding for removed component (AstNodeModelTest)

- Source: Q-Gate 6-finalize, functional-review, hash 18ef70, resolution accepted (operator, 2026-11-27)
- File: pm-workflow/src/test/java/de/planmarshall/workflow/ast/AstNodeModelTest.java
- Finding: 1 assertion gone from AstNodeModelTest.AbsentComponents instanceInputsKeepNull (changeType component removed from InstanceInputs).
- Disposition: accepted - deliberate removal of the changeType component; covered by negative controls.
- Observation: same pattern as the other five weakened_test findings of this run - deleting a record component by design reads to the gate as lost coverage.
