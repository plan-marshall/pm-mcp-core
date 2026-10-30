# PLAN-V2-00: Give the records of seven or more components a builder

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-00-builders-for-large-records.md` and is queued as one row file, `queue/PLAN-V2-00.json`,
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

Rules that apply to this plan: R02, R06, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12 to R16: no behaviour change.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none; ADR directory: none; layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

The `java-lombok` skill says four or more constructor parameters get a `@Builder`. 251 records have four or more components and 16 carry a builder. The operator decided on 2026-10-30 (decision 8) on a higher threshold: records of **seven or more components** get `@Builder` (about 85: core 49, workflow 16, runtime 16, providers 4); smaller records keep their canonical constructor. This plan runs after the `Optional` conversion, which changes the components of many of these records.

## Deliverables

1. **pm-core.** Find the records of seven or more components (`git grep -n -E 'record [A-Z][A-Za-z0-9]*\(' -- 'pm-core/src/main/java/*.java'` and count components; the audit counted 49 in core, among them `Finding` with 38 components). Add `@Builder` (Lombok) to each and move the call sites that name more than six arguments positionally to the builder; keep the canonical constructor for the codecs. Test first: one test per record builds it with the builder and with the constructor and compares, and the builder refuses a missing required component with the same refusal as the constructor.
2. **pm-workflow.** The same for the 16 records of seven or more components (for example `FinalizeStepDescriptor` with 21 components).
3. **pm-runtime and the providers.** The same for the 16 records of the runtime (for example `SupervisorEnvironment` with 22 components) and the 4 of the providers.

**Done when:** every record of seven or more components in `src/main` carries `@Builder` or is listed in the pull request body with its reason; the per-record test exists; no call site passes more than six positional arguments to a record constructor outside the codecs. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `java-lombok` (SKILL.md and standards: the `@Builder` rule); R20; decision 8 in `epic.md`; the audit `skills-audit-2026-10-30.md` § 2.10.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Threshold seven, not four** (operator, 2026-10-30): a record of four to six components is not changed.
- **Records stay records:** `@Builder` on a record, never `@Value` or `@Data`.
- **Optional components are gone** by the time this plan runs (PLAN-V2-97 to 99); a record that still has one is a defect of those plans.

## Claim Labels

- OBSERVED: records with four or more components — core 123, workflow 64, runtime 49, providers 15; with seven or more — 49 / 16 / 16 / 4; `@Builder` on 16 records of core and 1 of runtime (`skills-audit-2026-10-30.md` § 2.10)
- OBSERVED: the largest: `Finding` 38 components, `SupervisorEnvironment` 22, `FinalizeStepDescriptor` 21
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: the records of seven or more components found by the command of Deliverable 1 (about 85 files below `pm-core/src/main/java/`, `pm-workflow/src/main/java/`, `pm-runtime/src/main/java/`, `pm-providers/*/src/main/java/`) and their call sites
- Directory entries (R19 replaced): `pm-core/src/main/java/de/planmarshall/core/`, `pm-workflow/src/main/java/de/planmarshall/workflow/`, `pm-runtime/src/main/java/de/planmarshall/runtime/`

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (the dependencies and rule R20) and PLAN-V2-97, PLAN-V2-98 and PLAN-V2-99 (they change the components of these records).
- Overlaps by directory with every plan that edits production code; it runs in the conformance wave.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-00-builders-for-large-records.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
