envelope_version=1
sender_type=plan
sender_id=rb-v2-109-store-digest-main-green
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-10-30T16:01:00Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-109-store-digest-main-green
epic=pm-mcp-core-m2-m4-v2
pr=#250
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=2
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

Standing rules: r4 — R02, R06, R07, R08, R11, R14, R17, R18, R19, R20 (read in full before the first edit; the spec was read from the orchestrator ledger worktree, `spec_source: ledger_worktree`, no `spec_differ` marker).

Merge commit: `03f182c924c3f707755ad6601831e07e05c9873c`.

## 1. Deliverables

| # | Deliverable | Done-when clause | Verdict | Order | Test | Commit |
|---|---|---|---|---|---|---|
| D1 | `StoreDigest.Builder`: settle `java:S9428` — inline `@SuppressWarnings("java:S9428")` on the field with the reason beside it, in the style of `QueueTaskRecordCodec:303` / `ActivityEntryCodec:197`; the builder Javadoc states the one-thread confinement; no behaviour change; `StoreDigestTest` unchanged | annotation, reason, Javadoc, no behaviour change, test unchanged | met | `none` | `StoreDigestTest` (13 tests) unchanged in the diff and green | `fc751b05` (annotation + Javadoc), `66457b69` (Javadoc reworded as a caller contract) |
| D2 | One `core-store` ADR recording the suppression against the redesign and the dismissal fallback, sections of `doc/adr/template.adoc`, next free number at `origin/main` | ADR written | met | `none` | — | `81a93933`, `66457b69`, `f8225cc2`, `de3d9dec`, `6f689f7d` |

Plan Done-when, clause by clause:

| Clause | Verdict | Evidence at the merged head `17975d33` |
|---|---|---|
| The pull request is green and the PR Sonar search no longer lists `java:S9428` | met | CI green on `17975d33`; `issues/search?pullRequest=250&resolved=false` returns `total: 0` |
| The report states `sonar_open_issues` and the rules left open, and the PR gate state | met | `0` and `none`; PR gate `status: OK` (`new_reliability_rating` actual 1) |
| The ADR is written | met | ADR 052 |
| `verify -Ppre-commit` (its rewrites reviewed and committed) | met | green at `17975d33`; the rewrites are `00c89830`, reviewed line by line |
| `verify` of `pm-core` | met | full `verify` green at `17975d33` (the full run finished inside the bound in every attempt, so no targeted fallback was needed) |
| `verify -Pcoverage` | met | green at `17975d33` |
| `verify -Pintegration-tests -pl pm-core -am` | met | green at `17975d33` |
| Links check (R17) from the main checkout against the documentation checkout at its `origin/main` | met (run) | one pre-existing broken link, outside this footprint (see § 9) |
| After the merge: gate `OK` and `Maven Build` green on `main` | orchestrator's check at `analyze`, not the lane's | the `branch=main` gate read at the time of this landing still shows the pre-merge analysis (rating 4, coverage 90.2), i.e. no post-merge re-analysis yet |

No clause of this plan carries a "fails at `{sha}`" requirement, so every order is `none`; no failing run is claimed. Test runs at pushed heads: `StoreDigestTest` (13) at the branch base; a targeted `StoreDigestTest,JournalRecoveryTest,ScopeTransactionTest` run (86) and a full `pm-core verify` (3873) inside self-review; full `verify` green at `00c89830`, `f8225cc2`, `6f689f7d` and `17975d33`.

## 2. Personas and skills

- D1 (production code): `persona-implementer`; skills `java-core`, `javadoc`, `cui-logging` — the three the spec names — read as standards before the first edit.
- D2 (documentation): `persona-documenter`; same declared skills.
- Every self-review round and PR-comment triage: `persona-code-reviewer`.
- `skills: none named` in the spec for the deliverables themselves; no skill added for surface drift.

## 3. Hypotheses

