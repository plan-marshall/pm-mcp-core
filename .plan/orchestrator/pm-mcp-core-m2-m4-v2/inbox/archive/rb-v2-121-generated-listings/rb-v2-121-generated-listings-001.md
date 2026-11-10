envelope_version=1
sender_type=plan
sender_id=rb-v2-121-generated-listings
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-09T19:56:02Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-121-generated-listings
epic=pm-mcp-core-m2-m4-v2
pr=#339
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2088
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=6
review_body_findings_handled=6
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

# PLAN-V2-121 — generated-listings: landing

Standing rules: r6, R01, R02, R05, R06, R08, R10 (replaced), R12, R17, R18, R19, R20.
Spec read from `spec_source=ledger_worktree`; `spec_differ=true` (the main-checkout copy differed at resolve time).
Executor skew: at start the executor was 0.1.2076 and the lane text 0.1.2088 (verdict `skew`); the operator selected
"Regenerate now, then continue"; it was regenerated to 0.1.2088 (verdict `match` at landing).
Guard: none on this harness (unenforced).

## 1. Deliverables

| D | Done-when | Verdict | Test | Order | Commit |
|---|---|---|---|---|---|
| 1 | generated listings included, columns in Javadoc, ADR | MET | `LogMessageListing` + the three `doc/log-messages/*.adoc`; the full Description/component migration was verified against origin/main's rows | none | ff31c400, 9abd40de |
| 2 | per-module byte check, two controls | MET | `CoreLogMessagesTest.generatedListingIsCurrent` / `byteCheckNamesAChangedListing`; same in `RuntimeLogMessagesTest` and `WorkflowLogMessagesTest` | none | ff31c400, 9abd40de, b32d77a0 |
| 3 | protocol lists dropped for Javadoc links; one reason definition | MET | `WaitAnswerReasonsTest`; `TaskProtocol*Test` use the enum constants | none | ca7434aa, f4396019, b32d77a0 |
| 4 | document-parsing checks removed | MET | `RuntimeLogMessagesTest` no longer reads a document; `ProtocolVocabularyParityTest` deleted | none | ff31c400 |
| 5 | the other holders follow the same form | MET | `CoreLogMessagesTest` and `WorkflowLogMessagesTest` now byte-check their listings | none | ff31c400 |

Done-when 1 (MET, order `none`): `doc/LogMessages.adoc` states no hand-written identifier or template; `workflow-task.adoc`
states no list the code owns; no test opens either document; the only `doc/` paths in tests are the three generated
listings, compared with `Arrays.equals` (`LogMessageListing.same`). No failing run was captured and neither clause
carries a "fails at" condition.

Done-when 2 (MET, order `none`): the positive control is each committed listing; the negative control writes a listing
with one identifier changed to a `@TempDir` file and asserts the byte comparison reports the mismatch. Every
`*.py`-free gate is green on the merged head `ffe23794`:

- `verify -Ppre-commit` — log `/home/oliver/.plan-marshall/marshalld/job-logs/e7799d281c5448cb9410b4e03db6ac43.log`
- `verify` — log `/home/oliver/.plan-marshall/marshalld/job-logs/74dc5c3a835f4a41a283b29c04c27f70.log`
- `verify -Pcoverage -pl pm-core,pm-runtime,pm-workflow -am` — log
  `/home/oliver/.plan-marshall/marshalld/job-logs/6133c2734923402f9e0530ae8149239a.log` (8947 tests, coverage met)

Before the merge-base move the same three gates were green on `b32d77a0` (logs `9eddb6a1…`, `1a3afa58…`, `76ba3808…`).

## 2. Personas and skills

- Persona `implementer` for the production code; `module-tester` for the checks; `code-reviewer` for both self-review
  rounds and the PR triage.
- Skills read (R20) before the outline: `cui-logging`, `cui-testing`, `java-core`, `java-lombok`,
  `java-null-safety`, `javadoc`, `junit-core`, and the project skill `traced-implementation`.
- The spec declares `Skills: none named`; no surface drift needed an added skill.

## 3. Hypotheses

- "No test other than `RuntimeLogMessagesTest` checks the pm-core/pm-workflow rows" — **confirmed**
  (`CoreLogMessagesTest` and `WorkflowLogMessagesTest` read no document at the base).
- "The `WaitAgain`/`End` reasons are nine string literals, all in `TaskProtocol.java`; pm-runtime also sets some" —
  **confirmed** for the nine call sites (`TaskProtocol.java:203,207,211,220,240,250,256,262,269`); **refuted** for
  pm-runtime (no reason literals there; `WorkerCalls.WaitAnswer.WaitAgain`/`End` carry no reason).

## 4. Findings

Self-review round 1 (14 findings): F3/F4/F6/F7/F8/F10/F11/F12 fixed; F1 evidence recorded; F2/F5 recorded as owed;
F9/F13/F14 accepted (placement / optional / owed to PLAN-V2-122).

