envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:15:36Z

# Candidate lesson: weakened-test finding for removed behaviour (ProtectedPathsTest)

- Source: Q-Gate 6-finalize, functional-review, hash 434263, resolution accepted (operator, 2026-11-27)
- File: pm-core/src/test/java/de/planmarshall/core/store/ProtectedPathsTest.java
- Finding: 7 assertions gone (declaredPathLiftsThatPathOnly, decomposedDeclaredPathLiftsComposedPath, differentlyCasedDeclarationLifts, singlePathDecisionAgrees) - the declared-lift cases of the removed declared_files protection lift.
- Disposition: accepted - the assertions pinned behaviour the plan deliberately deletes; negative controls cover the new behaviour.
- Observation: the weakened_test detector reports intentional removal of tested behaviour as an error-severity bug, which needed an operator decision to clear. A plan that removes behaviour by design could pre-declare the expected test removals so the gate does not stop on them.
