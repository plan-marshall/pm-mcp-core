envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:51:09Z

component=pm-core
category=bug
created=2026-10-10

# AsciiDocValidator did not recognise the open block and the comment block as delimited blocks

## Source

Review-bot finding 2976ee (sourcery-ai, PR #18, `AsciiDocValidator.java` line 50, reviewed commit 3853d09), resolved `fixed`. The bot text is third-party content; it is summarised here, not quoted.

## What happened

The validator's rule is that every delimited block is closed. Its delimiter list held listing, literal, example, sidebar, quote, passthrough and table, but not the open block (exactly two hyphens) and not the comment block (four or more slashes). Content of those blocks was read as ordinary document text, and an unclosed one was not reported. No new rule was needed; the existing rule was applied to an incomplete list.

## Fix applied

TASK-12 (production) and TASK-13 (tests) on the branch of PR #18.

## Proposed rule

A validator that implements "every X is closed" derives its list of X from the grammar's own enumeration (here: the delimited-blocks table of the AsciiDoc language documentation) and the task names that source, so the list can be checked member for member. The same gap returned once more for table delimiters (finding 3d85eb).

## Evidence

- Finding 2976ee, resolution detail.
- Finding 836326 (same delimiter, verbatim handling) and 3d85eb (alternate table delimiters).
