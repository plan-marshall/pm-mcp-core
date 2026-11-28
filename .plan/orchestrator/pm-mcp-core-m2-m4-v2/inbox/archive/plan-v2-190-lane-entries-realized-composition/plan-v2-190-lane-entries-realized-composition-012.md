envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:16:38Z

# Candidate lesson: outline missed a positional-constructor consumer that also rests on a rewritten fixture (PlanProcessStatesTest)

- Source: Q-Gate 3-outline, consumer_sweep_completeness, hash 8af8b3, resolution taken_into_account
- File: pm-workflow/src/test/java/de/planmarshall/workflow/plan/PlanProcessStatesTest.java
- Finding: deliverable 2 removes CompositionInputs.changeType, but the test calls new CompositionInputs(...) positionally; it also composes over the per-lane fixture deliverable 1 rewrites (outline.light / outline.deep positions). Not in Affected files.
- Fix: added to deliverable 2 Affected files (drop the argument); deliverable 1 keeps the fixture states the test rests on and lists the test as read.
- Observation: a consumer can be coupled to two deliverables at once (a record change and a fixture rewrite); the sweep should check both couplings.
