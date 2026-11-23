envelope_version=1
sender_type=plan
sender_id=rb-v2-173-cut-log-residue-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-23T14:00:25Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-173-cut-log-residue-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#580
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
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-173 — the residue of the cut build log's closeout

Merged as #580, merge commit `8537af7627b71c83655c80af1742e2d057c1ed99`.

## 1. Deliverables

| Done-when | Verdict | Order | Test | Failing run | Commit |
|---|---|---|---|---|---|
| D1 1(a) no second failure for a per-class line its filed summary repeats | met | test-first | `BuildLogTextTest.cutMavenLogFilesNoSecondFailureForThePerClassLineItsSummaryRepeats` | `2be5eca8`, output `9e2fe1d99f8c4303b44492553da9dd51` | `3720712c` |
| D1 1(b) each execution's failures kept when one has no summary | met | test-first | `BuildLogTextTest.cutMavenLogKeepsTheFailuresOfAnExecutionWithoutSummary` | `2be5eca8`, output `9e2fe1d99f8c4303b44492553da9dd51` | `3720712c` |
| D1 1(c) the duplicated error count line subtracted once | met | test-first | `BuildLogTextTest.cutMavenLogSubtractsADuplicatedErrorCountLineOnce` | `2be5eca8`, output `9e2fe1d99f8c4303b44492553da9dd51` | `3720712c` |
| D1 an identical line across the `Results:` marker counted once | met | test-first | `BuildLogTextTest.cutMavenLogCountsTheSameLineOnceAcrossTheMarker` | `3c923b59`, output `70c6f4deef7548cfb67fcadce08072de` | `42e8bc08` |
| D1 no wrap and no over-subtraction above `Integer.MAX_VALUE` / warning-first duplicate | met | test-first | `BuildLogTextTest.cutMavenLogSaturatesAnOverflowingFailingCount`, `.cutMavenLogKeepsAnErrorForAWarningFirstDuplicate` | `65bc8e5f`, output `3a26c0df1f0a4de886aa1c15bfcfa163` | `5d606cb5` |
| D2(a) the precedence statement states the implemented rule and its bounded error | met | none (prose) | — | — | `7c5f7099`, `3c923b59` |
| D2(b) the population leaves the dispatcher | met | mutation-after | `BuildOutputParserTest.parseResultCarriesThePopulation` | mutation at `7c5f7099`, output `e5817568835d4437b08fed19fbf5ccc7` | `7c5f7099` |
| D2(c) "filed as one error diagnostic" corrected at all three sites | met | none (prose) | — | — | `7c5f7099` |
| D2(d) one remainder in the singular | met | test-first | `BuildLogTextTest.cutMavenLogNamesOneRemainingFailureInTheSingular` | `2be5eca8`, output `9e2fe1d99f8c4303b44492553da9dd51` | `3720712c` |
| D3 the guard no longer passes by construction | met | mutation-after | `ProcessTreeTrackerTest.dependsOnNoGroupSignal` (tracker; nested record; nested interface) | `63b98a013c3143b3974e49e04e74d77b`, `4180e8d3506d482b92a06664cb541577`, `37079fe3cd324638a0cf48b183956296` | `a1ff8dad`, `3c923b59` |
| D4(b) the two-write open stated in the guide | met | none (prose) | — | — | `a1ff8dad` |
| D4(a) the `JobLog` Javadoc | split off (see §9), as the spec's standing constraint instructs | — | — | — | — |

`deliverables_done=4`: deliverable 4 is met as the spec defines it — (b) delivered here, (a) left for PLAN-V2-162 because
the spec's standing constraint forbids the conflict while PR #567 is open (verified open at 2026-11-23, editing
`pm-runtime/src/main/java/de/planmarshall/runtime/job/JobLog.java`). The split-off is the carried item's recorded outcome.

Per-head test runs: `pm-core` 5 693 tests (`bd7e8e959a6f4823b49aa0b5a94aa13b`), `pm-workflow` 8 806
(`7c534ce0918f4bfbb676d759c592694f`), `pm-runtime` 8 431 (`b2bef4f7446a46f6a6dd856a6c53612f`), all at `a1ff8dad`;
`package` (module-tests) 12 739 at `7168506c` (`5ed94ad98f5340dca08cb923cc5c28fb`); `verify` 12 740 at `65bc8e5f`
(`b8d948dbda0f4d048b843613f86f85e7`), 12 742 at `5d606cb5` (`d7b34d511e494bda99d70a2f80d726d2`), 12 756 at the two
base merges (`791952064e3348a58518e6a2b4f69761`, `2df6baebcb054494807a393142c3356b`).

