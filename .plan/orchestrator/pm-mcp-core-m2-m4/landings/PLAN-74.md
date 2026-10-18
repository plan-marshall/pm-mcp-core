# Landing Analysis: PLAN-74 — The credential store as the operator's choice

epic: pm-mcp-core-m2-m4
workstream: WS-01
pr: #90 (https://github.com/plan-marshall/pm-mcp-core/pull/90), merged through the merge queue as squash `44f904e`, 2026-10-18

> Landing record for one shipped plan. Lives at `landings/PLAN-74.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `plan-74-credential-store-choice-036.md` (`kind: landing`), corroborated by the orchestrator against `ci pr view 90`, `git show --stat 44f904e`, the spec's Expected Surface and a `git grep` of `plan-marshall-mcp` `origin/main`. The plan id is `plan-74-credential-store-choice`.

## Deliverable Fidelity vs Spec

The spec carries seven bullets of `doc/plans/credential-store-choice.adoc` § What; the plan counts ten (`deliverables_total=10`, 10 done). `functional-review`: 8 met, 2 met with residuals, 0 not met. The orchestrator checked what the merged tree can show; the per-deliverable verdicts are the plan's.

| Spec bullet | Verdict | Evidence |
|-------------|---------|----------|
| 1. The three stores behind `SecretStore` | shipped-as-specified | 23 files under `pm-runtime/src/main`, among them `CredentialStoreChoice`, `CredentialStoreMoveRecord`, `IntegrityKeyCarry`, `CredentialStoreMoveResume` |
| 2. The recorded store; `SecretStoreSelector` and its fallback go | shipped-as-specified | `SecretStoreSelector` is gone from `pm-runtime` (and still imported by `pm-mcp-server`, below) |
| 3. Recording the choice under the machine-config lock, refused for every other writer | shipped-modified | `CredentialStoreChoice.record(LockManager, user, store)`; the refusal while a move record exists took four review rounds (lessons 006, 008, 014) |
| 4. The integrity key before the choice | shipped-modified | a transient OS outage could flip the key's store (lesson 002); the first-recording carry looks only at the store the instance found (advisory, below) |
| 5. The move in either direction, read back, resumable | shipped-modified | the resume rules were rewritten three times (lessons 001, 010, 011, 012, 013, 015); nine ADRs under `pm-runtime/doc/adr` |
| 6. Tests per OS | shipped-as-specified, unproven on this host | the Keychain and Secret Service paths are `@EnabledOnOs` and unrun on the Linux development host (the plan states it; a macOS run or the native build of `pm-mcpd` proves them) |
| 7. The assembly: `SecretStoreProducer` in `plan-marshall-mcp` | owed, outside this plan | verified: `pm-mcp-server/.../SecretStoreProducer.java` on `plan-marshall-mcp` `origin/main` still imports `de.planmarshall.runtime.credentials.SecretStoreSelector` and calls `SecretStoreSelector.select()` |

## Metrics and Anomalies

- Tokens: 15,213,155 (plan-reported); wall clock 71,198 s (about 19 h 47 min). The largest run of the epic, ahead of PLAN-69 (10.5M tokens) and PLAN-72 and PLAN-75 (about 3.9M each). Not re-measurable by the orchestrator.
- Anomalies, all plan-reported: four code-review rounds each found another unguarded transition of the move state machine, and an external bot (Sourcery) found a lost-data defect (lesson 011) that four internal rounds passed; a delta code-review round loaded 6 of 22 rule-source skills (lesson 026); a two-line Sonar fix re-fired both full reviews (lesson 028); the Sourcery budget refusal forced an operator question on every head (lesson 027).

## Routing and Merge Behavior

- Review: functional review as above; code review `blocking_count=0`, `advisory_count=8`. The review-bot step for the last head was closed by the operator with Sourcery's re-review refused (budget); cuioss-review-bot and CodeRabbit reviewed with no actionable comment.
- CI/merge: merge queue, `cleanup_owed=false`. The landing message was written by `emit-landing` before `emit-landing` and `archive-plan` recorded their outcome, hence their `pending` in `steps`.
- Surface calibration (declared against realised): the spec declared 37 entries; the merged diff touched 67 files, 28 of them not declared (the credentials classes `CredentialStoreChoice`, `CredentialStoreMoveRecord`, `CredentialAccount`, `IntegrityKeyCarry`, the enrolment sidecars, `RuntimeLogMessages`, `CredentialStoreMoveResume`, ten tests and fixtures, five documents and `pm-runtime/README.adoc`). Five declared entries were not touched: `RecoveryStep`, `StartupSequence`, `EnrolmentsTest`, `EnrolmentFixture` and `state-catalogue/19`. The declared overlap with PLAN-20 in `RecoveryStep`/`StartupSequence` therefore never materialised (the earlier branch footprint already said so). The class-level surface was a HYPOTHESIS list; the spec is shipped, so this is calibration only.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-74 --status shipped`
- [x] row `pr`, `landing`, `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-74`
- [x] epic.md narrative reconciled (queue annotation; Open Defect for the owed `plan-marshall-mcp` assembly change; the recorded advisories)
- [x] landing message `plan-74-credential-store-choice-036.md` archived after the reconciliation was persisted
- [x] candidate lessons `-001` to `-035`: dispositioned in the decision log (`-001` to `-008`, `-010` to `-018` folded as a checklist into the staged specs that declare `pm-runtime` or `core.store` code; `-009` and `-019` to `-035` forwarded to plan-marshall's `lessons-routing` inbox)
- [ ] owed outside this verb: `plan-marshall-mcp` `SecretStoreProducer` (see the Open Defect), and the four advisories (staged as a follow-up plan)
- [x] resume anchor updated in `resume_anchor.md`
