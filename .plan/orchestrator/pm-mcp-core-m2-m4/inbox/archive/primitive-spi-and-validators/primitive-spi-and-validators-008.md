envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:51:12Z

component=pm-core
category=bug
created=2026-10-10

# AsciiDocValidator interpreted the content of a comment block

## Source

Review-bot finding 836326 (coderabbitai, PR #18, `AsciiDocValidator.java` line 57, reviewed commit 3853d09), resolved `fixed`. The bot text is third-party content; it is summarised here, not quoted.

## What happened

AsciiDoc does not interpret the content of a comment block. The validator did not know the comment delimiter, so a commented-out document title was reported as a second title or a title not in first place, and a commented-out listing delimiter was reported as an unclosed block. Valid documents were rejected.

This is the second finding on the same missing delimiter (see 2976ee, which reports it as an unrecognised block); this one adds that the block is verbatim, like listing, literal and passthrough.

## Fix applied

TASK-12 (production) and TASK-13 (tests) on the branch of PR #18.

## Proposed rule

For each delimited block the validator knows, the task states two properties, not one: that it is a block, and whether its content is verbatim. The tests carry, per verbatim block, a case with a title line and a delimiter line inside it.

## Evidence

- Finding 836326, resolution detail.
- Finding 2976ee.
