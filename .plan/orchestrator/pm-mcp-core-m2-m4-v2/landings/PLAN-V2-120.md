# Landing Analysis: PLAN-V2-120 — The stalled threshold and the unit frontmatter limits move into properties files the code reads and the specification includes

epic: pm-mcp-core-m2-m4-v2
workstream: WS-12
pr: #321 (merge commit `7d1720c8`, single parent `9c867f05`; 29 files; merged 2026-11-08T20:09Z, runbook lane, harness opencode, `deepseek-v4.1-flash/high`)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-120.md`. A pasted claim is a lead, never a fact.

Source: the plan's landing message `rb-v2-120-limits-in-data-files-001.md` (21:17Z). `landing-check` `complete: false`: `total_tokens` and `plugin_version` are `unknown` (folded into the standing Open Defect on the runbook lane); `surface_delta` is `unmeasured` (neither a declared nor a realized footprint supplied). Reconciled in full. Spec source `ledger_worktree`, no `spec_differ`, executor skew `match` (lane 0.1.2076, executor 0.1.2084 at landing time).

Corroborated by the orchestrator: `ci pr view 321` reads `merged`, merge commit `7d1720c81c46…`; `ci checks status 321` reads 20 checks, overall success (Maven Build `build (25)` 572 s and `build (26)` 705 s, `sonar-build` 937 s, Integration Tests, SonarCloud and Sourcery pass; CodeRabbit `NEUTRAL`, the quota refusal the landing reports). Sonar over plain HTTP (`pullRequest=321`): 0 open issues, gate `OK` (new coverage 87.2, new duplication 0.0). The landing's lane Sonar step is `n/a`; its own account of two earlier red Sonar runs (duplication 27.1 percent, coverage 27.7 percent) is not shown by the final state and is not contradicted by it.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 stalled threshold into a properties resource, read at class load, fail fast | shipped-as-specified | `stalled-scope.properties` carries the `stalled-threshold` tag and key; `StalledScopeDetector.java:56` `THRESHOLD = Duration.ofSeconds(LimitProperties.read(LIMITS, THRESHOLD_KEY))` (re-read). The reader fails with `IllegalStateException` on an absent resource, absent key, non-numeric or negative value (`LimitProperties.java:42-85`, re-read). |
| D2 the three unit limits likewise, one tag per limit | shipped-modified | the three keys and tags are in `unit-limits.properties`; `MAX_UNIT_FILE_BYTES` is read at `ProjectUnitLoader.java:117` (re-read), the other two in `UnitFrontmatterReader`. The spec placed all three in `UnitFrontmatterReader` (HYPOTHESIS); the landing records `ProjectUnitLoader` as a surface delta. |
| D3 the specification includes the tagged lines | shipped-as-specified | `timeouts/02-runtime-leases-operator-web.adoc` and `workflow-dsl/01-grammar.adoc:260-266` include the tags (re-read for the grammar document: three includes, no number in the sentence); paths resolve from where each document lives. The includes render as links on GitHub (ADR 016; the landing says so too). |
| D4 the two prose-matching tests become load tests | shipped-modified, with a test-strength gap | the old tests are gone; the new ones are in `StalledScopeDetectorTest.java:644-690` and `UnitFrontmatterReaderTest.java:669-` (re-read). **They test the reader, not the constants** (layer 2, L2-1). |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, surface `orchestrator.analyze`, resolved through `manage-config effort resolve-target`) — reason: a full-ship landing is read on three layers by its own leaf, with no size qualifier; it shared no context with another landing. The leaf wrote nothing. By reading, no build, no test run, no write. The spec, the package file the spec names (deleted by the merge; its text at the parent) and the landing message were passed as data.
- **Re-read by the orchestrator at `git show 7d1720c8:<file>`:** `LimitProperties.java:36-85`; `StalledScopeDetector.java:50-60`; `ProjectUnitLoader.java:112-120,228-250`; `StalledScopeDetectorTest.java:640-691`; `UnitFrontmatterReaderTest.java:664-695`; `doc/specification/workflow-dsl/01-grammar.adoc:258-267`; `ProtocolVocabularyParityTest.java:38-46` and every `Pattern.compile` over a test source; `Traceability.adoc` lines that carry the numbers; the `core.config` `package-info.java`; a search of the native-image metadata for the two resources. Points not re-read are marked as such below.
- **Verdict:** layer 1 findings — 3; layer 2 findings — 5; layer 3 findings — 6.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | OBSERVED: the two tests name `pm.specification.root` | checked | confirms | the diff removes the `System.getProperty` of it from both tests; neither names it at the merge. Persisted as `corroborated`. |
| L1-2 | HYPOTHESIS: all four constants lie in the two named classes | checked | refutes | `MAX_UNIT_FILE_BYTES` lay in `ProjectUnitLoader.java:112` (`:117` at the merge). The spec's own package file already named that class under "Where". Persisted as `contradicted`, `rescoped: no`. |
| L1-3 | Done-when: a search of the test sources finds no `Pattern.compile` over a specification document | checked | refutes | the condition depended on `CredentialStoreMoveRecordParityTest`, a file outside the Expected Surface that PLAN-V2-117 rewrote; the spec named no dependency on 117 and 117 was running when 120 was emitted. It held at the merge only because the lane merged `origin/main` after #320. The gate could not see it: a repo-wide search in a Done-when is no declared surface. Not a corpus claim; nothing persisted. |

