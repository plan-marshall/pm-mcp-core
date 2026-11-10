# Landing Analysis: PLAN-V2-18 — Native coverage report parsers: JaCoCo, Cobertura, LCOV and Istanbul

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
pr: #336 (squash merge `f97bc7f2`, 2026-11-09)

> Landing record for one shipped plan. Lives at `landings/PLAN-V2-18.md`. A pasted claim is a lead, never a fact.

Source: the runbook lane's landing message `rb-v2-18-coverage-report-parsers-001.md` (20:04Z; `harness=opencode`, model `deepseek-v4.1-flash/high`; `landing-check` `complete: false`, missing `total_tokens`, `plugin_version`, `unresolved_review_threads`, `review_body_findings_total`: the block carries no review numbers at all). Corroborated by the orchestrator: PR #336 `merged`, merge commit `f97bc7f2`; `runbook_lane review-gate --pr-number 336` on the merged head returns `unresolved_review_threads: 0` and `review_body_findings_total: 24` (the landing counted CodeRabbit's findings per review pass, "11 + 2, 4, 4, 5 + 3"; the provider total is the figure to record). `review_body_findings_handled` is **not re-verified**: the progress file `.plan/temp/rb-v2-18-coverage-report-parsers/progress.md` holds no `finding:` line, so the number cannot be reproduced and is no evidence that the landing lied. `sonar_open_new_issues=0` taken as claimed (the lane's public-API read at `aead5cbb`). Read at `f97bc7f2` by one read-only leaf; the orchestrator re-read every point marked **checked** at its `file:line`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1 The coverage model and the threshold verdict | shipped-as-specified | `CoverageReport.java:51-60`: `passed` from `overall.line()` with `>=`, the low list from `item.line()` with `<`; `CoverageReportTest` (branch-below/line-above, the reverse, exact boundary) and the two `deliberate-branch-*` fixtures pin it end to end |
| 2 JaCoCo | shipped-modified | counters, per-class line, missed methods without `<init>`/`<clinit>` (`JacocoReportParser`, `leavesTheConstructorsOut`) as specified; the reader is **not** "no DTD": `CoverageXmlReader.java:194` sets `SUPPORT_DTD` to `TRUE` (see layer 1 and 3) |
| 3 Cobertura | shipped-as-specified | `CoberturaReportParserTest` over `cobertura-high/low.xml`, the external-entity and truncated fixtures; same reader |
| 4 LCOV | shipped-as-specified | `LcovReportParser`: `DA:` matches no prefix of `readCounter`, `statement` = `line`, truncated / missing LF-LH / LH > LF refused; `ignoresTheLineDetail` can fail (DA would give 66.67 against 80) |
| 5 Istanbul and documentation | shipped-modified | Jackson streaming; malformed, truncated, trailing content, string pct, pct outside 0..100 all refused; docs and ADR `core-build/005` present; the specification section is **condensed** to a pointer paragraph and a "Remaining" line, not **deleted** as the deliverable says (a defensible deviation since the runtime wiring remains, not recorded by the lane) |
| Done When: fixtures ported, commit recorded | shipped-as-specified | `pm-core/src/test/resources/ported/coverage/PROVENANCE.adoc` records `9cd6448921e3a7026df0427af65137c3ccca8037`, the files and the behaviour of each `deliberate-` file |
| Done When: "an XML report with a DTD or an external entity is refused" | **not met as written** | the lane's claim holds: the clause cannot be met together with real JaCoCo reports; what is refused is an internal DTD subset and any entity reference (layer 1) |

## Three-Layer Analysis

One read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), by reading, no build, no test run, no write. The orchestrator re-read the `checked` points at `f97bc7f2`: `CoverageXmlReader.java:110-125,140-165,188-200`, `CoveragePercentages.java:28-62`, `CoverageReport.java:48-60`, `IstanbulSummaryParserTest.java:55-59`, `03-parsers-and-gates.adoc:44-52`, ADR `core-build/005` lines 40-70 and `PROVENANCE.adoc:1-14`. What only a run could show is tagged `not shown by reading`.

