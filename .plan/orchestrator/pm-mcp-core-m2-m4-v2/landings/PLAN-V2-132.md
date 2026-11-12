# Landing Analysis: PLAN-V2-132 — the coverage parsers read with the DTD off, bounded, and a counter that measured nothing is absent

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
pr: #388 (merge `ce3f958eabf1ee1f7150cb75b4faf538c23c6a27`, state `merged` per the CI abstraction)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-12 from the message
> `plan-v2-132-coverage-parser-hardening-009.md` of the plan lifecycle (harness claude, model claude-sonnet-5-5/medium,
> executor 0.1.2106). The message is a lead. The three layers were read by one read-only leaf from git at `ce3f958e` (no
> build, no test run); the orchestrator re-read the points marked "re-read"; the provider figures were re-read from the
> CI abstraction (`ci pr view`) and the SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 DTD off in the parser, the text prefilter gone | shipped; one clause narrower than the spec | `CoverageXmlReader.java:171-174` switches `SUPPORT_DTD` off, external entities off, replacing off, an empty resolver; `refuseInternalSubset`, `doctypeIndex` and `decode` are gone; both routes (`String`, `InputStream`) go through `factory(resolver)` (`:96,129,170-177`). **"A report that references an entity is refused" holds for element content only**: an undeclared reference in an attribute value is dropped by the JDK reader with the DTD off (ADR `core-build/008` Decision 4, an operator-accepted Scope Deviation of 2026-11-12). |
| D2 hostile-prolog probes | partly | cases (a), (c), (d), (e), (f) and the UTF-16 form of (b) are in `CoverageXmlHardeningTest`; the UTF-32 form cannot be probed (BOM-less UTF-32 is refused as an unknown encoding, `CoverageXmlReaderTest.java:108-119`), and the test is a plain report. That the probes fail on `f97bc7f2` is stated in the Javadoc and ADR `008:44-52` only; no failing output is in the repository (not shown by reading). |
| D3 absent, not zero | shipped, with one Cobertura overreach (M1) | `CoveragePercentages.java:37` returns `Optional`; JaCoCo `:147-160`, LCOV `result()`, Istanbul `Metric.percentage` refuse an overall 0/0 (settled d1); `BigDecimal` and a 0..1 check at `CoveragePercentages.java:59-70` (settled c1). Tests are single `@Test`s, not one parameterised test per format; no fixture carries `1e-05` although the spec asked the plan to confirm that hypothesis. |
| D4 the rounding rule | partly | Javadoc on `CoverageReport`; `CoverageReportTest.java:79-91` pins 79.996 passing and 79.94 failing, where the spec's case was 79.994 reading 79.99 (79.94 fails without any rounding). |
| D4 bound and streaming | shipped; the wiring is untested (M2) | none of the four readers calls `readAllBytes` or `readString`; the counting stream and the LCOV line bound exist (`CoverageReportStream.java:116-138`, `LcovReportParser.java:138-144`); no recording source exists for the clause "at most the bound plus one buffer". |
| D4 duplicate file key | partly | Jackson strict detection (`IstanbulSummaryParser.java:58-59`); tests cover `total` only (`IstanbulSummaryParserTest.java:132-157`), and `refusesADuplicateKey` repeats the first parameterised case. |
| D5 documents and ADR | partly | ADR `008`, the status note of `005`, the specification, `core-build.adoc`, `PROVENANCE.adoc` are written; "the old wording is not found" fails: ADR `005:48-49` and `:89` still say "declares and uses an external entity" and "the internal subset is refused" (the spec itself forbade editing those bullets), and `package-info.java:48` names `core-build/005` alone. |
| Not in the declared surface | explained | `doc/Traceability.adoc` (+18/-), declared "not surface (R19)"; correct and expected. `CoverageXmlReaderTest.java` and `CoverageXmlHardeningTest.java` are edited by D4 but declared by D1/D2 (functional-review finding 567afa, the lane took it into account). |

Beyond the spec, listed in the narrative and operator-ruled 2026-11-12: repeated counters refused in JaCoCo (root and per-class) and LCOV (per record); an Istanbul metric without `total` refused (`IstanbulSummaryParser.java:213-215`); `Walk.nextLine`/`tooLong` in LCOV; the `TOTAL` constant (Sonar S1192). Not in the narrative: the Cobertura branch gating (M1).

## Three-Layer Analysis

Read at `ce3f958e` by one read-only leaf (`execution-context-level-3`); the orchestrator re-read M1 and M2 and the limit file at their `file:line`.

### Layer 1 — Specified

L1-1 (major): the spec assumed that an entity reference anywhere yields an `ENTITY_REFERENCE` event; with the DTD off the JDK reader drops an undeclared reference in an attribute value silently (ADR 008:64-73), so the clause holds for element content only (operator-accepted). L1-2 (minor): the hypothesis that BOM-less UTF-32 is detected is refuted; the outcome is fail-closed. L1-3 (minor): the D5 Done-when ("the old wording returns none") contradicts the plan's own constraint not to edit the ADR `005` bullets. L1-4 (minor): the spec lists `doc/Traceability.adoc` as not surface, while the repository records implementation there, so it had to change. L1-5 (minor): the invariant "no parser result changes below the cap for a report that carries a measured counter" (Done-when and ADR 008 Consequences) conflicts with the settled decisions c1 and d1 and the later rulings: an Istanbul `pct`-only metric was accepted before and is refused now.

