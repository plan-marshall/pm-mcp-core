# PLAN-V2-84: Mark every production package @NullMarked and return absence as Optional

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-84-null-safety-nullmarked.md` and is queued as one row file, `queue/PLAN-V2-84.json`,
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

Rules that apply to this plan: R02, R06, R08, R11, R13, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R07, R09, R10, R12, R14 to R16: no specification, message or protocol change; R13 because no stored format changes.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the guides of the packages whose public signatures change (additive sentence); ADR directory: none (the ADR of PLAN-V2-83 decides); layout: workstream WS-11.
- R12 — none.

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

None of the 67 production `package-info.java` files carries `@NullMarked`, there is no `@Nullable` anywhere, and 79 methods `return null;`. The skill `java-null-safety` wants `@NullMarked` in every package, absence as `Optional<T>` (never `null`, never a `@Nullable` return), and `@Nullable` for the states that really are null (a field, a parameter, a record component). The operator chose on 2026-10-30: `@NullMarked` everywhere, and (decision 7) to convert the existing `Optional` parameters, fields and record components too — that conversion is PLAN-V2-97 to PLAN-V2-99, which run after this plan.

## Deliverables

1. **`@NullMarked` on all 67 production packages, with a guard (test first).** Add `@NullMarked` (`org.jspecify.annotations`) to every production `package-info.java` of `pm-core` (20), `pm-workflow` (22), `pm-runtime` (20) and the five providers (1 each). A new test fails when any production package of any module has no `package-info.java` or one without `@NullMarked`; it scans the source trees of the reactor from the repository root (the same way `BuildGuardsIT` finds its fixtures), so a later plan that adds a package without the annotation fails the build. Done in one pass over the files: no class changes in this deliverable.
2. **The 79 `return null;` sites become `Optional` returns (test first).** pm-core 29, pm-workflow 25, pm-runtime 23, providers 2: find them with `git grep -n 'return null;' -- '*/src/main/java/*.java'`. At each site the method returns `Optional<T>` and its callers adapt; a method that implements an interface or overrides a method of a library keeps the library's contract and is listed in the pull request body with the reason. Test first: for each changed method, the test of the absent case asserts `Optional.empty()` and the callers' tests pass unchanged in meaning. The public API of `pm-core` that `plan-marshall-mcp` consumes changes: list every changed public signature in the pull request body under **Owed outside this repository**.
3. **Nullable states are `@Nullable`.** Fields that are assigned `null` and tested later, parameters that the code compares with `null`, and local states like `List<Fence> fences = null` (`FencedGenerations.java:208`) are annotated with `@Nullable` (JSpecify) or restructured so that no null state remains; record components that may be null are `@Nullable`, never `Optional` in new code. Find them with `git grep -n -E '(= null;|== null|!= null)' -- '*/src/main/java/*.java'` and judge each; the pull request body lists the count per module of annotated, restructured and left sites (a left site names its reason, for example an `Objects.requireNonNull` boundary check, which stays).

**Done when:** `git grep -c '@NullMarked'` over the production `package-info.java` files equals 67 (or the number then present) and the guard test fails on a fixture package without it; `git grep -n 'return null;' -- '*/src/main/java/*.java'` finds only the sites listed with a reason; every changed method has a test of its absent case; the pull request body lists the changed public signatures. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** `java-null-safety` (SKILL.md and standards); R20; the audit `skills-audit-2026-10-30.md` § 2.8; ADR of PLAN-V2-83.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Existing `Optional` parameters, fields and record components are converted by PLAN-V2-97 to PLAN-V2-99, not here;** this plan adds no new ones.
- **`@Nullable` never on a return** (the skill): an absent result is `Optional<T>`.
- **No behaviour change:** a conversion that would change what a caller observes (for example a `null` that is a deliberate marker of a sentinel in a stored record) is left and listed; the stored formats are untouched (R13).
- **Run alone at the front of the epic:** the plan edits a file in nearly every package; it is paired by directory (see the Expected Surface) and the test-migration plans PLAN-V2-85 to PLAN-V2-92 depend on it.

## Claim Labels

- OBSERVED: `@NullMarked` in 0 of 67 production package-infos (core 20, workflow 22, runtime 20, providers 5); no JSpecify import, no `@Nullable` or `@NonNull` in `src/main` (`skills-audit-2026-10-30.md` § 2.8)
- OBSERVED: `return null;` — pm-core 29, pm-workflow 25, pm-runtime 23, providers 2 = 79; `Objects.requireNonNull` 2592 sites (kept)
- OBSERVED: jspecify 1.0.1 is on the compile classpath of `pm-core` transitively; the direct dependency is PLAN-V2-83's
- HYPOTHESIS: the callers of the 79 methods are inside the repository; some public ones are used by `plan-marshall-mcp` — confirm with `git grep` of that repository's `origin/main` and list them
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: every `package-info.java` of `pm-core/src/main/java/`, `pm-workflow/src/main/java/`, `pm-runtime/src/main/java/` and `pm-providers/*/src/main/java/` (67 files)
- HYPOTHESIS: the classes with a `return null;` (79 sites, found by the command in Deliverable 2) and their callers; the tests of the changed methods
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/guard/` — the guard test of Deliverable 1 (new file)
- Directory entries (replacement of R19 for this plan): `pm-core/src/main/java/de/planmarshall/core/`, `pm-workflow/src/main/java/de/planmarshall/workflow/`, `pm-runtime/src/main/java/de/planmarshall/runtime/`

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`): the direct `jspecify` dependency.
- PLAN-V2-85 to PLAN-V2-92 (the test migrations) and PLAN-V2-94 depend on this plan: they edit the tests and the classes this plan changes.
- Overlaps: by directory with every plan that edits production code of the three modules; it therefore runs at the front, alone with PLAN-V2-83.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-84-null-safety-nullmarked.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
