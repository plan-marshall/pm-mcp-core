# Landing Analysis: PLAN-V2-121 — The log message tables are generated from the holders and the protocol lists are dropped for links to the Javadoc

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #339 (squash merge `cdf82f85`, 2026-11-09)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-121.md`. A pasted claim is a lead, never a fact.

Source: the runbook lane's landing message `rb-v2-121-generated-listings-001.md` (19:56Z; runbook-lane row, `harness=opencode`, model `deepseek-v4.1-flash/high`; `landing-check` `complete: false`, missing `total_tokens` and `plugin_version`, both `unknown` because the harness measures no tokens and the runtime seam returns no plugin version for the lane, the recurring defect already routed as a lesson). Corroborated by the orchestrator: PR #339 `merged` with merge commit `cdf82f85`; `runbook_lane review-gate --pr-number 339` on the merged head returns `unresolved_review_threads: 0`, `review_body_findings_total: 6`, which equals the landing's claim; `review_body_findings_handled: 6` re-read from the progress file (`.plan/temp/rb-v2-121-generated-listings/progress.md`, 8 `finding:` lines) is not contradicted. `sonar_open_new_issues=0` taken as claimed (the lane's own Sonar read; the S6213 fix `3bb39c07` is visible in the merged code). Read at `cdf82f85` by one read-only leaf; the orchestrator re-read every point marked **checked** at its `file:line`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1 The log message tables are generated | shipped-as-specified | three committed files `doc/log-messages/{pm-core,pm-runtime,pm-workflow}.adoc` rendered by `LogMessageListing` (pm-core main); `doc/LogMessages.adoc:16,22,28` hold only prose and `include::log-messages/*.adoc[]`; ADR 019 records location and form |
| 2 The check of the generated files | shipped-modified | `generatedListingIsCurrent` + `byteCheckNamesAChangedListing` in all three holder tests; the negative control proves the comparator, not the check (layer 3, L3-1) |
| 3 The protocol lists are dropped | shipped-as-specified | `WaitAnswer.WaitAgainReason` / `EndReason` enums; the nine `TaskProtocol` call sites (`:203,207,211,220,240,250,256,262,269`) construct constants; `workflow-task.adoc:28-38` links to the Javadoc; `WaitAnswerReasonsTest` pins every wire name |
| 4 Remove the checks that parse a document | shipped-as-specified | `ProtocolVocabularyParityTest` deleted; `everyRecordDocumented` and `undocumentedRecordsAreFound` gone from `RuntimeLogMessagesTest` |
| 5 The other holders | shipped-as-specified | `CoreLogMessagesTest` and `WorkflowLogMessagesTest` now byte-check their listings |
| Done When: no test opens either document | shipped-as-specified | `git grep` of the test trees at `cdf82f85`: the only `doc/` paths a test opens are the three generated listings (`CoreLogMessagesTest:55`, `RuntimeLogMessagesTest:56-57`, `WorkflowLogMessagesTest:48-49`) |

Dispositions of the lane's own residue: ADR 015 and the `.github/project.yml` comment still name the removed readers (re-read: `.github/project.yml:28-31`), and `SpecificationDocument` with `pm.specification.root` (`pm-core/pom.xml:22,23,112`) still exists with no user in tests: both are PLAN-V2-122's, in its spec. Standing rule R12 (`standing-rules.md:85`) still describes a hand edit: orchestrator-owned, see Follow-Ups.

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), by reading, no build, no test run, no write; the orchestrator re-read the points recorded as `checked` at `cdf82f85`: `WorkerCalls.java:104-116`, `SpecificationDocument.java:33-40`, `CoreLogMessagesTest.java:50-75,145-165`, `LogMessageListing.java:66-80`, `WaitAnswer.java:60-90`, `workflow-task.adoc:28-38`, `.github/project.yml:26-35`, the nine `TaskProtocol` lines and the `git grep` of `SpecificationDocument` / `pm.specification.root`. What only a run could show is tagged `not shown by reading`.

