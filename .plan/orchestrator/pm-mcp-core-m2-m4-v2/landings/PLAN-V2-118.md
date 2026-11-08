# Landing Analysis: PLAN-V2-118 — Move the schema blocks of seven record documents to files and add the parity tests they never had

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #319 (merge commit `3a5cd36b`, first parent `3b90dcb7`; merged 2026-11-08T04:29Z, runbook lane, harness opencode, `deepseek-v4.1-flash/high`)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-118.md`. A pasted claim is a lead, never a fact.

Source: the plan's landing message `rb-v2-118-schema-files-remaining-records-001.md` (04:32Z). `landing-check` `complete: false`: `total_tokens` and `plugin_version` are `unknown`, the harness measured neither (folded into the standing Open Defect on the runbook lane). The landing was reconciled in full. Spec source `ledger_worktree`, no `spec_differ`, executor skew `match`.

Corroborated by the orchestrator: `ci pr view 319` reads `merged`, merge commit `3a5cd36b3b06…`; `ci checks status 319` reads 20 checks, overall success (Maven Build, Integration Tests, SonarCloud pass; CodeRabbit `NEUTRAL`, the quota refusal the landing reports). Sonar read over plain HTTP (`pullRequest=319`): 0 open issues, as the landing claims; `main` gate OK, 0 open BUGs.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 move the 25 blocks | shipped-as-specified | 25 files (20 in `core/store`, 5 in `core/config`); each equals the removed block byte for byte, 2035 lines removed = 2035 added (leaf script over git, all 25, not a sample); per document 9, 2, 6, 2, 3, 2, 1; every `[source,json]` wrapper holds one `include::` line. |
| D2 a parity test per schema whose codec exists, both controls | shipped-modified | Four store classes (`IntentSchemaParityTest`, `OutlineSchemaParityTest`, `MetricsSchemaParityTest`, `ChangeLedgerSchemaParityTest`) carry the positive and both negative controls over `SchemaParity.java`. `ProjectConfigurationSchemaParityTest` carries **neither negative control** and no unchanged-file comparison: it checks `$id` and format version of five files and the `settings.qualification` components against the record. The second sentence of D2 (name the 19 codec-less schemas in the owning package's task) is not done; the landing calls it outside the write boundary but the plan edited that package file for a link. |
| D3 an existing schema file of the same record | vacuous | True: no schema file of the same record existed (`core/role/corpus` holds three unrelated schemas). |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, surface `orchestrator.analyze`) — reason: a full-ship landing is read on three layers by its own leaf, with no size qualifier; a second leaf read PLAN-V2-117 separately so neither shared context with the other. Neither leaf wrote anything. By reading, no build, no test run, no write.
- **Re-read by the orchestrator at `git show 3a5cd36b:<file>`:** `ProjectConfigurationSchemaParityTest.java` 60-102 (no negative control, messages at `:95` and `:99`); `MetricsCodec.java:84` and `metrics.schema.json:43`; `intent-record-v1.schema.json:25-29,51-54` and `IntentRecord.java:92-94`; `EpicIntent.java:36-44` and `epic-intent-v1.schema.json:8-16`; ADR 017's title. Every point below recorded as `checked` was re-read; the one marked unverified was not.
- **Verdict:** layer 1 findings — 2; layer 2 findings — 7; layer 3 findings — 4.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | verify-first count: 25 blocks | checked | confirms | `04:9 05:2 06:6 07:2 08:3 17:2 18:1` at `3b90dcb7` |
| L1-2 | HYPOTHESIS: codecs lie in `pm-core` and `pm-workflow` | checked | refutes | no `pm-workflow` file touched; `decision-record` and `replan-record` have no class at all. Persisted as `contradicted`, `rescoped: no`. |
| L1-3 | D3 could be triggered | checked | refutes | the clause is copied from the package and cites no instance |
| L1-4 | D2 internally consistent | checked | refutes | D2 says to name codec-less schemas in the owning package, yet Expected Surface lists no package file to edit; the cited form of `FindingSchemaParityTest` walks nested records, which Done-when does not require |

**Layer 1 verdict:** findings — 3 (L1-2 to L1-4); the count claim is corroborated.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | the 25 `*.schema.json` files | the four store tests and the config test read them | 25 added sentences "The schema is the file …" in the seven documents (not in the landing, not a schema change); `SchemaParity.java` (300 lines, a helper no deliverable names, not mentioned in the landing) | checked |
| D2, store records | `SchemaParity.java`, four `*SchemaParityTest` | member diff is symmetric, `requiredByCodec` compares the schema's required against what the codec refuses; controls run on an in-memory copy | `MetricsSchemaParityTest` compares `taskRow` only; `workerIdleRow` has a codec (`MetricsCodec.java:518`) and no check | checked |
| D2, config | `ProjectConfigurationSchemaParityTest.java:80` | `qualificationParity` can fail on a component, default or maximum change | **no negative control in either direction; the landing's "each test has both controls" is refuted for this class** | checked, refutes |

Not borne by the diff: the landing says `OutlineSchemaParityTest.recordEnumerations` failed first at `749fbd6e` as a genuine mismatch; that block is unchanged to the merge, `pm-core` main is unchanged since `fd26d7e3` and no outline drift is pinned or listed — **not shown by reading, recorded as an unverified lead**.

