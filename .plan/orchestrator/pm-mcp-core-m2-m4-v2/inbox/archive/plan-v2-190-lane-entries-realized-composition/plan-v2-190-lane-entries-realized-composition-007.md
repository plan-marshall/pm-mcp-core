envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:16:09Z

# Candidate lesson: outline missed a positional-constructor consumer (CompositionRequestRecordsTest)

- Source: Q-Gate 3-outline, consumer_sweep_completeness, hash d20ac6, resolution taken_into_account
- File: pm-workflow/src/test/java/de/planmarshall/workflow/plan/CompositionRequestRecordsTest.java
- Finding: deliverable 2 removes the record component CompositionRequest.realizedFootprintDigest, but the outline's Affected files did not list this test, which calls the canonical constructor positionally with 14 arguments. It would stop compiling.
- Fix: added to deliverable 2 Affected files (drop the argument).
- Observation: one of six consumer-sweep misses in this outline, all of the same shape - removing a record component breaks every positional `new Record(...)` call, and the outline sweep did not enumerate test-side constructor call sites. Sweeping for `new <Record>(` across src/test when a record component is removed would have caught all six up front.
