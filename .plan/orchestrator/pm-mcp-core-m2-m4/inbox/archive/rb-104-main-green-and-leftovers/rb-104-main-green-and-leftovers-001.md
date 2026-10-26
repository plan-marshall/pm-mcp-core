envelope_version=1
sender_type=plan
sender_id=rb-104-main-green-and-leftovers
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-26T02:42:55Z

```landing-facts
schema=landing-facts/1
plan_id=rb-104-main-green-and-leftovers
epic=pm-mcp-core-m2-m4
pr=#185
merge_state=merged
cleanup_owed=false
deliverables_total=9
deliverables_done=9
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2010
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

# Landing — PLAN-104 main green and the landed plans' leftovers

Standing rules: **r1**, R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18
(the plan's list; R16 is not named by it).

Spec read: `main_checkout` (no ledger copy; `spec_differ` not reported).

## 1. Deliverables

Each row is one deliverable; the Done-when is met as written unless stated. Tests are in the head that merged
(`8426edc6`, merged as `71a15338`). `none` means no failing run was captured, not that a test was seen failing.

| D | Done-when | Verdict | Order | Test / evidence | Failing run |
|---|---|---|---|---|---|
| D1 | `CAPABILITY_TESTS` present and the parity tests green; other reds listed | met | `test-first` | `FindingSchemaParityTest.enumerationsEqualSchema`, `FindingSourceKeyTest.wireNames`; enum at `FindingSourceKind.java:49`; commit `004242ca` | branch point `e873fb29`, log `maven-2026-10-25-223049.log` (5 tests, 1 failure) |
| D2 | `skip-on-docs-only: false`, its comment, the ADR and the index line | met | `none` | `.github/project.yml:27-31`, `doc/adr/009-…adoc`, `doc/adr/README.adoc:28`; commit `986c5b18` | — |
| D3 | metrics written through `ScopeTransaction` with its tests | met | `none` | `MetricsStoreTest.sidecarEntry`, `outOfBandEdit`, `otherScope`; ADR `core-store/044`; commit `a181e46f` | — |
| D4 | the ledger written through `ScopeTransaction` with its tests | met | `none` | `ChangeLedgerTest.sidecarEntry`, `outOfBandEdit`, `otherScope`; ADR `core-store/044`; commit `a181e46f` | — |
| D5 | stale revision refused, current accepted and incremented | met | `none` | `QualificationSettingsStoreTest.staleRevision`, `writeReplacesOnlyTheBlock`; ADR `core-store/045`; commit `a181e46f`, `a2e1e80e`, `4d2d3020` | — |
| D6(a) | a row outside the store's scope is refused, with a test per scope | met | `none` | `MetricsStoreTest.epicTaskRow`, `workspaceTaskRow`, `workerIdleRow`, `workspaceIdleRow` | — |
| D6(b) | a role skipped twice is refused, with a test | met | `none` | `QualificationSettingsTest.bounds`; `QualificationSettings.java:63-65` | — |
| D6(c) | the two PLAN-31 texts named and corrected | met | `none` | `ChangeLedgerTest` class Javadoc ("each bound to the working-tree digest" vs its own `unavailable()` test) and `QualificationSettingsStoreTest.bounds` `@DisplayName` ("a level outside 0 to 100" vs the role and gap assertions) — both rewritten | — |
| D7 | the three fixable Sonar issues gone; `S112` reported open | met with `S9428` left per its own clause | `none` | `S5778` (`ProfileValidatorTest` hoists `ProfileRules.none()`), `S9412` (`LockKeyTest` uses `List.sort`); `S9428` deliberately left — the `Builder`'s `MessageDigest` cannot become local without buffering chunks (breaking its bounded-buffer contract) or changing the API; `S112` untouched and reported open | — |
| D8 | coverage measured, ≥ 0.80, tests only | met | `none` | `pm-provider-git` branch **79.74% → 80.39%**, line 89.74% unchanged; tests `GitIdentityTest` (null arms), `LinkedWorktreesTest`; logs `maven-2026-10-26-001303.log` → `…-001932.log` | — |
| D9 | `harness_store_unreadable` named in both specification parts | met | `none` | `model-work/04-worker-launch.adoc:114-117`, `store-schemas/11-enrolment-store.adoc:60`; commit `abd86fb7` | — |

**Per-head test runs** (the project publishes no `module-tests`/`test-compile` canonical; the gate canonicals are
`quality-gate`/`verify`): `verify -Ppre-commit` and `verify` green at `11f50875` and again at `8426edc6`; targeted
suites green at each pushed head (`004242ca`, `986c5b18`, `a181e46f`, `2ae5c1b2`, `a2e1e80e`, `0d530c65`, `abd86fb7`,
`7fda2047`, `4d2d3020`, `11f50875`, `8426edc6`).

**All five Done-when gates are green at the merge head:** `verify` (7518 tests), `verify -Ppre-commit` (7518),
`verify -Pcoverage` (7518), `verify -Pintegration-tests -pl pm-core -am` (13), `verify -Pintegration-tests -pl pm-runtime
-am` (30). Links check (R17): `BROKEN LINKS 0` against the sibling at its `origin/main` (`e158165`).

**Sonar (R18):** the lane's own state is `not_configured`, so the two facts above are `n/a`; the required public read
was made instead: `pullRequest=185` → **0 open issues**, quality gate **OK** (new coverage 100%, duplication 0%, no
security hotspots).

## 2. Personas and skills

- `persona-implementer` for the production changes (D1, D3–D6), `persona-module-tester` for the unit tests, and
  `persona-code-reviewer` for the self-review rounds and the triage of every PR comment.
- The spec names **no** `Skills:`/`Persona:` lines: `skills: none named` for every deliverable.
- Surface drift: none — the four extra files (`QualificationSettingsCodec`, `StoreRefusal`, `StoreRefusalTest`,
  `VerifyNotResolvePipeline`) are the forced spread of D5's new `conflict` outcome, reported in the PR body, and no
  additional domain skill was needed.

## 3. Hypotheses

- "the full build has no other red than `FindingSchemaParityTest`" — **confirmed**: the branch-point run with failures
  ignored held exactly one failing test before `pm-runtime` stalled at the network-bound `LfsBatchClient`; the final
  `verify` ran 7518 tests green, `LfsBatchClient` included.
- "`pm-provider-git` is under 0.80 branch coverage" — **confirmed**: 79.74% (`maven-2026-10-26-001303.log`), lifted to
  80.39% with tests only.
- "`StoreDigest.Builder` can drop its `MessageDigest` field without changing the digest or the API" — **refuted**: the
  field is the incremental state across `update(...)` calls; removing it means buffering every chunk (changing the
  bounded-buffer behaviour) or changing the builder API. Left as it is, with the reason in the PR body.
- "the Expected Surface is inferred, not from a trace matrix" — not verified by a trace matrix (the lane does not build
  one); the declared surface held, with the four D5-forced additions above.

## 4. Findings

- **Self-review round 1:** 1 unused local, 1 false sentence in `core-findings.adoc`, 1 `runtime-job.adoc` drift, 1
  factory Javadoc gap, 1 `null` in a refusal message; all fixed (`bb2052c9`). The duplicate `Snapshot` stayed open.
- **Round 2:** the duplicate `Snapshot` (closed in `4d2d3020` by making the codec's record package-private and keeping
  the store's public one) and a false `ChangeLedgerTest` class Javadoc (fixed, and reported as D6(c)).
- **Round 3:** one low finding — a public codec method returning a package-private type — closed in `11f50875`.
- **Sonar on the PR:** 11 new issues at `11f50875` (`S1130`×2, `S1075`×2, `S1192`, `S5778`×4, `S1117`), all fixed in
  `8426edc6`; the re-read at `8426edc6` is 0 open.
- **PR comments:** 6 inline threads (Sourcery 4, CodeRabbit 2) answered with reasons and resolved; CodeRabbit withdrew
  both of its findings after the replies. CodeRabbit's outside-diff finding (distinguish caller-owned transactions in
  `core-store.adoc`) and its ADR-index nitpick are fixed in `8426edc6`. The title check was inconclusive only; the
  title is accurate.

## 5. Reviewers

- **coderabbit** (required, quota-limited): reviewed `11f50875` — a qualifying review, its summary marker names
  `coveredCommitId=11f50875`, `kind=reviewed`, with 2 inline findings, 1 outside-diff finding and 1 nitpick. Every
  finding handled (2 withdrawn after replies, 2 fixed), every thread resolved. Re-triggered after the last push at
  `2026-10-26T02:19Z` and **refused on quota** ("review rate limited; next included review in 32 minutes"), recorded.
  The merge used the standing authorization: qualifying review on a PR head, all findings handled, re-trigger recorded,
  every other gate green on the current head.
- **sourcery** (optional): reviewed `11f50875` with 4 inline findings; each answered with a reason and resolved. It
  does not gate.

## 6. Operator questions

1. Executor skew — the first check answered `skew` (executor `0.1.1996` vs lane `0.1.2010`); after regeneration the
   lane version could not be re-derived (`could_not_check`). Options: "Proceed — versions match" / "Stop the run".
   **Selected: Proceed.**
2. `resolve` failed because the installed plan-marshall flat OpenCode layout has no `plan-marshall` bundle root.
   Options: create the bundle-identity shim / operator reinstalls / stop blocked. **Selected: Create the shim.**

Free text: the initial invocation (`plan=…PLAN-104…`) only. No other free-text operator message.

## 7. Rule check

**guard: none on this harness (unenforced)** — the resolve result reports `guard: none`; the rule check therefore
counts the run's own actions, not a guard log. Departures from section 1, all recorded:

- One shell file listing outside the repository (`ls -1` of `~/.config/opencode/skills`) to identify the installed
  layout, and three writes into that install: the `plan-marshall/.claude-plugin/plugin.json` bundle-identity shim
  (operator-selected), the domain-bundle manifest shim `plan-marshall/skills/plan-marshall-plugin/SKILL.md` and its
  `extension.py` symlink (required before `resolve` could read the architecture; no plugin source was edited).
- The documentation sibling `/home/oliver/git/plan-marshall-documentation` was fast-forwarded to its `origin/main`
  before the links check (sanctioned by the Done-when; the checkout was clean apart from its own `.plan/`).
- Every build ran `--execution-mode in_process` (degraded routing; see section 8), never a routed build.
- The self-review round-1 sub-agent ran one `ls` of `.plan/local` beyond its instructions (read-only, nothing rests on
  it).
- No `git grep`, no `git stash`/`rebase`/`amend`, no direct `gh`/`glab`/`mvnw`/`pytest` call, no inline Python.

Spec read: `spec_source=main_checkout`, no `spec_differ` marker.

## 8. Build server

- Setup preflight: **ready** for the worktree.
- Every build fell back to `in_process` with the reason **`build_server_client_path_missing_flat_layout`**: the maven
  wrapper resolves `skills/build-server-client/scripts/build_server.py`, and the flat OpenCode install only has
  `plan-marshall-build-server-client`. The build gate ran unimpeded.
- Two timeouts, no killed jobs: the branch-point full `verify` hit the explicit 1785 s bound (that run is the D1
  inventory, log `…-223401.log`) and a diagnostic `pm-runtime` rerun hit 1200 s. Both were re-run; the final
  `verify` at `8426edc6` completed in 272 s.
- Runtime stamp: `plugin_version` is `unknown` (the seam returns none); `executor_version` `0.1.2010`.

## 9. Residue

- `java:S9428` (`StoreDigest.Builder`) remains open on `main` with the reason stated; closing it needs a design change,
  not a test.
- `java:S112` (`MacSecurity:305`) remains open; its only settlement is a dismissal, which needs the SonarCloud write
  credential this checkout lacks.
- CodeRabbit is quota-limited; the next included review was still ~32 minutes away at the merge.
- The push-triggered run made before the PR existed failed its `sonar-build` with the organisation's "non-main
  branches" refusal — the behaviour R18 documents; the PR-triggered run is green.
- CodeRabbit observed that a top-level `harnesses` member that is absent, `null` or not an object is still read as an
  empty map (pre-existing `HarnessEnrolmentStore.decode` behaviour, outside D9's entry-level wording and this plan's
  surface). It needs its own small fix and test.
- The two shims in the OpenCode plugin install remain in place so the lane stays runnable; they are not part of the
  repository.