- **Dispatch:** one read-only leaf per landing — reason: the XML hardening and the four parsers are the depth the Dispatch Decision Rule exists for.
- **Verdict:** layer 1 findings (2, major); layer 2 findings (6: 3 minor, 3 info); layer 3 findings (4 minor, 1 info, **2 major not shown by reading**).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The Done-when "a DTD … is refused" cannot hold with the spec's own HYPOTHESIS and the real `jacoco-high/low.xml`, which carry a `DOCTYPE`; the lane's contradiction claim holds **for the Done-when clause** | checked | confirms | `jacoco-high.xml` DOCTYPE, `CoverageXmlReaderTest.toleratesADoctypeDeclaration` |
| L1-2 | The lane overreaches by calling **D2** self-contradictory: "no DTD" is a factory setting (`SUPPORT_DTD` off), and the HYPOTHESIS itself says that setting reads the real files. The verify-at-outline step the spec demanded was skipped: ADR `core-build/005` Alternative 3 says "The DTD-off variant was not measured in this plan" and asserts without evidence that DTD off would refuse every JaCoCo report | checked | refutes | `005-…adoc:63-66` |

**Governing clause:** D2 with its HYPOTHESIS; the Done-when line is the one to correct. Suggested text for the corrected clause: "An XML report is read with DTD processing off. A `DOCTYPE` declaration is tolerated, never resolved or fetched. A report that declares an entity (internal subset) or references an entity is refused, never expanded; a fixture with a `DOCTYPE` and an external entity is refused." The HYPOTHESIS stays unconfirmed until it is run with the ported fixtures.

**Layer 1 verdict:** findings — 2. No `corpus set-verdict` call: PLAN-V2-18 is shipped, and a verdict persists only on a staged spec; the correction is carried by Follow-Ups instead.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `CoverageReport.java:51-60` | `CoverageReportTest`, fixtures `deliberate-branch-*` | none | checked |
| 2 | `JacocoReportParser.java` (`ClassState.endMethod`), `CoverageXmlReader.java:188-200` | `leavesTheConstructorsOut`, `JacocoReportParserTest:157` | the lane says the reasons "name the external entity reference"; the fixtures all declare an internal subset, so the text prefilter (`CoverageXmlReader.java:114-123`) refuses them first with "internal subset" (`JacocoReportParserTest:157`, `CoberturaReportParserTest:79`); the `ENTITY_REFERENCE` backstop (`:160`) is reached only through `CoverageXmlReaderTest.isRefused` | checked |
| 3, 4, 5 | see Deliverable Fidelity | listed there | none | checked |
| Surface delta | the lane names only `CoverageRefusals`; the diff also adds `CoverageFormat`, `CoverageParseResult`, `CoveragePercentages`, `CoverageXmlReader`, `CoverageFormatTest`, `CoverageRefusalsTest`, `CoverageReportFixtures` | n/a | each is implied by D1 to D3 ("exact names at the outline") so none is unexplained; the R19 delta is understated | checked |
| Specification text | `03-parsers-and-gates.adoc:48` and `core-build.adoc:305`, ADR line 44 say a report "that declares **and uses** an external entity is refused"; the code refuses **any** internal subset, even one that declares no entity (the ADR Consequences admit it) | n/a | the specification understates the behaviour and the documents disagree | checked |
| Developer guide | `core-build.adoc:268` uses Javadoc markup (`{@link CoverageXmlReader}`, `{@code …}`) in AsciiDoc; it renders literally and R17 does not catch it | n/a | documentation defect | checked |
| Assertion message | `IstanbulSummaryParserTest.java:57` "The file is named" is 17 characters | n/a | below the rule's 20 | checked |

