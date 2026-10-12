envelope_version=1
sender_type=plan
sender_id=plan-11-enrolment-store
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T13:19:11Z

# Re-baseline signed a file that a pending staged write later replaced, then hid that it settled one

Component (suggested): pm-core store (EnrolmentStore) / pm-runtime enrolment (Enrolments.rebaseline), category bug
Signal: automatic-review, pr-comment findings fixed in-run on PR #30 (coderabbitai), rounds 7 and 8

## Observation

- 2f1223 (`EnrolmentStore.java:596`): `write` ran `recoverPair` first and `remove` deleted staged names, but the re-baseline path (`writeSidecar`) did neither. A complete staged pair left by a stopped `enrol` would later be rolled forward over the re-baselined pair, so a file the operator did not review replaced the one they did, with a valid HMAC and no report. TASK-43 settles the pair right after the locks are held, before the snapshot.
- 53f5f7 (`Enrolments.java:272`, next round): the fix discarded the result of `recoverPair`, so the operator still could not tell that a stopped write had replaced their edit before signing. TASK-46 added `settled` (list of PairRecovery) to the Rebaseline outcome.

## Pattern

Every mutator of a paired, crash-recoverable store must run the same "settle pending staged state first" prologue, and an operator-confirming operation must report what that prologue changed. The second finding came only because the first fix was scoped to the reported site.

## Candidate rule

For a store with staged/recoverable writes, list every mutating entry point and give each the same settle-first prologue in one task; when settling can change what an operator confirms, return the settlement in the outcome. Related: 2026-10-11-07-006 (tampered/crashed on-disk states).

Source: plan plan-11-enrolment-store (PLAN-11, PR #30); findings 2f1223, 53f5f7.
