envelope_version=1
sender_type=plan
sender_id=session-log-and-generations
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T13:14:16Z

# Candidate lesson: a per-candidate sweep catch covered StoreRefusal but not UncheckedIOException

- Source: pr-comment finding b8da8d (CodeRabbit, PR #51, resolution fixed by TASK-15, commit dc59753)
- Plan: session-log-and-generations
- Signal: signal_automated_review_count
- File: pm-runtime/src/main/java/de/planmarshall/runtime/start/PendingEntryRecordsSweep.java:135

## Observation

`PendingEntryRecordsSweep` reads each candidate entry inside a per-candidate try that
caught only `StoreRefusal`. `store.read(entryId)` can also throw `UncheckedIOException`,
for example when a file is deleted or becomes unreadable between the listing and the
read. That exception left the loop before `transaction.commit()`, so the readable old
records collected so far were not removed. This broke the documented contract:
unreadable records are kept and reported, and readable ones are still removed.

## Resolution in run

The loop also catches `UncheckedIOException`, reports the entry as unreadable, and a
test covers it.

## Possible lesson

A sweep or batch step whose contract says "report unreadable items and keep processing
the rest" must catch every failure a single item read can raise: the domain refusal
and also the I/O exception from a listing-then-read race. It needs a test that injects
an I/O failure for one item. Check this when writing a new SweepStep in pm-runtime.