- **Dispatch:** one read-only leaf per landing — reason: reading the merged code of three holders, a generator and a protocol type is the depth the Dispatch Decision Rule exists for.
- **Verdict:** layer 1 findings (2, minor); layer 2 findings (3 minor, 1 not shown); layer 3 findings (3 minor, 4 not shown).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | Hypothesis 2 ("reasons also set in pm-runtime") has no object: the pm-runtime `WaitAgain()` and `End()` records carry no reason | checked | confirms | `WorkerCalls.java:106,114` |
| L1-2 | Done When says tests read only the generated files; true, but `SpecificationDocument` and `pm.specification.root` remain as dead residue until PLAN-V2-122 | checked | confirms | `SpecificationDocument.java:35`, `pm-core/pom.xml:23` |

**Layer 1 verdict:** findings — 2 (neither a contradiction; both already absorbed by the spec's labels and PLAN-V2-122). No `corpus set-verdict` call: PLAN-V2-121 is shipped, and a verdict persists only on a staged spec.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1, 2, 5 | `LogMessageListing.java`, `doc/log-messages/*.adoc` | `*LogMessagesTest.generatedListingIsCurrent` | none; the diff has no file outside the deliverables and the allowed bookkeeping (ADR 019, plans README, three holder guides, `core.log` package-info) | checked |
| 3 | `WaitAnswer.java:60-90`, `TaskProtocol.java:203-269` | `WaitAnswerReasonsTest` (pins `values()` and wire names, assertion messages 43 to 49 characters) | none | checked |
| 4 | deletions | n/a | none | checked |
| Record text unchanged | `git diff 363ecb48` of the three holders, comments ignored: no identifier or template changed; `ENDPOINT_NOT_READY` (144) only moved behind its enlarged Javadoc | listings equal the holder templates | none | checked |
| Description sentences moved into the Javadoc | `RuntimeLogMessages` 5 samples word for word (`PM_CORE-1`, `PM_RUNTIME-119`, `-139`, `-141`, `-144`) | n/a | the lane's "verified against origin/main's rows" holds on samples only: a full sentence-level comparison of about 100 records was not made | not_shown_by_reading |
| `workflow-task.adoc` residual list | `:33` keeps the words `drained` / `stopped` for `End` as prose; the type defines neither | n/a | a reader may take it as a code-owned list; whether the runtime answers those words is outside this repository | not_shown_by_reading |

**Layer 2 verdict:** findings — 3 minor (the `.github/project.yml` and ADR 015 text names readers that are gone: owed to PLAN-V2-122; the `drained` / `stopped` prose; the unsampled migration), 1 not shown by reading.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `CoreLogMessagesTest.java:148-158` (same in `RuntimeLogMessagesTest:315`, `WorkflowLogMessagesTest:120`) | the negative control tampers with a copy of the freshly generated text and compares that copy to the same text | proves `LogMessageListing.same` (`Arrays.equals`), not `generatedListingIsCurrent`: a defect that made the check compare expected with itself would pass the positive and the negative control | minor |
| L3-2 | `CoreLogMessagesTest.java:58` | system property `planmarshall.log-messages.regenerate` set | the check writes the committed file from the holder and compares it with itself, so it passes by construction; documented in the three developer guides, not set by the build | minor (not shown: whether any pom or CI config sets it) |
| L3-3 | `LogMessageListing.java:68-80` | a holder with an empty level | header-only table; unreachable because each module's range test asserts every level non-empty | info |
| L3-4 | `LogMessageListing.java:76` | a template with a `{name}` attribute reference or backtick | only `\|` is escaped; the byte check passes while AsciiDoc renders it differently from the Javadoc | info (no current template has one) |
| L3-5 | `LogMessageListing` in `pm-core` main | a production JAR ships a test-only reflection renderer | not in `pm-core:tests` as CLAUDE.md's Generator Artifacts section describes; ADR 019 does not record why | minor (a defensible, undocumented choice) |
| L3-6 | `WaitAnswer.java:66,85` | `WaitAgain.reason()` / `End.reason()` change from `String` to an enum | breaking API change for the assembly (`pm-mcp-server` must call `wireName()`); `toString` / `equals` semantics change too; disclosed in the PR and the landing | minor (not shown: whether the adapter compiles against the new SNAPSHOT) |

**Layer 3 not shown by reading:** the byte check against a CRLF checkout (no `.gitattributes` at `cdf82f85`; `core.autocrlf` on would fail the check); the green `verify`, `verify -Ppre-commit` and `verify -Pcoverage` claims (the squash includes the later Sonar fix `3bb39c07`, so the final state was not re-verified by reading); `L3-2` and `L3-6` as above.

**Layer 3 verdict:** findings — 6 (none blocker or major), 4 not shown by reading. Reported to the operator at once; none is staged without the operator's word (see Follow-Ups).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | `@UtilityClass`, `@NullMarked`, Javadoc on every public type | conforms | `LogMessageListing`, `core.log` package-info, the new enums |
| CLAUDE.md Code Standards | Optional / `@Nullable`, no generic catch | conforms | `IllegalStateException` for a missing level class, `IllegalAccessException` caught |
| CLAUDE.md Code Standards | assertion messages of 20 to 60 characters | deviates | untouched pre-existing assertions in the two touched test classes (`CoreLogMessagesTest.ranges`, `WorkflowLogMessagesTest` ranges / unique / planInstanceMessages) have none or one too short; the new ones conform |
| CLAUDE.md Code Standards | generator data, literal only where it is the contract | conforms | "Every datum in this class is a contract" comments; `RuntimeLogMessagesTest` lock paths from `Generators` |
| CLAUDE.md Code Standards | JUnit 5 only, no AAA comments | conforms | `@DisplayName`, `@TempDir` |
| CLAUDE.md Code Standards | `LogRecord` asserted by constant; records unchanged | conforms | no record added or changed |
| CLAUDE.md Dependencies and Versions | no dependency, no POM change | conforms | no `pom.xml` in the stat |
| CLAUDE.md Module Documentation | developer guides and one ADR in the same change | conforms | ADR 019, the three holder guides, `workflow-task.adoc` (cosmetic: mixed numbered-list markers in `core-log.adoc` and `runtime-log.adoc`) |
| CLAUDE.md Build Commands | `verify`, pre-commit, coverage green | not_shown_by_reading | see layer 3 |

## Metrics and Anomalies

- Tokens: `unknown` (the harness measures none; recorded as unobserved, never zero).
- Duration: not supplied.
- Anomalies: the executor was 0.1.2076 at start and the lane text 0.1.2088 (`skew`, regenerated, `match` at landing); `/tmp` inode exhaustion hit the coverage run twice (the second time ~47k stale `/tmp/guard-node-*` directories, 925,956 inodes freed by the operator-authorized cleanup); `self-review` reported `budget-exhausted` although no code defect was open; lane shell operations on host paths are recorded in its Rule check.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `3bb39c07`: six inline findings, all handled (inline-3, -4, -5, -6 fixed in `b32d77a0`; inline-1, -2 answered), every thread resolved; it then refused on quota after the later pushes, and the standing review authorization (qualifying review, every finding handled, re-triggered after each push, every other gate green) was used for the merge. Sourcery (optional) approved `3bb39c07`, dismissed by the later push. Provider re-read: 0 unresolved threads, 6 findings; handled 6 not contradicted.
- CI/merge: merged through the queue as `cdf82f85`; the lane merged `main` once into its branch (`ffe23794` head) and re-ran its three gates there.
- Sonar: the landing reports `sonar_open_new_issues=0` and `quality_gate_status=ok`; S6213 at `LogMessageListing.java:74` was fixed in `3bb39c07` (re-read: the loop variable is `logRecord`).

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-121 --status shipped`
- [x] row `pr` stamped — `#339`
- [x] row `landing` stamped — `landings/PLAN-V2-121.md`
- [x] row `plan_marshall_plan_id` stamped — `v2-121-generated-listings`
- [x] epic.md narrative reconciled against the queue rows
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- PLAN-V2-122 (`adoc-reading-retired`, already staged) owns the ADR 015 and `.github/project.yml` text, `SpecificationDocument` and `pm.specification.root`; the lane's residue is its input and needs no spec change.
- Standing rule R12 (`standing-rules.md:85`) should say the listing is regenerated from the holder, not edited by hand: an orchestrator edit of the rules file at the next revision.
- L3-1 (negative control proves only the comparator) and L3-5 (`LogMessageListing` placement) are offered to the operator; if wanted they fold into the next test-conformance plan (PLAN-V2-125) rather than a plan of their own.
- The `assembly pm-mcp-server` adapter must call `wireName()` when it takes the SNAPSHOT: an owed outside item (owner-register, plan-marshall-mcp).
