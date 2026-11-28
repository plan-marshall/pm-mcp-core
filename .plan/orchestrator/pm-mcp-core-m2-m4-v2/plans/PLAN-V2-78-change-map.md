# PLAN-V2-78: The change map of a large change

epic: pm-mcp-core-m2-m4-v2
workstream: WS-08
origin: PLAN-101 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-78-change-map.md` and is queued as one row file, `queue/PLAN-V2-78.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18, R19, R20.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-03.
- R12 — log message holder: a record, if any, goes into `WorkflowLogMessages` or `CoreLogMessages`, at the next free identifier of its level, and into `doc/LogMessages.adoc`.

## Objective

Implement the work package `change-map` of `doc/plans/change-map.adoc`: the change map: which modules a change adds or removes, which dependencies between modules it adds or removes, and which third-party dependencies it adds, removes, or replaces. The package file is written by PLAN-96 (which must have landed before this plan is emitted) from the text this spec carries; the specification it builds against is merged and no work package built it before (found by the analysis of PLAN-93, PLAN-2026-10-22). Serves: Milestone 10 (roadmap item _Implement the change map of a plan_; `PM-WF-13`, `PM-WF-21`), beside the Dependency Delta Check. Where: pm-workflow (the computation, the facts of the finalize step, the judgment at the self-review); pm-core (the module discovery crawl it reads, over a tree of git objects).

## Deliverables

