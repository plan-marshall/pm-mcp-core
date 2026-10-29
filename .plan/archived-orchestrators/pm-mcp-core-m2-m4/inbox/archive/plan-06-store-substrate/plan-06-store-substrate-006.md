envelope_version=1
sender_type=plan
sender_id=plan-06-store-substrate
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T07:10:28Z

component=pm-core
category=bug
source_plan=plan-06-store-substrate
source_record=pr-comment 33c094 (PR 26, review round 1, fixed by TASK-29 and TASK-31)

# Audit event appended after the mutation is lost when the process stops in between

## What happened

Sourcery reported (Medium) that recovery, JSONL tail repair and cross-scope reconciliation changed
data first and appended the audit event afterwards (`JournalRecovery` ~l.149, `OrderedWrite`
~l.137, `JsonlStoreFile` ~l.261). If the process stops or the audit sink fails between the two, a
retry no longer meets the condition that produces the event, so the required audit record is
permanently absent. TASK-29 introduced a replayable audit intent (`OwedAuditEvents`) with
transaction-id deduplication, and TASK-31 specified the audit delivery in the documentation.

The plan specified which events are emitted but not their durability relative to the change they
describe.

## Rule

When a mutation must be audited, the specification and the deliverables state the ordering: the
audit intent is recorded durably before (or atomically with) the mutation and is replayed until the
sink accepts it, deduplicated by transaction id. "Mutate, then append the event" is only acceptable
when losing the event is acceptable, and the plan must say so.
