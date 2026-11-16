envelope_version=1
sender_type=plan
sender_id=rb-v2-67-main-hygiene-leftovers
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-16T03:42:45Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-67-main-hygiene-leftovers
epic=pm-mcp-core-m2-m4-v2
pr=#445
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=1
review_body_findings_handled=1
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-67 — settlement of the unowned Sonar issues of `main`

Standing rules: r7 (2026-11-10), rules R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R14, R16, R17, R18, R19, R20 (R12 and R15 dropped; the spec's `r4` parenthetical is stale, the file's own revision is r7).

## 1. Deliverables

The spec lists three deliverables: D1 (dropped 2026-10-30, settled by PLAN-V2-109), D2, D4. D3 was dropped 2026-10-29 and carries no work.

**D1 — `StoreDigest.Builder`, `java:S9428` (dropped).** Met, order `none`. Read on the tree: `pm-core/src/main/java/de/planmarshall/core/store/StoreDigest.java:98` carries `@SuppressWarnings("java:S9428") // the builder is the incremental digest of one thread; chunks are not kept`, the builder Javadoc states one-thread confinement, and `pm-core/doc/adr/core-store/052-incremental-digest-field-of-store-digest-builder.adoc` is on `main`. Nothing changed.

**D2 — `MacSecurity.Downcall`, `java:S112`.** All clauses met.
- *Met, order `none`*: `java:S112` is gone from the pull request's SonarCloud list without a dismissal — read at `0289ba5f`: `open_issue_count 0`, `count_status confirmed`, `analysis_revision == head_revision`; the settlement is the inline suppression, not a SonarCloud transition. `java:S9428` is D1's (PLAN-V2-109).
- *Met, order `none`*: the `runtime-credentials` ADR is written — `pm-runtime/doc/adr/runtime-credentials/013-the-downcall-interface-declares-throwable.adoc` (next free number against `origin/main`, R11 checked), all template sections.
- *Met, order `none`*: the Build block and the links check — see the build rows below and `BROKEN LINKS 0`.
- The annotation is at `pm-runtime/.../credentials/MacSecurity.java:304` on the interface declared at `:305-307`, carrying R16 (4)'s stated reason beside it.
- Commits: `22fdf32d` (annotation + ADR), `2693f258` (ADR correction, F1), `0c4f8486` (wording precision), `3a8b8ce5` (run-time descriptor check, from the PR review).

**D4 — the five null-rule suppressions of `core.spi`.** All clauses met, order **`test-first`**.
- Test: `PrimitiveResultTest.refusesNullReasonType`, written first and seen failing against the unfixed code — failing run on `22fdf32d` with the test uncommitted, output `PrimitiveResultTest.refusesNullReasonType:142 Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown`, log `/home/oliver/.plan-marshall/marshalld/job-logs/f53823b17157463baf8b9d5eacc4605c.log`; passed at `cb3d88fe` (scoped run, log `25ae2ba63fe148fc9c5357a61781c209.log`), and the assertion carries its 20–60 character message in `a27dbff9`.
- *Met*: no `java:S2583/S2589/S2637/2789` suppression remains under `pm-core/src/main/java/de/planmarshall/core/spi/`; the only `@SuppressWarnings` there is `"unchecked"` at `PrimitiveRegistry.java:228`. The five fixtures (`test.reason-null`, `test.reason-not-class`, `test.reason-null-condition`, `test.reason-unnamed`, `PrimitiveResultTest.refusesNonClassReasonType`) stay green with their reason codes (`MALFORMED_REASON_TYPE`, `UNMAPPABLE_REASON_CONDITION`, `MALFORMED_REASON_WIRE_NAME`).
- *Met*: the pull request's SonarCloud search lists none of those rules (0 open at the head).
- Commits: `cb3d88fe` (sites + builder + new test), `a27dbff9` (assertion message).

**Carried 2026-11-14 (not a deliverable; the spec fixes them at the sites).** Settled in `5f406e7d` (+`0c4f8486` wording): `java:S3516` read first and found to be a false positive (`replayProject` answers the hand-over count on two paths and zero for a project whose enrolment file exists) and settled with an inline suppression and that reason; `java:S1117` renamed the local to `runningJob`; `java:S1144` deleted the unused private `ModelSlotCodec.scopeType` (the `scope_type` field stays write-only as before); `java:S1612` replaced the lambda with `QuestionChannel::wireName`. All four are gone from the pull request's Sonar list.