## 2. Personas and skills

- Production code (D1, D2b): `persona-implementer`.
- Tests (D1, D3): `persona-module-tester`.
- Prose corrections (D2a/c/d, D4b): `persona-documenter`.
- Every self-review round and the PR-comment triage: `persona-code-reviewer`.
- Domain skills: the spec's First Instruction names `java-core`, `java-null-safety`, `javadoc`, `junit-core` and
  `cui-testing`; all five were read at the head before any work (R20). The resolve result carried no per-deliverable
  `Skills:`/`Persona:` lines (`skills: none named`).
- `skills added: none` — no surface drift beyond the spec's Expected Surface plus the seven test files it implies.

## 3. Hypotheses

- 91a (LEAD): the three constructed tails of 1(a)/(b) reproduce as stated — **confirmed** by reading
  `MavenOutputParser.unfiledFailures()` (the two side-specific subtractions and `filed()`) at `2be5eca8` and by the
  four red tests in `9e2fe1d99f8c4303b44492553da9dd51`.
- 91b (LEAD): Gradle, pyproject, Jest and TAP build a `TestCounts` from the tail and `ParseResult` carries none —
  **confirmed** (`BuildOutputParser.parse` dispatch, `JobEvidence.ParseResult`'s four components at `2be5eca8`).
- 91c (OBSERVED, re-read): the guard's self-reference — **confirmed** (`ProcessTreeTrackerTest` imported
  `ProcessGroupSignals.class` into the rule's import set at `2be5eca8`).
- 91d(b) (LEAD): the two-transaction open — **confirmed** (`ConsultationService.open`: one `updateAll` records the entry and
  `CONSULTING`, `queue.create` is the second write; `reconcile`/`isOrphan` settle it after the grace).

## 4. Findings

- Self-review round 1 (3 rounds allowed + 1 operator-authorised confirmation): a medium (the guard's subject omitted
  `Descendants`/`Starts` while claiming every nested type), a medium (the new population test had no red run), six low
  items. All fixed in `3c923b59` and `65bc8e5f`; the confirmation round reported nothing open; the one remaining cosmetic
  `@param` was fixed after it (prose only).
- Self-review round 2 found a **reachable double report** (an identical count line before and after the `Results:` marker
  was one diagnostic but two figures) and a surviving over-generalisation; both fixed in `42e8bc08`/`64575607`, the
  double report test-first (`70c6f4deef7548cfb67fcadce08072de`).
- PR #580, CodeRabbit: `inline-1` (unchecked `long` addition could wrap the cut tail's failing sum to zero) and
  `outside-diff-1` (a warning-first duplicate was still subtracted, losing the error-level contradiction). Both real,
  reproduced test-first (`3a26c0df1f0a4de886aa1c15bfcfa163`) and fixed in `5d606cb5`; the inline thread was answered and
  resolved. Dispositions: `coderabbit inline inline-1 — 5d606cb5`, `coderabbit outside-diff outside-diff-1 — 5d606cb5`.
- PR #580, Sourcery (optional): approved at `565038c8`; its approval was dismissed by the later push, no findings.
- Build-gate findings: the module's assertion-message scanner refused a 66-character message (`88a6da2b…`), fixed in
  `64575607`; the pre-commit formatter re-indented the nested record header, committed as `7168506c`.

## 5. Reviewers

- `coderabbit` (required, `quota_limited`, `review_complete_after_handling`): reviewed `65bc8e5f` (COMMENTED, marker
  `kind=reviewed` covering `65bc8e5f`). Two findings, both handled (`5d606cb5`). It was not re-triggered for the later heads
  — the done exemption — and its own check on the last pushes answered a rate-limit notice ("next included review in 34
  minutes"), recorded in the progress file. The merge rests on the done exemption: qualifying review at `65bc8e5f`,
  findings handled, refusal recorded, every other condition green on `8bfa2054`.
- `sourcery` (optional): refused on its review budget at 13:00, then reviewed and **approved** `565038c8`; the approval was
  dismissed by the following push (it does not gate).

## 6. Operator questions

1. "Deliverable 1 (ask-at-refine, R07): how should the cut tail's visible failing count be computed?" — options A
   (per-execution, else dedup max, recommended), B (global dedup max), C (minimal subtraction fix); **selected A**.
2. "Deliverable 2(b) (ask-at-refine, R07): `JobEvidence.ParseResult` has no `TestCounts` field…" — options A
   (Javadoc + ADR only, recommended), B (also carry `TestCounts` in `ParseResult`); **selected B**.
3. "The runbook lane allows 3 self-review rounds; round 3 closed every earlier finding and left one nit, which I have now
   fixed…" — options (run a 4th confirmation round, recommended / proceed without); **selected "Run a 4th confirmation
   round"**.

Free-text operator messages: none.

## 7. Rule check

- `guard: none on this harness (unenforced)` — no tool-call guard was installed for OpenCode, so nothing was enforced and
  no guard log exists for this run; `.plan/temp/guard.log` is not read for a count (the resolve result reports `guard: none`).
- The run's own actions that the check counts: the shell file operations were `git` only (status, diff, log, ls-tree,
  rev-parse, fetch, merge, push, worktree remove) — no shell `cat`/`grep`/`find`/`ls` file reads; every build ran through
  the `build` map; every CI action through `ci` or a resolved provider verb. One exception, named as the rule's project
  requirement: the R17 links check ran `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/
  scripts/trace.py …`, the command the standing rule R17 prescribes, whose output went to `.plan/temp/linkcheck-173.txt`.
- Failures of my own: two compile errors in iterations (a missed constructor arity, a broken `@FunctionalInterface` in a
  mutation probe), one 66-character assertion message (the scanner caught it), and one test assertion written with a wrong
  expected substring, corrected after its first green attempt (`ca79432b03e64ef1815cc31debd94628`). Each cost a build and
  none touched a delivered file.
- Restarts and re-dispatches: one server restart during the CI wait on `8bfa2054`; the lane text was re-read after it and
  the wait re-issued (no work re-run). Four self-review sub-agent dispatches (rounds 1-3 and the confirmation round); none
  was killed, and no work was re-dispatched.
- Spec read: `spec_source: ledger_worktree`; no `spec_differ` marker was reported.
- `executor-skew`: `match` (executor `0.1.2183`, lane `0.1.2183` at setup; the landing stamp reads the executor's
  `0.1.2184`). `spec_source` and the skew verdict are the spec read's own facts, recorded here as the rule asks.

## 8. Build server

Setup preflight: `ready`, enrolled. Every submitted build answered `resolved=routed`, `mechanism=daemon_longpoll` (33
builds in the run, including the red runs and the two mutation runs); `in_process_fallback: 0`, launch-fault retries: 0.
One bound expiry: `verify -Ppre-commit --timeout 330` (job `73b0953f1a634f91898c0876aca18eec`, `timeout_used_seconds`
330, `timeout_source: explicit`); the split form then ran `verify -Ppre-commit -DskipTests` (clean, no rewrites) and
`package` without re-submitting the same bound. The pre-commit profile rewrote `MavenOutputParser`'s nested record-header
indentation once (`f27acd70803a4a1db08fd293e673b728`), reviewed and committed as `7168506c`; every later pre-commit run was
clean. No `killed` job. No `runtime_stamp_reason` was carried; `plugin_version` is `unknown` (the runtime-info seam read
none).

## 9. Residue

- **Carried item 91d(a)** (the `JobLog` Javadoc overclaiming a shrink-and-regrow) is **split off to PLAN-V2-162**, whose
  PR #567 is open and edits that file; the spec's standing constraint says to leave it rather than conflict. If #567 merges
  without it, the Javadoc still overclaims at `JobLog.java:221-224,246-249`.
- **Owed to plan-marshall-mcp**: `JobEvidence.ParseResult` gains a fifth component, so the assembly's constructions adapt
  after the `SNAPSHOT` deploy (R16(2), ADR `runtime-job/015`).
- **Merge-risk note**: PR #567 also edits `pm-runtime/doc/developer/runtime-job.adoc`, which this plan edits too; the guide
  merged cleanly here, but the rebase of #567 will need the same two paragraphs kept.
- **Links check**: `BROKEN LINKS 3252` by tool root, `INLINE SCHEMA BLOCKS 0` — the operator's pre-existing backlog into
  `plan-marshall-documentation`, reported separately from the one local link this plan introduced and fixed.
- The finding count is 2/2 handled on the merge head; no review thread remains open.
