# PLAN-V2-138: Harden the secret redactor and the npm parser after PLAN-V2-127: the shapes it still lets through, the journal path, the diagnostics and the install marker

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
origin: the three-layer analysis of PLAN-V2-127 (`landings/PLAN-V2-127.md`: layer 3 and layer 2), the merged pull request #353 (`7df1a1df368f5b4e24325074e7dbd8ccd17ede65`), the lane message `inbox/archive/rb-v2-127-npm-parser-success-and-redaction/rb-v2-127-npm-parser-success-and-redaction-001.md` and the operator's word of 2026-11-11 to stage the residue plans (the redactor plan on the plan-marshall lane: a normal plan with security weight)
lane: plan-marshall

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-V2-138-secret-redactor-hardening.md` and is queued as one row file, `queue/PLAN-V2-138.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (the current one; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R16, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R09: no confinement; R12: no message is added; R13: no stored format changes; R14, R15: no protocol or DSL change. R05, R10 because the paragraph of `doc/specification/job-runtime/03-parsers-and-gates.adoc` that names the redactor's shapes is edited: every block a test reads stays byte-stable. R11 because one ADR is written. R16 because an ADR records each redactor decision. R07 because Carried Open Items asks the operator at refine.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-queue.adoc` (the redactor's shapes), `pm-core/doc/developer/core-build.adoc` (the npm parser and the unparsed excerpt), `pm-runtime/doc/developer/runtime-job.adoc` (the install marker); ADR directories: `pm-core/doc/adr/core-build/` (one ADR for the redactor shapes and the diagnostic redaction; next free number at `origin/main`, R11: `core-build/007` and `runtime-job/017` are reserved by PLAN-V2-131 and 132 if they land first, so the lane takes the next free number against `origin/main`) and, only if Deliverable 5 changes the install-marker decision, `pm-runtime/doc/adr/runtime-job/` (the next free number; ADR 016 is superseded in part, marked as ADR 015 was).
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing` and `java-security` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. R20 applies to every test line this plan touches.

## Objective

PLAN-V2-127 (#353) taught `SecretRedactor` four shapes of an npm log (npm access tokens, URL user information, credential flags, PEM private-key blocks) and redacted the npm error diagnostic at its source. The analysis of its landing read the redactor and the npm parser line by line and found what still passes through: a URL password with a raw `/`, `?` or `#`, a URL user with a raw `@`, credential flags outside five lower-case names, a PGP private-key block, the journal path that does not strip escape sequences, and an npm diagnostic that embeds a package specifier unredacted. It also found one parser decision that can turn an unread test run into `succeeded` (the install marker is taken from any `added N packages` line whatever the job verb) and three places of the new npm prefix that no test reaches.

This plan closes those, one deliverable per class of gap, and records what it deliberately leaves readable. It changes redaction shapes and the install marker; it adds no new parser and no new field to a stored format.

## Deliverables

Six deliverables, one commit each, the test order `test-first` for every one (a negative control and a failing run before the fix, the failing run named in the progress note). The scope is six deliverables against the soft cap of about six: the cut line, if the outline wants one, is Deliverable 6 (the three pins and the parser-level tests), which can run as its own pull request.

