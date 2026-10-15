envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:57:53Z

# Torn-tail repair record of the audit log could be lost (intent deleted before durable)

- Source: Q-Gate record 52c2a0 (3-outline, warning, pm-core/.../store/AuditLog.java), resolution `taken_into_account`.
- Suggested component: project-local (pm-mcp-core core.store at-least-once delivery, ADR core-store/027).

## Observation

The outline kept `JsonlStoreFile` unchanged while having AuditLog capture the `jsonl_tail_repaired` record through a sink; `AuditIntentFile.handOver` would delete the owed intent before the record was in audit.jsonl, so a stop in that window loses the record. Settled with a private in-log sink that forces the chained record to disk via `SyncedFiles.append` before returning, with dedup by transaction_id on re-delivery.

## Candidate rule

Any sink that "accepts" an owed record must make it durable before returning; an outline that keeps a store unchanged while re-routing its sink must state the durability point explicitly.
