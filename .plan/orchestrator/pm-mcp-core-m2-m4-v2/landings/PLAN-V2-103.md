# Landing Analysis: PLAN-V2-103 — A message on every assertion of the pm-workflow and provider tests (partial landing)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #450 (merge `cd9da049`, head `7b32d030`, 9 files) and #452 (merge `e8e7c125`, head `2d2c2ee0`, 15 files), both `merged` through the merge queue

> Landing record written by the `analyze` drain of 2026-11-16 from the message `rb-v2-103-assertion-messages-workflow-and-providers-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, effort high; `total_tokens` unknown, `plugin_version` unknown: `landing-check` reports `complete: false`, missing `total_tokens` and `plugin_version`, the known defect E27). **This is a stopping report, not a full ship:** the lane stopped on the operator's answer "Stop; land PR1 and the provider PR", the `pm-workflow` half of the plan was not done, and the row is shipped for what landed with the remainder staged as PLAN-V2-152. Every point below marked re-read was read again by the orchestrator at its `file:line` at `8e93002f` (`git show`), not taken from the lane or the leaf.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| State item (1) the extraction of the scanner into `pm-core:tests` | shipped-as-specified | `pm-core/pom.xml:147` adds `de/planmarshall/core/testing/assertions/**` to the include list of the existing `test-jar` execution and `:152-153` keep `*Test` out (re-read); no plugin and no dependency added to `pm-core`. |
| State item (2)/(5) thin guards | shipped-as-specified for `pm-core` and the six provider modules; **not delivered for `pm-workflow`** | six `AssertionMessageScanTest` guards, one per provider module (re-run: one each of ci, git, github, gitlab, http, sonar), each with `BASE_PACKAGE = "de/planmarshall/provider/<x>"`, `AssertionMessages.scan(root, BASE_PACKAGE, Map.of())` and no scan logic (github's re-read); the `pm-core` guard has `BASE_PACKAGE = "de/planmarshall/core"` and two exemptions (`AssertionMessageScanTest.java:34-45`, re-read). `git ls-tree` finds no `AssertionMessageScan` file in `pm-workflow` (re-run: 0). |
| State item (3) the eight findings of the 102 landing | shipped-modified (3 fixed, 5 partial) | see layer 3 and the follow-ups: fixed 3, 4, 5 and the whitelist as a walk; partial 1, 2, 6, 7, 8. |
| State item (4) ADR 012, CLAUDE.md | shipped-as-specified | ADR 012 amended in Status, Date, Decision, Consequences and Implementation, README index and `pm-core/README.adoc` updated; CLAUDE.md "Generator Artifacts" names the scanner (leaf; CLAUDE.md of `8e93002f` carries it, as the project instructions of this session show). |
| D1 a message on every assertion of the scope | **not met** | providers: the 11 short messages fixed (github 4, gitlab 7; each 21 to 30 characters, the old 16 to 19; leaf, counted); `pm-workflow`: about 1 690 sites in about 76 files open; nine converted files sit on the pushed branch `feature/v2-103-assertion-messages-workflow-wip` (`f53df72a`, re-run with `git ls-remote`). |
| D2 the guard | met for `pm-core` and the providers, not for `pm-workflow` | the guard can only land green with the last workflow pull request, because it scans the whole `de/planmarshall/workflow` tree (the spec's State item 5 and its "each pull request green on its own" cannot both hold with the guard in the first pull request). |
| Done-when "the test method count is the same" | not borne out | `pm-core` scan methods went from 4 to 8 (guard 1, `AssertionMessagesTest` 6 `@Test` plus 1 `@ParameterizedTest`, re-run); the spec condition was written before the extraction; the lane's PR body says it. |

## Three-Layer Analysis

Read by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), no build, no test run, no write; the leaf also ran two throwaway regex ports over `git show` text (heuristics, not the real scanner) for the clean-tree counts. The orchestrator re-read the points marked above and in the tables.

- **Dispatch:** one read-only leaf per landing — reason: reading merged code is the depth the dispatch rule exists for.
- **Verdict:** layer 1 findings (2); layer 2 findings (partial delivery and narrative counts, 8); layer 3 findings (three silent or loud scanner shapes, 3 minor; no defect of what shipped).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | the first pull request can carry the `pm-workflow` thin guard and be green on its own | checked | refutes | the tree held 2 108 messageless assertions at `ab5c15fb` (the lane's count); a guard in the first pull request is red; the guard can only land last; this is the structural reason the plan stalls, not a lane defect |
| L1-2 | Done-when "the test method count is the same" | checked | refutes | the operator-decided extraction moves the controls into a new class: 4 to 8 scan methods |
| L1-3 | the eight carried findings are addressable in one move and each has a named line | checked | confirms | the carried section is consistent with the State section; its scanner wording is correctly overridden |

**Layer 1 verdict:** findings — 2 (1 major as a structural cause, 1 minor); the plan is not defective, the spec sequenced a green-on-its-own guard first.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| extraction | `pm-core/pom.xml:144-153`, `AssertionMessages.java` (637 lines, re-read), `scan(sourceRoot, basePackage, exemptions)`, `testSourceRoot` (`:142-153`, re-read) | `AssertionMessagesTest`: controls for findings 1 to 6 exist and would discriminate (`qualified-type-argument`, `tolerance-declared*`, `fail-cause/exception/new`, `trailing-comment`, `item-name`, `upper-case-comparisons`); that they were red first is not shown by reading | none: #450 = extraction, ADR, CLAUDE.md, README, ADR index, pom comment; #452 = six poms, six thin tests, three message-fix test files | checked |
| provider guards | six thin tests | each scans its whole module test tree (test-tree listing: ci 9, git 22, github 9, gitlab 6, http 15, sonar 4 files, all under the base package; leaf) | the only dependency change in the six poms is `pm-core` `test-jar`, test scope, no version literal (managed in the root pom), inside the ADR 012 matrix | checked |
| 11 message fixes | `GitHubClientTest:221,225`, `InstallationTokensTest:198,252`, `GitLabClientTest:307,459-461,479,483-484` | assertions otherwise unchanged; no diagnostic dropped | none | checked |

- **Verify-first answered by design:** `testSourceRoot` walks up from the working directory and accepts the first `src/test/java` that contains the base package (re-read), so the shared scanner reads the consumer's own test tree regardless of the jar's location; only a green CI run would show it works (the lane reports green heads; not re-run).
- **`pm-core` coverage after the rewrite is a superset** of the old 13-package whitelist: `BASE_PACKAGE = "de/planmarshall/core"` walks every file; newly covered are the five top-level files and any future package (leaf).
- **The two exemptions remain:** `core/id` (3 files) and `core/testing` (14 files, including `AssertionMessagesTest` itself) are kept "clean when the walk replaced the list" (`AssertionMessageScanTest.java:34-45`, re-read); a leaf heuristic port found 0 offenders in 106 calls across them, so the exemptions are unnecessary and leave the scanner's own tests unguarded: finding 7 is half done.
- **The scanner is 637 lines** against the 400-line budget (re-run `wc -l`); the Javadoc `:52-54` gives a rationale; the fixtures were split out (guard 60 lines, `AssertionMessagesTest` 286): finding 8 is partial.
- **Narrative counts off:** the lane's "10 controls in `AssertionMessagesTest`; guard count 4 to 7" is not borne out: 6 `@Test` plus 1 parameterized (3 values) = 7 methods and 9 invocations; the `pm-core` guard goes from 4 to 1; the scan total from 4 to 8 methods. The log cited as red-first evidence for the six latent controls (`118fa866…`) is described by the lane itself as "controls green": no red-first evidence is given for them (only the provider guards' logs `14e78aa1`, `ff866532` are given as red; CI logs not readable by reading).
- **The facts block is stale:** it says `merge_state=open`, `deliverables_done=0`, `pr=#452`; the provider returned `merged` for #452 and #450 (`ci pr view`, re-run). The narrative body is correct on the merges. A **false claim** of the landing facts (a merge state the provider contradicts), recorded as such.
- **`pm-core/pom.xml:141-143` overstates:** "the thin guards of pm-workflow and the provider modules scan their own test trees": the `pm-workflow` guard does not exist (and the runtime's lands with PLAN-V2-105); ADR 012's sentence is normative and not a claim of existence.
- No assertion removed or weakened: the old per-package "no source scanned" check became `scan()`'s single violation and the tolerance loop became a `@ParameterizedTest` (leaf).

**Layer 2 verdict:** findings — 8 (1 not-delivered half, the rest minor or info).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `AssertionMessages.java:398-432` (re-read) | `assertTrue(v < MAX && v > MIN, "msg")` or `assertEquals(X < MAX, Y > MIN, "msg")` | `opensTypeArgument` is true (`startsAType` accepts upper case, `followedLikeAType` rejects only a digit or an operator after `>`), the comma is swallowed, the spans collapse and a false "no message" is reported: loud, not silent; finding 6 only partly fixed | minor, latent |
| L3-2 | `AssertionMessages.java:480-509` (re-read) | `assertEquals(Map.of(), new HashMap<String, int[]>())` | a primitive-array type argument does not start a type (`startsAType`: upper case, `?`, `@`, or a lower-case chain with an upper-case segment), the comma splits, ` int[]>()` is read as the message and the call passes with none: silent false negative; finding 1 only partly fixed | minor, rare |
| L3-3 | `AssertionMessages.java:84-100` (re-read) | `assertEquals(EXPECTED, actual, Fixtures.EPS)` with the delta declared in another class or imported statically | `FLOATING_DECLARATION` runs over the single file, so the delta is read as a message: silent false negative; declared as F7 in #450, still silent | minor |
| L3-4 | `AssertionMessages.java:144-153` (re-read) | the pm-core guard run from the repository root (an IDE) | `IllegalStateException` instead of a scan; the reactor fallback was dropped (F10, declared); correct under Maven (working directory = module base); an ancestor never holds `src/test/java`, so no wrong-tree match | info |

Lexer reviewed (leaf, not re-read): the masked view keeps length and newlines and handles line and block comments, escaped strings, char literals, text blocks; qualified calls and nested lambdas are found by the identifier scan and depth counting; the one edge found (a block comment opening `/*/`, `:550-552`) is negligible. Dynamic messages (constant, concatenation, text block, supplier) stay accepted and unmeasured (Javadoc `:31-35`); the extraction neither reduces nor counts them (the "about 71" of the 102 landing is PLAN-V2-114 item 28). `Files.walk` is in try-with-resources and `IOException` maps to `UncheckedIOException` (leaf). **Layer 3 not shown by reading:** the 10 657-test builds, Sonar 0 on both heads (re-read below), CodeRabbit's clean review of #452, the CI runs `118fa866`, `14e78aa1`, `ff866532`.

**Layer 3 verdict:** findings — 4 (3 minor, 1 info; none a defect of what shipped).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|---|---|---|---|
| CLAUDE.md Code Standards | every assertion carries a 20 to 60 character message | conforms | the six thin tests, `AssertionMessagesTest` and the guard (leaf: 24 to 52 characters spot-checked); dynamic messages unmeasured by design |
| CLAUDE.md Dependencies | never add a dependency or a plugin without asking | conforms | only `pm-core` `test-jar` (test scope, no version literal) in six poms and one include entry; inside ADR 012's matrix and the operator's decision of 2026-11-15 |
| CLAUDE.md Dependency Rules | no framework; a provider depends on `pm-core` | conforms | the scanner uses the JDK and Lombok already in `pm-core` |
| CLAUDE.md Generator Artifacts, ADR 012 | the test-jar carries the named packages; the amendment is recorded and indexed | conforms | `pom.xml:146-153`, ADR 012 amended |
| CLAUDE.md Code Standards | `@UtilityClass`, `Optional` returns, no generic catch, JUnit 5, no AAA comments | conforms | `AssertionMessages` is a `@UtilityClass`, `violationOf` returns `Optional`, `IOException` only (leaf) |
| junit-core budget (R20) | a class at most 400 lines | deviates (minor) | `AssertionMessages` 637 lines, rationale in Javadoc |
| spec State item 5 | a thin guard per module over its own tree | deviates | providers present; `pm-workflow` absent by the operator's decision; `pm-runtime` is PLAN-V2-105 |
| spec Standing Constraints | at most 100 files per pull request, each green on its own | conforms | #450 9 files, #452 15 files; green heads per the landing (not re-run) |

## Metrics and Anomalies

- Tokens: unknown (the harness measured none; recorded as unobserved, not zero).
- Duration: not carried.
- Anomalies: the lane asked the operator how to proceed once the size was clear (options: stop and land; continue the workflow work; continue only the `dsl` package) and the operator chose to stop; one stray comment on #452 ("Fixed in 94c5933b: …", a `pr reply` issued while the reply slot held a thread-reply body), recorded by the lane, naming no false fact; two CI failures re-run, not masked (Maven Central 429 in `quarkus-alignment`; the flaky `CoverageItemTest.keepsAnAbsentFileAndBranch`).

## Routing and Merge Behavior

- Review: CodeRabbit (required) refused #450 on quota at 22:42Z, reviewed at 00:42Z with six inline findings (one major: `Optional` instead of `null`), all six fixed in `94c5933b`, threads answered and resolved; #452: "no actionable comments". Re-read against the provider: `review-gate` reports 0 unresolved threads on both; #450 shows 6 body findings, #452 0. **The landing facts state `review_body_findings_total=0`, which is true of #452 only** (`pr=#452`); the six findings of #450 are in the narrative and in no `finding:` line (the lane's progress file for this run holds 0 `finding:` lines): `review_body_findings_handled` for #450 is **not re-verified** (the zero unresolved threads show they were resolved). Sourcery (optional) refused on its budget.
- CI/merge: both merged through the merge queue; `executor-skew` `match`.
- Sonar: the landing's `sonar_open_new_issues=0`; re-read for `pullRequest=450` and `452`: total 0, gate `OK` on both — equal.

