# Epic: pm-mcp-core work packages, roadmap Milestones 2 to 4

slug: pm-mcp-core-m2-m4

> Hand-written narrative for one epic under `.plan/orchestrator/pm-mcp-core-m2-m4/`. The layout and
> authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. The ledger JSON files
> (`status.json`, the `queue/{PLAN-ID}.json` rows) and `resume_anchor.md` are the machine
> authority; any statement here that conflicts with them is stale prose.
>
> START HERE and the Ordered Queue are not in this file. They live in the generated,
> git-tracked `queue-view.md` next to it (see the Persist / Stop-Resume Contract in that
> standard). `queue-view.md` is never hand-edited. A merge conflict in it is never merged by
> hand: merge the source files, run
> `python3 .plan/execute-script.py plan-marshall:plan-orchestrator:orchestrator regenerate-view --slug pm-mcp-core-m2-m4`
> on the merged tree, and `git add` the result.

## Vision

Implement every work package of `doc/plans/` in this repository — the 35 packages that serve
roadmap Milestones 2 to 4 — one plan per package, each landing as one pull request here with its
documentation pull request in `plan-marshall-documentation`. The work is too large for one plan
because the packages form a dependency graph about nine packages deep that spans `pm-core`,
`pm-workflow` and `pm-runtime`. The index `doc/plans/README.adoc` stays the only place that states
a package's status and dependencies; this epic sequences against it and never replaces it. The
epic is done when every package in that index is `done`, or is recorded here as blocked on a
decision or a dependency outside this repository.

## Queue annotations

- PLAN-05 — not a package of `doc/plans/`: staged from the operator's HTTP rules, because the
  existing provider tests use hand-made `FakeServer` classes and `CiHttpClient` imports
  `java.net.http`. It adds its own package file and index row.
- PLAN-19 — parked: waits on `pm-mcp-clients` / `profile-documents` (`open` there on 2026-10-09).
  It gates PLAN-25 `waits` and PLAN-26 `role-sets-and-model-caller`, and through them most of
  WS-04 and WS-05 — the first external dependency on the critical path.
- PLAN-27 — staged, but also waits on `pm-mcp-clients` / `exec-read-denied-paths`.
- PLAN-04, PLAN-06 — shipped 2026-10-11 as #25 and #26.
- PLAN-09, PLAN-11, PLAN-12, PLAN-14 — ready since PLAN-06 shipped, but all four declare
  `core.store/` and overlap each other: run one at a time unless the operator pairs them.
  PLAN-09 also declares `runtime.start/` and so overlaps PLAN-08.
- PLAN-07 — unblocked on 2026-10-10: its only dependency PLAN-02 shipped.
- PLAN-15 — PLAN-03 shipped; still waits on PLAN-06 and PLAN-11.
- PLAN-38 — shipped as #24 on 2026-10-10; WS-09 closes with it.
- PLAN-36 — serves Milestone 6; ready now and disjoint by package from every other plan, so it
  is a good filler for a free slot. It sits at the queue end because a row's order is fixed when
  it is staged; ask for it by id when a slot is free.
- PLAN-39, PLAN-40 — WS-10 module documentation backfill, staged 2026-10-11; both wait for PLAN-08,
  PLAN-10, PLAN-11, PLAN-36 and are disjoint from each other.
