# PLAN-V2-65: Bound the strings the shipped corpus schemas match with a pattern

epic: pm-mcp-core-m2-m4-v2
workstream: WS-05
origin: PLAN-110 of the epic pm-mcp-core-m2-m4
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-65-corpus-schema-length-bounds.md` and is queued as one row file, `queue/PLAN-V2-65.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R17, R18, R19. (R13 binds the three schemas and the records: a format change replaces version 1 in place; R12 is dropped, no message is added; R14, R15 and R16 are dropped, no `core.store`, `pm-workflow` or `pm-runtime` class changes; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-role-corpus.adoc`; ADR directory: `pm-core/doc/adr/core-role-corpus/` (the next free number; 008 is the last); layout: workstream WS-05.

## Objective

`SchemaSubset.checkString` checks `maxLength` before `pattern` since #144 (PLAN-95, ADR `core-role-corpus/008`), so a bound on a string now keeps an over-long value away from the regex engine. The three corpus schema resources of `pm-core` do not use it: `imported-item.schema.json` has nine `pattern` members and a `maxLength` only on `title` (a field without a pattern), so a long value of a project- or hash-pinned source still reaches seven non-trivial patterns unbounded (the two patterns `.` are constant-time); `source-overlay.schema.json` and `source-manifest.schema.json` have the same shape. The enrolled-schema path has a hard bound (`ArgumentSchema.PATTERN_MAX_LENGTH_CEILING`, 256); the corpus path has none. The risk is low (the shipped patterns read as linear and the lane of PLAN-95 refuted that the schemas can be attacker-controlled), the work is mechanical, and the inconsistency is the point: bound every string a non-trivial pattern matches, in the schema and in the Java records that copy the schema.

## Deliverables

1. **Bound the schema strings.** Add `maxLength` to every string member that carries a non-trivial `pattern` in the three resources below `pm-core/src/main/resources/de/planmarshall/core/role/corpus/`: exact where the pattern fixes the length (a date 10, a commit 40, a SHA-256 64, a source name 64 for `^[a-z][a-z0-9-]{1,63}$`), and a stated ceiling otherwise (an item id and a topic 256, the same figure as `PATTERN_MAX_LENGTH_CEILING`; a repository URI 2048; a repository-relative path 1024; a licence id 64). The two patterns `.` get no bound. The values are the plan's proposal and are recorded in the ADR of Deliverable 3; a value the product corpus or a shipped source exceeds is found by running the check of Deliverable 2 over them and raised before it is fixed. Test first: `CorpusSchemaResourcesTest` (read it first) names, for every member of the three schemas that carries a non-trivial `pattern`, a `maxLength`, and fails for a member added without one.
2. **Bound the records the same way.** The Java records that copy the schema (`ImportedItem`, `ImportedItem.Origin`, `SourceManifest`, `SourceManifest.SourceFile`, `SourceOverlay`) check the same bounds before their `require(value, PATTERN, field)` (the precedent is `ImportedItem.TITLE_MAX_LENGTH`, checked at `ImportedItem.java` line 331 with `codePointCount`), through constants next to the patterns they bound; a value over the bound is refused with the existing `IllegalArgumentException` shape (`'<field>' is longer than N characters`). `CorpusPatternParityTest` is extended so that the bound of a record equals the `maxLength` of the schema member it copies (its inventory already fails a pattern on one side only: do the same for `maxLength`). Test first: a value of exactly the bound passes, a value one code point over is refused with the length message and never reaches the pattern, per bounded field (`@ParameterizedTest`), in `ImportedItemCodecTest`, `SourceManifestCodecTest`, `SourceOverlayCodecTest` and `CorpusValidatorTest` where the schema path is exercised.
3. **Document the decision.** One ADR in `pm-core/doc/adr/core-role-corpus/` (next free number; Status, Date, Context, Decision, Alternatives considered, Consequences, Source) records the bounds and why each value, the exact bounds derived from the pattern, the ceiling that matches the enrolled path, and the alternatives (leave the schemas unbounded because the patterns are linear; a time or step budget in `PythonRegex`, which it does not offer, ADR 008). Extend `pm-core/doc/developer/core-role-corpus.adoc` where it describes the formats and the order of the checks. The item schema `verification-item.schema.json` is the corpus's own in `plan-marshall-mcp` and is not touched: list in the pull request body that its patterns are bounded there or not (a finding, not a change).

**Done when:** every non-trivial `pattern` of the three corpus schemas has a `maxLength` and every record enforces the same bound, with the parity test proving it; the product validation test (`ProductCorpusValidationTest`) and every existing corpus case stay green; the ADR and the developer documentation are written; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** PM-WORK-6 (the verification corpus; `doc/specification/model-work/01-roles-and-configuration.adoc` § Corpus Topics of a Role and the corpus validator it names); ADR `core-role-corpus/001` (the formats), `005` (patterns with Python meaning), `008` (length before pattern). The lead is the lane report of PLAN-95 (`landings/PLAN-95.md`).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **A bound never rejects what the formats admit today without a decision.** A bound below what a shipped source or the product corpus holds is an operator question (inbox message), not a silent narrowing; the figures of Deliverable 1 are proposals until D1's check has run.
- **Format versions stay 1 (R13):** `schema_version` of `imported-item.schema.json` is untouched.

## Claim Labels

- OBSERVED: `imported-item.schema.json` has `pattern` at lines 26, 31, 49, 163, 177, 182, 187, 226 and 235 (nine) and `maxLength` once, at line 38 on `title`; `source-overlay.schema.json` has patterns at lines 21, 26, 34, 39, 68, 73, 78, 98, 242 and 257 and one `maxLength` (line 85, `title`); `source-manifest.schema.json` has six patterns (lines 22, 27, 32, 37, 61, 66) and no `maxLength`
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `grep -n '"pattern"\|"maxLength"'` over the three files at a8da4104
- OBSERVED: `SchemaSubset.checkString` tests `maxLength` before `pattern` since #144 (`3cc15854`), ADR `core-role-corpus/008`; the enrolled path bounds with `ArgumentSchema.PATTERN_MAX_LENGTH_CEILING = 256` (`ProjectEnrolment.java` line 684)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `git log` of `SchemaSubset.java`; the ADR and the constant read
- OBSERVED: the records copy the patterns as `PythonRegex.compile(...)` constants (`ImportedItem.ID_PATTERN`, `TOPIC_PATTERN`, `SLUG_PATTERN`, `SourceManifest.NAME_PATTERN`, `COMMIT_PATTERN`, `LICENCE_PATTERN`, line numbers 46 to 70 of the two classes) and the only length check in them is `TITLE_MAX_LENGTH` (`ImportedItem.java` line 73, check at 331; `SourceOverlay.java` line 95); `CorpusPatternParityTest` pins patterns record against schema and has no `maxLength` case
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `grep -n "MAX_\|Pattern.compile\|PythonRegex.compile"` over the records; the test header read
- OBSERVED: the three schemas exist only in `pm-core` (`find /home/oliver/git/plan-marshall-mcp -name "*.schema.json"` finds `test/model/verification/schema/verification-item.schema.json` alone)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `find` over the sibling checkout, which may be behind its `origin/main`
- HYPOTHESIS: the nine patterns are linear or constant-time, so the unbounded path is a consistency gap rather than a hang (the lane of PLAN-95 read them as linear; the path pattern with its lookahead `(?!.*(^|/)\.{1,2}(/|$))` was not measured) — confirm/refute at the trace matrix; the plan is wanted either way
  - verdict: unverifiable | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the ceilings (256, 2048, 1024, 64) clear every id, topic, repository and path of the shipped sources and of the product corpus — confirm/refute by running the corpus check (Deliverable 1) before the figures are fixed
  - verdict: unverifiable | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: hypothesis
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-core/src/main/java/de/planmarshall/core/role/corpus/` or its resources (the parked `judgment-votes` reads `ImportedItem`), re-read the three schemas and `CorpusPatternParityTest` at the head; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: no package file and no index row; no staged plan edits a corpus resource

## Expected Surface

- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/role/corpus/imported-item.schema.json` — `maxLength` per patterned string (D1)
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/role/corpus/source-overlay.schema.json` — same (D1)
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/role/corpus/source-manifest.schema.json` — same (D1)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/ImportedItem.java` — bound constants and checks (D2); the staged plan `judgment-votes` reads this class
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/SourceManifest.java` — same (D2)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/SourceOverlay.java` — same (D2)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/corpus/CorpusSchemaResourcesTest.java` — every patterned member has a bound
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/corpus/CorpusPatternParityTest.java` — `maxLength` parity
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/corpus/ImportedItemCodecTest.java`, `SourceManifestCodecTest.java`, `SourceOverlayCodecTest.java`, `CorpusValidatorTest.java` — the bound cases
- OBSERVED: `pm-core/doc/developer/core-role-corpus.adoc` — developer documentation, extended
- HYPOTHESIS: `pm-core/doc/adr/core-role-corpus/` — one ADR, the next free number of the directory

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, the ADR directory and developer guide above as bookkeeping where they exist.

## Dependencies and Sequencing

- Depends on: nothing (PLAN-95 shipped, #144)
- Overlaps with the live plans (class level): one read overlap — PLAN-V2-76 `judgment-votes` (parked) declares `pm-core/.../role/corpus/ImportedItem.java` as read for its `exact_choice` grading items; this plan edits that class. No other staged plan names `core.role.corpus`.
- Disjoint from the other drafts of this cluster (no shared class): PLAN-V2-46 (`runtime-endpoint-residues`), PLAN-V2-56 (`qualification-token-split`) (`core.role.qualification` and `core.store`, not `core.role.corpus`), PLAN-V2-67 (`main-hygiene-leftovers`), PLAN-V2-68 (`profile-wait-margin`).
- Adjacent to: `plan-marshall-mcp`, whose `verification-item.schema.json` and `validate.py` are the corpus's own and stay untouched.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-65-corpus-schema-length-bounds.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