The bullets of the package file `doc/plans/change-map.adoc` § What, in its words (authored by the orchestrator on 2026-10-22; the file itself is PLAN-96's).

1. *Implements* Phase Workflows § Change Map (watch PM-WATCH-PHASE-114). Removed coordinates are items of the sanity step `dependencies` (owner `finalize-pipeline-and-merge` task 5; doc-review fold of 2026-11-25), so this plan builds no coordinate result.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
2. *Two trees*: the in-process crawl of the module discovery runs over the tree at `base_ref`, read from git objects and never checked out, and over the worktree at `HEAD`. Enrolled discovery jobs are not run for the map; a module whose edges only such a job provides is drawn without them and marked `edges_partial`. The map is computed without a model, with the Dependency Delta Check, at Phase 6 entry and after every `finalize.settle-commit`.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
3. *The delta*: `modules_added[]`, `modules_removed[]`, `edges_added[]` and `edges_removed[]` (`from` module, `to` module), and the third-party coordinates of the dependency delta, `added[]` and `removed[]`, where a removed and an added coordinate of the same module and scope form a `replaced[]` pair. A module whose descriptor failed to parse on either side is named with its diagnostic, and the map is `partial`, never complete.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
4. *The rendering*: a fixed-width text diagram of at most `20` boxes: the modules the delta names and those with a path in the realized footprint, each with its direct neighbours; an added edge drawn `+-->`, a removed edge `--x`, a removed or replaced third-party dependency named under its module. A larger map is rendered as the lists alone. It is shown in the server-owned section `## Change Map` of the pull-request body, in the landing record, in the completion report, and as the resource `pm://plan/{plan_id}/change-map`.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
5. *Where it and the own calls are read*: the server-owned sections `## Own Calls` (shortcuts first, each with its limit and ending condition) and `## Change Map` of the pull-request body, the landing record, and the completion report, and the resource of the plan; the calls themselves are recorded by `own-calls`. The `simplify` review receives the shortcuts as facts and proposes the removal of none whose `ends_when` has not occurred.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
6. *Judged at the self-review*: the facts of `review-requirement` carry the map and the calls, and the self-review judges each own call against the intent record, and the map is review context only: no coordinate result, no forced conflict and no refusal for a replaced or removed coordinate (doc-review fold of 2026-11-25; removed coordinates are items of the sanity step `dependencies`).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
7. *The result*: `change_map: {state: complete | partial | indeterminate, modules_added[], modules_removed[], edges_added[], edges_removed[], replaced[], cause}` in the finalize facts; `indeterminate` when the realized footprint is not `measured`. The map blocks nothing by itself.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
8. *Left out*: the module discovery, the build-file dependency parser, and the Dependency Delta Check (Milestone 10, no work package); the guidance and the rendering in the clients.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer
9. *The writer of the change-size capture (ADDED 2026-11-17, operator answer 1 of the Open Defect "`head` in the change-size capture has no owner").* Store Schemas 13 § The Size of a Plan's Change (`#change-size`) says each capture of the realized footprint persists the size in `realized_state` from `git diff --numstat -M <base_ref>...HEAD`: `files_total`, `insertions_total`, `deletions_total` and `head`, the commit measured, and shows it only while `realized_state.state == measured` (an absent size is never `0`; a binary file counts as a file and adds no lines; git measures it, never a worker's report). The schema file carries the members (`pm-core` `references.schema.json`, `realized_state`: `head` is `null` unless `state == measured`), and no code in this repository, the assembly or the marketplace writes any of them. The plan adds the writer: the pure part in `pm-workflow` (parse of the `--numstat -M` output into the size, the `head` rule, the update of `realized_state` that leaves `realized_files`, `realized_renames`, `base_ref` and `captured_at` as the capture sets them, and an unmeasured state that carries no size), fed the git output by the caller because `pm-workflow` reads no git; the seam that runs the `git` job and hands the output over is named at the outline (the assembly's job runner, or a `GitOperations` read of the provider, which `pm-workflow` may not depend on). The clients' consumer event `PlanChangeCapturedEvent` (pm-mcp-clients #444) reads what this writes; the map of Deliverables 1 to 7 reads `realized_state` and is the only plan of the epic that does.

**Done when** (the package's own acceptance): the capture writer of Deliverable 9 turns the `--numstat -M` output of fixture diffs (added, deleted and renamed files, a binary file shown as `-	-`) into `files_total`, `insertions_total`, `deletions_total` and `head`, equal to the numbers `git` itself prints for the same fixture repository checked out in a scratch directory; `head` is `null`, and so are the three totals, for `unresolved` and `not_captured`, and `references.schema.json` accepts the result (`WorkflowStatusSchemaParityTest` or the parity test of the store the outline names); an absent size is never written as `0`. Beyond that: The guards of PM-WATCH-PHASE-114 pass: for fixture repositories whose base and head differ by an added module, a removed module edge, and a replaced coordinate (the map lists it, no result is asked for it), the map equals the crawl of each tree checked out in a scratch directory; a descriptor that fails to parse at `base_ref` yields `partial` with its diagnostic and never `modules_removed`; the status of the worktree is unchanged after the map is computed; the self-review facts carry the change map as review context (new behaviour: fails at the sha of the re-ground of the next outline) and no submission is refused for a replaced coordinate; a map of more than `20` boxes is rendered as the lists alone. The pull-request section, the landing record, and the completion report list the own calls and the change map of a fixture plan, shortcuts first. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WF-13; PM-WF-21; PM-EXT-3 (module discovery and build map, the crawl it reads)
- Specification: the sections the first bullet of _What_ names; Domain Extensions § Module Discovery; Phase Workflows § Own Calls of a Writer; MCP Tools § Resource URIs; Phase Workflows § Step Catalogue (`pre-submission-self-review`)
- Watch: Phase Workflows
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-documents:ref-asciidoc
   - Persona: implementer

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: no work package builds this: none of the package files under `doc/plans/` names `Change Map`, `change-map`, `change_map`, and the index has no row for `change-map` — `grep` of `doc/plans/` and `ls doc/plans/change-map.adoc` at `4617865`
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: doc/plans/change-map.adoc exists; README.adoc:222-226 has row change-map (blocked, Milestone 10); no ChangeMap/change_map/edges_partial symbol in any Java file (git grep empty). Package is written, code is not. Caused by #143/#151 and later; #623 touched the file.
- OBSERVED: the specification this plan builds exists and carries the anchors it names — `doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc`, `doc/implementation-watch/phase-workflows.adoc`
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: 05-finalize-review-metrics-descriptor.adoc exists, [#change-map] at :312, Rendering item at :324; doc/implementation-watch/phase-workflows.adoc has PM-WATCH-PHASE-114 at :1624-1625 (lines moved from :1610).
- OBSERVED: the dependencies named below are queue rows of this ledger (PLAN-96, PLAN-V2-77, PLAN-43); `PLAN-96` is the plan that writes the package file and the index row
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Queue rows are ledger state, not tree state; README.adoc:225 names own-calls, phase-sub-workflows, domain-extensions-and-architecture, finalize-pipeline-and-merge as dependencies, not PLAN-96/V2-77/43 (record says PLAN-96 and PLAN-43 have no row).
- OBSERVED: the packages this one waits on have no work package — the index and the package files under `doc/plans/` build none of `Dependency Delta`, `module discovery`, `build-file dependency parser`; the index will keep this package `blocked` on them
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: doc/plans/domain-extensions-and-architecture.adoc (module discovery, parsers, :18) and doc/plans/finalize-pipeline-and-merge.adoc (sanity step dependencies, :19) exist as packages; Dependency Delta Check no longer a spec section (#607). Both unbuilt: pm-core discovery holds only package-info.java.
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line and the specification parts — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Inference label confirmed at the trace matrix only; at head the HYPOTHESIS paths pm-workflow finalize/ChangeMap.java, ChangeSize.java and pm-core/doc/developer/core-discovery.adoc are still absent (git ls-files), consistent with the hypothesis but not decidable by reading.
- HYPOTHESIS: the milestone in the `Serves` line and the edges of the `Dependencies` section are the orchestrator's reading of the roadmap and of the specification, not an operator decision — confirm/refute at `doc/plans/README.adoc` and the roadmap items of `plan-marshall-documentation` (verify-at-outline)
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: Milestone 10 holds (README.adoc:226) but the edges differ: README.adoc:225 names own-calls, phase-sub-workflows, domain-extensions-and-architecture, finalize-pipeline-and-merge, not PLAN-V2-83/96/77/43; the index is the stated place for them.
- Verify-first clause: before scoping, check that PLAN-96 has landed (the package file and the index row exist), that the packages this one depends on are done or in flight, and list the open pull requests for one that names the package; a refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Procedural clause. PLAN-96 landed (change-map.adoc exists, README row :222); dependency packages exist and are unbuilt; open pull requests are not readable from the tree.

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
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/finalize/ChangeSize.java` — the pure writer of Deliverable 9 (numstat parse, `realized_state` update); its test `pm-workflow/src/test/java/de/planmarshall/workflow/finalize/ChangeSizeTest.java`; the seam that runs the `git` job is named at the outline
- OBSERVED: `doc/specification/store-schemas/13-leases-integrity-testing.adoc` — § The Size of a Plan's Change (`#change-size`), read, edited only if the writer shows the text wrong
- OBSERVED (understated, added by the cleanup re-ground of 2026-11-23 at `origin/main` `8c7eac6f`): `pm-core/src/main/resources/de/planmarshall/core/store/references.schema.json`, `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/UnreadSchemaFilesTest.java`, `pm-workflow/doc/developer/workflow-finalize.adoc`, `pm-workflow/doc/developer/workflow-diagram.adoc`, `doc/LogMessages.adoc`, `doc/Traceability.adoc`, `doc/plans/README.adoc`.

- OBSERVED (understated, added by the cleanup re-ground of 2026-11-25 at `origin/main` `3ba79c4f`; each path checked to exist): `doc/specification/phase-workflows/09-sanity-check.adoc`, `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/`

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
- #168 — Phase Workflows § Step Catalogue (04-finalize-step-bands: review-requirement now also returns one result per decision DC-n, followed or departed): Same submission contract this plan extends with own-call results; the DC-n results belong to phase-sub-workflows (PLAN-43, package file updated by #168). Note in Deliverable 6 that the contract is shared and the omission refusals compose; no new path

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
- #177 — phase-workflows/01 Capability Specs Verified by and phase-workflows/04 architecture-refresh row (verified_by test files taken from what the self-review named as evidence): The self-review submission that Deliverable 6 extends (results for own calls) becomes the source of verified_by evidence files; the contract is shared with the DC-n results of #168 already noted. Fold: in Deliverable 6 note that the review-requirement submission contract is shared and that the omission refusals compose; no new path (phase-workflows/04-finalize-step-bands.adoc is already on the Expected Surface)

- OBSERVED (cleanup 2026-11-19, understated surface; the files the Deliverables name and the surface did not): `pm-core/src/main/resources/de/planmarshall/core/store/references.schema.json`, `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java`, `pm-workflow/doc/developer/workflow-diagram.adoc`, `pm-workflow/doc/developer/workflow-finalize.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: PLAN-96 (`spec-ahead-work-packages`); PLAN-V2-77 (`own-calls`); PLAN-43 (`phase-sub-workflows`)
- Parked: the module discovery, the build-file dependency parser, the Dependency Delta Check, and the finalize pipeline of Milestone 10 have no work package; the index keeps this package `blocked` on them
- Blocks: nothing
- Overlaps with: computed by `corpus cross-check` at emit time
## Re-ground record (2026-11-17, Deliverable 9 added, BINDING over any contradicting older text of this spec)

Written by the orchestrator at the operator's word ("do the change", answer 1 of the Open Defect "`head` in the change-size capture has no owner"), measured at `origin/main` `63e5e80a` (fetched). Read, not run:

- **No writer exists.** `git grep` of `origin/main` for `realized_state`, `realizedState`, `RealizedState`, `files_total`, `insertions_total` finds only the specifications (`store-schemas/13`, `phase-workflows/05`, the store-schemas watch), `pm-core` `references.schema.json`, `UnreadSchemaFilesTest` (it reads the schema) and ledger documents; no class of `pm-core`, `pm-workflow`, `pm-runtime` or a provider writes the capture, so Deliverable 9 is new code, not a change.
- **The spec text is `store-schemas/13` line 69** (`[[change-size]]`): numbers from `git diff --numstat -M <base_ref>...HEAD`, `head` the commit measured, shown only while `measured`. `references.schema.json` forces `head` to `null` unless `state == measured`; the three totals are nullable integers with the same intent in their description.
- **The prerequisites of this plan stay parked** (PLAN-V2-30, 14 and 35 in the queue; Dependencies below). Deliverable 9 needs none of them: it reads a `git` output and writes `realized_state`. The operator unparks the row; whether Deliverable 9 can run before the discovery packages exist is the outline's first question, and the plan may ship Deliverable 9 first and leave 1 to 8 for the parked prerequisites if the outline finds that cut.
- **Scope.** The plan now has nine deliverables, above the soft cap of about six. Rationale recorded: Deliverable 9 produces the facts Deliverables 1 to 7 read, has one owner by the operator's answer, and stays a small pure writer; the cut, if the outline wants one, is Deliverable 9 first and the map after.
- **Claim labels.** The two claims below are new and unstamped (an open clause does not block the emit; the plan settles them).
  - OBSERVED: no code writes `realized_state`'s size or `head` — `git grep` of `origin/main` as above
  - HYPOTHESIS: the writer belongs in `pm-workflow/.../finalize/` as a pure function over the git output, the git job run by the assembly's runner — confirm or refute at the outline against where the capture of `realized_files` itself is to live (`store-schemas/13`, Capture Before Destruction)

## Re-ground record (cleanup 2026-11-15, BINDING over any contradicting older text of this spec)

Written by the `cleanup` verb after a read-only leaf compared every claim of this spec with `origin/main` `393b0210`. The leaf's statements below are not re-read by the orchestrator; the executing plan re-reads each at its outline before relying on it. A claim not named here was corroborated or could not be checked by reading (`corpus verdicts` carries each).

- Claim 2 (contradicted at HEAD): Only PLAN-V2-77 (parked) is a queue row of this v2 ledger; PLAN-96 (shipped #143) and PLAN-43 (landed) have no row in queue/ of pm-mcp-core-m2-m4-v2; they belong to the predecessor epic

## Re-ground record (cleanup 2026-11-17, second pass at `d506e818`, BINDING over any contradicting older text of this spec)

Written by the orchestrator from the verdicts of a read-only leaf (its statements are not re-read here; the executing plan re-reads each at its outline). Since the first pass of the day (`3d453e6c`) the docs review #484 changed 128 documentation files (package files now carry Tasks, Trace and Done When instead of What; the index dependency sets changed; schema blocks are included JSON files); code under `pm-*/src` is unchanged. Each contradicted claim below is stamped `rescoped: yes` with this section as its record; where a claim and this section differ, this section is the later reading.

- Claim 0 (contradicted): doc/plans/change-map.adoc exists at origin/main and README.adoc:246 has the row change-map (blocked, Milestone 10) Correction: A work package change-map exists (doc/plans/change-map.adoc, index row blocked); the spec must stop saying none builds it.
- Claim 2 (contradicted): change-map.adoc and its index row exist at head; README:249 lists own-calls, phase-sub-workflows, domain-extensions-and-architecture, finalize-pipeline-and-merge as deps Correction: Package file and index row exist; dependencies are own-calls, phase-sub-workflows, domain-extensions-and-architecture and finalize-pipeline-and-merge.
- Claim 3 (contradicted): doc/plans/domain-extensions-and-architecture.adoc (task 4 module discovery and dependency parsers) and finalize-pipeline-and-merge.adoc (task 3 Dependency Delta Check) exist as work packages Correction: The waited-on packages do exist: domain-extensions-and-architecture (module discovery, build-file parsers) and finalize-pipeline-and-merge (Dependency Delta Check).
- Claim 5 (contradicted): README.adoc:246-250 and change-map.adoc line 11 record the milestone and edges in the index as the only statement place; kept earlier verdict, edges now written in the index not left as a hypothesis Correction: The Serves and Dependencies edges are stated in doc/plans/README.adoc (Milestone 10; own-calls, phase-sub-workflows, domain-extensions-and-architecture, finalize-pipeline-and-merge).

## Re-ground record (cleanup 2026-11-19 at `e2ae8627`, BINDING over any contradicting older text of this spec)

Written by `pm-mcp-core-m2-m4-v2/cleanup` from the verdicts of a read-only leaf over this spec (its statements are not re-read here; the executing plan re-reads each at its outline). Since the first pass (`d506e818`) #484, #490, #494, #495, #496, #503, #504, #506, #511 and #512 merged. Where this section and an older text of this spec differ, this section is the later reading.

- Not emittable in any part today: `own-calls` (PLAN-V2-77) is parked; `domain-extensions-and-architecture` and `finalize-pipeline-and-merge` are `open` with no code (`core/discovery` holds only `package-info.java`); there is no tree-listing seam (`GitOperations` has only `readFile` `:145`); the index keeps `change-map` `blocked` (Milestone 10, README `:234-238`).
- The specification's section Change Map has 7 items now; Deliverables 4 and 7 mirror an older 5-item text and lack Reach (item 3), Hard to Undo (item 4), the web-app layout sentence (`05:374`) and `reach` / `hard_to_undo[]` in the result (`05:376`); `change-map.adoc:15-20` cites items 4 and 5, now 6 and 7.
- The Dependencies list (83, 96, 77, 43) is stale: PLAN-96 landed (the package file exists), PLAN-43 has no row. D9 alone does not depend on discovery, but no `references.json` codec exists (`references.schema.json` is in the codec-less list), so its pure part would have no caller: carvable only as a new deliverable with a surface row.
- Understated: the developer pages `workflow-finalize.adoc` and `workflow-diagram.adoc` (R06), `doc/LogMessages.adoc` and the log holders (R12), `doc/Traceability.adoc`, `WorkflowStatusSchemaParityTest`, `references.schema.json`. The roadmap's positioned-layout item (`roadmap.adoc:263`) has no owner.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-78-change-map.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Re-Ground Record of 2026-11-23 (cleanup)

Read by a read-only leaf at `origin/main` `8c7eac6faa00b26d0e11f0307c768d85e4fd044b`; the orchestrator did not re-read each point (the verdicts above carry the producer `pm-mcp-core-m2-m4-v2/cleanup`). The outline re-reads every line it acts on. Where this record and an older statement of the spec disagree, this record wins; the older text is kept as the audit record.

- Premise corrected: `doc/plans/change-map.adoc` EXISTS (6 Tasks, `== Trace`, `== Done When`, no `What` section; README row `change-map`, blocked, `doc/plans/README.adoc:222-226`). The package is written; its code is not. The Deliverables are no longer "the bullets of its What".
- Dependencies are stale: the queue holds PLAN-V2-77, 78, 14 and 83 only (no PLAN-96 or PLAN-43 row); the index (`README.adoc:225`) names own-calls, phase-sub-workflows, domain-extensions-and-architecture and finalize-pipeline-and-merge, all existing and unbuilt.
- Change Map section now has seven items (`05-finalize-review-metrics-descriptor.adoc:370-390`): 3 Reach, 4 Hard to Undo, 5 Rendering, 6 Read at Self-Review, 7 Result. The spec's item numbers for rendering (3), self-review (4) and result (5) are one to two off; `change-map.adoc` tasks 3, 5, 6 cite the old numbers.
- D4 omits the reach and hard-to-undo lines below the diagram and the positioned layout (`workflow-dsl/07 #diagram-layout`, `too_large` above 200 nodes). D7: the result record has two more members, `reach` and `hard_to_undo[]`. D6: the refusal is `invalid_link_args` for a submission whose `conflicts` omits an entry of `unexplained_coordinates[]` (05 :300-330), not a missing result.
- D2: not built, and the tree-read seam is absent: `pm-core/.../discovery/` holds only `package-info.java`; `GitOperations` has no tree listing or diff (`readFile` :155 reads a blob from a cache repository only). `pm-workflow` does not depend on `pm-provider-git`.
- D9: `WorkflowStatusSchemaParityTest` covers `status.schema.json` and `waiver-request.schema.json` only; `references.schema.json` is read by `UnreadSchemaFilesTest:180` and no `references.json` codec exists: the writer has no caller. The "or the parity test the outline names" hedge stands.
- Surface text: `04-finalize-step-bands.adoc` carries change map and own calls on the self-review row only (:195); the create-pr row (:231-237) names neither.
- ADR directories to add at the outline: `pm-workflow/doc/adr/finalize/`, `pm-workflow/doc/adr/diagram/`, `pm-core/doc/adr/discovery/`; code facts for the review in `pm-workflow/src/main/java/de/planmarshall/workflow/finalize/`.

## Re-Ground Record of 2026-11-25 (doc-review fold, BINDING over any contradicting older text of this spec)

From `doc-review-2026-11-25-001.md` section 2 (and round 1 section 3): removed coordinates are no longer a result of the self-review; they are items of the sanity step `dependencies` (owner `finalize-pipeline-and-merge` task 5). Deliverable 6 therefore keeps the change map as REVIEW CONTEXT of the self-review facts only. Superseded readings in the records above: the refusal `invalid_link_args` for a submission whose `conflicts` omits an entry of `unexplained_coordinates[]` (the D6 bullet of the 2026-11-23 record) and the citation of items 4 and 5 of `change-map.adoc:15-20`; the specification's Change Map items are now 3 to 5 rendering (Reach, Hard to Undo, Rendering), 6 Read at Self-Review (context), 7 Result. Replaced-coordinate results are dropped from the listed facts (lines of #168 and #177 above). The Expected Surface keeps every entry for the context facts; an entry that exists only for the coordinate refusal (candidate, HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/spi/validation/SelfReviewSubmission.java` and its test) is to be re-swept against the tree at re-ground and dropped if listed only for that refusal. `doc/plans/change-map.adoc` is not edited by this fold; whether its text follows is decided at this plan's re-ground (the doc-review finding names the spec lines only).

## Re-Ground Record of 2026-11-25 (cleanup, BINDING over any contradicting older text of this spec)

Read by a read-only leaf at `origin/main` `3ba79c4f96dab1f6954cbaf2b5a7a145f8d4556e` after #594, #598, #599, #603 to #606, #609, #612 and #613; the orchestrator persisted every verdict through `corpus set-verdict` (producer `pm-mcp-core-m2-m4-v2/cleanup`; contradicted claims are re-scoped by this record). The outline re-reads every line it acts on. Where this record and an older statement differ, this record wins.

- Contradicted and absorbed: (0) `doc/plans/change-map.adoc` EXISTS (README.adoc:222-226, status `blocked`, Milestone 10); the package is written and no change-map code exists. (3) The packages this one waits on exist and are unbuilt: `domain-extensions-and-architecture` task 4 (module discovery and parsers, README row `open`) and `finalize-pipeline-and-merge` task 5 (the sanity step `dependencies`, row `open`); the Dependency Delta Check is no longer a specification section (#607). (5) The edges are those of `README.adoc:225`: own-calls (`blocked`), phase-sub-workflows, domain-extensions-and-architecture, finalize-pipeline-and-merge; the older list (PLAN-V2-83, 96, 77, 43) is history.
- Lines moved: `#change-map` is `05-finalize-review-metrics-descriptor.adoc:312`, items 1 to 7 at `:320-326`; PM-WATCH-PHASE-114 is `phase-workflows.adoc:1610-1626` and now also requires that a decision of reversibility `costly` is listed under the map's hard-to-undo decisions (the Done-when does not carry that guard yet; it needs the decision-record source, Store Schemas 18).
- ADR directories are `pm-workflow/doc/adr/workflow-finalize/` (next free 005), `pm-workflow/doc/adr/workflow-diagram/` (next free 013) and `pm-core/doc/adr/core-discovery/` (absent, 001); the earlier names `finalize/`, `diagram/` and `discovery/` were wrong. `pm-workflow/.../diagram/` is not empty: it holds `WorkflowDiagramGenerator`, the three renderers and the instance renderers the positioned layout extends. The `create-pr` row of `04-finalize-step-bands.adoc` names neither the map nor the own calls (`:231`, `:237`); only the self-review row (`:195`) does.
- Done-when: the D9 acceptance (`references.schema.json` accepts the result) needs a new test or a codec: no references codec exists and `WorkflowStatusSchemaParityTest` covers `status.schema.json` and `waiver-request.schema.json` only. The refusal half of the D6 guard is already met (no `unexplained_coordinates` and no coordinate refusal exist); the `replaced coordinate` guard is meetable only after the sanity step `dependencies` exists.

## Re-Ground Record of 2026-11-27 (cleanup, head 115d5987)

Binding, like every earlier record: where a claim above and this record disagree, the record is what is true at the head; the claim bullets are not rewritten. Read by read-only leaves (R21) from the head `origin/main` 115d5987; each contradicted claim is absorbed here (`rescoped: yes`).

- Claim 0: doc/plans/change-map.adoc exists; README.adoc:222-226 has row change-map (blocked, Milestone 10); no ChangeMap/change_map/edges_partial symbol in any Java file (git grep empty). Package is written, code is not. Caused by #143/#151 and later; #623 touched the file.
- Claim 3: doc/plans/domain-extensions-and-architecture.adoc (module discovery, parsers, :18) and doc/plans/finalize-pipeline-and-merge.adoc (sanity step dependencies, :19) exist as packages; Dependency Delta Check no longer a spec section (#607). Both unbuilt: pm-core discovery holds only package-info.java.
- Claim 5: Milestone 10 holds (README.adoc:226) but the edges differ: README.adoc:225 names own-calls, phase-sub-workflows, domain-extensions-and-architecture, finalize-pipeline-and-merge, not PLAN-V2-83/96/77/43; the index is the stated place for them.
