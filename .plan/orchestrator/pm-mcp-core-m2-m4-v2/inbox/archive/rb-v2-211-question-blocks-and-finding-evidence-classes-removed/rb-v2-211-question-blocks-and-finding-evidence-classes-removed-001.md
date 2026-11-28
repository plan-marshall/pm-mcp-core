envelope_version=1
sender_type=plan
sender_id=rb-v2-211-question-blocks-and-finding-evidence-classes-removed
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-28T18:01:39Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-211-question-blocks-and-finding-evidence-classes-removed
epic=pm-mcp-core-m2-m4-v2
pr=#654
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=1
review_body_findings_handled=1
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-211 — landing

Standing rules: r7; rule ids R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (R21 binds the orchestrator; R12 not named). The rules file was read before any work; the spec's First Instruction is honoured.

Branch `chore/v2-211-question-blocks-and-finding-evidence-classes-removed`, PR #654, merged through the merge queue as `f991a6790467596785418d73065a2689e4c74be4`. Worktree removed, branch and remote ref pruned, `main` switched and pulled to the merge commit.

## Deliverables

The spec has three deliverables and one Done-when block. Every clause of that block was graded, including the spec-internal contradiction found in self-review: "the existing codec and schema tests hold unchanged at the head" is literally unachievable, because `blocks` was a **required** wire member of the decision, so every codec path that reads or writes a decision had to change (fixture JSON, the five-argument `Decision` constructor, the four `Blocks` imports, the refusal table rows). On **substance** no member or value beyond the named removals changed: `Decision` keeps `title/header/description/evidence/reversibility` with their types, order, bounds, null contract and `@Builder`; `Evidence` keeps `class/ref/claim_refuted`; `EvidenceClass` keeps only `none`; the envelope keeps every other member.

### D1 — the failing tests first (order `test-first` for all)

| Done-when test | Met | Failing run (job) | Failing output | Commit |
|---|---|---|---|---|
| `QuestionRecordRefusalsTest.removedBlocksMemberIsRefused` (stored `blocks` refused, key named) | yes | `c511bdbd` | "Expected StoreRefusal to be thrown, but nothing was thrown" | `21cb2fe9` |
| `QuestionRecordRoundTripTest.decisionWithoutBlocksRoundTrips` (decision without `blocks` round-trips) | yes | `51ab1746` | StoreRefusal: "field 'blocks' of the decision is missing" | `21cb2fe9` |
| `FindingTest.removedEvidenceClassesAreRefused` (`verifier`/`substitute` refused, `none` stays) | yes | `51ab1746` | 2 failures (verifier, substitute) | `21cb2fe9` |
| `FindingSchemaParityTest.evidenceClassListsNoRemovedValue` (schema enum has no `verifier`, keeps `none`) | yes | `51ab1746` | 2 failures | `21cb2fe9` |
| `RemovedSchemaMembersTest.claimRefutedNamesTheJobIdRule` (rewritten description) | yes | `51ab1746` | 4 failures | `21cb2fe9` |
| `DecisionEnvelopeSchemaTest.blocksMemberIsGone` (envelope declares no `blocks`, stays closed) | yes | `5a1e31e3` | 2 failures | `21cb2fe9` |

Failing-run logs: `.plan/local/plans/NO_PLAN/build-results/pm-core/maven-2026-11-28-144231.log` (run `51ab1746`) and the daemon logs of jobs `c511bdbd`, `5a1e31e3`. The first version of `removedBlocksMemberIsRefused` passed vacuously (the spliced JSON carried `blocks` twice, so the old codec refused it as a duplicate); it was rewritten before any evidence was taken — recorded in the progress note.