## Reconciliation Actions

- [x] row `status` → `shipped` (for what landed; the remainder is PLAN-V2-152)
- [x] row `pr` stamped `#450, #452`
- [x] row `landing` stamped `landings/PLAN-V2-103.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-103-assertion-messages-workflow-and-providers` (the sender, not typed by hand)
- [x] epic.md narrative reconciled
- [x] leftovers placed (below); `landings_incomplete`: `total_tokens`, `plugin_version` unknown, recorded under the known Open Defect E27
- [x] resume anchor updated, `queue-view.md` regenerated

## Follow-Ups

- **The `pm-workflow` message work and its guard:** the new staged plan PLAN-V2-152 `assertion-messages-workflow` (the lane's residue 1 to 3; carries the WIP branch and the Expected Surface of PLAN-V2-103's workflow directories, plus `envelope/`). No existing staged plan owns it.
- **The scanner residue** (L3-1 to L3-3, the two exemptions, the 637-line class, the overstated pom comment): PLAN-V2-114 item 29 (new; surface extended in the same edit).
- **PLAN-V2-105 is unblocked:** the extraction has merged on `origin/main` (`cd9da049`); its landing message should say whether it held its thin-test PR.
- The facts-block false claim (`merge_state=open`, `deliverables_done=0` for a merged pull request) is recorded here; the lane's own facts producer is plan-marshall's (E27's neighbourhood), not routed.
