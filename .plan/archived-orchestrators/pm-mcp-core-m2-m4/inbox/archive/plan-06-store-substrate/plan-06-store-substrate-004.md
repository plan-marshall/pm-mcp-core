envelope_version=1
sender_type=plan
sender_id=plan-06-store-substrate
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T07:10:08Z

component=pm-core
category=bug
source_plan=plan-06-store-substrate
source_record=pr-comment d93c68, c6e842, 60b99f, 13ebc4, 1a7522, 64305c (PR 26, review round 1, all fixed)

# Journaled store substrate shipped without the tampered and crashed on-disk states

## What happened

The first review round of PR 26 (sourcery, coderabbit) found six defects in the new store substrate
of `pm-core` (`de.planmarshall.core.store`), all of the same kind: the code trusted on-disk state it
had written itself, and no test put that state in a crashed or altered form.

- Symlinks escape the scope: `JournalScope.resolve` checked lexical paths only, while writes and
  JSONL repair followed symlinks (High; fixed in TASK-26 by refusing symlink components, following
  the NOFOLLOW_LINKS convention of `PathConfinement`).
- An altered journal could rename an unrelated in-scope file over a store: `TransactionJournal.Target`
  accepted any non-empty `stagedPath` (High; TASK-23).
- Recovery committed corrupt staged data: `JournalRecovery` did not verify staged bytes or the append
  prefix against the journaled digest before applying (Critical; TASK-24).
- A leftover `digests.json.tmp.<id>` from a crash inside `writeDigests` made every later recovery fail
  with `FileAlreadyExistsException` (Major; TASK-24).
- A sidecar with an absolute or escaping key threw `IllegalArgumentException` instead of a
  `StoreRefusal` (TASK-26).
- `ScopeIntegrity.snapshot` could return VERIFIED while a journal appeared during the read (TASK-27).

The plan's crash-point tests covered the boundaries of a clean commit; none covered "the file the
code reads back is not the file it wrote".

## Rule

For every persisted file a recovery or verification path reads back (journal, staged file, sidecar,
temporary file), the plan's test deliverables must enumerate and cover: the file altered after
writing, the file left over by a crash at each step (including temporaries a failed step leaves in
place), a path component replaced by a symlink, and a concurrent writer appearing between check and
read. Recovery must verify what it applies against the journaled digest, never only that the file
exists.
