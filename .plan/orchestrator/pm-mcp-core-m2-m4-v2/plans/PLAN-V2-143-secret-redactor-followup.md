# PLAN-V2-143: Close the gaps the landing analysis of PLAN-V2-138 found in the secret redactor: the quadratic PEM pattern, the empty-user URL, the diagnostics stored unredacted

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: the three-layer analysis of PLAN-V2-138 (`landings/PLAN-V2-138.md`: layer 3 major "the PEM pattern is quadratic across lines" and the minors), the merged pull request #407 (`64cec092`), ADR `pm-core/doc/adr/core-build/009-the-redactor-shapes-the-diagnostic-redaction-and-the-input-bounds.adoc`, and the plan's own leftovers (its landing message `inbox/archive/plan-v2-138-secret-redactor-hardening/plan-v2-138-secret-redactor-hardening-006.md`)
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-143-secret-redactor-followup.md` and is queued as one row file, `queue/PLAN-V2-143.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R09, R11, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09 because the redactor sits on the trust boundary between build output and the model; R10: no specification part is edited unless the outline finds a statement the code makes false (`doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` line 114 requires the job log read to be redacted); R12: no message is added; R13: no stored format changes; R14, R15, R16: no protocol, DSL or runtime behaviour beyond the redaction.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-build.adoc` and `pm-core/doc/developer/core-queue.adoc` (the lines that name the redactor's bounds); ADR: amend `pm-core/doc/adr/core-build/009-...adoc` in the lines this plan makes false (109, 148-150, 219-220, the "What stays readable" list), and write a new ADR with the next free number in `pm-core/doc/adr/core-build/` only for a design decision the outline takes (the PEM construction, a stored-message redaction seam); layout: workstream WS-07.

**Read the skills first (R20).** `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`, and `plan-marshall:persona-security-expert`; say in the first progress note which you read.

## Objective

PLAN-V2-138 hardened the redactor, and its analysis found what it left: one pattern whose cost the "per-line cut covers every pattern" argument does not bound, one credential shape that is not matched, a seam through which most diagnostics are stored unredacted, and tests that cannot fail. This plan closes them.

## Deliverables

Four deliverables, one commit each, in this order.

1. **The PEM pattern is linear (the major finding).** `SecretRedactor.PEM_PRIVATE_KEY` (`pm-core/src/main/java/de/planmarshall/core/queue/SecretRedactor.java:203-204`) is `(?s)-----BEGIN [^-]*PRIVATE KEY(?: BLOCK)?-----.*?-----END [^-]*PRIVATE KEY(?: BLOCK)?-----`: each `BEGIN` without a later `END` scans lazily to the end of the text, and it runs before the UNCLOSED pass, so a text of many `BEGIN` markers costs markers times text over two (about 3e9 steps at the excerpt maximum of 50 lines of 8192 characters). Replace it with a single left-to-right scan (find `BEGIN`, find the next `END` from that position once, mask the span; a `BEGIN` with no `END` takes the UNCLOSED rule from the one remaining position and the scan does not return to a marker already passed), or bound the search window; the output for every input the existing tests pin stays byte-identical. **Test order: the cost test first** — a text of 400 000 characters made of `-----BEGIN PRIVATE KEY-----` markers with no `END` must redact within a bound the test states (a generous wall-clock limit with `assertTimeoutPreemptively` that the old pattern exceeds, and a second test with 3 700 markers), and a 1 000 000-character quoted flag value (the possessive loop `(?:(?!\3)[^\\\r\n])*+`) must not raise `StackOverflowError`; then the fix. Correct ADR 009 lines 219-220 (the per-path bound does not cover a pattern that crosses lines) and line 109 ("measures the cost" becomes what the test bounds).
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer (the lens: security-expert)
2. **The credential shapes that slip through.** (a) The empty-user URL `https://:token@host` (Azure DevOps, git credential helpers): the user class `[^\s/?#:]++` of `urlUserInformation` (`SecretRedactor.java:222`) needs one character, so the secret stays readable; make the user optional and add the tests (the single-user form without a colon stays readable, as ADR 009 decided). (b) **Ask-at-refine:** the redactor is ASCII-only (`\s`, `\w` and `(?i)` are ASCII): `password =x` with a non-ASCII space and zero-width characters inside a label are not matched; recommendation: do not widen (the redactor guards accidents, not evasion) and record it in the ADR "What stays readable", about 70%.
   (c) **Ask-at-refine (carried from the landings of PLAN-V2-81 and 138, unowned until now):** a quoted assignment whose first word is under four characters stays readable: `token="ab cd ef"`, `password="my pass phrase"` (the value class `[^\s"',;]{4,}` stops at the space); a bearer value under 8 characters and a secret without a label stay readable by design (the minimum is deliberate: drop as an accepted risk and say so in ADR 009 "What stays readable"). Options for the quoted form: (1) a quoted value runs to its closing quote, as the flag forms do, (2) keep the four-character minimum and document it, (3) widen only for the labels `password`, `passphrase` and `secret`; recommendation (1), about 70%.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer (the lens: security-expert)
