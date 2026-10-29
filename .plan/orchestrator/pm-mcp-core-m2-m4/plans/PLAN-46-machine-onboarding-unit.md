# PLAN-46: The machine's stores and the credential-store aspect

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-46-machine-onboarding-unit.md` and is queued as one row file, `queue/PLAN-46.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> **Split from PLAN-46 on 2026-10-28** (operator: "Split it first, emit the first part", after PLAN-107 closed the package `token-usage`, #215 `81c51719`): the package `machine-onboarding-unit` is worked by **PLAN-46** (the machine's stores and the credential-store aspect), **PLAN-108** (the harness scan, the probes and the verification of an unknown harness, the usage coverage and the remainders of the usage package) and **PLAN-109** (the machine's settings, the automatic onboarding, the sub-workflows, the plan axes and the landing validation). The bullets keep the numbers of the package file (D1 to D12). The package file `doc/plans/machine-onboarding-unit.adoc` and its index row are removed by the **last** of the three to land (R01): PLAN-109; the other two leave them in place. Why: the spec carried 11 deliverables, the package gained a twelfth, and PLAN-43 stalled twice on size.
> **State on 2026-10-28:** the dependency on `token-usage` is spent (closed by PLAN-107, #215) and `operator-wait-and-machine-scope` is shipped (PLAN-42, #199/#203): the package depends on nothing. PLAN-98 (`judgment-endpoint`, running) lifts the refusals of `ProfileValidator` (the remote form, `system_one`): the plan that changes `ProfileValidator` waits for it. The claim verdicts below were stamped at `f4a1bd07`, before the closure; re-read them at the outline. The onboarding's default units (`onboarding.pmwf`, the aspect sub-workflows, `landing.*`, `method.*`, `ask.*`) are `plan-marshall-mcp` resources on its classpath, not units of `pm-workflow`: on the same footing as PLAN-43 (B2/B3, 2026-10-27) this repository builds the engine, store and runtime half and proves the Done-when over fixtures, and the landing names the real units as owed.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R15, R16, R17, R18, R19.

Plan parameters (the values the rules ask for):

- R06 — developer documentation: the developer file of each package it changes (`{module}/doc/developer/{package}.adoc`); ADR directory: `{module}/doc/adr/{package}/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); `CoreLogMessages` (`de.planmarshall.core.log`, prefix `PM_CORE`).

## Objective

Implement the first part of the work package `machine-onboarding-unit` (`doc/plans/machine-onboarding-unit.adoc`): the stores the onboarding writes and the aspects that depend on them alone. The consent store `consents.json` and the consent-before-acting rule (D3), the model catalogue, the operator's role assignment and the steward role with the machine-configuration writers (D5), the credential-store aspect with the machine's provider defaults and their verification (D6), and the revision rule of a concurrent change of a setting (D9). Serves: roadmap Milestone 7 (the machine scope and its onboarding); Use Cases UC-ONB-1 to UC-ONB-10. Where: pm-core (the consent, catalogue, steward, role and machine-configuration stores and their writers, `core/config`); pm-runtime (the delivered credential store: `CredentialStoreChoice`, `CredentialStoreMove`, the provider verification). The harness scan and the verification of an unknown harness are PLAN-108's; the machine's settings, the automatic onboarding, the sub-workflows, the plan axes and the landing validation are PLAN-109's.

**Why the credential-store aspect is on the critical path (analysed 2026-10-19 from plan-marshall-mcp #88 and #90).** The daemon serves only the store recorded in `credentials.store` of `machine-config.json` and refuses every credential operation until one is recorded: `CredentialsResource` answers `409 {"error":"onboarding_incomplete"}` (proven by `KeyringIT.refusesBeforeStoreChoice` against the packaged daemon). The only legitimate writer is this package's credential-store aspect, through `CredentialStoreChoice.record(LockManager, user, Store)` of `pm-runtime`. At `91b60d5` of `plan-marshall-mcp` and `ce43548` of this repository no production code calls it, so a fresh install cannot enter a credential until this aspect exists. The aspect is not separable from the machine scope: its states run in the machine-scope sub-workflows (`operator-wait-and-machine-scope`). The operator verb already exists and is not part of this spec: `pm-operator config credentials --store os|file` is implemented in `pm-mcp-clients` (`ConfigCommand.CredentialsCommand`) and designed in its terminal-client design (§ A Later Change Moves the Tokens), with `pm-operator credentials migrate os|file` named by the specification for the move; what no repository provides is the runtime side the verb talks to (the verb posts to `/api/v1/config/credentials`, which neither this repository, `plan-marshall-mcp` nor the specifications define). This package's aspect is that runtime side; the client's call path is the clients' to check.

## Deliverables

The bullets of `doc/plans/machine-onboarding-unit.adoc` § What assigned to this plan, in the package's words (numbers are the package's).

1. Implements the engine side of UC-ONB-1 to UC-ONB-10 (`PM-WF-26`, `PM-WF-25`, `PM-SEC-8`): State Catalogue § Machine Onboarding and § Onboarding Aspects; Store Schemas § Consent Before Acting, § Model Catalogue, § Machine Provider Defaults, § Machine Configuration Store; Client Profiles § Harness Scan, § Login Probe, § Unknown Harness, § Harness Icons; Model Work § Role Assignment, § Default Role Assignment, § Steward Role; Credentials & Enrolment § Credential Store as an Operator Choice, § Provider Verification, § Automatic Onboarding, § Concurrent Changes of a Setting; Workflow DSL § Plan Behaviour Axes, § Plan Overrides. The client side is `onboarding-contract` of `pm-mcp-clients`.
3. The consent store `consents.json` and its closed list of kinds, among them `check_credential_store` (asked in `store.consent`, only for an OS store; the file store is checked without one) and `verify_providers` (asked in `providers.verify-consent`); a project's kind with its project, and `run_checkout_scripts` with the digest of each script, in this store only, never in an enrolment; the consent state before every action: each action state is reached only through its consent state (`<sub-workflow>.consent` or `<sub-workflow>.<action>-consent`) answered `yes` or `yes_remember`, or passed without a question where the kind is remembered in the store, and `no` records nothing and leaves the scope in the consent state, an optional action going on without the action; an act of the operator's own, a credential the operator types, stored without a consent.
5. The model catalogue, the default and the operator's role assignment; the steward role outside both: not among the roles `roles.load` lists, always on the default harness with that harness's default model, outside the catalogue (`model_not_allowed` never applies to it), exempt from qualification, and its submits.
6. The credential-store aspect (`store.choose`, `store.consent`, `store.check`, `store.confirm-move`, `store.move`), a failed move going to `store.unavailable` with `credential_store_failed` and the old store kept, over the delivered store of `pm-runtime` (`CredentialStoreChoice`, which records the operator's choice, and `CredentialStoreMove`, which moves the entries; the automatic selection is removed); the machine's provider defaults and their verification.
9. Concurrent changes of a setting (CLI & Security § Concurrent Changes of a Setting): every change of a recorded value, of the machine's settings here, of a project's operator values in `project-onboarding-unit`, and of a plan's own values in `plan-and-epic-control`, carries the revision of the settings it was made against; the first change wins, a later one against an older revision is refused with `conflict` (`409`) and the current value, which the operator may change again on top of it; nothing is merged and nothing is overwritten unseen.

**Done when** (the part of the package's acceptance that this plan owns): no action runs without its consent, the store check and the provider verification included, a `no` leaving the scope in its consent state with no job started, and a typed credential is stored without one; the steward runs outside the catalogue and the assignment; a failed store move keeps the old store and reports `credential_store_failed`; of two changes of a setting made against the same revision the first is recorded and the second refused with `conflict` and the current value, and the machine default is never checked against a repository. (D1 is the umbrella the other plans share: here it covers the sections the bullets above name.) **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-runtime`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names a deleted watch item (`PM-WATCH-CLIENT-13`, `-14`, `PM-WATCH-DISP-33`, `-40`, `-43`, `-48`, `-55`, `-64`, `-65`) is the sibling's own links, not a defect of this change.

**Trace starting set** (the requirements, use cases and roadmap live in `../plan-marshall-documentation`; the specifications and watch documents of the engine in this repository, `doc/specification/` and `doc/implementation-watch/`):

- Use cases: UC-ONB-1 to UC-ONB-10
- Requirements: PM-WF-26; PM-WF-25; PM-SEC-8; PM-CRED-2; PM-WORK-1
- Specification: the sections the first bullet of _What_ names; Workflow DSL § Machine Scope; § Plan Instance; Model Work § Roles & Configuration; Store Schemas § Machine Stores; Client Profiles § Profile Schema


## Standing Constraints

None beyond the standing rules named in the First Instruction above.

## Claim Labels

- OBSERVED: the Objective, Deliverables, Done when and Trace above are the package file's own — read at `doc/plans/machine-onboarding-unit.adoc` § What, Trace, Done When on `origin/main` at `4617865` (11 bullets; the spec is regenerated from it by cleanup on 2026-10-22)
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: doc/plans/machine-onboarding-unit.adoc unchanged since 88ec9c70; 11 What bullets
- OBSERVED: the package file and its index row (`open`; depends on PLAN-42 (`operator-wait-and-machine-scope`); PLAN-61 (`token-usage`) (the usage record of the verification)) — read at `doc/plans/machine-onboarding-unit.adoc` and `doc/plans/README.adoc` on `origin/main` at `4617865`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row open, depends on operator-wait-and-machine-scope and token-usage; token-usage open (now depends only on role-qualification)
- HYPOTHESIS: the Expected Surface below is inferred from the `Where` line only — confirm/refute at the trace matrix (verify-at-outline)
  - verdict: unverifiable | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: hypothesis: landed code touched only core/store, core/profile, runtime/worker, runtime/log, runtime/job (one case); core/config still package-info only; no consents, onboarding or settings code
- OBSERVED: no production class of `plan-marshall-mcp` at `38bd66d` calls `CredentialStoreChoice.record`, and `CredentialStoreChoice.record(LockManager, String, Store)` exists in `pm-runtime` — `grep -rn 'CredentialStoreChoice.*record\|\.record(' pm-mcp-server/src/main` finds nothing; `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/CredentialStoreChoice.java` line 457
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: CredentialStoreChoice.record(LockManager, String, Store) at pm-runtime credentials/CredentialStoreChoice.java:457; the assembly only reads recorded() and activeStore()
- OBSERVED: the operator verb that records the store already exists in `pm-mcp-clients` and no repository serves its path — `pm-operator/src/main/java/de/planmarshall/operator/ConfigCommand.java` line 634 (`POST /api/v1/config/credentials`) at `735713b`; `git grep config/credentials` finds a hit only in that file and its test, none in this repository, `plan-marshall-mcp` or `plan-marshall-documentation`
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: pm-mcp-clients origin/main: pm-operator ConfigCommand posts /api/v1/config/credentials with a test; no hit in pm-mcp-core, plan-marshall-mcp or the documentation
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row open, not blocked; no open PR names it; restructured packages as stated plus #185 and #190

## Carried From The Re-grounding Records (2026-10-25, 2026-10-26)

- **Provider verification** checks `ci.whoami` (Credentials & Enrolment § Provider Verification) but the CI contract has no module (`pm-providers/pom.xml` lists no `pm-provider-ci`; the directory is untracked IDE residue) and no package builds it: settle at the outline how the verification is specified against it (a seam, named owed in the landing).
- **#181** — Store Schemas 12: the machine-config member `diagrams.format` (default `text`) is a new member the writers must keep; no aspect writes it (PLAN-109 decides whether `settings.choose` or the language aspect records it).
- **#185** — `StoreRefusal` `CONFLICT` and the revision pattern are the model for D9; `StoreRefusal.conflict` has no current-value field.
- Read side of roles, catalogue, steward and machine config shipped by #146 (`ModelCatalogue`, `RoleAssignments`, `RoleAssignment`, `StewardConfiguration.resolve`, `MachineConfiguration`, `RoleEnvironment`); missing: the writers and `consents.json`. The credential store, its check and its move are shipped (`CredentialStoreChoice.record`, `CredentialStoreCheck`, `CredentialStoreMove`); only the aspect states and the endpoint `/api/v1/config/credentials` (`plan-marshall-mcp`) are missing.
- **R12:** the plan parameters list `WorkflowLogMessages` and `CoreLogMessages`; the Where line also names `pm-runtime` (`RuntimeLogMessages`).

## Expected Surface

- OBSERVED: `doc/specification/credentials-and-enrolment/01-provider-credentials.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/model-work/01-roles-and-configuration.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/state-catalogue/19-onboarding-aspects-models-providers.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/12-machine-stores.adoc` — specification part; the implemented sections deleted
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/MachineConfiguration.java` — read side shipped (#146), the machine config writers (settings, documents mode, cleanup durations) are missing
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/ModelCatalogue.java` — read side shipped (#146), the catalogue writer is missing (D5)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/role/RoleAssignments.java` — read side shipped (#146), the operator's role assignment writer is missing (D5)
- HYPOTHESIS: `pm-core/src/main/java/de/planmarshall/core/config/` — the machine configuration store (package-info only today)
- HYPOTHESIS: `pm-core/doc/developer/core-config.adoc` — to be created; developer guide of the new config package
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/` — the provider verification and any change to the delivered credential store (`CredentialStoreChoice`, `CredentialStoreMove`); read first, the store is shipped

## Dependencies and Sequencing

- Depends on: PLAN-42 (`operator-wait-and-machine-scope`, shipped #199/#203); `token-usage` is spent (PLAN-107, #215).
- Overlaps with: PLAN-98 (`judgment-endpoint`) on `doc/specification/credentials-and-enrolment/01-provider-credentials.adoc` (PLAN-98 edits its `credential_key` entry) and on the directory `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/` (PLAN-98's remote form reads the token lane; this plan's provider verification may add classes there). The code files `ProfileValidator.java` and its test went to PLAN-108 to keep the rest disjoint. Sequenced after PLAN-98 (operator, 2026-10-28, "Wait for PLAN-98 to land first"): **PLAN-98 MERGED (#222 `d4d25e2a`, 2026-10-28), the overlap is spent and this plan is ready to emit.** `ProfileValidator` now carries PLAN-98's lifted refusals (the remote form, `system_one`); re-read it at the outline.
- Blocks: PLAN-108, PLAN-109 (and through them the package's dependents PLAN-45, 48, 51, 59, 62, 63, 76).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-46-machine-onboarding-unit.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
