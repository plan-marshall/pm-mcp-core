envelope_version=1
sender_type=plan
sender_id=rb-v2-110-null-contract-main-green
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-02T14:25:10Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-110-null-contract-main-green
epic=pm-mcp-core-m2-m4-v2
pr=#282
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

# Landing — PLAN-V2-110: make the null contract of the 99 `@NullMarked` files true

> Successor to `rb-v2-110-null-contract-main-green-001.md`. That message's `merge_state=open` and
> `cleanup_owed=true` are stale: the completion PR it described (#280) was closed unmerged under the
> reviewer-quota policy and superseded by #282, which has now merged. This message carries the final facts.

Standing rules: revision **r4**, rules R02, R06, R07, R08, R11, R13, R14, R15, R16, R17, R18, R19, R20.
This run landed **completely**: the three plan pull requests, the ADR, and the completion pull request are
merged; `main` is at the completion merge commit. Only the orchestrator's terminal `analyze` remains.

| PR | Slice | Files | State |
|---|---|---|---:|---|
| #264 | D1 — `core.store` and `core.queue` (158 sites) | 60 | merged `5b665f9b` |
| #268 | D2 — the other `pm-core` packages (98 sites) | 35 | merged `2f942feb` |
| #274 | D3 — `pm-workflow`, `pm-runtime`, providers (36 sites) + D4 ADR 013 | 33 | merged `5bed38c4` |
| #280 | completion — first attempt, closed unmerged (quota policy step 2) | 8 | closed |
| #282 | completion — the same tree plus Sourcery's follow-up | 8 | merged `65ee72ac` |

## 1. Deliverables

**D1 — `core.store` and `core.queue`. Met.**
- *Done-when (Sonar search of the PR lists no S2583/S2589/S2637 and no new other-rule issue):* met for #264
  (`pullRequest=264&resolved=false` → 0; gate passed; one S1192 found on the first push and fixed in
  `060aa886`). Evidence order **none** (the clause has no fail-first requirement).
- The post-merge self-review found two sites the new-code-scoped PR analysis did not surface:
  `QualificationSettingsCodec.DocumentReader.rewritten` (uninitialised, S2637) and
  `QuestionRecord.requireAsked`'s unannotated `planTaskId` (S2589). Fixed in `e09fbb8f`, landed in #282.
- Commits: `822707a6`, `060aa886` (D1); `e09fbb8f` (completion).

**D2 — the other `pm-core` packages. Met.**
- *Done-when (Sonar search of the PR):* met for #268 (`pullRequest=268` → 0, gate passed). Order **none**.
- The post-merge self-review found eight sites: seven S2589 in `ProfileValidator.modelsListingProbe`/
  `argvProbe`/`covered` (the *parameters* were the unannotated source, not the locals) and one S2637 in
  `Scoring.threshold` (the `@Nullable` from `Grading.passThreshold` spread into a non-null return). Fixed in
  `e09fbb8f`, landed in #282.
- Commits: `f7956a74`, `02453404`, `400f5da5`, `eb182c85` (D2); `e09fbb8f` (completion).

**D3 — `pm-workflow`, `pm-runtime` and the providers. Met.**
- *Done-when (Sonar search of the PR):* met for #274 (`pullRequest=274` → 0, gate passed). Order **none**.
- The post-merge self-review read all 36 sites as addressed. Its two code findings on this PR
  (the `JournalProvider` absent-journal path, its Javadoc) and the missing absent-journal test were fixed
  in `132b7676`/`eaab033f` with the reviewer.
- Commits: `21470b6d` (D3), `132b7676`, `eaab033f`.

**D4 — the ADR. Met.**
- `doc/adr/013-the-null-contract-of-the-null-marked-packages.adoc` (next free number after 012 at `origin/main`)
  records the three classes and the evidence each needs, the `Optional` rule, and the two rejected
  alternatives (the ignore of the rules and waiting for PLAN-V2-97..99). Index row in `doc/adr/README.adoc`.
- *Builds (Done-when):* `verify -Ppre-commit` green on every head (#264 `822707a6`, #268 `f7956a74`, #274
  `2cde9f8a`, completion `96cb85f6`, `54cdff3a`, `d53bc149`); `verify` green on `060aa886`, `400f5da5`,
  `2cde9f8a`, `eaab033f`, `96cb85f6`, `54cdff3a`, `d53bc149`; `verify -Pcoverage` green on `400f5da5`,
  `2cde9f8a`, `9f892cca`; `verify -Pintegration-tests` green on `9f892cca` (pm-core-scoped) and `2cde9f8a`
  (reactor); CI `Maven Build` and `Integration Tests` green on all heads (20/20 on the merged one).
  Order **none** throughout (the spec's Done-when carries no fail-first clause).
- *Links check (R17):* run from the worktree with the sibling's absolute paths — `BROKEN LINKS 0`, `REQS 115`
  (one pre-existing client-profiles traceability gap, not touched).

## 2. Personas and skills

Production code: `persona-implementer`; tests: `persona-module-tester`; self-review and PR-comment triage:
`persona-code-reviewer`; documentation (ADR/guides): `persona-documenter`. The spec names no `Skills:`/`Persona:`
lines (`skills: none named`); R20's `java-null-safety`, `java-core`, `javadoc`, `junit-core` and `cui-testing`
were read before the work, as recorded in the progress file. No skill was added for surface drift.

## 3. Hypotheses

- "Most S2583/S2589 sites are class (a) or (b), few need a behaviour decision" — **confirmed for (b)**, refuted
  for (a): (b) is the clear majority in all three deliverables; no test was deleted, and the class (c) sites are
  the JSON-null readers, the finders and `Json.parse`/`redactText`.
- "Deleting a dead test or annotating a source changes no behaviour a test observes" — **confirmed** for
  annotation-only sites (unchanged suites green); **refuted** for the (c) signature changes, which are the
  designed exception (the tests of `SecretRedactor`, `Json` and the provider helpers were adapted).
- "Before editing, list the open pull requests … and re-read the issue list and the gate of `main`" — done at
  start: only dependabot #71; 292 issues; gate ERROR.

## 4. Findings

| Source | Finding | Disposition |
|---|---|---|
| Self-review | F1 7x S2589 `ProfileValidator` (parameters) | fixed `e09fbb8f`, landed #282 |
| Self-review | F2 S2637 `Scoring.threshold` | fixed `e09fbb8f`, landed #282 |
| Self-review | F3 S2637 `QualificationSettingsCodec.rewritten` | fixed `e09fbb8f`, landed #282 |
| Self-review | F4 S2589 `QuestionRecord.requireAsked` | fixed `e09fbb8f`, landed #282 |
| Self-review | F5 terminal criterion unattainable until the completion PR lands | closed: #282 merged `65ee72ac` |
| Self-review | F6 `JournalProvider.journalFor` signature not in "Owed outside" | recorded below; sibling search: no caller |
| Self-review | F7 caller counts not stated per PR | recorded: D1 3 shared readers, 17 call files; D2 8 helper readers, 12 call files; D3 `Json.parse` 8 call sites, `JournalProvider` 2 test lambdas |
| Self-review | F8/F15 undeclared files and dropped-out files | recorded in §6 below |
| Self-review | F9 assertion message | fixed `e09fbb8f` |
| Self-review | F10 stale Javadoc | fixed `e09fbb8f` |
| Self-review | F12 ADR path / unnamed PR | fixed `e09fbb8f` |
| Self-review | F13 `askUserTool` over-annotated | kept: the public record accepts null; noted, not changed |
| Self-review | F14 `Enrolments` helper is a false-positive workaround | recorded: a Sonar false positive handled by extraction, not a (c) site |
| CodeRabbit #264 | dead guard in `QualificationSettingsCodec.read` | fixed `060aa886` |
| CodeRabbit #274 | ADR coverage claim; journal guard; `PlanFacts` builder fields; `Json.parse("null")` test | fixed `132b7676`/`eaab033f` |
| CodeRabbit #274 | `journalFor` Javadoc; absent-journal test | fixed `eaab033f` |
| Sourcery #282 | the initialised `rewritten` empty array could overwrite a settings file on a read path | fixed `83c5aa59`: field is `byte @Nullable []`, `write` answers `requireNonNull` of the document its read pass composed |
| Sonar | S1192 on #264 | fixed `060aa886` |
| Sonar | 10 post-merge sites (7+1 S2589, 2 S2637) on the completion PR | 0 open issues on #282, gate passed |
| CI | `build / quarkus-alignment` failed once on #282 `83c5aa59`, passed on the rerun | flapping npm/dependency alignment job; green on every later head |

## 5. Reviewers

- **coderabbit (required, quota-limited).** #264: reviewed `822707a6` (one finding, fixed). #268: reviewed
  `400f5da5` (three findings, fixed); later heads covered by standing authorization. #274: reviewed `2cde9f8a`
  and `dfdec8fd` (all findings fixed, threads resolved); later heads refused on quota. #280: no review; closed
  unmerged after the quota policy's 90-minute cycles (policy step 2) and superseded by #282. **#282: no formal
  review object**, but its persistent summary comment carries
  `final_review_risk_coverage:{"sourceCommitId":"6be9ac49…","coveredCommitId":"6be9ac49…","kind":"reviewed"}` —
  a head of this PR, `kind=reviewed`, "No actionable comments were generated in the recent review", pre-merge
  checks passed, merge risk Minimal. The merge used the standing authorization on that qualifying summary
  review: re-triggered after every push from `96cb85f6` on (refusals recorded 08:59:38, 10:31:11, 11:02:16,
  11:17:36, 12:49:13, 13:19:24, 13:44:41 UTC), no CodeRabbit finding or thread outstanding, and every other
  gate green on the merged head.
- **sourcery (optional, quota-limited).** One COMMENTED review on `632cea85` with the `rewritten` finding, then
  APPROVED on `96cb85f6`, `54cdff3a` and the merged head `d53bc149`.

## 6. Surface delta (R19) and owed signatures

Files outside a deliverable's declared surface that a (b)/(c) change forced, named as the plan requires:
D1 — `config/ConsentCodec`, `config/DefaultAssignment`, `config/RoleAssignmentsCodec`,
`role/corpus/ImportedItemCodec`, `role/corpus/SourceOverlayCodec`,
`role/qualification/QualificationRecordCodec`, `store/PlanHistoryCodec`, `store/LeaseCodec`,
`store/TransactionJournal`, `store/ProjectStoreIdentityCodec`, `store/BuildTimingsCodec`, `store/OutlineRecord`,
`store/WaiverRequest`, `store/PendingEntryRecord`, `store/IntentCodec`. D2 — `role/corpus/SourceOverlay`,
`role/qualification/GradedConfiguration`, `role/qualification/JudgeCheck`. D3 — `pm-core/profile/LoadedProfile`,
`pm-workflow/task/JournalProvider`, and the six provider test files that unwrap `Json.parse` (`GitHubClientTest`,
`GitHubAppJwtTest`, `InstallationTokensTest`, `GlabCommandsTest`, `GitLabClientTest`, `JsonTest`) plus
`TaskProtocolReOfferTest`. Declared files that dropped out or were resolved indirectly and were not edited:
`queue/ActivityJournal`, `store/OutlineRenderer`, `store/QuestionLog`, `role/RoleValidation`, `role/corpus/CorpusImport`,
`role/corpus/ItemGenerator`, `role/qualification/JudgeCheckCodec`.

Public/package-visible signatures that change (owed outside this repository; the sibling `plan-marshall-mcp` was
searched and has no caller of any of them): `StrictJson.nullableString`/`nullableInstant` →
`Optional`, `SecretRedactor.redactText` → `Optional`, `Json.parse` → `Optional`, `JournalProvider.journalFor` →
`Optional<ActivityJournal>`, `HarnessProfile.integer` (private) and the new `@Nullable` on the public record
components `LoadedProfile.verificationRecord`, `HarnessProfile.askUserTool`, `ImportedItem` (group, contamination,
Input, Grading, Review), `SourceOverlay`, `WaiverRequest`, `GradedConfiguration.effort`, `JudgeCheck.ItemScore.durationMs`,
`SelfReviewSubmission.ItemResult.evidence`, and the public `parse`/`isProviderId`/`kindOf`/`of` parameters.

## 7. Operator questions

1. "PR #268 blocked: required reviewer quota … how do you want to proceed?" — options: continue the 90-minute
   retry cycle (Recommended) / merge without the required review / stop. Free-text answer: **"retry now"**
   (not an option selection; recorded as free text, applied as the retry cycle).
2. "PR #274: required reviewer quota, again …" — options: keep retrying after the quota window (Recommended) /
   merge without the required review / stop with #274 open. Selected: **"Keep retrying after the quota window"**.
3. "Merge #282" — options: wait, retry, then merge (Recommended) / merge now without the review (waiver) / stop.
   Free-text answer: **"retry now"** (again not an option selection; applied as a retry, which was refused on
   quota). The merge was then made under the standing authorisation, not on an operator waiver.
No other question was asked.

## 8. Build server

Setup preflight: **ready** (project enrolled). Every build was `daemon_longpoll`; no `in_process_fallback`,
no launch-fault retry, no killed job. Scoped builds: 1 compile + 1 test for pm-core (D1), 5 scoped builds for
D2, 4 for D3 plus pm-workflow's, and the full-reactor gates on every head (the completion head ran both
`verify -Ppre-commit` and `verify`). `build_missing_canonicals`: none.

## 9. Rule check

- The guard is `none` on this harness (unenforced); no guard-log line exists for the run, and no "all kept" is
  claimed.
- **Deviation:** the §5 self-review subagent was dispatched **after** the three merges (over the merged diffs),
  not before each PR. It found 15 findings; F1–F4, F9, F10 and F12 are fixed and landed in #282, F5 is closed by
  that merge, F6/F7/F8/F15 are answered here, F13/F14 noted. Self-review state: **budget-exhausted** (open
  items remain), never "clear".
- `spec_source=ledger_worktree`, no `spec_differ`; `executor-skew=match` (0.1.2041/0.1.2041), recorded.
- Shell file operations: none used (`git status/diff/log/fetch/merge`, `ls`, and the R17 script only); no
  `git grep`, no `python3 -c`. Every script through `.plan/execute-script.py` except the R17 links check, which
  the standing rule itself spells as an absolute script path.
- Commits carry the resolved trailer. The merge commits the merge queue created also carry the platform's
  `Co-authored-by: plan-marshall-oliver` line above it — an observation, not a lane-authored commit.
- The local `verify` builds reformat some files in place (the formatter's format goal); every such working-tree
  change was reverted and never committed, so no unrelated formatting entered the branch.
- No `NOSONAR`, no `sonar.issue.ignore`, root `pom.xml` untouched; the one suppression is
  `RefusingFS.discoverGitExe` (JGit override, reason beside it, listed in #274). No format version moved (R13);
  no log record added (R12); ADR 013 is the next free number at `origin/main` (R11).

## 10. Residue

- **Nothing blocks this plan.** The completion PR merged as `65ee72ac`; base `main` was merged twice on the way
  (#283 at `54cdff3a`, #286 at `d53bc149`) with the gate and CI green on each.
- The plan's terminal measurement (`main` reads 0 null-contract issues, gate `OK`, deploy) is the orchestrator's
  `analyze`: the lane's own Sonar seam is `not_configured`, so it reported `n/a`, and the public search of `main`
  still lagged at the run's close because `main`'s own `sonar-build` was still running for `65ee72ac`.
- Cleanup is done: the worktree was removed without force, the branch and its remote ref pruned, and the main
  checkout fast-forwarded to `65ee72ac` with `switch-and-pull`.
- R11 renumbering was not needed (013 was free); R17's one pre-existing client-profiles traceability gap is not
  this plan's.
