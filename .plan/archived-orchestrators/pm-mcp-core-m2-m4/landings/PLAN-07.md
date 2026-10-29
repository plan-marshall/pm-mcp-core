# Landing Analysis: PLAN-07 — Workflow DSL parser and AST

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #27 (https://github.com/plan-marshall/pm-mcp-core/pull/27), merge commit d091527

> Landing record for one shipped plan. Lives at `landings/PLAN-07.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `workflow-dsl-parser-and-ast-019.md` (landing-facts complete, no missing
key), corroborated on 2026-10-11 against the PR state (MERGED 15:20Z), the file list of d091527,
`plan-marshall-documentation` #27 (MERGED), the `/review` comments and bot runs on #27, and the
plan's archived execution manifest and decision log.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Immutable AST | shipped-as-specified | `workflow/ast/` main and test |
| Lexer, parser and result (`Parsed` / `Refused` with one diagnostic) | shipped-modified | `workflow/dsl/`; per operator decisions the instance header follows the `PlanInstanceHeader` store schema and the scope clause is mandatory in the EBNF |
| Printer | shipped-as-specified | `WorkflowPrinter`, round-trip tests with fixtures in `src/test/resources/` |
| Index row | shipped-as-specified | `workflow-dsl-parser-and-ast` `done` on `origin/main` |
| Documentation | shipped | `plan-marshall-documentation` #27 merged as 9f220c0 (developer guide `workflow-dsl.adoc`, 228 lines of AST node model moved out of the specification, the `adjudicate.*` renames) |

61 files, +5,934. Surface: within the declared `workflow/dsl/`, `workflow/ast/` and
`src/test/resources/`, plus the index row and `pm-workflow/pom.xml` (undeclared), which adds the
parent-managed `jackson-core`. The operator consented at refine (decision log 5ec939); `pm-core`
already uses it.

## Metrics and Anomalies

- Tokens: 6,614,736 recorded; wall time 81,596 s (about 22.7 hours).
- Two specification contradictions surfaced only during execute: the instance header against the
  store schema, and the unit `question.adjudicate` against the reserved word `question`. Refine's
  premise check (6 of 6 valid) did not catch them, so execute was reopened once (lesson routed as
  `pm-mcp-core-m2-m4-071.md`).
- Two finalize fix rounds (6 fix tasks); the pre-push gate ran degraded (known, lesson 2026-10-10-07-016).
- The foreign-PR archive gate failed closed because the doc branch was `docs/…` and not
  `feature/{plan_id}`; archived by operator authorisation.

## Routing and Merge Behavior

- Review: the mailbox advisory was applied — the archived manifest reads `re_review_on_loopback:
  true`. `/review` was posted at 08:11Z and 14:48Z; the 14:48Z bot run succeeded; final round
  "required-bot participation complete". No override.
- CI/merge: merge queue, d091527; 0 new Sonar issues.
- Collisions: none; the doc PR rebased over #28 and kept both `DeveloperGuide.adoc` rows.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#27`; `landing` `landings/PLAN-07.md`
- [x] 19 lessons: 1 promoted here (2026-10-11-16-001, `pm-workflow`), 18 routed to plan-marshall (`pm-mcp-core-m2-m4-065.md` to `-082.md`)
- [x] stale re-review Open Defect closed (PLAN-07 was the last plan composed before #24)
- [x] epic.md narrative reconciled; resume anchor; `queue-view.md` regenerated

## Follow-Ups

- Unblocked: PLAN-10 `static-verifier-and-fuzzer` and PLAN-16 `diagram-generator`, both `pm-workflow`, disjoint from each other.
- Specs not yet started can contain contradictions between specification documents that refine's
  premise check does not catch. A refine that reads every specification section a spec names
  against each other is the plan-marshall side (routed); on this side, the unit `adjudicate.*`
  rename: no staged spec names the old unit (checked).
