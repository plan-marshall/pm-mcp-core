envelope_version=1
sender_type=plan
sender_id=rb-v2-188-schema-and-store-removals
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-26T09:18:01Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-188-schema-and-store-removals
epic=pm-mcp-core-m2-m4-v2
pr=#621
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2183
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced).

# PLAN-V2-188 — merged as `88680dd7`

Merge commit `88680dd78c162a09b73d07892ad8fbe66dba1e8f` (PR #621, head `464002aa`, branch `feature/v2-188-schema-and-store-removals`). Standing rules read at r7; the rule ids this plan binds are R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (R12: no message added; the plan parameters are `core-store.adoc` + `runtime-ingest.adoc`, ADR `core-store/`, WS-12).

## Deliverables

| # | Deliverable | Verdict | Order | Test | Failing run | Commit |
|---|---|---|---|---|---|---|
| 1 | Schema-only removals and wording (with the inbox's `required_check` addition) | **not met as written** — the one `fails at e451ed04` sub-clause was not run (see Residue); every other clause of the deliverable holds, and the removal substance is proved by the red-first run at the branch base and by mutation | test-first + mutation-after | `RemovedSchemaMembersTest` (14 cases) | test-first at `c6986b48`: 13 failed, 1 passed (the positive-control case), log `f3196a6be36a41528e53e2c635eedcb7.log`; mutation at `e61036d6`: re-adding `assessment_id` and `python` turned 2 cases red, log `01f72ba530d7408391916ece7d379de6.log` | e61036d6, d19d0b80, f14a7156, 6b01b186, 464002aa |
| 2 | The derived-fact kinds (with the inbox's two additions) | **not met as written** — same base-sha sub-clause; the kind removal is complete and proved | test-first + mutation-after | `DerivedFactsCodecTest.removedBranchUpToDateKindIsRefused`, `.removedCheckKindsAreRefused`, `DerivedFactsSchemaParityTest` | test-first at `e61036d6`: "Expected StoreRefusal to be thrown, but nothing was thrown", log `92f65dc6a0274ba38600368f8302b17b.log`; test-first at b39097b4's parent for the two check kinds, both documents accepted, log `4369c9a2bbcb48f8bf8c252e0d1609b2.log`; mutation at `ae545935`: parity + codec control red, log `c03d0639b8a84b97b62bb9b122d8736f.log` | ae545935, b39097b4 |
| 3 | The "Unicode fixpoint" wording | **met** | none | `IngestionValidatorTest` (11 tests, green) | — | 1f8f5817 |

Clauses met independently of the base-sha ones: `DerivedFactKind` holds thirteen constants in the schema's order and `DerivedFactsSchemaParityTest` is green; the thirteen remaining kinds read and write unchanged, and `base_advanced`, `overlap_max_files` and `epic.waits_overlap` are still declared, pinned by the positive-control case; every build clause is green — `verify -Ppre-commit` (12929 tests, log `61a16c812533424dbda7c3217fa40069.log`), full `verify` at the merge head (12993, log `94458ad5ae414b19b49c133933e8db81.log`), `verify -Pcoverage` (12929, log `2130cf7de81e48f3adc0368640aa4e80.log`), `verify -Pintegration-tests -pl pm-core -am` (21, log `78413df298ef407a8c77f6f4593fdcbf.log`), `verify -pl pm-core,pm-runtime -am` (8560, log `e41e3fbf65ec4c6690bf3dc848193a59.log`), `package -DskipTests` (log `38bc6bf661034c60877c41e5aa9b0a25.log`) and `package` over every module at the merge head (12993, log `395a0eeffe234465b2d8793e86c5a4a5.log`). Per-head test runs: 464002aa (12993 + 12993), 6b01b186 (12929 × 2), f14a7156 (12929 × 2 + 21), d19d0b80 (69 + 8560), 1f8f5817 (12929 × 3 + 21), b39097b4 (39 + 12993 + 12993).

`deliverables_done=1` stays conservative for the same reason as before: deliverables 1 and 2 are fully implemented and proved by a test-first red run at the branch's own base plus mutation-after runs on the committed fixes, but neither was ever run at the literal `e451ed04` the spec names. Every log above is a routed-build job log under `/home/oliver/.plan-marshall/marshalld/job-logs/`.

## The orchestrator's inbox item — read, and each item delivered

`orchestrator-pm-mcp-core-m2-m4-v2-001.md` (kind `finding`, created 2026-11-25T20:47:36Z; it arrived with the `origin/main` merge, and the `inbox read` channel still reports `no_mailbox` for this plan id because the file names the sender id):

- **`REQUIRED_CHECKS` and `CHECKS_VISIBLE` removed** — delivered: the constants and the two schema values left in one commit (`b39097b4`), after a red-first expectation in `DerivedFactsCodecTest` (both stored documents were accepted before the change, log `4369c9a2bbcb48f8bf8c252e0d1609b2.log`). Both are refused UNREADABLE with the key named now.
- **The count is thirteen** — delivered: the `DerivedFactKind` Javadoc, `core-store.adoc:1424` and the ADR 053 stamp all say thirteen, and the stamp names all three removed kinds. The parity test holds thirteen against thirteen.
- **`MERGE_QUEUE_ENFORCED` stays with `{available, check_response_timeout}`, its Javadoc drops "enforces its merge queue"** — delivered (the enumeration's Javadoc now reads "Whether a merge queue is available, and its check-response timeout (network)"), and the specification confirms the value shape (`store-schemas/09` line 61) and that the fact never answers whether a pull request must queue.
- **`finding-record.schema.json:40` `required_check` wording, `Finding.java:237` likewise** — delivered in `464002aa`: the description and the Javadoc now read the flag from the provider's read of the check at that moment, not from a derived `required_checks`.
- What the item did not ask is untouched: `decision-envelope-v1` `blocks`, `plan-instance-header`, `status.schema` `evidence_class` are not in this diff, and no count of fifteen survives anywhere.

## Personas and skills

- Personas: `persona-implementer` (D1, D2), `persona-documenter` (D3), `persona-code-reviewer` (three self-review rounds and all PR-comment triage).
- Domain skills per deliverable as declared: D1 `pm-dev-java:java-core`, `pm-dev-java:junit-core`, `pm-dev-java-cui:cui-testing`; D2 `pm-dev-java:java-core`, `pm-dev-java:javadoc`, `pm-dev-java-cui:cui-testing`; D3 `pm-dev-java:javadoc`, `pm-documents:ref-asciidoc`.
- Skills added for surface drift: none (the two undeclared files, `pm-workflow/doc/adr/workflow-unit/009-…` and `Finding.java`, are documentation changes served by the loaded skills).

## Hypotheses

- `EffectiveConfigKeysSchemaParityTest` / `RoleNameSingleDefinitionTest:87` do not pin `structural_caps` or `python` — **confirmed** (whole-document resolution at `config-project.schema.json:79`; the role guard pins a skill-URI grammar).
- A retained status member can only have a schema-level negative control — **confirmed** (`SchemaResource` map access plus `additionalProperties: false`).
- `DerivedFactsCodecTest` had no case for a removed kind — **confirmed** (`not_a_kind` only, before this plan).

## Findings

- Self-review round 1 (14), round 2 (8), round 3 (1): all fixed except four declined with reasons (the historical "27" in the ADR index got an amendment note instead of a rewrite; the removed-kind codec control stays its own test; the footprint-description test stays under its nested class; the parked-spec drift is the orchestrator's). Details in the progress file.
- PR comments: CodeRabbit published quota notices until its review landed, then a walkthrough summary with no findings; Sourcery approved. `review-gate` at the merge head: 0 unresolved threads, 0 body findings, 0 undisposed.
- Inbox: one item, both parts delivered (above).

## Reviewers

- **coderabbit** (required): **reviewed**. `review-gate --required-reviewer coderabbit` answered `all_handled` at `464002aa` (and `required_review_missing` for a control name that holds no review, which shows the check is live). Trigger history: five triggers over 6 h 52 min — refusals at 19:40:47, 21:16:22, 23:01:47 and 01:45:39 against the free-OSS quota (90-minute floor honoured each time, the bot's own 6- and 39-minute ETAs deliberately not used), then accepted at 06:32:33 on cycle 5. It holds `review_complete_after_handling: true`, so the later heads raised no re-trigger obligation.
- **sourcery** (optional): reviewed and **approved** at 08:10:08; its earlier budget notice (250 000 diff characters) had cleared by then.

## Operator questions

One operator message, free text: **"land it"**. No question-tool call was made during the run: the reviewer-quota policy forbids asking before its steps are spent, and "land it" was acted on through the ordinary gate — the review was obtained, the gates were green, and nothing was bent. The merge followed the operator's instruction, and the one rule-adjacent judgement is recorded in Residue: a merge-group run was ejected on a third-party HTTP 429 and the PR was re-enqueued rather than put to a question.

## Rule check

- Guard: `resolve` reports `guard: none`, so nothing enforced the tool rules on this harness: `guard: none on this harness (unenforced)`. The counts below are the run's own.
- Shell/executor: every script through `python3 .plan/execute-script.py …`; every build through the resolved `build-maven` canonical with `--project-dir`; every CI action through `plan-marshall:tools-integration-ci:ci`. One deliberate `rm` of the deleted schema file after `git rm --cached` had staged the deletion; two read-only `find`/`ls` probes to diagnose the base tree's missing executor.
- Git: one commit per deliverable (and per inbox addition), three review-fix commits and the Javadoc fix; one merge of `origin/main` with the trailer line; nothing staged wholesale; no stash, restore, rebase or forced push.
- `.plan/` state: `.plan/temp/` only, plus the `ci` body slots the `prepare-*` verbs returned.
- Spec read: `spec_source: ledger_worktree`, both copies agree — no `spec_differ` marker.
- executor-skew: `match` (executor 0.1.2183, lane 0.1.2183).
- Queue: enqueued twice. The first merge-group run (`45784844793`) failed in `quarkus-alignment` on `HTTP Error 429` from Maven Central while fetching `quarkus-bom-3.40.1.pom` — a job that passed on the PR head — and the queue dequeued the PR; it was re-enqueued and merged on the second attempt without any content change.

## Build server

Setup preflight `ready` (enrolled, version 1); `build-routing` `auto` with `executor_in_worktree: present`. Every build was routed (`daemon_longpoll`), no `in_process_fallback`, no launch-fault retry, no `killed` job. One explicit bound expiry: `verify` at a self-chosen 600 s bound (`timeout_source: explicit`, log `277357a9c39b4df584cc5770384f30f8`), after which the gate continued in the split form at the project's own bound (1166 s) and passed. `runtime_stamp_reason`: none. The landing-time resolve reports `executor_in_worktree: absent` with `worktree_not_created`, which is the post-cleanup state.

## Residue

1. **Base-sha evidence (owed to the marketplace, not to this plan).** A detached base tree at any commit reports `executor_in_worktree: absent` in this checkout: `prepare_execute` invokes the generator with `--marketplace-root <worktree>`, which is an explicit anchor, and a checkout without a vendored `marketplace/bundles` makes that raise — while `generate_executor.main` returns 0 regardless, so the failure is silent. Same gap as PLAN-V2-82, -152, -153, -185. Until it is fixed, every "fails at `{sha}`" clause in this epic is unprovable as written.
2. **Orchestrator state.** Parked PLAN-V2-03, -38 and -41 still plan or bless `regrounded_at_sha`, `reground_verdict`, a queue-row `overlap_found` and `epic.overlap_found`. PLAN-V2-195 covers the package prose only; the specs need re-grounding when revived.
3. **Nothing is owed on disk.** The worktree is removed, the local and remote branches pruned, the main checkout at `88680dd7`, and no base tree remains.
