# Landing Analysis: PLAN-V2-138 — the secret redactor hardened, the npm install marker, the parser bounds

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
pr: #407 (merge `64cec092`), `merged` through the merge queue, no label

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-14 from the messages
> `plan-v2-138-secret-redactor-hardening-001.md`, `-002.md` and `-006.md` of the plan lifecycle (harness claude, model
> claude-sonnet-5-5, executor 0.1.2114). The landing message is a lead. The three layers were read by one read-only leaf
> from git at `origin/main` `32a711f9` (64cec092 is an ancestor; no build, no test run); the orchestrator re-read the
> points marked "re-read"; the provider figures were re-read from the SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 URL user information | met | two patterns (`://` and `%3A%2F%2F`), possessive scheme and user, the password `\S+@` greedy to the last `@` (`SecretRedactor.java:135,138,221-223`); the example URLs are tested (`SecretRedactorTest:129-183`). |
| D2 flag names | met | `CREDENTIAL_FLAG_NAMES` (`:154`) holds the five old names and the six added; case-insensitive; `;`, quotes and two-line quoted values covered (`SecretRedactorTest:207-355`). |
| D3 token length and PEM | met | the npm token stays `{36}` (`:96`); the PGP block via `(?: BLOCK)?`; the head residue is documented. |
| D4 journal and npm | met | `stripAndRedact` (`:295`), `storedRedacted` around E404 and ERESOLVE (`NpmErrorParser:162-164`). |
| D5 install marker | met | `BuildOutputParser:118,167` gate the marker on `install` and `clean-install` through `verb(budgetKey)`; ADR `runtime-job/018`, `016` marked superseded in part. |
| D6 the pins | met | (a) (b) (c) present; the TAP `1..N` rule follows the top-level points. |
| Item 5 line bounds | met, one unreported deviation | six parsers and the dispatcher cut lines; `^[ \t]*` replaces `^\s*`; spec 5(b) said to cut the kept tail with `BuildLogText.boundLines` (head-keeping) and the code adds `UnparsedEvidence.boundLineTails` (`UnparsedEvidence.java:114`, keeps the line's END): ADR 009 5(b) documents and justifies it; the landing message does not list it. |
| Item 6 coverage | met | `readInt`, saturating `room()`, `CoverageRefusals.cut` at 256, `ACCESS_EXTERNAL_*`. |
| Documentation, ADR | partly | ADR `core-build/009` is the ADR the spec named; the residues below are missing or wrong. |

Plan-level Done-when: no `pom.xml` changed, no `@Test` removed, no existing `SecretRedactorTest` or `ActivityJournalMaskingTest` case changed; one existing `BuildOutputParserTest` case was rewritten, forced by D5. Spec items 5 and 6 (the residues of PLAN-V2-131 and 132) are closed by this plan.

## Three-Layer Analysis

Read at `32a711f9` by one read-only leaf (`execution-context-level-3`, 41 tool uses); the orchestrator re-read the PEM pattern, the URL pattern, the ADR lines, the TAP test, `DiagnosticCollector` and the redaction call sites.

### Layer 1 — Specified

- Ask-at-refine items 1 to 4 are coherent and each carries a recommendation; ADR 009 records the answers as the recommendations (a single-user URL stays readable, six flag names and no `--key`, the npm token stays `{36}`, the marker by verb). The hypothesis "is the verb available at the seam" is confirmed (`job.budgetKey()` at `BuildOutputParser.java:118`). Items 5 and 6 roughly double the scope: ADR 009 says nine deliverables against the soft cap of six and no cut was made.
- The spec did not foresee the overflow family: it came from code the plan wrote (the TAP plan-line sum) and was widened by review to Jest, ESLint and the summary counts; those changes lie beyond the Expected Surface, which named those parsers for the line bound only.
- **One spec statement was wrong:** the D6(c) hypothesis that TAP `1..5` against `# tests 3` is a defect (compare the plan with the counts). The ported real fixture `npm-tap/npm-tap-test-real.log` prints `1..4` beside `# tests 5` (lines 79-80) with four top-level `ok` points; comparing the plan with the points is right and ADR 009 Decision 3 records it. The deviation is justified.
- The 8192 bound was specified (the existing `pm.core.build.max-line-length` of 131).

### Layer 2 — Implemented

Redaction call sites (grep of `src/main`): `UnparsedEvidence:100`, `NpmErrorParser:181`, `ActivityJournal:208`, `ActivityRecorder:98`, `ProviderVerification:307`; the order is right on each (the excerpt cuts the line, then redacts, then caps the bytes; npm redacts, then cuts to 500; the journal relies on the entry's bounds, which hold before redaction). Paths the plan missed or excluded:

- **Non-npm diagnostics are stored unredacted at `DiagnosticCollector.stored`** (`DiagnosticCollector.java:72`, re-read: it only truncates to `MAX_MESSAGE_LENGTH` 500): TAP `error:` and `stack`, Jest, ESLint, tsc, Maven and Gradle messages. The spec lists this as "Not here"; it is a real path to the model.
- Whether the job-log resource read is redacted lives in `plan-marshall-mcp` (job-runtime/01 line 114 requires it): unverified. `ProviderVerification` takes an unbounded input (ADR 009 admits it).

### Layer 3 — Correct

**Major — the PEM pattern is quadratic across lines, and ADR 009's claim that the line cut covers "every pattern" is false for it (re-read).** `SecretRedactor.java:203-204`: `(?s)-----BEGIN [^-]*PRIVATE KEY(?: BLOCK)?-----.*?-----END [^-]*PRIVATE KEY(?: BLOCK)?-----`. Each `BEGIN` without a later `END` scans lazily to the end of the text, and the pattern runs before the UNCLOSED pass, so the cost is always paid: about markers times text over two. The excerpt maximum is 50 lines of 8192 characters (409 600 characters, about 15 000 markers of 27 characters), about 3e9 steps (an untimed estimate: tens of seconds); about 100 KB (3 700 markers) is about 2e8 steps, a second or a few. The journal (2 000 characters) and the npm message (about 8 KB) are harmless. The trigger is hostile build output that reaches `failed_unparsed`; it blocks the settling thread. The per-line cut does not bound a pattern that crosses lines; only the 50-line window does. ADR 009 lines 219-220 ("the per-path bound covers every pattern of the redactor at once", re-read) is wrong for this pattern; the lane's leftover (3) is confirmed and its severity is understated if read as "unnamed".

Minor:

- The cost tests are smoke tests, not bounds: `SecretRedactorTest:185-205` redact an 8192-character run and assert equality, which a quadratic pattern passes (about 1e7 steps); ADR 009 line 109 says the test "measures the cost". There is no cost or stack-depth test for the possessive quoted-flag loop `(?:(?!\3)[^\\\r\n])*+` or for PEM; whether the JDK keeps a lookahead inside a possessive group iterative (a 1 000 000-character quoted value, `StackOverflowError`) is unverified.
- **The head residue (the lane's leftover 1) is a documentation gap and inert today:** the npm path cuts the head at 8192, and a credentialed URL whose closing `@` lies beyond the cut leaves `scheme://user:<partial password>` readable; every npm message is then cut to 500 after redaction (`DiagnosticCollector.java:34`), the offset in the message is at least the offset in the line, so the fragment never reaches a stored diagnostic; the excerpt's tail-keeping cut is inert because 8192 exceeds the 4096-byte cap. The note should say so, because it breaks if the 500 is raised.
- **The two "parts wrap to the total" tests cannot fail** (`JestOutputParserTest:165-175`: a wrapped sum still goes through `TestCounts.from`, which returns unmeasured for any part above `Integer.MAX_VALUE`, `TestCounts.java:~143-147`; `TapOutputParserTest:330-343` (re-read): additionally `# fail > 0` already forces red); the wrap is only reachable through a count above `int`, so it is structurally untestable at that call. The sibling tests at `:300-328` work. Delete or reword the two.
- **An empty-user URL leaks:** `https://:token@host` (Azure DevOps, git credential helpers) does not match, because the user class `[^\s/?#:]++` needs one character (`SecretRedactor.java:222`, re-read); no test. By reading only.
- Redaction is ASCII-only (`\s`, `\w`, `(?i)`): `password =x`, `--password x` with a non-ASCII space and zero-width characters inside a label are not matched; low risk (the redactor guards accidents, not evasion).
- The TAP top-level point counter `^(?:not )?ok\b` (`TapOutputParser:105`) counts prose lines starting with "ok", which could make an unfinished plan equal the counted points and give a false marker; low risk, a mid-run `TAP version` line resets conservatively.
- Residue text: ADR 009 lines 148-150 merge the Maven and LCOV bounds (each is 2^58 separately; the merged sentence gives 2^59); the LCOV `Walk` Javadoc says the file is read "within the `int` bound of CoverageReportStream", the cap is a `long` limited by configuration; the `CountScan` Javadoc says the sums cannot wrap, which holds only because unrepresentable values are not summed.
- Nits: `CoverageRefusals.cut` cuts the composed `name + ": " + kind`, so a name near 255 characters loses its failure kind; `boundLines` can split a surrogate pair at the cut (`boundLineTails` guards it; inert behind the 500 cut); a new package cycle `core.queue` to `core.build` (`SecretRedactor` imports `BuildLogText`; ADR 009 admits no guard forbids it): the redactor could sit below both packages.

## Conformance to the Project's Own Rules

Added assertion messages hand-counted over `SecretRedactorTest`, `TapOutputParserTest`, `NpmErrorParserTest`, `BuildOutputParserTest`, `BuildLogBoundsTest`: all 20 to 60 characters ("URL user information" is exactly 20, "a Maven line of 262144 characters is parsed inside the bound" exactly 60). One pre-existing message of 67 characters (`BuildLogBoundsTest`, from PLAN-131) and about 25 pre-existing `SecretRedactorTest` assertions without a message (lines 42-112, 458-464, 509-511) are not this plan's (PLAN-V2-125 and 126). Added lines: no AAA comments, `Random`, UUID, Mockito, generic catch, `System.out` or `@Nullable` return. `SecretRedactor` is a `final` class with a private constructor and static methods, not Lombok `@UtilityClass` (pre-existing). Overlap with the later merges: `core-queue.adoc` (#410) and `doc/Traceability.adoc` (#412), docs only. Sonar `pullRequest=407`: 0 open, gate `OK` (re-read); CodeRabbit threads all answered and resolved across three firings; one CI `integration-tests` failure at the last head was infrastructure (Maven Central could not resolve `surefire-junit-platform:3.6.0`) and passed on re-run.

## Unverified leads

The PEM timing (an estimate: it needs a counted-steps harness or a preemptive timeout on a 400 KB input of `BEGIN` markers); the JDK's iterative handling of the possessive quoted-flag loop; whether the job-log resource read in `plan-marshall-mcp` calls `SecretRedactor`; that the failing run before each fix and the one mutation check the spec asked for were shown (the progress notes were not read).

## Metrics and Anomalies

`total_tokens` 43 955 207, `total_wall_seconds` 73 662 (about 20.5 h), harness claude, model claude-sonnet-5-5, effort unknown, plugin and executor 0.1.2114; four finalize loop-back rounds, each finding one sibling of the same overflow family (the TASK-22 to TASK-24 sweep closed it in the TAP, Jest and ESLint parsers and exempted Maven, LCOV, `TestCounts`, `RewriteLogResult` with a stated bound); the review counts are `unknown` in the facts (the manifest has no `review-gate` step); `sonar_open_new_issues=0` is the plan's own plain-HTTP read.

## Routing and Merge Behavior

One pull request through the merge queue under the `CLAUDE.md` "Who merges" rule (the lifecycle's `final_merge_without_asking=false` would have prompted; the consent was recorded from `CLAUDE.md` plus the operator's "continue unattended"). Five candidate lessons: 003 to 005 concern `plan-marshall:*` components and were forwarded by the plan to plan-marshall's `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-031` to `-033` (uncommitted in that ledger worktree). Lesson 001 (outline decisions on a `core.build` parser marker rule must read the ported real-tool fixtures first; route `pm-mcp-core`) is recorded here and in the follow-ups; lesson 002 (the `final_merge_without_asking` knob of `marshal.json` disagrees with the `CLAUDE.md` rule) is an **operator decision**: set `branch-cleanup.final_merge_without_asking: true` to match `CLAUDE.md`, or change `CLAUDE.md`; told to the operator.

## Reconciliation Actions

Row PLAN-V2-138 `running` -> `shipped`, `pr` `#407`, `landing` `landings/PLAN-V2-138.md`, `plan_marshall_plan_id` `plan-v2-138-secret-redactor-hardening`; the three messages archived; Watch entry in `epic.md`; decision log; resume anchor.

## Follow-Ups

Placed 2026-11-14 under the rule "every leftover goes into a staged plan, or a regular cleanup plan when none fits". The redactor has no other staged plan, so **new plan PLAN-V2-143 `secret-redactor-followup`** (WS-07, runbook lane) takes: the PEM single pass (the one code fix, with a timed cost test), the empty-user URL, redaction at `DiagnosticCollector.stored` for the non-npm diagnostics, the bounded `ProviderVerification` input, the cost and stack tests, the two parts-wrap tests, the ADR 009 corrections (lines 109, 148-150, 219-220, the head-residue bullet and the spec clause), the Javadocs of the LCOV `Walk` and the Maven `CountScan`, and the ask-at-refine items (Unicode separators, the package cycle). Other leftovers: the 25 message-less pre-existing `SecretRedactorTest` assertions and the 67-character message belong to PLAN-V2-125 and 126; lesson 001 is a rule for outlines of `core.build` parser changes and is added to the spec of PLAN-V2-143 as a standing constraint (read the ported fixtures under `pm-core/src/test/resources/ported/build/{tool}/` before settling a marker rule).