3. **The diagnostics stored unredacted.** `DiagnosticCollector.stored` (`pm-core/src/main/java/de/planmarshall/core/build/DiagnosticCollector.java:72`) only truncates to `MAX_MESSAGE_LENGTH` 500; only the npm parser redacts before it (`NpmErrorParser.java:181`). The TAP `error:` and `stack`, Jest, ESLint, tsc, Maven and Gradle messages reach the model unredacted. Redact at the seam (`stored`: redact, then cut) so every parser is covered, and bound the unbounded input of `ProviderVerification` (`pm-runtime/src/main/java/de/planmarshall/runtime/credentials/ProviderVerification.java:307`, which ADR 009 acknowledges). **Ask-at-refine:** the package cycle `core.queue` to `core.build` (`SecretRedactor` imports `BuildLogText`; ADR 009 admits no guard forbids it): move the redactor below both packages now, or record it; recommendation record it, about 60%, because the move renames a class every caller imports. Verify-first: whether the job-log resource read in `plan-marshall-mcp` calls `SecretRedactor` (a read of the sibling checkout; the finding is reported to the operator if it does not, not fixed here).
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer (the lens: security-expert)
4. **The tests that cannot fail, and the statements that are wrong (R05, R06).** (a) `JestOutputParserTest.java:165-175` and `TapOutputParserTest.java:330-343` ("parts wrap to the total"): a wrapped sum is unreachable at that call (`TestCounts.from` returns unmeasured for a part above `Integer.MAX_VALUE`, `TestCounts.java:~143-147`; for TAP `# fail > 0` already forces red): delete them or reword them to a case that can fail; keep the sibling tests at `:300-328`. (b) ADR 009: add the head-residue bullet to "What stays readable" with its justification (a credentialed URL whose closing `@` lies beyond the 8192 cut leaves `scheme://user:<partial password>` readable; inert because every stored message is cut to 500 after redaction, `DiagnosticCollector.java:34`, so it breaks if that cut is raised) and the matching clause of the spec's line-bound paragraph; lines 148-150 (the Maven and LCOV bounds are each 2^58, not one merged 2^59); the Javadocs of the LCOV `Walk` ("within the `int` bound of CoverageReportStream": the cap is a `long` limited by configuration) and of the Maven `CountScan` ("the sums cannot wrap": they cannot because an unrepresentable value is not summed). (c) Small: `CoverageRefusals.cut` cuts the composed `name + ": " + kind`, so a name near 255 characters loses its failure kind (cut the name, keep the kind); `BuildLogText.boundLines` can split a surrogate pair at the cut where `UnparsedEvidence.boundLineTails` guards it (guard it); the `TapOutputParser` point counter `^(?:not )?ok\b` (`:105`) counts prose lines starting with "ok" (anchor it to the TAP point shape `ok <number>`).
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:javadoc, pm-documents:ref-asciidoc
   - Persona: implementer (the documentation: documenter)

**Done when:** a text of 400 000 characters made of `BEGIN` markers without `END` redacts within the stated bound and the test fails on the old pattern; the 1 000 000-character quoted value raises no `StackOverflowError`; `https://:token@host` is redacted and a single-user URL without a colon is not; every parser's stored message passes through the redactor (a test per parser family through `DiagnosticCollector`); the two parts-wrap tests are gone or can fail; ADR 009 is true at the lines named; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

