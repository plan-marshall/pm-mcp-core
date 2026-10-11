envelope_version=1
sender_type=plan
sender_id=plan-06-store-substrate
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T07:10:40Z

component=pm-core
category=anti-pattern
source_plan=plan-06-store-substrate
source_record=pr-comment c590d7, e87af9 (round 1), 549203 (round 2), all fixed

# Smaller code defects the review bots caught in the store substrate

## What happened

Three review findings on PR 26 that are each small but recur in Java code generally:

- Mirrored literals (coderabbit, c590d7): the staged-file suffixes `.tmp.` and `.append.` were
  written as separate literals in `ScopeTransaction.stageTarget`, the journaled
  `AtomicStoreFile.write` and `JournalRecovery.STAGED_TMP` / `STAGED_APPEND`. Recovery deletes files
  by matching these strings, so a drift between writer and matcher leaves orphans or deletes the
  wrong files. TASK-23 defined them once.
- Whole-file materialization (sourcery, e87af9): a journaled JSONL append read the entire store into
  memory and copied it again to compute the resulting digest, so every append needed several
  full-log-sized arrays. TASK-30 computes the digests by streaming.
- `String.formatted` precedence (coderabbit, 549203): in `OwedAuditEvents`,
  `"Detail '%s' ... " + "a string: %s".formatted(name, item)` applies `formatted` to the second
  literal only, so the message showed the wrong value and never the offending item. TASK-32 wrapped
  the concatenation in parentheses.

## Rule

- A set of names that a writer produces and a matcher recognises is defined once and referenced by
  both.
- A digest over an append-only file is computed by streaming the existing content and the new block,
  never by building the resulting file in memory.
- `.formatted(...)` on a concatenated message wraps the whole concatenation in parentheses; prefer a
  single text block or one literal per message.
