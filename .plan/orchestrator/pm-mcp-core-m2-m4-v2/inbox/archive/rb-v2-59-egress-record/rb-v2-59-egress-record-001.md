envelope_version=1
sender_type=plan
sender_id=rb-v2-59-egress-record
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-17T22:37:00Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-59-egress-record
epic=pm-mcp-core-m2-m4-v2
pr=#487
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2149
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=10
review_body_findings_handled=10
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-59 — the worker egress record (runbook lane)

Merged as #487, merge commit `b83d7f2cd4779cedda60e9cb9c46cabd775e8409`. Standing rules: read at revision **r7**
(the spec's First Instruction names r4; the rules file itself says a starting plan picks up the current revision).
Rules applying: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19, R20.

## 1. Deliverables

| # | Done-when | Order | Verdict | Evidence |
|---|---|---|---|---|
| D1 | three parts read as above; `grep egress` finds the member, the kind **and the Remaining paragraph**; anchors resolve; links 0 | none (documentation) | **not met as written** — every clause is met except the Remaining paragraph, which standing rule R05 r7 forbids ("no paragraph that opens 'Implemented by' or says 'Remaining:' is written"). The remainder is stated in the package file instead. | member `job-record.schema.json:92` + `:196`; kind `activity-entry.schema.json:13` + `:24`; § Worker Egress Proxy `02-confinement-and-environment.adoc:157-168`; § Workers bullet `10-job-and-lesson-records.adoc:21`; *Harness and server entries* `16-task-queue.adoc:49`; anchors `#job-record` (10:11), `#activity-journal` (16:42), `#worker-egress` (02:157); commit `01090781` + `b38ddf73` |
| D2 | the three test classes green; the block differs by the member, its `required` entry and the `allOf` lines only | test-first | met | red with the edited block and the unchanged code (`JobRecordSchemaParityTest.membersEqualSchema`, `TaskQueueSchemaParityTest.members`/`enums`, log `c2cca4b6ec03480d8f8a72dddc8fdc94`); green `JobRecordSchemaParityTest`, `JobRecordCodecTest` (`workerEgressRoundTrip`, `workerWithoutEgressRefused`, `nonWorkerWithEgressRefused`, `gitMetadataKindRule`), `JobRecordStoreTest` (via the `workerAhead` helper); block diff reviewed: three lines only; commit `93d56d0c` |
| D3 | the cases green; `SubprocessJobRunner` differs by one expression | mutation-after | met | red with the `.egress(...)` expression reverted in `newRecord`: all three `EgressJobTest` cases fail with `A worker records whether its egress is enforced` (log `01e3efabed6f40399e1220a5434f3da8`); the runner diff is that one expression plus its import; commit `93d56d0c` |
| D4 | the classes green; the block differs by the kind, the member and the description only | test-first | met | red with the edited block and the unchanged code (`TaskQueueSchemaParityTest.members`/`enums`, log above); green `ActivityEntryValidationTest`, `ActivityEntryTest.egress`, `ActivityJournalMaskingTest` (masking + the shrunk-destination case), `ActivityEventMappingTest.declaredEgressMapsNothing`, `TaskQueueSchemaParityTest` (`egress`, `egressBounds`, `egressLineBreakRefused`); block diff reviewed: two lines only; commits `93d56d0c`, `ed76fd04`, `b38ddf73` |
| D5 | guides, both ADRs and the Traceability lines in the pull request; the remainder stated; the body lists *Owed to the milestone exit* | none (documentation) | met | `core-store.adoc:607`, `core-queue.adoc:61-71` and `:225`, `runtime-job.adoc:343`; ADR `core-store/072`, ADR `core-queue/010`; `Traceability.adoc` PM-SEC-5 (`Implementation`/`Verified by`/`Documentation` of the egress) and PM-WORK-7; package file `:32-51`; PR body's *Owed to the milestone exit*; commit `01090781` |

Plan-level Done-when: every worker record `unenforced` and every other kind `null` (met, mutation-after above); the
`egress` kind round-trips and is refused on any other kind (met); both parity tests green against the edited blocks and
red against an unedited block (met: blocks reverted to `origin/main` with the code unchanged → 6 failures, log
`cd32ef10f1a8410cad413d95dda5bff2`); no proxy start, no gate, no log message (met: the diff touches neither
`WorkerEgressProxy`, `WorkerSupervisor`, `LaunchReport`, `AuditEvent` nor a log holder, and `Egress.ENFORCED` has no
writer); the Remaining lists in the specification **and** the package file (**not met as written** on the specification
side, R05 r7 — see D1). Sonar: 0 open new-code issues and the quality gate green (met). Build: green (below). Links:
`BROKEN LINKS 0` on the first read, and 6 on the reads after the base merges — all in the documentation sibling pointing
at watch anchors this repository's `origin/main` no longer has (`PM-WATCH-HYP-11`, `PM-WATCH-JOB-1`, `PM-WATCH-JOB-11`,
`PM-WATCH-PHASE-104`, `#plan-deletion-lessons`), verified absent at `origin/main` and in no file this branch touches (the
Done-when's sibling carve-out).

Per-head test runs (a run at every pushed head, local gate at the tree the commit records, plus the project's own CI):
`01090781` (10 994), `ed76fd04` (10 994), `35e8271e` (10 995), `287e5c01` (10 995), `1500fb36` (10 995), `f0833952`
(10 995), `b38ddf73` (10 996), `2592fe56` (10 996), `94190a7a` (10 996). Module/coverage/integration runs at `01090781`:
`verify -pl pm-core,pm-runtime` 7 701, `verify -Pcoverage` 7 701, `verify -Pintegration-tests -pl pm-core -am` 21,
`-pl pm-runtime -am` 43 — all green.

## 2. Personas and skills

- production code: `persona-implementer` (D2, D3, D4); unit/module tests: `persona-module-tester`; documentation: `persona-documenter` (D1, D5); every self-review round and every PR-comment triage: `persona-code-reviewer`.
- The spec declares no `Skills:`/`Persona:` lines → **skills: none named**. The standing-rule skills loaded as standards: `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing`. Not loaded, with the reason: `cui-logging` (no log record), `junit-integration` (no integration test authored), `cui-http-testing` (no HTTP surface).
- **Surface added (R19), all new or compile-found files the spec's Expected Surface does not list:** `EgressJobTest` (new; the worker-start fixture lives beside `GitMetadataJobTest`), `ProbeJobTest` and `SubprocessJobRunnerTest` (the non-worker cases), `JobRecoveryStepTest`/`SupervisionPassTest` (one `.egress(...)` call each, found by compile), `ActivityEntryBuilderContractTest`/`JobRecordBuilderContractTest` (the added component), both schema resources (the pinned blocks D1 owns), and `SecretRedactor` (the Carried Open Item's `@UtilityClass` conversion).

## 3. Hypotheses

- "the job record has no `egress` member" — **confirmed** before the change: `JobRecord.java:93-101`, `MEMBERS` at `JobRecordCodec.java:110`.
- "the specification requires the member and the activity entry" — **confirmed**: `job-runtime/02-confinement-and-environment.adoc:163-166`.
- "the activity kinds are a closed list with no egress kind" — **confirmed**: `ActivityEntry.java:115-135` before the change.
- "`WorkerEgressProxy` has no production caller" — **confirmed**: the supervisor passed no proxy URL (`SubprocessJobRunner.startWorker` request has none; `WorkerSupervisor` passes `egressProxy(null)`).
- "the launch report carries no egress statement" — **confirmed**: `LaunchReport` has no such component.
- "`newRecord` is the one builder of the first record of every job" — **confirmed**: `SubprocessJobRunner.java:1813-1821`, used by `startWorker`, `refuseWorker`, `start`, `startProbe`.
- "`ActivityEventMapping`/`HarnessEventVerification` skip non-harness kinds" — **confirmed**: `ActivityEventMapping.java:70-76`, `HarnessEventVerification.java:65-69`.
- "`SecretRedactor.redact(ActivityEntry)` masks text, tool target and link target only" — **confirmed**: `SecretRedactor.java:648-663` before the change.
- "`egress` is non-null exactly for the kind `llm`" (HYPOTHESIS) — **refuted** by the spec's own re-ground (`job-runtime/02:198-203` names `egress` values for `build` and `project_script` too). Resolved in the text: the member is `null` for those kinds *today*, and their values join it "with their own implementation"; the plan's deliverables stay llm-only, as written.
- "a refused worker records `unenforced`" (HYPOTHESIS) — decided by ADR 072 and pinned by `EgressJobTest.refusedWorkerRecordsUnenforced`.
- "the 259-character bound covers every string the proxy admits" (HYPOTHESIS) — **refuted** (`isHostPort` bounds only the port). The bound is the record's own bound, not a mirror of the proxy's admission; the ADR and the guide say so.
- "the Expected Surface lists every test class that builds a worker `JobRecord`" (HYPOTHESIS) — **refuted**; the compile found `JobRecoveryStepTest` and `SupervisionPassTest`, adapted by one `.egress(...)` call each.
- "`.github/project.yml:31 skip-on-docs-only: false`" — **refuted at the base** (the spec's own re-ground); the skip stays on. No build decision relied on it: every head was built locally and by CI.

## 4. Findings

Self-review round 1 (sub-agent, `persona-code-reviewer`): spec contradiction on the non-worker values (fixed by scoping
the sentence to "today" and marking the build values as awaiting their implementation — no requirement deleted); D1's
"Remaining" paragraph vs R05 r7 (reported not met as written); **masking shrank an attacker-chosen destination below the
3-character floor and threw out of `ActivityJournal.append`** (fixed test-first: red `IllegalArgumentException: An egress
destination has 3 to 259 characters, not 2`, log `4857793be29e4b3fb2776209c532631e`, then masked whole); package-file
remainder completed; nits folded in. Round 2: all dispositions held; one new should-fix (unused `BUDGET_KEY`, a Sonar
S1068 waiting to happen) removed; the destination bounds pinned to the schema; duplicate git case deduped.

CI and Sonar: first analysis found 7 new-code issues (S1301, S3776×2, S1479, S5778×2, S5785) — all fixed; the next found
`javabugs:S6416` (the dataflow read the nullable parameters as non-null and reported the refusal as always throwing) —
fixed by keeping the refusal in the kind rule and extracting only the worker and probe checks; the Quarkus-alignment job
failed on a Maven Central HTTP 429 and passed on re-run.

PR comments (CodeRabbit, 10 threads, all replied and resolved; the two "derive it from the schema" recommendations were
withdrawn by the bot itself): 4 fixed in `b38ddf73` (the specification, the ADR and two guides now state the owed
producer and point at the package file; the activity schema rejects a destination with a line break, pinned by a parity
case), 6 answered with a reason — the schema is the specification the codec follows (parity tests pin the drift), R13
keeps `format_version` at 1 with no migration and the refusal of a pre-change record is the specified behaviour, and the
`EnumSet` assertion pins the kind fixture so it cannot drift silently.

## 5. Reviewers

- **coderabbit** (required, `quota_limited`, `review_complete_after_handling`): triggered at 19:41Z on `35e8271e` — **refused on quota** ("used all free OSS reviews"; next included review 46 minutes). No qualifying review existed then, so §6.7 applied: recorded, and no re-trigger before 21:11Z (90 minutes, not the bot's 46). It then reviewed automatically: **20:53Z on `1500fb36`** (8 findings, formal review) and **22:06Z on `2592fe56`** (2 findings). All 10 handled; threads resolved. Standing review authorization per §7: a qualifying review exists on a head of this PR (`1500fb36`, `2592fe56`), every finding is fixed or answered, and every other gate condition holds on the current head, which is why the two later orchestrator-only merges raised no re-trigger.
- **sourcery** (optional, `quota_limited`): triggered at 19:41Z — **refused** (7-day diff-character budget, resets 2026-11-19). Optional, so it does not gate; no escalated trigger (it refuses on its own budget, not on a per-review quota the escalation addresses).

## 6. Operator questions

- 2026-11-17, at setup: "executor skew: regenerate and continue, or stop blocked?" — options {Regenerate then continue (Recommended), Stop blocked}; **selected "Regenerate then continue"**. The executor was regenerated (`0.1.2136` → `0.1.2149`) and `executor-skew` then answered `match`. No other question was asked; no free-text operator message was received.

## 7. Rule check

- `guard: none on this harness (unenforced)` — nothing was enforced, so "all kept" is not claimed. No guard refusal occurred.
- Rule breaks by this run, stated plainly: two shell file-inspection calls that the rules reserve for the file tools — `ls pm-core/doc/adr/core-store pm-core/doc/adr/core-queue` (to find the next free ADR numbers) and `wc -l` over the test files (to size them). Everything else used the read/grep/glob tools. No `cat`/`find`/`head`/`sed`, no pipes, `&&`, `;`, redirects, heredocs, loops, `python3 -c` or `git grep`.
- Reads outside the repository: the harness's own saved skill output under `~/.local/share/opencode/tool-output/` (to recover the truncated skill text) and the build logs and the documentation sibling that R17 names. No credential file, config directory or environment dump was read.
- No restart, no killed sub-agent, no re-dispatch other than one invalid `subagent` call (missing `agent`) re-issued immediately with the same session — it re-ran nothing.
- Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker (both copies equal or only one present).
- `executor-skew`: first read answered **skew** (executor `0.1.2136` vs lane `0.1.2149`); the operator chose regeneration; the regeneration wrote the untracked `.plan/execute-script.py`; the re-read answered **match** at `0.1.2149`.
- Branch prefix `feature/` from the canonical set; the spec is in `.plan/orchestrator/{slug}/plans/`; nothing under `.plan/orchestrator/` was edited (the base merges only brought back main's own ledger commits); the only `.plan` writes are `.plan/temp/`, the two `ci` body slots a `prepare-*` verb returned, and this landing.

## 8. Build server

- Setup preflight on the worktree: **ready** (`registered: true`). No `runtime_stamp_reason` was carried (`plugin_version` is `unknown` because the stamp publishes no version; the executor version was read).
- Every build ran routed: `mechanism: daemon_longpoll` on all of them; `in_process_fallback: 0`; no launch-fault retry. One `status: indeterminate` (`1783c662`) — the `-pl pm-runtime -am` run whose selector matched no test in one reactor module, re-issued in two steps (install `pm-core`, then test `pm-runtime`).
- One bound expiry: `verify -Ppre-commit` at the explicit 330 s bound (`9cc5738a`, `timeout_used_seconds: 330`, `timeout_source: explicit`); the split form's first part is the same gate canonical, so it was re-run once with `--timeout 900` and passed. No `killed` job.

## 9. Residue

- D1's "Remaining paragraph" clause and the plan-level "Remaining lists are in the specification" are **not met as written**: R05 r7 forbids the paragraph the spec asks for. The operator decision is the spec's own wording upstream; the remainder is stated in the package file and the ADR.
- § Worker Egress Proxy still names `build`/`project_script` `egress` values (`open`, ...) that the member cannot carry until their implementation. The text now says so explicitly, but a future plan must implement them (the member's `else` branch forbids a non-null value for those kinds today).
- The § Worker Egress Proxy bullet "What a worker went through" still describes `endpoints`, `profile_source` and `profile_digest` as worker-block members, which are unbuilt (pre-existing forward text; outside D1's three named bullets, left untouched).
- The package file `job-confinement-and-environment.adoc` stays and its row stays `open` (the enforcement is not done) — by design.
- Six broken links in the documentation sibling on anchors this repository's `origin/main` no longer has — pre-existing, outside this change.
- No `{plan}-base` worktree was created (the base-sha evidence was not needed: the red run was the edited block against the unchanged code), so nothing is owed there.
