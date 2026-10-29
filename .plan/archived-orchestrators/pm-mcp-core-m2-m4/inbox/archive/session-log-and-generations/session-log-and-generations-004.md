envelope_version=1
sender_type=plan
sender_id=session-log-and-generations
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T13:14:30Z

# Candidate lesson: a store read did not check the record's embedded id against its file name

- Source: pr-comment finding 280f76 (CodeRabbit, PR #51, resolution fixed by TASK-16, commit 02bb391)
- Plan: session-log-and-generations
- Signal: signal_automated_review_count
- File: pm-core/src/main/java/de/planmarshall/core/store/PendingEntryStore.java:195

## Observation

`PendingEntryStore.read` (through `PendingEntryCodec`) accepted a valid record without
comparing its embedded `entry_id` with the id taken from its file name. If
`pending/<A>.json` contained `entry_id: B`, the sweep could remove `<A>` and emit a
`pending_entry_removed` audit event naming `A`. That removes a record under the wrong
identity and writes an incorrect audit event.

## Resolution in run

`PendingEntryStore.read` treats a record whose `entry_id` differs from its file-name id
as unreadable, so the sweep keeps it and reports it.

## Possible lesson

Every file-per-record store in pm-core whose file name encodes the record id should
check, on read, that the embedded id matches the file-name id, and should refuse a
mismatch as unreadable. A test should cover the mismatched case. This applies to any
sibling store (journal, generations, session log) that uses the same id-in-file-name
layout.
