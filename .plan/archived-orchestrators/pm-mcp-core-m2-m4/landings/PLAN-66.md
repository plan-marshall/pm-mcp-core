# Landing Analysis: PLAN-66 — Worker activity (partial: the package stays open)

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #198 (merged as 3385af53e4bb5254a0791c3f6acae83737bbac67)

> Landing record for one plan that shipped **partially**. Source: inbox message `rb-66-worker-activity-001.md` (runbook lane,
> opencode, 2026-10-27T01:54:11Z), corroborated against git at `3385af53`, the PR and its checks through the `ci` abstraction,
> the SonarCloud public API and the code. The message is a lead; every fact marked "checked" was read by me; no test was re-run.

Standing rules: r1, R01 to R14, R16 to R18 named by the landing. **R01 (the package file and its row removed) and R05 are not
applied, on the operator's answers to the lane** (partial pull request with the package open; specification and watch sections
left in place). The lane read the **ledger worktree copy** of the spec (`spec_source: ledger_worktree`, `spec_differ: true`: the
main-checkout copy lacked R17, the build and links clauses and the runbook hand-off, because #195 was not landed); the newer copy
was applied, so the lane worked from the adapted spec, including the supervision-loop question.

## Deliverable Fidelity vs Spec

Nine deliverables (the package file's nine bullets). The lane counts 1 of 9 done and says so per row; I agree with its reading.

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 the umbrella (`PM-WORK-7`) | not fully met | depends on D2 to D9 and the assembly. |
| D2 the events mapping per harness version | **not met as written**: no recorded output of a shipped profile exists in this repository (the profiles live in `pm-api`) | Checked: `ActivityEventMapper`, `ActivityEventMapping` (`core/queue`), `WorkerActivityPump` (`runtime/worker`) with `ActivityEventMapperTest` and `WorkerActivityPumpTest`, over a fixture written for the test. |
| D3 the journal: masked, bounded, archived with its plan, served | not fully met | Checked: `SecretRedactor`, masking at the journal's write boundary (ADR `core-queue/008`), `ActivityJournal.archiveInto`/`delete` exist and are tested; **nothing calls them** (the plan cleanup is another plan's). |
| D4 the off-track signals, thresholds, actions | not fully met | Checked: `OffTrackDetector`, `ActivityRecorder`, `ActivitySignal`, `WorkerTaskActivity` in `runtime/worker`; signals are evaluated inside the appending transaction (ADR `runtime-worker/010`). The plan-task boundary is the attempt's (the journal carries no plan-task id); `no_progress`'s link producer is another surface's. |
| D5 `agent_started` in the journal and `signals[]` | met | Already built by #160 and #190; this PR adds the journal evidence (`TaskProtocolReOfferTest.agentStartedJournalled`). |
| D6 served from a cursor and as a live stream; links in the process | not fully met | Checked: `ActivityService`, `ActivityStream`, `ActivityPage`, `ActivityLinks` in `pm-runtime/.../web/` (ADR `runtime-web/001`); the HTTP routes and the process resource are `pm-mcp-server`'s. |
| D7 the `events` stage | not fully met | Checked: `HarnessEventVerification` in `runtime/launch` with its test; writing the result into `VerificationRecord` / `HarnessEnrolment` is owed. |
| D8 the navigation figures | not fully met | Checked: `NavigationFigures`, `Figure`, `NavigationVocabulary` with `NavigationFiguresTest`; the completion report's Tokens section is `operator-reports`' file set. |
| D9 the skill residency | not fully met | Checked: `SkillResidency`; the in-band bytes are caller-supplied (the delivery record holds digests only). |

**Whole diff, checked** (`git diff 3385af53^1 3385af53`, first parent `9251596f` = #196): **47 paths: 34 `A`, 13 `M`;
+4,062/−4.** 20 main files (the activity mapping and journal in `core/queue`, six types in `runtime/web` and `runtime/worker`,
`HarnessEventVerification`), 15 test files, ADRs `core-queue/007` to `009`, `runtime-launch/006`, `runtime-web/001`,
`runtime-worker/010` and `011`, `doc/Traceability.adoc` (+72). **No specification, watch or package-file change.**

**Surface delta** (`inbox landing-check`): `expansion_detected`. **7 undeclared**, all test files (`ActivityEventMappingTest`,
`ActivityJournalMaskingTest`, `ActivityJournalTest`, `NavigationFiguresTest`, `SecretRedactorTest`, `HarnessEventVerificationTest`,
`TaskProtocolReOfferTest`: the tool compares test directories by exact path). **20 declared and untouched:** the package row and file
(operator choice), the watch and specification parts (operator choice), the ADR and developer directories `core-profile` and
`workflow-task`, and the directories `core/profile`, `core/role`, `core/store`, `core/profile` tests, `runtime/job`,
`workflow/task`. The surface was honest about the real code location after the cleanup of 2026-10-26 (`core/queue`).

## Metrics and Anomalies

- **Checked CI:** `success` over 30 checks on #198. **Checked Sonar:** `pullRequest=198` gate `OK`, new coverage 83.1,
  duplication 0.0, **0 open issues**; `branch=main` unchanged at 2.
- **The operator's answer to the loop question was "Ingress + supervision loop"; the loop was not built.** The lane built
  `WorkerActivityPump` (a step over the job log) and `ActivityRecorder`; ADR `runtime-worker/011` rejects a thread ("the supervisor's
  loop has no production caller yet, and a thread would outlive the caller that owns the worker's lifetime; the pump is a step the loop
  already needs"), and the lane's own residue lists "the production supervision loop that drives `WorkerActivityPump` and
  `ActivityRecorder`" as owed to `pm-mcp-server`. Checked: nothing in `src/main` references the pump or the recorder outside their file
  (grep), and no class named for a loop exists. **The answer was either not delivered or narrowed by the lane; the message does not say
  which.**
- **A partial plan:** 8 of 9 deliverables not fully met, the package row and file in place, the specification and watch not reduced.
  The package `worker-activity` stays `open` in the index, so `supervision-scenarios` and `operator-reports` stay blocked on it.
- **Build:** the daemon's routed job failed at spawn in this lane (`execution_failed`, exit −1), every build ran in process
  (`in_process_fallback`); the full `verify` timed out at the learned 1075 s after 3374 tests; the lane used the split form. This
  contradicts the other lane's report of the same day (PLAN-34: `daemon_longpoll`); both are the lanes' reports.
- **`total_tokens=unknown`** (32nd); `plugin_version=unknown`; executor `0.1.2017`.
- **Rule breaks the lane lists:** one `git grep`, one shell `grep -n`, two `ls` calls, a `tail` and a `date`/`command -v`
  diagnostic; R17's links check ran `trace.py` directly as R17 prescribes. **Links check:** 5 broken anchors, all in the sibling
  documentation (`PM-WATCH-CLIENT-13`, `PM-WATCH-DISP-33`, `-43`): the same deleted watch ids #190 removed here, still linked there.
- **Review:** self-review round 1 found 8 blockers and majors (a rename committed with incomplete staging so HEAD did not compile,
  signal evaluation outside the transaction, an unredacted entry returned), round 2 clear; CodeRabbit two rounds all fixed, Sonar
  17 → 0; `sourcery` refused the diff size; CodeRabbit refused on quota four times.

## Routing and Merge Behavior

Personas as the message states (implementer, module-tester, security-expert for redaction, documenter, code-reviewer). Merge: one
squash commit through the merge queue, `merge_group` run success; worktree and branch pruned. Operator questions: executor skew
("Proceed with 0.1.2017"), the supervision loop ("Ingress + supervision loop"), package lifecycle ("Partial PR, package open"),
specification sections ("Leave all in place").

## Collision Analysis

`corpus declaration-currency` over the 47-path footprint, PLAN-66's own spec excluded: **19 staged, parked and running specs share
files beyond the index**, the code ones PLAN-35, 46, 47, 51, 55, 57, 58, 59, 60, 61, 62, 63, 67, 73, 76, 77, 97, 98 (through
`core/queue`, `runtime/web`, `runtime/worker`, `runtime/launch` and the new ADRs) and PLAN-49 on `doc/Traceability.adoc`. **PLAN-42
(running) shares nothing.** The PLAN-42 trial merge conflicts only in `doc/plans/README.adoc` and `doc/plans/remediation-units.adoc`
(see `landings/PLAN-34.md`). ADR numbers taken: `core-queue` 007 to 009 (next 010), `runtime-launch` 006 (next 007), `runtime-web`
001 (next 002), `runtime-worker` 010 and 011 (next 012).

## Reconciliation Actions

- Row PLAN-66: `running` → `shipped`; `pr` `#198`; `landing` `landings/PLAN-66.md`; `plan_marshall_plan_id` `rb-66-worker-activity`. The
  status `shipped` records the pull request that merged, **not the package**: the package row stays open in the index.
- `epic.md`: the queue annotation; a new Open Defect for the remainder of `worker-activity`; the supervision-loop Open Defect gets the
  operator's answer and the fact that the loop is still unbuilt.

## Follow-Ups

1. **The remainder of the package** (an operator decision: a follow-up plan or a re-emit of PLAN-66's remainder): the supervision loop
   that drives the supervisor, `WorkerActivityPump` and `ActivityRecorder` (in this repository, or `pm-mcp-server`), the `events` stage
   written into the verification record and the enrolment, the plan-task boundary for `over_size` and `outside_footprint`, the plan
   cleanup adopting `archiveInto`/`delete`, the server `link`/`question`/`answer_ack` entry producers; the HTTP routes and process
   leaf links are `pm-mcp-server`'s; a recorded-output fixture per shipped harness version needs the profiles of `pm-api`.
2. The completion report's Tokens section: `operator-reports` (PLAN-58).
3. The documentation repository still links the deleted watch ids (5 broken anchors there).
4. Known limits recorded in the ADRs: `repeating` cannot reset on a context compaction (no compaction entry in the closed kind list),
   `languageServerUse` counts every `pm_lsp` call, `fallbackToReadRate` uses the lookup's call target as the answered file.