**Layer 2 verdict:** findings — 7 (the config class, `workerIdleRow`, the codec-less naming, the helper and added sentences unmentioned, the `limits` coverage of one bound per schema, `Intent`/`Metrics` set-based member diff, the unverified outline claim).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `metrics.schema.json:43`, `MetricsCodec.java:84` | any task row the codec writes | the row carries `attempt_end`, which the schema does not declare, and `additionalProperties` is `false`: a row the codec writes is invalid against its own file. The test pins it as expected (`MetricsSchemaParityTest.java:103,124`); the landing says "specification judged right" with no evidence, and `doc/plans/z3-deferred-code-removal.adoc:19` lists `attempt_end` as code to be removed while `doc/Traceability.adoc:496` calls it implemented. | major |
| L3-2 | `intent-record-v1.schema.json:53,25,63`, `IntentRecord.java:94,558` | an intent record | the schema says `choices`, `repairs` and decider `role`; the record writes `decisions` and decider `model`. Reported and pinned in `IntentSchemaParityTest.java:117,168-169`; no owner plan is named in the test. | major |
| L3-3 | `IntentRecord.java:92`, `intent-record-v1.schema.json:27` | an intent record with no non-goals | the record requires `non_goals` 1..20; the schema allows `minItems: 0`. **Not reported by the landing** and not tested (the test checks `why minLength` only). | minor |
| L3-4 | `EpicIntent.java:39-44`, `epic-intent-v1.schema.json:10-13` | an epic intent | the record takes `destination` 20..1000 and `non_goals` 0..30 of 10..500; the schema says 10..300 and 1..20 of 5..500. **Not reported**; the landing names `epic-intent-v1` only as codec-less, so no test applies. | minor |

Further minor: assertion messages of 62 and 63 characters at `ProjectConfigurationSchemaParityTest.java:95,99`; no `Generators.*` in any of the five tests (`@EnableGeneratorController` unused; literal ids, hashes and instants in `MetricsSchemaParityTest.java:52-69` and `ChangeLedgerSchemaParityTest.java:48-55` carry no contract note); javadoc defects in `SchemaParity.java:157,168` (`@param recorded` is no parameter, "in file order" returns `Set.copyOf`). Also the change-ledger `criterion` and `red_tests` are declared by the file (`change-ledger-record.schema.json:70,74`) and absent from the record and codec: an unbuilt spec feature rather than a disagreement.

**Layer 3 not shown by reading:** whether the parity tests pass at `3a5cd36b`, whether coverage meets 80 percent, whether the S5778 suppressions are honoured, and whether the `749fbd6e` failing run happened.

**Layer 3 verdict:** findings — 4 (L3-1 and L3-2 major, reported to the operator at once, no fix plan staged).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md | every assertion carries a message of 20 to 60 characters | deviates | `ProjectConfigurationSchemaParityTest.java:95` (62), `:99` (63); the rest in range |
| CLAUDE.md | test data from `cui-test-generator`, a literal only as the contract | deviates | no `Generators.*` in the five tests |
| Spec Done-when | a matched positive and negative control in both directions | deviates | four store tests have both; the config class has none |
| CLAUDE.md | licence header, JUnit 5 only, no Mockito, no AAA comments, no generic catch, suppression with reason, no new dependency, no model-facing content | conforms | `SchemaParity.java:212` catches `StoreRefusal` only; no pom touched |
| Spec | no schema text change except a correction with evidence | conforms | 25 of 25 files equal the removed blocks |
| Spec Done-when | `verify`, `-Ppre-commit`, `-Pcoverage` green | not_shown_by_reading | only a run shows it; CI is green (above) |

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none; recorded as unobserved, never as zero).
- Duration: the landing gives none.
- Anomalies: build server fell back `in_process` 4 times (daemon bound 310 s and 397 s), no launch fault, no killed job; CodeRabbit and Sourcery quota-limited.

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `e728e10d`, 7 inline threads answered and resolved; merged on the standing authorization over a quota-refused re-trigger at `c511bf91`. Sourcery refused on quota, optional.
- CI/merge: `ci` success on 20 checks; merge commit `3a5cd36b`; `#320` landed on top of it with no conflict recorded.
- Sonar: the landing claims `open_issue_count=0`, `open_issue_rules=none`; my plain-HTTP read of `pullRequest=319` returns 0 open issues. Claim and measure agree.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#319`
- [x] row `landing` stamped `landings/PLAN-V2-118.md`
- [x] row `plan_marshall_plan_id` stamped `v2-118-schema-files-remaining-records` (from the sender `rb-v2-118-…`)
- [x] layer-1 verdicts persisted on the spec (claim 0 `contradicted`, claim 1 `corroborated`)
- [x] `epic.md`: recurrence folded into the runbook-lane `unknown` Open Defect; one Open Defect for the findings below
- [x] resume anchor, `queue-view.md`, committed in the ledger worktree

## Follow-Ups

No fix plan is staged; the operator decides. Candidates: (1) a negative control and an unchanged-file comparison for `ProjectConfigurationSchemaParityTest` (L2); (2) the metrics `attempt_end` decision together with the Z3 deferred-code package, which already lists it (L3-1); (3) the intent-record `choices`/`decisions`, `repairs`, decider and `non_goals` drifts, whose owner is the `PLAN-V2-03`/`07` codec work (L3-2, L3-3); (4) the `EpicIntent` bounds against its schema (L3-4); (5) naming the 19 codec-less schemas in their owning packages' tasks (D2).
