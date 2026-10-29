# PLAN-86: Move the shared HTTP base into the module pm-provider-http

epic: pm-mcp-core-m2-m4
workstream: WS-08

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-86-provider-http-module.md` and is queued as one row file, `queue/PLAN-86.json`,
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

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: none expected: a move of classes and tests; a record the classes already log keeps its holder.

## Objective

plan-marshall-documentation #94 (`273171d`, 2026-10-18) gave the Module Structure Specification the module `pm-provider-http` (`de.planmarshall.provider.http.*`, in `pm-providers`): the shared HTTP base of the provider modules over `cui-http` — origin-pinned authentication, pagination with completeness, conditional requests, rate-limit outcomes with `resets_at`, and redaction; it names no forge and no analysis service and depends on `pm-core`. The roadmap item of Milestone 6 is "Move the shared HTTP base out of `pm-provider-ci` into the module `pm-provider-http` of `pm-providers`, on which `pm-provider-ci` and every provider adapter depend, so that `pm-provider-sonar` depends on no CI module; the move is done when the first analysis adapter starts, with the dependency rules and their enforcer executions". The first analysis adapter has started: `pm-provider-sonar` carries `SonarProvider` and uses the HTTP base from `pm-provider-ci`, which the new rule forbids. No package file of `doc/plans/` owns the move (found on 2026-10-19), so this spec is its whole brief.

Today `pm-provider-ci` holds the HTTP base and nothing else: `CiHttpClient`, `CiEndpoint`, `CiResponse`, `CiResult`, `PagedResult`, `TokenSource` and `Json` in `de.planmarshall.provider.ci`, with `CiEndpointTest`, `CiHttpClientTest` and `JsonTest`. The CI contract that module is named for (pull requests, review threads, checks, merge queue, issues) has no work package yet.

## Deliverables

1. **The module.** Create `pm-providers/pm-provider-http` (package `de.planmarshall.provider.http`, `package-info.java`, README in the form of the neighbouring modules, module entry in `pm-providers/pom.xml`, version management in the root POM) depending on `pm-core` and `cui-http` alone, and move the HTTP base classes and their three tests into it with history kept. The classes carry a `Ci` prefix that the module's contract rules out ("names no forge and no analysis service"); decide the neutral names at refine, record the choice in an ADR, and rename in the same change.
2. **The dependents.** `pm-provider-sonar`, `pm-provider-github` and `pm-provider-gitlab` depend on `pm-provider-http` and `pm-core`, import the moved classes from `pm-provider-http`, and no longer depend on `pm-provider-ci`.
3. **The dependency rules and their proof.** The root POM enforcer executions `provider` ("pm-core and pm-provider-ci alone among the modules of the product", skip property `pm.guard.provider.skip`) and `provider-contract` ("pm-provider-git and pm-provider-ci depend on pm-core alone", skip property `pm.guard.provider-contract.skip`) change to the rules that hold once `pm-provider-ci` is gone: a provider adapter module depends on `pm-core` and `pm-provider-http` alone among the modules of the product, never on the engine, `pm-runtime` or another provider implementation; `pm-provider-git` and `pm-provider-http` depend on `pm-core` alone. Each changed rule keeps a fixture project below `src/guard-controls` and its case in `pm-core/src/test/java/de/planmarshall/core/BuildGuardsIT.java` that fails without the rule, including the case that a provider adapter depending on `pm-provider-http` passes and one depending on a sibling provider module fails.
4. **Where the repository states the old shape.** `CLAUDE.md` § Dependency Rules ("A provider module depends on `pm-core` and, for the shared HTTP base, on `pm-provider-ci`; the contract modules `pm-provider-git` and `pm-provider-ci` on no other provider module") and § Build Commands (the list of modules with tests), `pm-providers/README.adoc`, the READMEs of the changed modules, ADR `doc/adr/002-cui-http-and-the-mock-web-server-for-http.adoc` where it names the module, the specification parts that name `pm-provider-ci` as the HTTP base (`credentials-and-enrolment/01-provider-credentials.adoc` and the others `git grep` lists), and the developer documentation of the moved package. Delete every statement of `pm-provider-ci` as a module of this repository (module entry in `pm-providers/pom.xml`, managed version in the root POM, the `pm.guard.provider-contract` text, `CLAUDE.md`, the READMEs).
5. **Remove the empty `pm-provider-ci` (operator decision, 2026-10-19).** After the move `pm-provider-ci` holds no class, and the move removes it: delete the module directory, its entries in `pm-providers/pom.xml`, the root POM and every dependent's POM, and the lines of the documents named in deliverable 4. The CI contract that module was named for has no work package; the package that builds it creates its module again and re-instates the row of the Module Structure Specification then. No stub, no placeholder `package-info.java` and no empty module is kept.
6. **The Module Structure Specification in `plan-marshall-documentation`.** `doc/specification/module-structure.adoc` lists `pm-provider-ci` in the module tree and in the rows of `pm-provider-github`, `pm-provider-gitlab` and `pm-provider-forgejo`, and states the dependency rules and the roadmap item of Milestone 6 with it. It is a specification that lives in the documentation repository, so this plan opens one pull request there that corrects it to the state after the move (the module `pm-provider-http` as the shared HTTP base of every provider adapter; `pm-provider-ci` reinstated by the package that builds the CI contract). Merging that pull request is the operator's; list it in the pull request body here.

**Done when:** `pm-provider-http` builds with the moved tests green and at least 80% coverage; no class of `de.planmarshall.provider.ci` remains; the directory `pm-providers/pm-provider-ci` and every reference to it as a module (POMs, enforcer executions, fixtures, `CLAUDE.md`, READMEs, specification parts) are gone; a provider adapter that depends on a sibling provider module fails the build and the existing fixtures still pass; the pull request in `plan-marshall-documentation` is open (the lane never edits outside this repository: it writes the exact correction of `module-structure.adoc` into the pull request body of this repository and asks the operator with the question tool how the documentation pull request is opened; the answer is reported, and a clause the lane could not meet itself is reported as not met as written rather than as met); the links check (R17, absolute paths of the sibling checkout) reports `BROKEN LINKS 0`, the run recorded in the pull request body; `verify`, `verify -Ppre-commit`, `verify -Pcoverage` and `verify -Pintegration-tests` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set:**

- Requirements: PM-IMPL-1, PM-TECH-2
- Specification: Module Structure § `pm-provider-http`, § Dependency Rules, § Codeberg and Forgejo (in `plan-marshall-documentation`, `doc/specification/module-structure.adoc`); Credentials & Enrolment § Native Confinement
- Watch: Module Structure (`PM-WATCH-MOD-*`, in `plan-marshall-documentation`)

## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: `pm-provider-ci` holds only the seven HTTP base classes (`CiHttpClient`, `CiEndpoint`, `CiResponse`, `CiResult`, `PagedResult`, `TokenSource`, `Json`) and three tests, and its `package-info.java` calls it "the CI contract's shared HTTP base over `cui-http`" — `pm-providers/pm-provider-ci/src` at 7756dfd
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-providers/ and the root pom.xml are unchanged since 99971f3 (git diff --stat empty at 4617865); the seven classes, three tests, the poms and the executions at pom.xml lines 253 and 278 read as claimed
- OBSERVED: `pm-provider-sonar` depends on `pm-provider-ci` (`pm-providers/pm-provider-sonar/pom.xml`) and `SonarProvider` exists in it; `pm-provider-github` and `pm-provider-gitlab` depend on `pm-provider-ci` and `pm-core` — poms at 7756dfd
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-providers/ and the root pom.xml are unchanged since 99971f3 (git diff --stat empty at 4617865); the seven classes, three tests, the poms and the executions at pom.xml lines 253 and 278 read as claimed
- OBSERVED: the root POM carries the enforcer executions `provider` and `provider-contract` with the skip properties `pm.guard.provider.skip` and `pm.guard.provider-contract.skip`, and their messages name `pm-provider-ci` — `pom.xml` lines 62-63 and the executions at lines 253 and 278 at 7756dfd
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-providers/ and the root pom.xml are unchanged since 99971f3 (git diff --stat empty at 4617865); the seven classes, three tests, the poms and the executions at pom.xml lines 253 and 278 read as claimed
- OBSERVED: the Module Structure Specification at plan-marshall-documentation `b8c61b3` lists `pm-provider-http` as a module of `pm-providers` and states the dependency rules of this spec; no file of `doc/plans/` at 4617865 names `pm-provider-http` — `git grep -il provider-http origin/main -- doc/plans` finds nothing
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/specification/module-structure.adoc at plan-marshall-documentation origin/main (b8c61b3) is unchanged since b8c61b3 (git diff --stat empty); the entries read as claimed; git grep -il provider-http HEAD -- doc/plans finds nothing at 4617865
- HYPOTHESIS: no other code than the three forge providers and `pm-provider-sonar` imports `de.planmarshall.provider.ci` — confirm/refute with `git grep -n 'provider.ci' -- '*.java'` over the repository (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- HYPOTHESIS: the three forge providers can drop their dependency on `pm-provider-ci` without losing a class they use — confirm/refute at the imports of `GitHubClient` and `GitLabClient` (verify-at-outline)
  - verdict: unverifiable | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis, verify-at-outline by the executing plan
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-providers/` or the root POM enforcer executions, re-read the Module Structure Specification § `pm-provider-http` and § Dependency Rules at `origin/main` of `plan-marshall-documentation`, and `git grep` the repository for `pm-provider-ci` and `provider.ci`; a refutation of any claim above loops back to re-scope.
  - verdict: corroborated | checked_at: 4617865ef6716b21e61b931f5d290a54d2eb5f91 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: no package file and no index row for this spec (a plan with no package file); the only open PR (ci pr list) is #71, a dependabot bump, which does not name it; the claims above were re-read individually

