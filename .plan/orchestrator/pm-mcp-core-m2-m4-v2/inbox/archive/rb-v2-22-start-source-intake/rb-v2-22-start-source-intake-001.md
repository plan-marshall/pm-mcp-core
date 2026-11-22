envelope_version=1
sender_type=plan
sender_id=rb-v2-22-start-source-intake
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-22T14:28:11Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-22-start-source-intake
epic=pm-mcp-core-m2-m4-v2
pr=#560
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2183
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=21
review_body_findings_handled=21
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-22 (v2-22-start-source-intake), pm-mcp-core slice — landed as #560

Merge commit `969669cda3cd471e10f9b0e3d8745cce0a09ce08`. Operator scope: deliverables 3, 4, 5 (store-level move),
12, 13; the `plan-marshall-mcp` parts are reported not met below. Standing rules read: r7, R01-R20.

## Deliverables

| Done-when | Verdict | Test | Order | Failing run | Commit |
|---|---|---|---|---|---|
| an entry is opened as its directory, created in one transaction | met | `PendingEntryStoreTest.created` | mutation-after | `8af69b5d`, log `b1516c0ebcae4bc1976d53954f11b272` | `e6aa76b6` |
| a tampered entry pauses only that entry until accepted/abandoned | **not fully met** (store-level only: `verify` names the entry and its store; no hold raiser in this repository) | `PendingEntryStoreTest.tamperedEntryAlone`, `untrackedIsTampered` | none | — | `e6aa76b6`, `94ee3695` |
| each stored source has its manifest entry | met | `PendingEntryStoreTest.sourcesAdded`, `PendingEntryCreationTest.refusesADifferingSource` | none | — | `e6aa76b6`, `21dd4e76` |
| creation moves sources, transcript and decision stores, directory last | met for the store-level move (the `.pmwf` units are `plan-marshall-mcp`) | `PendingEntryCreationTest.movesIntoTheNewScope`, `refusesARetryAfterTheTargetCommitted` | mutation-after | `f383cf81^`, log `e8eda285d3ca4f27b9e494e6638ce249` | `21dd4e76`, `f383cf81`, `3fd4a8fe` |
| an entry older than a day is still offered; no sweep removes it | met | `PendingEntryRecordsSweepTest.openEntryKept`, `tamperedRecordKept` | mutation-after | `8af69b5d`, log `af68590eaf1740b186c0cb902f02f0cf` | `47012293`, `dacd6c30` |
| the cut-off removal's directory is removed by the sweep | met | `PendingEntryRecordsSweepTest.cutOffRemovalSwept`, `unparsableNamesUntouched` | none | — | `47012293`, `dacd6c30` |
| `PendingEntrySchemaParityTest` + `AuditSchemaParityTest.enums` without `@Disabled` | met | both tests | none | — | `e6aa76b6` |
| Watch PM-WATCH-STORE-42 | met for this repository's slice | the tests above | none | — | `e6aa76b6`, `47012293` |
| D13 (reader + entry address) | met | `PendingEntryDirectoryReaderTest.live`, `QuestionLogRecordReaderTest.readsAnEntryScope` | mutation-after / none | `8af69b5d`, log `b9e7f63e7fd841aaae4034479dfb06ad` | `8af69b5d` |
| Sonar: no open issue, gate green | met | read `0` confirmed at the merge head | — | — | — |

Per-head test runs (module tests, every pushed head verified by CI's full `verify`; local `package` green at `8af69b5d`,
log `2243169908924cb985662e98a5f05a5d`): CI green at `dbe06a29`, `a05e5356`, `dacd6c30`, `4296704c`, `09d5fa06`.

## Personas and skills

- production code: `persona-implementer`; tests: `persona-module-tester`; self-review and PR triage:
  `persona-code-reviewer`; specs/guides/ADRs: `persona-documenter` behaviour under the implementer load (no explicit
  persona line in the spec).
- The spec names no `Skills:`/`Persona:` lines: `skills: none named`; no skill was added for surface drift (the
  changed modules are the ones the spec names).
