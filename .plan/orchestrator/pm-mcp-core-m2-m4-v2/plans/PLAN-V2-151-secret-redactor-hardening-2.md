# PLAN-V2-151: Close what the landing analysis of PLAN-V2-143 left in the secret redactor: the key body behind a labelled assignment, the quadratic URL pattern on the stored seam, the diagnostics still stored unredacted, the escaped-JSON secret and the job-log read-out

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: the three-layer analysis of PLAN-V2-143 (`landings/PLAN-V2-143.md`, layer 3 findings 1, 2, 3, 4, 7, 8, placed here 2026-11-15), the merged pull request #434 (`9c961d58`) and ADR `pm-core/doc/adr/core-build/009-the-redactor-shapes-the-diagnostic-redaction-and-the-input-bounds.adoc`
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-151-secret-redactor-hardening-2.md` and is queued as one row file, `queue/PLAN-V2-151.json`,
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

PLAN-V2-143 (#434) made the redactor's PEM pattern linear, masked the empty-user URL, widened the matcher to Unicode and put every classified diagnostic through the redactor. Its landing analysis (`landings/PLAN-V2-143.md`, read it first) found what that left: a private key given as the value of a labelled assignment still leaks its body; the one remaining backtracking pattern, the URL user-information, now runs on every stored diagnostic line and on every `stackTrace` line; three stored texts still bypass the redactor; the escaped-JSON secret matches no pattern; and the job-log read-out that `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` line 114 requires to be redacted has no redactor call. This plan closes those, each with a failing case first, and keeps every output an existing test pins byte-identical.

## Deliverables

1. **A private key behind a label is masked whole (failing case first).** `SecretRedactor.redactText` (`SecretRedactor.java:344-368`) runs the URL, quoted and unclosed-quoted passes before the PEM scan, so `PRIVATE_KEY="-----BEGIN RSA PRIVATE KEY-----\n<body>\n-----END RSA PRIVATE KEY-----"` and `secret: -----BEGIN …` lose the `BEGIN` marker to the label pass and the body and the `END` line stay readable. Mask the block first (run `redactPrivateKeyBlocks` before the label passes) or have the label pass leave a `BEGIN` marker; keep `redactPrivateKeyBlocks` linear (the cost tests of PLAN-V2-143 stay green). Tests: the two inputs above, a closed and an unclosed quoted form, and the negative control that a line mentioning `BEGIN` in prose with no `END` is masked as today.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer
2. **The URL pattern is linear and the stored seam has a cost test (failing case first).** `URL_USER_INFORMATION` (`SecretRedactor.java:178`, built by `urlUserInformation` `:330`) scans back from every scheme start on a line such as `x://y:x://y:…` with no `@`: about n²/6 steps on an 8192-character line, run by `DiagnosticCollector.stored` on every classified line and by the `Diagnostic` constructor on `stackTrace` (unbounded line count). Replace the backtracking by a find loop (the model is D1 of PLAN-V2-143, `redactPrivateKeyBlocks`) or bound the look-back, without changing any pinned output; add a timed test on a hostile flood (about 2000 lines of 8192 characters) through `DiagnosticCollector.stored` and through the `Diagnostic` constructor, with the bound stated in the test; turn the two equality-only smoke tests `SecretRedactorTest:192,201` into measured ones or say why not. Cut `Diagnostic.stackTrace` to the stored bound after redaction, so a credentialed URL whose closing `@` lies beyond the line cut cannot leave up to 8192 characters of partial password per line. Add the guard test for a long run of escape pairs (`\x\x\x…`, 1 000 000 characters) in a quoted value beside the plain-run test.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer
3. **The texts that still bypass the redactor, and the escaped-JSON shape.** (a) `RewriteFinding.message` and `.path` (`OpenRewriteLogParser.java:133`, no redactor call; ADR 009 admits it) and `Diagnostic.file` are stored unredacted: redact them at the seam the other parsers use (`DiagnosticCollector.stored` or the parser), with a test per field. (b) The escaped-JSON secret `{\"password\":\"hunter2\"}` matches no pattern (the separator class does not accept a backslash before the quote): add the shape with a negative control; the shape list is ask-at-refine (R07). (c) **Ask at refine, not decided here:** the `INVISIBLE` class (`SecretRedactor.java:75`, stripped from every text at `:338`) removes U+200C and U+200D (the joiners) and the bidirectional controls, which carry meaning in Persian, Indic and right-to-left text and in emoji sequences, so the ADR sentence "removing one costs no evidence" is false for them. The operator chose the Unicode widening on 2026-11-15; the question is only whether the class keeps those characters; the recommendation is to keep U+200C, U+200D and the bidi controls and to strip the rest, because a split label with a joiner is not a realistic secret shape; the answer and its false-positive cost go into ADR 009.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-null-safety
   - Persona: implementer
4. **The job-log read-out passes the redactor, or the gap is reported.** `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc:114` requires every read-out, "a log resource" included, to pass the redactor; `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobLog.java` (`readWhole`, around `:183`) and its callers carry no `SecretRedactor` call (the sibling `plan-marshall-mcp` had none at the last read; its local checkout lags `origin/main`: fetch before relying on that). Verify first which classes hand the job log out of the engine; redact what `JobLog` hands out, after the size cap and before the text leaves the class, with a failing case first; where the read-out lives in `plan-marshall-mcp`, report it to the operator in the landing message and change nothing there.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing, pm-dev-java:java-null-safety
   - Persona: implementer
