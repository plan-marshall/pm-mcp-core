envelope_version=1
sender_type=plan
sender_id=plan-69-unit-frontmatter-and-types
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T08:25:48Z

# Candidate lesson: outline split one refusal between parser and verifier

Source: Q-Gate finding 6a05f2 (phase 3-outline, severity error, resolved taken_into_account)
Component (suggested): pm-mcp-core:pm-workflow (WorkflowParser / WorkflowValidator), outline authoring

## What happened

Deliverable 2 made `WorkflowParser` refuse `start` beside declared entries
(STRUCTURE / DUPLICATE_ID), while Deliverable 5 added
`verify/refused/interface-start-with-entries.pmwf` as a VERIFIER refusal and
required every new `VerificationRule` member to have a refusing fixture
exercised by `WorkflowValidatorTest`. A fixture the parser already refuses never
reaches the verifier, so the D5 fixture and its success criterion could not
hold. The same split threatened "entry bound twice / undeclared binding"
(parser, D2) versus `interface-entry-unbound` / `interface-entry-state-missing`
(verifier, D5).

## Resolution in run

One owner per check: the parser refuses only `start` beside an entry line
(STRUCTURE) and a repeated binding (DUPLICATE_ID), with new `dsl/malformed`
fixtures; the verifier owns every rule-1 comparison with declared entries, and
each verifier fixture parses cleanly. D5 fixture count became 22.

## Candidate rule

When an outline adds a refusal for a workflow-DSL construct, name exactly one
layer (lexer/parser vs. verifier) as its owner, and check that every verifier
refusal fixture is a tree the parser accepts. Fixture-count success criteria
must be derived after that split is fixed.
