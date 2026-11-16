# Landing Analysis: PLAN-V2-102 — every assertion of the rest of `pm-core` carries a message, and the scanner is hardened

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #446 (merge `684039b2`) and #447 (merge `393b0210`), both `merged` through the merge queue; #446 = 71 changed files (70 tests + the scanner), #447 = 33 files; `git diff --stat 27d28bb7 393b0210` is 104 files, +3141 / −1847, every path under `pm-core/src/test/java` (none under `src/main`, none a doc)

> Landing record written by the `analyze` drain of 2026-11-15 from the message `rb-v2-102-assertion-messages-core-rest-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, effort high; `total_tokens` unknown, `plugin_version` unknown: `landing-check` reports `complete: false`, missing `total_tokens` and `plugin_version`, the known defect, E27 of the owner register, no new line). The facts fence names #446; the second pull request is named in the body (`393b0210`). Every point marked re-read was read again by the orchestrator at its `file:line` at `393b0210`, not taken from the lane or the leaf.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 a message of 20 to 60 characters on every assertion of the scope | met, by the leaf's re-parse; the green run is the lane's | The leaf ported the scanner's lexer and re-parsed every changed file before and after: the same number of assertion calls in each file, #446 = 1 172 pure message additions + 20 literals rewritten into range + 7 messages on inner `assertInstanceOf` calls, #447 = 574 + 8, 0 non-message edits, nothing deleted or weakened; 0 violations over all of `pm-core` at `393b0210`. The lane's 1 195 for #446 is 4 away from the leaf's 1 199 (#447 equal at 582): a counting difference, recorded, not a defect. |
| D2 a regression guard with the plan's packages added to its list | met | `SCANNED_PACKAGES` at `AssertionMessageScanTest.java:63-67` (re-read) lists `store`, `analysis`, `build`, `config`, `findings`, `guard`, `log`, `profile`, `queue`, `role`, `service`, `spi`, `toon`; the walk is recursive, so `spi/validation` and `role/corpus`, `role/qualification` are inside. `spi/validation` was dropped as redundant in #447, which is why #446's 1 276 over-counts the 1 195 distinct sites by 81 (the claim holds). |
| D3 harden the scanner | met, with the edge forms below | All seven added names are in `ASSERTIONS`, `fail` also in `MESSAGE_FIRST_ASSERTIONS`; angle tracking, the floating-point tolerance rule, the whitespace-or-comment rule, the offender list (first fifty and the count) and the 20/60/19/61 boundary controls are present; the class is 649 lines against the 400-line budget of `junit-core` (402 at the base), and its Javadoc `:54-56` records the reason, as the spec allows. The test method count of the scanner stays 4 (no `@Test`, `@ParameterizedTest` or `@Nested` line added or removed in any of the 104 files). |
| Done-when: Sonar, build, links, review | met, partly by the lane | Sonar re-read for `pullRequest=446` and `447`: 0 open issues, gate `OK` for each; review threads re-read through the provider: #446 8 threads, 0 unresolved, #447 2 threads, 0 unresolved, equal to the landing's 8 and 2 inline findings. The verify, coverage, integration and links runs and the three mutation artifacts are the lane's claims (not re-run, not re-verified). |
| Expected Surface | met | exactly the 15 directories and the scanner; the leaf found no unexplained file; the three unscanned test directories are named in Layer 2. |

## Three-Layer Analysis

Read by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), no build, no test run, no write; reason: every full ship is read on its own leaf. The orchestrator re-read the points marked above and below.

- **Dispatch:** one read-only leaf per landing — reason: reading merged code is the depth the dispatch rule exists for.
- **Verdict:** layer 1 clean (one minor counting difference); layer 2 clean with three directories outside the guard; layer 3 findings (8 minor, none a defect of what shipped).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | D1 to D3 are met in the spec's own words | checked | confirms | the table above |
| L1-2 | the class exceeds 400 lines (649) | checked | confirms, allowed | Javadoc `:54-56` states the reason, grew by 247 lines |
| L1-3 | the two pull requests are cut at the spec's State line, each under 100 files | checked | confirms | #446 71 files, #447 33 |
| L1-4 | the lane counts 1 195 sites for #446, the leaf 1 199 | checked | minor | a counting difference, not a code difference |
| L1-5 | the HYPOTHESIS "the scanner can be a plain regex" | checked | refuted as stated, confirmed in substance | the shipped scanner is a lexer plus a parenthesis/angle-depth splitter, not a regex; nested lambdas are handled because the scan visits every identifier |

**Layer 1 verdict:** clean; the spec's words are met.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D1 | 104 test files, message additions only | `everyAssertionOfTheScannedPackagesCarriesAMessage` | none | checked; the green run is not shown |
| D2 | `AssertionMessageScanTest.java:63-67` | the same test; a planted missing message sends it red (lane artifact, not re-run) | none | checked |
| D3 | `:76-86` names, `:148-164` `assertEveryKnownName`, `:167-178` tolerances, `:181-190` planted shapes, `:193-200` boundaries, `:243-254` the printed cut | the four test methods call these private helpers | none | checked |

Directories of `pm-core/src/test/java/de/planmarshall/core/` outside the guard: the top level (`BuildGuardsIT`, `ProductCorpus`, `SchemaResource`, `SchemaResourceTest`, `SpecificationDocument`), `id` (3 tests) and `testing` with `testing/generators`. The spec's State line calls them clean and out of scope; the leaf found 0 violations in them today; they are unguarded (L3-8). No test was weakened. **Layer 2 verdict:** clean; one point not shown by reading (that a build is green).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `AssertionMessageScanTest.java` `startsAType` (re-read at `:543-545`) | `new HashMap<java.lang.String, String>()` as an argument | a type argument that starts with a lowercase letter is not read as a type, the comma splits the argument list, the third part is taken for a message: the call passes with no message (false negative) | minor, latent (not found in the 15 directories) |
| L3-2 | `isTolerance` (re-read at `:443-454`) | `assertEquals(EXPECTED_D, actual, EPS)`, `assertEquals(Math.PI, v, EPS)` | a non-literal third argument is read as a message unless an operand carries a floating-point literal; the Javadoc `:46-49` states the first case | minor |
| L3-3 | `MESSAGE_FIRST_ASSERTIONS` (re-read at `:388-393`) | `fail(cause)` | any non-literal first argument counts as a message; the Javadoc says "message (or cause)" | minor |
| L3-4 | `:419-421`, `:434-436` | `"short" /* x */` | a literal followed by a comment is not measured; the Javadoc states the limit | minor |
| L3-5 | `FLOATING` `:95-98` (pattern re-read, no word boundary; whether `isFloatingLiteral` uses it with `matches` or `find` was not read) | `assertEquals(ITEM_2D, actual, MESSAGE)` | `2D` may be read as a tolerance (false positive) | minor, plausible |
| L3-6 | `:495-496`, `:525-539` | `assertEquals(a < MAX, b > 0, "msg")` | `<` read as opening a type argument swallows the comma: "no message" (false positive) | minor, latent |
| L3-7 | the scanned non-store packages | about 71 dynamic messages (`level`, `why`, `because`, `name`, `thrown.getMessage()`; `CoreLogMessagesTest:66,70,106-108`, `ProfileValidatorTest:73-79`, `RoleArtifactValidatorTest:190-192`) | the guard sees only "a message is present"; the CLAUDE.md rule of 20 to 60 characters that say what is checked cannot be shown for them; pre-existing, the spec carves out only the 44 of `store` | minor |
| L3-8 | `:63-67` | a new package, or `id`, `testing`, the top level | the guard is a whitelist: a new test package is unguarded | minor |

Message quality (leaf, sampled 25 per pull request plus 50 by heuristic): 1 746 added messages, 1 721 distinct, none repeated more than three times, no placeholder pattern, none that says the opposite of its assertion; three imprecise (`QualificationValueTest:86`, `OutlineCoverageTest:51`, `QualificationCheckTest:245`). **Layer 3 not shown by reading:** that the guard passes at `393b0210`, the three mutation artifacts, the length rule of the project as counted by the scanner (source characters between the quotes, `:424`; none of the added messages has an escape or a non-ASCII character). **Layer 3 verdict:** findings — 8 (minor, none a defect of what shipped).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|---|---|---|---|
| CLAUDE.md Code Standards | every assertion carries a message of 20 to 60 characters that says what is checked | conforms for every literal; the ~71 dynamic messages are unmeasured | L3-7 |
| CLAUDE.md Code Standards | JUnit 5 only, no Mockito, Hamcrest; no AAA phase comments; no `Random`, no `UUID.randomUUID()` as test data | conforms | none added in the changed files; the scanner uses `Generators.letterStrings` |
| CLAUDE.md Dependencies | no new dependency | conforms | no pom in the 104 files |

## Metrics and Anomalies

- Tokens: unknown (the harness measured none; recorded as unobserved, not zero).
- Duration: not carried.
- Anomalies, the lane's own: the executor-skew pre-check answered `skew` (0.1.2133 against 0.1.2135) and the operator chose regenerate; the self-review stands at `budget-exhausted` (three rounds on #446, the operator chose "Proceed, findings recorded"); one self-review reviewer ran `./mvnw` directly, a build-wrapper call the lane forbids (recorded by the lane, not repeated); two dropped edits repaired by the lane (a closing brace in `LfsPointerTest`, a missed assertion in `JsonlRowReaderTest`). No degraded build result (14 builds, `in_process_fallback` 0).

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed #446 with 8 inline findings, all defects (two reversed scope-refusal messages, a preferred-harness message describing the other scenario, a null state name called a primitive, four scanner holes), fixed and replied; on #447 2 inline findings, both real, fixed. Re-read against the provider: 8 and 2 threads, 0 unresolved, equal to the landing. `review_body_findings_total: 2`, `handled: 2`. Sourcery (optional) refused both pull requests on size ("larger than the review limit of 150,000 diff characters"); if it is wanted back the diff has to shrink below that.
- CI/merge: merged through the merge queue; `684039b2` and `393b0210` on `main`.
- Sonar: landing `sonar_open_new_issues=0`; re-read 0 and gate `OK` for each pull request: equal; the two issues found on the first #446 head (`java:S5961`, `java:S1172`) were fixed in code in `3781c559`.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#446, #447`
- [x] row `landing` stamped `landings/PLAN-V2-102.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-102-assertion-messages-core-rest` (the sender, not typed by hand)
- [x] epic.md narrative reconciled
- [x] leftovers placed (below); `landings_incomplete`: 1 (`total_tokens`, `plugin_version` unknown: the known defect, register E27)
- [x] resume anchor updated, `queue-view.md` regenerated

## Follow-Ups

- L3-1 to L3-6 (the scanner's edge forms), L3-8 (the whitelist) and the 649-line class → the carried section of PLAN-V2-103, whose first-emitted plan of 103/104/105 extracts the scanner into `pm-core:tests` (operator decision 2026-11-15): harden it in the same move, derive the package list by walking instead of listing, split the fixtures from the class without separating a control from the code it proves.
- L3-7 (about 71 dynamic messages outside `store`) → PLAN-V2-114 (`analysis-findings-fixes`), new item 28.
- The three unscanned directories (top level, `id`, `testing`) → the same extraction (L3-8).
- Counting note: #446's body gives 1 276, the distinct figure is 1 195 (the lane) or 1 199 (the leaf); the merge stands.