### Layer 2 — Implemented

Conformance to the standing rules and `CLAUDE.md`: no generic catch (`XMLStreamException | IllegalArgumentException`, `IOException`, `NumberFormatException`), no `Optional` parameter, no `@Nullable` return, JUnit 5 only, no AAA comments, no `Random`/`UUID`, EasyMock nice mocks for the resolver double (`CoverageXmlHardeningTest.java:195-200`), no hand-written fake, literals labelled; a regex sample of the added assertion messages found none outside 20 to 60 characters (not exhaustive). Deviations: `@ParameterizedTest` for 3+ variants (`JacocoReportParserTest.java:259-278`, none in `LcovReportParserTest`); two library strings pinned (`CoverageXmlReaderTest.java:~119` the JDK `ParseError at [row,col]:[1,40]`, `IstanbulSummaryParserTest.java:137,157` the Jackson `Duplicate field 'total'`); the LCOV repeat check is tested for `LH` and `LF` only (`LcovReportParserTest.java:~340-375`), so removing it on `BRF`, `BRH`, `FNF`, `FNH` leaves every test green; `CoverageXmlReader.open(String)` (`:165`) is used by tests only.

Can the tests fail: (a)(b)(c)(e) differ from the old code through `resolverCalls == 0` (the refusal reason is the same on the old code, ADR 008:44-46; not shown by reading that the old code called the resolver); (d) differs (old refused, new parses); (f) is a real negative control (`DOCTYPE` in a comment and in CDATA). A hostile token (`&x;` in element content) is present in (a) and (b).

### Layer 3 — Correct

**M1 (major, re-read). Cobertura drops a measured branch rate.** `CoberturaReportParser.java:166` sets `branchesMeasured = count(reader, BRANCHES_VALID).orElse(0L) > 0` and `:171-174` `branchPercentage` returns `null` whenever it is false, for any `branch-rate`. Input: `<coverage line-rate="0.9" branch-rate="0.5">` with no `branches-valid` (or `branches-valid="0"`); effect: the old result was branch 50.0, the new one is absent, silently. The spec (L3-5) limited the rule to `branch-rate="0"`; the Javadoc (`:36-38`) and ADR 008 state the wider rule, and the tests cover rate 0 only (`CoberturaReportParserTest.java:158-190`). Not shown by reading: whether real Cobertura producers write `branches-valid` beside a nonzero `branch-rate` (coverage.py and Cobertura Java both write it, which is why this is a regression risk and not a known break).

**M2 (major, re-read). The stream wiring is not tested at the parsers.** The cap is proved in `CoverageReportStreamTest` and `CoverageXmlReaderTest.java:~135-155` only. Removing `new CoverageReportStream(...)` at `JacocoReportParser.java:97`, `CoberturaReportParser.java:91`, `IstanbulSummaryParser.java:85` or `LcovReportParser.java:83` (all four re-read) leaves every test green (no parser test names the stream, a bound or the limit; counted 0 in all four), and the 256 MiB constant (`coverage-limits.properties`, re-read) cannot be lowered for such a test: no seam exists.

Minor: m1 the attribute entity dropped silently (`covered="1&x;0"` delivers `10` and is accepted; accepted by ruling, only `name=""` is pinned, `CoverageXmlHardeningTest.java:121-135`); m2 the bound edges: `CoverageReportStream.room()` is `limit - count + 1` (`:136-138`, re-read), so `coverage-max-bytes=9223372036854775807` overflows it to a negative read length, `MAX_LINE_LENGTH + 1` (`LcovReportParser.java:138-144`) overflows for `2147483647` and refuses every line, and a value over `Integer.MAX_VALUE` raises `ExceptionInInitializerError` from `Math.toIntExact`, which escapes every parser including the `String` overload of LCOV (the shared limit reader has the same open edge, see PLAN-V2-131 m2); m3 a refusal echoes report content without a length bound (`JacocoReportParser.java:119,168`, the `NumberFormatException` text at `:192`, `CoberturaReportParser.java:115`, the LCOV `SF:` name), up to the byte cap; m4 `XMLConstants.ACCESS_EXTERNAL_DTD` is not set as defence in depth and the factory is service-loader resolved (`CoverageXmlReader.java:170-177`); m5 the cap is on bytes, not heap (a 200 MiB text node or attribute is held by StAX, not shown), and a `BigDecimal` parse of a very long digit string is bounded only by the cap; m6 `round2` (`Math.round(v*100)/100.0`) is pre-existing and not new.

