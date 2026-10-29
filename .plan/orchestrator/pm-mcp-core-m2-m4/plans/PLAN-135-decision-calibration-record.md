# PLAN-135: The decision calibration record: codec, store and declaration digest in core.role.qualification

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-135-decision-calibration-record.md` and is queued as one row file, `queue/PLAN-135.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R17, R18, R19. Package bookkeeping (R01, R05): this plan is 1 of 2 or more (the other plans are blocked and have no spec yet) plans of the package `decision-quorum-and-calibration` (`doc/plans/decision-quorum-and-calibration.adoc`). It deletes from the package file the bullets of _What_ and the _Done When_ paragraphs it delivers and drops its deliverables from the covered sections of the specification and watch items; the plan that delivers the last bullet removes the package's row from `doc/plans/README.adoc` and deletes the package file. The plans of the package edit different sections of the same documents; whichever merges second merges the base branch (the lane's step 7) before the merge gate. R10 binds: the plan adds a schema-parity test that reads the `DecisionCalibrationRecord` block of `store-schemas/16-task-queue.adoc`; the block stays byte-stable.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-role.adoc` (existing, extended); ADR directory: `pm-core/doc/adr/core-role/` (the next free number of the directory); layout: workstream WS-10.
- R12 — log message holder: none expected (`CoreLogMessages`, prefix `PM_CORE`).

## Objective

Deliver the one slice of the work package `decision-quorum-and-calibration` that does not wait for `decision-engine`: the codec of the decision calibration record `qualification/decisions/<declaration-digest>.json`, whose schema is in the store specification and whose codec the package file says is "owed here, in `core.role`, where the record is owed" — including the token split of each item (`total_tokens` with `tokens_in` and `tokens_out` both measured and summing to the total, or both `unmeasured`; an `unmeasured` total carries no measured split), the store that writes it atomically, and the declaration digest that is the file's name. The declarations, the adjudication unit, the quorum roles and the calibration run need `decision-engine` and the unit machinery and stay in the blocked package.

## Deliverables

1. **The record and its codec** (new, `core.role.qualification`), following `JudgeCheck`/`JudgeCheckCodec`: `format_version` 1, `decision_kind` (the closed set of nine kinds), `declaration_digest`, `declaration` (`roles` 1 to 3 unique, `score` 50 to 100), `roles` (`role`, `form`, `qualification_digest`, `harness`, `model`), `corpus_revision`, `items_graded`, `auto_share`, `false_auto_share`, `constant_baseline` (percentages 0 to 100), `graded_at`, and the items (`item_id`, `votes`, `decided`, `agreed_option`, `false_auto`, `total_tokens`, `tokens_in`, `tokens_out`, `duration_ms`); strict reading that refuses an unknown key, a duplicate role, a score outside 50-100, a `decided` item without an agreed option and an undecided one with it; the token members reuse `TokenTotal` (the split is measured as a pair or `unmeasured`, never a measured `0`).
2. **The declaration digest:** SHA-256 over the decision kind, the listed roles in their declared order with their qualification digests and forms, and the score (Model Work § Quorum Calibration, *Configuration calibrated*); the record is valid only while the digest equals its file name. The canonical byte form of the digest input is not specified: decide it at the outline and record it in an ADR and in the specification (R07: ask the operator if the readings differ in the files they create).
3. **The store:** `qualification/decisions/<declaration-digest>.json` of the project role set, written by atomic write-and-rename under a transaction of the store's lock like `JudgeCheckStore`, read back with a refusal for a record whose digest differs from its name or whose bytes do not parse; a record is never overwritten by another digest. Tests: write/read round trip, a tampered name, a concurrent writer.
4. **Schema parity and documentation:** a test reads the `DecisionCalibrationRecord` schema block of `doc/specification/store-schemas/16-task-queue.adoc` and validates written records against it (the block stays byte-stable, R10); extend `pm-core/doc/developer/core-role.adoc`; ADR for the digest form; delete from the package file the codec sentence and the covered part of the _Done When_; the rest of the package stays, with its row, until its plans land.

**Done when:** A record written by the store validates against the specification's schema block and reads back equal; a record with an unknown key, a score outside 50-100, a duplicate role, a decided item without an agreed option or a split that does not sum to the total is refused with the member named; `unmeasured` total carries `unmeasured` at both members and a measured split is never `0` for an unmeasured one; the file name equals the declaration digest and a mismatch is refused; the developer guide and the ADR are written. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`; here: `pm-core`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Requirements: PM-WORK-6, PM-WF-18
- Specification: `doc/specification/store-schemas/16-task-queue.adoc` § Decision Calibration Record; `doc/specification/model-work/10-decision-quorum.adoc` § Quorum Calibration; ADR `pm-core/doc/adr/core-store/048-token-split-derived-on-read-and-the-coverage-aggregate.adoc`
- Watch: `doc/implementation-watch/model-work.adoc` PM-WATCH-DISP-63 (read; deleted only if the codec covers it)

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- Format versions stay 1 (R13). The shipped calibration of the default declarations (`classpath:/roles/qualification/decisions/…`) is model-facing release content of `plan-marshall-mcp` and not part of this plan.