5. **The statements the deliverables make false.** Amend ADR `core-build/009` in the lines D1 to D3 change (the "What stays readable" list, the matcher section for D3(c), the per-line-bound claim for D2) and `core-build.adoc` and `core-queue.adoc` where they name the redactor's bounds; no new ADR unless the outline takes a design decision (the PEM ordering, the seam of D3(a)).
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:** each of D1 to D4 has a test that fails on the head of `main` and passes after the change; the cost tests of D2 hold their stated bound; every output an existing `SecretRedactorTest` or `ActivityJournalMaskingTest` case pins is unchanged; ADR 009 and the guides state what the code does. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-core` and `pm-runtime` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run).

**Trace starting set:** `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` line 114 (the redaction of every read-out); `landings/PLAN-V2-143.md` Layer 3; ADR `core-build/009`; the redaction call sites `UnparsedEvidence`, `NpmErrorParser`, `ActivityJournal`, `ActivityRecorder`, `ProviderVerification`, `DiagnosticCollector.stored`.

## Standing Constraints

- **Failing case first** for D1 to D4 (record the failing run at the base commit); D2's measurement is the timed test, not the estimate of the landing analysis.
- **The output of every input an existing test pins stays byte-identical** (no existing case of `SecretRedactorTest` or `ActivityJournalMaskingTest` is changed, only added).
- **No dependency or plugin is added**; no new hand-written double.
- **Assertion messages:** every assertion added carries a message of 20 to 60 characters; `core/queue` and `core/build` tests are scanned once PLAN-V2-102 has landed.
- **Review lens:** `plan-marshall:persona-security-expert` (the redactor sits on the trust boundary between build output and the model); the `Persona:` lines name the work identity only.
- **Sonar:** read it over plain HTTP (`pullRequest=N`, never `branch=<feature>`); an open issue is fixed in a pushed commit or dismissed with `{issue-key}: {reason}` through `plan-marshall:workflow-integration-sonar:sonar_rest transition`.

## Claim Labels

All read at `9c961d58` by the landing analysis of PLAN-V2-143; the outline re-reads each before it acts. The cost figures are an untimed estimate; the D2 test is the measurement.

- OBSERVED: `SecretRedactor.java:344-368` runs `URL_USER_INFORMATION` (`:178`, `:344`), `QUOTED_ASSIGNMENTS` (`:348`), `UNCLOSED_QUOTED_ASSIGNMENTS` (`:352`) and `redactPrivateKeyBlocks` (`:368`, defined `:400`) last; `INVISIBLE` is `:75`, applied `:338`.
- OBSERVED: `OpenRewriteLogParser.java:133` builds `RewriteFinding` with `.message(match.group(6))` and no redactor call.
- OBSERVED: `pm-runtime/.../job/JobLog.java` has no `SecretRedactor` reference; `01-pipeline-and-subprocesses.adoc:114` requires it.
- HYPOTHESIS: the JDK keeps the possessive escape-pair group `(?:\\[^\r\n]RUN)*+` iterative at 1 000 000 characters; verify-first with the guard test.
- HYPOTHESIS: `URL_USER_INFORMATION` is quadratic on `x://y:x://y:…`; verify-first with the timed test before the fix.

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/queue/SecretRedactor.java` and `pm-core/src/test/java/de/planmarshall/core/queue/SecretRedactorTest.java` — D1, D2, D3(b), D3(c)
- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/build/Diagnostic.java`, `DiagnosticCollector.java`, `OpenRewriteLogParser.java`, `RewriteFinding.java` and their tests (`DiagnosticCollectorRedactionTest.java`, `OpenRewriteLogParserTest.java`) — D2, D3(a)
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobLog.java` and `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobLogTest.java` — D4
- OBSERVED: `pm-core/doc/adr/core-build/009-the-redactor-shapes-the-diagnostic-redaction-and-the-input-bounds.adoc`, `pm-core/doc/developer/core-build.adoc`, `pm-core/doc/developer/core-queue.adoc`, `pm-runtime/doc/developer/runtime-job.adoc` — D5
- OBSERVED, read not edited: `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` (line 114)

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/adr/README.adoc`. No `pom.xml`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-143 (shipped, #434) and PLAN-V2-83.
- Overlaps at class level, decided by the gate at emit: PLAN-V2-59 (`egress-record`) adds a branch to `SecretRedactor.redact(ActivityEntry)` and carries the `@UtilityClass` conversion of `SecretRedactor` (emit this plan after it, or rebase the hunks); PLAN-V2-102 (assertion messages of `core/queue` and `core/build` tests, the same test files); PLAN-V2-114 item 24 (statements, lines and messages of the same files); PLAN-V2-125 and 126 (the pre-existing messageless assertions of `SecretRedactorTest`).
- Not here: the pre-existing messageless assertions (102, 125, 126), the redaction of a job-log resource read in `plan-marshall-mcp` (a report to the operator), the `@UtilityClass` conversion of `SecretRedactor` (PLAN-V2-59).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-151-secret-redactor-hardening-2.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
