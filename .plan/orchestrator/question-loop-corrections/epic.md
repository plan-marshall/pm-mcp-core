# Epic: Question-loop verification: corrections and follow-up

slug: question-loop-corrections

> Hand-written narrative for one epic under `.plan/orchestrator/question-loop-corrections/`. The layout and
> authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. The ledger JSON files
> (`status.json`, the `queue/{PLAN-ID}.json` rows) and `resume_anchor.md` are the machine
> authority; any statement here that conflicts with them is stale prose.
>
> START HERE and the Ordered Queue are not in this file. They live in the generated,
> git-tracked `queue-view.md` next to it. `queue-view.md` is never hand-edited. A merge conflict in it is never merged by
> hand: merge the source files, run
> `python3 .plan/execute-script.py plan-marshall:plan-orchestrator:orchestrator regenerate-view --slug question-loop-corrections`
> on the merged tree, and `git add` the result.

## Vision

The work package `question-loop-verification` of `plan-marshall-mcp` measured a worker's question loop (Q1 to Q7, R1 to
R4) on Claude Code, OpenCode and Antigravity, but only at smoke scale (3 runs, 2 fixtures), and landed as
`plan-marshall-mcp` #107, `plan-marshall-documentation` #124, `pm-mcp-core` #197, and the follow-up package
`question-loop-follow-up` (`plan-marshall-mcp` #108). A review of the landing on 2026-10-27 found that several published
statements are stronger than the runs support, that measurement figures were written into normative specification
text, that the raw evidence was deleted with the plan's worktree, and that the follow-up package cannot run as written
(a dependency cycle with `question-continuation`, no durable evidence, a weakened scale and criteria rule). This epic
corrects the published results, repairs the follow-up package and its dependencies, and then runs the re-measurement at
the specified scale, so that `question-continuation` implements settled values and not smoke-scale candidates. Done
when the four plans have landed and the roadmap item of Milestone 4 can be checked on settled values.

## Queue annotations

- PLAN-01 — touches the four specification parts #197 changed in this repository and, in `plan-marshall-documentation`,
  `doc/specification/evaluation.adoc`, `doc/developer/question-loop-experiment.adoc` and
  `doc/discussions/open-questions.adoc`; disjoint from PLAN-02.
- PLAN-02 — touches `doc/plans/` here and `doc/plans/` of `plan-marshall-mcp`; disjoint from PLAN-01. Its change to
  `doc/plans/question-continuation.adoc` concerns PLAN-73 of the sibling epic `pm-mcp-core-m2-m4`, shipped as #201 (see
  Watches). Its sixth deliverable follows the OpenCode 2.x worker of #206 and documentation #125.
- PLAN-03 — shipped 2026-11-05 (`plan-marshall-mcp` #114 and #118, `plan-marshall-documentation` #144; plan
  `question-loop-run-follow-up-measurement`), analyzed 2026-11-10 from the merged diffs and the archived plan directory
  (`landings/PLAN-03.md`). Q5 bound 5 and R4 large work of Claude Code were not run.
- PLAN-04 — the OpenCode and Antigravity part, split from PLAN-03 on 2026-10-29; staged and deferred. Launch conditions
  (spec, Standing Constraints): PLAN-03 landed (met 2026-11-05), a consumer package for the OpenCode values (Milestone 14
  or `pm-mcp-clients`) or the operator's recorded decision to go without (not met, re-checked 2026-11-10), Antigravity
  usable (about 2026-10-31 07:10 CET; not confirmed by the operator since), and the run budget and data home confirmed
  (not yet). Its Expected Surface names the developer document `question-loop-measurement.adoc` since 2026-11-10.

## Decisions

- 2026-10-27 — Epic hosted in `pm-mcp-core`: the corrected specifications and the package `question-continuation` live
  here, and its ledger lands through the shared ledger worktree. Alternatives: `plan-marshall-mcp` (its ledger is not in a
  worktree, so landing would need a branch switch in its main checkout, and the core specifications would not resolve as
  a surface there).
