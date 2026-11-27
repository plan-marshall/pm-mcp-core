envelope_version=1
sender_type=plan
sender_id=plan-v2-189-outline-record-scope
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T16:08:29Z

component=plan-marshall:phase-3-outline
category=improvement
confidence=high
source_aspects=request-result-alignment,chat-history-analysis

# Declare the tests a removal deliverable deletes so review accepts the lost assertions

## Context

plan-v2-189-outline-record-scope removed record members and finding sources. Its spec ordered the deletion of the test cases that pinned those members, but the outline's change-per-file text only said "delete the cases" and declared no tests-to-remove list. Functional review and code review both counted four tests (OutlineValidatorTest::duplicateNamesFirstLine, OutlineRecordBuilderContractTest::builderBuildsOutline, OutlineRecordTest::roundTripsAndRenders, OutlineSchemaParityTest::recordEnumerations) as unaccepted removed assertions and blocked; both closed only through operator override.

## Root cause

The removed-assertion rule accepts a lost assertion only when the outline names it as an intended removal; the deep-lane outline for a breaking removal does not prompt the author to enumerate the pinning tests it deletes.

## Proposed action

For deliverables with Strategy: breaking / removal, have phase-3-outline enumerate the test methods the deliverable deletes in the structured field the removed-assertion rule reads, so the reviews accept them without an operator prompt.

## Evidence

- status.json: functional-review and code-review closed "operator close: 4 finding(s) accepted"
- chat: "the outline did not list them as tests to remove, so the rule counts them as unaccepted"
