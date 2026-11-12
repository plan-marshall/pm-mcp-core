# Landing Analysis: PLAN-V2-58 — the verifier's simple-cycle enumeration is restricted to the cyclic components

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
pr: #379 (merge `6a49db14`, state `merged` per the CI abstraction)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-12 from the message
> `rb-v2-58-verifier-cycle-scale-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, executor
> 0.1.2106, plugin 0.1.2106). The message is a lead. The three layers were read by one read-only leaf from git at
> `6a49db14` (no build, no test run); the orchestrator re-read the points marked "re-read"; the provider figures were
> re-read from GitHub and the SonarCloud public API (see Metrics).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 `UnitGraphCycleScaleTest` beside the unchanged tests | shipped | 10 tests; the diff touches no existing test; the pass is the lane's claim (not re-run) |
| D2 equality with the legacy result in content and order, measurement in ADR and PR body | shipped | order-sensitive `assertEquals` against a private copy of the pre-change search for the 12 fixtures, diamonds n=1..5, phases k=1..5, a 10-section sprawling and chained unit, a self-loop, the 16-diamond (65536 cycles) and 200-site units; ADR `workflow-verify/034` carries the table (arithmetic of states, edges and cycles checks against the generators; the before/after timings are not verifiable by reading); the PR body was not read |
| D3 the change of `UnitGraph.findSimpleCycles` | shipped, beyond the spec's wording | Kosaraju over the one edge set the legacy search used (`UnitGraph.java:413-450`, `finishOrder` iterative, no stack risk), the search starts only in cyclic components and stays inside the start's component (`:393`, re-read); `dfsCycles` stays recursive (depth at most the component's state count). Private helpers `CycleScope`, `cyclicComponents`, `finishOrder`, `successorsIn` were added; the spec's Expected Surface named `findSimpleCycles` and `dfsCycles`. |
| D4 guide | shipped | `workflow-verify.adoc` § Rule 11 "Cost of the enumeration", the `UnitGraph` entry, ADR range updated |
| Not in the declared surface | none | the diff is exactly the four declared files |

## Three-Layer Analysis

Read at `6a49db14` by one read-only leaf (`execution-context-level-3`).

### Layer 1 — Specified

Every Done-when clause is in the diff. Sonar, build, coverage and links clauses are the lane's claims and were not re-run by the leaf.

### Layer 2 — Implemented

Assertion messages: 0 of 37 outside 20-60 characters. The retained `legacyFindSimpleCycles` is a legitimate oracle (a reference implementation of the same function, required by D1), not a hand-written fake. Deviations: `fixturesMatchLegacy` (`:68`, 12 variants), `diamondsMatchLegacy` (`:81`, 5) and `phaseMatchesLegacy` (`:97`, 5) are `for` loops inside one `@Test` where `CLAUDE.md` asks for `@ParameterizedTest` at three or more variants (re-read); no `@EnableGeneratorController` or `Generators.*` (the file declares its data contract shapes, at the edge of the rule); `finishOrder` and `successorsIn` carry `@param`/`@return` without a summary sentence. ADR 034 is the next free number and its structure matches ADR 033. ADR 034 tabulates three families while the test and the guide have four: the chained-sections unit, which drove the final design, has no measured row in the ADR.

### Layer 3 — Correct

No major finding. The SCC restriction is correct, the result order is the legacy order for all units (argued from the start loop, the unchanged successor order, the unchanged `> startIndex` rule and the pruning of branches that leave a component), and ADR and guide text match the code.

Minor: (1) the ADR and the lane say the cost follows "increasing-index simple paths"; the code restricts to indexes greater than the START index (`UnitGraph.java:393`, re-read), not to path-increasing indexes, so the wording is loose; (2) the three loop-style tests hide the failing variant except through the message; (3) no test pins a cyclic component unreachable from the start state or a unit with unknown transition targets; (4) the ADR omits the measured row of the chained family.

**Residual risk accepted by the spec, not a defect:** there is no cap in production code. `findSimpleCycles` still materialises every cycle in an `ArrayList`; a diamond-chain unit with n=30 carries 2^30 cycles, so the validation stalls or exhausts the heap, and both callers (`WorkflowValidator.java:262`/`:2565`, `SubWorkflowInvariants.java:296`) iterate the whole list. The 65536-cycle and 15 s bounds exist only in the test; the ADR and guide state that a cap is not taken. Inside one component the search is still not polynomial in cycles: by reading the leaf built only a factor-2 case of dead paths, a bound is not shown. A cap, or a refusal of a unit for its cycle count, remains an operator decision.

Not shown by reading: all timings, the failing-run claims, the PR body, Sonar/build/coverage results, any stack-overflow bound beyond the component size. PLAN-V2-69's premise (parked; item 13 declares `UnitGraph.java` as a user of the expansion keys) is not broken: `findSimpleCycles` runs on `UnitGraph.of(definition)`.

## Conformance to the Project's Own Rules

See Layer 2. Landing facts: `unresolved_review_threads=0`, `review_body_findings_total=1`, `handled=1`.

## Unverified leads

- The failing-run claims (`7807546d` log `d7a0cc0f…`, `0a93051d` log `aa52ff7c…`), the timings in the ADR table, the PR body's D2 measurement.
- The lane's rule breaches (shell file reads, a read and run of the documentation checkout's `trace.py`, operator-approved) are recorded in the message; no effect on the code.

## Metrics and Anomalies

`complete` for the landing facts except `total_tokens` `unknown` (the Open Defect on runbook-lane landings in `epic.md`); `plugin_version` `0.1.2106`. Self-review ended `budget-exhausted` after four rounds (the operator approved the fourth; its one item was fixed without a fifth round). CodeRabbit required, reviewed at `0a93051d` after seven quota refusals; its one Major finding (the guard was per cyclic state, not per component) was reproduced as family (d), fixed in `bdd4265a`. The lane's `workflow-integration-sonar` verb answered `unconfigured`; the lane read SonarCloud over HTTP (0 open, gate OK at `fbba7fad`). R17 links: 3 broken, all naming `client-profiles/04-judgment-endpoint.adoc` in the documentation checkout (PR #374 repoints them).

## Routing and Merge Behavior

One PR (#379), squash through the merge queue as `6a49db14`. The message carries no `candidate-lesson`; nothing is routed to `lessons-routing`.

## Reconciliation Actions

Row PLAN-V2-58 `running` -> `shipped`, `pr` `#379`, `landing` `landings/PLAN-V2-58.md`, `plan_marshall_plan_id` `rb-v2-58-verifier-cycle-scale`; message archived; Watch entry in `epic.md`; decision log.

## Follow-Ups

Leftovers are routed by the operator's rule of 2026-11-12 (every leftover into a staged plan, a cleanup plan only when none fits); the placement is recorded in the Watch entry and the decision log. Items: the three loop tests to `@ParameterizedTest`, the ADR wording and the missing chained-family row, the unreachable-component test, and the open decision on a cycle cap.
