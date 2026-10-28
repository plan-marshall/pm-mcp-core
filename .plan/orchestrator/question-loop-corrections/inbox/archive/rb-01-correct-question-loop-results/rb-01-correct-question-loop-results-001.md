envelope_version=1
sender_type=plan
sender_id=rb-01-correct-question-loop-results
epic=question-loop-corrections
kind=landing
created=2026-10-28T12:36:13Z

```landing-facts
schema=landing-facts/1
plan_id=rb-01-correct-question-loop-results
epic=question-loop-corrections
pr=#213
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.2021
executor_version=0.1.2021
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

## Deliverables

Two pull requests: `plan-marshall/pm-mcp-core#213` (merged as `3c4e58fd`) and `plan-marshall/plan-marshall-documentation#127` (merged as `fa69be39`). Every clause carries `order: none`: the plan is a documentation correction, so no failing output exists for any Done-when and no test was added or changed.

- **D1 — overstated result statements corrected. Met.** Q1 names the two warm Claude Code runs whose continuation no generation took; Q2 says only what was observable (two hosts withheld the tool, Antigravity's output carries no tool call); Q3 and Q7 report the waiting fresh askers against the criterion instead of as a pass; the held-call cut reads 12, 12, and 9 of 12 with the three Antigravity cuts unexplained; R3 says its detection times are the driver's supervision values; Q5 says the bound is specified, not measured. Test: none. Order: none. Commits: pm `c07ccd2`, doc `c3e7675`, `63ea525`, `265fff7`.
- **D2 — measurement out of normative text. Met.** Client Profiles 01 § Resumability keeps shipped values (`hold` `false`, `resume` `null`) and links the evaluation rows for the candidates; Client Profiles 02, Model Work 01 and Model Work 06 keep the rule and link Q2/Q5/R2/R3/R4. Test: none. Order: none. Commits: pm `b94f016`, `43e7a5e`, `ff56f06`, `9d00913`, `bf258e0`, `8028ea2`.
- **D3 — limits completed. Met.** The raw evidence (deleted with the worktree, no copy), the missing Sonnet repeat and Codex are named; OpenCode reads 2.1.2 to 2.1.4 throughout. Test: none. Order: none. Commits: doc `94623b7`, `db7fdb8`.
- **D4 — developer document. Met.** Its top states branch `verification/question-loop-experiment` (`488ce47`), not `main`; the history narration and the proposals section are gone; no new document. Test: none. Order: none. Commit: doc `584ecef`.
- **D5 — open questions. Met.** Six questions with what each blocks, options and recommendation, covering the plan's three bullets and the developer document's proposals. Test: none. Order: none. Commits: doc `5f2dfcb`, `669423c`.

**The plan-level clause is NOT met as written.** "`trace.py` reports `BROKEN LINKS 0` … on the final commit of the `plan-marshall-documentation` pull request, against this repository's `main` after its pull request merged" fails: against merged `main` (`8cf297b9`) the check reports **BROKEN LINKS 12** and four undefined watch ids (`PM-WATCH-CLIENT-14`, `PM-WATCH-PHASE-106`, `PM-WATCH-PHASE-110`, `PM-WATCH-PHASE-111`). None of the twelve originates in this diff — they are links of the documentation repository and of `pm-mcp-core` files this plan did not edit, pointing at watch ids that other plans' landings removed (`pm-mcp-core` `model-work/01:357` still cites `PM-WATCH-CLIENT-14`, which `implementation-watch/client-profiles.adoc` no longer defines; `phase-workflows.adoc` dropped three items its index still counts). The same check against this plan's own content is clean: `BROKEN LINKS 0` and no undefined identifier on both branch heads.

Per-pushed-head test runs: the project publishes no `module-tests` or `test-compile`, so the gate canonical is the test run. Local `verify -Ppre-commit` at `b94f016`, `da6bc68e` and `2fd4cacb`; CI's `build / gate` and `integration-tests / test` green on `2fd4cacb` and `8028ea21`.

## Personas and skills

- Production-code persona: not applicable (no production code).
- Documentation persona (`persona-documenter`): applied to every deliverable edit.
- `persona-code-reviewer`: applied to every self-review round and to the triage of the CodeRabbit findings.
- `persona-security-expert`: applied to the two cross-repository reads, restricted to documentation files under the operator's authorisation.
- The spec declares no `Skills:` or `Persona:` lines: **skills: none named**. No skill was added for surface drift; the surface stayed the seven files of the plan's Expected Surface.

## Hypotheses

- Call-cut figures "12, 12, and 9 of 12" and "lost nothing on any of the three" — **confirmed**: `evaluation.adoc:205` (twelve of twelve, twelve of twelve, nine of twelve), `:206` ("a held call the host cuts is no loss on these hosts"), `doc/developer/question-loop-experiment.adoc:124` ("On every host the same worker waited again"), `:166` ("on all three hosts the worker survives the cut"), `model-work/06…:431` (the cut both as the reason for the check and as harmless).
- R3 detection times equal the driver's supervision values (15 s, 270 s, 180 s) — **confirmed**: `evaluation.adoc:205` (15.0, 270.0, 180.1 s) against `question-loop-experiment.adoc:127` (the same three values as `QuestionDriver.Host`).
- The measurement narration sits at Client Profiles 01 § Resumability, `model-work/06…:405`, `model-work/01…:239`, `client-profiles/02…:90,96` — **confirmed at those locations** (Client Profiles 01's narration is the R1/R2 basis column and the "measurement ran three runs" paragraph at `:714-741`; the inline-settings range the claim also named is normative and untouched).
- Q5's reach of the bound is not read from the event log — **confirmed**: `evaluation.adoc:646` (§ Limits).
- The OBSERVED claims held at HEAD too: Q2's `evaluation.adoc:165`/`:642`, Q3's `:170`, Model Work 06's `:431`, and the raw evidence's absence (no `question-loop-verification` worktree, no `runs.jsonl`/`invocations.jsonl` in the repository).

## Findings

**Self-review rounds 1–5** (rounds 4 and 5 operator-authorised after the 3-round limit):
- Round 1: 13 findings (2 high, 5 medium, 5 low, 1 info). High: Q3 asserted "Every warm group passes" while its own cell recorded a Claude Code warm failure; Client Profiles 01 asserted `max_hold_seconds`/`max_age_seconds` `3600` against shipped rows of `false`/`null`. Also: Antigravity's `ask_question` "not called in 36 runs" left in the host-values row; Model Work 06 still narrating R2; contradictory limits about the deleted evidence; Q7 citing Q3's criterion; open-question formatting and two overstated claims. All fixed; one preference (residual past-tense rationale in the developer document) declined.
- Round 2: 10 findings (2 medium, 8 low). The limits still gave two incompatible accounts of the surviving evidence; Q3's failure verdict was not self-substantiating; three section pointers did not contain the fact they trailed; a grammar break in Model Work 01. Fixed; Q5 bound-form phrasing declined.
- Round 3: 3 low (unscoped Q2 verdict, a pointer, a subject–verb error). Fixed after the limit.
- Round 4 (operator-authorised): 1 low (`max_age_seconds` called a declared value where the specification now declares none). Fixed after the limit.
- Round 5 (operator-authorised, final): 3 low (two subject–verb agreements, a dangling "named there"). Fixed after the limit; no round has reported nothing open, hence `self-review: budget-exhausted`.

**CodeRabbit, pm #213** (reviewed `da6bc68` and `2fd4cac`):
- Inline, Minor: the Q5 paragraph still stated measured behaviour — **fixed** in `8028ea2`, thread answered and resolved.
- Additional, Trivial, unverified: derive the resumability table from the built-in profile set or add a build-time parity check — **answered, not applied**: it changes a build and the specification's production, outside a documentation correction; recorded as a design question for its own plan.
- Final review on `2fd4cac`: no actionable comments.

**CI**: no failing check on any head except the two environment tests below.

## Reviewers

| Reviewer | Required / optional | Reviewed | History |
|---|---|---|---|
| CodeRabbit | required, `quota_limited` | `da6bc68` (10:06Z) and `2fd4cac` (11:30Z), both qualifying, `kind=reviewed` | Refused on quota at 06:44Z, 08:18Z, 08:28Z (escalated `@coderabbitai full review`) and 10:09Z; retried at 09:54Z (**85.5 minutes after the 08:28Z refusal, under the 90-minute rule**) and at 11:28Z, both successful |
| Sourcery | optional | none | Quota-refused 06:44Z; "4 days and 16 hours" from that point; optional, does not gate |

On the documentation PR neither bot is active: two `@coderabbitai review` triggers and one `@sourcery-ai review` produced no reply, no check and no movement in six hours. That PR was merged on the operator's explicit selection, with no review.

## Operator questions

1. "The spec spans two git repositories, but the runbook lane is single-repo … how should I proceed?" — options {Block lane; use /plan-marshall | Authorize cross-repo extension | In-repo part only}; **selected: Authorize cross-repo extension**.
2. "The self-review has reached its 3-round limit … how do you want to proceed?" — options {Confirming round 4 | Proceed, report budget-exhausted}; **selected: Confirming round 4**.
3. "Round 4 found three more low items, all fixed and pushed … how do you want to close the self-review?" — options {One final confirming round | Proceed with budget-exhausted}; **selected: One final confirming round**.
4. "Ready to merge the first pull request, pm-mcp-core#213 …" — options {Merge now (standing authorization) | Wait for a review of the new head | Stop}; **selected: Merge now**.
5. "I am ready to enqueue #213 … `origin/main` moved again … the repository has an active merge queue …" — options {Enqueue now, let the queue rebase | Merge base and repeat the cycle | Stop with the PR open}; **selected: Enqueue now, let the queue rebase**.
6. "Ready for the second pull request, documentation#127 … no review bot exists … trace.py fails on drift this plan does not own" — options {Merge docs #127, record both gaps | Hold until the drift is fixed | Stop with #127 open}; **selected: Merge docs #127, record both gaps**.
7. "documentation#127 cannot be merged through the CI abstraction … 403 on the ruleset probe" — options {Operator merges in the UI | Authorise gh pr merge directly | Leave open}; **selected: Authorise gh pr merge directly**.

Free-text operator messages: none.

## Rule check

Guard: `guard: none on this harness (unenforced)` — the harness has no pre-tool-use hook, so this section counts the run's own actions; no guard log line exists for this run.

Deviations and authorisations, in order:
- **Cross-repository scope** (operator-authorised): the lane forbids reading or editing outside the repository root and opening a second PR; this plan required it. Work stayed in a worktree of each repository, never in a checkout's working tree.
- **`trace.py` invoked directly**: the spec's Done-when names `python3 .claude/skills/doc-review/scripts/trace.py doc …`, which no executor notation resolves; it was run in that documented form, from the documentation worktree, four times.
- **`gh pr merge` invoked directly** (operator-authorised after `ci pr merge` and `ci pr safe-merge` both returned 403 on the documentation repository's ruleset probe): one call, squash with branch deletion.
- **Pre-enqueue base merge skipped for #213** (operator-authorised): `origin/main` moved three times in the hour before enqueue and the repository has an active merge queue, which built the merge group and reported success.
- **Reviewer retry at 09:54Z was 85.5 minutes after the 08:28Z refusal**, not the required 90. The reviewer accepted the trigger; the breach is recorded rather than hidden.
- **Spec copy**: read from the main checkout (`spec_source: main_checkout`); `spec_differ: true` was reported on both resolves, so the ledger copy differs and the read was not silent.
- No other section-1 rule was broken by the run or by a sub-agent: the sub-agents reported no shell file-operation, and no build was routed or backgrounded.
- Restarts/re-dispatches: none; five self-review sub-agent dispatches, each returned normally.

## Build server

- Setup preflight on the worktree: **disabled** (project not registered, no daemon probe). Every build therefore ran in-process: `mechanism=in_process_fallback`, `reason=disabled`.
- `build-routing` on the worktree answered `auto` with the executor present; no routed job existed.
- Bound expiries: 300 s (scoped base check), 1200 s and 1800 s (full gate). The 1800 s run stopped after 6044 tests with the same single environment failure, and the base-branch check showed that failure on `origin/main` too.
- No `killed` job. No degraded build beyond the disabled-daemon fallback.
- Local gate red: `SubprocessJobRunnerTest.pidReuse` (`No setsid in [/usr/bin, /bin]`) and `AgentStartedDetectorTest.theOperatorBinaryMatches` — both reproduced on `origin/main` in a throwaway worktree, both in `pm-runtime`, which this documentation change does not touch.

## Residue

- The plan-level `trace.py` clause is **not met** on merged `main`: 12 broken links and four undefined watch ids, all pre-existing and owned by other landings (`PM-WATCH-CLIENT-14` in `pm-mcp-core` `model-work/01:357` and in the documentation repository's roadmap, articles and requirements; `PM-WATCH-PHASE-106/110/111` in articles; `phase-workflows.adoc` lost three items its index still counts). Repairing them belongs to the plans that removed the watch items.
- The CodeRabbit parity-check finding (derive the resumability table or add a build-time check) is answered but unscheduled; it needs its own plan.
- Sourcery's quota reopens around 2026-11-01; it is optional and never reviewed either PR.
- The documentation checkout was left on its own `docs/macos27-on-device-coding` branch: `switch-and-pull` was deliberately not run there, because moving that checkout would disturb another session's working tree.
- `spec_differ: true` on both resolves: the ledger worktree copy of the spec differs from the main-checkout copy read here. Nobody reconciled them; the orchestrator owns the ledger.
