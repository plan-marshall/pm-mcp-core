# PLAN-V2-104: A message on every assertion of the pm-runtime start, config, job, launch, lsp, lock and audit tests

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-104-assertion-messages-runtime-a.md` and is queued as one row file, `queue/PLAN-V2-104.json`,
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

Rules that apply to this plan: R02, R06, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12 to R16: test-only.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none; ADR directory: none; layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

The `junit-core` skill wants a message of 20 to 60 characters on every assertion; about 91 % of the roughly 13,000 assertions in the repository have none. The operator decided on 2026-10-30 (decision 10) that every assertion everywhere gets one. This plan covers the test classes of `pm-runtime` in `start`, `config`, `web`, `workspace`, `test`, `log`, `ingest`, `corpus`, `job`, `launch`, `lsp`, `lock` and `audit` (about 2200 assertions without a message, from the audit). It runs last in the wave: the other plans rewrite the same test files.

## Deliverables

1. **A message on every assertion of the scope (class by class).** For each top-level `assertTrue`, `assertFalse`, `assertEquals`, `assertNotEquals`, `assertNull`, `assertNotNull`, `assertSame`, `assertThrows`, `assertInstanceOf`, `assertDoesNotThrow` and `assertArrayEquals` without a message, add a message of 20 to 60 characters that says what is checked in the language of the domain (not 'should be true'). The message goes in the position the JUnit signature gives it; inside `assertAll` each lambda's assertion carries its own. Assertions of `LogAsserts` already name the record and are left.
2. **A guard against regression.** A source-scanning test in `pm-core` (the plan of the family that lands first creates it; the others extend it) fails when a top-level assertion of the scanned test packages has no message; the packages of this plan are added to its list. The scan is by source text, the same way as the `@NullMarked` guard of PLAN-V2-84.

**Done when:** the scanner test passes for the packages of the scope and fails on a fixture assertion without a message; the pull request body gives the number of messages added per module; the test method count is the same. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `junit-core` (SKILL.md and standards: assertion messages); R20; decision 10 in `epic.md`; the audit `skills-audit-2026-10-30.md` § 2.7.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Test code only;** no assertion is changed except by adding the message argument (and reordering arguments where JUnit needs it).
- **Messages say what is checked, not that it failed:** 'the refusal names the store file', not 'assertion failed'.
- **Mechanical, so conflict-prone:** the plan starts from the head of `main` after its dependencies and merges the base before its gate.

## Claim Labels

- OBSERVED: assertions without a trailing message — core 4352, workflow 2587, runtime 4377, providers 477 (parsed, `skills-audit-2026-10-30.md` § 2.7)
- HYPOTHESIS: the scanner can be a plain regex over `src/test/java` because the assertions are top-level calls; nested lambdas are handled by the same parser as the audit's
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/start/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/config/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/web/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/workspace/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/test/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/log/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/ingest/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/corpus/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/launch/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/lsp/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/lock/` (directory; R19 replaced)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/audit/` (directory; R19 replaced)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (the dependencies and rule R20) and PLAN-V2-89, 90, 93, 99, 106 and PLAN-V2-101 (the scanner test).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-104-assertion-messages-runtime-a.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
