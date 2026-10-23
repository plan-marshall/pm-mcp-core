# Landing Analysis: PLAN-87 — reset the transaction journal to format version 1

epic: pm-mcp-core-m2-m4
workstream: WS-02
pr: #148 — https://github.com/plan-marshall/pm-mcp-core/pull/148 — merge commit `63819763083b4553027452e3164f5006ac41c07d`

> Landing record for one shipped plan. Written by the `analyze` verb after verifying claims against
> ground truth (the merged diff, CI and merge-queue state, the SonarCloud API, the code at the merge
> commit). The inbox message `rb-87-journal-format-version-reset-001.md` was the lead; the claims
> below were checked and two of them were refined.

## Deliverable Fidelity vs Spec

Corroborated against `6381976` (single parent `9ebee04`, 23 files, `+127/−97`), the PR state, the
SonarCloud API on 2026-10-23, and the code at the merge commit.

| Deliverable (spec) | Verdict | Evidence |
|---|---|---|
| D1 the constant and the document | shipped-as-specified | `TransactionJournal.FORMAT_VERSION = 1` and the one-shape Javadoc (`TransactionJournal.java:78`, the class Javadoc states the `format_version: 1` document); `TransactionJournalTest.writtenFormatVersion` (line 134) exists; refusal of another version flows through `StrictJson` (`format_outdated`/`format_newer`), asserted by `TransactionJournalTest.anotherVersionOutdated` (line 377) |
| D2 no reader of the old versions | shipped-as-specified | `anotherVersionOutdated` present; the five declared-but-untouched test files (`JournalRecoveryTest`, `ScopeTransactionTest`, `PendingEntryStoreTest`, `ScopeIntegrityTest`, `EnrolmentStoreTest`) carry **no** old-version literal (grep: 0 each), so they needed no edit — the message's "six named files" is right about content, not about edits. `ScopeSessionLogTest` no longer exists (deleted by PLAN-64) — the spec clause naming it is stale |
| D3 the documents | shipped-as-specified | `core-store.adoc:215` (`format_version` 1, the only version), ADR `core-store/007` item 5 plus its Consequences, `store-schemas/01-format-and-session-log.adoc:29` ("the journal in `format_version` 1 and every other version refused") |
| D4 the same sweep | shipped-as-specified | every `FORMAT_VERSION` constant in `*.java` at `6381976` reads `1` (28 constants across `pm-core`, `pm-runtime`, `pm-workflow`, plus the test and derived constants) — measured by grep, not taken from the PR body |
| D5 the 29 Sonar issues | shipped-as-specified | measured: `pullRequest=148` → **0** open issues; `branch=main` → **4** (was 33 before the merge, 293 at the start of the PLAN-94 run). The 29 of the `#120`/`#121`/`#122` set are gone; the 4 left are the two PLAN-94 residue items (`ProfileValidatorTest:414`, `MacSecurity:305`) and two the epic has never owned (`StoreDigest:97`, `LockKeyTest:79`) |

**Done-when caveat, resolved here:** the only clause not literally met was "no file of the repository
still records the old version" — the orchestrator ledger's Open Defect (and the spec's own
starting-state claims) still said `FORMAT_VERSION = 3`, and the plan's write boundary forbade it from
editing them. The orchestrator closed that record in this landing (see Reconciliation Actions).

## Metrics and Anomalies

- Tokens: **`unknown`** — `inbox landing-check` reports `complete: false`, `missing_keys: [total_tokens]`;
  the eighteenth opencode runbook landing with the standing property.
- Duration: not reported by the lane; CI elapsed is the measurable figure.
- Anomalies:
  - **The pre-commit rewrite recurred at a new scale**: after merging `origin/main` (5 commits) the
    gate rewrote 10 files the plan does not touch; the operator chose "Commit the 10 rewrites"
    (`fa09d68`). They are inside the 23-file squash and cannot be isolated from it, but the footprint
    is fully accounted for: 19 of the 23 files are the declared surface and the other 4 are the
    `S1172` ripple the message names.
  - `executor_absent_from_worktree` again (`in_process_fallback` on every build) — the fifth lane in a
    row; `verify -Pcoverage` hit two learned/explicit bounds (860 s, 1386 s) with no failures and
    passed at 2400 s.
  - The installed Sonar executors lack `sonar-gate`/`sonar-facts` and `read_open_issues`, so the lane
    read the public API directly under R18. No source was changed to silence the surface error.
  - **The orchestrator's mid-run spec adaptation DID reach the lane this time** (R16/R17 and the R17
    links-check clause in the Done-when), and the lane obeyed the later revision. That is the first
    run since the land-before-emit rule was written; it closes the loop on the earlier "a spec edited
    in the ledger worktree is not what a lane reads" defect.
  - `self-review: budget-exhausted` after three rounds, the third **CLEAR on code**; two cosmetic
    findings left open.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed `ddff25e`, `fa09d68` and `ba17f9e` — "No actionable comments were
  generated", merge risk Minimal, no inline threads; it refused on quota three times before. Sourcery
  is optional and refused on its own budget. No review finding needed handling, so nothing was
  dismissed.
- CI/merge: `overall_status: success`; merge-queue run `39979399520` success; squash merge
  `6381976`. No rebase conflict, no re-verify signal.
- Sonar: the landing's `step.sonar-roundtrip.open_issue_count` is `0` with `open_issue_rules: none`
  — corroborated at the PR (`0`) and on `main` (`33 → 4`, the 29 gone). Not a claimed zero: measured.
- Surface: declared 24 entries; realized 23 files; **4 undeclared**, exactly the `S1172` ripple the
  message names (`ExecutableCheck`, `VerifyNotResolvePipeline`, `ExecutableCheckTest`,
  `WrapperVerificationTest` — the callers and tests of the removed `WrapperVerification.projectId`).
  No file outside the named modules.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-87 --status shipped`
- [x] row `pr` stamped `#148` — `orchestrator queue --set-row PLAN-87 --field pr --value #148`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-87 --field landing --value landings/PLAN-87.md`
- [x] row `plan_marshall_plan_id` stamped `rb-87-journal-format-version-reset` — `orchestrator queue --set-row`
- [x] **Open Defect "Format version bumped although nothing is released" CLOSED** in `epic.md`: the
      constant, its Javadoc, the guide, the ADR and the specification now state 1, and every other
      `FORMAT_VERSION` in the repository is 1
- [x] Sonar watch updated: `main` reads 4, not 33 — PLAN-87's 29 are cleared, `#120`/`#121`/`#122`'s
      set is gone
- [x] inbox message archived on consume
- [x] resume anchor updated; `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **`StalledHold.kind()` → `StalledHold.KIND`** (S3400's fix). No in-repo consumer remains, and a grep
  of the `plan-marshall-mcp` checkout finds no `StalledHold` reference at all; re-check at the next
  SNAPSHOT deployment. Recorded as a Watch, not a defect.
- **Stale spec clause**: PLAN-87's Deliverable 2 still names the deleted `ScopeSessionLogTest`. The
  spec is shipped and frozen; `cleanup` may correct it. No plan work.
- **ADR 007 wording (F-1 in the lane's report):** judged NOT a defect here — item 5 states the
  decision ("stays 1", no bump, no shim) and the Consequences record the earlier bumps by `PLAN-06`
  and `PLAN-12` and this plan's reset. The two read coherently; no follow-up.
- The 4 remaining Sonar issues are already the subject of the Open Defect from the PLAN-94 landing.