Also read, no finding: the spec's "no second copy" Objective against `doc/Traceability.adoc`, which the spec lists as not surface (R19). `Traceability.adoc:1925,2019` still state the numbers in prose (the matching lines were re-read as matching; the lane names them as residue). The literal Done-when does not cover them.

**Layer 1 verdict:** findings — 3 (L1-2 and L1-3 minor, L1-1 corroborated); the dependency on PLAN-V2-115 is on `main` and untouched.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | `stalled-scope.properties`; `StalledScopeDetector.java:56` | the reader's controls fail on a defaulting reader; **no test makes the class initialiser fail**: `thresholdIsReadFromTheLimitsResource` (`StalledScopeDetectorTest.java:644-650`) compares `THRESHOLD` with the reader's reading of the same resource, so a constant hard-coded to `600` stays green | `pm-core` `LimitProperties` and its test with a fixture (a shared reader the lane added for the Sonar duplication gate; it is real surface, not in the Expected Surface), `pm-workflow` `UnitLimits`, three `alternate-limits.properties`, ADR and developer-guide edits, `Traceability`, `implementation-watch`, two specification passages made false by the move; the README row and the package file are R01/R08 | checked, refutes |
| D2 | `unit-limits.properties`; `ProjectUnitLoader.java:117`, `UnitFrontmatterReader` | same: `constantsAreReadFromTheLimitsResource` (`UnitFrontmatterReaderTest.java:669-682`) compares each constant with the reader's reading of the same resource | `ProjectUnitLoader.java` outside the Expected Surface (recorded by the lane) | checked, refutes |
| D3 | the includes | no run shows the rendering (`trace.py BROKEN LINKS 0` is the lane's claim) | — | checked |
| D4 | the old tests are gone | the boundary tests the spec names stay green and untouched by this diff (not re-read) | `Pattern.compile` over a specification document: none. `ProtocolVocabularyParityTest.java:41-46,86,94` still regexes a developer guide (`workflow-task.adoc`), which is not a specification document — the literal Done-when is met, the spirit (no test parses prose) is not, and `generated-listings` owns it | checked |
| landing claim | "the two copies of `LimitProperties` were merged into pm-core" | — | **borne out**: the merge commit adds only `pm-core/.../config/LimitProperties.java` (the diff stat lists no runtime or workflow copy). **The PR body is stale**: it still names `pm-runtime/.../start/LimitProperties.java (new)` and `pm-workflow/.../dsl/LimitProperties.java (new)`, which are not in the merge | checked, refutes |

Not shown by reading: the mutation-after runs. The lane reports two failing runs after the loader was "mutated to default 0" (`StalledScopeDetectorTest.java:664` and `:675`, two failures; the workflow test, two failures). Three controls exist per module (absent resource, absent key, non-numeric value); two failures cannot cover all three, so the absent-resource control has no shown failing run. Commit `52d9e30e` holds production and tests together, so no committed failing state exists. The job output is not readable by this reading. Every green-run claim (9488 tests, 2903/2904 integration tests, coverage) is not shown by reading; CI is green (above).

An untested branch: the second disjunct at `ProjectUnitLoader.java:239-240` (`bytes.length == Integer.MAX_VALUE && source.read() >= 0`) is reachable only when the limit equals `Integer.MAX_VALUE`; with the shipped value it is dead and no test in the diff reaches it. The `+1` boundary rests on `ProjectUnitLoaderTest` (ADR 006), which this diff did not touch and which was not re-read.

**Layer 2 verdict:** findings — 5 (L2-1 the constants have no test that can fail for them, major; the stale PR body; the mutation evidence not shown; the untested overflow branch; the parse-prose test that remains).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `LimitProperties.java:50` | a malformed escape in a properties file (`\u12`) | `Properties.load` throws `IllegalArgumentException`, neither caught nor in the `@throws` (`:38-40`); the class initialiser fails with a cause that names neither resource nor key | minor |
| L3-2 | `LimitProperties.java:81` | `…stalled-threshold-seconds=0`, `max-file-bytes=0`, `max-echo-length=0` | only a negative value is refused; a limit of zero loads silently (every idle scope is stalled at once; every non-empty unit is refused). The Javadoc says "non-negative", so deliberate; no per-limit minimum. The lane lists this | minor |
| L3-3 | `ProjectUnitLoader.java:117-118` (also `UnitFrontmatterReader.java:81,88`, not re-read) | a value above 2147483647 | `LimitProperties.read` returns `long`; `Math.toIntExact` then throws `ArithmeticException` in the initialiser, not the documented `IllegalStateException` naming resource and key | minor |
| L3-4 | `LimitProperties.java:46` | the two resources in the native `pm-mcpd` build | the lookup runs through pm-core's class and loader; an unregistered resource returns `null` and the first use of `StalledScopeDetector`, `UnitFrontmatterReader` or `ProjectUnitLoader` fails. A search of the native-image metadata of the repository finds no registration of either file (the schema files are in the same position, so the assembly may register by glob). **Not shown by reading**: only the native build in `plan-marshall-mcp` shows it | minor, unverified |
| L3-5 | `pm-core/.../config/package-info.java`; `core-config.adoc` | — | neither mentions `LimitProperties`, a new public type in a package whose guide the plan changed by adding a class; the developer guides of `runtime-start` and `workflow-dsl` do | minor |
| L3-6 | `StalledScopeDetectorTest.java:675,678,681`-class lines (`UnitFrontmatterReaderTest.java:675,678,681`); `LimitPropertiesTest` | `@EnableGeneratorController` on a class that generates nothing; three assertion lines with the message after a long call, far over 120 columns | no generated-number test of the positive path; cosmetic | minor |

Also read and noted: the class-load semantics (`ExceptionInInitializerError`, then `NoClassDefFoundError` on later touches without the cause) follow the spec's "read when the class loads"; the overflow guard at `ProjectUnitLoader.java:237-240` is correct (long cap, `Math.min`, the `+1` byte caught by the first disjunct); `LimitProperties` is thread-safe (local `Properties`, JVM-serialised initialisation) and throws `IllegalStateException`/`UncheckedIOException`/`NullPointerException`, not a generic type; no log record is owed (the spec's R12 is "none unless a load fault is logged").

