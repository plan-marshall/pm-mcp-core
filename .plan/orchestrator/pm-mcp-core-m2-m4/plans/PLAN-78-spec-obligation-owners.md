# PLAN-78: Give the new specification obligations of the article findings an owner in the work packages

epic: pm-mcp-core-m2-m4
workstream: WS-10

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-78-spec-obligation-owners.md` and is queued as one row file, `queue/PLAN-78.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

pm-mcp-core #84 (`5197490`, engine specifications for plan-marshall-documentation #84) added obligations to the specifications of the engine, and amended only one package file, `doc/plans/runtime-control.adoc`. A search of `doc/plans/` at `c281f6c` finds none of `egress`, `update_evidence`, `agent_started`, `Seatbelt`, `harness_update_unverified`, `extension_isolation`, navigation figures, the writing skill, the comparison footprint or `PM-WATCH-GEN-59` in any other package file, so a plan that implements a package would not build them and the traceability would not notice. This plan adds each obligation to the package file that should build it, as bullets of § What in the form of the bullets around them, with the specification link, and adjusts Trace and Done When where an obligation needs a test of its own. It is documentation only, one pull request in this repository, and it works on the package files as they are on `main` when it starts.

The operator authorised the amendment on 2026-10-17 ("yes"). The orchestrator regenerates the specs of the amended packages afterwards; the plan does not touch the ledger.

## Deliverables

Each deliverable names the obligation, the specification part it comes from, and the package the orchestrator proposes as its owner. The owner is a proposal: read the package file and the neighbouring ones before writing, move the obligation to the package whose _Where_ it fits, and say so in the pull request body. The deliverable counts are those of the package files at `c281f6c`.

