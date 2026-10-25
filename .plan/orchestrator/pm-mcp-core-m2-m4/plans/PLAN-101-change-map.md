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

- OBSERVED: no work package builds this: none of the package files under `doc/plans/` names `Change Map`, `change-map`, `change_map`, and the index has no row for `change-map` — `grep` of `doc/plans/` and `ls doc/plans/change-map.adoc` at `4617865`
  - verdict: contradicted | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: unchanged at 8233fffc: doc/plans/change-map.adoc exists (ls-tree at 8233fffc) and README row change-map (line 222) is blocked; the claim of no package file and no row is false
- OBSERVED: the specification this plan builds exists and carries the anchors it names — `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc`, `doc/implementation-watch/phase-workflows.adoc`
  - verdict: corroborated | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: re-read at 8233fffc: phase-workflows/05 still carries #dependency-delta (line 239) and #change-map (line 263; moved by #177 inserting the Capability Test Guard at 251) and PM-WATCH-PHASE-114 is still at implementation-watch/phase-workflows.adoc line 1708; 118 and 119 were appended after it
- OBSERVED: the dependencies named below are queue rows of this ledger (PLAN-96, PLAN-100, PLAN-43); `PLAN-96` is the plan that writes the package file and the index row
  - verdict: corroborated | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: unchanged at 8233fffc: queue files PLAN-96.json PLAN-100.json PLAN-43.json exist in the ledger queue dir; PLAN-96 shipped (#143)
- OBSERVED: the packages this one waits on have no work package — the index and the package files under `doc/plans/` build none of `Dependency Delta`, `module discovery`, `build-file dependency parser`; the index will keep this package `blocked` on them
  - verdict: contradicted | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: unchanged at 8233fffc: doc/plans/domain-extensions-and-architecture.adoc and finalize-pipeline-and-merge.adoc exist (ls-tree at 8233fffc) and the change-map README row depends on both; #177 adds a Capability Test Guard to the same 05 part but doc/plans names neither capability_test nor verified_by
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the specification parts — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: unchanged at 8233fffc: hypothesis verify-at-outline; workflow/finalize holds FinalizePipelineValidator FinalizeStepDescriptor PipelineRule PipelineViolation and core/discovery is package-info only; no change_map ChangeMap or edges_partial symbol in main java
- HYPOTHESIS: the milestone in the `Serves` line and the edges of the `Dependencies` section are the orchestrator's reading of the roadmap and of the specification, not an operator decision — confirm/refute at `doc/plans/README.adoc` and the roadmap items of `plan-marshall-documentation` (verify-at-outline)
  - verdict: contradicted | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: yes | evidence: unchanged at 8233fffc: README change-map row (line 222) depends on own-calls phase-sub-workflows domain-extensions-and-architecture finalize-pipeline-and-merge while the spec Dependencies section still lists PLAN-96 PLAN-100 PLAN-43 only; Milestone 10 item holds
- Verify-first clause: before scoping, check that PLAN-96 has landed (the package file and the index row exist), that the packages this one depends on are done or in flight, and list the open pull requests for one that names the package; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 8233fffcaf64e1605fa4fe1b0912ac06f44bb428 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: unchanged at 8233fffc: package file and row exist and are not in the diff; no PR branch re-read

## Expected Surface

- HYPOTHESIS: `doc/plans/change-map.adoc` — the package file, written by PLAN-96; deleted when the package is done
- OBSERVED: `doc/plans/README.adoc` — this package's own status row only
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/finalize/` — the finalize steps and their facts
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/discovery/` — the module discovery the crawl reads (`package-info.java` only today)
- HYPOTHESIS: `pm-workflow/doc/developer/workflow-finalize.adoc` — developer documentation, extended
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-finalize/` — ADRs of the package's design decisions
- OBSERVED: `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/implementation-watch/phase-workflows.adoc` — PM-WATCH-PHASE-114 retired with the package
- OBSERVED: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- HYPOTHESIS: `pm-core/doc/developer/core-discovery.adoc` — to be created; added by the re-grounding of 2026-10-25 (see Re-grounding Record)
- OBSERVED: `doc/specification/phase-workflows/04-finalize-step-bands.adoc` — exists at HEAD `88ec9c70`; added by the re-grounding of 2026-10-25 (see Re-grounding Record)

## Re-grounding Record (2026-10-25, HEAD `88ec9c70`)

Written by `pm-mcp-core-m2-m4/cleanup`, measured against `main` at `88ec9c70` (the plans #172, #169 and #164 and the specification pull requests #141, #165, #168, #171, #173, #174 and #175 have landed since the grounding of `4617865`). The Objective, Deliverables, Dependencies and Hand-Off above are the earlier text and stay as the audit record; **where this section and that text differ, this section is the later reading**, and the outline records the departure. Spec-sourced items below are the specification's own content, not inferences of the orchestrator; every statement about the repository was read at the cited place.

- Claims re-read: 7: 3 corroborated, 3 contradicted (re-scoped by this section), 1 unverifiable. Verdicts are stamped on the claims above (`checked_at: 88ec9c70`).

### Premises corrected (contradicted at HEAD, re-scoped here)

- Claim 0: doc/plans/change-map.adoc EXISTS at HEAD (created by 9c9d99c6 #143 on 2026-10-22 19:33, after 4617865; reclassified in 35fba9ed #151) and README row change-map is blocked; doc/plans/own-calls.adoc exists too
- Claim 3: doc/plans/domain-extensions-and-architecture.adoc (module discovery plus build-file dependency parser) and doc/plans/finalize-pipeline-and-merge.adoc (pre-merge Dependency Delta Check and finalize pipeline) exist at HEAD (cut by #151 35fba9ed); change-map.adoc Left-out bullet now names both and README change-map row depends on them
- Claim 5: Milestone and item hold: roadmap origin/main line 618 (Milestone 10) names the change-map item with PM-WF-13 and PM-WF-21. The Dependencies edges are stale: README row depends on own-calls, phase-sub-workflows, domain-extensions-and-architecture, finalize-pipeline-and-merge; spec lists PLAN-96, PLAN-100, PLAN-43 only

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

- Dependency edges are stale: the spec lists PLAN-96, PLAN-100, PLAN-43 but the index row now also depends on domain-extensions-and-architecture and finalize-pipeline-and-merge; the own-calls package is itself blocked on the task envelope submit link and Task Diff Record (no work package)
- The completion report is created by operator-reports (PLAN-58) and PLAN-101 Deliverables 4 and 5 add own calls and change map to it, but no dependency edge links the two
- Merge Danger (roadmap origin/main line 620, Phase Workflows § Merge Danger) reads the change map and no package file or queue spec owns it

### Second pass (HEAD `8233fffc`, after #177 and #179)

The specification pull requests #177 (capability specs: Proportion, Amended after refine, Verified by, the Capability Test Guard, the replan triggers `capability_conflict` and `intent_amended`) and #179 landed after the first pass. 7 claims were re-read: 3 corroborated, 3 contradicted (still re-scoped by this record), 1 unverifiable; stamped `checked_at: 8233fffc`.

Specification changes carried (not yet in the Deliverables above):

- #177 — phase-workflows/05 Capability Test Guard (Pre-Merge) anchor capability-test-guard watch PM-WATCH-PHASE-119 at implementation-watch/phase-workflows.adoc line 1791: Same run points as the Dependency Delta Check and this map (Phase 6 entry and after every settle-commit and inside default:branch-cleanup after its rebase) and same inputs (realized_state measured; capability specs read at base_ref from git objects; git rename detection) and a sibling result in the finalize facts (capability_tests beside change_map) and in the merge record. Fold: add a Left-out note that the Guard is not this package's (no doc/plans file names capability_test or verified_by so it has no owner) and require the base_ref git-object read seam of Deliverable 2 to be reusable by it; keep the Guard section when deleting implemented 05 sections. No path to add: phase-workflows/05 and implementation-watch/phase-workflows.adoc are already on the Expected Surface
- #177 — phase-workflows/01 Capability Specs Verified by and phase-workflows/04 architecture-refresh row (verified_by test files taken from what the self-review named as evidence): The self-review submission that Deliverable 6 extends (results for own calls and replaced coordinates) becomes the source of verified_by evidence files; the contract is shared with the DC-n results of #168 already noted. Fold: in Deliverable 6 note that the review-requirement submission contract is shared and that the omission refusals compose; no new path (phase-workflows/04-finalize-step-bands.adoc is already on the Expected Surface)

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
