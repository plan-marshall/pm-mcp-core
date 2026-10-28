# PLAN-108: The harness scan, the probes and the verification of an unknown harness

epic: pm-mcp-core-m2-m4
workstream: WS-03

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-108-machine-harness-verification.md` and is queued as one row file, `queue/PLAN-108.json`,
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

Implement the second part of the work package `machine-onboarding-unit`: the harness scan, the login and model probes and the verification of an unknown harness as confined jobs of the kind `probe` (D4), the usage coverage the verification records and the harness list shows, and the remainders of the closed usage package that landed in the package (D12). Serves: roadmap Milestone 7 and Milestone 14 (a harness the operator adds, verified under `verify_harness_profile`); UC-ONB-1 to UC-ONB-10. Where: pm-core (`ProfileValidator`, `VerificationRecord`), pm-runtime (`worker`: `ProbeWorkspace`, `DecoyServers`, `VerifyingBinaryCheck`). **It waits for PLAN-98**, whose first act lifts the refusals of `ProfileValidator`, and for PLAN-46 (the consent store with the kind `verify_harness_profile`). The credential-store aspect, the machine's settings and the automatic onboarding are PLAN-46's and PLAN-109's.

## Deliverables

The bullets of `doc/plans/machine-onboarding-unit.adoc` § What assigned to this plan, in the package's words (numbers are the package's).

4. The harness scan, the login and model probes, and the verification of an unknown harness, as confined jobs of the kind `probe`; the verification runs only from `harness.verify-profile` after `harness.add-consent` answered `yes` with the expected tokens of its stages shown, the kind `verify_harness_profile` never remembered and so asked before every verification, in a first run and in a reconfiguration alike, and the draft validated as a profile before anything runs; the profile fields they read. The harnesses are listed in the order of their `client_id`, every supported worker-eligible one preselected and the first of them the default; the same order serves `auto.run` and the first enrolled harness of `roles.default`. The verification records per usage figure what the harness reports (`reported`, `not_applicable`, `missing`, as `token-usage` specifies), and the harness list shows each harness's usage coverage for its version: tokens `complete`, `partial` with the figures missing, or `not_reported`. The step needs one usable harness: the last chosen one cannot be left out, and when none is found or every chosen one fails its probe the step goes to `harness.none` (an operator wait with `recheck` and `add`, naming each known harness's install hint, the onboarding resting there until the operator acts).
12. *The recorded runs, the repeated evaluation and the install-path handoff* (added to the package by PLAN-107, #215 `81c51719`). Three remainders of the closed usage package are owed here: the recorded output of each shipped harness and version, the turns of a warm worker included, as a parsing fixture with its expected row (Client Profiles § Tokens, Not Money); the repeated usage evaluation for every new harness and harness version before its figures are trusted (Evaluation Reference § Results, in `plan-marshall-documentation`); and the install path that hands a verification stage's `UsageResult` to the enrolment's `UsageReport`, whose `of` is the mapping boundary and whose field mapping the enrolment store specifies (Store Schemas § Harness Enrolment Store). The recorded runs and the repeated evaluation need a real harness run, which a lane may not be able to make: what cannot be recorded is named owed in the landing, never faked; the install path is `plan-marshall-mcp`'s, only the seam here.

**Done when** (the part of the package's acceptance that this plan owns): an unknown harness's verification runs only from `harness.verify-profile` after `harness.add-consent` answered `yes` with the expected tokens of its stages shown, is asked every time and never remembered, in a first run and in a reconfiguration alike, and the draft is validated as a profile before anything runs; a run with every chosen harness failing rests in `harness.none`; the harness list shows for each harness and version the usage coverage its verification recorded (and the start context where the usage stage recorded it, `harness_store_unreadable` shown instead of an empty map); the harnesses are listed and preselected in the order of their `client_id`. D12: the recorded output of each shipped harness and version is a parsing fixture with its expected row where a harness run was possible; what could not be recorded or evaluated is named owed in the landing. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`, `pm-runtime`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (`verify -Pintegration-tests -pl <module> -am`, only on `pm-core` and `pm-runtime`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names a deleted watch item (`PM-WATCH-CLIENT-13`, `-14`, `PM-WATCH-DISP-33`, `-40`, `-43`, `-48`, `-55`, `-64`, `-65`) is the sibling's own links, not a defect of this change.

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
- Verify-first clause: before scoping, check that the index no longer says `blocked`, that the use cases are agreed, and which of the packages this one restructures have shipped since; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: f4a1bd07f480bbf04c18545192e0690fbca03563 | by: pm-mcp-core-m2-m4/cleanup | rescoped: n/a | evidence: index row open, not blocked; no open PR names it; restructured packages as stated plus #185 and #190

## Carried From The Re-grounding Records (2026-10-25, 2026-10-26)

- **#187 (`f287eb50`)** — Client Profiles 01 § Readable Paths and § Profile Verification, Job Runtime 02 § Worker Read Set: the headless profile schema REQUIRES `headless.readable_paths` (home-relative, never the home directory or a credential location; the compiled lists of claude, opencode, codex are none and provisional), and a `paths` stage runs the base task with the worker read set and the draft's `readable_paths`: the task must fail to read a decoy placed in the home directory; `worker.eligible` needs `paths` passed. `ProfileValidator` does not know `readable_paths` (`job-confinement-and-environment` owns that) and `ProbeWorkspace` builds no decoy-in-home control. **Which job kind carries the `paths` stage is unspecified** (D4 says `probe`, which keeps the open read set): settle at the outline whether to add `job-confinement-and-environment` (open) as a dependency.
- **#185** — Store Schemas 11 § Unreadable Store: a `harnesses.json` whose entries lack `events`, `usage_report.figures` or `weights` is refused `harness_store_unreadable`; the harness list must show the refusal and enrolling several harnesses in a scan must not lose entries.
- **#174** — Client Profiles § Start Context and § Nothing of the Operator's Own: the harness list also shows `start_context` per class and model where the usage stage recorded it (unmeasured shown as such); the verification of an unknown harness runs the isolation stage with decoys at the real home directory; recording `start_context` in the usage report was `token-usage`'s (closed).
- **#190** — `ProfileWorkerEligibility` consults the record before every start; the supervisor, its gate (`EligibilityRefusal`) and the three verification controls (ADR `runtime-worker/007`) exist; **the stage runner is still missing**. `PM-WATCH-CLIENT-16` is present (`doc/implementation-watch/client-profiles.adoc`).
- **PLAN-97/98:** the endpoint harness (#214) and the `ProfileValidator` lift of PLAN-98 change the same validator; build on `main` after PLAN-98 has merged.
- **R12:** the Where line names `pm-runtime` (`RuntimeLogMessages`); the plan parameters list `WorkflowLogMessages` and `CoreLogMessages` too.

## Expected Surface

- OBSERVED: `doc/specification/client-profiles/01-profile-schema.adoc` — specification part; the implemented sections deleted
- OBSERVED: `doc/specification/store-schemas/11-enrolment-store.adoc` — specification part; harness_store_unreadable and start_context shown in the harness list
- OBSERVED: `doc/implementation-watch/client-profiles.adoc` — watch items of the verification (PM-WATCH-CLIENT-16)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` — readable_paths and the credential rule; the draft is validated as a profile (D4); shared with job-confinement-and-environment
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/VerificationRecord.java` — the verification record read for usage coverage and start_context (D4)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/ProbeWorkspace.java` — unknown-harness verification (D4); no decoy-in-home control yet (paths stage)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/DecoyServers.java` — the isolation stage with decoys at the real home directory (D4)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/VerifyingBinaryCheck.java` — verification control used by the unknown-harness verification (D4)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/profile/ProfileValidatorTest.java` — readable_paths rule of the profile validation
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/ProbeWorkspaceTest.java` — verification workspace extended for the paths stage
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/DecoyServersTest.java` — decoys at the real home directory
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/VerifyingBinaryCheckTest.java` — verification control test

## Dependencies and Sequencing

- Depends on: PLAN-46 (the consent store and its kind `verify_harness_profile`); PLAN-98 (`judgment-endpoint`: `ProfileValidator`, `doc/specification/store-schemas/11-enrolment-store.adoc` and `doc/implementation-watch/client-profiles.adoc` are shared, so it runs after PLAN-98 has merged); `token-usage` is spent (PLAN-107, #215).
- Blocks: PLAN-109 (the harness list in the settings and the automatic run).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-108-machine-harness-verification.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
