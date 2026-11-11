# Landing Analysis: PLAN-V2-127 — npm parser success markers, error prefixes and redaction

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
pr: #353 (merge `7df1a1df368f5b4e24325074e7dbd8ccd17ede65`, state `merged` per the CI abstraction; 27 files, +869 -72)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-11 from the runbook-lane
> message `rb-v2-127-npm-parser-success-and-redaction-001.md` (harness opencode, model deepseek-v4.1-flash/high).
> The message is a lead. The three layers were read by one read-only leaf from git at `7df1a1df` (no build); the
> orchestrator re-read the points marked "re-read"; the provider figures were re-read from GitHub, the run's progress
> file and the SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 a passing Jest or TAP run is a success | shipped | Marker rule: status SUCCESS, counts measured, `failed == 0`, `passed > 0` (`JestOutputParser`, `TapOutputParser`); a failed suite line and `Bail out!` force FAILURE; measured zero gives no marker; `OutcomeDerivation` row 7 and every signature unchanged. Tests `jestPassingRun`, `tapPassingRun`, `tapBailout`, `jestFailedSuite` fail without the change; three fixtures flipped, three negative controls untouched. |
| D2 npm tools that print no passing summary | shipped | `NpmErrorParser.INSTALL_SUMMARY` requires a printed count and sets the marker only when no error block exists; `tsc` and ESLint stay `INDETERMINATE`; `unrecognisedLog` deliberately changed; ADR `runtime-job/016` added, 015 marked superseded in part. |
| D3 read the prefix current npm prints | shipped in letter, partly unpinned | Both prefixes are accepted at five places and a fixture pair records npm 9 and npm 12 output; three of the five places have no test reaching the new-prefix branch (`E404_PACKAGE`, `CONFLICT_MARKER`, `firstLine`), so removing the `npm error` alternative there passes every test. |
| D4 the unparsed excerpt is redacted on what a person reads | shipped | Order in `UnparsedEvidence`: strip ANSI, reduce CR runs, last 50 lines, redact, byte cap; three tests fail on the old code by reading. |
| D5 the redactor knows the shapes an npm log carries | shipped, with gaps | Shapes (a) to (d) implemented with negative controls and an idempotence test; see Layer 3 for what still passes through. |
| D6 the npm error diagnostic is redacted at its source | shipped with a spec correction | The composed message is redacted, then cut to 500 (`NpmErrorParser.java:172-177`, re-read). The spec's motivating example cannot reach `Diagnostic.message`: the block starts at the code line, which `firstLine` returns; the lane reported this and ADR `core-build/006` states it. |

Facts block: `deliverables_total=6`, `deliverables_done=6` — consistent with this table.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), reason: a redactor and three parsers whose edge cases are a per-class question; no build.
- **Verdict:** specified — one finding; implemented — findings (minor); correct — findings (minor, security relevant), reported to the operator at once.

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence |
|---------|-------|---------|----------|
| L1-1 | D6's premise is false: the block starts at the `code` line and `firstLine` returns that line, so a later `request to https://u:p@host failed` line never reaches `Diagnostic.message`; the reachable vector is a secret in the code token. | checked, refutes | `NpmErrorParser.java:137-150`, `:223-232` (leaf) |
| L1-2 | D1 is consistent with the outcome table (row 7 needs exit 0 plus the marker), and the shared-file claim holds: no other commit touched `03-parsers-and-gates.adoc`, the `core-build` or `runtime-job` ADRs, `SecretRedactor` or `core/build` between the base and `23a7e51f`. PLAN-V2-131 and 132 (staged) will need ADR numbers `core-build/007` and `runtime-job/017`. | checked, confirms | leaf |
| L1-3 | D3's hypothesis "npm 12.2.2 prints `npm error`" rests on a fixture comment recording a real run. | not_shown_by_reading | — |

**Layer 1 verdict:** one finding — the D6 example was wrong and the spec needs the correction the lane recorded.

### Layer 2 — Implemented

- **Confirmed:** D1, D2, D4, D5 and D6 trace to code and to tests that fail without the change; no `pom.xml` and no signature change in the 27 files; ADR numbers are unique at `23a7e51f` (`core-build` 001 to 006, `runtime-job` 001 to 016).
- **Weak spots:** `cleanSyntaxToolLogs` feeds the same empty string to the `tsc` and the ESLint cases through the same record, so it exercises neither parser (`BuildOutputParserTest.java:411`); the spec's Expected Surface names `JestOutputParserTest` and `TapOutputParserTest`, which are not in the diff, so the marker is pinned only at the adapter level and by three fixtures; the test-first order is not shown by the history (test and fix share a commit per deliverable, the failing runs live in daemon job logs).
- **Stretched constraint:** `printsFailedSummary` and `Bail out!` change the printed status of Jest and TAP logs, not only the marker; no consumer reads that status, so outcomes are unchanged, but "no behaviour change beyond the named ones" is stretched.
- **Stale text:** the squash message says a literal `@` in a password is not covered by the URL shape, while the code and `SecretRedactorTest:114` mask it whole.
- **Surface delta:** `core-queue.adoc` and ADR `core-build/001` were touched beyond the declared surface and the lane named them; `doc/Traceability.adoc` carries the updated PM-SEC-1 lines.

**Layer 2 verdict:** findings — delivered and tested, with three unpinned D3 places, a duplicate test and a stretched constraint.

### Layer 3 — Correct (reported to the operator at once)