## Expected Surface

- HYPOTHESIS: `pm-providers/pm-provider-http/` — the new module
- OBSERVED: `pm-providers/pm-provider-ci/` — the seven classes and three tests move out and the module is deleted
- HYPOTHESIS: `pm-providers/pm-provider-sonar/pom.xml` — drops `pm-provider-ci`
- HYPOTHESIS: `pm-providers/pm-provider-github/` — pom and imports
- HYPOTHESIS: `pm-providers/pm-provider-gitlab/` — pom and imports
- OBSERVED: `pm-providers/pom.xml` — module entry
- OBSERVED: `pom.xml` — managed version of the new module and the enforcer executions `provider` and `provider-contract`
- HYPOTHESIS: `src/guard-controls/` — fixture projects of the changed rules
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/BuildGuardsIT.java` — the cases of the changed rules
- OBSERVED: `CLAUDE.md` — Dependency Rules and Build Commands
- HYPOTHESIS: `pm-providers/README.adoc` — module list
- HYPOTHESIS: `pm-providers/pm-provider-http/doc/developer/` and `pm-providers/pm-provider-http/doc/adr/` — developer documentation and ADRs of the moved package
- HYPOTHESIS: `doc/specification/credentials-and-enrolment/01-provider-credentials.adoc` — the places that name `pm-provider-ci` as the HTTP base
- OBSERVED: `doc/adr/002-cui-http-and-the-mock-web-server-for-http.adoc` — where it names the module
- OBSERVED: `doc/plans/README.adoc` — the row of `forgejo-provider` only, if its _Depends on_ entry names the HTTP base
- OBSERVED: `../plan-marshall-documentation/doc/specification/module-structure.adoc` — corrected in a pull request there (deliverable 6)

## Dependencies and Sequencing

- Depends on: nothing
- Runs beside PLAN-94 (`sonar-main-backlog`, running), which owns Sonar issues in `CiHttpClient.java`, `Json.java`, `GitHubClient.java`, `InstallationTokens.java` and `GitLabClient.java`, the very classes this plan moves (7 declared files shared). Moving a class with an open issue moves the issue: do not fix an issue of PLAN-94's list, merge the base branch (the lane's step 7) before the merge gate, and read what PLAN-94 changed in those files at the head of `main` first; if PLAN-94 has merged, move the fixed text.
- Overlaps with: computed by `corpus cross-check` at emit time; PLAN-49 `forgejo-provider` builds on the module this plan creates and is re-grounded after it lands
- Adjacent to: the Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp`, which may name the moved classes in its wiring (`git grep` there before the plan finishes and list what it needs in the pull request body as **Owed outside this repository**; the other repositories check themselves), and `plan-marshall-documentation`, where only the correction of `module-structure.adoc` (deliverable 6) goes, in a pull request there

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-86-provider-http-module.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