**Layer 3 not shown by reading:** the verify, coverage and integration runs; the native build; how Asciidoctor renders the includes; the failing mutation runs.

**Layer 3 verdict:** findings — 6, all minor, none a defect that breaks the shipped behaviour; L3-4 is an unverified lead.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md | no new dependency or plugin; module dependency rules | conforms | no `pom.xml` in the diff; pm-runtime and pm-workflow take the reader from pm-core |
| CLAUDE.md | no generic `Exception`/`RuntimeException` thrown; absence is not `null`; public type documented; `@NullMarked` | conforms | `LimitProperties.java:48,52,72,78,82` |
| CLAUDE.md | every assertion carries a message of 20 to 60 characters | conforms | the 13 new messages read are 32 to 48 characters; pre-existing assertions in the changed files not re-checked |
| CLAUDE.md | test data from `cui-test-generator`, a literal only as the contract | deviates | `@EnableGeneratorController` on `LimitPropertiesTest` with nothing generated; `limits`, `42`, `not-a-number` are signalled by display names only in part |
| CLAUDE.md | a plan writes the developer documentation of the packages it changes | deviates | no entry for `LimitProperties` in the `pm-core` `core.config` guide or package Javadoc |
| CLAUDE.md | no number repeated where the specification can include it | conforms | `timeouts/02` and `workflow-dsl/01-grammar` include the tags; `Traceability.adoc:1925,2019` keep the numbers (R19 not surface) |
| CLAUDE.md | native-image metadata of a class travels with its module | not_shown_by_reading | no registration found; only the native build shows whether it is needed |
| Spec Done-when | `verify`, `-Ppre-commit`, `-Pcoverage` green | not_shown_by_reading | only a run shows it; CI is green (above) |
| CLAUDE.md | R01/R08: package file deleted, README row removed | conforms | `doc/plans/limits-in-data-files.adoc` deleted, its README row removed |

