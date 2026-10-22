# PLAN-101: The change map of a large change

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-101-change-map.md` and is queued as one row file, `queue/PLAN-101.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-03.
- R12 — log message holder: a record, if any, goes into `WorkflowLogMessages` or `CoreLogMessages`, at the next free identifier of its level, and into `doc/LogMessages.adoc`.

## Objective

Implement the work package `change-map` of `doc/plans/change-map.adoc`: the change map: which modules a change adds or removes, which dependencies between modules it adds or removes, and which third-party dependencies it adds, removes, or replaces. The package file is written by PLAN-96 (which must have landed before this plan is emitted) from the text this spec carries; the specification it builds against is merged and no work package built it before (found by the analysis of PLAN-93, 2026-10-22). Serves: Milestone 10 (roadmap item _Implement the change map of a plan_; `PM-WF-13`, `PM-WF-21`), beside the Dependency Delta Check. Where: pm-workflow (the computation, the facts of the finalize step, the judgment at the self-review); pm-core (the module discovery crawl it reads, over a tree of git objects).

## Deliverables

The bullets of the package file `doc/plans/change-map.adoc` § What, in its words (authored by the orchestrator on 2026-10-22; the file itself is PLAN-96's).

1. *Implements* Phase Workflows § Change Map and the removed-coordinates half of § Dependency Delta Check (watch PM-WATCH-PHASE-114).
2. *Two trees*: the in-process crawl of the module discovery runs over the tree at `base_ref`, read from git objects and never checked out, and over the worktree at `HEAD`. Enrolled discovery jobs are not run for the map; a module whose edges only such a job provides is drawn without them and marked `edges_partial`. The map is computed without a model, with the Dependency Delta Check, at Phase 6 entry and after every `finalize.settle-commit`.
3. *The delta*: `modules_added[]`, `modules_removed[]`, `edges_added[]` and `edges_removed[]` (`from` module, `to` module), and the third-party coordinates of the dependency delta, `added[]` and `removed[]`, where a removed and an added coordinate of the same module and scope form a `replaced[]` pair. A module whose descriptor failed to parse on either side is named with its diagnostic, and the map is `partial`, never complete.
4. *The rendering*: a fixed-width text diagram of at most `20` boxes: the modules the delta names and those with a path in the realized footprint, each with its direct neighbours; an added edge drawn `+-->`, a removed edge `--x`, a removed or replaced third-party dependency named under its module. A larger map is rendered as the lists alone. It is shown in the server-owned section `## Change Map` of the pull-request body, in the landing record, in the completion report, and as the resource `pm://plan/{plan_id}/change-map`.
5. *Where it and the own calls are read*: the server-owned sections `## Own Calls` (shortcuts first, each with its limit and ending condition) and `## Change Map` of the pull-request body, the landing record, and the completion report, and the resource of the plan; the calls themselves are recorded by `own-calls`. The `simplify` review receives the shortcuts as facts and proposes the removal of none whose `ends_when` has not occurred.
6. *Judged at the self-review*: the facts of `review-requirement` carry the map and the calls, and the self-review judges each own call against the intent record and each removed or replaced coordinate no call and no deliverable names; it is refused when it omits a result for a replaced coordinate that no own call names. An added coordinate stays the Dependency Delta Check's.
7. *The result*: `change_map: {state: complete | partial | indeterminate, modules_added[], modules_removed[], edges_added[], edges_removed[], replaced[], cause}` in the finalize facts; `indeterminate` when the realized footprint is not `measured`. The map blocks nothing by itself.
8. *Left out*: the module discovery, the build-file dependency parser, and the Dependency Delta Check (Milestone 10, no work package); the guidance and the rendering in the clients.

**Done when** (the package's own acceptance): The guards of PM-WATCH-PHASE-114 pass: for fixture repositories whose base and head differ by an added module, a removed module edge, and a replaced coordinate, the map equals the crawl of each tree checked out in a scratch directory; a descriptor that fails to parse at `base_ref` yields `partial` with its diagnostic and never `modules_removed`; the status of the worktree is unchanged after the map is computed; the self-review is refused when it omits a result for a replaced coordinate that no own call names; a map of more than `20` boxes is rendered as the lists alone. The pull-request section, the landing record, and the completion report list the own calls and the change map of a fixture plan, shortcuts first. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WF-13; PM-WF-21; PM-EXT-3 (module discovery and build map, the crawl it reads)
- Specification: the sections the first bullet of _What_ names; Domain Extensions § Module Discovery; Phase Workflows § Own Calls of a Writer; MCP Tools § Resource URIs; Phase Workflows § Step Catalogue (`pre-submission-self-review`)
- Watch: Phase Workflows

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: no work package builds this: none of the package files under `doc/plans/` names `Change Map`, `change-map`, `change_map`, and the index has no row for `change-map` — `grep` of `doc/plans/` and `ls doc/plans/change-map.adoc` at `0bf64f7`
  - verdict: corroborated | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: grep -i of ['Change Map', 'change-map', 'change_map'] over doc/plans/*.adoc at 0bf64f7: no hit; doc/plans/change-map.adoc absent; README.adoc names no row `change-map`; the staged specs carry the content only as 'not carried' (Open Defect of the epic, cleanup 2026-10-22)
- OBSERVED: the specification this plan builds exists and carries the anchors it names — `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc`, `doc/implementation-watch/phase-workflows.adoc`
  - verdict: corroborated | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: anchors found at 0bf64f7: doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc: [#change-map], [#dependency-delta]; doc/implementation-watch/phase-workflows.adoc: [#PM-WATCH-PHASE-114]
- OBSERVED: the dependencies named below are queue rows of this ledger (PLAN-96, PLAN-100, PLAN-43); `PLAN-96` is the plan that writes the package file and the index row
  - verdict: corroborated | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: queue rows read at the head of the ledger: PLAN-96, PLAN-100, PLAN-43 exist; PLAN-96 is staged by this stage
- OBSERVED: the packages this one waits on have no work package — the index and the package files under `doc/plans/` build none of `Dependency Delta`, `module discovery`, `build-file dependency parser`; the index will keep this package `blocked` on them
  - verdict: corroborated | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: grep -i of ['Dependency Delta', 'module discovery', 'build-file dependency parser'] over doc/plans/*.adoc at 0bf64f7: no hit
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the specification parts — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the milestone in the `Serves` line and the edges of the `Dependencies` section are the orchestrator's reading of the roadmap and of the specification, not an operator decision — confirm/refute at `doc/plans/README.adoc` and the roadmap items of `plan-marshall-documentation` (verify-at-outline)
  - verdict: unverifiable | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before scoping, check that PLAN-96 has landed (the package file and the index row exist), that the packages this one depends on are done or in flight, and list the open pull requests for one that names the package; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 0bf64f7578d6024cca76116c1e34618fdaf59ff5 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: open PRs (ci pr list) on 2026-10-22: #137, #129, #71; none names the package (none touches doc/plans/change-map.adoc: it does not exist yet); PLAN-96 not yet landed, it is the dependency

## Expected Surface

- HYPOTHESIS: `doc/plans/change-map.adoc` — the package file, written by PLAN-96; deleted when the package is done
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/finalize/` — the finalize steps and their facts
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/discovery/` — the module discovery the crawl reads (`package-info.java` only today)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-finalize.adoc` — developer documentation, extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-finalize/` — ADRs of the package's design decisions
- OBSERVED: `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/implementation-watch/phase-workflows.adoc` — PM-WATCH-PHASE-114 retired with the package

## Dependencies and Sequencing

- Depends on: PLAN-96 (`spec-ahead-work-packages`); PLAN-100 (`own-calls`); PLAN-43 (`phase-sub-workflows`)
- Parked: the module discovery, the build-file dependency parser, the Dependency Delta Check, and the finalize pipeline of Milestone 10 have no work package; the index keeps this package `blocked` on them
- Blocks: nothing
- Overlaps with: computed by `corpus cross-check` at emit time
## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-101-change-map.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
