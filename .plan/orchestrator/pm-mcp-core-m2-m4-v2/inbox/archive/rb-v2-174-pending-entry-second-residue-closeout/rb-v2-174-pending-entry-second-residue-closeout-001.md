envelope_version=1
sender_type=plan
sender_id=rb-v2-174-pending-entry-second-residue-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-23T16:04:51Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-174-pending-entry-second-residue-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#578
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=1
review_body_findings_handled=1
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced).

# PLAN-V2-174 — the pending entry's second residue: landing

Standing rules: read r7 (R02, R05, R06, R07, R08, R10, R11, R13, R14, R17, R18, R19, R20). Spec read from the ledger
worktree (`spec_source: ledger_worktree`, no `spec_differ`). `executor-skew`: match (executor 0.1.2183, lane 0.1.2183).
Merge commit `9f9fe51b17271b1c5c18071469021b1925aca8fb` (merge queue, merge_group run 45210852937 success).

## 1. Deliverables

| Deliverable | Done-when | Order | Verdict | Test | Failing run | Commit |
|---|---|---|---|---|---|---|
| D1 | count the directories gone; name removed and kept; report the unreadable list when the removal throws; correct the claim | test-first | met | `PendingEntryRecordsSweepTest.discardRefusedAfterCommitCountsTheDirectoriesGone`, `journalToRecoverFails`, `cutOffRemovalSwept`, `removalsCarryDistinctTransactionIds` | base `2be5eca8`, log `37c0d4c832ad4217b765e62079aa046f` (11 failures) | `70861cf3`, `39a719ac` |
| D1(c), optional | a non-link refusal through `run()` | none | **not adopted (optional), recorded** — no filesystem state produces a plain non-link `IllegalArgumentException` from `verify`, and the sweep has no seam to inject one; the predicate is covered by `secondCauseIsNoHeldEntry` and the run-level fault path by `journalToRecoverFails` | — | — | PR body § Deliverable 1(c) |
| D2 | refuse a non-regular file the manifest names; assert the refusals' reasons and own types; reach the parent-link guard | test-first (special file) / mutation-after (guards) | met | `PendingEntryCreationTest.refusesASpecialFileTheManifestNames`, `refusesAPathBelowALinkedParentInTheComposition`, `refusesALinkedTranscript`; `PendingEntryStoreTest.linkedStoreRefused`; `SourceManifestCodecTest.invalidCharacters` | test-first `39a719ac` log `89e4f71e967b4d56a915b10bd02fcfac`; mutation-after `39a719ac` log `b54b0c3c0ae643f9821e419f8131f4dc` (parent-link check removed, nothing thrown); mutation-after `5eb33019` log `f219408f112949469508ae9bec52a87f` (regular-file guard removed) | `7b2f8bed`, `5eb33019` |
| D3 | ask the operator; implement the answer; correct Javadoc and messages; assert the rule; test the stored-manifest read; state it in ADR 084 | test-first (rule) / mutation-after (stored read) | met | `SourceManifestCodecTest.normalisationVariantPaths`, `casedNormalisationVariantPaths`, `caseVariantStoredManifest` | test-first `7b2f8bed` log `4f55ed403a17409ea94e68f33a0aebe6`; mutation-after `7b2f8bed` log `b551f60dcb0740118f83a4eac869d6d6` (fold replaced by identity) | `0e29d124` |
| D4 | guard every fraction length; separate ADR 083's attribution; correct the stale statements; record the don't-fix list | mutation-after (fraction guard) / none (doc statements) | met | `AuditEventTest.removedAtFraction[0,3,6,9]` | mutation-after `b08b7c08` log `90a843aee4a54c66b51eb648ef6860a3` (`removed_at` truncated to millis; cases 6 and 9 failed) | `b08b7c08` |

Every carried item 92(a)–(d) has an outcome: (a) D1, (b) D2, (c) D3, (d) D4; the record-don't-fix list (`ent-` prefix
shadowing, the creation cut between the two commits and the live-plan enumerator — the assembly, ADR 086 — and the epic's
ledger copy of the sources, PLAN-V2-133 D5 parked) is recorded in the PR body and above.

Per-head test runs against a pushed head: `39a719ac` 12734, `7b2f8bed` 12736, `0e29d124` 12737, `b08b7c08` 12741,
`4dbd99bd` 12741, `6c286bce` 12741, `5eb33019` 12741 tests (`module-tests`, all green); the merge head `81e60811` ran
the full `verify` green with 12763 tests.

## 2. Personas and skills

- Personas applied: `persona-implementer` (production changes in the sweep and the stored-file read),
  `persona-module-tester` (the test work of D1–D4), `persona-code-reviewer` (both self-review rounds and the triage of
  the CodeRabbit finding), `persona-documenter` (ADR, guide and specification corrections).
- Domain skills read at the start, as the spec asks (R20): `java-core`, `java-null-safety`, `javadoc`, `junit-core`,
  `cui-testing`. The spec declares no per-deliverable `Skills:`/`Persona:` lines (`skills: none named`).
- Skills added for surface drift: none (no file outside the declared surface was touched).

## 3. Hypotheses

- F1 (`removed.size()` counted an entry whose discard failed) — confirmed at `origin/main` `2be5eca8`
  (`PendingEntryRecordsSweep.java:159` pre-change).
- F2 (the catch returned the failure without the `unreadable` list) — confirmed at `2be5eca8`
  (`PendingEntryRecordsSweep.java:121-124` pre-change).