**Per-head test runs** (module-tests / gate canonical), each on a pushed head: `22fdf32d` 10613; `cb3d88fe` 10614; `5f406e7d` 10614; `0c4f8486` 10614 (`pre-commit` and `test`); `3a8b8ce5` 10614 (`pre-commit`, `verify`); `0289ba5f` 10658 (`pre-commit`, `verify`, `-Pcoverage`), 7518 (`verify -pl pm-core,pm-runtime`), 21 and 43 (integration profiles). Logs: `0d74d93e…`, `595fae8b…`, `5c72ed75…`, `cefc466c…`, `b8135624…`, `70c0e2fd…`, `19b585e1…`, `f2880363…`, `c71e0dd2…`, `32ddfc41…`, `26923127…`, `b265e391…` (all under `/home/oliver/.plan-marshall/marshalld/job-logs/`).

## 2. Personas and skills

- Per step: `persona-implementer` for D2 and D4 and the carried singles (production code, all three rows declare it); `persona-code-reviewer` for the three self-review rounds and for triaging the PR finding; `persona-module-tester` as the standard for the new unit test.
- D1: `skills: none named` (`Skills:` empty).
- D2: `pm-dev-java:java-core`, `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`, `pm-dev-java:java-null-safety`.
- D4: the same plus `pm-dev-java:java-lombok`.
- `skills added`: none — no deliverable touched a module the Expected Surface did not name.

## 3. Hypotheses

- D1 (S9428 already settled): **confirmed** — `StoreDigest.java:98`, ADR `052`.
- D2 (the analysis honours an inline `java:S112` suppression, and the interface cannot declare less): **confirmed** — the interface's `T call() throws Throwable` is at `MacSecurity.java:306` and every call site closes over `invokeExact`; the PR's Sonar read at `0289ba5f` reports 0 open issues, so the analysis honours the suppression.
- D4 (the five suppressions guard raw-typed implementors and the pattern match replaces them): **confirmed** — `PrimitiveResult.java:70-74`, `PrimitiveResult.java:113,168-169`, `PrimitiveRegistry.java:213-226,271-274,287-305`; the fixtures' refusals keep their codes.

## 4. Findings

- **Self-review round 1, F1 (Low), `pm-runtime/doc/adr/runtime-credentials/013-*.adoc` Consequences**: claimed the class "is exercised by no unit test". False — `KeychainSecretStoreTest` (`@EnabledOnOs(OS.MAC)`) reaches `MacSecurity.Downcall` through `KeychainSecretStore`. Fixed in `2693f258`; re-verified in round 2.
- **Self-review rounds 2 and 3: clear.** Round 3 confirmed the wording precision of `0c4f8486`.
- **CI finding at `3a8b8ce5` (build (26), `AssertionMessageScanTest`)**: my new `assertThrows` carried no message. Cause: CI builds the pull request's **merge** commit, and the `main` merged since my start (`cd9da049`, PLAN-V2-103, #450) publishes the scanner so it also covers `core/spi`; my local tree still scanned only `core/store`, so the local gate passed. Fixed in `a27dbff9`; every later head is green. This is a genuine local-gate miss caused by a fast-moving base, not a false positive.
- **PR review (coderabbit) `inline` / `inline-1` at ADR 013:38**: `MethodHandle.invokeExact` checks the descriptor at run time (`WrongMethodTypeException`), not at compile time. Real defect, and the `Downcall` Javadoc said the same wrong thing before this plan. Fixed in `3a8b8ce5` (ADR Context, Alternatives item 2, Consequences, and the Javadoc); thread replied to and resolved; disposition `finding: coderabbit inline inline-1 — 3a8b8ce5`.
- No other comment was posted on the pull request.

## 5. Reviewers

- **coderabbit** (required; `quota_limited`, `review_complete_after_handling`): refused on quota six times — 17:51:18Z, 19:23:42Z, 20:54:35Z (escalated `@coderabbitai full review`), 22:12:37Z, 23:44:04Z, 01:15:58Z — each trigger posted at or after the policy's 90-minute floor (waits 4,844 s, 4,488 s, 4,533 s, 5,078 s, 4,821 s, 5,423 s). It reviewed at **02:52:44Z** (`COMMENTED`; the marker `final_review_risk_coverage` records `kind=reviewed` covering `0c4f8486`), with one inline finding, fixed and disposed. No re-trigger was posted for the later heads (`3a8b8ce5`, `0289ba5f`): the flagged reviewer holds a qualifying review and its finding is handled, so the currency demand does not apply. `review-gate` on the merge head: `merge_allowed: true`, `all_handled`.
- **sourcery** (optional): refused at 17:51:33Z on its own character budget (next review in 15 h 27 m); no review obtained, and it does not gate the merge.

