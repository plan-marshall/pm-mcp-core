# WS-11: Skills conformance

epic: pm-mcp-core-m2-m4-v2

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-11-skills-conformance.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Bring the existing code of all modules to the coding skills of the marketplace (rule R20): dependencies and the recorded exceptions, JSpecify null safety, generated test data and contract tests, annotation-based HTTP tests, `@UtilityClass` and specific catches. The family runs first (operator decision 2026-10-30); the audit is `skills-audit-2026-10-30.md`.

## Scope

- In scope: the POMs, CLAUDE.md, repository ADRs, every package-info, the tests of all modules, the static-only classes and the generic catches of `pm-runtime`
- Out of scope: the surfaces of the other workstreams' feature plans; a plan belongs to exactly one workstream.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-V2-83-skills-conformance-foundation | staged | Dependencies, CLAUDE.md and the ADR of accepted exceptions |
| PLAN-V2-84-null-safety-nullmarked | staged | @NullMarked everywhere, Optional returns |
| PLAN-V2-85-core-store-tests-a | staged | Generated test data, core store tests A to L |
| PLAN-V2-86-core-store-tests-b | staged | Generated test data, core store tests M to W |
| PLAN-V2-87-core-tests-rest | staged | Generated test data, the rest of pm-core |
| PLAN-V2-88-workflow-tests | staged | Generated test data, pm-workflow |
| PLAN-V2-89-runtime-tests-start-and-config | staged | Generated test data, runtime start/config/small packages |
| PLAN-V2-90-runtime-tests-jobs | staged | Generated test data, runtime job/launch/lsp/lock/audit |
| PLAN-V2-91-runtime-tests-workers-and-endpoints | staged | Generated test data, runtime worker/endpoint/credentials/enrolment/question |
| PLAN-V2-92-providers-tests | staged | Generated test data, provider modules |
| PLAN-V2-93-http-tests-annotations | staged | The twelve HTTP test classes to the annotations; first sonar HTTP test |
| PLAN-V2-94-utility-classes-and-generic-catches | staged | @UtilityClass and the generic catches of pm-runtime |
| PLAN-V2-97-optional-conversion-core | staged | Optional to @Nullable, pm-core |
| PLAN-V2-98-optional-conversion-workflow | staged | Optional to @Nullable, pm-workflow |
| PLAN-V2-99-optional-conversion-runtime-and-providers | staged | Optional to @Nullable, runtime and providers |
| PLAN-V2-100-builders-for-large-records | staged | @Builder for records of seven or more components |
| PLAN-V2-101-assertion-messages-core-store | staged | Assertion messages, core store |
| PLAN-V2-102-assertion-messages-core-rest | staged | Assertion messages, core rest |
| PLAN-V2-103-assertion-messages-workflow-and-providers | staged | Assertion messages, workflow and providers |
| PLAN-V2-104-assertion-messages-runtime-a | staged | Assertion messages, runtime a |
| PLAN-V2-105-assertion-messages-runtime-b | staged | Assertion messages, runtime b |
| PLAN-V2-106-easymock-for-fakes | staged | EasyMock doubles for the hand-written fakes |

## Sequencing and Surface Notes

- The family is ordered by negative `seq` values (PLAN-V2-83 first); `next` fills the free slots in that order and the disjointness gate decides each emit. The wave plans declare their test packages by directory (R19 replaced), so no other plan that edits a test of those packages runs beside them.
- The wave also holds PLAN-V2-95 and PLAN-V2-96 of WS-05 (lock manager, grading adapter); see `epic.md` wave 0 for the order.
- PLAN-V2-83 and PLAN-V2-84 run alone; PLAN-V2-85 to PLAN-V2-88 run together; PLAN-V2-89 to PLAN-V2-91 and PLAN-V2-94 next; PLAN-V2-92 and PLAN-V2-93 last.
