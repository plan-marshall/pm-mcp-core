envelope_version=1
sender_type=plan
sender_id=rb-v2-187-build-output-parser-statement-and-sweep-wording-residue
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-25T22:59:25Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-187-build-output-parser-statement-and-sweep-wording-residue
epic=pm-mcp-core-m2-m4-v2
pr=#620
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=2
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-187 — landing

Standing rules: **r7**; rules read and applied: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (R12 none, R13 no stored format, R14 because the sweep's statements are touched, R17 lifted). The spec and `standing-rules.md` were read at session start (ledger worktree copy).

## 1. Deliverables

| Deliverable / clause | Verdict | Test | Order | Failing run | Commit |
|---|---|---|---|---|---|
| D1(a) shared rule in `TestCounts.from` + each parser's own rules named against its source; false "states it beside its count scan" dropped | met | none (statement only) | none | none | `a27ba206`, corrected in `a1b1fffd` |
| D1(b) `described` `@return` states `<SimpleName> with no message` and blank-as-is | met | none | none | none | `a27ba206` |
| D1(c) guard decision at the `run` catch as a comment pointing to the Javadoc; no test added | met | none | none | none | `a27ba206` |
| D2(a) ADR `runtime-start/016` amended stamp in the neighbours' form | met | none | none | none | `da78821f` |
| D2(b) guide orphan "A fault of" re-flowed | met | none | none | none | `da78821f` |
| D2(c) literal named as the report contract in the display names | met | none | none | none | `da78821f` |
| no behaviour changed (main diff comment lines only) | met | — | none | none | all |
| `verify -Ppre-commit` green | met | full suite | none | none | `a1b1fffd` |
| `verify -pl pm-runtime -am` green | met | 8548 tests | none | none | `a1b1fffd` |
| open Sonar issues fixed or dismissed | met | — | none | none | head `82e7625e` |

All Done-when clauses carry `order: none`: the change is comments, Javadoc, `@DisplayName` and `.adoc` only, so no failing test was captured and none is claimed.

Per-head test runs: `da78821f` — full `verify` 232 s (12917 tests), `-Ppre-commit` 356 s, `verify -pl pm-runtime -am` 157 s; `a1b1fffd` — `-Ppre-commit` 352 s, targeted 158 s (8548 tests); `8d730e29` — full `verify` 894 s; `a2869c2f` — full `verify` 878 s; final head `82e7625e` — `test-compile` 6 s plus CI's full Maven build/test (1123 s / 1056 s) green. The final head's compiled source is byte-identical to `a2869c2f`; the `82e7625e` delta is root `doc/` and `.plan/orchestrator` only.

## 2. Personas and skills

- Persona for both deliverables: `persona-documenter` (the spec's `Persona: documenter`).
- D1 skills: `pm-dev-java:javadoc`, `pm-dev-java:java-core`.
- D2 skills: `pm-documents:ref-asciidoc`, `pm-dev-java:javadoc`, `pm-dev-java:junit-core`.
- All were loaded as standards before their deliverable. No surface drift: only the five declared files changed; no skill was added.

## 3. Hypotheses

- OBSERVED — the Javadoc says "no rule is shared" while all five parsers call `TestCounts.from`: confirmed at `BuildOutputParser.java:113-116` (`c6986b48`) vs `TestCounts.java:138-148` and the five callers (Gradle `:156`, Jest `:227`, Maven `:635`, pyproject `:695`, TAP `:468`).
- OBSERVED — the decision stands on a private method's Javadoc and `@return` says "the type label": confirmed (`PendingEntryRecordsSweep.java:342-352` at `c6986b48`).
- OBSERVED — ADR `016:61-66` has no amended stamp; the guide has the orphan line: confirmed (stamps at `:51`, `:55`, `:56`; guide "A fault of" at `:136`).
- HYPOTHESIS — the literal's display name does not name the contract: **confirmed** (`PendingEntryRecordsSweepTest.java:745`, `:787` at `c6986b48`; literals at `:766`, `:801`).
- Correction from the re-ground record was verified: TAP has its own rule Javadoc (`TapOutputParser.java:426-432`), so "only Jest" was wrong; the final statement names each parser's own rules.

## 4. Findings

- Self-review round 1 (sub-agent, `persona-code-reviewer`): (1, major) the reworded text still claimed the parsers state their rules — false for Gradle/pyproject; fixed in `a1b1fffd`. (2, minor) rule index incomplete; fixed. (3, minor) shared-rule summary omitted the negative per-runner condition; fixed. (4) no PR/Sonar yet — resolved by PR #620 and the Sonar read. (5) two gate runs lacked completion records — job ids/log paths recorded here. (6) run-catch Javadoc universal is asserted from `entryIds()` only — pre-existing 186 text, info. (7–8) info, accepted.
- Self-review round 2: all three fixes verified line-by-line; no open findings (`self-review: clear`).
- CI: green on every head; final head `82e7625e` all required checks success.
- PR comments: CodeRabbit "No actionable comments were generated in the recent review"; Sourcery "Approved."; Sonar "Quality Gate passed". The rate-limit notices are not findings. No unresolved review threads.

## 5. Reviewers

- **coderabbit** (required; quota-limited; `review_complete_after_handling`): refused on quota at 2026-11-25T18:59:05Z ("review limit reached"). Per §6 step 7 the lane waited 90 minutes (wait timed out with no new comments at 20:40Z) and re-triggered at 20:40Z. Review landed 20:43Z: marker `kind=reviewed`, `coveredCommitId=a1b1fffd`, "No actionable comments were generated". That qualifying review covers the later merge heads under `review_complete_after_handling`; no re-trigger was posted for them.
- **sourcery** (optional; quota-limited): refused at 18:59:24Z; later submitted an approving review at 21:10:34Z ("Sourcery assessment — Approved."). Optional, not gating.

## 6. Operator questions

None asked. The reviewer-quota policy was executed as written (§6 step 7), without asking the operator.

## 7. Rule check

- `guard: none on this harness (unenforced)` — no guard log exists for this run, so the check counts the run's own actions.
- Spec read: `spec_source=ledger_worktree`; no `spec_differ` marker.
- Executor skew: `match` (executor 0.1.2183, lane 0.1.2183) at session start; the landing resolve reports executor version 0.1.2184.
- Shell: no shell file operations (all reads via file tools); `git` and the build/CI executor commands only; every call one command, no pipes/`&&`/loops/heredocs.
- Git: base brought in by merge only; no stash/restore/checkout/rebase/force-push; cleanup used `git worktree remove` (not the git-workflow remove verb).
- `.plan/` state: wrote only `.plan/temp/` (progress + landing) and the `ci` body slots returned by `pr prepare-body` / `pr prepare-comment`; no edit under `.plan/orchestrator/`.
- Commit trailer on every commit, including both merge commits. No behaviour change.

## 8. Build server

- Setup preflight: `ready` (registered: true).
- Mechanism: `daemon_longpoll` for every dispatched build; 0 `in_process`.
- Bound expiry: `8d730e29` full `verify` hit the 600 s explicit bound (job `a7f1e472`, `timeout_source: explicit`), then was re-run at a raised 1500 s bound and passed (job `e8d0153a`, 894 s). No launch-fault retry, no degraded fallback, no killed job.
- `runtime_stamp_reason`: none.

## 9. Residue

- Info: the colon list names Maven's four tail rules but not its `cut` arm; that arm is stated two sentences above (this Javadoc) with ADR `core-build/011`, so the document is complete.
- Info: "a negative block count" describes the branch (a digit run past `Long.MAX_VALUE` reaches the `< 0` fallback); it is true of the code and Jest's own Javadoc phrases the same guard by its trigger.
- Pre-existing (not introduced): the `described` Javadoc's universal "no input reaches that catch with a message-less fault" rests on `entryIds()` alone; `isResidue`'s uncaught paths at `:458-467` are not shown by reading. Recorded for a later residue.
- The base moved three times during the run (`90505ad2`, `250b2f08`, `5ae4fd1c`); each was merged, the final head `82e7625e` is fully based on `5ae4fd1c`, and the merge queue landed it as `03fdfe57`.
- Optional Sourcery was rate-limited at trigger time and reviewed later; not gating.
