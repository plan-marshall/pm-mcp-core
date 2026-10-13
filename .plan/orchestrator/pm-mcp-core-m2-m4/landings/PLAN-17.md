# Landing Analysis: PLAN-17 — Project-owned units and compiled invariants

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #56 (https://github.com/plan-marshall/pm-mcp-core/pull/56), merge commit df4b318

> Landing record for one shipped plan. Lives at `landings/PLAN-17.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-17-project-units-and-invariants-012.md` (landing-facts complete,
8/8 deliverables), corroborated on 2026-10-13 against:
- the PR (CI on head 5f8ea6a, `/review` history);
- the file list of df4b318;
- the archived plan's decision and build logs (`.plan/local/archived-plans/2026-10-13-plan-17-project-units-and-invariants`);
- the diff of plan-marshall-documentation#57.

Harness: Claude Code (default).

## Related pull requests (operator rule 2026-10-13)

| PR | Repository | State |
|----|------------|-------|
| #56 | pm-mcp-core | MERGED 2026-10-13T19:53:14Z (df4b318) |
| #57 | plan-marshall-documentation | **OPEN** — requirements, specification sections (AST node model, finalize descriptor, remediation units, dynamic re-planning, decision quorum, module structure), LogMessages 70/170/270, roadmap, DSL watch |

**Not complete until doc #57 merges.** Its content was checked against the merged code: the three
log rows match identifier, class and template of `PmMcpLogMessages` at df4b318, and rules 6
(`work_performed`), 7 and 8 are described as named hooks, as merged. Its last commit (12:46Z)
predates the code's last fix commits, but none of those changed a documented fact.

## Deliverable Fidelity vs Spec

106 files, +10,222 / −85; 8 of 8 deliverables by the plan's count.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Specification first | shipped (open PR) | doc #57, see above |
| Project-unit loader, declarations against compiled bounds | shipped | `ProjectUnitLoader`, `DeclarationBounds`, `UnitLoadResult`, `UnitFinding`; `config_invalid` with file and line |
| Plan-unit merge and `derived_from` | shipped | `PlanUnitMerger`; parser records `derived_from` and refuses it on a default unit |
| Default units from a classpath root | shipped | `DefaultUnitLoader`; the assembly supplies the root |
| Compiled invariants per phase | shipped with named hooks | `InvariantId` (30 ids), one checker per phase/unit kind, `CompiledInvariants`; INV-GEN-2, INV-GEN-4 (runtime part), INV-REM-6 tree-checked with named hooks (operator-accepted) |
| `FinalizePipelineValidator` over the project roster | shipped with named hooks | rules 1–5 and the input-scope part of 6 checked; 6 (`work_performed`), 7, 8 named hooks (operator-accepted) |
| Developer docs and ADRs | shipped | `workflow-unit.adoc`, `workflow-verify.adoc`, `workflow-finalize.adoc`; ADRs workflow-unit 001–004, workflow-finalize 001–003, workflow-verify 017–020 (renumbered after #54 and #53) |
| Index row `done` | shipped | `doc/plans/README.adoc` |

Owed elsewhere (operator-accepted): `DefaultUnitInvariantTest` and the wiring of the loader into plan
start/resume belong to the assembly `pm-mcp-server` in plan-marshall-mcp.

## Surface

Beyond the declared surface:
- `PmMcpLogMessages` (WS-03 block: 70, 170, 270);
- `workflow.ast` / `workflow.dsl` / `engine/EngineStep` — the operator's execute-time decision to give
  declare/decide lines a source location;
- about 25 test files with imports re-sorted by the quality gate;
- `pm-workflow/pom.xml`, `pm-workflow/README.adoc`, `.pmwf` fixtures.

No concurrent plan was running, so nothing collided.

## Dependencies

`pm-workflow/pom.xml` gains `cui-test-juli-logger` and `cui-test-generator`, both test scope, with
versions from the parent. The operator consented to both (decision.log 09:43:37Z and 10:36:07Z).

## Metrics and Anomalies

- Tokens: 7,802,605; wall time 60,624 s. Finalize spent 3.1M (38%) across 6 automatic-review,
  9 sonar-roundtrip and 7 pre-push-gate firings.
- Gates: `verify -Ppre-commit` started 19:19:57Z, after the last commit 5f8ea6a (19:19:49Z), BUILD
  SUCCESS; CI green, including the integration tests and SonarCloud (0 new-code issues).
- Format versions: none raised; DSL `COMPILED_FORMAT_VERSION` stays 1.
- Log identifiers 70/170/270 sit in the WS-03 block. They will be renumbered by the log-holder session
  plan.

## Routing and Merge Behavior

- `/review` was posted 6 times, the last at 19:36:20Z, after the final commit.
- cuioss-review-bot declined one re-review (head 0f23147); the operator proceeded, and later heads
  were reviewed by both required bots.
- Sourcery (optional) was out of budget for the whole run; its refusal was not classified as one
  (lesson `-254`).
- Merge queue, df4b318.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#56`; `landing` `landings/PLAN-17.md`
- [x] 11 lessons routed to plan-marshall (`pm-mcp-core-m2-m4-252.md` to `-262.md`); `-252` is a
      second occurrence of `-242`
- [x] doc #57 recorded as an open related PR in `epic.md`

## Follow-Ups

- Merge doc #57 **before** the log-holder session plan's documentation PR: both edit
  `doc/LogMessages.adoc`, and the split renumbers rows 70/170/270.
- plan-marshall-mcp owes `DefaultUnitInvariantTest` and the loader's wiring into plan start/resume.
