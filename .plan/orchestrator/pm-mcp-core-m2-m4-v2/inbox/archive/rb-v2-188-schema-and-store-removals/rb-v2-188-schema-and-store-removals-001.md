envelope_version=1
sender_type=plan
sender_id=rb-v2-188-schema-and-store-removals
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-26T01:47:13Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-188-schema-and-store-removals
epic=pm-mcp-core-m2-m4-v2
pr=#621
merge_state=open
cleanup_owed=false
deliverables_total=3
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2183
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:pending,merge:pending
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced).

# PLAN-V2-188 — stopped before the merge on the required reviewer's quota

**Head:** `6b01b186` (branch `feature/v2-188-schema-and-store-removals`, PR #621, open). CI green, Sonar new-code clean (0 issues, 0 hotspots, gate OK), every build gate green. The run stopped at the required-reviewer step: CodeRabbit refuses on its free-OSS review quota with no qualifying review on record, so the merge gate's first condition is unmet and the run does not merge.

## Deliverables

| # | Deliverable | Verdict | Order | Test | Failing run | Commit |
|---|---|---|---|---|---|---|
| 1 | Schema-only removals and wording | **not met as written** — one sub-clause names `e451ed04`, which could not be run (see Residue); the removal substance is delivered and every check below holds | test-first + mutation-after | `RemovedSchemaMembersTest` (14 cases) | test-first at the branch base `c6986b48`: 13 failed, 1 passed (the positive-control case), log `f3196a6be36a41528e53e2c635eedcb7.log`; mutation at `e61036d6`: re-adding `assessment_id` and `python` turned 2 cases red, log `01f72ba530d7408391916ece7d379de6.log` | e61036d6, d19d0b80, f14a7156, 6b01b186 |
| 2 | The derived-fact kind `branch_up_to_date_required` | **not met as written** — same base-sha sub-clause; substance delivered | test-first + mutation-after | `DerivedFactsCodecTest.removedBranchUpToDateKindIsRefused`, `DerivedFactsSchemaParityTest` | test-first at `e61036d6`: "Expected StoreRefusal to be thrown, but nothing was thrown", log `92f65dc6a0274ba38600368f8302b17b.log`; mutation at `ae545935`: parity + codec control red, log `c03d0639b8a84b97b62bb9b122d8736f.log` | ae545935 |
| 3 | The "Unicode fixpoint" wording | **met** | none | `IngestionValidatorTest` (11 tests, green) | — | 1f8f5817 |

Clauses that are met independently of the base-sha ones: `DerivedFactKind` holds fifteen constants and `DerivedFactsSchemaParityTest` is green (same order, both sides derived from the enumeration); the fifteen other kinds read and write unchanged and `base_advanced`, `overlap_max_files` and `epic.waits_overlap` are still declared, pinned by the positive-control case; `verify -Ppre-commit` (12929 tests, log `61a16c812533424dbda7c3217fa40069.log`), full `verify` (log `9bee386f833040d691c0074c2d7c5aca.log`), `verify -Pcoverage` (log `2130cf7de81e48f3adc0368640aa4e80.log`), `verify -Pintegration-tests -pl pm-core -am` (21 tests, log `78413df298ef407a8c77f6f4593fdcbf.log`) and `verify -pl pm-core,pm-runtime -am` (8560 tests, log `e41e3fbf65ec4c6690bf3dc848193a59.log` at d19d0b80) are green; `package` over every module at the head is green (log `a0fc4735e08a455189f5e9b1f379bbba.log`). Per-head test runs: 6b01b186 (12929 + 12929), f14a7156 (12929 + 12929 + 21), d19d0b80 (69 + 8560), 1f8f5817 (12929 + 12929 + 12929 + 21).

The count `deliverables_done=1` is deliberately conservative: deliverables 1 and 2 are fully implemented and proved by a test-first red run at the branch's own base plus a mutation-after run on the committed fix, but neither was run at the literal `e451ed04` the spec names. Every artifact path above is a routed-build job log under `/home/oliver/.plan-marshall/marshalld/job-logs/`.

## Personas and skills

- Personas applied: `persona-implementer` for deliverables 1 and 2, `persona-documenter` for deliverable 3, `persona-code-reviewer` for the three self-review rounds and the PR-comment triage.
- Domain skills, per deliverable as the spec declares them: D1 `pm-dev-java:java-core`, `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`; D2 `pm-dev-java:java-core`, `pm-dev-java:javadoc`, `pm-dev-java-cui:cui-testing`; D3 `pm-dev-java:javadoc`, `pm-documents:ref-asciidoc`.
- Skills added for surface drift: none — the one undeclared file (`pm-workflow/doc/adr/workflow-unit/009-...`) is documentation, served by the already-loaded `pm-documents:ref-asciidoc` judgement.

## Hypotheses

- `EffectiveConfigKeysSchemaParityTest` and `RoleNameSingleDefinitionTest:87` do not pin `structural_caps` or `python` — **confirmed**: the parity test resolves the whole `review-bot-registry` document for `config-project.schema.json:79` (`target()` in `EffectiveConfigKeysSchemaParityTest`), and the role guard pins the skill-URI grammar in `NOT_COMPARABLE`.
- A negative control for a retained status member can only be schema-level — **confirmed**: `SchemaResource` exposes the loaded map, so the test asserts the member is absent and the enclosure's `additionalProperties: false`.
- `DerivedFactsCodecTest` has no case for a removed kind — **confirmed**: its kind refusals use `not_a_kind` (`:259-272`).

## Findings

- Round 1 (14): fixed the scope gap (`verify -pl pm-core,pm-runtime -am` run), the evidence paths (full log paths), the claim integrity of the D1 evidence, ADR 053/018 and `workflow-unit/009` stale counts, the status `change_type` cross-reference, a display-name typo, the absence control's vacuity and the Javadoc claim. Declined with reason: the index row's historical "27" (now carries an amendment note instead), folding the removed-kind codec control into `keyRefusals`, and moving the footprint-description test out of its nested class.
- Round 2 (8): fixed the ADR 018 codec-less count (nine, with the `plan-overrides` codec named), re-ran the three gates at the fixed head, corrected the progress wording, added the surviving-sibling witness and the two description positive controls, extended the class Javadoc. Recorded as not this branch's: parked PLAN-V2-03/38/41 still plan the removed queue-row and overlap members.
- Round 3 (1): the Javadoc enumeration omitted the quality-report case; fixed at 6b01b186 and verified by the test run. The fix landed at the round limit, so it was not re-reviewed by a fourth round.
- PR comments: CodeRabbit and Sourcery published only quota notices — no findings to triage. `review-gate` on the head: 0 unresolved threads, 0 body findings.

## Reviewers

- **coderabbit** (required, `quota_limited: true`, `review_complete_after_handling: true`): **refused**, four quota cycles, no qualifying review on record. Triggers and refusals: 19:40:39 → 19:40:47 ("Review limit reached"), 21:16:06 → 21:16:22 (after the 90-minute hold), 23:01:29 → 23:01:47 (the policy's one escalation, `@coderabbitai full review`), 00:31 → 00:28:27, 01:45 → 01:45:39. `ci pr wait-for-comments` polls recorded `coderabbit awaitable_window rate_limited` throughout; the bot's own ETA (6 minutes, then 39 minutes) was never used, the lane's 90-minute floor was. Standing review authorization not available — it needs a qualifying review, and none exists.
- **sourcery** (optional): its own budget notice (250 000 diff characters, next in 6 h 30 min). Not triggered by the run; does not gate.

## Operator questions

none asked. The reviewer-quota policy forbids asking before its steps are spent, and the run stopped under the policy's own "stop the session" option rather than asking a rule-bending permission.

## Rule check

- Guard: `resolve` reported `guard: none`, so this harness enforced no tool-call rule. `guard: none on this harness (unenforced)`; the counts below are the run's own.
- Shell/executor: every script through `python3 .plan/execute-script.py …` from the main checkout, every build through the resolved `build-maven` canonical with `--project-dir`, every CI action through `plan-marshall:tools-integration-ci:ci`. Reads and edits through the file tools. One shell file operation outside that rule: `rm` of the deleted schema file after `git rm --cached` had already staged its deletion (a deliberate delete, no read). Two `find`/`ls` probes were used to diagnose the base tree's missing executor — both read-only and inside the repository.
- Git: one commit per deliverable plus three self-review fix commits and one Javadoc fix; nothing staged wholesale; no stash, restore, rebase or forced push; the base branch was not merged in (it never moved).
- `.plan/` state: only `.plan/temp/` (progress + landing) and the `ci` body slots the `prepare-*` verbs returned.
- Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker (both copies agree).
- executor-skew: `match` (executor 0.1.2183, lane 0.1.2183) — not a source checkout, so the verdict is a real comparison, not `not_applicable`.

## Build server

Setup preflight `ready` (enrolled, version 1); `build-routing` answered `auto` with `executor_in_worktree: present`. Every build of the run was routed: `daemon_longpoll` 26 calls (including the re-attaches of long jobs inside the same call), `in_process_fallback` 0. Fallbacks: none — no `degraded`, no `executor_absent_from_worktree`, no launch-fault retry, no `killed` job. `runtime_stamp_reason`: none.

## Residue (what the next session must do)

1. **The required review.** CodeRabbit must review `6b01b186`. The next trigger may be posted no earlier than 90 minutes after the last refusal (`01:45:39Z`), i.e. from **`03:15:39Z` on 2026-11-26**; cycles 5 to 10 of §6 step 7 remain. The quota is shared with the epic's other running plans, which is why four 90-minute windows all refused.
2. **After the review:** handle every finding (fix or reply with a reason), re-run `review-gate`, then the merge gate and `ci pr merge-queue` — nothing else is outstanding. The worktree `.plan/local/worktrees/v2-188-schema-and-store-removals`, the branch and the PR are all left in place, and the progress file resumes at the reviewer step.
3. **Base-sha evidence.** The detached base tree at `e451ed04` reports `executor_in_worktree: absent` in this checkout (generator invoked with `--marketplace-root` on a tree without `marketplace/bundles`, and `generate_executor.main` returns 0 regardless), so the two "fails at `e451ed04`" sub-clauses are unproved as written. This is the epic's recurring gap (PLAN-V2-82, -152, -153, -185); fixing it belongs to the marketplace's executor provisioning, not to this plan.
4. **Orchestrator state.** Parked PLAN-V2-03, PLAN-V2-38 and PLAN-V2-41 still plan or bless `regrounded_at_sha`, `reground_verdict`, a queue-row `overlap_found` and `epic.overlap_found`; they need re-grounding when revived. PLAN-V2-195 covers the package prose only.
