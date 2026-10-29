# Landing Analysis: PLAN-46 — The machine's stores and the credential-store aspect (`machine-onboarding-unit`, part 1 of 3)

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #233

> Landing record for PLAN-46 (runbook lane, harness not stated). No inbox message from the lane exists; every claim below is
> read from the merge commit `a7027600e298192c272173b39747ba9b961e1c66` (squash, merge queue, merged 2026-10-29T19:46:57Z,
> parent `13a1236c`), the PR read through `ci`/`gh` read verbs, the CI runs and a token-free SonarCloud query. The package is
> only PARTLY delivered: D3, D5, D9 shipped (engine/store half), D6 partial, D1 (umbrella) partial. The PR body itself says it is
> "the first part" and that the onboarding units are `plan-marshall-mcp`'s and remain owed.

## Deliverable Fidelity vs Spec

Evidence base: 63 files, +7419/-9, all in `pm-core` (`core.config` 22 new classes, `core.role.RoleSet`, `core.service.AuditEvent`,
`core.store.LockKey`/`StrictJson`), `pm-runtime` (`runtime.config` 7 new classes, `runtime.credentials` 2 new + `CredentialStoreMove`
+16, `RuntimeLogMessages` +37), 14 test classes, 5 ADRs, 3 developer guides, `doc/Traceability.adoc` +101, `doc/LogMessages.adoc` +4.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 umbrella: engine side of UC-ONB-1..10 for the named spec sections | PARTIAL (shipped-modified) | Engine/store/runtime half over fixtures only. No onboarding unit, no sub-workflow, no `RoleEnvironment` wiring, no endpoint. The four spec parts named in the Expected Surface (`credentials-and-enrolment/01`, `model-work/01`, `state-catalogue/19`, `store-schemas/12`) are NOT in the diff: implemented sections were not deleted (lane's stated reason: the parts also describe PLAN-108/109 and `plan-marshall-mcp` work). `doc/Traceability.adoc` was extended instead. |
| D3 consent store `consents.json`, closed kinds, consent-before-acting | shipped-as-specified (engine half) | `ConsentKind` (14 kinds incl. `check_credential_store`, `verify_providers`, `check_updates`, `run_checkout_scripts`, `verify_harness_profile`; remembered MACHINE/PROJECT/NEVER), `Consent`, `ConsentScript` (digest per script), `ConsentCodec`, `ConsentStore` (revision-checked, `locks/consents.lock`, remembered/withdrawn audited via new `AuditEvent.consentRemembered/Withdrawn` before the write), `ConsentGate` (yes / yes_remember / no / passed-without-question / `operatorAct()`). Tests `ConsentGateTest`, `ConsentStoreTest`, `ConsentCodecTest`; `CredentialStoreAspectTest.consentNo`, `ProviderVerificationTest.consentNo` prove `no` stays in the consent state. The rule is a Java gate; the states `<sub-workflow>.consent` are not written (plan-marshall-mcp). |
| D5 model catalogue, default, operator's role assignment, steward outside | shipped-as-specified (stores); steward partly by earlier code | `ModelCatalogueStore` (machine + project, drops an unenrolled harness), `RoleAssignmentsStore` (effort 3 states, escalation), `DefaultAssignment` (`default-assignment.json`), `RoleAssignmentProposal`, `RoleAssignmentValidation` (4 checks of `roles.validate`), `RoleSet.assignable()`. Steward: `RoleAssignmentProposalTest.stewardOutside`; the steward's resolution/exemption itself is #146 code, not re-written. NOT delivered here: the "machine-configuration writers" for the steward/settings (`MachineConfiguration` is untouched; `MachineConfigurationStore` writes the `providers` member only) — those are D8, PLAN-V2-43. |
| D6 credential-store aspect + provider defaults + verification | PARTIAL | `CredentialStoreAspect` (`store.detect/choose/consent/check/confirm-move/move/record` over `CredentialStoreChoice/Check/Move`; failed move keeps old store, `credential_store_failed`, `CredentialStoreAspectTest.moveFailed`). `ProviderVerification` over `ProviderProbe`/`ToolProbe` seams (9 tests) — `ci.whoami` is NOT real (no CI contract module). `runtime.config`: `Provider`, `ProviderVariant`, `ProviderDefault(s)`, `MachineConfigurationCodec/Store`, `MachineDefaultsValidation`. NOT delivered: the endpoint `POST /api/v1/config/credentials`, the `store.*` state files, the clients' views, host-tools enrolment. The verification result with its time cannot be stored (closed schema has no member; `ProviderDefault` records `available` only). |
| D9 revision rule of a concurrent change (machine settings here) | shipped-modified | `SettingsConflict` (code `conflict`, 409, revision + current value) raised by `ConsentStore`, `ModelCatalogueStore`, `RoleAssignmentsStore`, `MachineConfigurationStore`; tests `staleRevision`/`conflict` x4. Modified: a new exception type instead of extending `StoreRefusal.conflict` (ADR `core-config/001`). Project operator values (PLAN-V2-17) and a plan's own values (PLAN-V2-21) are not covered here, as the spec says. |

Done-when, clause by clause: no action without consent / `no` stays / typed credential needs none: met over fixtures. Steward
outside catalogue and assignment: met. Failed move keeps old store: met. Two changes against one revision: met; machine default
never checked against a repository: met (`MachineDefaultsValidationTest`). **Sonar clause: NOT met** (63 open issues, see below; the
PR body promised the result "see Residue" but the Residue does not report it). **Build: coverage NOT met** (operator chose to skip the
re-run after SIGKILL; green at `f18b08eb` only); `verify -Ppre-commit` only green as two split invocations; integration tests green
(pm-core 13, pm-runtime 30). Links: 18 broken, all reported sibling-side. The Done-when of the package as a whole is NOT met (by design, part 1).

Expected Surface comparison. Declared but untouched: the four specification parts; `core/role/MachineConfiguration.java`,
`ModelCatalogue.java`, `RoleAssignments.java` (read side only, as hypothesised). Declared and touched: `core/config/`, `core-config.adoc`
(new), `runtime/credentials/`. Undeclared: `core/role/RoleSet.java`, `core/service/AuditEvent.java`, `core/store/LockKey.java`
(+3 lock keys: consents, model-catalogue, role-assignments), `core/store/StrictJson.java`, `LockKeyTest`, new package `runtime/config/` (7
classes, ADR `runtime-config/001`: provider-named members barred from `pm-core` by its provider-neutrality guard), `RuntimeLogMessages`,
`doc/LogMessages.adoc`, `doc/Traceability.adoc`, both READMEs. `ProfileValidator`/`core/profile` and `HarnessEnrolmentStore` are NOT touched.

## Metrics and Anomalies

- Tokens: `unknown` (no inbox message, no metrics supplied). Duration: first commit 10:46Z, last 17:24Z, merged 19:46Z (about 9 h wall).
- Lane plan id: `unknown` — best candidate `46-machine-onboarding-unit` (worktree directory and branch `feature/46-machine-onboarding-unit`;
  no plan directory survives under `.plan/local/plans/`, only `NO_PLAN`). It is not the `rb-NN-` form of PLAN-35; confirm before stamping.
- Anomalies:
  1. Sonar: gate "OK" (`sonarqubecloud` comment, `project_status`) but `issues/search?pullRequest=233&resolved=false` lists **63 open issues**
     (45 MAJOR, 10 CRITICAL, 3 BLOCKER, 5 MINOR; all code smells): java:S5778 x38 (tests), S1192 x10, S1845 x3 (BLOCKER: field `HARNESS`
     vs `harness` in `RoleAssignmentValue`, method `code` vs field `CODE` in `SettingsConflict`), S6213 x2, S1068 x2 (unused `auditSink`, `clock`
     in `CredentialStoreAspect`), others x1; 37+14 in pm-core, 5+7 in pm-runtime. None dismissed. The spec's Sonar Done-when is unmet and the
     PR was reported ready regardless.
  2. CodeRabbit reviewed the head `24d04bd6` at 19:41:50Z with 13 inline findings (3 Major, 10 Minor) and merged 5 minutes later (19:46:57Z);
     all 13 threads still unresolved, no reply, no fix commit after it. Substantive ones: `ConsentGate` validate the device before the answer;
     `MachineConfigurationCodec` trailing JSON accepted and settings not validated before being preserved; `MachineConfigurationStore.writeProviders`
     stores `available` without a verified result; `ProviderDefault` accepts omitted variants for multi-variant providers; `CredentialStoreAspect`
     occupied test accounts and configuration read failures not mapped to `UNAVAILABLE`; `ProviderVerification` probe details not redacted
     (Security); `RuntimeLogMessages` warning asserts a wrong store state; two ADR scope/boundary corrections and a missing README row.
  3. Coverage not met at the head (operator decision, in the PR body); `verify -Ppre-commit` single run does not finish on the host.
  4. Spec parts not reduced; two spec inconsistencies reported by the lane (verification result has no schema member; `model-work/01` calls the
     assignment's harness/model optional while `store-schemas/12` requires them).
  5. The merge-queue head carried a `merge: origin/main` commit (17:24Z); the PR list shows only 8 commits, all `plan-marshall`-trailer commits.

## Routing and Merge Behavior

- Review: coderabbit COMMENTED once (13 unresolved threads, see above; check `CodeRabbit` SUCCESS); sourcery refused 3 times (diff over its 150,000-char
  limit); the operator-id account triggered both bots by comment. No human review, `review_decision: none`.
- CI/merge: squash-merged through the merge queue, `ci pr view`: `state: merged`, `merge_commit_sha` = `a7027600...`, base `main`. Latest PR run (19:20Z,
  `Maven Build` 16m, `Integration Tests`, `Dependency Review`) all green; 20 checks, `overall_status: success`. Five older **push-event** `Maven Build`
  runs on the feature branch failed (09:42, 10:51, 11:04, 12:00, 16:59Z), every time only in `build / sonar-build` with
  `Failed to get Quality Gate status - Organization is not allowed to access data from non main branches` (the push-run/Sonar race, R18; tests passed).
  The `pull_request` runs of 17:08Z and 19:20Z were green.
- Sonar: measured — quality gate OK, but 63 open issues (see Anomaly 1); `step.sonar-roundtrip.open_issue_count` of the lane is unknown (no message). The lane's
  PR body claims neither; its Done-when line stays unreported.
- Collisions: none with PLAN-105 (#230, landed just before) in the diff.

## Reconciliation Actions

- [ ] row `status` → `shipped` — `orchestrator queue --transition PLAN-46 --status shipped` (PARTLY delivered; package stays open, see Follow-Ups)
- [ ] row `pr` stamped — `orchestrator queue --set-row PLAN-46 --field pr --value #233`
- [ ] row `landing` stamped — `orchestrator queue --set-row PLAN-46 --field landing --value landings/PLAN-46.md`
- [ ] row `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-46 --field plan_marshall_plan_id --value 46-machine-onboarding-unit` (unconfirmed; `unknown` if not confirmed)
- [ ] epic.md narrative reconciled against the queue rows (PLAN-108/PLAN-109 now unblocked for their dependency on the consent store; in the new epic PLAN-V2-39/PLAN-V2-43)
- [ ] Open Defect: Sonar Done-when unmet at merge (63 open issues, 3 BLOCKER S1845) and 13 unresolved CodeRabbit findings (incl. the probe-detail redaction) merged unaddressed
- [ ] Open Defect: coverage clause unmet (`verify -Pcoverage` not run at head)
- [ ] Watch: owed units and residue (below); package file `doc/plans/machine-onboarding-unit.adoc` and its index row (`open`, depends on nothing) REMAIN; removed by the last part (PLAN-109 / PLAN-V2-43)
- [ ] resume anchor updated in `resume_anchor.md` — `manage-status update-field --field resume_anchor --store orchestrator`
- [ ] `queue-view.md` regenerated and committed — `orchestrator regenerate-view`

## Follow-Ups

Owner this repository (`pm-mcp-core`):
- A cleanup/continuation plan (recommend: stage ONE small continuation plan in the new epic, `machine-stores-residue`, not leave it to PLAN-V2-43) for: the 63 Sonar
  issues of `core.config`/`runtime.config`/`runtime.credentials` and the 13 CodeRabbit findings (esp. `ProviderVerification` redaction, `ConsentGate` device check,
  `MachineConfigurationCodec` trailing JSON/settings validation, `CredentialStoreAspect` UNAVAILABLE mapping and occupied accounts, the `RuntimeLogMessages` text, two ADR
  corrections, `pm-runtime/README.adoc` row); and a `verify -Pcoverage` run on the head.
- The two specification inconsistencies (verification result + time has no schema member; optional vs required assignment harness/model): operator/doc decision, fix in
  `store-schemas/12` and `model-work/01` (this repository).
- Reduction of the implemented sections of the four spec parts (R05) once the last part lands.
- `ProviderProbe` real implementation on `ci.whoami` — PLAN-V2-12 (`ci-contract-module`), then wire `ProviderVerification` to it.
- Machine settings writers (`diagrams.format`, documents mode, cleanup durations, `MachineConfiguration` writers), sub-workflows, automatic onboarding, plan axes, landing
  validation: PLAN-V2-43 (old PLAN-109). Harness scan/probes/verification: PLAN-V2-39 (old PLAN-108).
Owner `plan-marshall-mcp`: the onboarding units (`onboarding.pmwf`, aspect sub-workflows `store.*`, `catalogue.record`, `roles.write`, `providers.*`), the endpoint
`/api/v1/config/credentials` (the runtime side of `pm-operator config credentials`), the `RoleEnvironment` wiring in the assembly, host-tools enrolment; clients: provider
views. Owed to the milestone exit: concept/user documentation of the slice and the roadmap check-off (documentation repository).

## Changes For Plans Of The New Epic (`pm-mcp-core-m2-m4-v2`)

- PLAN-V2-10 (`production-registries-and-wiring`): `core-config.adoc` and `pm-core/doc/adr/core-config/001..003` now EXIST (it assumed "whichever merges first creates it"): add a section, ADRs start at 004; `core.config` is no longer package-info only (22 classes).
- PLAN-V2-17 (`project-onboarding-unit`): `ConsentStore` + `ConsentKind.RUN_CHECKOUT_SCRIPTS` (PROJECT) + `ConsentScript` digests exist; its claim "no consents.json code" and the "which store API joins the two writes" question are answered by `ConsentStore.grant(Consent, revision)`/`removeProject` (separate lock `consents`, so no joint transaction with `Enrolments.enrol`); D7 must use `SettingsConflict` for the project's values; `INSPECT_CHECKOUT`, `MODEL_COST` kinds are present.
- PLAN-V2-39 (`machine-harness-verification`): consent kinds `scan_harnesses`, `fetch_icons`, `probe_harness_login`, `probe_harness_models`, `verify_harness_profile` (NEVER remembered) exist; `ModelCatalogueStore`/`RoleAssignmentsStore` already drop an unenrolled harness; `ProfileValidator`/`core/profile` untouched, so no merge conflict, only PLAN-98's lifted refusals as before.
- PLAN-V2-43 (`machine-settings-and-axes`): D8 writers are NOT present: `MachineConfigurationStore` (in `pm-runtime`, `runtime.config`, not `core.config`) writes `providers` only and copies every other member through; settings need a new member writer there plus `diagrams.format`; D9's rule is `SettingsConflict`; it is the LAST of the three, so it removes the package file and index row and reduces the four spec parts.
- PLAN-V2-12 (`ci-contract-module`): `ProviderVerification.ProviderProbe` is the seam awaiting `ci.whoami`; its "shared file with PLAN-46" note is spent (spec part untouched).
- PLAN-V2-71 (`profile-skill-load-template-check`) and PLAN-V2-63 / PLAN-V2-52: PLAN-46 did NOT touch `ProfileValidator` or `core/profile`; the dependency on PLAN-46 for `ProfileValidator` is spent (remaining: PLAN-V2-39 and PLAN-V2-43).
- PLAN-V2-73 (`runtime-control`): consent kind `check_updates` (MACHINE) now exists in `ConsentKind`; its "no consent store exists" unresolvable is cleared.
- PLAN-V2-21 (`plan-and-epic-control`) and PLAN-V2-24 (`start-from-clients`): PLAN-46 is shipped (part 1), dependency on the stores met; the plan's own values use `SettingsConflict`; V2-24's note that `core/config` is package-info only is outdated.
- PLAN-V2-22 (`start-source-intake`), PLAN-V2-69 (`process-designer`): depend on PLAN-46 for the consent store (met) and the plan axes (not met, PLAN-V2-43).
- PLAN-V2-50 (`r05-reduction-role-sets`): `model-work/01` and `store-schemas/12` were not reduced by PLAN-46, so its "run after PLAN-46" condition is spent but the two inconsistencies above should be fixed with it.
- PLAN-V2-46 (`runtime-endpoint-residues`), PLAN-V2-40, PLAN-V2-60: no effect (no class shared).
