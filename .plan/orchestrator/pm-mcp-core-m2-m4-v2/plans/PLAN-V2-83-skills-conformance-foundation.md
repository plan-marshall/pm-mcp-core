# PLAN-V2-83: Add the dependencies the coding skills need, and record the repository's rules and accepted exceptions

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-83-skills-conformance-foundation.md` and is queued as one row file, `queue/PLAN-V2-83.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
>
> Origin: the skills conformance audit of 2026-10-30 (`skills-audit-2026-10-30.md` in the epic directory), taken at `origin/main` `6847c748`, and the operator's decisions of the same day (Decisions in `epic.md`). The plan family is PLAN-V2-83 to PLAN-V2-94 (workstream WS-11); it runs first.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R08, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04 apply only through the dependency; R05, R07, R09, R10, R12 to R16: no behaviour, specification, message or store change.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: none; ADR directory: `doc/adr/` (repository-wide); layout: workstream WS-11.
- R12 — none (no message).

**Read the skills first.** Before the outline, read `SKILL.md` and the standards it points to of every skill R20 names that this plan uses: `plan-marshall:../marketplace/bundles/pm-dev-java-cui/skills/{cui-logging,cui-testing,cui-http-testing}` and `../marketplace/bundles/pm-dev-java/skills/{java-core,java-lombok,java-null-safety,javadoc,junit-core,junit-integration}` (the plan-marshall checkout beside this repository: `/home/oliver/git/plan-marshall/marketplace/bundles/`). Say in the first progress note which you read. Where two skills disagree, the ADR of PLAN-V2-83 decides; where it does not, stop and report through the inbox message; do not choose.

## Objective

The audit found that `pm-core` and the five provider modules have no `cui-test-generator`, that `cui-test-value-objects` and `org.jspecify:jspecify` are in no pom, and that `pm-workflow` and `pm-provider-sonar` have no Lombok. CLAUDE.md forbids adding a dependency without asking; the operator answered on 2026-10-30 and allowed exactly these four (plus the test-scope `cui-test-mockwebserver-junit5` that R04 already allows for `pm-provider-sonar`). This plan adds them with managed versions, and writes the rules and the accepted exceptions down once — in CLAUDE.md and one repository ADR — so the eleven plans after it, and every plan of the epic, work from the same text.

## Deliverables

1. **The dependencies (test first: the build is the test).** `cui-test-generator` (test scope) in `pm-core`, `pm-provider-git`, `-github`, `-gitlab`, `-http`, `-sonar`; `cui-test-value-objects` (test scope) in every module that has tests; `org.jspecify:jspecify` (compile scope) in every module; `org.projectlombok:lombok` (provided/annotation processor, as the other modules declare it) in `pm-workflow` and `pm-provider-sonar`; `cui-test-mockwebserver-junit5` (test scope) in `pm-provider-sonar`. The version of each comes from the parent POM's management (`pm-mcp-parent`) — **no version literal in a module POM**. If the parent does not manage one of them, the plan stops, reports it through the inbox message and leaves that dependency out: the parent is outside this repository. `pm-core` and `pm-workflow` keep their enforcer rules (no JGit, no `cui-http`, no Quarkus): the new artifacts must not pull any of them in, which `BuildGuardsIT` proves.
2. **CLAUDE.md and the repository ADR.** CLAUDE.md § Code Standards gains the rules of R20 in the repository's own words (generated test data and its exemption, contract tests, `LogAsserts`, annotation-based HTTP tests, `@NullMarked` and `Optional` for absence, `@UtilityClass`, no generic catch) and § Dependencies and Versions names the four dependencies as allowed. One ADR `doc/adr/NNN-the-coding-skills-and-the-accepted-exceptions.adoc` (next free number, R11; template `doc/adr/template.adoc`, index `doc/adr/README.adoc`) records, with the reason for each, the exceptions that the audit showed the skills cannot cover. **These are proposals; the operator confirms each at the refine step before the ADR is written, and an answer of no turns the exception into a conversion task for a plan of the family:** (a) `Optional` as a parameter, a field or a record component stays (about 224 parameters and record components and 55 fields; the null-safety skill forbids it, the repository's records and ports are built on it); (b) a record with four or more components gets no `@Builder` (its canonical constructor is the builder; the 7 to 38 component records are a design question of their owners); (c) tests follow behaviour, not one class per production class, and the 400-line budget of `junit-core` is reported by the landing, not enforced (the build enforces 80 % coverage); (d) an assertion carries a message only where `assertAll` or the display name does not already say what failed; (e) hand-written fakes stay and EasyMock is not adopted (28 fakes, none needs a mocking library).

