envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:58:01Z

# Changed log-message template left out of the LogMessages.adoc update

- Source: Q-Gate record f9ad42 (3-outline, warning, ../plan-marshall-documentation/doc/LogMessages.adoc), resolution `taken_into_account`.
- Suggested component: project-local (pm-mcp-core CLAUDE.md logging rule) / `pm-dev-java-cui:cui-logging`.

## Observation

D3 changed the template and arguments of the existing WARN `RuntimeLogMessages.AUDIT_CHAIN_BROKEN` (PM_RUNTIME-108), but D6 restricted the documentation change to new identifiers only, so the documented template would have gone stale. PM_CORE-100 wording was also affected by the per-record transaction id change.

## Candidate rule

A deliverable that changes an existing `LogRecord` (template or arguments) must list the corresponding `doc/LogMessages.adoc` row as a change, not only rows for new identifiers; the success criterion "lists exactly the new identifiers" is the wrong shape.
