# PLAN-V2-44: The positioned layout of a unit and of a graph of modules, computed on the server

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
origin: PLAN-121 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-44-positioned-layout.md` and is queued as one row file, `queue/PLAN-V2-44.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: item (2) of the epic entry "The specification of #181, #186 to #189, #191, #192 added requirements no staged package carries": Workflow DSL § Positioned Layout (`PM-WATCH-DSL-37`, `-38`), which no work package owns.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R15, R17, R18, R19. (R12 applies only if the trace matrix shows a message to add; R14 and R16 do not apply: `pm-workflow` only; R15 item (4), a completeness test over an enum derives the expected set from the enum, applies to the node-kind table of D1; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-workflow/doc/developer/workflow-diagram.adoc`; ADR directory: `pm-workflow/doc/adr/workflow-diagram/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); none expected.

## Objective

Implement Workflow DSL § Positioned Layout (`doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc`, anchor `diagram-layout`): the diagram generator of `de.planmarshall.workflow.diagram` also lays a graph out and returns the result as data, which the web app draws; the server never sends drawing markup. The layout is a layered layout (layers along the edge direction, back edges reversed and marked, a fixed number of barycentre sweeps, ties broken by declaration order, edges routed as polylines through one bend per crossed layer), with text widths from an advance-width table compiled into the generator, the form `{width, height, direction, nodes[], edges[]}` with integer pixel coordinates serialized in a fixed field order, byte-identical for the same input, and the answer `too_large` above 200 nodes. Nothing of this exists: the package renders three text forms of a unit and a plan instance, and no class lays out. The layout takes a neutral graph, so that the two graphs the specification names, a unit of the plan instance addressed by the `node_id` of the process tree (PLAN-V2-26) and the change map of a plan (PLAN-V2-78), are adapters over it; this plan builds the layout, the unit adapter with a caller-supplied node id, and leaves the change-map adapter to the plan that computes the change map. Serves: PM-IMPL-5; Roadmap Milestone 10 (the web app's process diagram and the change map). Where: pm-workflow, package `workflow.diagram`, new classes.

## Deliverables

1. **The neutral graph and the layered layout.** New package-private or public types in `workflow.diagram` (names decided at the outline; the shape below is the specification's): an input graph of nodes `{id, kind, label}` and edges `{from, to, kind, label}` with a direction (`down` or `right`); the layered layout: layer assignment along the edge direction, cycle breaking by reversing each back edge a depth-first search from the first node (the unit's start state) finds in declaration order, a reversed edge drawn in its own direction and marked `back`, a fixed number of barycentre sweeps (a named constant) with every tie broken by declaration order (for a graph without a start state, by the node's id), and polyline routing from the border of the source box to the border of the target box through one bend point per crossed layer. All arithmetic on integers; no map is iterated in hash order (`LinkedHashMap` or sorted lists), no number is formatted with the default locale.
2. **The text width.** An advance-width table for the printable ASCII characters of Arial (the metrically identical Liberation Sans), regular and bold, in thousandths of an em, compiled into a class of the package; a fixed width for any other character and one em for a wide East Asian character (the ranges the table names are decided at the outline and recorded in the ADR); a label longer than the maximum box width wraps at word boundaries, and a single word longer than that width is cut with an ellipsis; box width and height from the wrapped lines at a font size of 14 px.
3. **The form and the bound.** `{width, height, direction, nodes[], edges[]}`; a node `{node_id, kind, label_lines[], x, y, w, h}`, an edge `{from, to, kind, label, back, points[]}`; the fields serialized in the specification's order (written with the streaming `JsonGenerator` of `jackson-core`, which `pm-workflow` already depends on, not with an object mapper), the nodes and edges in layout order; coordinates integers. A graph of more than `200` nodes is not laid out: the answer is `too_large` with the node count.
4. **The unit adapter.** From a verified `WorkflowDefinition` (the unit of the plan instance in force, the plan unit or one phase sub-workflow) the graph of its states as nodes, with the kind of `StateKind` and the `node_id` that a caller-supplied function gives each state (the process tree of PLAN-V2-26 owns the form of the id; this plan fixes only that the id is opaque and carried through), and its outcome transitions as edges labelled with the outcome. A state pruned from the composed unit is not in the unit and so not in the graph. The adapter is a pure function of the unit and the function. The change-map adapter (modules as nodes, `kept`/`added`/`removed` edges, a removed or replaced coordinate as a label line, direction `right`) is not built here: it is the plan that computes the change map (PLAN-V2-78, parked), which calls the neutral graph this plan ships.
5. **The golden tests that the watch items ask for.** `PM-WATCH-DSL-37`: golden files lay out every fixture twice in one JVM and once in a fresh JVM under another default locale and compare the bytes; one fixture declares its states in an order other than the alphabetical one and one has two nodes with equal barycentres, so a tie broken by name or by hash order fails. `PM-WATCH-DSL-38`, the server half: a generator test measures labels of wide characters (`W`, `M`), narrow characters (`i`, `l`), bold and a wide East Asian character against the table. The browser half of `PM-WATCH-DSL-38` (draw the fixture layout with the shipped font in the web app and check every label lies within its box) is the web app's, in `pm-mcp-clients`: the watch item stays in `doc/implementation-watch/workflow-dsl.adoc` with its server half marked covered, or is deleted with a pointer if the maintainer prefers; the plan decides and records it in the ADR. Fixtures: the unit fixture `diagram/diagram-fixture.pmwf` that the existing generator tests use, a unit with a back edge, a unit of exactly 200 and one of 201 nodes (generated in the test), and a graph with a label wider than the maximum.
6. **The documents.** `workflow-diagram.adoc` (the new types, the form with an example, the determinism rule, the bound), `package-info.java`, one ADR (the choice of the algorithm's constants, the table's source and the wide-character ranges, the neutral graph as the seam to the two callers, the split of `PM-WATCH-DSL-38`), and the specification: § Positioned Layout is replaced by a pointer to the guide and `doc/Traceability.adoc` (the implemented section is deleted, R05) with `PM-WATCH-DSL-37` deleted and `-38` reduced to its browser half.

**Done when:** a graph of the fixtures is laid out to the form of the specification; two layouts of one input in one JVM and one in a fresh JVM under another default locale are byte-identical; a tie of equal barycentres and a declaration order other than alphabetical are broken by declaration order; a label wraps at word boundaries and a too-long word is cut with an ellipsis, and the width table is checked against characters of every class; a graph of 200 nodes is laid out and one of 201 answers `too_large` with its count; the unit adapter carries the caller's `node_id` and the outcome labels; the guide, `package-info.java`, the ADR and the specification change are written. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-workflow` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (none) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:**

- Requirements: PM-IMPL-5
- Specification: Workflow DSL § Positioned Layout (`workflow-dsl/07-loop-back-diagrams-verification.adoc`, anchor `diagram-layout`, lines 51 to 62); § Automated Mermaid, AsciiDoc Table, and ASCII Diagram Generation (line 49, the bullet "Positioned layout"); Phase Workflows § Change Map (the second graph; read, not implemented)
- Watch: `doc/implementation-watch/workflow-dsl.adoc` `PM-WATCH-DSL-37` (line 544) and `PM-WATCH-DSL-38` (line 558)

## Standing Constraints

- **Log messages: none expected.** An INFO, WARN or ERROR message the trace matrix shows goes into the holder of the module (ADR `doc/adr/005-one-log-message-holder-per-module.adoc`) and the inbox message says so.
- **No rendering.** The server sends no drawing markup (no SVG, no Mermaid in this form); the result is data. No dependency is added (the streaming `jackson-core` is present); no font file is read at run time.
- **No dependency on the process tree.** The `node_id` form is PLAN-V2-26's; this plan takes it as an opaque string from a function the caller gives, and PLAN-V2-26 adopts it. A statement of the form of the id in this plan would be a second owner.
- **The change-map adapter is not built here** (PLAN-V2-78 computes the change map; the data does not exist at `a8da4104`).
- **Determinism is a feature, not an effort:** a layout step that iterates a hash map, compares floating-point values without a tie rule or formats a number in the machine's locale is a defect (the hazard of `PM-WATCH-DSL-37`).

## Claim Labels

- OBSERVED: the specification of the layout is in the repository and unimplemented: `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` anchor `diagram-layout` (line 51) with the bullets Graphs, Layered layout, Text width, Form, Determinism, Bound (lines 55 to 61); the bullet "Positioned layout" at line 49 — read at `a8da4104`
- OBSERVED: no class lays out a graph: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/` holds `AsciiDocTableRenderer`, `AsciiTableRenderer`, `InstanceAnnotations`, `InstanceAsciiDocTableRenderer`, `InstanceAsciiTableRenderer`, `InstanceMermaidRenderer`, `MermaidRenderer`, `StateKind`, `StateTransitions`, `WorkflowDiagramGenerator`, `package-info`; `git grep -i "layout\|positioned" pm-workflow/src/main/java` finds no class of the diagram package (and `git grep -i "node_id\|nodeid"` over `src/main` of `pm-core`, `pm-workflow` and `pm-runtime` finds nothing)
- OBSERVED: the facade is `WorkflowDiagramGenerator`, "the only public type: three static methods, one per form" — `pm-workflow/doc/developer/workflow-diagram.adoc` § Types; ADR `workflow-diagram/001-one-facade-three-forms.adoc`; the renderers are package-private
- OBSERVED: `pm-workflow` depends on `jackson-core` and `pm-core` (`pm-workflow/pom.xml` lines 22 and 26); the golden files of the generator tests sit in `pm-workflow/src/test/resources/diagram/` (`diagram-fixture.{adoc,mmd,pmwf,txt}`, `instance-fixture.{adoc,mmd,pmwf,txt}`) and are read by `WorkflowDiagramGeneratorTest` with `DiagramFixtures`
- OBSERVED: the watch items: `PM-WATCH-DSL-37` "The same graph laid out twice gives two layouts" (`doc/implementation-watch/workflow-dsl.adoc` line 545; guard: golden tests twice in one JVM and once in a fresh JVM under another locale, a non-alphabetical declaration order, equal barycentres) and `PM-WATCH-DSL-38` "A label that fits on the server overflows its box in the browser" (line 559; guard: a generator test of the width table, and a browser test of the web app)
- OBSERVED: no package owns the layout: the epic entry names PLAN-43, PLAN-V2-26, PLAN-V2-78 and the generator as touched and none carries it; PLAN-V2-26 lists the specification part only ("Positioned Layout constrains the node_id form of D2"), PLAN-V2-78 lists the specification, the watch items and the `diagram/` directory ("no class nameable yet") and leaves "who builds the positioned layout of the change map" as an open question (its Dependencies, #181 note)
- OBSERVED: the process tree and its `node_id` are not built: PLAN-V2-26 (staged) specifies `GET /api/v1/plans/{plan_id}/process` with a `node_id` per node; no class of `pm-workflow`, `pm-runtime` or `pm-core` carries a process tree (`git grep -i "node_id\|nodeid"` over `src/main` of the three modules finds nothing)
- HYPOTHESIS: a layout input independent of the process tree and of the change map is enough for both callers — confirm/refute at PLAN-V2-26 D2 (the node and its `node_id`) and PLAN-V2-78 (the module graph) when they are outlined (verify-at-outline; if either needs an input field the neutral graph lacks, the plan adds an opaque `attributes` member and says so in the ADR)
- HYPOTHESIS: a unit of the composed plan instance has no pruned states left, so "a pruned state is left out, as in the tree" needs no code in the adapter — confirm/refute at `workflow/plan/PlanInstanceComposer` and `InstancePruner` (verify-at-outline)
- HYPOTHESIS: `WorkflowDiagramGenerator` gets one added method for the unit layout; if the outline prefers a separate public type, the class list below changes by that file only — confirm at the outline against ADR `workflow-diagram/001`
- Verify-first clause: before scoping, list the open pull requests for one that touches `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/` or the Positioned Layout section; re-read the section and the two watch items at the head (the sibling documentation repository may hold a newer wording: fetch it before the links check); a refutation of any claim above loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/PositionedLayout.java` — new: the layered layout (the neutral graph and the form may be nested records of it or separate new files)
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/TextWidth.java` — new: the advance-width table and the wrapping
- HYPOTHESIS: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/UnitLayoutGraph.java` — new: the unit adapter
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/WorkflowDiagramGenerator.java` — one added method for the unit layout (the facade)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/diagram/package-info.java` — Javadoc of the package
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/PositionedLayoutTest.java` — new: golden, determinism, tie, back edge, bound
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/TextWidthTest.java` — new: the width table and the wrapping
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/DiagramFixtures.java` — fixtures shared with the generator tests (additive: the layout fixtures), or a new fixtures class if the outline prefers it
- HYPOTHESIS: `pm-workflow/src/test/resources/diagram/` — new golden files `layout-*.json` beside the existing goldens (new files; the existing goldens do not change)
- OBSERVED: `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` — specification part; § Positioned Layout only (replaced by a pointer); not the other sections
- OBSERVED: `doc/implementation-watch/workflow-dsl.adoc` — watch document; `PM-WATCH-DSL-37` deleted, `-38` reduced to its browser half (lines 544 to 570)
- OBSERVED: `pm-workflow/doc/developer/workflow-diagram.adoc` — developer guide of an existing package (additive; shared bookkeeping under R19)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-diagram/` — one ADR, the next free number of the directory (012 is the last at `a8da4104`; renumber against `origin/main`, R11)
- Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`. This plan has no package file.

## Dependencies and Sequencing

- Depends on: nothing. It builds the layout; PLAN-V2-26 (`process-and-overview`, staged) and PLAN-V2-78 (`change-map`, parked) call it.
- Overlaps with: PLAN-46 and PLAN-105 (running): PLAN-105 declares `workflow/diagram/InstanceMermaidRenderer.java`; this plan adds new files and one method of `WorkflowDiagramGenerator.java`, neither of which PLAN-105 declares: 0 shared files (a rebase conflict in the facade is possible if PLAN-105 adds a call there; `corpus cross-check` at emit time decides). Staged and parked plans that declare the same part files: PLAN-V2-26 (`workflow-dsl/07` and the `node_id` note), PLAN-V2-13 and PLAN-V2-69 (`doc/implementation-watch/workflow-dsl.adoc`), PLAN-V2-78 (`workflow-dsl/07`, the watch document, the `diagram/` directory): 1 shared specification or watch file with each, in different sections or items; whichever merges second merges the base branch before the merge gate. PLAN-V2-26 and PLAN-V2-78 should drop their notes on the layout and name this plan when they are next re-grounded.
- Shares `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` with PLAN-V2-33 (`scenario-task-protocol`) and PLAN-V2-49 (`scenario-worker-faults`) (different sections): 1 shared specification file, to be merged in order of merge.
- Disjoint from the other plans of this cluster (PLAN-V2-58 (`verifier-cycle-scale`), PLAN-V2-57 (`default-plan-lanes-and-run`)) by file.
- Package bookkeeping (R01/R05): no work package file covers this plan (like PLAN-95); the section of the specification is deleted by this plan's pull request as R05 says.
- Adjacent to: the web app of `pm-mcp-clients` (draws the layout; the browser half of `PM-WATCH-DSL-38`), the runtime route that serves the layout (`plan-marshall-mcp` / PLAN-V2-26 `process-and-overview`), `pm-api`.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-44-positioned-layout.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