1. **Seatbelt confinement and the worker egress proxy** (`job-runtime/02-confinement-and-environment.adoc` § macOS Confinement, § Worker Egress, the "Not covered" bullet; `client-profiles/01-profile-schema.adoc` `endpoints`) → `job-confinement-and-environment` (6 bullets today). The profile, the same read and write sets as Landlock, the network rule per job kind, the per-release verification on every supported macOS version, and the proxy a worker reaches the network through.
2. **The git identity check** (`PM-WATCH-GEN-59`, `PM-EXEC-5`) → `job-confinement-and-environment`, or the package that builds the commit path if the watch item and the code point elsewhere. Read the watch item in `plan-marshall-documentation` `doc/implementation-watch/cross-cutting.adoc` first.
3. **Update evidence and the profile stages** (`client-profiles/01-profile-schema.adoc`: `update_evidence` with `code_signature`, `package_provenance` and `none`, the supported version range, `endpoints`, `extension_isolation`, the `isolation` and `egress` stages, usage parsing cross-checked against the harness's own record, and no worker class while a harness's MCP configuration is not exclusive) → `profile-loader` (3 bullets today).
4. **Worker launch** (`model-work/04-worker-launch.adoc`: the writing skill required for every task whose links carry free text, derived from the link schemas; the shared part of the launch prompt byte-identical per role and first; workers load no harness plugins, hooks, MCP servers, endpoints or environment from settings; harness enrolment; the launch refusals `harness_not_enrolled`, `harness_update_unverified` and `harness_version_unsupported`; `store-schemas/10-job-and-lesson-records.adoc` `refusal_reason`; `store-schemas/11-enrolment-store.adoc` § Self-Update Within Range) → `worker-launch` (4 bullets today).
5. **The `agent_started` signal in the task queue** (`store-schemas/16-task-queue.adoc`: the `signal` enum of the task record's `signals[]` and of the queue entry, the action `restart`) → `task-queue-and-protocol` (6 bullets today).
6. **The `agent_started` signal in supervision** (`model-work/03-supervision-and-recycling.adoc` the signal and its basis, the worker's recorded process tree running an enrolled harness executable or `pm-operator`; `timeouts/01-job-budgets-and-waits.adoc` the compiled threshold) → `supervisor` (5 bullets today), or `worker-activity` (6) where the detection reads the activity records. Decide from the two package files.
7. **Navigation figures** (`model-work/09-completion-usage-and-spend.adoc`: cache-read share, revisits, language-server use, fallback-to-read rate, each with its population and `indeterminate`; shown in the completion report's section _tokens_) → `worker-activity` (6 bullets today). `token-usage` is the other candidate but holds 12 bullets, the upper limit of the band; do not push it over 12.
8. **The comparison footprint** (`workflow-dsl/07-loop-back-diagrams-verification.adoc` § Comparison Footprint: both versions over the same tasks, several times each, tokens and revisits per run, measured or estimated) → `differential-test-harness` (3 bullets today), or `role-qualification` (5) if the footprint is read from a qualification run.
9. **The remainder of #84 that names no package.** The restraint ladder of the finalize step `review-simplify` (`phase-workflows/04-finalize-step-bands.adoc`), the truncation report of the hypermedia format (`hypermedia-format/01-representation-and-link-forms.adoc` rule 4: a truncation names where the full text is read), and the upgrade checked against the build attestation (`runtime-lifecycle/01-startup-recovery-drain.adoc`, `store-schemas/12-machine-stores.adoc` `sha256` shown only). For each: read the package files for an owner. `runtime-control` already mentions the attestation (amended by #84): check that it covers the upgrade and amend only what it omits. Where no package owns an obligation, do not invent one: list it in the pull request body under **No owner found**.
10. **Not edited here, listed in the body.** `job-runner` (PLAN-20) is running: its package file is deleted by its own pull request, so editing it here would conflict. The termination step 3a (the launcher stays as the job's process, descendants of a worker and a probe ended with the job) and the refusal reason in the pipeline table are PLAN-20's; list both under **Owed to a running plan**.
11. **Conservation and band.** Every package file keeps its old bullets unchanged and gains the new ones; the pull request body lists, per package, the bullets before and after and the specification parts linked. A package that would end above 12 bullets is not amended past 12: ask the operator at refine whether to move the obligation or split the package, and list the case under **Needs a split**.

**Done when:** every obligation named above is either a bullet of a package file with its specification link, or listed in the pull request body as having no owner, as owed to a running plan, or as needing a split; no package file is above 12 bullets; no existing bullet changed; `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py /home/oliver/git/plan-marshall-documentation/doc .` reports `BROKEN LINKS 0`; the index `doc/plans/README.adoc` is unchanged unless a package was split.

**Trace starting set:** documentation only, no requirement of its own.

- Requirements: none
- Specification: the parts named in the deliverables
- Watch: none

## Standing Constraints

Set by the operator for every plan of this epic on 2026-10-09, beside the rules of `CLAUDE.md`:

- **Documentation only.** No production code, no test, no POM change. The Maven gates have nothing to build; say so rather than reporting them green.
- **Work the package as the index says** (`doc/plans/README.adoc` § How to Work a Package): the usual plan-marshall flow, a branch, the commits of the plan, a pull request opened at finalize. This plan is not a package of the index: it adds no row and no file of its own. Before starting, list the open pull requests for one that edits a package file named above: such a pull request would be working a package this plan amends, so the amendment of that package waits for it or the operator decides.
- **Review bots are suppressed (operator, 2026-10-17).** Open the pull request with the label `skip-bot-review` (`ci repo label ensure` first, then `pr create --label skip-bot-review`), so that the quota of the bots is not used on a documentation change. Sourcery does not honour the label; if it reviews, answer it as usual.
- **Do not leave a design choice open that changes the file set.** Where two readings of an obligation differ in the package it belongs to, or in whether a package is split, ask the operator at refine.
- **Documents (operator decision 2026-10-15, pm-mcp-core ADR 007).** The specifications, the implementation watch and the traceability of the engine live in this repository. This plan changes none of them: it links them. It opens a pull request in another repository only where a link there would break; none is expected, because it deletes no file.
- **Sibling checkouts from the plan worktree.** The plan runs in `.plan/local/worktrees/{plan}/`, three directories deeper than the main checkout, so `../plan-marshall-documentation` does not resolve there. Run the links check with the absolute path of the sibling checkout, `python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py /home/oliver/git/plan-marshall-documentation/doc .`, and link from the package files with the relative form the neighbouring bullets use.
- **Log messages: none.** **Format versions: untouched.**

## Claim Labels

- OBSERVED: pm-mcp-core #84 merged as `5197490` on 2026-10-17 and amended `doc/plans/runtime-control.adoc` only among the package files; it changed 15 specification parts — `git show --stat 5197490`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git show --stat 5197490: 16 files, one package file (doc/plans/runtime-control.adoc) and 15 specification parts
- OBSERVED: at `c281f6c` no file of `doc/plans/` other than `runtime-control.adoc` (for `attestation`) and the README, `plan-instance-alignment` and `recipe-workflows` (for `simplify`, in another sense) contains any of `egress`, `update_evidence`, `agent_started`, `navigation figure`, `writing skill`, `Seatbelt`, `PM-WATCH-GEN-59`, `comparison footprint`, `harness_update_unverified`, `extension_isolation` — `git grep -il` of `c281f6c -- doc/plans`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: git grep -il of c281f6c -- doc/plans for each term: only runtime-control (attestation) and README, plan-instance-alignment, recipe-workflows (simplify, another sense) match; egress, update_evidence, agent_started, navigation figure, writing skill, Seatbelt, PM-WATCH-GEN-59, comparison footprint, harness_update_unverified and extension_isolation match no file
- OBSERVED: the bullets of the proposed owners at `c281f6c`: `job-confinement-and-environment` 6, `profile-loader` 3, `worker-launch` 4, `task-queue-and-protocol` 6, `supervisor` 5, `worker-activity` 6, `token-usage` 12, `differential-test-harness` 3, `role-qualification` 5, `runtime-control` 8 — bullets of § What counted at `c281f6c`
  - verdict: corroborated | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: bullets of § What counted at c281f6c: 6, 3, 4, 6, 5, 6, 12, 3, 5, 8 for the ten packages named
- HYPOTHESIS: each proposed owner is the package whose _Where_ the obligation fits — confirm/refute by reading each package file and its neighbours (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: `job-confinement-and-environment` can take Seatbelt, the egress proxy and the git identity check within 12 bullets — confirm/refute by counting after the amendment (verify-at-outline)
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that edits a package file named above, re-count the bullets of each, and check `doc/plans/README.adoc` for rows added since `c281f6c`; a refutation of any claim above loops back to re-scope.
  - verdict: unverifiable | checked_at: c281f6c762a9c6e5cde21bb8b6fb5bef9cc3d852 | by: pm-mcp-core-m2-m4/analyze | rescoped: n/a | evidence: procedural clause for the executing plan at refine, not evaluable now

## Expected Surface

- HYPOTHESIS: `doc/plans/job-confinement-and-environment.adoc` — Deliverables 1 and 2
- HYPOTHESIS: `doc/plans/profile-loader.adoc` — Deliverable 3
- HYPOTHESIS: `doc/plans/worker-launch.adoc` — Deliverable 4
- HYPOTHESIS: `doc/plans/task-queue-and-protocol.adoc` — Deliverable 5
- HYPOTHESIS: `doc/plans/supervisor.adoc` — Deliverable 6
- HYPOTHESIS: `doc/plans/worker-activity.adoc` — Deliverables 6 and 7
- HYPOTHESIS: `doc/plans/differential-test-harness.adoc` — Deliverable 8
- HYPOTHESIS: `doc/plans/role-qualification.adoc` — Deliverable 8, if the footprint is read from a qualification run
- HYPOTHESIS: `doc/plans/runtime-control.adoc` — Deliverable 9, only what it omits of the upgrade check
- HYPOTHESIS: `doc/plans/README.adoc` — Deliverable 11, only if a package is split

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: computed by `corpus cross-check` at emit time; the package files above are edited only by their own plans when they finish (deleting them), and none of those plans is running except `job-runner` (PLAN-20), whose package file this plan does not touch
- Run before the plans of the amended packages are emitted (PLAN-19, 27, 28, 29, 30, 34, 37, 61, 66): the orchestrator regenerates their specs from the amended package files after this plan has landed
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` and the client contract `pm-api` in `pm-mcp-clients`, which stay untouched; and `plan-marshall-documentation`, where nothing changes

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-78-spec-obligation-owners.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
