envelope_version=1
sender_type=plan
sender_id=rb-v2-144-main-green-dead-null-checks
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-15T02:34:46Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-144-main-green-dead-null-checks
epic=pm-mcp-core-m2-m4-v2
pr=#433
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2125
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# Landing — PLAN-V2-144: remove the two dead null checks in `EndpointVerification`

Standing rules: r7, R02, R06, R08, R17, R18, R19, R20 (R21 binds the orchestrator, not this plan). PR #433 merged as `f82f3e733f6ce0b318e1a84d4fc88001fc0c0346`; PR #425 (same branch) was closed unmerged under the reviewer quota policy and is superseded by #433.

## 1. Deliverables

### D1 — Remove the two dead checks (commit `72ddc8ea`)

| Done-when clause | Verdict |
|---|---|
| `EndpointVerification.java` holds neither check | **met** — `ownsTheNamedProgram` keeps only `!lookup.isFound()` (`:125-130`); `probe` reads `profile.identity()` directly (`:296-299`) |
| `verify`, `verify -Ppre-commit` (rewrites reviewed and committed), `verify -Pcoverage` green | **met** — three BUILD SUCCESS runs on the tree committed as `72ddc8ea`, 10,475 tests each; `git status` showed no rewrite to commit |
| `git diff --stat` shows `EndpointVerification.java` alone | **met** — `1 file changed, 3 insertions(+), 6 deletions(-)` |
| Sonar PR search `resolved=false` lists no open issue and the PR gate is green | **met** — PR #433 read at `3ee97467`: `count_status: confirmed`, `open_issue_count: 0`, `analysis_revision == head_revision`; quality gate `OK` (all six conditions) |
| After the merge the gate of `main` is `OK` and `deploy-snapshot` ran | **partly met, partly not evidenced** — the gate of `main` is `OK` (see D2); `deploy-snapshot` is **not evidenced** |

Test name: `EndpointVerificationTest` (unchanged, byte-identical). Order: `none` — a refactor, and no Done-when clause says "fails at `{sha}`", so no failing run is claimed and no failing-output identifier exists. The spec's claim that no test constructs an `EndpointProfile` with a null `executable` or `identity` is confirmed (see §3).

Per-head test runs: `module-tests` green at `72ddc8ea`, `c18cef4c` and `3ee97467` (10,475 / 10,475 / 10,555 tests); `verify` green at the same three heads.

### D2 — Prove the gate (no commit)

- **Gate of `main`: `OK`.** Read through `sonar_rest gate-status --project plan-marshall_pm-mcp-core --branch main`: `new_reliability_rating` 1 against 1, `new_security_rating` 1, `new_maintainability_rating` 1, `new_coverage` 91.0, `new_duplicated_lines_density` 0.2, `new_security_hotspots_reviewed` 100.0 — all six conditions `OK`.
- **`sonar-build` succeeded on the merge commit: evidenced.** The main-branch Compute-Engine task `AaHdCiWKTXYi85DtheHn` ran `SUCCESS` at `2026-11-15T02:29:52Z` on `branch=main`, which is the analysis the `sonar-build` job performs.
- **`deploy-snapshot` ran: NOT EVIDENCED.** The `ci` surface has no verb that lists a workflow run by commit sha or by a branch push (`checks status --head main` resolves a PR and answered `no pull requests found for branch "main"`; `pr landing-state --branch main` answered `pushed_no_pr`); the merge-queue run `pr queue-state` reports (`43667221566`) is the Integration Tests workflow and carries no `deploy-snapshot` job. The clause is therefore reported **not evidenced**, and D2 is **not fully met as written**. D1 is fully met; `deliverables_done` is 1 of 2.

## 2. Personas and skills

