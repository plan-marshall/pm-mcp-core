# Landing Analysis: PLAN-V2-82 — The plan-instance position per (unit, state) and the nested policy calls

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
pr: #492 (`55a37c2f`, merged through the merge queue 2026-11-18, runbook lane, opencode, `rb-v2-82-instance-composition-residue`)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message `rb-v2-82-instance-composition-residue-001.md`) after verifying claims against git, the PR and one read-only three-layer leaf; a pasted claim is a lead, never a fact.

## Deliverable Fidelity vs Spec

Four of four deliverables met (`deliverables_done=4`), each with the operator-accepted adaptation the lane records (five operator questions, all answered, no free text).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the one guard test #236 removed (rewritten 2026-11-17) | shipped-modified | `PlanInstanceComposerTest.ComposedSet.prunedCallInsideAComposedUnit` exists; it prunes `execute.replan/replan` (a digest-bound unit) because `execute.land-entry/land` is entry-bound and #236's guard keeps it (`execute.pmwf:26,78`); the position is `(replan, replan.assess)`; so it covers a pruned call inside a composed unit but not a composed FILE omitted because only a pruned call reached it (L2-1) |
| D2 the position rule over `(unit, state)` | shipped-modified | `InstancePosition(UnitState current, List<UnitState> callStack)`, nested `UnitState(unit, state)`, `at(String)` = plan unit, a unit-aware factory, `missingFrom` by unit; the composer builds the map per unit; the witness is `finalize.push-barrier` (the spec's `completed` scenario cannot be produced: a rule prune applies by bare id to every template, only the entry guard makes units differ; the spec's Verify-first clause allowed for this); `positionIsPerUnit` fails on the merged-set rule (mutation reported) |
| D3 nested policy calls | shipped-modified | `PolicyCall` carries `@Nullable String inUnit` (the spec said `Optional<String>`; R20 and CLAUDE.md forbid an Optional component); `HeaderJson`, `HeaderSchemaCheck`, `DiffSummary.byCallSite` keyed by `(inUnit, callSite)`, `CeilingChange` carries `inUnit`; schema files edited in place (format version 1, R13) |
| D4 documents and the Watch | shipped-as-specified | Workflow DSL 09, Store Schemas 14, the guide, ADRs 024 to 027, watch item `PM-WATCH-STORE-60` |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`), a leaf of its own for this landing; the orchestrator re-read the points marked "re-read" at `55a37c2f`.
- **Verdict:** layer 1 findings (3, all the spec's, all confirmed); layer 2 findings (minor); layer 3 clean at blocker and major (minor and info only).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | D2's Done-when: `completed` pruned from unit A while B declares it is refused | checked | refutes | a rule prune applies by bare id to every template (`InstancePruner.Selection.resolve`, `case STATE`); only the entry guard makes units differ; the lane used `finalize.push-barrier` and the operator accepted |
| L1-2 | D1's premise: the pruned call is not entry-bound | checked | refutes | `execute.land-entry/land` is entry-bound (`execute.pmwf:26,78`) after #236; the rewritten D1 still named it |
| L1-3 | D2: `PlanInstanceStoreTest` and `PlanProcessStatesTest` need no change beyond the message | checked | refutes | `at(String)` = plan unit forces the positions of other units' states through the unit-aware factory |

**Layer 1 verdict:** findings — 3. The orchestrator's rewrite of D1 on 2026-11-17 missed L1-2 (it re-read the removed test, not the fixture's entry bindings): an authoring defect of the orchestrator, absorbed by the lane's question 2.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D1 | `PlanInstanceComposerTest.java:1270` | `prunedCallInsideAComposedUnit` | the Done-when is partly narrowed (L2-1) | checked |
| D2 | `InstancePosition.java`, `PlanInstanceComposer.java` | `positionIsPerUnit` (`:1305`) | lacks the contained `(B, state)` arm and a contained call-stack frame (the frame appears on the refusal arm only) | checked |
| D3 | `PolicyCall.java`, `HeaderJson.java`, `HeaderSchemaCheck.java`, `DiffSummary.java` | `nestedPolicyCallsInTheHeader`, `headerAndComposedBodyAgree`, `DiffSummaryTest` and `PlanInstanceHeaderTest` cases | no composer-level test raises a budget onto a nested call and follows it into `header.policyCalls()` (`:494`; the nested ceiling is covered on hand-built headers, `DiffSummaryTest.java:196`), not shown by reading | checked |
| D4 | docs | links | none | checked |

Files no deliverable or the PR's surface list explains: the whitespace-only edit of `pm-core/.../QualificationSchemaParityTest.java:104` (re-read: the diff splits a joined line; it is the formatter residue PLAN-V2-96's merge left on `main`, so this plan **fixed** the Watch item "line 104 is joined by `verify -Ppre-commit`" while its own spec says "no change to pm-core"); the `DUPLICATE_CALL_SITE` refusal (`HeaderSchemaCheck.java:76` constant, `:243` use, re-read), stated by no spec, ADR, guide or schema; an `empty_harvest` correction in `store-schemas/14` (correct, consistent with spec 09).

**Layer 2 verdict:** findings — 5 (minor).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `HeaderSchemaCheck.java:230`, `:243` (re-read), `PlanInstanceHeaderTest.java:357` | a header listing one `(unit, callSite)` twice | an unrequested, undocumented refusal; R15 item 1 (a refusal has one owner) is not shown for it | minor |
| L3-2 | `HeaderSchemaCheckTest.java:395`, `HeaderSchemaCheck.java:124` | `/policy_calls/0/in_unit` of the wrong type | no test row for the type violation or the optional acceptance; no test ties the schema file's `in_unit` to `CALL_OPTIONAL` | minor |
| L3-3 | `DiffSummary.java:187` | a ceiling "did not change" message | names no unit (R15 item 6 sweep: every position message converted, this one not) | minor |
| L3-4 | `PolicyCall.java:56`, `DiffSummary.java:168` (re-read) | — | the 3-argument `PolicyCall` and the 4-argument `CeilingChange` constructors delegate with `null`: back-compatibility shims against "pre-1.0: no shims"; main code does not use them | minor |
| L3-5 | `PlanInstanceComposer` (position map) | a sub-unit named `plan` | would overwrite the plan unit's position set; guarded only by the loaders | info |
| L3-6 | `pruned[]` | — | still dedups by bare id | info |

**Layer 3 not shown by reading:** build, test, coverage, compiler warnings, Sonar and the generator and assertion scanners (the lane's claims). Per-unit containment, call-stack frame handling, plan-unit-first and sub-unit ordering, `CallKey` separation, `in_unit` nullability, the header round trip and the refusal message: no defect found.

**Layer 3 verdict:** clean at blocker and major; findings — 6 (minor and info).

### Conformance to the Project's Own Rules

Fourteen rows: four deviate (the pre-1.0 no-shim rule, "no change to pm-core", R15 item 1, R15 item 6), the rest conform (`@Nullable` instead of `Optional`, `@Builder` on records of four or more components, no generic catch, no new log record, format version 1, ADRs and guide, R10), build and scanners not shown.

## Metrics and Anomalies

- Tokens: `unknown` (harness opencode); `plugin_version` `unknown`; `executor_version` is 0.1.2152 in the facts block and 0.1.2149 in the lane's own rule check (`executor-skew`: match): a difference between two statements of the same report, not resolved by the landing.
- Anomalies: three self-review rounds (22 findings) and a fourth continued on the operator's word; one server restart mid-run (resumed at the CI wait); the base worktree at `13a1236c` reported `executor_in_worktree: absent` and was removed (no base evidence claimed; the red evidence is by mutation); `quarkus-alignment` failed once on a Maven Central HTTP 429 and passed on re-run; two pending review shells published with `ci pr submit-review --event COMMENT` (the provider refused further replies); Sourcery refused (250000 diff characters, 1 day 6 hours); `verify -Ppre-commit` took 285 to 506 s.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `76b903af` (13 inline and 1 additional) and `ccaaa117` (5 inline and 3 outside-diff), 22 findings, 21 fixed and one refuted with a reason (`HeaderSchemaCheck.POLICY_UNITS` mirrors the schema enum: pre-existing). `review-gate --pr-number 492`: unresolved threads 0 (matches), body findings total 19 (matches); `review_body_findings_handled` 19 **not re-verified** (the lane's progress file holds no `finding:` line; not evidence of a false claim).
- CI/merge: merged through the merge queue as `55a37c2f` onto `51d350e3`.
- Sonar: `sonar_open_new_issues=0`, gate OK, new coverage 96.0 % on the lane's read (not re-read here); `java:S6126` and `java:S1612` fixed.
- Parallelization: ran beside PLAN-V2-59, 96, 145 and, at the end, 10, 17 and 46 (started 2026-11-18). Realized footprint: 27 files, `doc/Traceability.adoc` and the store-schemas documents among them; the gate's earlier prompts (3 files with PLAN-V2-59, bookkeeping) did not become a collision; PLAN-V2-114 (2 files) and PLAN-V2-13 (4) are staged.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#492`
- [x] row `landing` stamped `landings/PLAN-V2-82.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-82-instance-composition-residue`
- [x] `epic.md`: Watch for the residue, annotation for PLAN-V2-13 (now unblocked)
- [x] resume anchor, view regenerated

## Follow-Ups

- L2-1 to L2-5, L3-1 to L3-4: PLAN-V2-114 item 51.
- PLAN-V2-13 (`plan-instance-versions`): its dependency on this plan is met; its spec still says `InstancePosition` is `(currentState, callStack)` and owes the engine's `instance` history line and the `CallStack`-to-`InstancePosition` mapping (the lane records both as owed to it): it re-reads at its outline.
- `HeaderSchemaCheck.POLICY_UNITS` as a hand-maintained mirror of the schema's `unit` enum: item 51.
- The local branch `fix/instance-composition-guards` is redundant (the commit it named never existed): the operator deletes it.