- 2026-10-27 — Three plans in two workstreams: corrections of the published results (WS-01) and the follow-up (WS-02:
  package revision, then the re-measurement). The re-measurement stays a separate plan because it needs an operator run
  budget and runs in another repository.
- 2026-10-27 — Parallelization scope 2 (operator).
- 2026-10-27 — Two specification changes landed after decompose and were incorporated into the three plan specs before
  the plans were emitted: `pm-mcp-core` #206 (the profile member `headless.inline_settings` and the OpenCode 2.x worker,
  values provisional until verified) and `plan-marshall-documentation` #125 (`PM-CLIENT-2`, Roadmap Milestone 14, the open
  questions `opencode-worker-login` and `opencode-event-source`). Effect: PLAN-01 names, among the limits, that the OpenCode
  runs had no inline settings, and adds its open questions beside the two new ones; PLAN-02 gains a sixth deliverable (the
  follow-up package states the configuration of its OpenCode runs with the unset defaults as matched control, hands the
  2.x profile to Milestone 14, records a decision on the data home, and takes `inline_settings` as the form of a schema
  addition); PLAN-03 carries those as constraints and waits also on the data-home decision. No Expected Surface changed.
  Also found while reading: `question-continuation` is partly built (PLAN-73 shipped as #201, library core), which the
  PLAN-01 and PLAN-02 specs had read as staged. The decision on the control runs, the data home and the 2.x profile owner
  is left to the operator at refine, as the specs say.
- 2026-10-28 — PLAN-02 landed (`pm-mcp-core` #212, `6010dbc7`; `plan-marshall-mcp` #110, `da9d6d8`). Decisions the plan
  recorded with the operator: the evidence store is a branch of `cuioss/plan-marshall-telemetry`; Codex is out of scope
  and named not measured; the `done` of a measurement package keeps the build-only definition and states in the row that
  the follow-up carries the unmet part; the inline-settings control runs for the cheap items only; the data home is
  serial on the operator's data home; R3 `CALL_CUT` changes the criterion; the Q4 reference accepts the expression form.
  Consequence for this epic: PLAN-03 no longer changes any specification of this repository; the settled values go to
  `client-profile-measured-values`. PLAN-03's Expected Surface lost two paths and its Deliverable 4 was rewritten in the
  same act (two verdicts persisted).
- 2026-10-28 — PLAN-01 landed (`pm-mcp-core` #213, `3c4e58fd`; `plan-marshall-documentation` #127, `fa69be3`). Its seven
  declared paths are exactly the paths changed. Two deliverables are not clean: D3 omitted the limit that the OpenCode runs
  had no inline settings (the plan read the older main-checkout copy of its spec, `spec_differ: true`), and the plan-level
  clause `BROKEN LINKS 0` failed on merged `main` for reasons outside the diff. Operator decisions the plan recorded: it
  ran across two repositories, a fifth and final self-review round, and #127 merged without a review bot through
  `gh pr merge` directly. Consequence for this epic: PLAN-03 may extend `evaluation.adoc` on the corrected rows; the
  missing limit is offered to the operator as a one-sentence direct pull request (see Open Defects).
- 2026-10-28 — PLAN-03 made decision-driven, not scale-driven (operator). The harnesses change nearly daily, so values
  measured over days are stale on arrival, and the 20 runs of the original package exist only to test its 95 % and 90 %
  rate criteria (Q1, Q2, Q4, Q5, R1, R2); the other criteria are "every run" and need no statistics. Decided: base of 3
  runs per fixture, host and form (the smoke scale, driver defects fixed); the rate criteria become "every run passes",
  with a group whose runs disagree extended to 10 and reported with its rate; Q5's bound runs sequentially (`3` first,
  `4` and `5` only if a run reached it); R1 and R2 run longest first (3,600 s, resume after 1 h) and descend only on a
  failure; client version and model recorded per run. The plan amends the package, its criteria and the Evaluation
  Reference's "specified scale" first, before any run. Consequence: no row claims the specified scale, and every verdict
  states its run count and version; a 95 % reliability is no longer claimed, the runtime's acknowledgement and re-offer
  path covers a failed hold. Supersedes the 5 and 8 runs recorded earlier the same day. Alternatives not taken: 20 runs
  for all items (the 240 h default); 20 only for the rate items (the cost is mostly there); dropping the measurement (the
  values stay provisional).
- 2026-10-29 — PLAN-03 split by host (operator, after a benefit-versus-cost analysis; `next` emitted nothing). Reason: the
  harnesses change nearly daily, so a measured value is a statement about the client versions it was measured on and goes
  stale within days; the repeatable driver and the evidence store keep their value, the numbers do not. The OpenCode 2.x
  profile (Milestone 14) and the shipped profile documents of `pm-mcp-clients` have no work package, so OpenCode and
  Antigravity values have no consumer yet, and Antigravity is unavailable before about 2026-10-31. Decided: PLAN-03 keeps
  the package amendment, the extraction of the driver for all three hosts, the evidence store and the Claude Code groups;
  PLAN-04 (new, staged, deferred) takes tasks 3 and 4, the OpenCode and Antigravity groups, their values and the close of
  the package. The package stays open until PLAN-04's part is done; the bound is settled for Claude Code as host-specific.
  Alternatives not taken: emit PLAN-03 whole (spends budget on values without a consumer, and waits on Antigravity);
  defer it entirely (leaves the driver and the Claude Code values, the one part with a consumer, undone).
- 2026-10-30 — Hand-off and limit (operator). The operator launches PLAN-03 from `plan-marshall-mcp` on `main`, with the
  sibling path to the ledger copy of the spec, and the landing is analyzed from the PR and the final report, because the
  plan cannot reach this epic's inbox. The missing no-inline-settings limit stays with PLAN-03, as its spec says.
- 2026-10-30 — Budget and parallel limit (operator). Budget accepted at a cap of 60 USD for the Claude Code groups, R4 large
  work last. PLAN-03 also measures how many workers Claude Code sustains at once (1, 3, 6, and 12 if 6 is clean) and runs
  its groups at that limit; written into its spec as a finding, not a shipped value.
- 2026-10-30 — Q6's quorum moves to PLAN-04 (operator), because it votes on OpenCode as well and settles none of PLAN-03's
  values; PLAN-03 keeps Q6's consultation part. PLAN-04's hand-off takes the same sibling path as PLAN-03's.
- 2026-11-10 — PLAN-03 analyzed as shipped (`plan-marshall-mcp` #114 `61daf53`, #118 `e22f6c1`;
  `plan-marshall-documentation` #144 `2071fbc`; all merged 2026-11-05). The cap of 60 USD was replaced by 45 USD by the
  operator on 2026-10-31 (package decision record, no reason recorded); spent 41.89 USD on `claude-haiku-4-5` plus 1.76 USD
  on `claude-sonnet-5-5` outside the cap. Parallel limit 12; Q1–Q4, Q6 (consultation), Q7, R1–R3 pass; R4 small work
  figures only; Q5 fails on Haiku at bounds 3 and 4 and passes on Sonnet. Not run: Q5 bound 5 (cap rule) and R4 large
  work (operator, 2026-11-01). Raw worker outputs are not in the evidence store (operator, 2026-11-01). The package's own
  Done When is met only in part (question bound unsettled; `verify -Pintegration-tests` red, accepted in finalize and not
  recorded in the package). The three-layer read found layer-3 defects in the measurement code (see Open Defects). The
  developer document is `question-loop-measurement.adoc`; `question-loop-experiment.adoc` was deleted by documentation
  #145. PLAN-04's spec was re-scoped in the same act (claim 1, Expected Surface).
- 2026-10-27 — The `pm-mcp-core` executor in the main checkout was stale (0.1.1924 against the registry's 0.1.2017) and
  was regenerated from the plugin cache before scaffolding; the old file is kept in the session scratchpad. The shared
  ledger worktree was fast-forwarded from #78 to `origin/main` (#195), with no pending paths.

## Open Defects

- The raw evidence of the smoke run (`runs.jsonl`, stub logs, per-invocation archives under `target/question-loop/` in
  the plan's worktree `plan-marshall-mcp/.plan/local/worktrees/question-loop-verification`) was deleted when the
  worktree was removed; no copy exists on this machine. The figures in the evaluation reference can no longer be checked
  against a source. — source: landing review 2026-10-27 (PLAN-01 records it, PLAN-02 prevents a repeat). PLAN-02 landed
  2026-10-28: the follow-up package now names the evidence store (the branch `question-loop-follow-up` of
  `cuioss/plan-marshall-telemetry`) and checks it in its Done When; the branch does not exist until PLAN-03 creates it.
  PLAN-01 (`pm-mcp-core` #213, merged 2026-10-28) records the loss. Resolved for this epic.
- The landing of PLAN-02 (`rb-02-revise-follow-up-package-001.md`) is incomplete: `total_tokens` is `unknown`, so its
  token total is unobserved, not zero. It was reconciled as far as its facts go; a manual paste from that run may still
  surface the token figure. — source: `inbox landing-check`, 2026-10-28

- The landing of PLAN-01 (`rb-01-correct-question-loop-results-001.md`) is incomplete the same way: `total_tokens` is
  `unknown`. — source: `inbox landing-check`, 2026-10-28
- Resolved 2026-11-10: the Limits of `evaluation.adoc` now say that the OpenCode runs of the smoke run had no inline
  settings (documentation `main`, `evaluation.adoc:1004`, by PLAN-03 / #144). — source: PLAN-01 analysis 2026-10-28.
- The measurement code PLAN-03 landed carries layer-3 defects (`landings/PLAN-03.md` § Layer 3): `runs.jsonl` is
  appended without a lock from up to 12 threads (`QuestionResults.java:260`); a re-aggregation from the evidence store
  passes Q2's and R1/R2's output-read clauses vacuously because missing worker outputs read as empty
  (`RunView.java:124`, `:196`); the fresh half of Q3 cannot fail because the driver's own teardown counts as the stop
  (`QuestionResults.java:859`); a JVM abort leaves running workers alive (`QuestionDriver.java:675`, `:699`); a gated
  run without `pm.experiment.hosts` launches all three harnesses (`QuestionLoopMeasurement.java:225`). The first, second,
  fourth and fifth bear on PLAN-04's runs. — source: PLAN-03 analysis 2026-11-10; reported to the operator, a fix plan only
  on the operator's word.
- The package `question-loop-follow-up` (`plan-marshall-mcp` `main`) leaves four record gaps PLAN-03 owed: the
  `-Pintegration-tests` deviation from task 1's Done When is not recorded; the cap change of 2026-10-31 has no reason; Q3's
  pass rests on a reading of the criterion that is recorded only as open; the index row still titles the package "at
  the specified scale". — source: PLAN-03 analysis 2026-11-10; a small direct pull request there, or fold into PLAN-04;
  the operator decides.
- Merged `main` fails `BROKEN LINKS 0` (the landing reports 12 and four undefined watch ids; this analysis confirmed
  `PM-WATCH-CLIENT-14` cited at `model-work/01-roles-and-configuration.adoc:357` and not defined in
  `implementation-watch/client-profiles.adoc`). None comes from this epic's diffs; the owners are the plans that removed
  the watch items. — source: PLAN-01 landing, partly verified 2026-10-28. Measured 2026-10-30 on `origin/main` of
  documentation (`53a514e`) and core (`03f182c9`): 1 broken link, 0 undefined watch ids and 0 undefined requirement ids. The
  one is `pm-runtime/doc/developer/runtime-config.adoc:10`, a missing anchor `#PM-EXT-6` in
  `requirements/16-model-work.adoc`; it is outside this epic. The documentation's `origin/main` has no
  `.github/workflows/` file, so the `links.yml` that `CLAUDE.md` names does not run from there.
- PLAN-03's hand-off does not work as written: its command runs in the `plan-marshall-mcp` checkout and points at
  `.plan/orchestrator/question-loop-corrections/plans/…`, which exists only in `pm-mcp-core`. A sibling or absolute path
  reads the spec, but `inbox detect` answers `not_orchestrator_pointer` for it, and the epic does not resolve in that
  repository's store (`exists: false`), so the plan cannot file its landing to the inbox. The same holds for PLAN-04.
  — source: tested 2026-10-30. Settled for PLAN-03 by the operator's decision of 2026-10-30 (see Decisions) and written into
  the specs of both plans. The operator also brings that checkout to `main` first; it stands on the
  merged branch `fix/exclude-orchestrator-ledger-push-builds` (#111).

## Watches

- Launching a plan from the main checkout reads that checkout's copy of the spec, which is older than the ledger copy
  when the orchestrator has incorporated changes (PLAN-01's landing: `spec_differ: true`, and its D3 limit was lost to it).
  Before PLAN-03 is emitted, compare the two copies or hand the ledger copy over. The copies were identical on
  2026-10-29, before the host split rewrote PLAN-03 and staged PLAN-04 in the ledger copy; landing the ledger fast-forwards
  the main checkout, so compare again after the land. Compared 2026-10-30: PLAN-03, PLAN-04 and `epic.md` were
  byte-identical in both copies (before this day's `epic.md` edit, which is narrative only). Retired for PLAN-03 2026-10-30, launched from the ledger copy; PLAN-04 compares again.
- Client versions, re-checked 2026-10-30: Claude Code 2.1.314 (also the npm latest; the smoke run was 2.1.306 to 2.1.310),
  OpenCode 2.2.3 (smoke 2.1.2 to 2.1.4), `agy` 1.4.7 (smoke 1.4.7 at the end). PLAN-03 measured on Claude Code 2.1.316.
  — PLAN-04 re-reads them at its first run and says which PLAN-03 rows it re-ran.
- The documentation worktree `/Users/oliver/git/plan-marshall-documentation-question-loop-follow-up` of PLAN-03 is
  still there, on `docs/question-loop-follow-up-measurement` whose upstream is gone (#144 merged). — retire when the
  operator has removed it.

- `pm-mcp-core-m2-m4` PLAN-73 (`question-continuation`) read `question-loop-verification` as `done` and its
  dependencies as met, although no `headless.resumability`, `questions` or bound value was settled. A finding was sent to
  that epic's inbox on 2026-10-27. On the same day the operator reported #73 landed; the ledger shows it `shipped` as
  #201, titled "partial: library core". PLAN-02 landed 2026-10-28: `question-continuation` now depends on
  `client-profile-measured-values` and its bullet *Measured first* names the follow-up, and its first bullet of *What*
  was corrected to what #201 built (the caller, the wiring, the loss path and the restart sweep are still missing).
  Still open: two statements in the sibling epic name `question-loop-verification` as the source of the values
  (`plans/PLAN-73-question-continuation.md` and `epic.md` of `pm-mcp-core-m2-m4`); a finding went to that epic's inbox
  on 2026-10-28. — retire when that epic has reconciled them.
- The OpenCode 2.x items of Roadmap Milestone 14 (verify and ship the 2.x profile; the version range, launch, version
  probe, `events` mapping and tool names) have no package in `doc/plans/` of this repository. Re-checked at the landing
  of PLAN-02, 2026-10-28: still none. `client-profile-measured-values` takes only the `inline_settings` member and the
  measured values; PLAN-02 named Milestone 14 as owner and created no package. PLAN-03's OpenCode values reach the
  profile only once a package ships it. Re-checked 2026-11-10: still none (`client-profile-rendering` and
  `profile-verification-stages`, both 2026-11-02, do not carry these items); this is PLAN-04's launch condition 2. — tell
  the operator; stage a package when the operator decides.
- The shipped profile documents are `classpath:/profiles/<client_id>.json` resources of `pm-api` in `pm-mcp-clients`,
  not of `pm-core`, so the measured values need a landing there. `client-profile-measured-values` names it as owed;
  `pm-mcp-clients` has no work package for it and no index dependency records it. — re-check when
  `client-profile-measured-values` is launched.
- `doc/plans/worker-integration-tests.adoc:14` still names `question-loop-verification` as the measurer of the real
  harnesses; it lies outside PLAN-02's declared surface and was not edited. — a row in a later plan, or a fold into one.
- Resolved 2026-10-30: PLAN-02's reported 2843 `BROKEN LINKS` for `plan-marshall-mcp` is an artefact, not a defect count.
  `trace.py` takes the documentation tree and the core tree as its only two arguments; given `plan-marshall-mcp` as the
  second (2936 on `origin/main` `b66fe12`), it reports the links into core as missing, among them about 2840 GitHub URLs of
  `pm-mcp-core`. The check has no reading for that repository. Neither PLAN-03's spec nor the package file carries the
  clause, so PLAN-03 does not repeat it.
- The run budget PLAN-03 waits on: task 6 of the package names about 55 USD and about 240 h as the default, an estimate
  from the smoke figures. The scale was reduced 2026-10-28 and the plan split by host 2026-10-29 (see Decisions); the
  orchestrator's rough estimate of 12 to 25 USD and 15 to 25 h of machine time covered all three hosts, is unmeasured, and
  was not recomputed for the Claude Code groups alone (smoke figure: 24 Claude Code runs cost 2.3 to 2.9 USD). Task 6 of
  the package recomputes it per part. Recomputed 2026-10-30 for the Claude Code groups from the smoke figures of
  `evaluation.adoc` (estimate, unmeasured at this scale): 270 base runs plus 24 to reproduce a smoke group, 28 to 36 USD and
  about 32 to 49 h serial; with Q5's further bounds, groups extended to 10 and Q6's voters, 41 to 52 USD. Not included: R4
  large work (36 runs, one smoke run took up to 18.2 million tokens on OpenCode) and the Sonnet repeats. Accepted for PLAN-03 2026-10-30 at a cap of 60 USD; the parallel-limit runs add 48 to 72 runs
  (5 to 9 USD); with Q6's quorum moved to PLAN-04 that leaves about 5 to 16 USD for R4 large work in the case with extras. Replaced by the operator
  on 2026-10-31 by a cap of 45 USD; PLAN-03 spent 41.89 USD (9.47 USD of it on the parallel limit) plus 1.76 USD of Sonnet
  repeats outside the cap. — the operator confirms a budget for PLAN-04 before it is emitted.
- Gemini / Antigravity is available from about 2026-10-31 07:10 CET (operator, 2026-10-28 18:10 CET, "in 2 d 13 h"). Since
  the split this binds PLAN-04's Antigravity groups, not PLAN-03. — retire when the Antigravity groups have run, or move
  the date if the operator gives a new one.
- `question-continuation` should read `headless.resumability`, the `questions` values and the bound from the profile at
  runtime and fall back conservatively, since measured values go stale with the harness version. Outside PLAN-03 and this
  epic. — raise with the owner of `question-continuation` (`pm-mcp-core-m2-m4`) when its remaining package is staged.
- Resolved 2026-10-27: the local main checkout of `plan-marshall-mcp` pulled #107 and #108 and is level with
  `origin/main` (`3b93248`).