- **Redaction still lets these through (re-read at `SecretRedactor.java:74-123`):**
  - a URL password that holds a raw `/`, `?` or `#` (`https://deploy:pa/ss@registry.example/pkg` comes back whole; the password class is `[^\s/?#]+`);
  - a URL user that holds a raw `@` (the user class excludes `@` and then demands `:`), a percent-encoded scheme, and the single-user form `https://<token>@host` (documented residue);
  - credential flags outside the five names (`--auth-token`, `--api-key`, `--client-secret`, `--otp`, `-p`), upper-case flags (the patterns are compiled without `(?i)`), a value on the next line, and a value holding `;` or a quote;
  - an `npm_` token that is not exactly 36 characters or has a word character before it (the exact length is deliberate, see the Javadoc, and a labelled `_authToken=` is masked by an older pattern);
  - a PGP private-key block (the label must end in `PRIVATE KEY-----`), and a PEM block that begins before the 50-line window.
- **Journal path:** `SecretRedactor.redact` does not strip ANSI, so an escape-split label is still unredacted in an activity entry; D4 fixed only `UnparsedEvidence`.
- **Order:** redaction precedes the byte cap on every path read, so neither cut splits a single-line secret; a multi-line secret whose opening line falls outside the window leaks.
- **Complexity:** the URL pattern `(?i)\b([a-z][a-z0-9+.-]*://)...` rescans a long `[a-z0-9+.-]` run from every word boundary (quadratic on one long line such as `a.a.a.a...`), and the line length is unbounded before redaction; the slowdown was not measured. The lane's "false positive" argument for the ast-grep ReDoS warnings covers only pattern injection.
- **Unredacted messages:** `E404` embeds the quoted package specifier and `ERESOLVE` the peer and `from` tokens without redaction (`NpmErrorParser.java:74`, `:184-191`); the guide and ADR 006 say they are built from dependency names, which does not hold for a URL or git specifier carrying user information.
- **Install marker:** `BuildOutputParser.java:117` takes the install marker from any `added N packages` line whatever the verb, so `npm ci && npm test` with a runner no scanner reads (mocha, vitest) and exit 0 settles `succeeded` at row 7 where it was `indeterminate` before (re-read).
- **Edges that hold:** Jest and TAP markers on empty output, two summaries, a failing block after a passing one, todo-only runs and `1..0`; both npm prefixes at the places the leaf listed; CR reduction and idempotence of the flag, URL and PEM shapes.
- Not shown by reading: pass counts (10003 tests as the lane reports), `verify`, coverage, the failing runs, the real npm 12 output.

**Layer 3 verdict:** no blocker by reading; several minor correctness and security-relevant gaps in the redactor and one that can turn an unread test run into `succeeded`.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md | no dependency or plugin added; no generic `Exception`; `Optional` only as a return | conforms | no `pom.xml` among the 27 files |
| CLAUDE.md Code Standards | every assertion carries a message of 20 to 60 characters | conforms by eye, not machine-checked | shortest 20, longest 58 |
| CLAUDE.md Code Standards | test data from `cui-test-generator`; a literal stays only where it is the contract and the test says so | deviates | literal secrets and hosts in `SecretRedactorTest:103-215`, `UnparsedEvidenceTest:84`, `NpmErrorParserTest:113` without a contract comment; the classes already enable the generator controller |
| standing rules R10, R11, R19 | spec blocks byte-stable, next free ADR number, surface delta recorded | conforms | leaf |
| standing rules R17 | `BROKEN LINKS 0` | not shown by reading | anchors exist; `trace.py` not run here |
| standing rules R20 | test-first and generators | deviates in part | the failing runs are not shown by the history; generator data as above |

## Metrics and Anomalies

- Tokens: `unknown`; `plugin_version` `unknown`; executor `0.1.2088`. The landing-check reports `complete: false` (`total_tokens`, `plugin_version`), folded into the standing Open Defect on runbook-lane landings.
- Review: `review_body_findings_total=14` equals the provider's count; `review_body_findings_handled=14` is not reproducible from the provider (`review-gate` reads 0 handled), and the run's progress file carries 14 `finding:` lines, so the number is re-verified from the progress file, not a difference.
- One server restart cancelled an integration-tests build; the job was re-attached and returned green. One non-related flake (`LspClientTest.stubborn`) passed on re-run.

## Routing and Merge Behavior

- Review gate re-read: 0 unresolved threads; SonarCloud `pullRequest=353`: 0 open new-code issues, quality gate `OK`. Equal to the facts block.
- CI/merge: #353 `merged` at `7df1a1df`. The lane notes its Standing review authorization was used (a qualifying CodeRabbit review on a PR head, all findings handled, one re-trigger after the last push).
- Surface collisions: PLAN-V2-131 and 132 (staged, after 127) share `03-parsers-and-gates.adoc` and the `core-build` and `runtime-job` ADR numbers; none has landed, so no collision has happened. 131 and 132 should start from a branch after `7df1a1df` and take ADR numbers `core-build/007` and `runtime-job/017`.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#353`
- [x] row `landing` stamped `landings/PLAN-V2-127.md`
- [x] row `plan_marshall_plan_id` stamped `v2-127-npm-parser-success-and-redaction`
- [x] the incomplete landing facts fold into the standing Open Defect on runbook-lane landings; a Watch records the residue
- [x] message archived after the disposition was persisted
- [ ] residue plan: not staged; put to the operator (Follow-Ups)

## Follow-Ups

- **Layer 3 is reported to the operator at once and becomes a staged fix-plan spec only on the operator's word.** A plan for the redactor would close the gaps listed above (URL passwords and users, flag names and case, `npm_` length, PGP and out-of-window PEM, the journal path's ANSI, the quadratic pattern, E404/ERESOLVE messages) and the install-marker verb restriction, and would pin the three D3 places and the parser-level tests. The scope would be a "normal" plan by the operator's rule, so a plan-marshall lane unless the operator calls it a residue.
- The D6 spec correction belongs to the next reconcile (the lane's own residue item).