## Metrics and Anomalies

- Tokens: `unknown` (unobserved, never zero).
- Duration: the landing gives none; merged 2026-11-08T20:09Z, the landing message was filed 21:17Z (68 minutes later). `build (25)` ran 572 s, `build (26)` 705 s, `sonar-build` 937 s (`ci checks`).
- Anomalies: self-review ended `budget-exhausted` after three rounds with one Done-when open; the operator chose to open the PR and report it unmet, and the merge of `origin/main` met it. CodeRabbit refused on quota on the last two heads and the PR was merged on the standing authorization; two Sonar runs failed on new-code duplication and coverage before the final head. One `verify -Pcoverage` run hit its 1391 s bound and was re-run at 2400 s. After the merge the lane's worktree was dirty (the gate rewrote an import order in `SchemaResource.java`, a file `main` changed); the operator had the rewrite reverted. The lane reports one own rule break (a shell pipe).

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `79c0250d` and `aa1e705e`, 22 findings answered or fixed, threads resolved; rate-limited on the last two heads. Sourcery approved `57f37ed2`.
- CI/merge: success on 20 checks; merge commit `7d1720c8`; the lane merged `origin/main` once.
- Sonar: the orchestrator's plain-HTTP read of `pullRequest=321` returns 0 open issues and gate `OK`.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#321`
- [x] row `landing` stamped `landings/PLAN-V2-120.md`
- [x] row `plan_marshall_plan_id` stamped `v2-120-limits-in-data-files` (from the sender `rb-v2-120-…`)
- [x] layer-1 verdicts persisted on the spec (claim 0 `corroborated`, claim 1 `contradicted`, `rescoped: no`)
- [x] `epic.md`: recurrence folded into the runbook-lane `unknown` Open Defect; findings recorded as one Open Defect
- [x] resume anchor, `queue-view.md`, committed in the ledger worktree

## Follow-Ups

No fix plan is staged; the operator decides. Candidates: (1) a test that makes the class initialiser fail (or reads the constant through a seam the test can point at a fixture) so the constants, not only the reader, have a failing case (L2-1); (2) the diagnostic and range of `LimitProperties` — malformed escape, a minimum per limit, the `long`-to-`int` conversion (L3-1 to L3-3); (3) the `core.config` guide and Javadoc, and the generator use in `LimitPropertiesTest` (L3-5, L3-6); (4) whether the native `pm-mcpd` build finds the two resources (L3-4; the owner is the assembly repository); (5) the `Traceability.adoc` number copies and the stale PR body need no plan; `ProtocolVocabularyParityTest` is already owned by PLAN-V2-121.
