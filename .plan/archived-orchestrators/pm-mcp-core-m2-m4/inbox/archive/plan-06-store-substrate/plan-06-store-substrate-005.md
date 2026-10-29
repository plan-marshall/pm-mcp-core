envelope_version=1
sender_type=plan
sender_id=plan-06-store-substrate
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T07:10:19Z

component=pm-core
category=bug
source_plan=plan-06-store-substrate
source_record=pr-comment 2b2edf (review round 1) and eedad7 (review round 2), both fixed

# Store keys built from raw path spellings, fixed at one site per review round

## What happened

The same defect class reached two review rounds of PR 26.

- Round 1 (sourcery, 2b2edf): `ScopeTransaction.add` deduplicated targets by the raw path string
  although `scope.resolve` normalizes it, so `status.json` and `./status.json` became two targets of
  one store. TASK-22 keyed targets by the normalized store path.
- Round 2 (coderabbit, eedad7): `ScopeIntegrity.verify` and `snapshot` still looked the sidecar entry
  up with the raw `relativePath`, so a tracked store named with a non-normalized spelling verified as
  `Untracked` / `UNCHECKED` and the `store_tampered` evidence was missed. TASK-32 fixed the lookup;
  TASK-33 added regression tests.

The round-1 fix corrected the reported site and did not sweep the other sites that turn a caller's
path into a sidecar key.

## Rule

A path a caller supplies is turned into a store key once, at the boundary, through one function
(`ScopeTransaction.storePath`), and every read and write of the sidecar uses that key. When a review
reports a key-derivation defect at one site, list every site that derives or looks up the same key
and fix and test them all in the same round; one regression test per entry point with a
non-normalized spelling (`./x`, `a/../x`).
