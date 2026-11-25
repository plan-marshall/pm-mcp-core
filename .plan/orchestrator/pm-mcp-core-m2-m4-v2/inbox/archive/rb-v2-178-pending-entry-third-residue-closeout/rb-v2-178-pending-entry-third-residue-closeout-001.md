envelope_version=1
sender_type=plan
sender_id=rb-v2-178-pending-entry-third-residue-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-24T01:07:57Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-178-pending-entry-third-residue-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#588
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=5
review_body_findings_handled=5
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

# PLAN-V2-178 — the third residue of the pending entry's closeout

Standing rules: r7, R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (R21 binds the orchestrator; R17 lifted 2026-11-23). Read at session start; no later revision of the file was read.

PR #588 merged through the merge queue as `5777d1c4d1c348da12d922e77ad7ea4ae377f5a7` (the second enqueue; the first entry left the queue without merging although its merge-group run succeeded — see *Residue*).

## 1. Deliverables

Spec `Deliverables` 1–3, the Done-when at spec line 48.

| Done-when | Verdict | Order | Test | Failing run | Commit |
|---|---|---|---|---|---|
| A test for D1 fails on the code before it | met | `test-first` | `PendingEntryRecordsSweepTest.refusedAuditHandOverReportsTheCommittedRemoval` and `faultBeforeTheCommitRollsBackTheStagedRemovals`, written before the fix and failing on it | both failed at `1ebb9615`, build log `707d44a72a0343428a9d0a3e0a80f9e6` (19 tests, 2 failures: the report named no staged removal) | `50d9f185` |
| A test for D2 fails on the code before it | met | `test-first` + `mutation-after` | `SourceManifestCodecTest` rule tests (`caseVariantPaths`, `unicodeCaseVariantPaths`, `normalisationVariantPaths`, `casedNormalisationVariantPaths`, `caseFoldWidthPaths`, `nestedPaths`, `onePathInTwoSources`) and the four stored-manifest twins | 9 failed at `50d9f185` (log `72c7a54e5598427ea731c1421dd7470d`); the `mutation-after` swap of the case message for the normalisation text failed exactly the four case-rule tests (log `d754f68d23044890a40304473b12c8c7`); the fold-width assertion's own `test-first` run is log `1308d86ca6f74732866195b551b9228d` | `64ae062f`, width in `9aa83f02` |
| The statements of D1 and D3 say what the code does | met | `none` | Javadoc (class, `run`, `remove`, `reach`, `move`, `verified`, `copyDecision`), ADR `runtime-start/016`, ADR `core-store/084` rules 1 and 3, the Runtime Start and Core Store guides, `store-schemas/12` read against the code after three self-review rounds and five review findings | — | `50d9f185`, `291609be`, `9aa83f02`, `e01f76d6`, `5a4f1741`, `5c4de0c4` |
| `verify` per module and `verify -Ppre-commit` green, rewrites reviewed and committed | met | `none` | full-reactor `verify` at `5c4de0c4`: 12,819 tests (log `257f1aa0e9b74ebbb9a8107d732403d3`); `verify -Ppre-commit` green with its rewrites reviewed and committed as `e60beafc` (log `f148496429fa4a1884aabe4cc8f58e5d`) | — | `e60beafc` |
| The open Sonar issues of the pull request are fixed or dismissed | met | `none` | two rounds of new-code findings fixed (`java:S1192`, `java:S1130`; then `java:S1141`, `java:S7467`, `java:S2583` — a reliability bug that failed the quality gate on `new_reliability_rating` — and `java:S3398`); measured read at `5c4de0c4`: 0 open | — | `d53622b2`, `36d9c94f`, `97b2a86c` |

