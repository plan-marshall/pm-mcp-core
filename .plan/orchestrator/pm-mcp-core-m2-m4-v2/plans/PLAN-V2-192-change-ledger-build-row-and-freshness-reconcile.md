# PLAN-V2-192: The change-ledger build row records what the gate derives from, and the freshness reconcile stamp goes

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: doc-review-2026-11-25-001.md section 1 V4 (supersedes the first round's wording item on `change-ledger-record.schema.json:83`: `criterion` is DELETED) and doc-review-2026-11-24-001.md section 1 ("Freshness reconcile replaced by the gate's derivation"); operator's cut, plan 5.
lane: runbook

> Staged plan spec — ready for `/plan-marshall-runbook-lane` hand-off. Lives at `plans/PLAN-V2-192-change-ledger-build-row-and-freshness-reconcile.md`; one queue row, lane `runbook`. The orchestrator EMITS the command; it never launches the plan inline.

## First Instruction: Read The Standing Rules

Read `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` completely first; say so with the revision in the first progress note and repeat revision and rule ids in the landing message. Drafted set: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (orchestrator confirms; R13 binds). Plan parameters: R06 developer documentation `pm-core/doc/developer/core-store.adoc` and `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc`; ADR directory `pm-core/doc/adr/core-store/` (a new ADR supersedes 089, 088 is adjusted); layout WS-07.

## Objective

A build row of the change ledger drops the members `criterion` and `red_tests` and records instead what the gates derive from: the commit it built (`head`), the paths dirty then, every test file the plan changed against `base_ref` with the digest of the plan's own diff of it, and the identities of the failed tests of its parsed result. The freshness gate derives build-neutrality from the latest succeeded build row, so the stamp `freshness_reconcile` of the status, its record `FreshnessReconcile` and its schema member are removed. `Expect` and `ExpectResult` stay (`expect: red` is a hint that proves nothing).

## Deliverables

Three deliverables, about 22 files, no split needed. Schema first.

1. **The build row (schema, record, codec).** `change-ledger-record.schema.json`: delete `criterion` (:81-84) and `red_tests` (:85-97); add the four members for a build row (proposed names `head`, `dirty_paths`, `test_files[{path, diff_digest}]`, `failed_tests[]`, to be fixed by the outline and written into `doc/specification/store-schemas/04-epic-and-change-ledger.adoc` at "Build Rows" :156); extend `ChangeLedgerRecord` (:34-58, the builder) and `ChangeLedgerCodec` once. `ChangeLedgerSchemaParityTest` (:37-39, :59-65, :79-80 pin `criterion` and `red_tests` as file-only) is rewritten to an EXACT member match with a build-row case.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first the negative controls (a stored build row that still carries `criterion`, one that carries `red_tests`, each refused whole and named — in `DeletedMemberRefusalTest` style) and the exact-match parity test, red at e451ed04; then schema, record, codec in one commit.
2. **`FreshnessReconcile` goes.** Delete `FreshnessReconcile.java` and `FreshnessReconcileTest`; `WorkflowStatus` (:64, :89), `WorkflowStatusCodec` (:105,174-177,273,524,581,980-1001), `package-info.java:51-57`, `status.schema.json:363-375`; tests `WorkflowStatusCodecTest` (:104,120,368-373), `WorkflowStatusSchemaParityTest` (:75,198,503-516), `WorkflowStatusBuilderContractTest`.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first the negative control (a stored status carrying `freshness_reconcile` is refused, naming the member; the written status carries no such member); then the removal in one commit with the schema.
3. **Documentation and the ADRs.** `core-store.adoc` (:55-63, :349-371, :1328-1330, :2809-2810), `provider-git-primitive.adoc:285`, `doc/Traceability.adoc` (the FreshnessReconcile and test rows; the finding names :851, the rows stand at :877 and :893 at the head), ADR core-store/089 is superseded by a new ADR, 088 is adjusted.
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when** (against `origin/main` e451ed04): a build row written by the codec carries `head`, the dirty paths, the test files with their diff digests and the failed-test identities, and reads back equal — fails at e451ed04; a stored row with `criterion` or `red_tests`, and a stored status with `freshness_reconcile`, are refused and named — fails at e451ed04; `ChangeLedgerSchemaParityTest` is an exact member match — fails at e451ed04 (it pins two file-only members); every other kind (`change`, `commit`, `job`, `task_diff`, `baseline_red`) and `Expect`/`ExpectResult` read and write unchanged — holds at e451ed04. `verify -Ppre-commit`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am`, `verify -pl pm-core -am` green (targeted `-Dtest` where a full verify exceeds the host budget). Sonar: 0 open new-code findings at the head or each key selected by the operator.

## Standing Constraints

- Every removal deletes its pinning tests and adds a negative control; no read shim; schema and parity test in one commit.
- The writer of the new members (the job runner side, `pm_build`) and the freshness gate's derivation are not built here (owner of both: `build-parsers-and-gate-honesty`, task "The build-neutral derivation of the freshness gate", merged by PLAN-V2-195 as #623; `finalize-pipeline-and-merge` only READS the derivation for its `open_ended` verdict-currency exception); this plan delivers the record and its codec.
- Read cited lines at the head first. No dependency or plugin; at most 100 files per pull request; no model-facing content.
- Sonar: fix or dismiss only on the operator's selection; an undecidable read blocks.

## Claim Labels

- OBSERVED: `change-ledger-record.schema.json:81-97` hold `criterion` and `red_tests`; `ChangeLedgerRecord` has neither (:54-58) and has `expect`, `expectResult`, `baseline`; `ChangeLedgerSchemaParityTest.java:37,59-65` pins the two as file-only.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: change-ledger-record.schema.json:81-84 criterion, :85-97 red_tests; ChangeLedgerRecord.java:54-58 components have no criterion/red_tests and include expect, expectResult, baseline; ChangeLedgerSchemaParityTest.java:37 (javadoc) and :59-65 pin file-only:criterion and file-only:red_tests.
- OBSERVED: `freshness_reconcile` occurs only in `FreshnessReconcile.java`, `WorkflowStatus.java`, `WorkflowStatusCodec.java`, `package-info.java`, `status.schema.json`, three tests, `core-store.adoc`, ADRs core-store/088, 089, `provider-git-primitive.adoc:285`, `Traceability.adoc`.
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: ADR core-store/088 does not contain freshness_reconcile (git grep -i: only 089, FreshnessReconcile.java, WorkflowStatus.java:64,89, WorkflowStatusCodec, package-info.java:57, status.schema.json:360, core-store.adoc, provider-git-primitive.adoc:285, Traceability.adoc, 3 tests); 088 only mentions the freshness verb.
- OBSERVED: the specification fixes the target: `store-schemas/04-epic-and-change-ledger.adoc:156` (Build Rows), `phase-workflows/09-sanity-check.adoc:41` (`fix-proof`).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: store-schemas/04-epic-and-change-ledger.adoc:156 Build Rows: kind build records head, dirty paths, every changed test file with digest, failed-test identities; phase-workflows/09-sanity-check.adoc:41 fix-proof (expect: red is a hint that proves nothing; build row records test files and failed tests).
- HYPOTHESIS: the wire names `head`, `dirty_paths`, `test_files`, `failed_tests` — the specification names `head` only; confirm or fix at `doc/specification/store-schemas/04-epic-and-change-ledger.adoc:156` and the schema (verify-at-outline).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Spec :156 names only (`head`); git grep dirty_paths|test_files|failed_tests|diff_digest over doc, pm-core, pm-providers returns nothing, so those wire names appear in no specification or schema.
- HYPOTHESIS: the member `worktree_sha_failure`-style conditional (`allOf`) schema rules need a `kind == build` condition for the new members — confirm at `change-ledger-record.schema.json:14-27`.
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: change-ledger-record.schema.json:14-27 allOf holds only the worktree_sha=unavailable rule (:15-19) and a kind=commit rule (:20-26); the existing build-only members expect, expect_result, criterion, red_tests (:71-97) carry only a 'For kind == build' description and no conditional, so the current pattern does not need a kind==build condition.
- Verify-first clause: re-read `ChangeLedgerRecord` and the codec at the head; PLAN-V2-55 (`gate-baseline-separation`, staged) also edits them (see Sequencing).
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: PLAN-V2-55 gate-baseline-separation exists only as a staged spec under .plan (off limits) and in no tracked file (git grep PLAN-V2-55 and gate-baseline-separation over doc finds nothing); whether it edits ChangeLedgerRecord or the codec cannot be read.

## Expected Surface

- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/store/change-ledger-record.schema.json`, `pm-core/src/main/resources/de/planmarshall/core/store/status.schema.json`
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/ChangeLedgerRecord.java`, `pm-core/src/main/java/de/planmarshall/core/store/ChangeLedgerCodec.java`, `pm-core/src/main/java/de/planmarshall/core/store/FreshnessReconcile.java` (deleted), `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatus.java`, `pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatusCodec.java`, `pm-core/src/main/java/de/planmarshall/core/store/package-info.java`
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/store/ChangeLedgerSchemaParityTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/ChangeLedgerRecordBuilderContractTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/ChangeLedgerTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/DeletedMemberRefusalTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/FreshnessReconcileTest.java` (deleted), `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusCodecTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusSchemaParityTest.java`, `pm-core/src/test/java/de/planmarshall/core/store/WorkflowStatusBuilderContractTest.java`
- HYPOTHESIS: `pm-core/src/test/java/de/planmarshall/core/store/ChangeLedgerCommitRowTest.java`, `doc/specification/store-schemas/04-epic-and-change-ledger.adoc`
- OBSERVED: `pm-core/doc/developer/core-store.adoc`, `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc`, `doc/Traceability.adoc`, `pm-core/doc/adr/core-store/088-a-commit-is-a-kind-of-the-change-ledger.adoc`, `pm-core/doc/adr/core-store/089-the-freshness-reconcile-stamp-is-typed-through-the-status-codec.adoc`

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`

## Dependencies and Sequencing

- Depends on: the schema-and-store-removals plan (`status.schema.json`); PLAN-V2-185 (staged and open: it edits `FreshnessReconcile` Javadoc, `core-store.adoc` and ADRs 087-089) — this plan STARTS AFTER 185 MERGES, because the removal deletes the very file 185 edits.
- Overlaps with: PLAN-V2-55 `gate-baseline-separation` (staged; also edits `ChangeLedgerRecord`; assumed overlap on `ChangeLedgerRecord.java`, `ChangeLedgerCodec.java`, `ChangeLedgerSchemaParityTest.java`, `change-ledger-record.schema.json` — the gate decides). Recommended order: PLAN-V2-55 first, this plan rebased on it, so the exact-match parity test is written once over the final member set. Also PLAN-V2-150 (`failing_again` reads the failed-test identities): this plan lands before that half.
- Adjacent to: the owner of the writer and of the gate (`build-parsers-and-gate-honesty`; `finalize-pipeline-and-merge` reads the derivation, it does not own it); `provider-git-primitive.adoc` (one line only).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-192-change-ledger-build-row-and-freshness-reconcile.md
```

## Write-Boundary

The plan touches only its own repository source and tests; it creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message (`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary).

## Record of 2026-11-25 (analyze of PLAN-V2-185, merged as #613 `3ba79c4f`; BINDING)

The three-layer analysis of PLAN-V2-185 found statements about the freshness stamp that this plan removes with the stamp, so the outline does NOT re-word them first: `FreshnessReconcile.java:24-27` ("a future writer will name the commit", "a writer must confirm a successful build exists"), ADR core-store/089 `:28` and `:56-57` ("the writer must write the stamp in the same status transaction", "a future writer will read"), `core-store.adoc` `:356` and `:372`, the ragged re-wrap of `core-store.adoc` (lines 357, 373, 377, 1337, 1342 over 120 characters) and the stale `status.schema.json:365` description (R10 forbade a block edit; it sits inside `:363-375`, which this plan removes). The Javadoc, ADR and guide statements of PLAN-V2-185 (#613) are the text read at `3ba79c4f`; PLAN-V2-196 edits other lines of `core-store.adoc` (`:340`), so the two plans overlap on that file only. The landing is `landings/PLAN-V2-185.md`.

## Re-Ground Record of 2026-11-27 (cleanup, head 115d5987)

Binding, like every earlier record: where a claim above and this record disagree, the record is what is true at the head; the claim bullets are not rewritten. Read by read-only leaves (R21) from the head `origin/main` 115d5987; each contradicted claim is absorbed here (`rescoped: yes`).

- Claim 1: ADR core-store/088 does not contain freshness_reconcile (git grep -i: only 089, FreshnessReconcile.java, WorkflowStatus.java:64,89, WorkflowStatusCodec, package-info.java:57, status.schema.json:360, core-store.adoc, provider-git-primitive.adoc:285, Traceability.adoc, 3 tests); 088 only mentions the freshness verb.
- Claim 4: change-ledger-record.schema.json:14-27 allOf holds only the worktree_sha=unavailable rule (:15-19) and a kind=commit rule (:20-26); the existing build-only members expect, expect_result, criterion, red_tests (:71-97) carry only a 'For kind == build' description and no conditional, so the current pattern does not need a kind==build condition.
