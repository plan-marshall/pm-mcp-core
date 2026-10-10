envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:52:54Z

# Candidate lesson: atomic store write must force the parent directory after the rename

- Component: pm-core store (AtomicStoreFile); specification Format & Atomic Write Discipline item 2
- Signal: review-bot findings fixed in-run (pr-comment fbb4c4 sourcery, 24a9a9 coderabbit; same defect)
- Evidence: fixed in 50f9ce8 (TASK-017); decision log 42c424

## What happened

`AtomicStoreFile.write` forced the temporary file and then did `Files.move(..., ATOMIC_MOVE)`, but did not force
the containing directory. On POSIX a power loss after the move can lose the directory entry, so a lease claim,
release or orphan mark the caller saw succeed could disappear after restart. Fix: open the parent directory
read-only after the move and `force(true)` it; a failure there is reported as "store replaced, directory not
forced". The written specification item did not require this; its amendment follows in
plan-marshall-documentation.

## Suggested direction

Any write-temp-then-rename store primitive (here and in later packages) must fsync the directory after the
rename; the specification item should say so, so the next implementation does not depend on a review bot.