## Claim Labels

- OBSERVED: the package file `doc/plans/decision-quorum-and-calibration.adoc` (the bullet *The calibration record's tokens*: "its codec is owed here, in `core.role`") and its index row (`open`, depends on `decision-engine`, a staged plan: PLAN-47) at `a8da4104`
- OBSERVED: no decision calibration class exists: `grep -rli 'decision.calibration\|DecisionCalibration' --include=*.java pm-*/src` finds nothing; `pm-core/src/main/java/de/planmarshall/core/role/qualification/` holds `JudgeCheck`, `JudgeCheckCodec`, `JudgeCheckStore`, `QualificationRecord`, `QualificationRecordCodec`, `QualificationRecordStore`, `TokenTotal` among 27 files
- OBSERVED: `TokenTotal.java` line 27 `record TokenTotal(Long tokens, Long tokensIn, Long tokensOut)` with `UNMEASURED_MARK` at line 30 implements the pair-or-unmeasured rule of ADR core-store/048; `JudgeCheckCodec.java` line 174 builds it from the read members; `JudgeCheckStore.java` line 36 `FILE_NAME = "judge-check.json"` writes `qualification/judge-check.json` atomically
- OBSERVED: the schema block is `doc/specification/store-schemas/16-task-queue.adoc` from line 314 (`decision-calibration-record`) with `$defs.roleConfig`; `doc/specification/model-work/10-decision-quorum.adoc` lines 205-210 define the declaration digest, the record path and the shipped calibration
- HYPOTHESIS: the store location is under the project role set like `judge-check.json` (`<set>/qualification/decisions/`) — confirm at `filesystem-layout` and at `JudgeCheckStore.java` line 49 (verify-at-outline)
- Verify-first clause: before editing, list the open pull requests touching `core/role/qualification/` (PLAN-46 declares other `core/role` classes: `MachineConfiguration`, `ModelCatalogue`, `RoleAssignments`), re-read the schema block at the head and check whether `decision-engine` (PLAN-47) has already added a calibration type; a refutation loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/role/qualification/` new classes: `DecisionCalibrationRecord`, `DecisionCalibrationCodec`, `DecisionCalibrationStore`, `DeclarationDigest` (exact names at the outline)
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/role/qualification/` new test classes, including `DecisionCalibrationSchemaParityTest` reading `store-schemas/16`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/TokenTotal.java` — read, reused unchanged
- OBSERVED: `doc/plans/decision-quorum-and-calibration.adoc` — the package file, edited (the row stays: the package is not finished)
- OBSERVED: `doc/specification/store-schemas/16-task-queue.adoc` and `doc/specification/model-work/10-decision-quorum.adoc` — the digest form added; the schema block stays byte-stable
- OBSERVED: `pm-core/doc/developer/core-role.adoc` and `pm-core/doc/adr/core-role/` — existing, extended additively (shared bookkeeping, R19)

## Dependencies and Sequencing

- Depends on: nothing in code. The rest of `decision-quorum-and-calibration` depends on `decision-engine` (PLAN-47, staged) and is not drafted.
- Sequenced 2026-10-29 by the cross-check: shares `JudgeCheckCodec`, `QualificationRecordCodec` and `TokenTotal` (6 code entries) with PLAN-113 (`qualification-token-split`); run after PLAN-113 has merged.
- Overlaps with: none with PLAN-46, 105, 108, 109 (PLAN-46 declares `core/role/MachineConfiguration`, `ModelCatalogue`, `RoleAssignments`, not `core/role/qualification/`). PLAN-99 (`judgment-votes`, parked) declares `core/role/corpus/ImportedItem.java` and `core/store/` (directory): no shared class. PLAN-47 (`decision-engine`) declares `workflow/decision/`, not `core.role`.
- Adjacent to: `decision-engine` (PLAN-47) and the blocked part of `decision-quorum-and-calibration`; `plan-marshall-mcp` `default-units-and-drift-tests` (the default unit files).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-135-decision-calibration-record.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