Self-review round 2: O1 fixed (this record); O2 (standing rule R12 still describes a hand edit — orchestrator-owned);
O3 (ADR 015 and `.github/project.yml` still name the removed readers — owed to PLAN-V2-122); O4 fixed
(`WaitAnswerReasonsTest` now pins `TaskRefusal` and both reason enums from `values()`); O5/O6/O7 nits/refuted.
The residual items are recorded in §9; no code defect is open, but `self-review` is reported `budget-exhausted` because
those items remained open when the review stopped.

PR #339 (CodeRabbit, 6 inline + 2 nitpicks): inline-1 (include not rendered on GitHub) answered as the spec's required
form and ADR 019's recorded trade-off; inline-2 (derive the includes) answered — the holder set is exactly three and is
guarded by `prefixUniqueAcrossHolders`, and a test may not open the catalogue; inline-3 (resolve from the repository
root) fixed in `b32d77a0`; inline-4 (`NO_TASK` wording) fixed in `b32d77a0`; inline-5/inline-6 (assertion messages)
fixed in `b32d77a0`. Both nitpicks (exhausted-budget and consulting branch tests) answered: the branches' behaviour is
unchanged (literal to enum) and their wire words are pinned.

Sonar: S6213 (the restricted identifier `record`) at `LogMessageListing.java:74` fixed in `3bb39c07`; re-read 0.

## 5. Reviewers

- **coderabbit** (required, `quota_limited`): offered `@coderabbitai review` at 14:05; refused on quota at 14:05:24;
  re-triggered at 15:37 (after the 90-minute wait) and reviewed `3bb39c07` at 15:50:14 (COMMENTED). All six findings
  were handled and every thread resolved. After the later pushes it refused on quota at 16:07:55 and 19:22:26; the
  standing review authorization (qualifying review on `3bb39c07`, every finding handled, re-triggered after each push,
  every other gate green on `ffe23794`) was used for the merge.
- **sourcery** (optional): refused on quota at 14:04; approved `3bb39c07` at 14:24:56; its approval was dismissed by
  the later push (non-gating).

## 6. Operator questions

1. "The executor … is older than the runbook-lane text … How should I proceed?" — options {Regenerate now, then
   continue; Regenerate via /marshall-steward; Stop as blocked}; selected **Regenerate now, then continue**.
2. "`verify -Pcoverage` cannot run: /tmp is out of inodes … How should I proceed?" — options {Free /tmp, then re-run
   coverage; Open the PR and report coverage blocked; Stop as blocked}; selected **Free /tmp, then re-run coverage**.

No other question-tool call was made; no free-text operator message changed a decision.

## 7. Rule check

- Guard: none on this harness (unenforced); no guard log exists.
- Spec read recorded: `spec_source=ledger_worktree`, `spec_differ=true`.
- Executor skew recorded: `skew` → regenerated → `match`.
- Deviations and host operations, all recorded: I read build logs under `/home/oliver/.plan-marshall/…` and the Maven
  log under `.plan/local/plans/NO_PLAN/build-results/…`; I used `ps`, `df`, `du`, `lsof`, `tail`, `find` and `rm` to
  diagnose and clear the `/tmp` inode exhaustion the operator authorized (the second time the culprit was ~47k stale
  `/tmp/guard-node-*` directories, removed; 925,956 inodes freed). No repository file was read with a shell command and
  no file outside the repository was edited except that authorized `/tmp` cleanup.
- Sub-agents: two read-only self-review rounds in one session; no restarts, no killed sub-agents, no re-dispatch.

## 8. Build server

Setup preflight `ready`; every build in this run was routed (`mechanism=daemon_longpoll`, `requested=auto`);
`fallbacks: none` (no degraded routing, no launch-fault retry, no killed job). One coverage run hit its explicit 1083s
bound (`timeout_source=explicit`) and one failed on the `/tmp` inode exhaustion; both were re-run, the second after the
operator-authorized cleanup, and the final coverage run was green. `runtime_stamp_reason` was absent.

## 9. Residue

- **Orchestrator:** standing rule R12 still says the identifiers go into `doc/LogMessages.adoc` by hand; it should say
  the listing is regenerated from the holder (`.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md:85`).
- **PLAN-V2-122:** ADR 015 and the `.github/project.yml` comment still name `RuntimeLogMessagesTest` reading
  `doc/LogMessages.adoc` and `ProtocolVocabularyParityTest` reading `workflow-task.adoc`; both readers are gone and the
  follow-up plan owns the include-list narrowing and those lines. `doc/plans/adoc-reading-retired.adoc` names the
  deleted package.
- **Assembly `pm-mcp-server`:** `WaitAnswer.WaitAgain.reason()`/`End.reason()` are now enum types; the adapter must call
  `wireName()` when it takes the SNAPSHOT.
- **Nits not actioned:** `LogMessageListing` lives in `pm-core` main (used by the three tests); the renderer emits an
  empty table for an empty level.
- The mailbox reported `no_mailbox` / `never_delivered` at all three reads (setup, before self-review, before merge);
  it was never a delivered message that went unhandled.