The negative control the removal would otherwise lose (`readDecision`'s required members) is restored as the `decision without reversibility` row of `askedMemberShape`.

### D2 — the removals (order `test-first`, same tests)

Met. `QuestionRecord` (enum `Blocks`, the component, its null check), `QuestionRecordCodec` (field, write, read), `pm-workflow`'s `decision-envelope-v1.schema.json` (`required` and `properties`; the object stays closed), `finding-record.schema.json` (`evidence.class` = `["none"]`, rewritten `claim_refuted` description), `Finding.EvidenceClass` (only `NONE`; parity pin holds), and the fixtures/tests that named the removed things. Commits `411f2c7b`, `c434f327` (pre-commit rewrites of this plan's files), `8d33589d` and `96d526e1` (self-review rounds 1–2), `2c1c7f5e` (the gate's own import rewrite).

The envelope's payload arm: this repository holds no decision-envelope submit handler and no main source loads the schema (`SchemaRegistry` registers names only, ADR 010), so the `invalid_link_args` refusal lives in the `pm-mcp-server` assembly; here the contract is pinned as member-absence plus closure, and the stored-record arm is executable. The spec's Done-when says "a payload **or** a stored record" — the stored arm is met and tested.

### D3 — the documents

Met as written: `pm-core/doc/developer/core-store.adoc` does not list the decision's members, and no ADR of `pm-core/doc/adr/core-store/` names `blocks` or the evidence classes (read; no match), so there was nothing to amend. `core-findings.adoc` deliberately does not repeat enum values. No ADR is added: no design decision was taken (the fourth round decreed the removal).

## Personas and skills

- D1: `persona-module-tester`; skills `pm-dev-java:junit-core`, `pm-dev-java:java-core`, `pm-dev-java-cui:cui-testing`.
- D2: `persona-implementer`; skills `pm-dev-java:java-core`, `pm-dev-java:java-null-safety`, `pm-dev-java:javadoc`.
- D3: `persona-documenter`; skills `pm-documents:ref-asciidoc`, `pm-dev-java:javadoc`.
- Self-review rounds and PR triage: `persona-code-reviewer`.
- Skills added for surface drift: none. Surface deltas: `decision-envelope-v1.schema.json` lives in `pm-workflow`, not the `pm-core` path the spec's Expected Surface printed (it was moved by #404); new `pm-workflow/src/test/.../question/DecisionEnvelopeSchemaTest.java`; `RemovedSchemaMembersTest` extended (the PLAN-V2-188 pattern home). All were reported by the reviewer as traceable to a deliverable.

## Hypotheses

- "The specification removes `substitute` together with `verifier`" — **confirmed** (`git show d961c9df`, `doc/specification/store-schemas/03-findings-and-review-records.adoc:96,97`): the rejected row that named the verifier sub-state *and* substitute evidence was replaced by the job-id rule; the class vocabulary has no member but `none` left. `store-schemas/14-plan-instance-waivers-questions.adoc:134` already says what a decision blocks is derived on read and never stored.
- "`Finding.java:461,473-477` and `FindingTest:213-222` are the only Java places naming the classes" — **confirmed** by grep; `FindingSchemaParityTest:52` only maps the keyword member name.

## Findings

- Self-review round 1: D1 Javadoc claimed the payload refusal this module does not test (Javadoc corrected); D2 two dead `.replace()` calls (removed); D3 lost negative control for the decision's required members (row added); D4 stale "Six cases" count and plan list in `RemovedSchemaMembersTest` (corrected). Fixed in `8d33589d`.
- Self-review round 2: F1 the corrected Javadoc named enum values the class does not pin (now names only `claim_refuted`); F2 the evidence test sat in the `accepted` nest (moved to `refusals`); F4 cosmetic import/CSV alignment; F3 restated as the accepted out-of-repo gap. Fixed in `96d526e1` (and the gate's import rewrite in `2c1c7f5e`).
- Self-review round 3: **no findings open**; the only note was the pre-commit rewrite of the envelope test, accepted and committed.
- CodeRabbit inline `inline-1` (`decision-envelope-v1.schema.json:6`): the payload refusal has no in-repo handler — handled as a stated reason (no in-repo validator; the submit handler is in the `pm-mcp-server` assembly; the schema closes `blocks`), thread answered and resolved, disposition recorded.
- Sourcery: own review budget exhausted — a quota notice, not a finding; optional reviewer.

## Reviewers

| Reviewer | Required | Result | Head | History |
|---|---|---|---|---|
| coderabbit | yes | reviewed (qualifying summary review, `final_review_risk_coverage` names `2c1c7f5e`) | `2c1c7f5e` | triggered 17:21; review 17:26; one inline finding answered and resolved; `review_complete_after_handling` satisfied by done |
| sourcery | no | refused (own diff-character budget for 7 days) | — | triggered 17:21; refusal 17:21; optional, does not gate |

## Operator questions

none asked. No free-text operator messages.

## Rule check

- Guard: `guard: none on this harness (unenforced)` — no tool-call guard is installed for OpenCode, so no guard-log line exists for this run; nothing was enforced, and nothing is claimed as "all kept".
- Self-reported shell-rule breaches, counted by the run's own actions: one `find / … -name runbook-lane` at start-up (a directory search outside the repository root — the skill's install path, no credential file); shell `ls` on worktree directories; shell `grep`/`sed`/`tail` used to inspect the runbook-lane and `ci` scripts and to read build logs (file tools should have been used); one `cp` to duplicate a prepared PR body. No `git stash`, `restore`, `checkout --`, rebase or forced push; nothing staged with `git add -A`; no `.plan/orchestrator` write.
- One `indeterminate` targeted run: `test -pl pm-runtime -am -Dtest=…` answered `test_selector_matched_nothing` (pm-core ran no selected test). It was not retried; the full `module-tests` run superseded it.
- Restarts: none. Killed sub-agents: none. Re-dispatch: self-review rounds 2 and 3 followed their predecessors.
- `executor-skew`: `match` (executor 0.1.2183, lane 0.1.2183) — answered, recorded, never asked about.
- Spec read: `spec_source=ledger_worktree`, `spec_differ` absent (the two copies agree).

## Build server

- Setup preflight: `ready` (enrolled).
- Mechanism: `daemon_longpoll` for every routed build — `module-tests` (`package`), three `quality-gate` (`verify -Ppre-commit`), three `verify`, and the targeted test runs. No `in_process_fallback`, no degradation reason, no launch-fault retry, no `killed` job.
- Client bound expiries were re-attached by `wait` on the job id (queue/running), never backgrounded.
- `runtime_stamp_reason`: none carried; `plugin_version` remains `unknown` (the resolve seam returned none).
- Indeterminate run: the targeted `-Dtest` run above (a selection miss, not a build failure).

## Residue

- The `invalid_link_args` payload refusal is **out of this repository**: the `pm-mcp-server` assembly (repository `plan-marshall/plan-marshall-mcp`) must apply `decision-envelope-v1.schema.json` at submit for a `blocks`-carrying payload to be refused. Owner open; reported in PR #654 and in the resolved CodeRabbit thread.
- Carried items the spec names as "owner open" (the findings write seam): the `claim_refuted` job-id resolution, the new findings members `rule_id`/`domain`/`evidence_ref`, the `FIX`→`fixed` transmit, and the derived `decision.blocks` read — none built here, as the spec orders.
- Existing stores: a stored question line whose decision carries `blocks` (and a finding with `evidence.class: verifier`) now reads `UNREADABLE`; the format version does not move (R13). This is the refusal the spec mandates; the operational consequence is stated in the PR body.
