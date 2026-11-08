# Landing Analysis: PLAN-V2-117 — Move the schema blocks of four machine-store documents to files; the audit and credential parity tests read the file

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #320 (merge commit `025a5224`, first parent `3a5cd36b` = PLAN-V2-118; 39 files; merged 2026-11-08T08:47Z, runbook lane, harness opencode, `deepseek-v4.1-flash/high`)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-117.md`. A pasted claim is a lead, never a fact.

Source: the plan's landing message `rb-v2-117-schema-files-machine-stores-001.md` (08:49Z). `landing-check` `complete: false`: `total_tokens` and `plugin_version` are `unknown` (folded into the standing Open Defect on the runbook lane). Reconciled in full. Spec source `ledger_worktree`, no `spec_differ`, executor skew `match`.

Corroborated by the orchestrator: `ci pr view 320` reads `merged`, merge commit `025a5224d04f…`; `ci checks status 320` reads 20 checks, overall success (Maven Build, quarkus-alignment, SonarCloud pass; CodeRabbit `NEUTRAL`, the quota refusal the landing reports). Sonar over plain HTTP (`pullRequest=320`): 0 open issues. The landing's lane Sonar step is `not_configured` (`n/a`), and its own R18 read of 0 agrees with mine.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 move the 22 blocks | shipped-as-specified | 22 of 22 files equal the former block plus one final newline (leaf script comparing each `3a5cd36b` block with `git show` of its include target); includes 11:1, 12:18, 13:1, 15:2; deleted-line counts 87/765/24/54 equal the files'; every target resolves from `doc/specification/store-schemas/`. The 22 `[source,json]` markers remain, each over one `include::` line (ADR 016's form); no inline JSON. |
| D2 the two tests read the files | shipped-as-specified | `AuditSchemaParityTest` (10 tests) loads the three audit files through `SchemaResource`; `CredentialStoreMoveRecordParityTest` (3) drops the `PROPERTY`/`REQUIRED` regexes and `Matcher`; neither names `SpecificationDocument` or `pm.specification.root` (`git grep` empty at the merge). |
| D3 a test for each schema whose codec exists | shipped-as-specified, with a test-strength gap | Six new classes with the narrated counts (Consent 5, ModelCatalogue 3, RoleAssignments 4, LeaseOwner 3, MachineConfiguration 4, HarnessEnrolment 5). The 12 codec-less schemas are named in the landing, not in any package task, and none of the 12 is parsed by any test. |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, surface `orchestrator.analyze`) — reason: a full-ship landing is read on three layers by its own leaf, with no size qualifier, and PLAN-V2-118 had its own. Neither leaf wrote anything. By reading, no build, no test run, no write.
- **Re-read by the orchestrator at `git show 025a5224:<file>`:** ADR 017's title and a grep of `doc/adr` and `pm-*/doc` for a codec-less rule; `LeaseOwnerSchemaParityTest.java:36,83-97`; `CredentialStoreMoveRecordParityTest.java:58-70,101-105`; `ConsentSchemaParityTest.java:95-112` and the count of rejecting assertions in the four config and store classes. The line numbers the leaf gave for the credential test are off by a few lines; the defect each names is present.
- **Verdict:** layer 1 findings — 1; layer 2 findings — 4; layer 3 findings — 5.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | verify-first count: 22 blocks | checked | confirms | `1+18+1+2` at `3a5cd36b` |
| L1-2 | HYPOTHESIS: files lie in `core/store` and a `credentials` package | checked | refutes | files landed in 7 further resource packages (`core/config`; runtime `config`, `launch`, `security`, `start`) by owning codec; Expected Surface named `AuditRecord.schema.json`, the file is `audit-record.schema.json`. Persisted as `contradicted`, `rescoped: no`. |
| L1-3 | `pm-runtime` reaches `SchemaResource` through `pm-core:tests` | checked | confirms | `pm-runtime/pom.xml` already declares the test-jar; `pm-core` packs `SchemaResource` and its nested classes; no pom changed |
| L1-4 | Done-when `verify`, `-Ppre-commit`, `-Pcoverage` and `trace.py` green | not_shown_by_reading | none | only a run shows it |

