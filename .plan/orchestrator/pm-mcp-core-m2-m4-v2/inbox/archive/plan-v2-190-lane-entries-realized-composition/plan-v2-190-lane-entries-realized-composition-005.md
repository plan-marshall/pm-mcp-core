envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:16:00Z

# Candidate lesson: weakened-test finding for removed component (PlanInstanceHeaderTest)

- Source: Q-Gate 6-finalize, functional-review, hash c215b2, resolution accepted (operator, 2026-11-27)
- File: pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanInstanceHeaderTest.java
- Finding: 2 assertions gone from RoundTrip absentAndPresentMembersSurvive and Values absentComponentsAccepted (changeType component removed from the header inputs).
- Disposition: accepted - deliberate removal of the changeType component.