**Done when:** every module of the Expected Surface compiles and passes its tests with the new dependencies and no version literal; `BuildGuardsIT` (pm-core) and the module enforcer rules are green; `mvn dependency:tree` of `pm-core` and `pm-workflow` shows none of the forbidden artifacts; CLAUDE.md and the ADR say the same thing as R20 of `standing-rules.md`; the operator's answers on the five exceptions are quoted in the ADR. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and, for `pm-core` and `pm-runtime`, `verify -Pintegration-tests -pl <module> -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`.

**Trace starting set:** Repository `CLAUDE.md` (Dependencies and Versions, Code Standards, Dependency Rules); R04 and R20 of `standing-rules.md`; the conformance audit `skills-audit-2026-10-30.md` §§ 1, 4 and 5; ADR `doc/adr/001` to `008` (the form and the numbering).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Dependencies are the operator's decision:** the five artifacts above are allowed (2026-10-30: `cui-test-generator`, `cui-test-value-objects`, JSpecify as a direct dependency, Lombok in `pm-workflow` and `pm-provider-sonar`; `cui-test-mockwebserver-junit5` for `pm-provider-sonar` by R04). Nothing else is added, and no plugin.
- **The parent POM is not edited here.** A version the parent does not manage is a finding for the parent, not a literal in this repository.
- **No behaviour change:** adding a dependency changes no production class; the annotation processing of Lombok in `pm-workflow` and `pm-provider-sonar` must not change any generated signature (the modules hold no Lombok annotation yet).
- **The ADR is the decision record of the exceptions**; a later plan that disagrees with it supersedes it by a new ADR, it does not work around it.

## Claim Labels

- OBSERVED: dependencies today — `cui-test-generator` only in `pm-workflow` and `pm-runtime`; `cui-test-value-objects` and `jspecify` in no module POM (jspecify 1.0.1 reaches `pm-core` transitively through `cui-java-tools`); Lombok in core, runtime and the providers git, github, gitlab, http, but not in `pm-workflow` and `pm-provider-sonar`; `cui-test-mockwebserver-junit5` in runtime, github, gitlab, http (`skills-audit-2026-10-30.md` header)
- OBSERVED: the four exceptions the skills cannot cover and the conflicts between the skills and CLAUDE.md (`skills-audit-2026-10-30.md` § 5, items 1 to 11)
- HYPOTHESIS: the parent POM `pm-mcp-parent` manages the versions of the artifacts above — confirm with `help:effective-pom` of one module at the outline; if not, the plan reports it and leaves the artifact out
- Verify-first clause: before editing, list the open pull requests for one that touches the packages of the Expected Surface and re-run the counting commands of the claims above at the head of `main` (a plan of the family may have landed since the audit); keep only what is still open; a refutation loops back to re-scope.

## Expected Surface

- OBSERVED: `pom.xml` (root), `pm-core/pom.xml`, `pm-workflow/pom.xml`, `pm-runtime/pom.xml`, `pm-providers/pm-provider-git/pom.xml`, `pm-providers/pm-provider-github/pom.xml`, `pm-providers/pm-provider-gitlab/pom.xml`, `pm-providers/pm-provider-http/pom.xml`, `pm-providers/pm-provider-sonar/pom.xml`
- OBSERVED: `CLAUDE.md` (Code Standards, Dependencies and Versions)
- HYPOTHESIS: `doc/adr/NNN-the-coding-skills-and-the-accepted-exceptions.adoc` (new), `doc/adr/README.adoc` (index row, additive)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`, the ADR directory and developer guide of an existing package as additive bookkeeping.

## Dependencies and Sequencing

- Depends on: nothing. It is the first plan of the family and of the epic: every other plan reads R20 and uses the dependencies.
- Every later plan of the epic depends on it (the orchestrator adds the edge to their `Depends on`); the plans PLAN-V2-84 to PLAN-V2-94 depend on it directly.
- Overlaps: the nine POMs and `CLAUDE.md` are touched by no other plan's Expected Surface.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-83-skills-conformance-foundation.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
