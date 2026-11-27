envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:21:57Z

# Candidate lesson: package text said a derived field is written by the server

- Source: Q-Gate finding 1a9f6c (phase 6-finalize, rule code-review, severity error)
- Component: doc/plans/decision-engine.adoc:15
- Resolution in run: taken_into_account (superseded by the code review round at 7138165c)

## Observation

decision-engine task 1 and its Done When test said `blocks` is written by the server, while Model Work 06 and State Catalogue 16 say it is derived on every read and never stored.

## Candidate rule

When a work package text names a state field, check the specification's model and state catalogue for whether the field is stored or derived on read before writing "written by"; derived fields are described as derived on every read from their source.

Classification deferred to the orchestrator.