Checked and found fine: no bypass of the count by `read()`, `read(buf)`, `readAllBytes`, `skip`, `mark` or `reset` (`markSupported` false, `reset` throws, `CoverageReportStream.java:116-128`; a single read takes at most `limit - count + 1` bytes, so the stream can reach the limit plus one); a single huge LCOV line is cut at `MAX_LINE_LENGTH + 1` and a CRLF line at exactly 8192 is read; no hardened-path bypass (StAX has no XInclude or schema loading; both routes use `factory(resolver)`); no path yields 0.0 from 0/0 (a Cobertura class `line-rate="0"` is a measured 0.0, unavoidable: a class has no denominator); the JaCoCo repeat check covers report-root and class-direct counters only, so reports with several packages or source files are not refused wrongly; no absolute path in a refusal (`CoverageRefusals.java:58-71` names the file name only); streams and readers are closed by try-with-resources; a `NumberFormatException` is an `IllegalArgumentException` and is caught.

## Conformance to the Project's Own Rules

See Layer 2. The landing message carries no review-gate counts (`unresolved_review_threads`, `review_body_findings_*` are `unknown`; the lifecycle producer records none). The lane's own report: all bot comments answered (CodeRabbit `3e7d73` fixed, `2c25d7` declined under the operator ruling, Sourcery refused on quota, optional); 14 code-review and 5 functional-review advisories left unfixed (the lane's decision log; not checkable by reading).

## Unverified leads

- Whether the probes (a)(b)(c)(e) fail on `f97bc7f2` and whether the resolver is called once there: stated in the ADR only.
- Behaviour of a non-JDK StAX implementation; the JDK's handling of `&amp;` in content with replacing off; the exact JDK and Jackson message strings the tests pin.
- Heap use for a hostile 200 MiB text node or attribute; the `BigDecimal` parse of a 100 MB digit string.
- Whether real Cobertura producers write `branches-valid` beside a nonzero `branch-rate`, and whether real LCOV producers always write `LF`/`LH` (the unchanged rule refuses a record with `DA` lines and neither).

## Metrics and Anomalies

`total_tokens` 26 603 095, `total_wall_seconds` 36 970 (about 10.3 h), harness claude, claude-sonnet-5-5/medium, plugin 0.1.2106, executor 0.1.2106; the retrospective's budget error "[BUDGET] 2.0M (7.5M observed)" for the finalize share (60 %). Cause named by the retrospective lessons: four HEAD advances re-fired the functional and code review as full rounds (six and four rounds) because `verdict_inputs` were undeclared, and a no-conflict overlap with #391 forced a rebase instead of the queue route. Sonar over plain HTTP: `pullRequest=388` 0 open issues and 0 hotspots to review, gate `OK`; `main` re-analysed at `ce3f958e`, 84 open (85 before). `surface_delta` unmeasured. Operator rulings 2026-11-12 recorded in the plan (D2 case (e) option B as a Scope Deviation Accepted; three accepted test rewrites; repeated counters refused; Istanbul metric without `total` refused) match the diff.

## Routing and Merge Behavior

One PR (#388), squash through the merge queue (rebased once at the branch cleanup because #391 touched the same spec file). Eight `candidate-lesson` messages (`plan-v2-132-coverage-parser-hardening-001…008`), all about `plan-marshall:*` components (hand-filed sonar `raw_input` schema, no unauthenticated Sonar read path, `prompt_not_found` in the subagent dispatch on consumer repos, review re-fires not scoped to the delta, no queue route for a no-conflict overlap, spec-mandated test rewrites not declared as `Tests to remove`, no ledger row for gate-rewrite commits at the hand-back, no detached probe tree in `worktree-create`) were forwarded to plan-marshall's `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-015` to `-022` and archived here, by the standing routing rule.

## Reconciliation Actions

Row PLAN-V2-132 `running` -> `shipped`, `pr` `#388`, `landing` `landings/PLAN-V2-132.md`, `plan_marshall_plan_id` `plan-v2-132-coverage-parser-hardening`; message `plan-v2-132-coverage-parser-hardening-009` archived; Watch entry in `epic.md`; decision log.

## Follow-Ups

Placed 2026-11-12 under the operator's rule "every leftover goes into a staged plan":

1. M1 (the Cobertura rule wider than the spec; ask-at-refine, recommendation fix to the spec ~75%), M2 (the stream wiring test and its seam; ask-at-refine, recommendation the seam ~60%), the statements (ADR `005:48-49`/`:89`, `package-info.java:48`) and the two not-shown questions -> PLAN-V2-114 item 15.
2. m2 (bound overflow edges), m3 (the echoed report content), m4 and m5 (defence in depth, heap) -> PLAN-V2-138 item 6.
3. The Layer-2 deviations and the missing cases (parameterised refusals, pinned library strings, `BRF`/`BRH`/`FNF`/`FNH`, duplicate keys other than `total`, `1e-05`, 79.994, the recording source, the UTF-32 display name) -> PLAN-V2-125.
4. m1 (the attribute entity dropped silently), L1-1 to L1-5 and the D2 old-code claim: statements of the record; accepted by ruling or settled by the ADR; no plan carries them.
