# PLAN-V2-112: Close the two reliability bugs that keep the quality gate of `main` red

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
origin: the three-layer analysis of PLAN-V2-110 (`landings/PLAN-V2-110.md`, 2026-11-02, C1) and the operator's decision of 2026-11-04 ("The two open bugs: Stage a fix"); owner-register Wa58
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-112-main-green-reliability-bugs.md` and is queued as one row file, `queue/PLAN-V2-112.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r6`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R15, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R10: no specification text is read as test input; R11: no ADR is added, ADR 013 is extended; R12: no message; R13: no format change; R14, R16: neither `core.store` locks nor `pm-runtime` are touched. R15 because `UnitFrontmatterReader` is a `pm-workflow` class. R18 is the rule this plan exists for, and the one that bit PLAN-V2-110: see the first Standing Constraint.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none new (no package gains a behaviour); ADR directory: `doc/adr/` (repository-level, ADR 013 is extended in place, no new number); layout: workstream WS-11.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read.

## Objective

SonarCloud lists two open bugs on `main` (analysis of `70bc999f`, 2026-11-02), both `java:S2637`, and the quality gate of `branch=main` fails `new_reliability_rating` (2 against 1) on them alone; every other condition is `OK`. While the gate fails, the `sonar-build` job of `main` fails and `deploy-snapshot` is skipped, so the consumers of the `SNAPSHOT` (`plan-marshall-mcp`, `pm-mcp-clients`) build against an old artifact. PLAN-V2-110 set out to clear the null-rule issues of `main` and cleared the S2583 and S2589 ones; these two survived it:

1. `pm-core/src/main/java/de/planmarshall/core/role/qualification/Scoring.java` line 80, in `Scoring.threshold(ImportedItem)`: "This method's return value is marked @NullMarked at package level but null is returned" (issue `AaGW3GG6S7pM3HqFXmV_`, created 2026-10-27). The method returns the pass threshold of the item's grading, which is a `@Nullable BigDecimal` component of the record `ImportedItem.Grading`, or the default `0.5`. The original code read the accessor twice (`x == null ? DEFAULT : item.grading().passThreshold()`, the second call unguarded); PR #282 rewrote it to `Objects.requireNonNullElse(item.grading().passThreshold(), DEFAULT_PASS_THRESHOLD)`; the issue is still open at line 80 after the analysis of the merge.
2. `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/UnitFrontmatterReader.java` line 191, the constructor of the private nested class `Fields`: "`modificationDate` is marked @NullMarked at package level but is not initialized in this constructor" (issue `AaGRXlk3MRLCRryorZIe`, created 2026-10-18). The field `LocalDate modificationDate` (line 188) is assigned once, at line 294, and read once, at line 366 in `complete(...)`, after the `REQUIRED_KEYS` check (lines 359 to 363) has proved that `modification_date` was read. Between the construction and that line the field is null, which is a real state of the object. #282 touched only a Javadoc line of this file.

This plan clears both in a way the analysis of `main` accepts, and records which form worked. It changes no behaviour.

## Deliverables

1. **`Scoring.threshold`.** Read the accessor once into a local and test the local: `var threshold = item.grading().passThreshold(); return threshold == null ? DEFAULT_PASS_THRESHOLD : threshold;` (the default form). The other forms, if the default is not accepted, in this order: `Optional.ofNullable(...).orElse(DEFAULT_PASS_THRESHOLD)`; then making the component non-null with the default applied in the compact constructor of `ImportedItem.Grading` — the last is invasive (`ImportedItemCodec` lines 217 and 398 to 412 and `SourceOverlayCodec` lines 198 and 328 to 342 rely on the null) and is not taken without the operator (R07). Tests: `ScoringTest` gains a case that a declared pass threshold wins over the default and one that the default is `0.5` when the grading declares none (today only `rubric` touches the threshold, through `QualificationFixtures.item(...)`, which always builds `new Grading(method, null, null)`); the new cases take their data from generators and carry messages (R20). **Done when:** the cases pass and the diff changes `Scoring.threshold` and `ScoringTest` only.
2. **`UnitFrontmatterReader.Fields.modificationDate`.** The default form follows the repository's own precedent for "null until a required key is read": `private @Nullable LocalDate modificationDate;` (as `@Nullable UnitType type` at line 187 of the same class) and `Objects.requireNonNull(modificationDate, ...)` where `complete(...)` hands it to `UnitFrontmatter` (line 366), as `QualificationSettingsCodec.java` line 126 (`private byte @Nullable [] rewritten;`) and line 114 do, and which is not among the open issues of `main`. Check the neighbouring field `String previousKey` (line 189): it is also uninitialised and unannotated and Sonar does not flag it; if it is in the same state, give it the same treatment, and say in the landing why it was or was not flagged. Tests: the existing `UnitFrontmatterReaderTest` cases ("refuses a frontmatter that lacks a required key at its closing line", "is read field by field, with the line its body starts at") stay green unchanged; no behaviour changes. **Done when:** the diff changes `UnitFrontmatterReader` only (and `UnitFrontmatterReaderTest` if a case is added).
3. **The record of what works.** Extend `doc/adr/013-the-null-contract-of-the-null-marked-packages.adoc` (class (c), lines 36 to 46) with three statements, each backed by this plan's evidence: (a) which form was accepted for a `@Nullable` record component defaulted in a `@NullMarked` method (and that the double-call ternary and `Objects.requireNonNullElse` were not — if the evidence at `main` after the merge shows it; until then the ADR says "not accepted by the analysis of `main` at `70bc999f`"); (b) the form for a field that is non-null once a required key was read (`@Nullable` plus `requireNonNull` at the read); (c) that the analysis of a pull request is scoped to new code and does not prove `main`: #282 reported "0 open issues" for a rewritten line that `main` still flagged, so the proof of a null-rule fix is the read of `branch=main` after the merge. **Done when:** ADR 013 carries the three statements and its links resolve (R17).

**Done when:** Deliverables 1 to 3. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready — **and that is not the proof of this plan** (first Standing Constraint). **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-core` and `pm-workflow` (where a full run does not finish inside the host's bound, the targeted `-Dtest=ScoringTest` and `-Dtest=UnitFrontmatterReaderTest` runs and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main` first; the sibling checkout lags).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **The proof of this plan is `main`, read by the orchestrator after the merge, not the pull request.** The analysis of a pull request scores new code; PLAN-V2-110's #282 rewrote `Scoring.threshold` and reported zero open issues while `main` kept the issue. The lane reports, in its landing message, that it could not read `main` before the merge, names the form it chose for each site and the evidence it had (a site of the tree with the same shape that `main` does not flag), and does not claim the terminal measurement. The orchestrator reads `branch=main&resolved=false&rules=java:S2637` after the merge: both issues closed, the gate `OK`, `Maven Build` green on `main`, `deploy-snapshot` ran. If a site is still flagged, the next form of the order above is staged as a follow-up, and a form that needs the operator (the non-null component, a `@SuppressWarnings` with a reason where ADR 013 allows it) is asked, not decided.
- **No suppression, no `NOSONAR`, no `sonar.issue.ignore`** (ADR 013, ADR 011): the sites are fixed in code. A form that cannot be fixed in code is a question to the operator (R07), not a suppression.
- **No behaviour change.** `Scoring.threshold` returns the same values for the same inputs; `UnitFrontmatterReader` accepts and refuses the same documents with the same refusals.
- **Do not touch** the other 19 open issues of `main` (smells: `S1135`, `S1192`, `S3776`, `S135`, `S6878`, `S1612`, `S1117`, `S1144`, `S3516`, `S112`); they do not fail the gate and belong to other plans (PLAN-V2-67, 94).
- **Where the project analyses its pull requests with Sonar** (`runbook_lane resolve` reports `sonar: configured`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker, never a silent pass. The merge gate reads the open-issue count at the head — `0` confirmed, or every remaining issue dismissed — and an `undecidable` read blocks.

## Claim Labels

- OBSERVED: the two open BUG-type issues of `main` are the only open issues of type BUG; the gate fails `new_reliability_rating` (actual 2, threshold 1) only; S2583 and S2589 are 0 — SonarCloud public API, `branch=main`, analysis of `70bc999f`, read 2026-11-02 and 2026-11-04
- OBSERVED: `ImportedItem.Grading` is `record Grading(GradingMethod method, @Nullable String judge, @Nullable BigDecimal passThreshold)` (`ImportedItem.java` line 164), compact constructor lines 169 to 175; `DEFAULT_PASS_THRESHOLD = new BigDecimal("0.5")` (`Scoring.java` line 28); `Scoring.threshold` has the one caller `Scoring.right(ImportedItem, BigDecimal)` (line 72), whose callers are `RoleGrader.java` line 312 and `ScoringTest` lines 68 to 70 — read at `70bc999f`
- OBSERVED: `Fields` is the private static nested class at `UnitFrontmatterReader.java` lines 179 to 370; `modificationDate` (188) is written at line 294 (`case KEY_MODIFICATION_DATE -> modificationDate = date(value, number, column)`) and read at line 366; `date(...)` returns a non-null `LocalDate` or throws `Refusal`; `complete(...)` checks `REQUIRED_KEYS` first — read at `70bc999f`
- OBSERVED: PR #282 changed one line of `Scoring.java` (the ternary to `requireNonNullElse`) and one Javadoc line of `UnitFrontmatterReader.java`, and reported "0 open issues of any rule" for its pull request while `main` kept both
- OBSERVED: `QualificationSettingsCodec.java` line 126 `private byte @Nullable [] rewritten;` read through `Objects.requireNonNull(reader.rewritten, "rewritten")` at line 114, and it is not among the open issues of `main`
- HYPOTHESIS: the single-read local form clears the issue at `Scoring.java` line 80 (the double-call ternary and the `requireNonNullElse` form did not): confirm/refute by a site of the tree with the same shape that `main` does not flag; the proof is `main` after the merge
- HYPOTHESIS: `String previousKey` (line 189) is in the same state as `modificationDate` and is not flagged because it is assigned before any read in every path: confirm/refute at the class
- Verify-first clause: before editing, list the open pull requests for one that touches `Scoring.java`, `ImportedItem.java`, `UnitFrontmatterReader.java` or `doc/adr/013-*` (the conformance family PLAN-V2-97, 98, 100, 101 may touch them), re-read the live issue list of `branch=main` and the two files at the head; a refutation loops back to re-scope

## Expected Surface

Code and tests:

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/qualification/Scoring.java` — `threshold` (D1)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/qualification/ScoringTest.java` — two cases (D1)
- OBSERVED: `pm-workflow/src/main/java/de/planmarshall/workflow/dsl/UnitFrontmatterReader.java` — the nested class `Fields`, the field and its read in `complete` (D2)
- HYPOTHESIS: `pm-workflow/src/test/java/de/planmarshall/workflow/dsl/UnitFrontmatterReaderTest.java` — unchanged unless the outline adds a case (D2)

Documentation:

- OBSERVED: `doc/adr/013-the-null-contract-of-the-null-marked-packages.adoc` — class (c) extended (D3)

Not surface (R19): `doc/adr/README.adoc` (no new ADR), `doc/Traceability.adoc`, `doc/plans/README.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-110 (`null-contract-main-green`; shipped, #264, #268, #274, #282) and PLAN-V2-83 (shipped). Nothing live.
- Overlaps with (class level): PLAN-V2-97 (`optional-conversion-core`) declares `core.role` and `PLAN-V2-98` the `pm-workflow` DSL by directory; the gate (`corpus cross-check`) decides at emit. The hunks are one method and one field.
- Adjacent to: PLAN-V2-67 D4 (the five suppressions of the null rules, Wa55) and PLAN-V2-114 (the other null-contract findings of PLAN-V2-110, Wa60), which edit other files of the same rules. Emit this plan first: it is what turns the gate green and makes the `SNAPSHOT` deploy again.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-112-main-green-reliability-bugs.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
