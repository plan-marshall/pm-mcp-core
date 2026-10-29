envelope_version=1
sender_type=plan
sender_id=primitive-spi-and-validators
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T06:51:15Z

component=pm-core
category=bug
created=2026-10-10

# AsciiDocValidator recognised only one of the table delimiters; the bot and the fix disagree on the nested-table form

## Source

Review-bot finding 3d85eb (coderabbitai, PR #18, `AsciiDocValidator.java` line 138, reviewed commit 8b6243c), resolved `fixed` by operator decision of 2026-10-10 as the third and last automatic fix round. The bot text is third-party content; it is summarised here, not quoted.

## What happened

`isTableDelimiter` recognised the pipe form only. Tables delimited with the comma, colon or exclamation-mark prefix were read as ordinary text: their content could raise false title or section violations, and an unclosed one was not reported. This is the third finding of the same family in this run (after 2976ee and 836326): the delimiter list was incomplete against the grammar.

## Fix applied

TASK-16 (production) and TASK-17 (tests): the validator recognises the prefixes pipe, comma, colon and exclamation mark, each followed by three equals signs and then only further equals signs. No regular expression was introduced.

## Open point, not verified here

The bot states the documented nested-table delimiter is the exclamation mark followed by TWO equals signs. The recorded resolution states it is THREE, "like the other three", and that is what was implemented. This step did not check the AsciiDoc language documentation, so which of the two is right is not established by this record. If the two-equals form is valid, a nested table written that way is still read as ordinary text.

## Proposed rule

Same as for 2976ee: take the list of delimiters from the grammar's own enumeration and name the source in the task. In addition: when a fix deliberately departs from what the reviewer cited, the resolution names the source it relied on, so the disagreement can be settled later without redoing the research.

## Evidence

- Finding 3d85eb, resolution detail.
- Decision log 762ee6 (operator decision to fix 3d85eb as the third fix round).