- D1: persona `persona-implementer` (the spec's `Persona: implementer`); domain skills `pm-dev-java:java-core`, `pm-dev-java:java-null-safety`, `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`, loaded as standards via the skill tool before the work.
- D2: no persona or skill declared (`skills: none named`); the read used the Sonar provider verbs.
- Self-review and PR-comment triage: `persona-code-reviewer`.
- Surface drift: none — the only changed file is the spec's declared surface. `skills added: none`.

## 3. Hypotheses

- OBSERVED `EndpointVerification.java:126` (`profile.executable() == null`) and `:298` (`identity == null`) — **confirmed** at the branch base: `EndpointVerification.java:126` and `:298` held exactly those checks.
- OBSERVED `EndpointProfile.java:50-51` (`requireNonNull(executable)`, `requireNonNull(identity)`) — **confirmed** in `pm-core/src/main/java/de/planmarshall/core/profile/EndpointProfile.java:50-51`; no test constructs a profile with either component null (the only null component anywhere is a null `clientId` in `EndpointProfileBuilderContractTest:51`, which asserts the refusal).
- OBSERVED the gate of `main` `ERROR` on `new_reliability_rating` 3-against-1 — **confirmed** at setup: the main read reported exactly that condition.

## 4. Findings

- Self-review round 1 (sub-agent, `persona-code-reviewer`): five findings, all refuted / not open — (1) the double `profile.identity()` accessor is the spec's own "the local goes with it" (preference, not defect); (2) the untested "found but foreign executable" branch is pre-existing and outside the diff; (3) the gate "at `{sha}`" shorthand covers a content-identical tree (the post-commit `module-tests` run at each head closes it); (4) the three pre-existing `java:S1135` TODOs are a watch item the spec forbids touching — the Sonar PR read returned 0 open issues, so the clause is satisfiable; (5) the post-merge clause was not gradeable pre-merge. Verdict: CLEAR, no open item.
- CI: `build / quarkus-alignment` failed once on PR #433 with an external `HTTP 404` fetching `quarkus-bom-3.40.1.pom` from Maven Central (the same job passed on #425 forty minutes earlier); rerun of run `43662203472` — success.
- CodeRabbit review bodies: no actionable comments at any head; `review_body_findings_total: 0`.
- Sourcery (optional): refused on its own review budget at #425 and #433; optional bot, never gating.
- No PR thread was opened and no comment needed a reply.

## 5. Reviewers

| Reviewer | Kind | Outcome | Head(s) | History |
|---|---|---|---|---|
| coderabbit | required | reviewed | `72ddc8ea` (kind=reviewed), `c18cef4c` (carry-forward, re-reviewed fresh on #433), `3ee97467` (full review, kind=reviewed) | triggered at 18:30Z, 19:01Z, 19:02Z, 20:38Z, 20:39Z, 22:10Z, 22:11Z, 23:43Z, 02:16Z; quota refusals at 19:02:16Z, 20:39:46Z, 22:11:36Z, 23:43:16Z, each followed by the policy's 90-minute wait (not the bot's shorter notice) |
| sourcery | optional | refused (quota) | — | triggered 18:30Z; refused 18:30:09Z and again at 01:15:25Z (250,000 diff-character budget, next in 17h43m); optional, so the merge was never gated on it |

Standing review authorization: not used. The quota policy's step 1 (90-minute waits and re-triggers) and step 2 (close PR #425 unmerged, reopen as #433 from the same branch) were both spent; the fresh PR produced a qualifying `kind=reviewed` marker, and the final full review did the same at the merge head. No operator question about the quota was asked before those steps.

## 6. Operator questions

1. Asked at setup: "The runbook lane's preflight found an executor skew: the project executor at `.plan/execute-script.py` carries version 0.1.2118 while the lane text this run loaded is 0.1.2123. The lane stops as blocked here, because a stale executor's own resolve cannot perform the check. Remedy: regenerate the executor. How should I proceed?" Options: "Regenerate executor now (Recommended)" and "Stop; I will run /marshall-steward". Selected: free text — "executor is regenerated" (verified: `executor-skew` then answered `match`, 0.1.2123 = 0.1.2123).

No other question-tool call was made. Free-text operator messages: the answer above only.

## 7. Rule check

- **Guard: none on this harness (unenforced).** The resolve result reported `guard: none`; no guard log exists for this run, so nothing is reported as "all kept".
- Spec read: `spec_source: ledger_worktree`; no `spec_differ` marker was returned.
- Executor skew: initial verdict `skew` (0.1.2118 vs 0.1.2123) — the run stopped and asked; after the operator's regeneration the verdict was `match`. Recorded, never read as no-skew.
- The one deliberate deviation from §1's "every build: the `build` commands the resolve result supplies": the spec's Done-when requires `verify -Pcoverage`, which the resolve build map does not carry, so that one profile was run through the same `plan-marshall:build-maven:maven` notation (`--command-args "verify -Pcoverage"`), never through `mvn` directly. All other builds used the resolved canonicals.
- Shell discipline: one command per call; files read with the file tools; no `sleep`, no `gh`/`glab`, no inline Python; temp files only under `.plan/temp/`; all repository edits inside the worktree.
- Git: commits staged by name, one commit for D1, one merge commit per base movement; no stash, restore, checkout-of-path, rebase or forced push.
- `.plan/` writes: the progress file, the landing payload, the CI body slots returned by `prepare-*`, and this one inbox message. The spec and `.plan/orchestrator/` were not edited.
- Sub-agent: the self-review reader reported no file writes and no builds; its rule breaks would count as the run's, and none were reported.

## 8. Build server

- Setup preflight: `ready` (project enrolled, daemon reachable).
- Builds: 11 local builds, all `mechanism: daemon_longpoll` — `quality-gate` ×3, `verify` ×4 (one per head plus the pre-PR head), `verify -Pcoverage` ×1, `module-tests` ×3. Fallbacks: none; retries: none; `killed` jobs: none.
- `runtime_stamp_reason`: absent (every stamp fact was measurable except `plugin_version`, which resolved to `unknown`).

## 9. Residue

- **D2's `deploy-snapshot` clause is not evidenced** (the `ci` surface cannot list a run by commit sha). The gate of `main` is `OK` and the `sonar-build` analysis ran on the merge commit; only the `deploy-snapshot` job's run is unreadable through the sanctioned surface.
- PR #425 remains closed unmerged (superseded by #433) under the reviewer quota policy.
- The structural finding worth keeping: a merge-only head makes CodeRabbit publish `kind=unchanged_files_carry_forward`, which `review-gate` refuses as `required_review_missing`; a fresh PR, or an accepted `@coderabbitai full review`, is what produces the `kind=reviewed` marker the gate needs.