## Standing Constraints

- **Read the ported real-tool fixtures before settling a marker or count rule of a `core.build` parser** (PLAN-V2-138's lesson 001): `pm-core/src/test/resources/ported/build/{tool}/`; a rule that contradicts a real-tool fixture is a decision for the refine, not a discovery in the execute.
- **The output of every input an existing test pins stays byte-identical** (the redactor's tests are the guard; no existing case of `SecretRedactorTest` or `ActivityJournalMaskingTest` is changed, only added).
- **No dependency or plugin is added**; no new hand-written double.
- **Where the project analyses its pull requests with Sonar** (read it over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open issue is fixed in a pushed commit, or dismissed with a stated reason `{issue-key}: {reason}` through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`; a dismissal the run cannot transmit is a blocker.

## Claim Labels

All lines read at `32a711f9` through the landing analysis of PLAN-V2-138; the outline re-reads each before it acts. The step counts are an untimed estimate of the leaf; the cost test is the measurement.

- OBSERVED: `SecretRedactor.java:203-204` (the PEM pattern, lazy `.*?` under `(?s)`), `:222` (the user class `[^\s/?#:]++`), `DiagnosticCollector.java:34` (`MAX_MESSAGE_LENGTH` 500) and `:72` (`stored` truncates only), `NpmErrorParser.java:181` (the only parser that redacts before `stored`).
- OBSERVED: ADR 009 lines 109, 148-150 and 219-220 make the claims named above.
- OBSERVED: the redaction call sites are `UnparsedEvidence:100`, `NpmErrorParser:181`, `ActivityJournal:208`, `ActivityRecorder:98`, `ProviderVerification:307`.
- HYPOTHESIS: a lazy quantifier under `(?s)` with a literal prefix is quadratic in the number of unmatched prefixes; verify-first with the cost test before the fix.
- HYPOTHESIS: the JDK keeps `(?:(?!\3)[^\\\r\n])*+` iterative; verify-first with the 1 000 000-character test.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/SecretRedactor.java` — D1, D2
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/queue/SecretRedactorTest.java` — the cost, stack and URL tests (D1, D2)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/DiagnosticCollector.java`, `pm-core/src/main/java/de/planmarshall/core/build/NpmErrorParser.java` — D3
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/credentials/ProviderVerification.java` — the bound (D3)
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/build/JestOutputParserTest.java`, `pm-core/src/test/java/de/planmarshall/core/build/TapOutputParserTest.java` — D4(a)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/TapOutputParser.java`, `pm-core/src/main/java/de/planmarshall/core/build/BuildLogText.java`, `pm-core/src/main/java/de/planmarshall/core/build/CoverageRefusals.java` — D4(c)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/LcovReportParser.java`, `pm-core/src/main/java/de/planmarshall/core/build/MavenOutputParser.java` — the Javadocs (D4(b))
- OBSERVED: `pm-core/doc/adr/core-build/009-the-redactor-shapes-the-diagnostic-redaction-and-the-input-bounds.adoc`, `pm-core/doc/developer/core-build.adoc`, `pm-core/doc/developer/core-queue.adoc` — the lines named
- OBSERVED, read not edited: `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` (line 114), `pm-core/src/test/resources/ported/build/` (the fixtures)

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`. No `pom.xml`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-138 (shipped, #407), PLAN-V2-131 and 132 (shipped).
- Overlaps at class level, decided by the gate at emit: PLAN-V2-125 and 126 (the pre-existing `SecretRedactorTest` assertions without a message and the 67-character message in `BuildLogBoundsTest` are theirs; they touch the same test trees), PLAN-V2-114 (items on `core.build`), PLAN-V2-141 (test-design rework of the same trees).
- Not here: the pre-existing message-less assertions (125, 126), the redaction of the job-log resource read in `plan-marshall-mcp` (a report to the operator if it is missing), Lombok `@UtilityClass` for `SecretRedactor` (pre-existing: `final` class, private constructor, static methods).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-143-secret-redactor-followup.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
