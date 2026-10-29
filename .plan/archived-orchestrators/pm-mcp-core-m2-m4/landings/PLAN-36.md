# Landing Analysis: PLAN-36 — Code analysis base

epic: pm-mcp-core-m2-m4
workstream: WS-07
pr: #31 (https://github.com/plan-marshall/pm-mcp-core/pull/31), merge commit d1608b7

> Landing record for one shipped plan. Lives at `landings/PLAN-36.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `code-analysis-base-016.md` (landing-facts complete, no missing key),
corroborated on 2026-10-11 against the PR state (MERGED 19:55Z), the file list of d1608b7, the
index on `origin/main`, the PR body and the plan's archived request, outline and decision log.

## Deliverable Fidelity vs Spec

52 files, +5,600. The plan counts 10 deliverables; 8 to 10 are documentation.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Provider-neutral analysis record, findings types, provider interface and contract in `pm-core` | shipped-modified | `core/analysis/`, `core/findings/` main and test; the findings registry key settled as `FindingSourceKey(kind, provider)`. Breaking: `FindingSource` and `FindingResponder` lost their type parameters (users in plan-marshall-mcp unchecked) |
| Guards keeping provider names, HTTP and file writes out of the packages | shipped-as-specified | `core/guard/ProviderNeutralityArchTest`, `AnalysisBoundaryArchTest` with control fixtures that must fail |
| Own package file and index row | shipped-as-specified | `doc/plans/code-analysis-base.adoc`; row `done` on `origin/main` |
| Documentation | in flight | `plan-marshall-documentation` #31 open, awaits the operator's merge (PM-WATCH-MOD-8 settled in code, still on documentation main) |

Surface: beyond the declared `core/analysis/`, `core/findings/`, `src/guard-controls/`: the guards and
fixtures landed in `core/guard/` (test) instead of `src/guard-controls/`, plus
`SpecificationDocument` (test) and `pm-core/pom.xml`. The POM adds two **test-scope dependencies with
the operator's consent** (request.md: "the operator's answers are the consent"):
`com.networknt:json-schema-validator` 1.5.8 (not parent-managed, pinned in `pm-core/pom.xml`) and
`com.tngtech.archunit:archunit-junit5` (parent-managed), and passes `pm.documentation.root` to
surefire: the schema parity test reads the sibling checkout of `plan-marshall-documentation` and
aborts by assumption when it is absent.

## Metrics and Anomalies

- Tokens: 5,155,669 recorded; wall time 18,045 s (about 5 hours) — the fastest product plan so far.
- Two review/Sonar loop-backs: 3 CodeRabbit findings, 26 Sonar issues fixed.

## Routing and Merge Behavior

- Review: manifest `re_review_on_loopback: true`; `/review` posted at 18:39Z and 19:16Z. No override.
- CI/merge: merge queue, d1608b7; 0 new Sonar issues.
- Collisions: none.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#31`; `landing` `landings/PLAN-36.md`
- [x] 15 lessons: 1 promoted here (2026-10-11-20-001), 14 routed to plan-marshall
- [x] Watch: schema parity test skipped where the documentation checkout is absent (CI)

## Follow-Ups

- `plan-marshall-documentation` #31 to merge (operator); the separate doc worktree
  `/Users/oliver/git/plan-marshall-documentation-code-analysis-base` can go after that.
- WS-07 closes with this plan.
