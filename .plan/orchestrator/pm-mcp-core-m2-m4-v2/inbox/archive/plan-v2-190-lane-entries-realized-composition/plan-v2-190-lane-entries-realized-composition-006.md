envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:16:05Z

# Candidate lesson: renamed test reported as vanished (LaneEntryIdsTest)

- Source: Q-Gate 6-finalize, functional-review, hash 8d9476, resolution accepted (operator, 2026-11-27)
- File: pm-workflow/src/test/java/de/planmarshall/workflow/verify/LaneEntryIdsTest.java
- Finding: 3 assertions gone from entriesAreStatesOfTheDefaultPlanUnit - the test was renamed to entriesAreStatesOfTheDefaultUnits and extended, but the detector reported it as a vanished test name.
- Disposition: accepted.
- Observation: the weakened_test detector keys on test method names, so a rename plus extension reads as a removal. A false positive class worth noting for the detector (rename tracking) or for plan authors (keep the method name when extending a test).
