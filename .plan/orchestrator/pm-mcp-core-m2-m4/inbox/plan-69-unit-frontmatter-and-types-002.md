envelope_version=1
sender_type=plan
sender_id=plan-69-unit-frontmatter-and-types
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T08:25:57Z

# Candidate lesson: outline edited a spec read by a schema-parity test without declaring the test

Source: Q-Gate finding 62fc59/03cdb0 (phase 3-outline, resolved taken_into_account)
Component (suggested): pm-mcp-core:pm-core store schema / outline authoring

## What happened

`pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java`
reads the JSON schema block after `== Workflow State Store` in
`doc/specification/store-schemas/02-workflow-state-store.adoc` (its
`retryRulesEqualSchema` test is enabled). Deliverable 8 edited that file and
Deliverable 3 added `entry` to the codec, but neither listed the parity test,
not even as a read file.

## Resolution in run

The parity test became a read file of D3 and D8; D8 keeps the JSON schema block
unchanged (including the call_stack frame property `entry`) and removes only the
not-implemented marker and covered prose; success criteria were added in D3 and
D8.

## Candidate rule

An `.adoc` specification in this repository can be load-bearing test input.
Before an outline edits a spec under `doc/specification/`, search the test tree
for readers of that file (schema parity tests and similar) and list them as read
files, stating which blocks must stay byte-stable.
