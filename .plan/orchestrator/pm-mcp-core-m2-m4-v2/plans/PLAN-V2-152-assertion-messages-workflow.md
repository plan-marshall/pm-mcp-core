# PLAN-V2-152: A message on every assertion of the pm-workflow tests, and the guard that ends the family (the remainder of PLAN-V2-103)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: the landing of PLAN-V2-103 (`landings/PLAN-V2-103.md`, pull requests #450 `cd9da049` and #452 `e8e7c125`): the lane stopped on the operator's word after the extraction and the provider guards; the `pm-workflow` half of Deliverables 1 and 2 of PLAN-V2-103 was not done
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-152-assertion-messages-workflow.md` and is queued as one row file, `queue/PLAN-V2-152.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
>
> Origin: written by the orchestrator on 2026-11-16 at the analysis of the landing of PLAN-V2-103 (the plan family PLAN-V2-83 to PLAN-V2-108, workstream WS-11). The operator decided on 2026-11-15 that the scan logic is published once in `pm-core:tests` with a thin test per module; that part has shipped.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision, and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12 to R16: test-only.)

- R06 — developer documentation: none; ADR directory: none; layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

The `junit-core` skill wants a message of 20 to 60 characters on every assertion, and the operator decided on 2026-10-30 (decision 10) that every assertion everywhere gets one. PLAN-V2-101, 102 and 103 did `pm-core`, the provider modules and the shared scanner; PLAN-V2-104 was folded into PLAN-V2-105 (the runtime). What is left of `pm-workflow` is this plan: about 1 690 assertion sites in about 76 test files, and the thin guard of `pm-workflow`, which can only land green when the tree is clean and therefore ends the work.

## Deliverables

1. **A message on every assertion of the `pm-workflow` tests (class by class).** For each top-level `assertTrue`, `assertFalse`, `assertEquals`, `assertNotEquals`, `assertNull`, `assertNotNull`, `assertSame`, `assertThrows`, `assertInstanceOf`, `assertDoesNotThrow` and `assertArrayEquals` without a message, and each literal message outside 20 to 60 characters, give a message of 20 to 60 characters that says what is checked in the language of the domain (not "should be true"). The message goes in the position the JUnit signature gives it; inside `assertAll` each lambda's assertion carries its own; assertions of `LogAsserts` already name the record and are left. Where an assertion uses a diagnostic as its message (`result.detail()`), keep it. **Start from the pushed branch `feature/v2-103-assertion-messages-workflow-wip` (`f53df72a`, nine converted files: `ast`, `classification`, `consult`, `diagram` and part of `dsl`) where it still merges, or re-derive the list from the scanner; re-measure at the head of `main` first.**
2. **The thin guard of `pm-workflow`.** One test, `pm-workflow/src/test/java/de/planmarshall/workflow/testing/AssertionMessageScanTest.java`, the same shape as the six provider guards of #452 (`BASE_PACKAGE = "de/planmarshall/workflow"`, `AssertionMessages.testSourceRoot(...)`, `AssertionMessages.scan(root, BASE_PACKAGE, Map.of())`), and `pm-workflow/pom.xml` declares `pm-core` `test-jar` in test scope if it does not already (no version literal; the matrix of ADR 012 allows it; it is not a new dependency decision). **It lands in the last pull request**: the guard scans the whole `de/planmarshall/workflow` tree and is red until the tree is clean. Do not copy scan logic.

**Done when:** the thin guard passes for the whole `pm-workflow` test tree and fails on a fixture assertion without a message (the controls live in `AssertionMessagesTest` of `pm-core`; name which control proves the failing shape); no assertion is removed or weakened (the number of test methods per class is the same); each pull request body gives the number of messages added per package; every pull request is green on its own and holds at most 100 files. **Sonar:** the SonarCloud search of each pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed; files the gate rewrites outside this plan's scope go in their own cleanup commit), then `verify` of `pm-workflow` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `junit-core` (assertion messages); R20; decision 10 in `epic.md`; `landings/PLAN-V2-103.md`; ADR `doc/adr/012-generator-artifacts.adoc` (the thin-guard rule).

## Standing Constraints

- **Test code only;** no assertion is changed except by adding or lengthening the message argument (and reordering arguments where JUnit needs it).
- **Messages say what is checked, not that it failed:** "the refusal names the store file", not "assertion failed".
- **Mechanical, so conflict-prone:** start from the head of `main`, merge the base before the gate.
- **No more than 100 files per pull request** (the watch of the epic ledger; CodeRabbit refuses a larger one with "Too many files"): at least two pull requests; cut by package before the first is opened, for instance `engine`, `plan`, `task`, `verify` (the large ones) apart from the rest. The guard goes in the last.
- **The scanner does not measure dynamic messages** (a constant, a concatenation, a supplier lambda, a method reference; Javadoc of `AssertionMessages`); do not rely on the guard for them, and read the dynamic ones by eye while converting a file.
- **Known latent shapes of the scanner** (`landings/PLAN-V2-103.md` L3-1 to L3-3; the hardening is PLAN-V2-114 item 29, not this plan): an argument list with a primitive-array type argument, a comparison pair of two upper-case operands, and a delta declared in another class can pass the guard without a message. Do not write such an assertion without a message.
- **Do not touch** `AssertionMessages` or the `pm-core` guard.

## Claim Labels

- OBSERVED: the `pm-workflow` test tree held 2 108 messageless assertions at `ab5c15fb` (the lane's measurement with the shipped scanner); the branch `feature/v2-103-assertion-messages-workflow-wip` converts 349 of 2 039 reported sites in 9 of about 85 files; about 1 690 sites in about 76 files remain (`landings/PLAN-V2-103.md`, residue 1; counts good to a few)
- OBSERVED: the thin guard of every provider module exists and scans its whole tree (#452); the shared scanner is published in `pm-core:tests` (`pm-core/pom.xml:147`, include `de/planmarshall/core/testing/assertions/**`)
- OBSERVED: `pm-workflow/pom.xml:30-34` already declares the `pm-core` `test-jar` in test scope (read by the orchestrator 2026-11-16), so Deliverable 2 needs no pom edit unless the head differs
- Verify-first clause: before editing, list the open pull requests for one that touches the `pm-workflow` test tree and re-run the scanner at the head of `main`; keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/verify/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/task/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/plan/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/engine/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/model/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/unit/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/finalize/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/consult/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/diagram/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/ast/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/question/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/setup/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/log/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/hypermedia/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/classification/` (directory)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/envelope/` (directory; two sites, a package PLAN-V2-103 did not list)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/testing/AssertionMessageScanTest.java` (the thin guard; new file) and `pm-workflow/pom.xml` (the `pm-core` `test-jar`, only if not yet declared)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-103's extraction and provider guards (merged: #450, #452), PLAN-V2-136 (shipped; it converted the port doubles of `engine`, `scenario`, `model` and `task`).
- Shares the `pm-workflow` test directories with PLAN-V2-125 (core and workflow test conformance), PLAN-V2-141 (test-design rework, `pm-core` only) and PLAN-V2-114 (items touching `pm-workflow`): the gate reads the overlap; the dependency is the oracle, so emit this plan when its surface is disjoint from running work, and merge the base before each gate.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-152-assertion-messages-workflow.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