- **The analysis honours `@SuppressWarnings("java:S9428")`** — **CONFIRMED**. On the PR head the issue search lists no `java:S9428` and the PR gate reads `OK` with `new_reliability_rating` 1. The dismissal fallback was not needed. (`StoreDigest.java:98-99`.)
- **With `java:S9428` gone the new-code rating is A and the `main` gate turns green with nothing else changed** — confirmed at the pull-request level (rating A, gate `OK`); the `main`-level confirmation is the orchestrator's post-merge check. (`StoreDigest.java:98-99`.)
- Verify-first: no open pull request touched `StoreDigest.java`, `JournalRecovery.java` or `ScopeTransaction.java` (only dependabot #71 was open); the `main` gate and issue list were re-read and matched the spec's OBSERVED claims.

## 4. Findings

- Self-review round 1: builder Javadoc stated non-sharing as an enforcement claim → reworded as a caller contract (`66457b69`); the ADR's Sonar figures gained the reads they came from (`66457b69`). One reported item (the spec "missing") was refuted: the spec lives in the orchestrator ledger worktree and `resolve` read it there.
- Self-review round 2: no `verify -Ppre-commit` evidence at the then-final tree → re-run at `f8225cc2`; ADR wording aligned (`f8225cc2`).
- Self-review round 3: the ADR attributed the bounded-memory cost of the literal local-variable fix to the `compute(consumer)` redesign → the two alternatives are now separate (`de3d9dec`). The operator chose to accept the fix and record `budget-exhausted` rather than run a fourth round; the fix is therefore **not re-reviewed**.
- CI: no failing check. CodeRabbit: no actionable comments on either head. Sourcery: approved at `17975d33`. No inline review threads existed at any point.
- Surface delta (R19 observation): five files outside the declared surface were rewritten by `verify -Ppre-commit` (`00c89830`) — `RoleAssignmentValidation`, `RoleAssignmentProposalTest`, `RoleAssignmentValidationTest`, `PlanInstanceComposer`, `PlanInstanceComposerTest`. Formatter/OpenRewrite only; no assertion removed or relaxed.

## 5. Reviewers

| Bot | Required | Outcome | Head | History |
|---|---|---|---|---|
| coderabbit | yes (`quota_limited`) | reviewed | `17975d33` (marker `kind=reviewed`, `sourceCommitId=coveredCommitId=17975d33`; “No actionable comments”) | triggered 13:55 on `6f689f7d`; reviewed it; re-triggered 15:39 on `17975d33`, answered “Already reviewed the last commit” — the review of `17975d33` had already landed at 15:18 |
| sourcery | no (optional, `quota_limited`) | refused on quota at `6f689f7d`, then approved | `17975d33` | quota notice 13:56 (“2 days 9 hours”); re-triggered after the merge, “Sourcery assessment **Approved.**” at 15:15 |

Standing review authorization was not needed: CodeRabbit reviewed the current head.

## 6. Operator questions

1. “Self-review round limit: run a fourth round or accept the fix?” — options: {run a 4th review round (recommended), accept the fix and record `budget-exhausted`}; **selected: accept the fix, record `budget-exhausted`**.

No free-text operator messages.

## 7. Rule check

- Guard: `none on this harness (unenforced)`; `{guard_log}` carries nothing for this run, and no guard refusal occurred.
- The run's own actions: 19 daemon-routed builds, all `daemon_longpoll`, all successful, no fallback and no retry; every shell call was a single command with no `&&`, `;`, pipe, substitution, loop or heredoc; every file read and search went through the file tools; no `sleep`; no `gh`/`glab`; no direct Maven wrapper, test, lint or type-check binary; no `python3 -c`; no `git grep`; no `git stash`/`restore`/`rebase`; no forced push; every commit staged by name; repository edits confined to the worktree; `.plan/` writes limited to `.plan/temp/` and the prescribed `ci-bodies` slots plus this message.
- Recorded deviations, each sanctioned by the governing text: the R17 links check was run as the standing rule spells it, `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …` (outside the executor surface by the rule's own wording), and the sibling documentation checkout was fast-forwarded to its `origin/main` so the check ran against the tree the rule names; the coding-skill standards were read from the installed skill bundle (`/home/oliver/.config/opencode/skills/…`) as R20 requires; public SonarCloud and GitHub API reads were made with the web tool, as R18 requires for the public project.
- Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker. Executor skew: `match` (executor 0.1.2041, lane 0.1.2041).

## 8. Build server

- Setup preflight: `ready` (registered, version 1).
- Builds: `daemon_longpoll` = 19, `in_process_fallback` = 0; no degradation, no launch-fault retry, no `killed` job.
- `runtime_stamp_reason`: none reported by `resolve`. `plugin_version` is `unknown` (the seam returned none).

## 9. Residue

- The `main` gate and the `Maven Build` of the merge commit are the orchestrator's post-merge check; at landing time `branch=main` still showed the pre-merge analysis.
- One pre-existing broken link outside this footprint: `pm-runtime/doc/developer/runtime-config.adoc:10` → `plan-marshall-documentation/doc/requirements/16-model-work.adoc#PM-EXT-6` (missing anchor).
- The unrelated analyzer error `Unable to run check class org.sonar.java.se.SymbolicExecutionVisitor … StoreSnapshot.java` is untouched: it does not fail the gate (the spec's observation).
- The 12 code smells open on `main` (S1192 ×4, S3776, S1135 ×6, S112) are untouched by design; they belong to PLAN-V2-46/81/67.
- The five pre-commit-rewritten files are an undeclared surface delta for the analysis (R19), not a failure.
- Self-review is `budget-exhausted`: the round-3 finding is fixed but not re-reviewed, at the operator's instruction.
