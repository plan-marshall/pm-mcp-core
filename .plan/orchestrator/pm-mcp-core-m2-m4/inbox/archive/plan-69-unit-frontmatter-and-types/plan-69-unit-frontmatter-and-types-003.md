envelope_version=1
sender_type=plan
sender_id=plan-69-unit-frontmatter-and-types
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T08:26:06Z

# Candidate lesson: new frontmatter reader refused CRLF units

Source: review-bot finding 586c3f (coderabbitai, PR #91, Minor, resolved fixed by TASK-17, commit 0f229d4)
File: pm-workflow/src/main/java/de/planmarshall/workflow/dsl/UnitFrontmatterReader.java:124

## What happened

`UnitFrontmatterReader.read` split the source only on `\n`. A CRLF unit then
starts with `---\r` instead of `---` and is refused, although `WorkflowLexer`
already accepts `\r` as whitespace, so CRLF workflow bodies had been supported
before the frontmatter layer was added in front of the lexer.

## Resolution in run

The reader treats CRLF like LF, so a CRLF unit reads to the frontmatter of its
LF twin; a lone carriage return stays refused.

## Candidate rule

A new pre-parsing layer placed in front of an existing lexer must accept at
least the input the lexer accepts (line endings, whitespace). Add a CRLF twin
fixture for every new line-oriented reader.
