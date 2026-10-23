# Landing Analysis: PLAN-94 — clear the SonarCloud issues of `main` that no plan owns

epic: pm-mcp-core-m2-m4
workstream: WS-06
pr: #147 — https://github.com/plan-marshall/pm-mcp-core/pull/147 — merge commit `9ebee040c14732db3624c3acf9d105a15d5bc972`

> Landing record for one shipped plan. Written by the `analyze` verb after verifying claims
> against ground truth (the merged diff, the CI and merge-queue state, the SonarCloud API at the
> merge head and on `main`). The inbox message `rb-94-sonar-main-backlog-001.md` was the lead;
> every claim below was checked, and the three that did not hold are named as such.

## Deliverable Fidelity vs Spec

The spec stamps no "fails at `{sha}`" clause on any Done-when, so no verification order applies.
Corroborated against `9ebee04` (`75 files, +1628/−649`, single parent `7881eea`), the CI checks of
PR #147 (30 checks, `overall_status: success`), and the SonarCloud API on 2026-10-23:

- `branch=main&resolved=false` → **33** open issues (the plan started from 293).
- `pullRequest=147&resolved=false` → **0** open issues.
- `branch=main&rules=java:S4032` → **0** — the rule is gone from `main`, not only from the PR analysis.

| Deliverable (spec) | Verdict | Evidence |
|---|---|---|
| D1 unnamed patterns (`S7467`×15, `S7466`, `S7475`) | shipped-as-specified | no issue remains in any of the eight named files; the four `S7467` left on `main` sit in `StalledScopeDetectorTest` (×3) and `PortedFixtures`, outside D1's scope |
| D2 lambdas that can throw twice (`S5778`×13) | shipped-as-specified | the seven `S5778` left on `main` are one in `ProfileValidatorTest` (not in D2's list, see D9) and six in `StalledScopeDetectorTest`/`CredentialStoreMoveResumeTest` (both from later landings); no `S5778` remains in `ToonEncoderTest`, `FileSecretStoreTest`, `JobRecoveryStepTest` or `WorkflowStatusCodecTest` |
| D3 loops and conditionals (`S9391`×7, `S9358`×2, `S135`) | shipped-as-specified | no `S9391`/`S135` remains in the named files; the two `S9358` and the one `S8786` left are in `StalledScopeDetectorTest` and `CredentialStoreMoveRecordParityTest`, both outside D3 |
| D4 regex and record patterns (`S6353`×4, `S6878`×4) | shipped-as-specified | no `S6353`/`S6878` on `main`; `ToonEncoder`, `GitOperations` and `GlabCommands` are clean |
| D5 names and test hygiene (`S6213`×3, `S1130`×2, `S2925`, `S1481`, `S1854`) | shipped-as-specified | none of the named files carries a remaining issue; `Thread.sleep` replaced by a condition wait — `PoolLanguageClient.awaitDiagnostics` exists at `pm-runtime/.../lsp/PoolLanguageClient.java:61` and `ADR runtime-lsp/001` is added |
| D6 design smells (`S127`×2, `S3776`×3, `S112`, `S2386`×2, `S1075`×2, `S8491`) | **shipped-modified — better than the landing reported** | `S1075` fixed: `DbusConnection.BUS_PATH = '/' + BUS.replace('.', '/')` (line 39). `S2386`×2 are **gone from `main`**, not owed: the two `PosixModes` sets became `Set.copyOf(...)`, which is immutable and satisfies the rule — the landing's residue claimed they remained. Only `S112` remains (`MacSecurity.java:305`, `T call() throws Throwable`), reported for dismissal |
| D7 the 21 `S4032` ignore | shipped-as-specified, measured on `main` | `pom.xml:67-69` carries the three `sonar.issue.ignore.multicriteria.e1.*` properties; `doc/adr/008-ignore-s4032-for-package-info.adoc` is added; `main` lists 0 `S4032`, and no package was removed or annotated |
| D8 re-list and report | shipped-modified | the re-list is corroborated: `main` 293 → 33, and every issue of the 31-item post-spec list matches component+line except that `ProfileValidatorTest:414` is a **32nd** item the list does not name. No behaviour change is corroborated structurally (the diff is smells, documentation and tests) but the test/build figures are the lane's report, not re-run |
| D9 the 176 `core.profile` issues | **partially met** | `LoadedProfileTest` and the `core-profile/005` ADR are added, the two `S6218` are gone, and `S1192` in `core.profile` is gone. `main` still lists **one** `S5778` at `ProfileValidatorTest:414` — a `core.profile` file the plan extended (it hoisted `fixture.bytes()` out of the lambda at 413/415) — so the Done-when "no issue in the files of the package `core.profile` other than one reported as dismissed" is not satisfied. The `new_coverage` 87.6 % vs 80 is the lane's report; the gate check `SonarCloud Code Analysis` passed |

## Metrics and Anomalies

- Tokens: **`unknown`** — the opencode run measured none. `inbox landing-check` reports
  `complete: false`, `missing_keys: [total_tokens]`: the seventeenth opencode runbook landing with
  the standing property (folded into the epic's Open Defect).
- Duration: the lane did not report wall time; the CI elapsed figure is 5030 s over 30 checks.
- Anomalies:
  - **The merge-group run id differs**: the message says `39957777599`, `ci pr queue-state` reports
    the newest merge-group run as `39957777525`, `conclusion: success`. Immaterial, recorded as a
    transcription error in the landing narrative.
  - The quality gate was killed twice by the kernel OOM killer (28/30 GiB, 7.9 GiB swap) and every
    build ran in-process (`executor_absent_from_worktree`), the fourth lane in a row to meet it.
    Reported by the lane; consistent with the epic's watch.
  - `self-review: budget-exhausted` with the suppression finding still open when the round limit
    was reached; the operator answered question 3 ("fix what can be fixed").
  - One flaky local failure, `LspClientTest.stubborn` (a 2 s `initialize` timeout under load);
    green in CI.
  - **The pre-commit rewrite carried 10 files of `#146`/`#150` code into the PR** (verified: exactly
    the 10 `workflow.model`/`core.role` test files, +23/−16, formatting and `assertInstanceOf`).
    None of them is declared by the spec and none is a collision with PLAN-87: the realized set
    shares no file with PLAN-87's declared surface.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed `d768ceb`, `a9d7fa3` (3 findings) and `680ad8c`; every actionable
  finding is fixed and every thread resolved, and it refused on quota five times. Sourcery refused
  every attempt — the diff exceeds its 150,000-character limit — and does not gate. Merged under the
  standing review authorization; no fresh-head review after the last push.
- CI/merge: `overall_status: success` over 30 checks, merge queue run `39957777525` success, squash
  merge `9ebee04`. No rebase conflict, no re-verify signal.
- Sonar: the landing's `step.sonar-roundtrip.open_issue_count` is `n/a` (the `sonar` credential is
  not configured, so `sonar-gate` answered `not_configured`). This analysis read the API directly:
  PR #147 = 0, `main` = 33, `S4032` = 0. The lane's own claim of a clean run is corroborated at the
  PR, not merely asserted.
- Surface: declared 41 entries; realized 75 files; **20 undeclared** (`surface_delta`
  `expansion_detected`). Ten are the pre-commit rewrite above — no scope of the plan. The other ten
  are in-scope but undeclared: `doc/adr/008` and its index, the `core-profile` guide, the
  `runtime-lsp` guide and ADR, the three module `README`s, `PoolLanguageClient`, and the
  `DbusConnectionTest`/`SecretServiceStoreTest` extensions. The declaration under-stated D5–D7's
  documentation half, the same class of under-declaration the epic records elsewhere.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-94 --status shipped`
- [x] row `pr` stamped `#147` — `orchestrator queue --set-row PLAN-94 --field pr --value #147`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-94 --field landing --value landings/PLAN-94.md`
- [x] row `plan_marshall_plan_id` stamped `rb-94-sonar-main-backlog` (the sender's plan id) — `orchestrator queue --set-row PLAN-94 --field plan_marshall_plan_id --value rb-94-sonar-main-backlog`
- [x] epic.md narrative reconciled: the Sonar backlog watch retired and replaced by the residue; the
      `total_tokens` defect folded; the pre-commit-rewrite collision recorded
- [x] inbox message archived on consume — `orchestrator inbox archive`
- [x] resume anchor updated; `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **One SonarCloud dismissal is owed** for `java:S112` (`MacSecurity.java:305`) — it cannot be fixed
  without changing the FFM shape — and **one unreported issue is to settle** at
  `ProfileValidatorTest:414` (`S5778`). Both belong to whoever holds the SonarCloud write credential;
  neither is a plan of this epic.
- The 31 issues that appeared since the spec (plus the one above) sit in files no staged plan owns.
  They are not folded into a plan; the operator decides if any becomes one.
- `PLAN-87` (running) still owns the 29 issues of the `#120`/`#121`/`#122` set; `#147` touched none
  of its files.
- The pre-commit rewrite of already-merged code keeps widening a later PR's footprint; the epic's
  existing watch (`verify -Ppre-commit` is not idempotent) covers it and is unchanged.