**Layer 2 verdict:** findings — 6 minor/info (none blocks).

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `CoverageXmlReader.java:119-120` | `<!DOCTYPE r SYSTEM "a>b" [<!ENTITY x "y">]>`: `end` is the first `>` after `<!DOCTYPE`, which lies inside the quoted literal and before the `[`, so the internal subset is not refused; the same for byte input in UTF-16 or UTF-32 without a BOM, which `decode` (`:146-153`) reads as UTF-8 so the DOCTYPE is never found | the defence falls to `IS_SUPPORTING_EXTERNAL_ENTITIES=false`, the empty-stream resolver and the `ENTITY_REFERENCE` backstop; internal entities in attribute values would still expand under the JDK's limits. A parser-enforced `SUPPORT_DTD=false` (the spec's setting) removes the whole class | **major, not shown by reading** (needs a run with a probe) |
| L3-2 | `CoverageXmlReader.java:194,197` | `<!DOCTYPE report SYSTEM "http://host/x.dtd">`, no `[` | passes the prefilter; safety rests on the factory and the resolver stub never fetching it. No fixture uses a reachable or unreachable system id (`jacoco-high.xml` uses the relative `report.dtd`) | **major, not shown by reading** (an SSRF / file-read property with no failing test) |
| L3-3 | `CoveragePercentages.java:35` | a `0/0` counter (`LF:0 LH:0`, `BRF:0 BRH:0`, `FNF:0 FNH:0`, a JaCoCo `LINE` 0/0) | reported as a measured `0.0`: a file with no lines enters the low-coverage list; an LCOV file with `BRF:0` sets the branch metric to 0.0 instead of absent (the conflation ADR Alternative 4 rejects for exactly this case); no test covers it | minor |
| L3-4 | `CoveragePercentages.java:59`, `CoverageReport.java:53-56` | a true 79.996 % | rounded half-up to 80.0 before the comparison, so it passes a threshold of 80; Python's `round` is half-even, so ties may differ from the original; below 0.005 pp | minor |
| L3-5 | `CoberturaReportParser.java:142` | `branch-rate="0"` (coverage.py writes it when branch measurement is off) | reported as a measured 0.0 branch percentage, not absent; it feeds no threshold. `Double.valueOf` also accepts `1d` and hex floats | minor |
| L3-6 | `IstanbulSummaryParser.java:166` | a string `pct` (`"Unknown"` for a zero denominator in some versions) is a refusal; duplicate keys accepted last-wins (`STRICT_DUPLICATE_DETECTION` off) | fail-closed for the string; duplicate `total` or file keys silently yield the last value or a duplicate item | minor (not shown: which Istanbul versions emit the string) |
| L3-7 | `JacocoReportParser.java:85` | a very large report or deeply nested XML | `Files.readAllBytes` / `readString` with no size cap; an `OutOfMemoryError` is not turned into a refusal; the `Refused` reason of a missing file carries the absolute path | info (the lane's separate Build-log-bounds scope is PLAN-V2-131) |

**Layer 3 not shown by reading:** L3-1 and L3-2 (the JDK StAX behaviour for an external subset and for the quoted-literal case); L3-6 (Istanbul versions); green builds and coverage.

**Layer 3 verdict:** findings — 7 (2 major not shown by reading, 4 minor, 1 info). Reported to the operator at once; none is fixed inline or staged without the operator's word (see Follow-Ups).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | records of four or more components carry `@Builder` | conforms | `CoverageMetrics` (5), `CoverageItem` (5), `CoverageVerdict` (4); `CoverageReport` (3), `Parsed`, `Refused` keep canonical constructors; component indentation at column 0 (`CoverageMetrics.java:33`) is a formatting oddity |
| CLAUDE.md Code Standards | static-only classes are `@UtilityClass` | conforms | the four parsers, `CoverageXmlReader`, `CoveragePercentages`, `CoverageRefusals` |
| CLAUDE.md Code Standards | absence as `Optional` return only; `@Nullable` elsewhere | conforms | private `Optional` returns; `.orElse(null)` into `@Nullable` components |
| CLAUDE.md Code Standards | no generic `Exception` / `RuntimeException` / `Throwable` | conforms | `XMLStreamException`, `IllegalArgumentException`, `IOException` only |
| CLAUDE.md Code Standards | `@NullMarked`, public Javadoc, no log messages | conforms | package-info edited, loggers absent |
| CLAUDE.md Dependencies and Versions | no new dependency | conforms | StAX and `jackson-core`; no POM change |
| CLAUDE.md Code Standards | assertion messages of 20 to 60 characters | deviates | `IstanbulSummaryParserTest.java:57` (17 characters) |
| CLAUDE.md Code Standards | generator data, a literal only where it is the contract and the test says so | deviates | `CoverageReportTest` (90, 40, 80, 79.99, 33.33), the Metrics / Item / Verdict tests use literal percentages without saying they are the contract; only `CoveragePercentagesTest` uses `Generators` |
| CLAUDE.md Code Standards | `@ParameterizedTest` for three or more variants | deviates | the refusal cases of `JacocoReportParserTest` and `LcovReportParserTest` are separate `@Test` methods |
| CLAUDE.md Code Standards | no process start, no Python in tests | conforms | class-path fixtures through `CoverageReportFixtures` |
| Spec (data) | D2: no DTD, no external entities | deviates | `SUPPORT_DTD` is TRUE; an internal subset is refused by a text prefilter |
| Spec (data) | Done-when: a DTD is refused; unreadable / truncated is a refusal, never 0 % | deviates | never-0 % holds for all four formats (sealed `Parsed` / `Refused`); the DTD half is unmet as written (layer 1) |

## Metrics and Anomalies

- Tokens: `unknown`.
- Duration: not supplied.
- Anomalies: the landing is `complete: false` for four keys, two of them (`unresolved_review_threads`, `review_body_findings_total`) omitted rather than `unknown`; the lane's rule check records shell file operations, a self-review sub-agent that ran `rg`, `sed`, `curl`, `jshell` and created files in a temp directory, two build-bound expiries, and two coverage runs lost to the shared `/tmp/surefire-oliver` race (the green run used an isolated `java.io.tmpdir`); `self-review` reported `budget-exhausted` with the spec wording (F1) the only open item.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `2b640f1f`, `ae11e068`, `5fa0f84c` (11 + 2, 4, 4 and 5 + 3 findings by pass; provider total 24); the current head `aead5cbb` was not reviewed (re-triggers at 16:05 and 19:24 refused with "Review rate limited"); the standing review authorization was used for the merge. Sourcery (optional) refused on its own budget. Provider re-read: 0 unresolved threads, 24 findings; handled **not re-verified**.
- CI/merge: `origin/main` moved from `46b144e7` to `363ecb48`; merged into the branch (`aead5cbb`), the whole gate re-run there; all runs at the merge head green.
- Sonar: the lane reports 31 → 10 → 2 → 0 open issues over three cycles, quality gate OK at `aead5cbb`, new-code coverage 90.8 %, duplication 1.6 %; `sonar_open_new_issues=0` taken as claimed.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-18 --status shipped`
- [x] row `pr` stamped — `#336`
- [x] row `landing` stamped — `landings/PLAN-V2-18.md`
- [x] row `plan_marshall_plan_id` stamped — `v2-18-coverage-report-parsers`
- [x] epic.md narrative reconciled against the queue rows
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- **Layer-3 L3-1 and L3-2 (the XML hardening)** are the substantive finding of this landing: reported to the operator, with the choice between a fix plan (set `SUPPORT_DTD` false, measure it with the ported fixtures, replace the text prefilter, and add a probe test for an external subset) and folding it into the existing analysis-findings fix plan PLAN-V2-114. Nothing is staged on the orchestrator's own word.
- **Specification and ADR wording** (L1-1, L1-2, the "declares **and uses**" sentence in `03-parsers-and-gates.adoc:48`, `core-build.adoc:305` and ADR line 44): the corrected clause above belongs in the same fix.
- **Developer guide markup** (`core-build.adoc:268`), the 17-character assertion message, the literal percentages in `CoverageReportTest` and the unparameterized refusal tests: test-conformance work for PLAN-V2-125 (core) if wanted.
- **PLAN-V2-127** (`npm-parser-success-and-redaction`, after 18) is now emittable.