**Layer 1 verdict:** findings — 1 (L1-2); one claim not shown by reading.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | the 22 files | the member, enum and default assertions that compare the records to the files | `pm-core/doc/developer/core-store.adoc` (4 lines replaced by 8, not "additive") and the `host-tools` prose gaining "and the instant of enrolment" are not in the landing's surface-drift list | checked |
| D2 | `AuditSchemaParityTest.java:51`, `CredentialStoreMoveRecordParityTest.java:45` | member, required, written-member and enum assertions fail on a changed file | `IntentSchemaParityTest.java` (a #319 file; an import and one line, explained only as a gate rewrite) | checked |
| D3 | six `*SchemaParityTest` | member, enum and default equalities can fail | **no test in the six new classes rejects a violating document**: `writtenStoreIsValid` (`ConsentSchemaParityTest.java:103`) and its siblings assert only that a written document validates, so a validator that accepts everything leaves them green; the `allOf` rules of the consent schema are never exercised negatively; Lease, MachineConfiguration and Harness tests have no validator at all | checked, refutes |
| landing claim | "the codec-less placement rule is recorded on `main` as ADR 017 (from #319)" | — | **refuted (re-read)**: ADR 017 is the HTTP-tests ADR (#316); no text in `doc/adr` or `pm-*/doc` states where a codec-less schema goes | checked, refutes |

Not shown by reading: the mutation-after failing runs (`df402a67…`, `b847fd67…`) and every green-run claim (6586 tests, CI 25/26); the asserts would fail on a member, enum or default change, but which mutation was applied is not shown.

**Layer 2 verdict:** findings — 4 (the missing rejecting case, the false ADR 017 citation, the two unlisted files, the 12 schemas no test parses).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `LeaseOwnerSchemaParityTest.java:85` | the assertion | message "The pid is a number" is 19 characters (re-read) | minor |
| L3-2 | `CredentialStoreMoveRecordParityTest.java:61-69,103` | three assertions with no message, two whose message is the data value (the written JSON, the generated id) | the 20-to-60-character rule is unmet (re-read) | minor |
| L3-3 | `LeaseOwnerSchemaParityTest.java:36` (javadoc), `HarnessEnrolmentSchemaParityTest.java:52`, `ModelCatalogueSchemaParityTest.java:40` | the class javadoc says the ids and model id are generated | `writtenOwner` uses the literals `project-a`, `plan-a`, `build-slots`; the others use `opencode` and `deepseek-v4.1` — the javadoc is false and the literals are not marked as the contract (re-read for LeaseOwner) | minor |
| L3-4 | `ConsentSchemaParityTest.java:108-109`, `RoleAssignmentsSchemaParityTest.java:67-95`, `CredentialStoreMoveRecordParityTest.java:48` | literal `project-a`, `scripts/build.sh`, a SHA256 constant, a device id, a fixed `Instant` | test data not from `cui-test-generator` and not declared as a contract | minor |
| L3-5 | `CredentialStoreMoveRecordParityTest.java:69` | the schema file reformatted | `assertTrue(text.contains("\"additionalProperties\": false"))` is whitespace-sensitive and also matches a nested occurrence; a structured check through `SchemaResource.object` is sturdier. Passes today. | minor |

Also read and noted as one-way by design: `LeaseOwnerSchemaParityTest.java:60` checks the codec's scope values are a subset of the schema's enum, so a schema value the codec cannot write is allowed; `MachineConfigurationSchemaParityTest` covers required members, two defaults, provider keys and the variant enum of a 187-line schema, never validates a document, and does not exercise the external `$ref` to `local-config.json#/$defs/settings`. No resource-path or classpath defect was found: the files sit in `src/main/resources` of the owning module and ship in the production JARs (ADR 016).

**Layer 3 not shown by reading:** whether the parity tests pass, coverage, and the mutation runs.

**Layer 3 verdict:** findings — 5, all minor; none is a defect in production code (no production Java changed).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md | a move only: each file the former block byte for byte | conforms | 22 of 22 |
| CLAUDE.md | every assertion carries a message of 20 to 60 characters | deviates | `LeaseOwnerSchemaParityTest.java:85` (19), `CredentialStoreMoveRecordParityTest` (three missing, two data values); the other 200-odd assertions in the eight classes read are in range |
| CLAUDE.md | test data from `cui-test-generator`, a literal only as the contract | deviates | literals in five classes; three javadocs claim they are generated |
| CLAUDE.md | JUnit 5 only, no Mockito, no AAA comments, no generic catch, no dependency, no model-facing content, licence header | conforms | no pom and no production Java changed |
| CLAUDE.md | every `LogRecord` asserted | not_shown_by_reading | no record added or changed |
| Spec Done-when | `verify`, `-Ppre-commit`, `-Pcoverage` | not_shown_by_reading | only a run shows it; CI is green (above) |

## Metrics and Anomalies

- Tokens: `unknown` (unobserved, never zero).
- Duration: the landing gives none; merged 2026-11-08T08:47Z, the final `build (25)` job ran 565 s (`ci checks`).
- Anomalies: CodeRabbit refused on quota twice, then reviewed `27b1f313`; the merge head `7e5e2929` was merged on the standing authorization over a quota-refused last trigger; queue waits up to about 1030 s.

## Routing and Merge Behavior

- Review: CodeRabbit (required) gave 18 inline threads and 20 further findings, all 22 inline threads answered and resolved; Sourcery refused on quota (optional).
- CI/merge: success; the run merged `origin/main` once (`9dbd9b67`), resolved one index-row conflict and re-ran the gate. The merge landed on top of #319 with no file collision beyond that row.
- Sonar: the lane's step is `n/a` (`not_configured`); the orchestrator's plain-HTTP read of `pullRequest=320` returns 0 open issues, agreeing with the landing's own R18 read.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#320`
- [x] row `landing` stamped `landings/PLAN-V2-117.md`
- [x] row `plan_marshall_plan_id` stamped `v2-117-schema-files-machine-stores` (from the sender `rb-v2-117-…`)
- [x] layer-1 verdicts persisted on the spec (claim 1 `contradicted`, claim 2 `corroborated`)
- [x] `epic.md`: recurrence folded into the runbook-lane `unknown` Open Defect; findings recorded as one Open Defect
- [x] resume anchor, `queue-view.md`, committed in the ledger worktree

## Follow-Ups

No fix plan is staged; the operator decides. Candidates: (1) a rejecting case for the validity tests of the six new classes, and the missing structured check at `CredentialStoreMoveRecordParityTest.java:69` (L2, L3-5); (2) one cleanup of the assertion messages, generator data and false javadoc in these tests and in PLAN-V2-118's (the conformance family's PLAN-V2-101 to 105 already own messages; this is new code they have not seen); (3) the 12 codec-less schemas named in their owning packages' tasks (`runtime.launch`, `runtime.start`, `runtime.security`, `runtime.credentials`, `core.store`), which neither landing did; (4) correct `core-store.adoc` and ADR 016 wording, which PLAN-V2-122 already owns.
