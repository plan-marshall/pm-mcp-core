# PLAN-95: Check the length of a string before its pattern in the verification corpus

epic: pm-mcp-core-m2-m4
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-95-schema-subset-length-first.md` and is queued as one row file, `queue/PLAN-95.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r1`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R17, R18.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-05.
- R12 — log message holder: none.

## Objective

`SchemaSubset.checkString` of `pm-core` (package `core.role.corpus`, the schema subset the verification corpus is validated against, PM-WORK-6) compiles a schema `pattern` and matches it against the whole value before it tests `maxLength`, so a value far over its bound reaches the backtracking engine first. CodeRabbit raised it on #126 (the enrolled-schema path, which PLAN-89 bounded); the corpus path was verified in the code on 2026-10-21 and no plan owned it. Check the length first, report an over-long value as such, and do not evaluate the pattern for it. The operator asked for a small plan of its own (2026-10-22); it is not part of PLAN-94, which promises no change of behaviour.

## Deliverables

1. **The order in `SchemaSubset.checkString`.** Test `maxLength` first (counted in code points, as today). When the value is longer, add the existing message `<path>: longer than <N>` and do not compile or match the `pattern`. Otherwise unchanged: the pattern is evaluated as Python's `re.search` as today, with the same messages. One method changes; no signature, no message text, no other type.
2. **The tests, in `SchemaSubsetTest`** (JUnit 5, `@DisplayName`, AAA): (a) a value over `maxLength` that also fails its `pattern` yields exactly the length error and no pattern error; (b) a value over `maxLength` with a `pattern` that does not compile yields the length error alone and does not throw (this pins the one consequence: the broken schema pattern is reported for the next value inside the bound, not for an over-long one, and it is the deterministic proof that the pattern is not evaluated); (c) a value inside the bound still yields the pattern error, and a value of exactly `maxLength` code points still goes through the pattern; (d) the existing cases stay green unchanged, the code-point case (`"😀😀"` against `maxLength` 2) included.
3. **Document the decision.** One ADR in `pm-core/doc/adr/core-role-corpus/` (next free number; Status, Date, Context, Decision, Alternatives considered, Consequences, Source) records: the length is checked first and an over-long value skips the pattern; the consequence of (b); the alternatives (compile the pattern first and report a broken schema for every value; bound the regex by a timeout or a step budget, which `PythonRegex` does not offer). Extend `pm-core/doc/developer/core-role-corpus.adoc` where it describes the order of the checks.
4. **A read-only sweep, reported in the pull request body.** List every place in `pm-core`, `pm-runtime` and `pm-workflow` that matches a pattern taken from a schema or a document against a value of unbounded length (`grep` for `PythonRegex.compile` and `Pattern.compile` fed by a schema member), and state for each whether its value is bounded first. Fix nothing outside `checkString`; a finding becomes a lead for the orchestrator, not a change of this pull request.

**Done when:** `SchemaSubset.checkString` tests `maxLength` before `pattern` and skips the pattern for an over-long value; the four test groups of Deliverable 2 exist and pass beside every existing `SchemaSubsetTest`, `CorpusValidatorTest` and `ProductCorpusValidationTest` case; the ADR and the developer documentation are written; the sweep of Deliverable 4 is in the pull request body; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set:** PM-WORK-6 (the verification corpus; `doc/specification/model-work/01-roles-and-configuration.adoc` § Corpus Topics of a Role and the corpus validator it names); the lead is the CodeRabbit thread on #126, which the pull request body links.

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: `SchemaSubset.checkString` evaluates `pattern` before `maxLength` — `pm-core/src/main/java/de/planmarshall/core/role/corpus/SchemaSubset.java` lines 139 to 154 (the `pattern` block at line 140, the `maxLength` block at line 150)
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: 1 cited path(s) exist at 4617865 and are unchanged since 0bf64f7 (git diff --stat 0bf64f7..4617865 empty): pm-core/src/main/java/de/planmarshall/core/role/corpus/SchemaSubset.java
- OBSERVED: `checkString` has one caller, `SchemaSubset.java` line 107 (`case JsonString(var string) -> checkString(...)`), and `SchemaSubset` is used by `CorpusValidator.java`, `JsonValue.java`, `SchemaSubset.java` — `git grep` of the three modules' `src/main`
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: git grep checkString over src/main at 4617865: only SchemaSubset.java; users of SchemaSubset: CorpusValidator.java, JsonValue.java, SchemaSubset.java
- HYPOTHESIS: whether a value or a schema of the corpus can be attacker-controlled was not assessed (the corpus items are imported or project-supplied, the shipped patterns read as linear) — confirm/refute at `CorpusValidator` and `CorpusImport` (verify-at-outline); the fix is wanted either way, as the enrolled-schema path now has a hard bound and this one is the inconsistent path
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-core/src/main/java/de/planmarshall/core/role/corpus/` and re-read `checkString` at the head; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: no package file and no index row for this spec (a plan with no package file); the only open PR (ci pr list) is #71, a dependabot bump, which does not name it; the claims above were re-read individually

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/corpus/SchemaSubset.java` — `checkString`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/role/corpus/SchemaSubsetTest.java` — the four test groups of Deliverable 2
- OBSERVED: `pm-core/doc/developer/core-role-corpus.adoc` — developer documentation of the package, extended
- HYPOTHESIS: `pm-core/doc/adr/core-role-corpus/` — one ADR, the next free number of the directory

## Dependencies and Sequencing

- Depends on: nothing
- Overlaps with: computed by `corpus cross-check` at emit time (the corpus plans PLAN-79 and PLAN-80 are shipped; no staged plan names `core/role/corpus/`)
- Adjacent to: PLAN-89's bound on the enrolled-schema path (shipped as #126), which this plan does not touch

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-95-schema-subset-length-first.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