- PLAN-42, PLAN-43 — parked: added to the index by #35 and #36 (2026-10-12), `blocked` on use cases
  in `plan-marshall-documentation` (whose #38 and #39 merged the same day — re-check the index).
  They rework parts of PLAN-13, PLAN-14, PLAN-17, PLAN-24, PLAN-25 and PLAN-41's Rules 7 and 10.
- PLAN-45 — parked: added by #37 (2026-10-12), `blocked` on UC-PRJ-1 and PLAN-42.
- PLAN-44 — shipped as #38, gaps materially open; PLAN-54 completed the package (#48, minor gaps). PLAN-17 is unblocked; PLAN-56 holds the residuals and blocks nothing.
- PLAN-55 — parked: `chat`, added by #46 (2026-10-13), blocked; PLAN-51, PLAN-52 and PLAN-53 now also depend on it.
- PLAN-46 to PLAN-53 — parked: blocked packages added by #39 to #42 (2026-10-12), specs generated from their package files; re-ground before emitting.
- PLAN-37 — parked: `blocked` in the index on the operator's tool decision.

## Decisions

- 2026-10-09 — One orchestrator epic for the whole repository, not one per milestone or per
  module. Alternatives: an epic per milestone; an epic per module. Rationale: the dependencies
  cross every such cut (Milestone 2's `stalled-scope-detection` needs Milestone 3's
  `store-substrate` and `workflow-engine`; `workflow-engine` in `pm-workflow` needs
  `store-substrate` in `pm-core`), and an epic orders only its own queue.
- 2026-10-09 — `parallelization_scope` set to 3 by the operator on first init (project default
  suggestion was 1).
- 2026-10-09 — The operator switched `orchestrator.use_worktree` on right after init. The ledger
  lives in the shared ledger worktree and reaches `main` only through `land`; the ignore rule
  that makes it trackable and the knob itself are PR #16.

- 2026-10-09 — Decomposed into 8 workstreams and 37 staged plans: one plan per work package (36)
  plus PLAN-05 `http-baseline`. The operator's planning rules — Java 25 baseline, HTTP calls
  through `cui-http`, HTTP tests on `cui-test-mockwebserver-junit5` — stand in every spec as
  Standing Constraints.
- 2026-10-09 — Split guard: PLAN-08, PLAN-27 and PLAN-36 carry five bullets each and proceed
  unsplit; the work package is the unit the index defines, and the index leaves a split to the
  trace matrix of the executing plan.
- 2026-10-09 — Superseded the same day: the specs first declared `PmMcpLogMessages` as shared
  surface, which made the gate ask on 553 of 666 plan pairs.
- 2026-10-09 — Operator decision (AskUserQuestion): log identifiers stay in the three-digit
  scheme and are allocated per workstream, same offsets in each level — WS-01 x50-x59, WS-02
  x60-x69, WS-03 x70-x79, WS-04 x80-x89, WS-05 x90-x99, WS-07 x02-x09; WS-06 and WS-08 get none.
  Alternatives: widen to four digits with a block per plan (recommended, declined); no allocation.
  `PmMcpLogMessages` is therefore NOT a declared surface any more. This is a deliberate
  under-declaration: plans of one workstream still share a block, and the guard is
  `PmMcpLogMessagesTest`, which refuses an identifier used twice.

- 2026-10-10 — PLAN-02 `primitive-spi-and-validators` landed as #18 (abaf95d); landing record
  `landings/PLAN-02.md`. Inbox drain of its 25 messages: the landing reconciled, and the 24
  candidate lessons `primitive-spi-and-validators-001.md` to `-024.md` each promoted to the
  lessons store as 2026-10-10-07-001 to -024 in the same order. Messages 003 to 010 are `pm-core`
  engineering rules from defects fixed inside #18. Messages 001, 002 and 011 to 024 concern
  plan-marshall tooling; the store of this repository does not own that bundle, so they were
  filed with the foreign-store override and still need routing to plan-marshall.

- 2026-10-10 — PLAN-03 `workspace-confinement` landed as #19 (7d99648); landing record
  `landings/PLAN-03.md`. Inbox drain of its 20 messages: the landing reconciled; of the 19
  candidate lessons, 6 promoted (009, 010, 014, 017, 018, 019 as lessons 2026-10-10-08-001 to
  -006), 11 folded (001 to 003, 012, 015 as recurrences into lessons -07-001, -07-013, -07-016;
  004 to 008 and 011 into the specs not yet started), 2 discarded as uncorroborated (013, 016).
- 2026-10-10 — #19 reworded the working rules on `main` by operator decision: no draft pull
  request claims a package, and the documentation pull request need not name the code pull
  request. The 34 specs not yet started (PLAN-04 to PLAN-37) were regenerated with these rules,
  with the settled confinement design (a refused project path is a `ConfinementOutcome` value, no
  checked exception) and with a rule against leaving open a design choice that changes the file
  set. The specs of PLAN-01, PLAN-02 and PLAN-03 were left as launched.
- 2026-10-10 — Package homes re-checked against the package table after PLAN-03 refuted both of
  its inferred homes. Covered by a package description and kept: PLAN-17 `workflow.finalize`,
  PLAN-20 `core.service`, PLAN-25 `workflow.hypermedia`, PLAN-31 `runtime.launch`. Not covered and
  now labelled as guesses that will need an operator answer at refine: PLAN-09 and PLAN-23
  (`runtime.start`, and `core.store` for PLAN-23), PLAN-11 (`runtime.security`), PLAN-34
  (`workflow.model`). Their declared surfaces are unchanged, because no better home is known.
- 2026-10-10 — Collision recorded: PLAN-01 (#20) and PLAN-03 (#19) both took WARN 150 in the shared
  WS-01 block of `PmMcpLogMessages`, which no spec declares by the block decision of 2026-10-09.
  The pairing gate could not predict it. PLAN-01 was told through its mailbox.

- 2026-10-10 — Operator asked for the review-bot fix as a plan. Staged PLAN-38
  `review-bot-re-review` in a new workstream WS-09 `project-configuration`: set
  `re_review_on_loopback` to `true` so the review step posts `/review` after a fix commit. The
  Docker Hub pull rate limit of the reviewer image is a change in `cuioss/cuioss-organization`
  and was handed to the operator as a command for that repository; it is not a plan of this epic.

- 2026-10-10 — PLAN-01 `lock-manager-and-leases` landed as #20 (c2b608b); landing record
  `landings/PLAN-01.md`. Inbox drain of its 20 messages: the landing reconciled; of the 19
  candidate lessons, 15 promoted (002, 003, 007 to 019 as lessons 2026-10-10-14-001 to -015 in that
  order), 4 folded: 001 into lesson -14-011 (same `scope_creep_check` failure, also filed as 015),
  004 as a recurrence into lesson -07-016, 005 as a recurrence into lesson -07-013 and the
  review-bot Open Defect (PLAN-38 owns it), 006 into the same-block identifier Open Defect.
  Lessons 007 to 009 are dispatcher reports the plan itself marks as not confirmed in its logs.

- 2026-10-10 — Operator decision: all 32 plan-marshall tooling lessons of this repository's store
  routed to plan-marshall's `lessons-routing` epic inbox as `pm-mcp-core-m2-m4-001.md` to `-032.md`
  (sender orchestrator, kind candidate-lesson), then retired here with verdict `superseded`. The
  13 lessons left here are `pm-core`, `pm-runtime` and `pm-mcp-parent` rules.

- 2026-10-10 — The reviewer image no longer comes from Docker Hub: cuioss-organization #321
  (merged 0faf9b2, released v0.41.0) pulls it from `ghcr.io/cuioss/pr-agent-mirror` by digest
  sha256:b253845c…, byte-identical to `pragent/pr-agent`. The pin moved here with #23 (067032c,
  opened and merged by plan-marshall-release-bot): `reusable-cuioss-review-bot.yml@73712a1 # v0.41.0`,
  checked against the dereferenced tag. Not a plan of this epic.

- 2026-10-10 — PLAN-38 `review-bot-re-review` landed as #24 (66950bb); landing record
  `landings/PLAN-38.md`. `re_review_on_loopback` is `true` on `main`; the proof on a pull request
  was not possible on #24 (both required bots skip `.plan/` paths) and became a Watch by operator
  decision. Inbox drain of its 7 messages: the landing reconciled; the 6 candidate lessons, all
  plan-marshall tooling, routed to plan-marshall `lessons-routing` as `pm-mcp-core-m2-m4-033.md` to
  `-038.md` under the routing decision above, nothing kept in this store.

- 2026-10-10 — Operator decision: `parallelization_scope` raised from 3 to 4, because PLAN-05
  `http-baseline` is disjoint from the running PLAN-04, PLAN-06 and PLAN-07 (only
  `doc/plans/README.adoc` shared). PLAN-36 is also disjoint and is the next filler.

- 2026-10-11 — PLAN-04 `service-unit` landed as #25 (0ca1b24) and PLAN-06 `store-substrate` as
  #26 (cbdd04e); landing records `landings/PLAN-04.md`, `landings/PLAN-06.md`. Inbox drain of 29
  messages: both landings reconciled; of the 27 candidate lessons, 9 about this repository's code
  promoted here (PLAN-04 003 to 006 and PLAN-06 004 to 008 as lessons 2026-10-11-07-002 to -010),
  18 about plan-marshall routed to plan-marshall `lessons-routing` as `pm-mcp-core-m2-m4-039.md` to
  `-056.md`.
- 2026-10-11 — The review-bot proof of PLAN-38 was taken on #26: plan-marshall posted `/review`
  (06:30:39Z), the run started from it (the posting account passed the author-association guard),
  pulled the image from ghcr.io and produced a review of head 64414e3. Watch retired.
- 2026-10-11 — Advisory delivered to PLAN-07's mailbox (`inbox/to/workflow-dsl-parser-and-ast/`):
  its manifest carries the stale `re_review_on_loopback: false`; set it plan-locally.

- 2026-10-11 — PLAN-05 `http-baseline` landed as #28 (268d2c0); landing record
  `landings/PLAN-05.md`. WS-08 closes. The provider tests run on the mock web server and the
  `FakeServer` classes are gone. First plan with `re_review_on_loopback: true` from the start:
  plan-marshall posted `/review` after both fix commits and the bot reviewed each time, no stall.
  Inbox drain of 10 messages: the landing reconciled; 8 lessons routed to plan-marshall
  `lessons-routing` as `pm-mcp-core-m2-m4-057.md` to `-064.md`; 1 folded as a recurrence into lesson
  2026-10-10-14-001 (pre-commit recipe).

- 2026-10-11 — Correction: the documentation of PLAN-01 was not missing. `plan-marshall-documentation`
  #24 ("document the lock manager, lock order and leases of PM-IMPL-7", LogMessages 50/151/250)
  merged on 2026-10-10; the plan's landing message predated it. Watch retired. Likewise PLAN-04's
  documentation (#26 there) and PLAN-06's (#28 there) are merged; their Watches retired. PLAN-02's
  (#20 there) and PLAN-07's (#27 there) are open.

- 2026-10-11 — Operator asked whether the per-package documentation pull requests are sensible.
  Analysis of doc PRs #19–#28: about a third is contract change (specification, concepts); the rest
  copies code state (trace class lists, roadmap checkboxes, watch counts, log table, package
  descriptions) or describes code (developer guides). Staged, at the operator's request, as a plain
  Claude Code session plan outside this epic: `.plan/temp/doc-structure-single-pr-plan.md` of
  pm-mcp-core. When its PRs merge, the Standing Constraints of the specs not yet started are
  rewritten from its report.

- 2026-10-11 — PLAN-07 `workflow-dsl-parser-and-ast` landed as #27 (d091527), its documentation as
  `plan-marshall-documentation` #27; landing record `landings/PLAN-07.md`. The mailbox advisory was
  applied (manifest `re_review_on_loopback: true`), so the re-review ran without an override. Inbox
  drain of 20 messages: the landing reconciled; 1 lesson promoted here (2026-10-11-16-001,
  `pm-workflow`), 18 routed to plan-marshall `lessons-routing` as `pm-mcp-core-m2-m4-065.md` to
  `-082.md`. Closed the stale re-review defect: PLAN-07 was the last plan composed before #24.
  Still open as an operator question: whether one bot review is enough to merge (asked on #26).
- 2026-10-11 — Spec quality: PLAN-07 hit two contradictions between specification documents only in
  execute (instance header vs store schema; `question` as a reserved word). Specs not yet started
  name specification sections the same way and can carry the same kind of contradiction.

- 2026-10-11 — Operator decision: every module documents itself — `{module}/README.adoc`, and all
  other documentation below `{module}/doc/`: developer documentation per package
  (`doc/developer/{package}.adoc`) and ADRs (`doc/adr/{package}/NNN-{slug}.adoc`, repository-wide in
  `doc/adr/`). The per-package file and ADR directory names, and numbering per package directory so
  parallel plans of different packages never collide, were chosen by the orchestrator. New
  workstream WS-10 `module-documentation`; staged PLAN-39 `module-readmes-and-adrs` (rule, template,
  READMEs, ADRs for decisions already shipped) and PLAN-40 `module-developer-docs` (developer files,
  taking over the guides in `plan-marshall-documentation`). The 26 staged and parked specs got the
  Standing Constraint "Module documentation" and their package's developer file and ADR directory in
  the Expected Surface; the "Documents" bullet no longer sends developer documentation to
  `plan-marshall-documentation`. Running PLAN-08, PLAN-10, PLAN-11 and PLAN-36 keep their specs; the
  backfill covers them. The session plan `.plan/temp/doc-structure-single-pr-plan.md` was aligned:
  its decision 1 is settled and its pm-mcp-core moves belong to PLAN-40.

- 2026-10-11 — PLAN-36 `code-analysis-base` landed as #31 (d1608b7) and PLAN-08 `runtime-lifecycle`
  as #29 (e163bf8); landing records `landings/PLAN-36.md`, `landings/PLAN-08.md`. WS-07 closes.
  Inbox drain of 42 messages: both landings reconciled; of the 40 candidate lessons, 8 about this
  repository's code promoted here (2026-10-11-20-001 to -008), 32 routed to plan-marshall
  `lessons-routing` as `pm-mcp-core-m2-m4-083.md` to `-114.md`. Both plans re-reviewed without an
  override. PLAN-36 added the test-scope dependencies `json-schema-validator` (pinned 1.5.8) and
  `archunit-junit5` with the operator's consent recorded at refine.
- 2026-10-11 — The WS-01 WARN block 150-159 is full since #29. Allocated WARN 141-149 (free on main
  at e163bf8) as WS-01 overflow, keeping the three-digit scheme of 2026-10-09; PLAN-09 and PLAN-23,
  the WS-01 plans left, were amended.

- 2026-10-12 — PLAN-10 `static-verifier-and-fuzzer` landed as #32 (c0d4b92); landing record
  `landings/PLAN-10.md`. By operator override it implemented part of the rules and added the package
  `static-verifier-remaining-rules` to the index; staged as PLAN-41 in WS-03 (overlaps PLAN-17 in
  `workflow.verify`). Inbox drain of 11 messages: the landing reconciled; 1 lesson promoted here
  (2026-10-12-05-001), 9 routed to plan-marshall as `pm-mcp-core-m2-m4-115.md` to `-123.md`.

- 2026-10-12 — Operator decision (AskUserQuestion): PLAN-13 `workflow-engine` and PLAN-17
  `project-units-and-invariants` both wait for PLAN-41 `static-verifier-remaining-rules`.
  Alternatives: only PLAN-17 waits (recommended); neither waits. Their specs carry the dependency;
  PLAN-41's pull request adds it to the two index rows, as a sanctioned exception to the own-row rule.

- 2026-10-12 — PLAN-41 `static-verifier-remaining-rules` landed as #34 (ebe82b3), the first plan run
  under Antigravity; landing record `landings/PLAN-41.md`. Extended verification at the operator's
  request, including an independent read-only audit of the code against the package and the
  specification (two key findings re-checked by hand): **materially incomplete** although it reported
  7/7 and ADR 001 claims complete coverage — Rule 9 duplicates only, Rule 11b dead, Rule 3 config
  reachability unsound, Rule 6 `stepped_out` rules missing, question class `ask` missing, 23 fixtures
  for ~55 checks, `every-construct.pmwf` trimmed, tests weakened. Process: 0 tokens, missing phase
  boundaries, an empty execute trail, Spotless from Maven Central, no pre-commit/coverage gate on the
  final head, `/review` not re-posted (operator override), no documentation PR.
- 2026-10-12 — Operator decisions (AskUserQuestion): (1) the gaps become a new package and plan,
  PLAN-44 `static-verifier-rule-gaps` (alternatives: reopen the package; accept as is); (2) PLAN-17
  waits for PLAN-44, PLAN-13 does not (alternatives: both wait; neither waits). (3) Antigravity
  issues are filed directly as high priority to plan-marshall `lessons-routing`: 7 findings,
  `pm-mcp-core-m2-m4-124.md` to `-130.md`. Inbox drain: the landing reconciled, 3 lessons promoted
  here (2026-10-12-12-001 to -003).
- 2026-10-12 — #35 and #36 added the packages `operator-wait-and-machine-scope` and
  `phase-sub-workflows` (both `blocked`); staged as parked PLAN-42 and PLAN-43.

- 2026-10-12 — PLAN-11 `enrolment-store` landed as #30 (5010969), run under Claude Code (inferred);
  landing record `landings/PLAN-11.md`. 48 h and 11.6 M tokens, nine finalize rounds, `/review`
  re-posted ten times with no override, documentation PR merged. New package `runtime.enrolment`
  (the spec's `runtime.security` guess was wrong) plus recovery steps in `runtime.start` and
  `LiveWork` in `core.service`. Inbox drain of 18 messages: the landing reconciled; 6 lessons
  promoted here (2026-10-12-13-001 to -006), 1 folded into 2026-10-11-20-001, 10 routed to
  plan-marshall as `pm-mcp-core-m2-m4-134.md` to `-143.md`. #37 added `project-onboarding-unit`
  (`blocked`), staged as parked PLAN-45.

- 2026-10-12 — PLAN-44 `static-verifier-rule-gaps` landed as #38 (6caa663), the first plan run under
  opencode; landing record `landings/PLAN-44.md`. Extended verification with an independent code
  audit (five key claims re-checked by hand): **gaps materially open**. Rule 9 untouched; Rule 3
  booleans only, the transition check deleted as "infeasible"; Rule 6 made conditional against the
  specification; tests still count enum constants, with weakenings kept plus one new one. The package
  file and ADRs 002–005 were rewritten to match what shipped; the project-units dependency cell was
  missed. Process trail far better than PLAN-41's, but tokens 0, phases unclosed, finalize build logs
  not archived. Three high-priority opencode findings filed: `pm-mcp-core-m2-m4-146.md` to `-148.md`.
  Lessons: 2 promoted here (2026-10-12-18-001, -002), 2 routed (`-144.md`, `-145.md`).
- 2026-10-12 — Operator decisions (AskUserQuestion): (1) one more remediation plan, PLAN-54
  (alternatives: split in two; record only); (2) PLAN-54 runs under Claude Code, written into its
  spec (alternatives: harness open; Claude Code for all plans); (3) PLAN-17 waits for PLAN-54
  (alternative: may start).
- 2026-10-12 — #39 to #42 added eight `blocked` packages; staged as parked PLAN-46 to PLAN-53 from
  their package files. PLAN-45's dependency widened to UC-PRJ-1 to UC-PRJ-3.

- 2026-10-13 — PLAN-13 `workflow-engine` landed as #45 (5a5db68), Claude Code; landing record
  `landings/PLAN-13.md`. Clean: CI green, `/review` after every round with no override, pre-commit
  on the final head, documentation PR #49 merged, and the first plan to follow the WS-10 module
  documentation rule (developer guides plus 9 ADRs). Inbox drain of 19 messages: the landing
  reconciled; 1 lesson promoted (2026-10-13-06-001, specification drift during a run), 1 folded into
  2026-10-10-14-001, 16 routed as `pm-mcp-core-m2-m4-153.md` to `-168.md`. #46 added the `blocked`
  package `chat`, staged as parked PLAN-55.
- 2026-10-13 — Operator rule: the harness is Claude Code unless the operator states otherwise.

- 2026-10-13 — PLAN-18 `verify-not-resolve` landed as #43 (68c6bc3), Claude Code; landing record
  `landings/PLAN-18.md`. CI green, pre-commit on the final head, required bots re-reviewed (one
  override for the optional Sourcery only), module docs plus 3 ADRs. Operator decisions dropped the
  wrapper git-tracked requirement from the specification and limited the forbidden free flags to
  canonical build jobs. Documentation PR #46 open and behind the code. Inbox drain of 24 messages:
  the landing reconciled; 2 lessons promoted (2026-10-13-07-001, -002), 1 folded into 2026-10-11-20-001,
  20 routed (`pm-mcp-core-m2-m4-169.md` to `-188.md`). The stray plan-marshall lesson 2026-10-11-07-001
  in this store was routed as `-189.md` and removed; the store holds no plan-marshall lesson now.

- 2026-10-13 — PLAN-54 `static-verifier-rule-gaps-completion` landed as #48 (36a0bcd), Claude Code;
  landing record `landings/PLAN-54.md`. Extended verification with an independent audit (two key
  residual claims re-checked by hand): **complete with minor gaps** — the Rule 6 regression is
  reverted, the weakened tests are restored, there is a real 79-entry checklist with exact fixtures,
  and every-construct is restored. Residuals: Rule 9 firing not per step; result guards checked on
  calls only; the remediation budget seam is not genuine; `lost` limited to plan units; the Rule 3
  activation was kept; the guide over-claims; the Rule 3 specification issue is not filed.
  Orchestrator judgement: accepted as shipped, PLAN-17 unblocked, residuals staged as PLAN-56
  (blocks nothing). Inbox drain of 14 messages: the landing reconciled, 13 lessons routed
  (`pm-mcp-core-m2-m4-190.md` to `-202.md`). #47 widened the scope of the parked `chat` package.

- 2026-10-13 — PLAN-16 `diagram-generator` landed as #49 (f4b14f2), Claude Code; landing record
  `landings/PLAN-16.md`. Clean: exactly the declared surface, pre-commit on the final head, required
  bots reviewed (override only for the optional Sourcery), module docs plus 10 ADRs. Documentation PR
  #52 open, and it overlaps that repository's still-open #34 (PLAN-10). Inbox drain of 11 messages:
  the landing reconciled; 1 lesson promoted (2026-10-13-10-001), 9 routed (`pm-mcp-core-m2-m4-203.md`
  to `-211.md`).

- 2026-10-13 — PLAN-22 `scenario-replay-harness` landed as #50 (115d7ed), Claude Code; landing record
  `landings/PLAN-22.md`. Clean: declared surface only, pre-commit on the final head, required bots
  reviewed (override for the optional Sourcery only), module docs plus 6 ADRs; documentation PR #53
  open. Inbox drain of 17 messages: the landing reconciled; 1 lesson promoted (2026-10-13-11-001),
  1 folded into 2026-10-10-14-001, 14 routed (`pm-mcp-core-m2-m4-212.md` to `-225.md`).
- 2026-10-13 — Operator decision (AskUserQuestion, overlap prompt): start PLAN-39 alongside PLAN-12,
  PLAN-17 and PLAN-21 despite the shared ADR directories; its backfilled ADRs are renumbered onto the
  free numbers after a rebase before push (rule added to the spec). Alternative: leave the slot
  empty. PLAN-40 stays behind PLAN-12 (`core-store.adoc`).

- 2026-10-13 — Operator rule: every landing analysis verifies that all PRs related to the plan,
  especially its `plan-marshall-documentation` PR, are **merged**, and that their content matches the
  merged code. Each landing record lists them by number with state and merge time. Applied at once to
  all shipped plans: the documentation PRs of PLAN-01, 02, 03, 04, 06, 07, 08, 10, 11, 13, 16, 18, 22
  and 36 (doc #19, 20, 24, 26, 27, 28, 30, 31, 33, 34, 46, 49, 52, 53) are all MERGED; no PR is open
  in `plan-marshall-documentation`. Gaps found: doc #46 (PLAN-18) merged with its single pre-review
  commit, so the mandatory `maxLength` of enrolled patterns is not in the specification; PLAN-41,
  PLAN-44 and PLAN-54 opened no documentation PR, so PM-WATCH-DSL-3/4/5/8/9/11/15/16/29/32 stay open on
  documentation main although the verifier implements them.

- 2026-10-13 — PLAN-12 `session-log-and-generations` landed as #51 (d7b622d), Claude Code; landing
  record `landings/PLAN-12.md`. Code clean (pre-commit on the final head, `/review` after both rounds,
  no override, docs plus 9 ADRs in three modules); it also wrote `runtime.start` sweeps and
  `core.service`. **Not complete under the related-PR rule:** documentation PR #59 is OPEN, and the
  `pm-mcp-server` sweep registration in plan-marshall-mcp is owed (the SNAPSHOT breaks the assembly
  until then). Inbox drain of 12 messages: the landing reconciled; 3 lessons promoted
  (2026-10-13-13-001 to -003), 8 routed (`pm-mcp-core-m2-m4-226.md` to `-233.md`).
- 2026-10-13 — Operator instruction: no new emits; the queue drains (running PLAN-17, PLAN-21,
  PLAN-39 finish first). #52 and #55 changed only parked packages; PLAN-46 to PLAN-53 and PLAN-55
  were regenerated from their package files, and PLAN-42, PLAN-43 and PLAN-45 were marked for
  re-reading before any emit.

- 2026-10-13 — Operator decision: every versioned artifact stays at `format_version` 1 until the
  first release; a format change replaces version 1 in place. Existing bumps are an Open Defect to
  clean up at the end of this epic. Added as a Standing Constraint to every staged and parked spec;
  the running PLAN-17, PLAN-21 and PLAN-39 were told through their mailboxes.

- 2026-10-13 — Operator decision: split `PmMcpLogMessages` into one holder per Maven module
  (`CoreLogMessages`/`PM_CORE`, `RuntimeLogMessages`/`PM_RUNTIME`, and more only where records exist),
  renumbered per holder from the start of each level range; pre-1.0, so the break is intended. It is a
  plain Claude Code session plan outside this queue, `.plan/temp/log-messages-per-module-plan.md` of
  pm-mcp-core, following the git workflow and the quality profile. It runs after the running PLAN-17,
  PLAN-21 and PLAN-39 have landed, because they may still add records. When it lands, the
  per-workstream identifier blocks go away and the staged specs' log bullet is rewritten from its
  report.

- 2026-10-13 — PLAN-21 `entity-addressing` landed as #54 (b25d115), Claude Code; landing record
  `landings/PLAN-21.md`. Code clean (pre-commit on the final head, `/review` x3, override only for the
  optional Sourcery). Reworked in-PR after PLAN-12's `EntryId` landed. It also touched `core.spi` and
  `workflow.verify` (the `lessons-review` rename). **Not complete under the related-PR rule:** doc #56
  is OPEN. PLAN-12's doc #59 is now MERGED, so PLAN-12 is complete. Inbox drain of 9 messages: the
  landing reconciled, 8 lessons routed (`pm-mcp-core-m2-m4-234.md` to `-241.md`). #58 and #59 added
  four `blocked` packages (epic-auto, operator-reports, runtime-control, runtime-metrics), staged as
  parked PLAN-57 to PLAN-60; PLAN-51 and PLAN-53 were regenerated. Practice from now on: re-check a
  spec's premises against the landings since staging, right before emitting it.

- 2026-10-13 — PLAN-39 `module-readmes-and-adrs` landed as #53 (c0ecf33), Claude Code; landing
  record `landings/PLAN-39.md`. Complete (no documentation PR required): 9 module READMEs, the ADR
  template and index, 44 ADRs including 4 repository-wide ones, and the rule in CLAUDE.md. The
  concurrent-numbering rule worked: two collisions, and only its own ADRs were renumbered. ADR 003
  (log identifier blocks) is to be superseded by the log-holder split. Inbox drain of 11 messages:
  the landing reconciled, 10 lessons routed (`pm-mcp-core-m2-m4-242.md` to `-251.md`). #57 moved
  plan-marshall to the `functional-review` / `code-review` finalize steps (required bots kept). #60
  added the `blocked` package `token-usage`, staged as parked PLAN-61. WS-10: PLAN-39 is done,
  PLAN-40 remains.

- 2026-10-13 — PLAN-17 `project-units-and-invariants` landed as #56 (df4b318), Claude Code; landing
  record `landings/PLAN-17.md`. Code clean (8/8 deliverables, pre-commit on the final head, `/review`
  x6, Sonar 0 new issues). Rules 6 (`work_performed`), 7 and 8 of the finalize validator and three
  invariants are named hooks (operator-accepted). Test-scope dependencies `cui-test-juli-logger` and
  `cui-test-generator` were added to pm-workflow with consent. **Not complete under the related-PR
  rule:** doc #57 is OPEN (content matches the code). Inbox drain of 12 messages: the landing
  reconciled, 11 lessons routed (`pm-mcp-core-m2-m4-252.md` to `-262.md`). #61 and #62 added the
  `blocked` packages `process-designer` and `cleanup`, staged as parked PLAN-62 (WS-03) and PLAN-63
  (WS-01). No plan is running: the drain is complete, and the log-holder session plan is next.

- 2026-10-13 — Doc #57 merged (20:44:58Z, fff6552), before the log-holder plan's documentation PR:
  PLAN-17 is complete. Cleanup while the log-holder session plan runs (main at fa58b0d):
  - Re-grounding of the 40 open specs: 236 claims, 99 corroborated, 137 unverifiable (hypotheses
    and verify-first clauses), none left contradicted.
  - Four stale "greenfield" claims rewritten (PLAN-20, PLAN-27 `runtime.job` of PLAN-18; PLAN-25
    `workflow.engine` of PLAN-13/21; PLAN-28 `core.queue` of PLAN-12); PLAN-20's `LiveWork` claim
    updated.
  - PLAN-40 re-scoped to the real gap: 25 packages without a developer file, seven guides still
    in `plan-marshall-documentation`. It now runs after the log-holder plan, because `core-log.adoc`
    describes its result.
  - 19 parked specs (PLAN-42/43/45–53/55/57–63) gained their module-doc paths in Expected Surface;
    their constraints already required them.
  - Watches for doc #57, doc #20 and documentation #36 relocated to `settled.md`.
  - Deferred to the log-holder report: every open spec's log bullet and the holder path in its
    surface.

- 2026-10-14 — Log-holder session plan reported: pm-mcp-core #64 (head a777427, CI green, OPEN) and
  plan-marshall-documentation #68 (OPEN, merge after #64). Verified on the branch: `CoreLogMessages`
  3 records, `RuntimeLogMessages` 54, `WorkflowLogMessages` 3 (= the 57 of `PmMcpLogMessages` plus
  PLAN-17's 3); `PmMcpLogMessages` deleted with no reference left outside `.plan/`; ADR 005
  supersedes ADR 003; `KNOWN_PREFIXES` in `CoreLogMessagesTest`. The operator put the 16 records only
  the assembly uses into `RuntimeLogMessages` (Javadoc "Used by the assembly pm-mcp-server"), not into
  `CoreLogMessages`. The log rule of the 40 open specs was rewritten to "the holder of your module, next
  free identifier", naming each spec's holders. Like the old shared holder, a module's holder stays
  out of the Expected Surface; collisions are resolved on rebase. Emitting waits for #64 on main. The
  ledger's history (landings, older decisions) keeps the old names on purpose.

- 2026-10-14 — Log-holder PRs merged by the session, with the operator's authorisation:
  pm-mcp-core #64 at 06:08:33Z (50827cf; main CI green, SNAPSHOT deployed) and
  plan-marshall-documentation #68 at 06:20:45Z (d9d3e83). No identifier changed since a777427. The
  orchestrator verified on both mains:
  - holders 3/54/3, with `PmMcpLogMessages` absent and no reference left;
  - no `PM_MCP-<n>` row left in `doc/LogMessages.adoc`.

  The shared-identifier defect and the PR watch are relocated to `settled.md`. Emitting may resume.

- 2026-10-14 — Doc #56 (PLAN-21) merged by the orchestrator on the operator's word, at 06:51:30Z
  (42217c3, squash through the merge queue): PLAN-21 is complete.
  - It conflicted with main in `doc/roadmap.adoc` only. The resolution kept main's `[~]` for
    `pm-mcp enter` and #56's `[x]` for entity addressing, with main's specification links.
  - The first resolution attempt pushed a commit with conflict markers (dfb23c7). 3c5342b fixed it
    before the merge, and the squash left no trace on main; main was checked to have no markers.
  - No open documentation PR remains for this epic.

## Open Defects

- **Format version bumped although nothing is released (cleanup at the end of the epic).**
  - `TransactionJournal.FORMAT_VERSION = 3` (`pm-core/src/main/java/de/planmarshall/core/store/TransactionJournal.java`
    l.77, and its Javadoc l.48): raised 1→2 by PLAN-06 (#26, owed audit events) and 2→3 by PLAN-12 (#51,
    delete mode).
  - The bump is also recorded in `pm-core/doc/developer/core-store.adoc` (l.169, l.414) and ADR
    `pm-core/doc/adr/core-store/007-delete-mode-of-the-transaction-journal.adoc`, plus any test fixture
    or test that pins 2 or 3.
  - Open `plan-marshall-documentation` #59 (PLAN-12) puts `"format_version": 3` into the store schema
    specification.

  The target: the journal at version 1 everywhere — code, tests, fixtures, guide, ADR (record the
  decision instead of the bump) and specification. Every other versioned artifact is at 1 (checked on
  2026-10-13 at bed24eb) — source: operator, 2026-10-13.

- Documentation behind the code: (a) the Job Runtime specification lacks PLAN-18's mandatory
  `maxLength` for enrolled patterns (doc #46 merged before the review fixes); (b) the Workflow DSL
  watch items PM-WATCH-DSL-3/4/5/8/9/11/15/16/29/32 are still open on documentation main although
  PLAN-41/44/54 implemented them (no documentation PR). Proposed owner: PLAN-56 (residuals) takes (b);
  (a) is a small direct documentation PR — source: PR check of 2026-10-13.

- Static verifier residuals after PLAN-54: Rule 9 firing per step, result guards on non-call transitions, the remediation budget re-check, `lost` in plan-only sub-workflows, the Rule 3 activation, and documentation over-claims. Owned by PLAN-56 — source: landing PLAN-54.

- No consumer is wired to the confinement check of PLAN-03; `pm-provider-git` was put out of slice
  by operator decision and no staged plan owns the wiring. The specs not yet started now tell each
  package to adopt the check itself — source: landing PLAN-03.
> ↪ Relocated to `settled.md` § "Defect: shared log identifier blocks" — replaced by one holder per module (pm-mcp-core #64, 50827cf)

- The roadmap item behind PLAN-02 is only partly delivered and no staged plan owns the rest:
  validators for `request.md` and pull request bodies (PM-WF-6), the `OutlineValidator` rules that
  need the working tree, task derivation or build extensions, the registry checks that need the
  closed gate list or the keyword list, and the binding of free parameters — source: landing
  PLAN-02 (plan's own statement; not checked against the roadmap).
- `AsciiDocValidator` treats `!===` as the nested-table delimiter; CodeRabbit named `!==`. Neither
  was checked against the AsciiDoc documentation. If `!==` is valid, such a nested table is read
  as ordinary text — source: landing PLAN-02 and lesson 2026-10-10-07-010.


## Watches

> ↪ Relocated to `settled.md` § "Watch: doc #56 of PLAN-21" — #56 merged 2026-10-14T06:51:30Z (42217c3); PLAN-21 complete
- A pm-runtime adapter for `core.spi.PendingEntryReader` over `PendingEntryStore` is owed (PLAN-21) — the daemon wiring.
> ↪ Relocated to `settled.md` § "Watch: the log-holder PRs" — #64 merged 06:08:33Z (50827cf, SNAPSHOT deployed), doc #68 merged 06:20:45Z (d9d3e83)
> ↪ Relocated to `settled.md` § "Watch: pm-mcp-server against the log-holder SNAPSHOT" — plan-marshall-mcp #76 merged 2026-10-14 (e34961b), assembly on `RuntimeLogMessages`
> ↪ Relocated to `settled.md` § "Watch: doc #57 of PLAN-17" — #57 merged 2026-10-13T20:44:58Z (fff6552); PLAN-17 complete
- plan-marshall-mcp still owes (after #76): from PLAN-17, `DefaultUnitInvariantTest` over the real default units and wiring the project-unit loader into plan start/resume (the `config_invalid` refusal); taking its 16 log records over from `RuntimeLogMessages` into a holder of its own; wiring a `PeriodicSweep` with `PendingEntryRecordsSweep` and `FencedSessionGenerationsSweep` once the daemon runs the periodic sweep (new behaviour — the assembly never used `SweepStep`).

> ↪ Relocated to `settled.md` § "Watch: pm-mcp-server and SweepStep.notImplemented" — refuted by #76: no use site in the assembly
- **No production `AuditSink` or `AuditChainVerification` in pm-mcp-core** (only test sinks; `AuditChainVerification.NONE`). Since #76 the daemon starts with `NONE` and `NO_AUDIT_STORE`, a sink that refuses every record (`StartupSequence` then logs `AUDIT_CHAIN_BROKEN_NOT_RECORDED`). PLAN-09 `audit-trail` is the natural owner — check at its re-grounding that its deliverables include the production sink and the startup verification the assembly can wire, and that plan-marshall-mcp swaps them in afterwards — source: plan-marshall-mcp #76 report, corroborated 2026-10-14.

- The specification issue "Workflow DSL Rule 3: define how a `depends_on_config` value activates a guard" is not filed in `plan-marshall-documentation`; its text is in PR #48 and ADR 006 (operator, or PLAN-56).
- Verifier performance on the real merged plan unit (simple-cycle enumeration, 2^N configuration combinations) is untested; no default units live in this repository.

- No plan records its harness (high-priority finding `pm-mcp-core-m2-m4-132.md`). The operator's rule since 2026-10-13: assume Claude Code unless stated otherwise.
- **Non-Claude-Code runs (Antigravity, opencode) need the extended landing verification** (code-vs-package audit, build-log and
  gate check, review trail), because the plan's self-report was unreliable on its first run (seven
  high-priority findings at plan-marshall). Retire when an Antigravity landing passes it cleanly.
> ↪ Relocated to `settled.md` § "Watch: documentation #36 during PLAN-41" — PLAN-44 shipped as #38 against the current specification


- `FindingSchemaParityTest` of PLAN-36 and `WorkflowStatusSchemaParityTest` of PLAN-13 read the sibling checkout of `plan-marshall-documentation`
  (`pm.documentation.root`) and aborts by assumption when it is absent — in CI it most likely never
  runs. Check a CI log of `main`; if skipped, the parity guard exists only on developer machines.
> ↪ Relocated to `settled.md` § "Watch: breaking changes for plan-marshall-mcp" — settled by plan-marshall-mcp #76 (e34961b)

- Closing a channel on a POSIX file drops the process's OS locks on
  it, so `JsonlStoreFile` appends on file-locked stores lose the lock manager's OS lock — PLAN-09
  `audit-trail` must account for it; the fix belongs in the lock manager.

- The `plan-marshall-mcp` package that wires `FileLockManager` must create one instance per runtime
  (a second one can release the first holder's OS lock on POSIX); stated only in the Javadoc.
  Outside this repository — carry to whoever stages that wiring.
- `verify -Ppre-commit` of `pm-mcp-parent` is not idempotent and rewrites files a plan never
  touched (27 `pm-runtime` files on PLAN-01; lesson 2026-10-10-14-001). Every running plan can
  pull unrelated diffs into its PR. A parent POM change, outside this repository.

> ↪ Relocated to `settled.md` § "Watch: doc #20 of PLAN-02" — #20 merged 2026-10-13T11:28:36Z (be27641)
- Token metrics of a plan can be overstated: PLAN-02 recorded 8,809,647 against its own sum of
  6,131,992 (lesson 2026-10-10-07-019). Read later landings' totals with that in mind.

> ↪ Relocated to `settled.md` § "Watch: PR #16 before the first land" — PR #16 merged (f049631) and the first land #17 carried the ledger
> ↪ Relocated to `settled.md` § "Watch: code-analysis-base at decompose" — it joined as PLAN-36 and shipped as #31
> ↪ Relocated to `settled.md` § "Watch: the two files nearly every package edits" — settled by the per-workstream identifier block decision of 2026-10-09
- About eleven packages land in `pm-core` package `core.store` — re-check at each pairing decision.
- Cross-repository blockers: `profile-loader` waits on `pm-mcp-clients` / `profile-documents`;
  `job-confinement-and-environment` waits on `pm-mcp-clients` / `exec-read-denied-paths` —
  re-check when those merge and their `SNAPSHOT` is deployed.
- `differential-test-harness` is `blocked` on an operator tool decision (a Python interpreter in
  the test environment and a `plan-marshall` checkout at the pinned inventory commit).
