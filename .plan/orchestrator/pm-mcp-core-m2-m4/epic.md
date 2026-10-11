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

## Open Defects

- No consumer is wired to the confinement check of PLAN-03; `pm-provider-git` was put out of slice
  by operator decision and no staged plan owns the wiring. The specs not yet started now tell each
  package to adopt the check itself — source: landing PLAN-03.
- Two plans of one workstream can take the same log identifier; it surfaced as a merge conflict
  between #19 and #20. The shared block is per workstream, not per plan — source: landing PLAN-03;
  confirmed by PLAN-01 (inbox 006), which moved to WARN 151. PLAN-04, PLAN-08 and PLAN-09 share the
  same WS-01 block.

- The roadmap item behind PLAN-02 is only partly delivered and no staged plan owns the rest:
  validators for `request.md` and pull request bodies (PM-WF-6), the `OutlineValidator` rules that
  need the working tree, task derivation or build extensions, the registry checks that need the
  closed gate list or the keyword list, and the binding of free parameters — source: landing
  PLAN-02 (plan's own statement; not checked against the roadmap).
- `AsciiDocValidator` treats `!===` as the nested-table delimiter; CodeRabbit named `!==`. Neither
  was checked against the AsciiDoc documentation. If `!==` is valid, such a nested table is read
  as ordinary text — source: landing PLAN-02 and lesson 2026-10-10-07-010.

- Plans composed before #24 merged carry `re_review_on_loopback: false` in their execution
  manifest, because phase 4 copies the step parameters once (lesson routed from PLAN-38, message
  006). PLAN-04 merged under an operator override for it, PLAN-06 set it by hand, PLAN-07 still
  runs with it (advised). PLAN-05 has `true`. Closes when PLAN-07 has landed. Also seen: the operator
  ruled on #26 that one review of the bot is enough and merged without re-reviewing the last three
  fix commits — decide whether that is the standing rule — source: landings PLAN-04, PLAN-06.

## Watches

- PLAN-06 changed the signatures of `AuditEvent` and `CrossScopeReconciler` and the journal format;
  `plan-marshall-mcp` must follow. Closing a channel on a POSIX file drops the process's OS locks on
  it, so `JsonlStoreFile` appends on file-locked stores lose the lock manager's OS lock — PLAN-09
  `audit-trail` must account for it; the fix belongs in the lock manager.

- The `plan-marshall-mcp` package that wires `FileLockManager` must create one instance per runtime
  (a second one can release the first holder's OS lock on POSIX); stated only in the Javadoc.
  Outside this repository — carry to whoever stages that wiring.
- `verify -Ppre-commit` of `pm-mcp-parent` is not idempotent and rewrites files a plan never
  touched (27 `pm-runtime` files on PLAN-01; lesson 2026-10-10-14-001). Every running plan can
  pull unrelated diffs into its PR. A parent POM change, outside this repository.

- `plan-marshall-documentation` #20 (documentation of PLAN-02) was still open on 2026-10-10 at the
  PLAN-03 drain. The index already says `done`. Retire when #20 is merged.
- Token metrics of a plan can be overstated: PLAN-02 recorded 8,809,647 against its own sum of
  6,131,992 (lesson 2026-10-10-07-019). Read later landings' totals with that in mind.

- PR #16 must be on `main`, and the ledger worktree on that base, before the first `land`:
  until then the worktree's `.gitignore` still ignores the ledger.
- `code-analysis-base` (added by #15, serves Milestone 6, depends on nothing, `open`) is in the
  index but outside the Milestone 2 to 4 scope this epic was framed for — decide at `decompose`
  whether it joins the queue.
- Nearly every package is expected to edit both `doc/plans/README.adoc` (its status row) and
  `PmMcpLogMessages` in `pm-core`; two shared files is the threshold at which the disjointness
  gate asks the operator — decide at `decompose` how the specs declare these two files.
- About eleven packages land in `pm-core` package `core.store` — re-check at each pairing decision.
- Cross-repository blockers: `profile-loader` waits on `pm-mcp-clients` / `profile-documents`;
  `job-confinement-and-environment` waits on `pm-mcp-clients` / `exec-read-denied-paths` —
  re-check when those merge and their `SNAPSHOT` is deployed.
- `differential-test-harness` is `blocked` on an operator tool decision (a Python interpreter in
  the test environment and a `plan-marshall` checkout at the pinned inventory commit).