Targeted `-Dtest` runs were used while iterating (the spec's Done-when allows them); the full `verify` runs above cover the whole reactor at the final head, and every pushed head carried a green run.

Per-head test evidence: `50d9f185` and each later head was verified through the full reactor runs recorded in the PR body (12,769 tests before the merge of `main`, 12,819 after); the final head `5c4de0c4` by log `257f1aa0e9b74ebbb9a8107d732403d3`, and CI green on it.

D1's second test names its seam: the staging loop faults on a tracked store made unreadable (`sources/sources.json` with no permissions) after a lower-sorted candidate was already staged, so the rollback is shown with nothing written; no seam exists for a fault *between* a complete staging and `commit()` from `pm-runtime` — `ScopeTransaction.StepHook` is package-private to `core.store`, and every pre-journal refusal of `commit()` is either unreachable under the sweep's lock or preempted by the verification — which the D1 report in the PR states.

## 2. Personas and skills

- Production code, tests, docs: `persona-implementer` loaded as the standard for all three deliverables (the spec's `Persona:` lines); `persona-code-reviewer` for the three self-review rounds and every PR comment; `persona-module-tester` and `persona-documenter` logic applied inside those steps (the spec names `implementer` for each deliverable).
- Declared skills (read before the work, as R20 asks): `pm-dev-java:java-core`, `java-null-safety`, `javadoc`, `junit-core`, `pm-dev-java-cui:cui-testing` for D1 and D2; the same plus `pm-documents:ref-asciidoc` for D3.
- `skills added: none` — no surface drift: the diff is the spec's Expected Surface (11 code/doc files plus `store-schemas/12`), and no module outside `pm-core`/`pm-runtime`/`doc` was touched.

## 3. Hypotheses

The spec's `Claim Labels`. OBSERVED lines re-read at the head and confirmed: `transaction.commit()` precedes the discard loop; the loop caught only `IllegalStateException` and `UncheckedIOException`; `requireDistinctPath` threw one text for every collision; `fold()` was unchanged.

- LEAD "`casedNormalisationVariantPaths` hits the second NFC step" — **refuted by reading** (`SourceManifestCodecTest.java:321-341`): `upper(NFC(U+0390))` and `upper(NFC(U+0399 U+0308 U+0301))` are the same three code points, so the case mapping unifies the pair before the final NFC; the second NFC is not what makes it equal. The pair is therefore the case rule, and the test now says so.
- LEAD "a commit can fail after its journal is durable in a way that reaches the catch" — **confirmed by reading** (`ScopeTransaction.java:363-378`): `committed = true` is set before staging, and an `IOException` from `apply`, `writeDigests` or the journal write leaves it as an `UncheckedIOException`; the D1 test reaches the same window through a refusing audit sink (`OwedAuditEvents.deliver` at `:372`).
- LEAD "the lane's mutation log ids" — a claim about this run, not the code; the ids are cited above.

## 4. Findings

Self-review (three rounds, `persona-code-reviewer`):

1. the case message claimed a case-insensitive filesystem for every fold-equal pair — false for the width the operator kept (`ß`/`ss`, `ı`/`i`, ligature/letters); the message now states the width and a test refuses `straße.md`/`strasse.md` — fixed in `9aa83f02`;
2. the reach was inferred from the journal alone, so a fault after a successful commit could read as rolled back; `Fault` now carries the entries whose directory remains and the reach — fixed in `9aa83f02`, reshaped into four states in `28d4c379`;
3. a stale `@return` and a mangled class-Javadoc sentence — fixed in `e01f76d6`;
4. a dirty worktree at a reviewed head — the Javadoc repair committed in `e01f76d6`;
5. stale socket comment, leaked-thread note, POSIX guard on the permission test, store-schemas run-on, ADR date — fixed in `9aa83f02`;
6. round 3's two cosmetic nits (paragraph wrap, `reach()`'s causal clause) — fixed in `5a4f1741`;
7. the double `pending_entry_removed` record per directory is pre-existing and intended by `PeriodicSweepKilledRuntimeIT`; recorded as residue (below), not fixed.

PR comments (CodeRabbit, 5 findings, all answered in their threads and resolved; dispositions as handed to `review-gate`):

- `finding: coderabbit inline inline-1 — 28d4c379` — the Javadoc claimed a guard the decision store does not have; the sentence now says `copyDecision` checks only existence (statement only, per the plan's no-behaviour-change constraint).
- `finding: coderabbit inline inline-2 — 28d4c379` — `createFifo` let `ProcessBuilder.start()` escape, so the test errored instead of skipping; it returns `false` now.
- `finding: coderabbit inline inline-3 — 97b2a86c` — a lock-release fault dropped the removal's state; the state moved outside the lock transaction and the attempt got its own method (`28d4c379`, reshaped in `97b2a86c` after the intermediate guard failed the quality gate as `java:S2583`).
- `finding: coderabbit inline inline-4 — 28d4c379` — a missing journal was read as "nothing committed", although `ScopeTransaction.close` deletes the journal after applying its targets; the four reaches replace the journal-only inference.
- `finding: coderabbit inline inline-5 — 5c4de0c4` — the guide kept the old three-state list beside the four-state one; removed.

CI: two red runs, both Sonar's quality gate on new-code findings (`java:S1141`/`java:S7467`; then the reliability bug `java:S2583`); both fixed and the gate green on `97b2a86c` onwards. No other check failed.

## 5. Reviewers

- `coderabbit` (required, `quota_limited`, `review_complete_after_handling`): first trigger at 2026-11-23T21:36Z was refused on quota ("Review limit reached"; the command reply "Review rate limited") with no qualifying review on record. Per the quota policy the run waited the full 90 minutes — `ci pr wait-for-comments` in three calls (63 s, 3,761 s, 125 s) — and re-triggered at 23:08Z: "Review triggered", reviewed `d53622b2` (marker `final_review_risk_coverage` names `d53622b2`, `kind=reviewed`) with 4 inline findings. After the merge of `main` it reviewed `e8083464` again (marker names it) with 1 more finding. All 5 handled; no re-trigger was posted for `97b2a86c`/`e8083464`/`5c4de0c4` because the reviewer is `review_complete_after_handling` and holds a qualifying review whose findings are all handled; no escalated trigger was needed.
- `sourcery` (optional): triggered at 21:36Z, refused with a hard quota ("2 days and 16 hours"), silent otherwise. Optional, so it never gated the merge; no further trigger was posted because the window is days long.

Standing review authorization was not used: the qualifying review and the handled findings carried the merge, and `review-gate` reported `merge_allowed: true` on the merge head with `--required-reviewer coderabbit`.

## 6. Operator questions

none asked. No free-text operator messages arrived during the run.

## 7. Rule check

- `guard: none on this harness (unenforced)`; no guard log exists, so nothing is claimed as witnessed.
- Spec read: `spec_source: ledger_worktree` (the shared ledger worktree's copy), and no `spec_differ` marker — the main checkout's copy either does not exist or is equal.
- `executor-skew`: verdict `match` (executor `0.1.2183`, lane `0.1.2183`), read before `resolve`.
- Deviations and errors of this run, stated rather than smoothed over:
  - the quality gate was run as an ad-hoc `verify -Ppre-commit` invocation (no `--canonical`) because the plan's Done-when names that profile and the resolved gate canonical is plain `verify`; the gate canonical itself was also run (`quality-gate`, `verify`).
  - `verify -Ppre-commit` after the merge of `main` proposed rewrites in three files that `main` brought in (`FreshnessReconcile.java`, `FreshnessReconcileTest.java`, `CommitPathRefusalsTest.java`): a broken indentation and two `throws IOException` widened to `Exception`. They were reviewed and **not** committed — that content is not this plan's and the rewrite is a regression — and were restored with file edits, never `git checkout --`. Reported as residue below.
  - one `git worktree remove` was run for this plan's own worktree after the merge (the lane's cleanup step), and the git-workflow `worktree-remove` verb was not used, as the lane requires.
  - two shell calls failed on typos of my own (`tools-integration-cn:ci`, `--mode structured`, a `--format` flag the verb lacks); no rule was bent by them.
  - one sub-agent was spawned without the reviewer's session context (`ses_df5b36117ffeJgcnFP4fB3MGDC`) and its report was not used for the self-review state; the three review rounds all ran on `ses_df5c90331ffeYd6Y3fAxFtWWDK`.
  - two build results came back `running` and were re-attached with the same call (jobs `f148496429fa4a1884aabe4cc8f58e5d`, `71eebec5326e40848feadd3877233b14`); no build was backgrounded.
  - no `git grep`, no shell file-reading command, no `sleep`, no inline Python; `.plan/temp/` held the progress and landing files, and the `ci` body slots came from `prepare-*` verbs.

## 8. Build server

- setup preflight: `ready` (registered, version 1); the daemon never went down and no build needed the `in_process` fallback.
- mechanism: `daemon_longpoll` for every build of the run; `in_process_fallback`: 0. No `degraded` result, no launch fault, no `killed` job, no bound expiry.
- The run's builds: 12 targeted `-Dtest` module-tests runs while iterating (4 of them the deliberate failing runs/ mutations above), and 9 full `verify`/`verify -Ppre-commit` runs, the last at `5c4de0c4` (12,819 tests, log `257f1aa0e9b74ebbb9a8107d732403d3`); plus the Sonar-quality-gate build the queue ran on the merge group.
- `runtime_stamp_reason`: none — the resolve result carried none, and `plugin_version` reads `unknown` from the seam.

## 9. Residue

- One directory's residue can carry two `pending_entry_removed` records after a recovery — the replayed one and the next run's own — each under its own `transaction_id`, so a reader that drops duplicates by that key cannot collapse them. Pre-existing, documented as intended by `PeriodicSweepKilledRuntimeIT`; `PendingEntryRecordsSweepTest` now asserts it, and this plan did not change it.
- `verify -Ppre-commit` wants to rewrite the three `main`-side files named in the rule check; that belongs to `main`'s content, not to this plan.
- Two of the sweep's reaches cannot be produced by any test available here: `UNDECIDED` (a journal write failing before its rename) and `COMMITTED` (a fault outside the discard's two refusals). Both are stated as such in the Javadoc, the guide and ADR `runtime-start/016`.
- The plan touched `pm-core/doc/developer/core-store.adoc`, which PLAN-V2-176 (`#589`) also edited; the merge auto-merged cleanly and the paragraph was rewrapped in `e8083464` — the analysis should read that region for shape, not content change.
- Not reported by this plan, seen in the log: the `ent-` prefix shadowing, the creation cross-commit window and the live-plan enumerator remain as PLAN-V2-174 recorded them.