- F3 (the parent-link branch of `verified` was untested; the walk refused the fixture) — confirmed: `requireOnlyManifestFiles`
  refuses any link below `sources/` (`PendingEntryCreation.java:263-266`), so the branch at `:219` was unreached from the walk.
- F4 (a FIFO or special file the manifest names passed the walk and was opened) — confirmed: the walk treats a named
  non-directory as admissible (`:267-271`) and the read had no regular-file check before `Files.newInputStream`.
- F5 (ADR 083 attributed the question move's refusal to the operator's 2026-11-22 word) — confirmed
  (ADR 083 Decision, pre-change), refuted as a decision of `PLAN-V2-171`'s question: it belongs to `PLAN-V2-167` D1a,
  which the spec of 167 records.
- F10 (a stored manifest with case variants reads as a refusal at version 1) — confirmed by test
  (`SourceManifestCodecTest.caseVariantStoredManifest`: `Outcome.UNREADABLE`, `current() == 1`).

## 4. Findings

- Self-review round 1 (sub-agent, `persona-code-reviewer`): 11 findings — one defect (the claim "one failure never drops
  another" was still false when the removal throws after an entry was held) fixed in `4dbd99bd`; nits 5–9 and 12 fixed in
  `4dbd99bd`; 2, 3, 4 and 11 recorded (the sweep test's whole-second form, the D1(c) outcome, the non-discriminating
  case-pair assertions, and the evidence logs' location outside the repository).
- Self-review round 2: 3 preferences, no defect — 1 and 2 fixed in `6c286bce` (the report guarantee scoped to a completed
  removal; "held or tampered" named), 3 satisfied by running the gate at that head.
- CodeRabbit (Major, `6c286bce`): the special-file test's socket path could exceed the 103-byte `sun_path` limit on macOS
  — fixed in `5eb33019`; thread `PRRT_kwDOVBd2NM666NpI` answered with the commit and resolved; CodeRabbit marked it
  addressed.
- Sourcery: approved `5eb33019`, no finding. SonarCloud: 0 open new-code issues, gate passed. All bot notices (rate
  limits) were recorded, none is a finding.

## 5. Reviewers

| Reviewer | Role | History |
|---|---|---|
| coderabbit | required (quota-limited, `review_complete_after_handling`) | triggered 11:23 → refused on quota; waited 90 min; re-triggered 12:56 and posted the escalated trigger once → refused 13:31; waited; re-triggered 15:02 → **reviewed 6c286bce 15:09** (`kind=reviewed`, covered `6c286bce`) with one Major finding, fixed in `5eb33019` and resolved. Done exemption applies: the review and its handled finding stand at any head, no re-trigger on later heads. |
| sourcery | optional | triggered 11:23 → refused; re-triggered 15:02 → refused; **approved `5eb33019` 15:14**; re-triggered on the merge head, no new review (optional, does not gate). |

Standing review authorization: not needed (no unresolved finding, and the required reviewer's review is on record with its
single finding handled).

## 6. Operator questions

1. "PLAN-V2-174 Deliverable 3 is the case-fold decision PLAN-V2-171 marked ask-at-refine but never asked … Which reading
   should this plan implement and record in ADR core-store/084 (the Javadoc and the message currently name only 'case'
   and will be corrected either way)?" — options: **Keep the fold as it is (Recommended)**; Narrow it to ASCII and a
   documented Unicode set; Refuse at write, tolerate at read. **Selected: "Keep the fold as it is (Recommended)".**

No free-text operator messages. No surface/naming error prompted a question.

## 7. Rule check

- Guard: `guard: none on this harness (unenforced)` — nothing was enforced or recorded. The run issued no shell file
  operation (all reads and searches went through the file tools); every script and build went through
  `python3 .plan/execute-script.py …`; no `gh`/`glab`, no direct build wrapper, no `sleep`.
- Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker. `executor-skew`: match.
- Restarts: one server restart at 2026-11-23 13:40 during the CodeRabbit quota wait; the lane text was re-read, the
  re-read recorded in the progress file, `executor-skew` re-run (match) and the wait re-issued; no step was redone.
  No sub-agent was killed and nothing was re-dispatched except self-review round 2 over the same session.
- Proof-read of a mutation: two mutations were run and reverted (the parent-link confinement check, the fold key, the
  `removed_at` truncation, the regular-file guard); each revert left `git status --porcelain` clean before the next commit.

## 8. Build server

- Preflight at setup: `ready` (registered). Every build was submitted with the default routing and resolved
  `daemon_longpoll`: 32 routed build calls, 0 `in_process_fallback`, no launch-fault retry. Four routed jobs returned a
  live `running` status and were re-attached with `build_server wait` (`b8daffd7`, `97e91082`, `6223c74e`, `219bfcf3`).
- No `killed` job. No `runtime_stamp_reason` (every runtime fact was measurable except `plugin_version`, which the
  resolve result itself reports as `unknown`).

## 9. Residue

- The links check: the operator removed the requirement from the epic's standing rules on 2026-11-23 (R17 amendment,
  `handle-open` Q10), and plans started before that date keep the text they read; this lane did not run a script by path.
  No new link was introduced by the change.
- Recorded, not fixed (deliverable 4): the `ent-` prefix shadowing, the creation cut between the two commits and the
  live-plan enumerator (the assembly, ADR 086), and the epic's ledger copy of the sources (PLAN-V2-133 D5, parked).
- Deliverable 1(c) is the one optional item not adopted, with the reason above.
