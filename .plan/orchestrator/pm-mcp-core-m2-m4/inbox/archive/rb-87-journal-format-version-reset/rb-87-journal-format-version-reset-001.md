envelope_version=1
sender_type=plan
sender_id=rb-87-journal-format-version-reset
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-23T15:49:02Z

```landing-facts
schema=landing-facts/1
plan_id=rb-87-journal-format-version-reset
epic=pm-mcp-core-m2-m4
pr=#148
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.1989
executor_version=0.1.1989
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

Standing rules: r1, R01–R18. The spec was updated by the orchestrator during the run: it began with R01–R15 and R18, and the base merge brought R16 and R17 into its rule list and an R17 links-check clause into the Done-when. Both revisions were obeyed; the later one binds.

## 1. Deliverables

| Deliverable | Done-when verdict | Evidence |
|---|---|---|
| D1 — the constant and the document | **met** | `TransactionJournal.FORMAT_VERSION = 1` and the one-shape Javadoc (`TransactionJournal.java:48-78`); reader refuses another version through `StrictJson` (`format_outdated`/`format_newer`). Test `TransactionJournalTest.writtenFormatVersion`, `order: test-first`, failed at `3cc1585` (log `maven-2026-10-23-061656.log`, output: `expected: <true> but was: <false>`), passed at `05d3c33`; commit `05d3c33` |
| D2 — no reader of the old versions | **met** | `TransactionJournalTest.anotherVersionOutdated` (`order: test-first`, failed at `3cc1585` with `expected: <1> but was: <3>`, passed at `8a9e99d`); the six existing named files carry no journal-version literal; commit `8a9e99d`. The spec's `ScopeSessionLogTest` no longer exists (deleted by PLAN-64) — see Residue |
| D3 — the documents | **met** | `core-store.adoc`, ADR 007 and `store-schemas/01-format-and-session-log.adoc` state one version; commit `605febf` (+ ADR link in `dd94c61`) |
| D4 — the same sweep | **met** | The sweep is in the PR body: 28 other `FORMAT_VERSION` constants and every schema `const` are 1; every remaining non-1 value is a refusal fixture; the orchestrator-owned ledger history is listed. No failing-output order applies (`none`) |
| D5 — the 29 Sonar issues | **met** | All 29 fixed in code, none suppressed, reported one per row in the PR body; PR Sonar read `total: 0`, gate `OK`. `order: none` (the issue list is the evidence). Commits `86aa180`, `dd94c61`, `7987ae9` |

Per-head runs against every pushed head: `verify -Ppre-commit`, `verify`, `verify -Pcoverage`, `verify -Pintegration-tests` at `86aa180`, `dd94c61`, `7987ae9`, `ddff25e`, `fa09d68`, `ba17f9e` — all green. At the final head `ba17f9e`: 7160 / 7160 / 7160 / 2286 tests. The project publishes no `module-tests`/`test-compile` canonical, so the gate canonicals are the per-head runs.

## 2. Personas and skills

- D1, D4, D5 production code: `persona-implementer`.
- D2, D5 tests: `persona-module-tester`.
- D3, D4 documents: `persona-documenter`.
- Every self-review round: `persona-code-reviewer`.
- Domain skills: `skills: none named` — the spec declares no `Skills:` line. No surface drift into a module the Expected Surface did not name (the four extra files are the S1172 ripple inside already-named modules).

## 3. Hypotheses

- `FORMAT_VERSION` is 3 and the Javadoc says v1/v2 are refused — **confirmed**, `pm-core/src/main/java/de/planmarshall/core/store/TransactionJournal.java:48-54,77` at `3cc1585`.
- The Deliverable-2 test list is complete — **refuted**: `ScopeSessionLogTest` no longer exists (deleted by PLAN-64, `landings/PLAN-64.md:63`); the other six files exist.
- `store-schemas/01-format-and-session-log.adoc` is the only specification part showing the journal version — **confirmed** for `doc/specification` (`01-format-and-session-log.adoc:29`); the developer guide also carried it (`core-store.adoc:215,1024`).

## 4. Findings

- **Self-review round 1** (no code defect): fixed the ADR 007 PR link and the two new-code Sonar issues (S1117, S5778) introduced by the first round of fixes.
- **Self-review round 2**: fixed the links-check gap (R17 — `BROKEN LINKS 0`), the stale PR body, and the D4 count; left the ledger item open as out of the write boundary.
- **Self-review round 3**: **CLEAR on code**. Open and non-blocking: F-1, the ADR's "stays 1" wording (low, documentation-only); F-6, the orchestrator ledger's historical records (outside the boundary). No code defect remains.
- **CI**: all checks green on every pushed head. One stale Maven Build run on `86aa180` had failed only because Sonar refused a non-main branch before the PR existed; it was re-run and is green.
- **PR comments**: no actionable CodeRabbit or Sonar findings. The only bot comments are rate-limit notices and Sonar's 0-new-issues/gate-passed report. Nothing to fix or answer.
- **Sourcery** is an optional bot and refused on its review budget; it never gated the merge.

## 5. Reviewers

| Bot | Class | Result |
|---|---|---|
| CodeRabbit | required, quota-limited | Reviewed `ddff25e` (12:41Z), `fa09d68` (13:45Z) and `ba17f9e` (automatically, confirmed by `no_unreviewed_commit` at 15:11Z): "No actionable comments were generated", merge risk Minimal, no inline threads. Quota history: refused 07:42Z, 09:22Z, 10:54Z (escalated trigger); 90-minute waits observed; reviewed each head once quota allowed. No finding needed handling. |
| Sourcery | optional, quota-limited | Refused 07:43Z (own review budget, 1 day 14 hours). Optional, never a merge gate. |

## 6. Operator questions

- Asked: "After merging origin/main (5 new commits…), the project's `verify -Ppre-commit` rewrote 10 files that this plan does not touch — all formatting-only… How should I proceed?" Options: *Commit the 10 rewrites* / *Stop and report blocked* / *Discard the rewrites (rule bend)*. **Selected: "Commit the 10 rewrites"**, committed as `fa09d68`. No other question was asked. No free-text operator messages.

## 7. Rule check

- `guard: none on this harness (unenforced)` — no guard log line exists to read, and none is claimed.
- R17 (links check) ran exactly as the rule prescribes, with the absolute sibling path outside this repository; this is the one deliberate exception to the executor-only rule, and R17 is its authority.
- The installed `runbook-lane` executor lacks the documented `sonar-gate`/`sonar-facts` verbs and the installed Sonar executor lacks `read_open_issues`. The Sonar decisions were therefore taken from the documented contract using `sonar_rest` and the public issue/gate API (R18 names that API as the authority). No source was changed to silence the surface error.
- No other rule was bent.

## 8. Build server

- Setup preflight: `ready`, project registered.
- The worktree carried no `.plan/execute-script.py` (`executor_absent_from_worktree`), so every build ran with `--execution-mode in_process`; `mechanism: in_process_fallback` on every result.
- `verify -Pcoverage` at `ba17f9e` hit two bound expiries (`timeout_used_seconds` 860, source `learned`; then 1386, source `explicit`) with no failures; it was re-run with `--timeout 2400` and passed (1005 s).
- No `killed` job. No `runtime_stamp_reason`; the runtime stamp resolved.

## 9. Residue

- **Orchestrator ledger**: `.plan/orchestrator/pm-mcp-core-m2-m4/epic.md` still records the Open Defect as `FORMAT_VERSION = 3` (and the plan spec and historical PLAN-06 inbox message state the old values). The plan's Write-Boundary forbids editing them; the orchestrator closes the Open Defect at landing. This is the one Done-when clause ("no file of the repository…") that is not literally met.
- **ADR 007 wording**: decision item 5 says `FORMAT_VERSION` "stays 1" while the Consequences record that PLAN-06 raised it to 2 and PLAN-12 to 3 and this plan resets it. Low, documentation-only; a one-line follow-up if wanted.
- **`StalledHold.kind()`** was removed in favour of `StalledHold.KIND` (S3400's prescribed fix). No in-repo consumer remains; the `pm-mcp-server` assembly in `plan-marshall-mcp` is outside this repository and should be checked at the next SNAPSHOT.
- **Stale spec clause**: Deliverable 2 still names the deleted `ScopeSessionLogTest`.
- **`sonar: not_configured` in resolve** reflects the absent provider credential, not the project: the public API read of PR #148 returned 0 open issues and a green gate, and the landing facts carry that measured 0.
- The PR was merged as a squash commit `63819763083b4553027452e3164f5006ac41c07d`, so the per-head shas (`ba17f9e` and earlier) are not in `main`'s history; the content is identical.
