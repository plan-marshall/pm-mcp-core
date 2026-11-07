# Landing Analysis: PLAN-V2-93 — HTTP tests through annotations, first Sonar client and test

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #316 (merge commit `73de1648`; 33 files, +3738/-1576; one production file changes imports, two production files are new)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-93.md`. A pasted claim is a lead, never a fact.

Source: the plan's landing message `plan-v2-93-http-tests-annotations-008.md` (19:07Z; `landing-check` `complete: false`, only `effort` missing; 24,713,538 tokens, 34,662 s; 9 of 9 deliverables, 7 met and 2 met with residuals, 0 blocking, 11 advisory). Seven candidate-lessons (001 to 007) were archived earlier.

Corroborated by the orchestrator: Sonar `main` (analysis of `73de1648`, 2026-11-07 19:08 UTC) gate OK, 0 open BUGs; `Maven Build` and `Integration Tests` green on push and on merge_group, `Scorecard` green on push. Merged over a recorded `barrier-ask-override` (CodeRabbit never reviewed the last commit `a76cb3f4`; the unreviewed delta is three edits, one of which fixes a latent flake). The reading of the merged code is a read-only sub-agent's (R21, r6); the points marked "re-read" were re-read by the orchestrator at `73de1648`.

## Deliverable Fidelity vs Spec

- **Met (re-read):** the dispatcher grep (`setDispatcher|new Dispatcher|extends Dispatcher`) over `src/test` is empty; the 12 classes keep their test-method counts (13/5/16/17/20/19/7/10/5/9/8/6) and their `@DisplayName` sets (three renamed); ADR 002 reads "Superseded by ADR 017", ADR 017 exists and `doc/adr/README.adoc:36` agrees; the two Judgment classes use `LogAsserts` with constants; the `HttpClient.java` change is import order only; `Optional`/null rules and `@NullMarked` hold in `SonarClient`/`SonarIssue`; no dependency was added (`pm-provider-sonar/pom.xml` unchanged).
- **Boundaries kept:** `LOOP_REDIRECTS` and the page cap 3 to 2 (`HttpClientTest`), `REUSE_MARGIN` plus and minus one (`InstallationTokensTest`), `cap` and `cap+1` (`HuggingFaceSourceRepositoryTest`), one byte beyond the size, stall and read timeout, 503/404/500 rows (`LfsBatchClientTest`). Failure diagnostics were not lost: `outcome::detail` and `fetch::detail` are used as messages, and their counts rose (`EndpointHarnessTest` 1 to 9, `LfsBatchClientTest` 22 to 28).
- **Gain:** the Harness, Verification and Judgment classes used catch-all dispatchers; their new routes check method and path (the old `readiness()` only asserted `true`).
- **Not met:** the per-class table of generator calls versus kept literals is absent (the landing admits it).

## Findings (no fix plan yet; the operator decides)

1. **Weakness, conversion by form (the Done-when is satisfied by the grep).** Every converted class carries its own routing map inside a hand-written `new ModuleDispatcherElement` (15 copies, re-read). `@MockResponseConfig` is used 6 times in 3 classes (`HttpClientTest`, `GitHubClientTest`, `GitLabClientTest`); nine classes use none, although ADR 017 says static responses go through it. ADR 017's rationale ("answers whatever request reaches it") is false for `HttpClientTest`, `GitHubClientTest`, `GitLabClientTest` and `LfsBatchClientTest`, which already keyed on method and target with a 404 fallback before the plan.
2. **Weakness, helper copies.** `private static String word()` is copied into 15 classes (re-read); the `routes`/`on`/`response`/`json` skeleton is repeated in the github, gitlab, http and sonar modules, which could take it from `pm-provider-http:tests`; ADR 017 cites ADR 002's "no shared helper" instead.
3. **Weakness, draws outside the seed.** `SonarClientTest.java:415` (`static Stream<Arguments> unreadablePages()`) draws `word()` in a static `@MethodSource` (re-read), so a failure there cannot be replayed from the seed.
4. **Weakness, unpinned boundaries in the new Sonar test.** No test for `total == 500` (one full page) or `total == 10000` (complete at page 20); `stopsAtTheCap` draws 1..1000 above the cap; the pull-request bounds 0 and 1 are only drawn from `integers(-100,0)` and `integers(1,100000)`. A transport failure and the 405/406/409/422 mappings are untested.
5. **Weakness, rule gaps.** `EndpointHarnessTest` has no `LogAsserts` although `EndpointHarness.java:72,86,94,101,123` log WARN records (re-read). At least 22 assertion messages are under 20 characters (`GitHubClientTest:221,225`, `InstallationTokensTest:198,252`, `GitLabClientTest:307,459-461,479,483,484`, `HuggingFaceSourceRepositoryTest:217,376,435,449,463,476`, `LfsBatchClientTest:305,471,563,575,588`); one is 62 (`JudgmentEndpointTest:300`). `SubprocessJobRunnerTest.java:230` (`LoggedWatch extends Handler`, re-read) is a hand-made log handler that remains in `pm-runtime` outside the 12.
6. **Doc-gap, stale ADR 002 citations (re-read).** `provider-github.adoc:217`, `provider-gitlab.adoc:218`, `provider-http.adoc:99,138` and `runtime-corpus.adoc:101` cite ADR 002 as live; `provider-http.adoc:99-104` and `runtime-corpus.adoc:101` still describe the old hand-written dispatcher. The future tense "PLAN-V2-93 supersedes" survives in `CLAUDE.md:191`, `.opencode/rules/plan-marshall-standing-rules.md:189` and `doc/adr/010` lines 38, 92-93 and 132.
7. **Weakness, scope change.** The spec said "no production change"; the plan added `SonarClient` (231 lines) and `SonarIssue` (45 lines) with ADR `provider-sonar/001`, a developer-doc section and `README.adoc` and `Traceability.adoc` entries, all documented. The "operator decision at refine" appears only in the landing message and the ADR text; the ADR itself says the reason for `projects` as the query parameter is "not recorded"; Sonar's behaviour for `projects` with `pullRequest`, and bearer auth below SonarQube 10, were not verified by the reader. The landing does not say whether a client reads untrusted HTTP input, which the spec required.
8. **Notes.** `JudgmentEndpointRemoteTest:282` keeps `new MockWebServer()` for a second (cleartext redirect) server, legitimate but not named in ADR 017. The pre-commit gate rewrote imports in `EndpointTest`, `JsonTest` and the generators package of `pm-provider-http`, including `HttpGenerators` in the published test artifact. The unused `@Builder` on the private `ThreadData` record (`GitHubClientTest:181`) was added only to satisfy the "4+ components" rule. The `INLINE SCHEMA BLOCKS 91` the landing mentions were `main`'s, not this branch's; after documentation #160 the count is 0 with 88 pending.

## Recurring weaknesses (a to g)

(a) lost boundaries: no in the converted classes, yes in the new Sonar test (4); (b) copied helpers: yes (2); (c) draws outside the seed: yes, once (3); (d) satisfied by form: yes (1); (e) lost diagnostics: no; (f) per-datum overclaim: mostly no; (g) missing per-class table: yes.

## Process

Merged with the required reviewer absent and the optional one refused for size: a second merge over `review-barrier-gap` in this epic. The landing reports the Sonar step as `work_performed=false` (no plugin credentials); the orchestrator's own HTTP read above is the proof.
