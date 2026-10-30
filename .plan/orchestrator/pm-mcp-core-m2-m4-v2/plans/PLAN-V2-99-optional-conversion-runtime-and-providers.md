# PLAN-V2-99: Convert the Optional fields, parameters and record components of pm-runtime and the providers to @Nullable

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-99-optional-conversion-runtime-and-providers.md` and is queued as one row file, `queue/PLAN-V2-99.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
>
> Origin: the skills conformance audit of 2026-10-30 (`skills-audit-2026-10-30.md` in the epic directory), taken at `origin/main` `6847c748`, and the operator's decisions of the same day (Decisions in `epic.md`). The plan family is PLAN-V2-83 to PLAN-V2-106 (workstream WS-11, with PLAN-V2-95 and PLAN-V2-96 in WS-05); it runs first, except PLAN-V2-96, which follows PLAN-V2-31.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R08, R11, R13, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12, R14 to R16: no specification, message or protocol change.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the guides of packages whose public signatures change (additive sentence); ADR directory: none; layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

The null-safety skill forbids `Optional` as a field, a parameter or a record component; the audit counted 12 + 17 fields and 54 + 15 parameters and record components in the runtime and the providers. The operator decided on 2026-10-30 (decision 7) to convert all of them, reversing the earlier choice to keep them. After PLAN-V2-84 every package is `@NullMarked`, so a nullable state is written `@Nullable` and the compiler-visible contract replaces the wrapper. The providers' `Optional` fields (17) are in the contract modules `pm-provider-git` and `pm-provider-http`; their signatures are consumed by `plan-marshall-mcp`.

## Deliverables

1. **Fields.** Every field of type `Optional<T>` becomes a field of type `T` annotated `@Nullable` (JSpecify), or the null state is removed by restructuring (an empty value object, a sealed state). Accessors that returned the field return `Optional<T>` built at the call (`Optional.ofNullable`): the absence stays visible to callers as a return value.
2. **Parameters and record components (test first).** Every parameter and record component of type `Optional<T>` becomes `@Nullable T`. A public method with an `Optional` parameter gets the `@Nullable` parameter in place (pre-1.0: no overload, no shim). Constructors and canonical record constructors accept null for exactly the components annotated `@Nullable`; a record accessor of such a component is the generated one. Tests first: for each changed signature the absent case is asserted (`null` in, same behaviour as `Optional.empty()` before), and the codecs that read or write the component keep the stored format byte-identical (R13).
3. **Callers, ports and the owed list.** Callers adapt; a port (an interface in `pm-core`) whose signature changes is listed in the pull request body under **Owed outside this repository** with the consumer in `plan-marshall-mcp` or `pm-mcp-clients` where one exists (`git grep` of their `origin/main`).

**Done when:** `git grep -n -E 'Optional<[^>]*> +[a-z][A-Za-z0-9]*[,)]|private (final )?Optional<|record [A-Za-z]+\([^)]*Optional<' -- 'pm-runtime/src/main/java/*.java'` finds only the sites listed in the pull request body with their reason (a generic functional type like `Function<X, Optional<Y>>` is not a parameter of type `Optional`); every changed signature has a test of the absent case; the stored formats are byte-identical. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `java-null-safety` (SKILL.md and standards), `java-core`; R20; the audit `skills-audit-2026-10-30.md` § 2.8; decision 7 in `epic.md`.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **`Optional` stays as a return type** (absence); this plan converts fields, parameters and record components only.
- **No format change** (R13): the codecs map `null` to the absent member exactly as `Optional.empty()` was mapped.
- **R19 is replaced:** the Expected Surface names packages by directory; the plan runs in the conformance wave, after the test migration of its module.

## Claim Labels

- OBSERVED: `Optional` fields — core 10, workflow 16, runtime 12, providers 17; parameters and record components (heuristic) — core 74, workflow 81, runtime 54, providers 15 (`skills-audit-2026-10-30.md` § 2.8); examples `ConsentGate.java` lines 93, 137, 165 and `JobEnvironmentComposer.java` line 152
- HYPOTHESIS: some hits are generic functional types (`Function<..., Optional<...>>`) that are not parameters of type `Optional`; the outline separates them
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/start/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/config/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/web/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/workspace/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/test/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/log/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/ingest/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/corpus/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/lsp/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/lock/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/audit/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/dbus/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/enrolment/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/question/` (directory; R19 replaced)
- Directory entries: `pm-providers/*/src/main/java/`, `pm-runtime/src/test/java/de/planmarshall/runtime/` and `pm-providers/*/src/test/java/` (the tests of the changed signatures)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (the dependencies and rule R20) and PLAN-V2-84 and the test migrations of the runtime and the providers, PLAN-V2-89, 90, 91, 92 and 93.
- PLAN-V2-100 depends on this plan.
- Overlaps by directory with every plan that edits production code of `pm-runtime`; it runs in the conformance wave.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-99-optional-conversion-runtime-and-providers.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
