envelope_version=1
sender_type=plan
sender_id=plan-70-plan-instance-alignment
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-19T18:15:41Z

component=pm-workflow
category=bug

# Javadoc promised IllegalStateException but recordComposed threw IllegalArgumentException for a bad predecessor header

Source: Q-Gate 6-finalize finding eb273a (code-review, `PlanInstanceStore.java:226`; fixed in correction commit 8c36987, resolved in run), plan plan-70-plan-instance-alignment.

`PlanInstanceStore.recordComposed` let the `IllegalArgumentException` of `HeaderJson.read` escape when the recorded predecessor header failed the schema, although its contract documented `IllegalStateException` for a predecessor that cannot be read. A later CodeRabbit comment (187d38) on the same block asked for the parser's refusal `Diagnostic` to be kept in the message too.

## Rule

When a method documents one exception type for "stored state is unreadable", wrap every lower-level parse/validation exception on that path into the documented type (keeping the cause), and add one test per unreadable-input branch (missing, refused parse, schema-failing header) asserting the documented type.
