envelope_version=1
sender_type=plan
sender_id=plan-69-unit-frontmatter-and-types
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T08:26:14Z

# Candidate lesson: printer copied the reader's plain-scalar rule instead of calling it

Source: review-bot finding dd0203 (coderabbitai, PR #91, Minor, resolved fixed by TASK-17, commit 0f229d4)
File: pm-workflow/src/main/java/de/planmarshall/workflow/dsl/WorkflowPrinter.java:64

## What happened

`WorkflowPrinter.PLAIN_INDICATORS` repeated `UnitFrontmatterReader.INDICATORS`
character for character, and `scalar` repeated the other plain-value rules of
the reader (no leading/trailing blank, no `": "`, no `" #"`). A change to one copy
would let the printer write plain values the reader refuses and break the round
trip.

## Resolution in run

`UnitFrontmatterReader` got a package-private `isPlain(String)` as the one
definition of the plain-scalar rule; `WorkflowPrinter` calls it.

## Candidate rule

When a printer and a reader in the same package must agree on a lexical rule,
the printer calls the reader's predicate; never re-type the character set. (Same
family as the hardcoded-mirror rule: a list that must mirror a set defined
elsewhere is a defect unless derived from it.)
