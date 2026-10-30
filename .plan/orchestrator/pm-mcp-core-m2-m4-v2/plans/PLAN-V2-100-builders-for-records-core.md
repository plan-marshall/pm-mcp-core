# PLAN-V2-00: Give the records of four or more components in pm-core a builder

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-00-builders-for-records-core.md` and is queued as one row file, `queue/PLAN-V2-00.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
>
> Origin: the skills conformance audit of 2026-10-30 (`skills-audit-2026-10-30.md` in the epic directory), taken at `origin/main` `6847c748`, and the operator's decisions of the same day (Decisions in `epic.md`). The plan family is PLAN-V2-83 to PLAN-V2-108 (workstream WS-11, with PLAN-V2-95 and PLAN-V2-96 in WS-05); it runs first, except PLAN-V2-96, which follows PLAN-V2-31.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12 to R16: no behaviour change.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none; ADR directory: none; layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

The `java-lombok` skill says four or more constructor parameters get a `@Builder`. 251 records have four or more components and 16 carry a builder. The operator decided on 2026-10-30 (decision 8, then corrected to 'use the documented 4') that **every record of four or more components gets `@Builder`**; a smaller record keeps its canonical constructor. This plan covers the records of `pm-core` (123 records with four or more components, 16 of them with a builder already). It runs after the `Optional` conversion, which changes the components of many of these records.

## Deliverables

1. **Find and annotate (test first).** List the records of four or more components in `src/main` of the scope (`git grep -n -E 'record [A-Z][A-Za-z0-9]*\(' -- '<module>/src/main/java/*.java'` and count the components; the multi-line declarations need the parser of the audit's scan). Add `@Builder` (Lombok) to each that has none. Test first: one test per record builds it with the builder and with the constructor and compares them, and the builder refuses a missing required component with the same refusal as the constructor (null checks of the canonical constructor).
2. **Call sites.** Move the call sites that pass four or more arguments positionally outside the codecs and the record's own factory methods to the builder; leave a call whose arguments are all variables of the same names (the builder adds nothing). The pull request body lists, per record, the number of call sites moved.

**Done when:** every record of four or more components in `src/main` of the scope carries `@Builder`; the per-record test exists; no call site outside codecs and factories passes four or more positional arguments to a record constructor; the pull request body gives the counts. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `java-lombok` (SKILL.md and standards: the `@Builder` rule and the table); R20; decision 8 in `epic.md`; the audit `skills-audit-2026-10-30.md` § 2.10.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Threshold four, the documented rule** (operator, 2026-10-30).
- **Records stay records:** `@Builder` on a record, never `@Value` or `@Data`; the canonical constructor stays for the codecs.
- **Optional components are gone** by the time this plan runs (PLAN-V2-97 to 99); a record that still has one is a defect of those plans.
- **R19 is replaced:** the Expected Surface names the production directories.

## Claim Labels

- OBSERVED: records with four or more components — core 123, workflow 64, runtime 49, providers 15 (251); `@Builder` on 16 records of core and 1 of runtime (`skills-audit-2026-10-30.md` § 2.10); the largest `Finding` 38, `SupervisorEnvironment` 22, `FinalizeStepDescriptor` 21 components
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/` (directory; R19 replaced)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 and PLAN-V2-97 (the `Optional` components of `pm-core` records are gone first).
- Overlaps by directory with every plan that edits production code of `pm-core`; it runs in the conformance wave.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-00-builders-for-records-core.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
