# Landing Analysis: PLAN-V2-98 — Optional to @Nullable in pm-workflow

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #309 (merge `4edb20c2`; every package except `model`; 100 files, +2127/-1468) and #310 (merge `07af8fd3`; the `model` package; 10 files, +177/-55). Split because the review bot refused a single 110-file pull request on its 100-file cap.

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-98.md`. A pasted claim is a lead, never a fact.

Source: the plan's hand-written landing message `plan-v2-98-optional-conversion-workflow-001.md` (17:05Z; `landing-check` `complete: false`, only `effort` missing; 53,609,331 tokens, 115,925 s; functional review 6 met + 1 superseded, 0 blocking, 9 advisories), written after the archive because the plan was started with the old path (`not_orchestrator_pointer`). Corroborated by the orchestrator: Sonar `main` (analysis of `262059a5`) gate OK, 0 BUGs; push and merge_group runs of `262059a5` green; `git log 07af8fd3..origin/main -- pm-workflow` is empty. Read at `07af8fd3` by a read-only sub-agent (nothing built); points marked **checked** were re-read by the orchestrator.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 fields become `@Nullable T`, accessors return `Optional.ofNullable` | met | no `Optional` field or record component remains; `CompiledInvariants.lane()`, `CompositionRules.Checker.refusal()` |
| D2 parameters and components, tests first, codecs unchanged | met in form, deviations | two `Optional<T>` parameters kept (`WorkflowValidator.checkCompletionAdmission`, `FinalizeInvariants.describe`) and collection-wrapped `Optional` in `WorkflowValidator` and `CallGraphExpansion`; goldens pin stored formats |
| D3 callers, ports, owed list | met | PR body "Owed outside this repository"; the assembly does not use `pm-workflow` yet |
| `model` package (outline D4, #310) | met, outside the pipeline | read: correct (C7) |
| Done-when grep | not satisfiable as written | S1 |

## Three-Layer Analysis (standing rule R21, r6)

### Layer 1 — Specified
- **S1 (medium).** The Done-when regex `private (final )?Optional<` matches every private method returning `Optional`: 20 lines at `07af8fd3` that the spec keeps (checked: `git grep` count 20), and it cannot see `Optional` inside a collection. "Only the sites in the PR body" is met by reading only.
- **S2 (medium).** Silent on `Map<K,Optional<V>>`, `Set<Optional<V>>`, `Map.Entry<K,Optional<V>>`; the outline's "no new `orElse(null)` round trip" conflicts with converting them.
- **S3/S4 (medium/low).** `OptionalInt`/`OptionalLong`, `ParamType` bounds and the pm-core boundary unwraps are decided only in the outline (operator decisions 2026-11-06). R06 said "ADR directory: none", yet the plan decided: paired bounds stay as two components, `EntityKind`'s convenience constructor removed, three pm-core boundary unwraps, collection-wrapped `Optional` kept (CLAUDE.md: one ADR per design decision).
- **S5/S6 (low).** The 100-file cap was foreseeable (17 package directories plus the whole test tree) and the spec has no split rule; the Done-when asks for coverage and integration tests of modules the plan does not touch.

### Layer 2 — Implemented
- **I1 (checked in part).** Two real `Optional<T>` parameters remain, both `private static`, kept deliberately: `WorkflowValidator.java:1558` (`checkCompletionAdmission(…, Optional<OutcomeClass> outcomeClassOpt, …)`, re-read) and `FinalizeInvariants` `describe(Optional<CallValue>)`; `Map<OutcomeTransition,Optional<OutcomeClass>>` in nine private methods; `CallGraphExpansion:183,196` (`Map.entry` refuses null); `OptionalInt column` in `HeaderRefusal` (operator decision). The "forces `orElse(null)`" justification is a preference, not a necessity.
- **I2/I3/I4/I5 met (checked).** Accessors, ten constructor invariants (polarity correct), every `@Nullable` is `org.jspecify.annotations.Nullable`, every `package-info.java` `@NullMarked`, `ParamType` bounds callers defaulted.
- **I6 (partly checked).** Absent-case tests exist for most signatures; no direct test of `Waiver` with a null value or `CompositionRequest` with a null `previous` (maybe covered indirectly). "Tests first" is the plan's claim.
- **I7 (medium).** Six accepted ADRs still prescribe `Optional` arguments (`workflow-ast/002:25`, `workflow-diagram/001,003,007`, `workflow-plan/014:32`, `workflow-verify/029:45`); the repository's mechanism (Status "Superseded by", or an amendment note in `doc/adr/README.adoc`, as ADR 012) was not used; the PR body admits it. Developer-guide sentences were added for eight packages.

### Layer 3 — Correct
- **No behavioural defect found.** C1 (checked): parser order, `InstanceHeaderReader.isNullValue`, `WorkflowPrinter.optionalField`, `HeaderJson` match the old `Optional` behaviour. C2 (low): `HeaderJson:480-483` now throws on an unknown wire name where `flatMap` produced empty; unreachable behind `HeaderSchemaCheck`. C3 (low): `ParamType.toString` in two violation messages reads `min=null` instead of `Optional.empty`; no test pins it. C4 (low): `InstancePruner` throws NPE instead of `NoSuchElementException` on an unreachable invariant.
- **C5 (checked).** Ten conversions read, no inverted polarity. **C6.** Golden literals pin the stored formats (`CompositionRuleEvaluatorTest:566-587`, `PlanInstanceStoreTest:400-420`, print/parse round trips), written in the same PR.
- **C7 (checked, re-read).** The late Sonar refactor of `RoleGrader.gradeForm` (`RoleGrader.java:179-199`) is behaviour-preserving: the former two-branch reuse and top-up became `reused(previous, …)` (empty without a previous record) plus a top-up from `reused.size() + 1`, which is `1..runs` for an empty list; both sides are tested (`RoleGraderTest:349-413`). The missing CodeRabbit review carries no behavioural risk.
- **C8 (low).** Three `orElse(null)` boundary calls in main code: `ModelCaller:73`, `RoleComposition:50` (not in the outline's list), `RoleGrader:131-133`. **C9.** Unrelated but equivalent refactors (`AsciiDocTableRenderer.PhaseGroup`, Javadoc reflow). **C10.** No NullAway or checker in the build: `@Nullable` is documentation plus Sonar S2637/S2583.
- **C11 (assembly).** Breaking public signatures without shim (diagram generator renderers, `EntityAddress`, `WaitSelection.select`, `FinalizeStepDescriptor`, the removed `EntityKind` constructor, `CompiledInvariants.check`, `ModelJob`, `ModelQueue`, `ModelCallResult.Closed`, `CompositionRefusal.roleUndefined`). The sub-agent's `plan-marshall-mcp` checkout is stale; its `origin/main` has no `pm-workflow` dependency, so there is no live break.
- **C12 (medium-low).** Coverage ran on the combined tree (`690c20a8`) before the split; no record for the merged heads; the links check is not evidenced.

## Verdict

Everything the plan set out to convert is converted, correctly as far as read; the deviations (two kept parameters, collection-wrapped `Optional`) are decided and disclosed. Not done: the stale ADRs and the ADR for the kept-shape decisions (I7, S4). The Done-when grep is unusable (S1). Main is green. Assembly follow-up: plan the `plan-marshall-mcp` wiring against the new signatures; no dependency exists today.

## Ownership

- PLAN-V2-94, 99, 100 and 108 (the other conversion plans) read the S1 lesson (the regex) and the kept-shape rule. PLAN-V2-97 (`Optional` conversion in `pm-core`) decides the same collection-wrapped question; add it as a bullet there.
- No owner: I7 (six ADRs, the ADR for the decisions), S1 (the Done-when regex), the doc advisories (`workflow-engine.adoc:460-462`, `workflow-plan.adoc:340`, `ModelWaitTest` `@DisplayName`), the two kept parameters. A fix plan only on the operator's word (Wa78).
