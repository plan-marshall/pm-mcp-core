# Landing Analysis: PLAN-V2-59 — The worker egress record

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #487 (`b83d7f2c`, merged through the merge queue 2026-11-17, runbook lane, opencode, `rb-v2-59-egress-record`)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message `rb-v2-59-egress-record-001.md`) after verifying claims against git, the PR and one read-only three-layer leaf; a pasted claim is a lead, never a fact.

## Deliverable Fidelity vs Spec

Four of five deliverables met as the lane reports (`deliverables_done=4` of 5); the fifth (D1) is met except one clause the lane's reading of the standing rules forbids.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 specification parts and package file | shipped-modified | the member and kind blocks and the § Worker Egress Proxy text are in; the "Remaining paragraph" the Done-when asks for was not written because standing rule R05 (revision r7) forbids a paragraph that says "Remaining:"; the remainder is one pointer sentence at `02-confinement-and-environment.adoc:168` plus the list in the package file; the leaf could not read the rules file; the orchestrator re-read R05 r7 in `standing-rules.md` and it says what the lane quotes (layer 1, L1-4) |
| D2 the member `egress` on the job record | shipped-as-specified | `JobRecord.Egress` and `JobRecordCodec` agree member by member; `JobRecordSchemaParityTest`, `JobRecordCodecTest` cases named by the lane exist |
| D3 `SubprocessJobRunner` writes it | shipped-as-specified | `SubprocessJobRunner.java:1820` (`.egress(worker ? Egress.UNENFORCED : null)`, re-read at `b83d7f2c`); the diff is that one expression and its import |
| D4 the `egress` activity kind | shipped-as-specified | `ActivityEntry.java:402,417` hold the member to its kind; masking fix `SecretRedactor.java:680` (re-read) masks a destination below the 3-character floor whole; `ActivityJournalMaskingTest:99` |
| D5 guides, ADRs, Traceability | shipped-as-specified | ADR `core-store/072`, `core-queue/010`, guide lines the lane names |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`), its own leaf because each landing is read separately; the orchestrator re-read the points marked "re-read".
- **Verdict:** layer 1 findings (3); layer 2 findings (3, minor); layer 3 clean (no blocker or major; one cross-repository point not shown by reading).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | the Watch has items that name egress | checked | refutes | `doc/implementation-watch/job-runtime.adoc:331,666` (PM-WATCH-JOB-45, -46) |
| L1-2 | `egress` is non-null exactly for kind `llm` | checked | refutes | `job-runtime/02-confinement-and-environment.adoc:199-201` gives `build` and `project_script` records values including `open`; the new enum has no `open` (the lane resolved it in the text: null "today") |
| L1-3 | the 259-character destination bound covers what the proxy hands over | checked | refutes | the bound is the record's own; `WorkerEgressProxy.java:193` passes any request-line token up to 8 KiB (see layer 3) |
| L1-4 | D1's Remaining paragraph clause is consistent with the standing rules | checked by the orchestrator (the leaf could not read the rules file: not_shown_by_reading) | refutes | `standing-rules.md` revision line, r7: R05 amended to "condense and point without an 'Implemented by' or 'Remaining:' paragraph"; the spec's First Instruction names r4, whose R05 asked for the paragraph: **the Done-when is the orchestrator's authoring defect against its own later rule, not the lane's miss** |

**Layer 1 verdict:** findings — 4. The re-ground record of the spec already named L1-2 (the lane read it).

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D1 | specification files, package file | links check | none | checked |
| D2 | `JobRecord.java`, `JobRecordCodec.java`, schema `:92,:196` | parity and codec tests | **no test pins the `allOf` rule at `job-record.schema.json:196`** | checked |
| D3 | `SubprocessJobRunner.java:1820` | `EgressJobTest` (three cases) | none | checked |
| D4 | `ActivityEntry.java`, `ActivityEntryCodec`, `SecretRedactor.java:680` | `ActivityJournalMaskingTest:99` and four more | `JobRecord.requireKindRules` refactor and a new Sonar `@SuppressWarnings` on `readDocument` (follows the repo's S3776 pattern) are explained by no deliverable | checked |
| D5 | guides, ADRs | links | none | checked |

The adapted test classes the lane names (`JobRecoveryStepTest`, `SupervisionPassTest`, builder contract tests) are in the diff; whether the landing lists them outside the squash message is not shown by reading (the landing message does list them, §2).

**Layer 2 verdict:** findings — 3 (minor).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `WorkerEgressProxy.java:193` | a request-line token of 8 KiB to the activity sink once a producer exists | `ActivityEntry.Egress` throws outside 3 to 259 characters; no ADR, guide or package file says the future producer must bound it first; nothing calls the proxy today | minor |
| L3-2 | `ActivityEntryCodec.java:184` | every journal line now carries `"egress":null` | a closed activity schema in `pm-api` or the clients would reject every entry; the landing does not say it was checked | minor, not shown by reading (outside this repository) |
| L3-3 | `activity-entry.schema.json:24` | a destination with a trailing line break | whether the schema pattern refuses it depends on the validator's regex engine; the record refuses `\n` and `\r` itself | minor, not shown by reading |
| L3-4 | `02-confinement-and-environment.adoc` bullet "What a worker went through" | — | lost its clause that the worker block names egress (the member is top-level now); not named in D1 or the lane's report | info |

**Layer 3 not shown by reading:** L3-2 and L3-3; a run shows them. No generic catch, no log message, `@NullMarked` and `Optional` rules hold.

**Layer 3 verdict:** clean at blocker and major; findings — 4 (minor and info).

### Conformance to the Project's Own Rules

Twelve rows from the leaf: all conform or are not shown by reading; none deviates. (`@UtilityClass` conversion of `SecretRedactor`, `SecretRedactor.java:77`, matches the spec's Carried Open Items.)

## Metrics and Anomalies

- Tokens: `unknown` (the runbook lane measured none, harness opencode); `plugin_version` `unknown`.
- Anomalies: CodeRabbit refused on quota at 19:41Z (next included review 46 minutes), the lane waited 90 minutes and the bot reviewed automatically at 20:53Z and 22:06Z; Sourcery refused (7-day diff budget, resets 2026-11-19); the Quarkus-alignment job failed on a Maven Central HTTP 429 and passed on re-run; one `verify -Ppre-commit` bound expiry at 330 s, re-run at 900 s; one `status: indeterminate` build (selector matched no test), re-issued in two steps. The lane's rule check names two shell file-inspection calls (`ls`, `wc -l`) it should have made with file tools; guard `none` on this harness (unenforced).

## Routing and Merge Behavior

- Review: CodeRabbit (required, `quota_limited`) reviewed on `1500fb36` (8 findings) and `2592fe56` (2 findings); Sourcery refused. `runbook_lane review-gate --pr-number 487` on the merged head: unresolved threads 0 (matches), body findings total 10 (matches); `review_body_findings_handled` 10 is **not re-verified** (the lane's progress file holds 8 `finding:` lines; the lane reports 4 fixed and 6 answered with a reason; the difference of two is not evidence of a false claim, the threads are all resolved).
- CI/merge: merged through the merge queue as `b83d7f2c`; two base merges (the lane's own, of `origin/main`) brought back main's ledger commits only.
- Sonar: `sonar_open_new_issues=0`, quality gate ok on the lane's read (not re-read: no Sonar read verb is available to a plan-less caller here beyond the PR's own check, which was green); first analysis had seven new-code issues and a later `javabugs:S6416`, all fixed; `open_issue_rules=none`.
- Parallelization: running beside PLAN-V2-96, 82 and 145; no collision observed. The gate had reported 6 shared files with PLAN-V2-135, which is staged and unaffected.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#487`
- [x] row `landing` stamped `landings/PLAN-V2-59.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-59-egress-record`
- [x] `epic.md`: Watch for the landing (residues placed), PLAN-V2-135 annotation
- [x] resume anchor, view regenerated

## Follow-Ups

- L1-1, L1-2 (`build` and `project_script` egress values) and the owed producer: the package file `job-confinement-and-environment.adoc` stays `open` (the enforcement is not done); PLAN-V2-149 owns the confinement records and checks and reads these (its claim 1 was re-grounded today).
- Minor layer-2 and layer-3 points: PLAN-V2-114 item 50.
- The `egress:null` on every journal line against the clients' schema: owed to `pm-mcp-clients` and `plan-marshall-mcp` (the operator carries it).
