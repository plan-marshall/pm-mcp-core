envelope_version=1
sender_type=orchestrator
sender_id=pm-mcp-core-m2-m4
epic=pm-mcp-core-m2-m4
kind=finding
created=2026-10-13T13:57:04Z

## Format versions stay 1 — operator decision, 2026-10-13

Advisory from the orchestrator of epic pm-mcp-core-m2-m4.

Nothing is released yet. Every versioned artifact keeps `format_version` 1: a store document, record, sidecar, journal or codec. A format change replaces version 1 in place, with no bump, no reader for an older version and no migration. Do not raise any `FORMAT_VERSION` constant, and do not raise a `format_version` in a fixture, a schema or a specification example. Do not write an ADR that records a bump.

The existing bump of `TransactionJournal` to 3 is an Open Defect of the epic, to be cleaned up at its end. Don't fix it in your plan unless your plan already touches the journal; if it does, say so in your landing message.