1. **URL user information (test first).** `SecretRedactor.URL_USER_INFORMATION` (`pm-core/.../core/queue/SecretRedactor.java:84-85` at `7df1a1df`: `(?i)\b([a-z][a-z0-9+.-]*://)([^\s/:@?#]+):([^\s/?#]+)@`) leaves these readable: a password holding a raw `/`, `?` or `#` (`https://deploy:pa/ss@registry.example/pkg` comes back whole), a user holding a raw `@` (`https://me@corp.example:s3cret@host/p`: no start position matches), and a percent-encoded scheme. Mask the secret in each, keep the scheme, the user and the host readable, keep the percent-encoded password form masked, and add a negative control for each shape that must stay readable (a path with a colon, a prose sentence, `scheme://host:8080/path`). The single-user form `https://<token>@host` stays readable unless the operator decides otherwise (Carried Open Items 1). The pattern's cost is measured: a long single line such as `a.a.a.a…` of the sizes the spec of PLAN-V2-131 uses must not cost more than a stated bound (the lane measures it by a test with a time-free assertion, for example by a counted-steps harness or a fixed input whose result is asserted, and says how); if PLAN-V2-131 has landed, its per-line bound applies before redaction on the unparsed-evidence path and this deliverable adds the same bound on the journal path or makes the pattern linear (the outline chooses and records which). **Done when:** each shape above fails before and passes after; every negative control stays readable; the existing `urlUserInformation` case and the idempotence test pass unchanged; the Javadoc of the constant states what stays readable and why.
2. **Credential flags (test first).** The flag patterns (`SecretRedactor.java:93-108`) name only `password|passwd|passphrase|token|secret` directly after `--`, are compiled without `(?i)`, stop a value at whitespace, a quote or `;`, and do not carry a value across a line end. Make them case-insensitive, extend the closed name list (Carried Open Items 2 asks the operator which names), mask a value that holds `;` or a quote (the escape-pair alternation of the quoted forms stays), and mask a quoted value that spans two lines. A flag outside the list stays readable and the Javadoc says so. **Done when:** `--PASSWORD x`, each added name, `--password pa;ss`, `--password 'abc"def'` and a two-line quoted value fail before and pass after; `--password-file notes.txt`, `--token` at the end of a line and prose that merely contains the word stay readable (negative controls); the idempotence test passes; the false-positive cost the leaf measured (`--token <token>` in usage text masks the next word) is stated in the Javadoc.
3. **Token and key shapes (test first).** (a) `NPM_ACCESS_TOKEN` is `\bnpm_[A-Za-z0-9]{36}\b`: a token of 37 or more characters, one followed by `_` and one preceded by a word character comes back whole when no label precedes it. The exact length is deliberate (the Javadoc: a package name that merely starts with `npm_` stays readable); the operator decides whether to keep it (Carried Open Items 3). (b) `PEM_PRIVATE_KEY` and its unclosed variant do not match `-----BEGIN PGP PRIVATE KEY BLOCK-----`; add it, with its `END` line, and keep the over-masking of the unclosed form documented. (c) A PEM block whose `BEGIN` line lies before the 50-line window is not masked (carried by the spec of PLAN-V2-127): this deliverable states the residue in the guide and does not widen the window. **Done when:** each of (a) as decided, (b) fails before and passes after; the negative controls (a package name `npm_pkg`, prose with `PRIVATE KEY` outside a marker) stay readable; the Javadoc lists the shapes.
4. **The journal path and the npm diagnostics (test first).** (a) `SecretRedactor.redact` (the journal path, `:152-165`) does not strip escape sequences, so `ESC[1mpassword ESC[0m=value` is still unredacted in an activity entry; D4 of PLAN-V2-127 fixed only `UnparsedEvidence`. Strip them there too, or make the label patterns tolerate them (the outline chooses and records which; no change to what `ActivityJournalMaskingTest` expects). (b) The `E404` message embeds the quoted package specifier and `ERESOLVE` the peer and `from` tokens without redaction (`NpmErrorParser.java:74`, `:184-191`); redact the composed message before it is cut (the order of `storedRedacted`, `NpmErrorParser.java:172-177`, stays), and correct the sentence of the guide and the ADR that says they are built from dependency names. **Done when:** an escape-split label on the journal path and a specifier with user information in an `E404` and in an `ERESOLVE` message fail before and pass after; `ActivityJournalMaskingTest` and the existing `ERESOLVE`/`E404` messages without a secret are unchanged.
5. **The install marker only for an install (test first; ask-at-refine).** `BuildOutputParser.java:117` (`pm-runtime`) dispatches on `NpmErrorParser.INSTALL_SUMMARY.matcher(output).find()` whatever the job verb, so `npm ci && npm test` with a runner no scanner reads (mocha, vitest) and exit 0 settles `succeeded` where it was `indeterminate` before. Restrict the marker to an install job, or require that no test runner's output precedes the install line (the outline reads the job record to see whether the verb is available at this seam and records which it uses; Carried Open Items 4 asks the operator). **Done when:** a log holding an `added N packages` line followed by output of an unscanned runner and exit 0 stays `indeterminate`; an install-only log still settles `succeeded`; ADR `runtime-job/016` is marked superseded in part if the decision changes.
6. **The pins and the parser-level tests (test first where a behaviour is pinned).** (a) Three places of the new npm prefix have no test that reaches their `npm error` branch: `E404_PACKAGE` (the npm 12 fixture uses the "requested resource" wording), `CONFLICT_MARKER` (no `ERESOLVE` log in the new prefix) and `firstLine` (`NpmErrorParserTest.java:113` uses `npm ERR!`); add a case for each so that removing the `npm error` alternative there fails a test. (b) `BuildOutputParserTest.cleanSyntaxToolLogs` (`:411`) feeds one empty string to the `tsc` and the ESLint cases through the same record, so it exercises neither parser; give each its own input and parser. (c) `JestOutputParserTest` and `TapOutputParserTest`, named in the Expected Surface of PLAN-V2-127 and not touched, get the parser-level cases for the marker: skipped-only TAP, a passing summary with a filed diagnostic, a Jest `todo` summary, and the `1..N` plan line against the counts (the leaf found that `1..5` with `# tests 3` still gives a marker: the outline decides whether that is a defect and records it). **Done when:** each pin fails when its alternative is removed (shown by the temporary mutation and a revert for one of the three); the two syntax-tool cases use distinct inputs; the parser-level cases pass.

