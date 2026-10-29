# Landing Analysis: PLAN-104 — Main green and the landed plans' leftovers

epic: pm-mcp-core-m2-m4
workstream: WS-06
pr: #185 (merged as 71a1533805f2f336aa084c64bc8e22fdac36471a)

> Landing record for one shipped plan. Source: inbox message `rb-104-main-green-and-leftovers-001.md` (runbook lane,
> opencode, 2026-10-26T02:42:55Z), corroborated against git at `71a15338`, the PR and its checks through the `ci`
> abstraction, the SonarCloud public API (`pullRequest=185` and `branch=main`), the lane's progress file
> (`.plan/temp/rb-104-main-green-and-leftovers/progress.md`), the build logs it cites, and the code the deliverables name.
> The message is a lead; every fact below marked "checked" was read from git, the PR or a run of mine, not taken from it.

Standing rules: r1 and R01 to R15, R17, R18 named by the landing (R16 is not in the plan's list). R05 does not apply:
the plan deletes no implemented specification text.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 `capability_tests` finding source | shipped-as-specified | Checked: `FindingSourceKind.java:49` `CAPABILITY_TESTS("capability_tests")` after `DEPENDENCY_DELTA`; `FindingSourceKeyTest` changed (+2/−2); `core-findings.adoc` names the two server-side sources. The scan the spec asked for is reported, not re-run by me: nine of ten modules completed with exactly one failing test in the reactor, `pm-runtime` stalled at `LfsBatchClient` (the stall the memory note records); the final `verify` ran 7518 tests green. |
| D2 `skip-on-docs-only: false`, ADR, index line | shipped-as-specified | Checked: `.github/project.yml:27-31` carries the key with its comment; `doc/adr/009-no-build-skip-for-a-specification-only-pull-request.adoc` exists; `doc/adr/README.adoc:28` lists it. **The CI run set of #185 shows the effect:** a first run (`40257921610`) has `check-changes`, `build` and `sonar-build` SKIPPED, a second (`40257925422`) ran `build (25)`, `build (26)`, `sonar-build`, `quarkus-alignment` to success; the first is the pre-change workflow, the second the one the new setting drives. The setting reaches `main` with this merge, so the first specification-only pull request after it is the live proof. |
| D3 `MetricsStore` through `ScopeTransaction` | shipped-as-specified | Checked: `MetricsStore.append(ScopeTransaction, …)` (lines 79, 112, 174) adds the document as one replace target; class Javadoc says a write never commits on its own; ADR `core-store/044`; `MetricsStoreTest` +214 lines. |
| D4 `ChangeLedger` through `ScopeTransaction` | shipped-as-specified | Checked: `ChangeLedger.append(ScopeTransaction, …)` at line 61, torn tail and journal in the class Javadoc; `ChangeLedgerTest` +129 lines. |
| D5 stale revision refused | shipped-as-specified | Checked: `QualificationSettingsStore` checks the caller's revision, increments it, refuses with `StoreRefusal.Outcome.CONFLICT`; ADR `core-store/045`; `QualificationSettingsStoreTest` +70. Four unplanned files carry the new outcome (`QualificationSettingsCodec`, `StoreRefusal`, `StoreRefusalTest`, `VerifyNotResolvePipeline`, the last because a `switch` over the outcomes was left non-exhaustive). |
| D6 minors of the PLAN-31 review | shipped-as-specified | Checked: `QualificationSettings.java:63-65` refuses a role skipped twice; per-scope row refusal tests in `MetricsStoreTest`; the two named texts (`ChangeLedgerTest` class Javadoc, `QualificationSettingsStoreTest.bounds` display name) rewritten. |
| D7 Sonar fixes | **shipped in part, as the spec allowed** | Checked on the API: `branch=main` now reads **2** open issues — `java:S9428` `StoreDigest.java:97` and `java:S112` `MacSecurity.java:305`. `S5778` (`ProfileValidatorTest` hoists `ProfileRules.none()`) and `S9412` (`LockKeyTest` uses `List.sort`) are gone. The lane refuted the S9428 hypothesis: the `MessageDigest` is the incremental state of the builder, so a local variable would mean buffering every chunk or changing the API. The spec's "if clean" clause covers it. |
| D8 `pm-provider-git` branch coverage | shipped-as-specified | Checked in the logs: `maven-2026-10-26-001303.log` reports `branches covered ratio is 0.79, but expected minimum is 0.80`; `…-001932.log` (117 tests, no rule violation line) after `GitIdentityTest` (+4/−4) and the new `LinkedWorktreesTest` (4 tests, 73 lines). Tests only; no production file of `pm-providers` is in the diff. |
| D9 `harness_store_unreadable` named | shipped-as-specified | Checked: `store-schemas/11-enrolment-store.adoc:60` and `model-work/04-worker-launch.adoc:115` name it with the refusal fate. |

Whole diff, checked (`git diff e873fb29 71a15338`, first parent `e873fb29` = #184): **27 paths** (4 `A`, 23 `M`),
+921 / −190: 11 `src/main` and `src/test` Java under `pm-core` plus 3 under `pm-runtime` and `pm-providers`, 10 documents
(two ADRs, ADR 009, the index, two specification parts, `core-findings.adoc`, `core-store.adoc`, `runtime-job.adoc`), and
`.github/project.yml`. The landing states no count.

Surface delta (`inbox landing-check`, declared surface of `PLAN-104-main-green-and-leftovers.md`, 23 entries, against the 27
realized paths): `expansion_detected`. **5 undeclared:** `QualificationSettingsCodec.java`, `StoreRefusal.java`,
`StoreRefusalTest.java`, `VerifyNotResolvePipeline.java` (the four D5 forced; the landing names these four) and
`pm-runtime/doc/developer/runtime-job.adoc` (a self-review round-1 drift fix; the landing mentions it in its findings, not in
its "four extra files"). **3 declared and untouched:** `pm-core/doc/adr/core-findings/` (no ADR for a plain enum value, as the
PR body says), `StoreDigest.java` (S9428 left) and `FindingSchemaParityTest.java` (the test was right; the enum was wrong).
The spec is terminal; its declaration is not corrected.

Done-when, clause by clause:

1. **`main` green on the parity tests — met by the lane's gates, checked on CI.** The merge-head run is `success` over 30 checks
   (`ci checks status`, PR #185); the lane reports `verify` 7518 tests at `8426edc6`. I did not re-run the suite.
2. **The CI skip is off — met, checked** (D2 above). Proof is the next specification-only PR.
3. **PLAN-31's owed findings — met.** The three major ones (D3 to D5) and the minors (D6) are in the merged code and tests.
4. **Sonar (R18) — MET, checked.** `pullRequest=185`: gate `OK`, 0 open issues, new-code coverage 100.0, duplication 0.0, hotspots
   reviewed 100. The lane's own `step.sonar-roundtrip` facts are `n/a` (`sonar: not_configured`), and its text says the same
   numbers from the public API. The 11 new issues the lane found at `11f50875` were fixed in `8426edc6`.
5. **All five gates — reported green at the merge head** (`verify`, `verify -Ppre-commit`, `verify -Pcoverage`, the two
   integration suites); the `-Pcoverage` run is the first reported green on `pm-provider-git`. Not re-run by me.
6. **Links check (R17) — reported `BROKEN LINKS 0`** against the sibling at `origin/main` `e158165`; I did not repeat it.

## Metrics and Anomalies

- Tokens: unknown (opencode measured none). `plugin_version` is also `unknown`: the first landing for which
  `inbox landing-check` reports `missing_keys: [total_tokens, plugin_version]`.
- Model: `deepseek-v4.1-flash/high`, harness `opencode`, effort `high`. `steps` ends `self-review:clear` (three rounds, all fixed).
- Duration: not supplied; the PR's checks span 15273 s by the `ci` read, which includes the quota wait of the last review.
- Anomalies:
  - **The flat OpenCode install has no `plan-marshall` bundle root.** `resolve` failed until the operator chose a bundle-identity
    shim; the lane wrote the shim, a domain-bundle manifest shim and an `extension.py` symlink into
    `~/.config/opencode/skills/` (outside this repository, left in place so the lane stays runnable).
  - **Every build ran `in_process`** with the reason `build_server_client_path_missing_flat_layout` (the wrapper looks for
    `skills/build-server-client/…`; the flat install has `plan-marshall-build-server-client`). A new variant of the standing
    `executor_absent_from_worktree` class: fourteen lanes in a row did not use the routed build.
  - **Executor skew** at the start (`0.1.1996` vs `0.1.2010`); the operator proceeded.
  - The lane fast-forwarded the documentation sibling to its `origin/main` before the links check (sanctioned by the Done-when).
  - Two build timeouts (1785 s and 1200 s), both re-run; the final `verify` took 272 s.
  - Rule breaks the lane reported itself: one `ls` outside the repository, the three install writes above. The guard was
    `none on this harness (unenforced)`.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `11f50875` (2 inline, 1 outside-diff, 1 nitpick; both inline findings
  withdrawn after the lane's replies, the other two fixed); re-triggered after the last push and **refused on quota**;
  merged under the standing review authorisation. Sourcery (optional) had 4 inline findings, answered and resolved.
- CI/merge: checked, `ci pr view` reads `merged`, head `fix/104-main-green-and-leftovers`, merge commit
  `71a1533805f2f336aa084c64bc8e22fdac36471a`, first parent `e873fb29`; the lane reports a merge-group run `40259441305`
  success (not re-read).
- Sonar: read from the public API, PR gate OK, 0 open; `main` 2 open (above).
- Collision: PLAN-104 shared exactly one declared entry with PLAN-30 (`model-work/04-worker-launch.adoc`). A trial merge of
  `feature/plan-30-supervisor` into `main` (`git merge-tree`) is **clean**. PLAN-30's branch touches none of PLAN-104's code
  files, so the `StoreRefusal.Outcome.CONFLICT` addition reaches it as a plain merge. `declaration-currency` over the 27 realized
  paths: 102 specs compared, all `disagree` (declarations are package-level), 1 not evaluable (PLAN-102). Staged specs whose
  declared files the landing now changes: `QualificationSettingsStore` and `StoreRefusal` in 20 of them (PLAN-34, 42, 43, 45,
  48, 51 to 53, 55, 59, 61, 67, 68, 76, 77, 92, 99, 100), `VerifyNotResolvePipeline` in PLAN-46, 47, 57, 58, 62, 63,
  `FindingSourceKind` in PLAN-36 and 49, `store-schemas/11` in PLAN-97 and 98, `core-store.adoc` in PLAN-60. Nothing collided at
  run time; the staged specs that touch `StoreRefusal` now find a fifth outcome, `conflict`.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-104 --status shipped`
- [x] row `pr` stamped — `#185`
- [x] row `landing` stamped — `landings/PLAN-104.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-104-main-green-and-leftovers` (from the sender)
- [x] epic.md narrative reconciled: queue annotation for PLAN-104; the PLAN-31 post-merge, `FindingSchemaParityTest`-red and `pm-provider-git` coverage Open Defects closed; the Sonar defect updated (4 → 2); one new Open Defect (the `harnesses` decode lead); the `total_tokens` recurrence folded in (29th); the build-routing Watch extended
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **`main` is green and the skip is off.** The first specification-only pull request after #185 should show `build` running;
  if it shows `SKIPPED`, the setting was lost (memory note `docs-only-pr-skips-the-parity-tests`).
- **PLAN-30** merges `main` when it opens its pull request (clean trial merge today); no hand-off paste is owed.
- **`HarnessEnrolmentStore.decode` reads a missing, `null` or non-object top-level `harnesses` as an empty map** (CodeRabbit
  lead, corroborated in the code at `HarnessEnrolmentStore.java:229`); the schema requires the member. No staged spec owns it.
- **`StoreDigest:97` S9428** needs a design change of the builder; **`MacSecurity:305` S112** needs the SonarCloud write
  credential.
- **The OpenCode install carries two shims** the lane wrote; the next reinstall removes them.
- **`total_tokens=unknown`** (29th) and the new `plugin_version=unknown`: folded into the standing Open Defect.
