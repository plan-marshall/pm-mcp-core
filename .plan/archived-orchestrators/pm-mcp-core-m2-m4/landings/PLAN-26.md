# Landing Analysis: PLAN-26 — Role artifacts, role sets and the one model caller

epic: pm-mcp-core-m2-m4
workstream: WS-05
pr: #146 (merged as 964f673d163cc903d88bd2a490120cb6b5dc8c88)

> Landing record for one shipped plan. Source: inbox message `rb-26-role-sets-and-model-caller-001.md`
> (runbook lane, opencode, 2026-10-23T10:51:16Z), corroborated against git at `964f673`, the PR and its 30
> checks through the `ci` abstraction, the SonarCloud pull-request API (plain HTTP, read only) and one run of
> the links check at the current `main`. Facts marked "checked" were read from those; the rest is the lane's report.

Standing rules: r1, R01 to R13, R15 and R18 named by the landing; R17 is "not in the plan's list and not run". The lane started on the
unadapted spec (see the PLAN-25 landing, § Process finding), which is why R17 and the build clauses of the adapted text were not in front of it.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — role artifact schema, shipped/machine/project sets, validation against the profiles, composition checks | shipped-as-specified (lane's tests not re-run) | Checked: `core.role` gains `RoleArtifact`, `RoleArtifactValidator` (+417), `RoleSet`, `RoleSets`, `RoleSetLoader`, `RoleClass`, `RoleForm`, `HarnessProfile` and the rest of 26 main classes in all; `RoleValidationReason` has 10 constants, matching the lane's "all ten reasons". |
| D2 — effective configuration over six levels, both digests, read at every composition, task creation and offer | shipped-as-specified (lane's tests) | Checked: `EffectiveRole`, `RoleResolution`, `RoleDigests`, `RoleAssignments`, ports for the stores; `RoleEffectiveConfigurationTest` +325 lines, `RoleDigestTest` +169. The lane's mutation-after failing run is quoted, not re-run. |
| D3 — validation against the effective values (`model_not_allowed`, `session_first_not_permitted`, `schema_invalid`) | shipped-as-specified (lane's tests) | Checked: `RoleValidationTableTest` +233, `RoleArtifactValidatorTest` +211. |
| D4 — the one model caller (`job llm` to queue tasks; `judge` and `steward` skip Qualify; steward from `harnesses.default`) | shipped-as-specified | Checked: `pm-workflow` `workflow.model` gains `ModelCaller`, `ModelQueue`, `RoleComposition`, `QualificationLookup`; the `ModelCaller` Javadoc states that a task of the `judge` or the `steward` skips Qualify, and it holds a separate steward resolver taken from the machine. `ModelCallerTest` +237. |
| D5 — the shipped set read from `classpath:/roles/`, tests use fixture roles | shipped-as-specified | Checked: `ShippedRoles` +135, `ShippedRolesTest`; test resources `roles/{default-assignment,interpret,steward}.json` under `src/test`. |

All Done-when clauses as the lane read them are reported met, five with `order` test-first or mutation-after and the Sonar clause `none`. Checked on the
merge: ADRs `core-role/001`, `002` and `workflow-model/001`, the developer guides `core-role.adoc` (+194) and `workflow-model.adoc` (+95), 98 new lines of
`doc/Traceability.adoc`, the package file `role-sets-and-model-caller.adoc` deleted with its index row. Checked: CI `overall_status: success` over 30 checks;
SonarCloud for #146 lists 0 open issues, the gate reads `OK`, new coverage 88.6 (threshold 80). The lane's per-head test counts (7031 to 7093) are its report.

**Not carried (the lane's own statement, checked in the diff):** the implemented sections of `model-work/01-roles-and-configuration.adoc`,
`client-profiles/01-profile-schema.adoc` and `store-schemas/12-machine-stores.adoc`, and the covered items of `implementation-watch/model-work.adoc` and
`client-profiles.adoc`, are untouched. The lane gives two reasons: more than 100 link sites from other specification parts anchor them, and `model-work/01`
still holds the escalation assignment, the shipped content and the qualification text that this package does not implement. This is the second plan in a
row (PLAN-25 kept its specification rows for the same kind of reason) that does not carry the "implemented sections deleted" instruction of its Expected Surface.

## Metrics and Anomalies

- Tokens: unknown (`missing_keys: [total_tokens]`); `plugin_version` 0.1.1989 at landing against 0.1.1986 at setup (the environment was updated during the run).
- Self-review: `budget-exhausted` after three rounds (14, 11 and 3 findings, all fixed by the lane); you chose to proceed with three open items reported.
- Footprint (checked, `surface_delta`: `expansion_detected`, 15 declared, 63 realized, 9 added, 5 missing): added `doc/Traceability.adoc`, five package files whose links were re-pointed (`consultation`, `plan-instance-versions`, `question-continuation`, `role-qualification`, `worker-launch`), and the three test role resources; missing are the five specification and watch documents above. **No `pm-runtime` file is in the merge**: the lane reverted a pre-commit rewrite there (`7fc49ef`).
- Sonar: the first pull-request run was red (`new_reliability_rating` 3, three BUGs, 49 code smells), fixed or suppressed with stated reasons; final 0 issues.
- Build: setup preflight `ready` (the project-root resolve said `down`); every build in process after `executor_absent_from_worktree` (the fourth lane); one run hit the 617 s bound after 5387 passing tests, re-run at 1200 s in 396 s.
- Rule deviations disclosed by the lane (guard: none on this harness): the implementation files were first written in the main checkout and moved into the worktree before any commit (one build ran against the main checkout); the revert commit `7fc49ef` lacks the `Co-Authored-By: plan-marshall` trailer and cannot be amended without a force-push the lane bans.
- Out-of-lane: the links check was not run by the lane. Run here at `main` `905fb5d`: `BROKEN LINKS 0` (two `NO BACKLINK` notes for `PM-WATCH-CLIENT-13` and `-15`, not attributed to this change).

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `7cb1dbd` (10 findings and a nit, fixed in `acda501`) and `acda501` (2 findings, fixed in `0e0d44b`); two answered with reasons and resolved (the configuration digest's scope as the specification words it; the ordering of an unlisted effort, which the specification does not place). The head `2724d34` was not reviewed: the re-trigger after the last push was refused `no_unreviewed_commit`. Merged under the standing review authorisation. Sourcery refused on size (150,000-character limit).
- CI/merge: checked, #146 reads `merged`, head `feature/26-role-sets-and-model-caller`, merge commit `964f673`, first parent `ac34703` (#150, merged earlier the same day; the lane merged the base). No dequeue.
- Collisions: the footprint touches the declared surface of PLAN-34 (54 files), PLAN-73 (40), PLAN-61 and PLAN-99 (27 each, through the directory `core/role/`) and the index of every plan; PLAN-40 (2 developer guides) is the live one among them.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-26 --status shipped`
- [x] row `pr` stamped — `#146`
- [x] row `landing` stamped — `landings/PLAN-26.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-26-role-sets-and-model-caller` (from the sender)
- [x] epic.md narrative reconciled: queue annotation, `total_tokens` recurrence, the bookkeeping Open Defect widened to two plans, specification questions from the lane
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **PLAN-26 shipped: PLAN-28 (`task-queue-and-protocol`) and PLAN-71 (`plan-instance-versions`) have no unmet dependency left**; PLAN-29, 34 and 97 still wait on others.
- **Specification and watch deletions (two plans now):** decide whether the "implemented sections deleted / covered items deleted" instruction stands for a package whose specification part is anchored from many places, or becomes "mark implemented, link the developer guide", as the lane did. Until decided, later specs repeat the instruction and lanes deviate with a reason.
- **Specification questions the lane raises (unverified by this verb):** the specification does not place an unlisted effort above the range of `headless.model_efforts`; the configuration digest covers the effective artifact with only harness, model and effort replaced, so a profile change that keeps the harness `major.minor` can change derived budgets without changing the digest (the reviewer withdrew it as outside the contract). `PM-WATCH-DISP-49` stays (it needs the assembly's shipped role set), `PM-WATCH-DISP-57` stays with PLAN-34.
- **Owed to the milestone exit**, in `plan-marshall-documentation`: the concept and user documentation of the slice and the Milestone 4 roadmap check-off (existing Watch).