## 6. Operator questions

None asked — no `question` call was made in this run, and no free-text operator message was received.

## 7. Rule check

- **Guard**: none on this harness (unenforced).
- **`executor-skew`**: `skew` at the start — executor `0.1.2133`, lane `0.1.2135`; the named remedy was applied through the sanctioned generator notation (`generate_executor preflight`, `executor_action: regenerated`), and the re-check answered `match`. `.plan/execute-script.py` is untracked, so the main checkout stayed clean. At landing time the executor reports `0.1.2136`.
- **Spec read**: `spec_source: ledger_worktree` (`.plan/local/worktrees/_orchestrator/…`), and no `spec_differ` marker — the main checkout's copy does not differ.
- **Own actions that broke a rule**: four shell `ls` calls (listing `pm-runtime/doc/adr/runtime-credentials/`, `pm-core/doc/adr/core-store/`, `pm-core/doc/adr/`, `pm-core/doc/adr/core-spi/`) — the rule lists files with the file tools. The equivalent `glob`/`read` was available; the breach is recorded here.
- **R17**: the links check was run as the standing rule writes it, with the sibling repository's script by absolute path — outside this project's executor surface; the rule and the lane's "executor only" rule pull in different directions and R17 (a binding standing rule of the epic) was followed. It read `/home/oliver/git/plan-marshall-documentation` (a sibling repository the rule names), nothing credential-bearing.
- Everything else: every script through `python3 .plan/execute-script.py …`, every build through the resolved `build` map, every CI action through `ci`. No `git grep`, no `sleep`, no inline Python, no heredoc, no `&&`/`;`/pipe chaining, no `git stash`/`restore`/`checkout -- path`/`rebase`/force-push.
- **Restarts and dispatches**: no session restart, no killed sub-agent; three self-review sub-agent dispatches (rounds 1–3), each completed and reported.
- **Builds**: 21 local maven invocations, all `mechanism: daemon_longpoll` — 18 green, two expected red (the test-first run and a compile error from an incomplete rename I then fixed), one `indeterminate` because a `-Dtest` selector written with `+` matched nothing (a wrong selection, not a test result).

## 8. Build server

- Setup preflight: `ready` (`registered: true`).
- Per-mechanism count: `daemon_longpoll` 21, `in_process_fallback` 0. No build was submitted and fell back; no retry was needed; no `killed` job.
- `runtime_stamp_reason`: none reported by `resolve`.

## 9. Residue

- **The spec's D2 premise is false and needs the orchestrator's correction**: "The class is OS-specific and has no unit test (`@EnabledOnOs`)". `KeychainSecretStoreTest` is `@EnabledOnOs(OS.MAC)` and reaches `MacSecurity.Downcall`. The ADR states it correctly; the spec is outside this lane's write boundary.
- **The spec's Standing Constraint "No credential, no UI dismissal" is stale** against the epic's own 2026-11-14 retirement (the lane resolves `sonar: configured`, `sonar_ce_wait_timeout_seconds 60` on this host). No dismissal was needed, so nothing followed from it.
- **`doc/adr/013-the-null-contract-of-the-null-marked-packages.adoc:61`** says behaviour does not change except where a class changes a signature; `PrimitiveResult` now refuses a `null` reason type where it previously read it as absence. The new behaviour is documented at the site, pinned by a test and prescribed by D4, and the sentence is narrower than the tree — a note for the analysis, not a defect.
- **sourcery's optional review was not obtained** (its own budget); coderabbit reviewed, so the required review exists.
- **`sonar-facts` refuses `--rules none` at a confirmed zero** (`confirmed_zero_with_rules`) although the skill's text says `none` is written at a measured `0`; the fact line was rendered without the flag and reads `none`. A skill/verb mismatch worth reporting to the lane's owner.
- **`cleanup_owed: false`** — the base-evidence worktree `.plan/local/worktrees/v2-67-main-hygiene-leftovers-base` reported `executor_in_worktree: absent`, so it was not evidence and was removed; the plan worktree is removed, local and remote refs are pruned, and the main checkout is at `8e93002f`. No residue on disk.