**Done when (plan):** Deliverables 1 to 6; no `pom.xml` in `git diff --stat`; no `@Test` removed; the ADR exists with the next free number at `origin/main`; the guides name the shapes the redactor still leaves readable; `ActivityJournalMaskingTest`, `UnparsedEvidenceTest` and the existing `SecretRedactorTest` cases pass unchanged. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed, in their own commit where they touch files outside the plan's scope), then per-module `test -pl <module> -am` runs of `pm-core` and `pm-runtime` (a full verify exceeds the host's budget: per-module runs with `--timeout`), `verify -Pcoverage`, and `verify -Pintegration-tests -pl pm-runtime -am` (Deliverable 5 touches the settlement path) and `-pl pm-core -am`. **Links:** the R17 check with the absolute paths, after `git fetch` of the documentation checkout, reports `BROKEN LINKS 0` for the files this plan touches.

## Standing Constraints

- **Redaction runs before every cut.** The order stays strip, reduce, window, redact, byte cap on the evidence path and redact then cut on the diagnostic path; no deliverable moves a redaction after a cut. A multi-line secret whose opening line falls outside the window leaks and stays a documented residue.
- **A shape that cannot be told from prose stays readable and is named.** Each deliverable states in the Javadoc and the guide what its pattern deliberately leaves alone (the false-positive side is the cost of the evidence the operator reads), and adds a negative control for it.
- **No pattern injection and no unbounded repetition added.** Every `Pattern.compile` argument stays a `static final` literal composed from literals; a new repetition over an attacker-controlled line carries the bound or the proof Deliverable 1 asks for. An inline `@SuppressWarnings` for a Sonar rule carries its reason (R16).
- **No behaviour change beyond the named ones.** No `OutputParser` or `ParsedBuild` signature changes; `OutcomeDerivation` is not edited.
- **Not here:** other parsers' diagnostic messages at `DiagnosticCollector.stored` (PLAN-V2-127 settled: npm only), unlabelled query secrets (`sig=`, `signature=`, `key=`, `code=`, `X-Amz-Signature=`), bare JWTs and AWS key ids, `Authorization:` schemes other than `bearer|basic|token` (the pre-existing `AUTH_HEADER` masks one word), the PEM block that begins before the window, the single-user URL form unless Carried Open Items 1 says otherwise, the per-line and whole-file bounds of the build log (PLAN-V2-131), and the coverage parsers (PLAN-V2-132).
- **No dependency or plugin is added.**
- **Where the project analyses its pull requests with Sonar** (read it over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` and transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker. The merge gate reads the open-issue count at the head (`0` confirmed, or every remaining issue dismissed); an `undecidable` read blocks.

## Claim Labels

All lines read at `7df1a1df368f5b4e24325074e7dbd8ccd17ede65` (the merge commit of #353) through `git show`; the shapes below are the leaf's reading of the patterns, and the orchestrator re-read the lines named "re-read".

- OBSERVED: `URL_USER_INFORMATION` is `(?i)\b([a-z][a-z0-9+.-]*://)([^\s/:@?#]+):([^\s/?#]+)@`: the user class excludes `@`, the password class excludes `/`, `?` and `#`; so a raw `/` in the password and a raw `@` in the user leave the URL readable — `SecretRedactor.java:84-85` (re-read)
- OBSERVED: `CREDENTIAL_FLAG_NAMES` is `password|passwd|passphrase|token|secret` after `--`, the three flag patterns have no `(?i)`, the unquoted value class is `[^\s"';]+`, the quoted and unclosed forms stop at the line end — `SecretRedactor.java:93-108` (re-read)
- OBSERVED: `NPM_ACCESS_TOKEN` is `\bnpm_[A-Za-z0-9]{36}\b`; `PEM_PRIVATE_KEY` is `(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----` — `SecretRedactor.java:77`, `:113-121` (re-read); a PGP block's label ends in `PRIVATE KEY BLOCK-----` and does not match (the leaf; not run)
- OBSERVED: `BuildOutputParser.java:117` dispatches on `NpmErrorParser.INSTALL_SUMMARY.matcher(output).find()` without reading the job verb (re-read)
- OBSERVED: `NpmErrorParser.storedRedacted` redacts the composed message and then cuts it (`:172-177`, re-read); `E404_PACKAGE` embeds the quoted specifier (`:74`, re-read); `ERESOLVE` embeds the peer and `from` tokens (`:184-191`, the leaf)
- OBSERVED: `SecretRedactor.redact` (`:152-165`) does not strip escape sequences; `UnparsedEvidence` does (the leaf)
- HYPOTHESIS: the URL pattern is quadratic on one long `[a-z0-9+.-]` line — the leaf's reading of the pattern, not measured; confirm or refute by the test of Deliverable 1 (verify-at-outline)
- HYPOTHESIS: the job verb is available at the `BuildOutputParser` seam — confirm or refute at `BuildOutputParser.java` § `parse` and the job record (verify-at-outline)
- HYPOTHESIS: `TAP` `1..5` with `# tests 3 / # pass 3` and exit 0 gives a marker (the leaf, `TapOutputParser.java:105-125`) — confirm or refute by the case of Deliverable 6(c)
- Verify-first clause: before editing, list the open pull requests for one that touches the files of the Expected Surface (PLAN-V2-131 and 132 are staged and share `03-parsers-and-gates.adoc`, `UnparsedEvidence` and the `core-build` ADR numbers; PLAN-V2-137 and 139 do not), re-read the files at the head, re-run the scans and the counts above, and drop what has changed; a refutation loops back to re-scope.

## Expected Surface

Code and tests (R19: per file; every file OBSERVED at `7df1a1df` unless marked):

- `pm-core/src/main/java/de/planmarshall/core/queue/SecretRedactor.java`, `pm-core/src/test/java/de/planmarshall/core/queue/SecretRedactorTest.java` — D1, D2, D3, D4(a)
- `pm-core/src/test/java/de/planmarshall/core/queue/ActivityJournalMaskingTest.java` — D4(a), expected to stay unchanged (read; edited only if the journal path's behaviour changes a case it asserts)
- `pm-core/src/main/java/de/planmarshall/core/queue/ActivityJournal.java` — read, edited only if the journal path calls the redactor differently
- `pm-core/src/main/java/de/planmarshall/core/build/NpmErrorParser.java`, `pm-core/src/test/java/de/planmarshall/core/build/NpmErrorParserTest.java` — D4(b), D6(a)
- `pm-core/src/test/java/de/planmarshall/core/build/JestOutputParserTest.java`, `pm-core/src/test/java/de/planmarshall/core/build/TapOutputParserTest.java` — D6(c); `pm-core/src/main/java/de/planmarshall/core/build/TapOutputParser.java` only if the `1..N` case is a defect
- `pm-runtime/src/main/java/de/planmarshall/runtime/job/BuildOutputParser.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/BuildOutputParserTest.java` — D5, item 5(a)
- `pm-core/src/main/java/de/planmarshall/core/build/UnparsedEvidence.java`, `pm-core/src/main/java/de/planmarshall/core/build/GradleOutputParser.java`, `pm-core/src/main/java/de/planmarshall/core/build/TypeScriptOutputParser.java`, `pm-core/src/main/java/de/planmarshall/core/build/EslintOutputParser.java`, `pm-core/src/main/java/de/planmarshall/core/build/JestOutputParser.java`, `pm-core/src/main/java/de/planmarshall/core/build/TapOutputParser.java` — item 5 (carried 2026-11-12; OBSERVED at `4ef5c7d6`), D6(b)
- HYPOTHESIS, new: the fixtures the cases need under `pm-core/src/test/resources/ported/build/npm-errors/` and the runtime test fixtures (the outline names them)

Documentation:

- `pm-core/doc/developer/core-queue.adoc` (the redactor's shapes and what stays readable), `pm-core/doc/developer/core-build.adoc` (the diagnostics sentence), `pm-runtime/doc/developer/runtime-job.adoc` (the install marker)
- HYPOTHESIS, new: `pm-core/doc/adr/core-build/007-…adoc` — one ADR, the next free number at `origin/main` (R11); `pm-runtime/doc/adr/runtime-job/017-…adoc` only if D5 changes the install-marker decision, with ADR 016 marked superseded in part
- `doc/specification/job-runtime/03-parsers-and-gates.adoc` — the paragraph that names the redactor's shapes, if it does (R05, R10: the blocks a test reads stay byte-stable)

Not surface (R19): `doc/Traceability.adoc` unless a verification line names a changed shape (R08), every `pom.xml`, `OutcomeDerivation.java`, `OutputParser.java`, `ParsedBuild.java`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-127 (shipped, #353, `7df1a1df`: the redactor shapes and the npm parser this plan hardens), PLAN-V2-83, 97, 98, 99 (shipped).
- Runs after PLAN-V2-131 and 132 (staged; both start after `7df1a1df`): they share `03-parsers-and-gates.adoc`, `UnparsedEvidence.java` and the `core-build` ADR numbers, and PLAN-V2-131's per-line bound before the regexes is the answer to the pattern-cost half of Deliverable 1. If this plan runs first, Deliverable 1 makes the URL pattern linear itself and the ADR numbers shift for 131 and 132 (they renumber against `origin/main`, R11).
- Overlaps at file level are decided by the disjointness gate at emit, from the Expected Surface above.

## Carried Open Items

Ask-at-refine, one at a time, each with a recommendation (R07):

1. **The single-user URL form `https://<token>@host` (D1).** Options: leave it readable (the documented residue), or mask the user part when it looks like a token (length and class). Recommendation: leave it readable and say so; confidence about 70%.
2. **The credential-flag names (D2).** Options: add `auth-token`, `api-key`, `apikey`, `access-token`, `client-secret`, `otp` and `key`; add the first six only; keep the five. Recommendation: the first six, not `key` (it masks too much prose); confidence about 70%.
3. **The exact length of the npm token (D3(a)).** Options: keep `{36}` exactly (the shape npm documents; a longer token is not an npm token), or accept `{36,}` and a leading underscore. Recommendation: keep it exact; confidence about 60%.
4. **The install marker (D5).** Options: only for a job whose verb is an install, or only when no test-runner output follows the install line. Recommendation: the verb restriction if the verb is available at the seam, else the output rule; confidence about 65%.

5. **The residue of the PLAN-V2-131 landing, the npm dispatcher patterns and the excerpt's tail** (`landings/PLAN-V2-131.md` m3, m4; carried 2026-11-12 under the operator's rule "every leftover goes into a staged plan"; this plan is the one that edits `BuildOutputParser`, `UnparsedEvidence` and the redactor the excerpt feeds). Re-read at the head of `main` before editing. (a) `BuildOutputParser.java:83` feeds six parsers that PLAN-V2-131 left without a per-line cut (`GradleOutputParser`, `NpmErrorParser`, `TypeScriptOutputParser`, `EslintOutputParser`, `JestOutputParser`, `TapOutputParser`), and the dispatcher's own patterns `ESLINT_ROW` (`BuildOutputParser.java:61`) and `JEST_MARKER` (`:65`) are `(?m)^\s*…` where `\s*` can span newlines: they run on up to 16 MiB with no line cut, and a long run of blank lines looks quadratic by reading (timing not shown). Apply `BuildLogText.boundLines` at the entry of the six parsers (the npm toolchain's `NpmErrorParser`, `TypeScriptOutputParser`, `EslintOutputParser`, `JestOutputParser`, `TapOutputParser` and the `GradleOutputParser`, as ADR `core-build/007` says a plan that touches one does) and of the dispatcher, and replace `\s*` by a horizontal-space class where the pattern must not cross a line; **Done when:** a log of one million blank or whitespace lines and a hostile line of the limit's length return inside a stated bound that the head misses (the failing run is reported). (b) `UnparsedEvidence.of` copies the reduced text three times (`stripAnsi`, the builder, `toString`) and does not line-cut the kept fifty lines, so a log whose last line is one huge line (up to the 16 MiB cap) goes whole into `SecretRedactor.redactText`: cut the kept tail with `BuildLogText.boundLines` before the redaction and say so in the guide (this also bounds D4's journal-path input); its Javadoc "costs the reduced text" stays true. Not here: the Maven, OpenRewrite and pytest patterns, the cut report and `JobLog` (PLAN-V2-114 item 11).

## Landing

This plan runs under the plan lifecycle (`/plan-marshall`). Its `request.md` `source_id` points at this spec, so `orchestrator inbox detect` classifies it as orchestrated and its phase 6 files one `landing` message in the epic inbox with the `landing-facts` block of `standards/landing-payload-spec.md`; the orchestrator reconciles the row from it and from the pull request. The plan writes nothing else under `.plan/orchestrator/`.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-138-secret-redactor-hardening.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