- R20 read and applied (generators, assertion messages, `@NullMarked`, builder rule, no hand-written fakes).

## Hypotheses

- D3 (a 24 h maximum age exists in code): confirmed at `a958d75d`.
- D4 (flat record, `ofFileName`, raw `source`, schema `recipe` drift): confirmed at `a958d75d`.
- D5 (`moveEntryQuestions` exists; no manifest, no request composition): confirmed at `a958d75d`.
- D13 (no entry address, no runtime entry reader): confirmed at `a958d75d`.

## Findings

Self-review rounds: 1 (5 HIGH/MEDIUM, fixed in `94ee3695`), 2 (one normative contradiction + small items, fixed in
`3fd4a8fe`), 3 (no HIGH; two findings fixed in `3559494b` after the limit, not re-reviewed → `budget-exhausted`).
CodeRabbit at `dbe06a29`: 19 inline comments + one outside-diff + one nitpick = 21 findings, all handled in
`dacd6c30`/`4296704c` or answered in the PR comment; 14 threads resolved (one with an in-thread reply, 13 against the
general reply — a deviation from the per-thread rule, recorded below). Sourcery refused the PR (over its 150,000
character diff limit); optional and not gating. Sonar: 19 issues on `3559494b` fixed iteratively; 1 more on `dacd6c30`
fixed; `0` at the merge head, gate `OK`.

## Reviewers

- `coderabbit` (required, quota-limited, review_complete_after_handling): reviewed `dbe06a29` (a head of this PR),
  risk "Moderate", 21 findings — all handled; re-triggered after the fixes (rate-limited: "next included review in
  ~42 minutes"), no further review was needed because its review stands for later heads of the same PR and every
  finding is handled.
- `sourcery` (optional): refused for size at `3559494b`, no review.

## Operator questions

1. "PLAN-V2-22's binding re-ground record says the plan stays held for its split per repository; how should this run
   proceed?" — options: run the pm-mcp-core slice / run all five and decide open designs / hold / split first;
   selected **Run the pm-mcp-core slice**.
2. "self-review is at the 3-round limit with O2/O6 open and N1/N2 fixed after it; how should the run continue?" —
   options: proceed to the PR / one more round / stop; selected **Proceed to the PR**.

No free-text operator messages were given.

## Rule check

- **guard: none on this harness (unenforced)**, so no guard-log line exists and no count is taken from one.
- Breaks of the run's own: (1) one test-file edit was made through a shell heredoc driving inline Python (the codec
  test's recipe fixture) — the shell/file rules forbid both; (2) one `git add -A .` staged the worktree instead of
  naming paths; (3) 13 review threads were resolved against one general PR reply instead of a reply in each thread
  (the reply names `dacd6c30` for every finding); (4) the links check could not report `BROKEN LINKS 0` from the
  worktree (3253 pre-existing sibling links into `plan-marshall-documentation` that cannot resolve from a worktree
  three levels deep; no link this plan adds is among them, checked by grep of the report); (5) the final local `verify`
  ran at `3559494b`; later heads rely on CI's full `verify` (build (25)/(27) and the Sonar build step), green at every
  head.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ`. `executor-skew: match` (0.1.2183 / 0.1.2183).

## Build server

Setup preflight `ready`; every build `mechanism=daemon_longpoll`, requested `auto`; `fallbacks: none`; no `killed`
job; no `timeout` bound expiry.

## Residue

- The entry's hold has no producer here (a `pm-mcp-server` class); the store-level verification and re-baseline are
  here.
- `PeriodicSweepKilledRuntimeIT` models an open entry with a fixture `.keep` sibling, not with `entry.json`.
- Kept with a reason from the review: the manifest codec's `MEMBERS` lists (repository codec convention) and the
  log-record inventory sentence in `runtime-start.adoc` (authority is the generated, byte-checked listing).
- `grants.jsonl` of the layout is the `access-grants` package's; the spec now states that.
- The `.pmwf` units', the entry routes' and the client hold's plan-marshall-mcp parts remain for that repository.
