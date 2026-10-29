# PLAN-V2-78: The change map of a large change

epic: pm-mcp-core-m2-m4-v2
workstream: WS-08
origin: PLAN-101 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-V2-78-change-map.md` and is queued as one row file, `queue/PLAN-V2-78.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-03.
- R12 — log message holder: a record, if any, goes into `WorkflowLogMessages` or `CoreLogMessages`, at the next free identifier of its level, and into `doc/LogMessages.adoc`.

## Objective

Implement the work package `change-map` of `doc/plans/change-map.adoc`: the change map: which modules a change adds or removes, which dependencies between modules it adds or removes, and which third-party dependencies it adds, removes, or replaces. The package file is written by PLAN-96 (which must have landed before this plan is emitted) from the text this spec carries; the specification it builds against is merged and no work package built it before (found by the analysis of PLAN-93, PLAN-2026-10-22). Serves: Milestone 10 (roadmap item _Implement the change map of a plan_; `PM-WF-13`, `PM-WF-21`), beside the Dependency Delta Check. Where: pm-workflow (the computation, the facts of the finalize step, the judgment at the self-review); pm-core (the module discovery crawl it reads, over a tree of git objects).

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

- OBSERVED: no work package builds this: none of the package files under `doc/plans/` names `Change Map`, `change-map`, `change_map`, and the index has no row for `change-map` — `grep` of `doc/plans/` and `ls doc/plans/change-map.adoc` at `4617865`
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: doc/plans/change-map.adoc exists (8 What bullets), row blocked, Milestone 10; unchanged
- OBSERVED: the specification this plan builds exists and carries the anchors it names — `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc`, `doc/implementation-watch/phase-workflows.adoc`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: phase-workflows/05 dependency-delta and change-map anchors present (moved by #177, #186, #189, #191); PM-WATCH-PHASE-114 at phase-workflows.adoc:1712, text unchanged
- OBSERVED: the dependencies named below are queue rows of this ledger (PLAN-96, PLAN-V2-77, PLAN-43); `PLAN-96` is the plan that writes the package file and the index row
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: PLAN-96 shipped (#143); PLAN-V2-77 and PLAN-43 open rows; queue files exist in the ledger
- OBSERVED: the packages this one waits on have no work package — the index and the package files under `doc/plans/` build none of `Dependency Delta`, `module discovery`, `build-file dependency parser`; the index will keep this package `blocked` on them
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: domain-extensions-and-architecture.adoc and finalize-pipeline-and-merge.adoc exist (index rows open); code absent: core/discovery is package-info only, no change_map symbol in main Java
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the specification parts — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis; workflow/finalize holds FinalizePipelineValidator, FinalizeStepDescriptor, PipelineRule, PipelineViolation; workflow/diagram holds the renderers
- HYPOTHESIS: the milestone in the `Serves` line and the edges of the `Dependencies` section are the orchestrator's reading of the roadmap and of the specification, not an operator decision — confirm/refute at `doc/plans/README.adoc` and the roadmap items of `plan-marshall-documentation` (verify-at-outline)
  - verdict: contradicted | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: milestone holds (10); the index row depends on own-calls, phase-sub-workflows, domain-extensions-and-architecture and finalize-pipeline-and-merge while the spec lists three and parks the packages as no work package
- Verify-first clause: before scoping, check that PLAN-96 has landed (the package file and the index row exist), that the packages this one depends on are done or in flight, and list the open pull requests for one that names the package; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: package file and row exist; PLAN-96 landed; dependencies not done so the plan is not ready; the open-PR list is not readable

## Expected Surface

- OBSERVED: `doc/plans/change-map.adoc` — the package file (exists), deleted when the package is done
- OBSERVED: `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc` — Change Map, Dependency Delta Check; leave Added-Line Scan and the other later sections
- OBSERVED: `doc/specification/phase-workflows/04-finalize-step-bands.adoc` — self-review and create-pr rows carry change map and own calls
- OBSERVED: `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` — Positioned Layout of the change map
- OBSERVED: `doc/implementation-watch/phase-workflows.adoc` — PM-WATCH-PHASE-114 retired with the package
- OBSERVED: `doc/implementation-watch/workflow-dsl.adoc` — PM-WATCH-DSL-37 and -38 (positioned layout)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/finalize/ChangeMap.java` — the change_map result record (state, modules and edges delta, replaced, cause)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/discovery/` — module discovery the crawl reads; no class nameable yet
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/` — generator the positioned layout belongs to; no class nameable yet
- OBSERVED: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/GitOperations.java` — contract lacks a tree listing and read at base_ref over git objects
- OBSERVED: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/JGitOperations.java` — JGit implementation of the tree read
- OBSERVED: `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/JGitOperationsTest.java` — tree read fixtures
- HYPOTHESIS: `pm-core/doc/developer/core-discovery.adoc` — developer documentation of the package, to be created

## Re-grounding Record (2026-10-26, HEAD `f4a1bd07`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `f4a1bd07`. This section is the **latest** reading; the earlier section below stays as the audit record, and **where they differ, this section is the later reading**. The plan stays **parked**.

- Claims re-read: 7: 3 corroborated, 3 contradicted (re-scoped by this section), 1 unverifiable. Verdicts are stamped on the claims above (`checked_at: f4a1bd07`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: `doc/plans/change-map.adoc` exists (8 What bullets, row `blocked`, Milestone 10), unchanged.
- Claim 3: `domain-extensions-and-architecture.adoc` (module discovery and parser) and `finalize-pipeline-and-merge.adoc` (Dependency Delta Check) exist (index rows open); code is absent (`core/discovery` is package-info only; no `change_map`, `ChangeMap` or `edges_partial` symbol in main Java).
- Claim 5: the milestone holds (10); the edges do not: the index row depends on `own-calls`, `phase-sub-workflows`, `domain-extensions-and-architecture` and `finalize-pipeline-and-merge`, the spec lists three and parks the packages as "no work package"; Deliverable 8's Left-out names only "(Milestone 10, no work package)" while the package file links both.

### Surface

- Added to the Expected Surface above: `workflow-dsl/07`, `workflow/diagram/`, the workflow-dsl watch.
- wrong: `doc/plans/change-map.adoc` is annotated HYPOTHESIS "written by PLAN-96"; it exists.
- wrong: `pm-core/.../core/discovery/` is "package-info only" (still true), but the `base_ref` crawl needs a git read seam that `pm-core` cannot implement (no JGit dependency allowed); `pm-provider-git` is in the surface from the earlier pass.

### Specification changes carried (not yet in the Deliverables above)

- **#181 (`7f1d6a99`) — Phase Workflows 05 § Change Map item 3 Rendering (one sentence) and Workflow DSL 07 § Positioned Layout (anchor `diagram-layout`; watch `PM-WATCH-DSL-37`, `-38`):** for the web app the same boxes and edges are laid out as data and drawn in place of the text diagram: nodes with `kept`/`added`/`removed` edges, a removed or replaced coordinate as a label line, layered with back edges, an advance-width table, golden files, `too_large` above 200 nodes; part of the generator in `de.planmarshall.workflow.diagram`. **Not in the Deliverables or the Done-when; no work package mentions layout.** Add a deliverable or name the owner.
- #189 — Phase Workflows 05 § Added-Line Scan (Pre-Merge) (anchor `added-line-scan`, `PM-WATCH-PHASE-122`): a sibling pre-merge check reading added lines from git objects at `base_ref` and `HEAD`, its result `added_lines` in the finalize facts beside `change_map`; it must reuse the same git-object read seam. #191 — item 4 Credential-Shaped Values (reads each commit of `base_ref..HEAD`) and the Outbound Text check (`credential_shaped_text`, `PM-WATCH-PHASE-123`): more users of the git-object read seam; no effect on the `change_map` result. #186 — Code Scanning Alerts as a Code Analysis Provider (`PM-WATCH-PHASE-120`) and Security Audit Calibration (`-121`): not the change map. #192: Phase Workflows 01 only.

### Shipped or superseded since the last grounding

- None of the change map; #185 and #190 touch nothing here. `FindingSourceKind` already has `ADDED_LINES` and `CAPABILITY_TESTS` (finding kinds only). Dependencies: PLAN-96 shipped; PLAN-V2-77, PLAN-43 and the two packages open. `PM-WATCH-PHASE-114` present (phase-workflows.adoc:1712).

### Open questions for the outline

- **Who builds the positioned layout of the change map** (this plan, a diagram package or the web client plan); **who owns the git-object read seam** shared by the change map, the Capability Test Guard and the Added-Line Scan; the owner of Merge Danger Reach (earlier pass) is still unnamed.

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 7: 3 corroborated, 3 contradicted (re-scoped by this section), 1 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: doc/plans/change-map.adoc EXISTS at HEAD (created by 9c9d99c6 #143 on 2026-10-22 19:33, after 4617865; reclassified in 35fba9ed #151) and README row change-map is blocked; doc/plans/own-calls.adoc exists too
- Claim 3: doc/plans/domain-extensions-and-architecture.adoc (module discovery plus build-file dependency parser) and doc/plans/finalize-pipeline-and-merge.adoc (pre-merge Dependency Delta Check and finalize pipeline) exist at HEAD (cut by #151 35fba9ed); change-map.adoc Left-out bullet now names both and README change-map row depends on them
- Claim 5: Milestone and item hold: roadmap origin/main line 618 (Milestone 10) names the change-map item with PM-WF-13 and PM-WF-21. The Dependencies edges are stale: README row depends on own-calls, phase-sub-workflows, domain-extensions-and-architecture, finalize-pipeline-and-merge; spec lists PLAN-96, PLAN-V2-77, PLAN-43 only

### Surface

- Added to the Expected Surface above: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/`, `pm-core/doc/developer/core-discovery.adoc`, `doc/specification/phase-workflows/04-finalize-step-bands.adoc`.
- wrong: doc/plans/change-map.adoc is listed as written by PLAN-96 but already exists at HEAD (#143, #151); PLAN-96 is shipped. Fix Objective, Deliverable 8 Left-out bullet and Dependencies: module discovery and parser are domain-extensions-and-architecture, Dependency Delta Check is finalize-pipeline-and-merge, both have package files and rows
- understated: The crawl at base_ref over git objects needs a git read the contract lacks (GitOperations has readFile over a cache repository only, no tree listing) and pm-core must not depend on JGit: add pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/ (GitOperations, JGitOperations), a pm-core seam in core/discovery or core/spi, and pm-core/doc/developer/core-discovery.adoc (new); add doc/specification/phase-workflows/04-finalize-step-bands.adoc (self-review row and create-pr row carry change map and own calls)

### Specification changes carried (not yet in the Deliverables above)

- #141 — Phase Workflows § Merge Danger (05-finalize-review-metrics-descriptor: Reach from the change map's crawl at HEAD, section ## Merge Danger of the pull-request body and the landing record): Add a deliverable: the Reach part of ## Merge Danger (modules with a path in the realized footprint and their direct dependents from the HEAD crawl, two counts and names up to 20, named not computed when the map is partial or indeterminate) rendered beside ## Change Map; the Hard to Undo part reads decisions and stays with decision-engine. Roadmap origin/main line 620 has the item, no package file names it. Paths: same finalize/ and 05 spec part
- #168 — Phase Workflows § Step Catalogue (04-finalize-step-bands: review-requirement now also returns one result per decision DC-n, followed or departed): Same submission contract this plan extends with own-call and replaced-coordinate results; the DC-n results belong to phase-sub-workflows (PLAN-43, package file updated by #168). Note in Deliverable 6 that the contract is shared and the omission refusals compose; no new path

### Shipped or superseded since the last grounding

- Package file, index row and the dependency packages now exist; the change map itself is not built: doc/plans/change-map.adoc (#143), README row blocked, doc/plans/domain-extensions-and-architecture.adoc and finalize-pipeline-and-merge.adoc (#151); no change_map, ChangeMap or edges_partial symbol in src; core/discovery is package-info only

### Open questions for the outline

- Dependency edges are stale: the spec lists PLAN-96, PLAN-V2-77, PLAN-43 but the index row now also depends on domain-extensions-and-architecture and finalize-pipeline-and-merge; the own-calls package is itself blocked on the task envelope submit link and Task Diff Record (no work package)
- The completion report is created by operator-reports (PLAN-V2-41) and PLAN-V2-78 Deliverables 4 and 5 add own calls and change map to it, but no dependency edge links the two
- Merge Danger (roadmap origin/main line 620, Phase Workflows § Merge Danger) reads the change map and no package file or queue spec owns it

### Second pass (HEAD `8233fffc`, after #177 and #179)

The specification pull requests #177 (capability specs: Proportion, Amended after refine, Verified by, the Capability Test Guard, the replan triggers `capability_conflict` and `intent_amended`) and #179 landed after the first pass. 7 claims were re-read: 3 corroborated, 3 contradicted (still re-scoped by this record), 1 unverifiable; stamped `checked_at: 8233fffc`.

Specification changes carried (not yet in the Deliverables above):

- #177 — phase-workflows/05 Capability Test Guard (Pre-Merge) anchor capability-test-guard watch PM-WATCH-PHASE-119 at implementation-watch/phase-workflows.adoc line 1791: Same run points as the Dependency Delta Check and this map (Phase 6 entry and after every settle-commit and inside default:branch-cleanup after its rebase) and same inputs (realized_state measured; capability specs read at base_ref from git objects; git rename detection) and a sibling result in the finalize facts (capability_tests beside change_map) and in the merge record. Fold: add a Left-out note that the Guard is not this package's (no doc/plans file names capability_test or verified_by so it has no owner) and require the base_ref git-object read seam of Deliverable 2 to be reusable by it; keep the Guard section when deleting implemented 05 sections. No path to add: phase-workflows/05 and implementation-watch/phase-workflows.adoc are already on the Expected Surface
- #177 — phase-workflows/01 Capability Specs Verified by and phase-workflows/04 architecture-refresh row (verified_by test files taken from what the self-review named as evidence): The self-review submission that Deliverable 6 extends (results for own calls and replaced coordinates) becomes the source of verified_by evidence files; the contract is shared with the DC-n results of #168 already noted. Fold: in Deliverable 6 note that the review-requirement submission contract is shared and that the omission refusals compose; no new path (phase-workflows/04-finalize-step-bands.adoc is already on the Expected Surface)

## Dependencies and Sequencing

- Depends on: PLAN-96 (`spec-ahead-work-packages`); PLAN-V2-77 (`own-calls`); PLAN-43 (`phase-sub-workflows`)
- Parked: the module discovery, the build-file dependency parser, the Dependency Delta Check, and the finalize pipeline of Milestone 10 have no work package; the index keeps this package `blocked` on them
- Blocks: nothing
- Overlaps with: computed by `corpus cross-check` at emit time
## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-78-change-map.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
