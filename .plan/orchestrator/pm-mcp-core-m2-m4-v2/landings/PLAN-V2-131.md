# Landing Analysis: PLAN-V2-131 — a build log costs the settlement a bounded read, bounded lines and an excerpt that does not list the log

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
pr: #382 (merge `4ef5c7d6bbdf4b99d2de4fc9058b0c54389c831a`, state `merged` per the CI abstraction)

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-12 from the message
> `rb-v2-131-build-log-bounds-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, executor
> 0.1.2106). The message is a lead. The three layers were read by one read-only leaf from git at `4ef5c7d6` (no build,
> no test run); the orchestrator re-read the points marked "re-read"; the provider figures were re-read from GitHub
> (`ci pr view`, `ci pr reviews`, `runbook_lane review-gate`) and the SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 cap on the two whole-file reads | shipped, one clause partly | `BuildLogText.java:58-71` and `JobLog.java:52-63` read the cap (16 MiB) from `build-limits.properties` and `job-log-limits.properties` through `LimitProperties.read`; no literal in the code; an alternate resource is read by the tests. `BuildLogText` reads over-cap logs from the end with a bounded channel read (`:142-179`), `JobLog.readWhole` answers first line plus last cap bytes with `Complete.length` the real length (`:201-236`). **"The result says it was cut" is documented only** (`BuildLogText.java:40-50`): `read` returns a plain `String`, no caller learns of the cut (M1). |
| D2 per-line limit and the three patterns | shipped in form; **the rewrites are pre-tests** | the per-line limit is a data value (8192, `BuildLogText.java:63-78`) and `boundLines` runs in `parseStripped` of `MavenOutputParser` (`:150`), `OpenRewriteLogParser` (`:89`) and `PyprojectOutputParser` (`:156`). `EXECUTION_HEADER` and `FINDING` are NOT rewritten: `MavenOutputParser.executionHeaderRunner` (`:119-125`, re-read) rejects a line without `@`, `OpenRewriteLogParser.mayBeFinding` (`:149-151`, re-read) a line without `CUI_REWRITE-10` and ` by `; the expressions at `:82-83` and `:52-53` are unchanged. Only `SUMMARY_LINE` is rewritten (possessive, two steps), with a fallback to the ported expression for a line holding a line terminator. `EXCEPTION_LINE` and `SECTION_LINE` were measured linear and dropped, as the spec allows. |
| D3 excerpt without listing the log | shipped | `UnparsedEvidence.java:87-121` scans backwards for line starts; the carriage-return reduction and the line count are equivalent to the old `lines().toList()` for `\r\n`, trailing `\r`, mid-line frames, leading blank lines, a trailing newline, fewer than and exactly 50 lines (leaf, by reading). The memory bound is the reduced text, not the tail (m4). |
| D4 statements, ADR, guides | shipped | `03-parsers-and-gates.adoc` (+10, includes the properties tag), ADR `core-build/007` (Context, Decision, Alternatives, Consequences; its Consequences state that the pre-tests are necessary conditions), `core-build.adoc`, `runtime-job.adoc`. No ADR in `runtime-job/`, correct because `JobLog`'s answer type is unchanged. No sentence says "the whole log" where the cap applies. |
| Not in the declared surface | explained | `LaunchReport.java` (12 lines, Javadoc only, verified by the leaf). |

## Three-Layer Analysis

Read at `4ef5c7d6` by one read-only leaf (`execution-context-level-3`); the orchestrator re-read M1 and M2 and the pre-tests at their `file:line`.

### Layer 1 — Specified

Every deliverable is in the diff. Two clauses are not met as written: the cut is not reported (D1, M1), and the Done-when of D2 ("for each named pattern a hostile line of the limit's length returns inside a bound that the pre-change code misses") is met only for hostile lines that lack the pre-test token (M2).

### Layer 2 — Implemented

Conformance to the standing rules and `CLAUDE.md`: no Optional parameter, no `@Nullable`, no record, no fake, no `Random`/`UUID`, no AAA comments, no generic catch, every new test has `@DisplayName`; the three new public constants are documented. Deviations: concatenated assertion messages of 62 characters (`BuildLogBoundsTest:187`, `:196`) and four supplier messages that carry the generated line (`:261`, `:278`, `:289`, `:303`); three generated-line loops and a 15-case edge list that are not `@ParameterizedTest` (`BuildLogBoundsTest:254-305`); literal filler (`"x\n".repeat`, `"line " + index`) and a literal 16 777 216 bound in `UnparsedEvidenceTest`; near-duplicate `tail`/`read`/`fromLineStart` code in `BuildLogText:156-192` and `JobLog:242-296`.

### Layer 3 — Correct

**M1 (major, re-read). The cut is never reported, and the Maven parser aggregates silently on a cut log.** `CountScan` sums every summary block (`MavenOutputParser:226-304`); on a log over 16 MiB the earlier module blocks are gone, so the parse returns `Measured` for the tail alone, and `currentRunner` (`:236`) is set only by a header that may lie before the cut, so `runnerExecuted` (`:234`) is partial. Scenario: a 20 MiB multi-module log whose failing module lies in the cut head: the settlement sees the tail's counts, `failingTests` is false, the contradiction check misses the failure, and nothing signals the cut. The spec's grounding section left the runner-header point to the outline; no code addresses it.

**M2 (major, re-read). The pre-tests do not bound a token-carrying hostile line.** An 8192-character line that carries `@` (Maven), or `CUI_REWRITE-10` and ` by ` (OpenRewrite), or a pytest line holding one line terminator, still takes the quadratic path. Every hostile test line lacks the token (`BuildLogBoundsTest:152` `--- a:b:c ` repeated, no `@`; `:178` finding lines without ` by `; `:191` `passed ` repeated), so the tests prove the pre-test only. By arithmetic on the landing's own figures, 2048 lines at the cap (16 MiB / 8192) cost about 28 s (Maven, 13.5 ms/line), about 11 s (finding, 5.2 ms/line) and about 79 s (pytest with a terminator, 38.5 ms/line); not shown by running. ADR 007 admits the pre-tests are necessary conditions only, but the Done-when asked for a bound, and the stall the plan exists to prevent stays reachable (one log line stuffed with the token).

Minor: m1 `JobLog.capped` reads a full cap-sized head window (`:229`), so I/O and allocation reach twice the cap; a 15 MiB first line gives an answer over the cap; a head with no newline (`:231-233`) returns the head alone, never reads the tail, and still answers `Complete`. m2 `LimitProperties` accepts 0 (`max-line-length=0` blanks every line, `log-max-bytes=0` gives an empty read) and a value over `Integer.MAX_VALUE` throws `ArithmeticException` at class initialisation; absent, malformed and negative values fail loudly. m3 the six uncut parsers sit on the settlement path (stated in the ADR), and the npm dispatcher patterns `ESLINT_ROW` (`:61`) and `JEST_MARKER` (`:65`) are `(?m)^\s*…` where `\s*` can span newlines, run on up to 16 MiB with no line cut; they are not in the residue list (timing not shown by reading). m4 `UnparsedEvidence` copies O(total) three times (`stripAnsi`, the builder, `toString`), and the kept 50 lines are not line-cut, so a log ending in one huge line goes whole into `SecretRedactor.redactText`. m5 a newline at the window's last byte makes `fromLineStart` return an empty array (`BuildLogText:185-191`, `JobLog:269-276`). m6 `readLines` stays uncapped (`SubprocessJobRunner:1240`, `WorkerActivityPump:55`; stated in the ADR). m7 `boundsRepeatedRewriteFindingLines` parses one single-hostile-line log 201 times and stops at the first PARSE_ERROR, though its message says "200 hostile lines"; a 1 s wall bound can flake on shared CI.

Checked and found fine: UTF-8 cut after a newline cannot split a multibyte sequence; a newline-free tail is kept whole; short reads and shrink/grow of the file; `TRUNCATED` is never answered for an over-cap `JobLog`, and `SubprocessJobRunner:1494-1505` handles `Complete` and `Unavailable` exhaustively; the first line plus the tail is built without duplicating the first line, and `LaunchReport.read` parses it as before; `boundLines` cuts to the prefix, leaves a line under the limit unchanged and keeps `\n`; the landing's list of six uncut parsers is complete; the `FINDING` and `SUMMARY_LINE` changes are result-equivalent below the bounds, and the differential tests are real (`PORTED_*` copies over 500 generated lines); the limit keys and resource paths agree between code, data and alternate files; `LaunchReport.java` is Javadoc only. Not shown by reading: whether the sibling assembly includes `*.properties` in the native image (this repository has no resource-config; the PLAN-V2-120 `stalled-scope.properties` precedent is treated the same way).

## Conformance to the Project's Own Rules

See Layer 2. The review gate at the merge head reads 0 unresolved threads and 5 body findings (`runbook_lane review-gate`, re-read; `handled` is the lane's progress-file count and is not re-derivable here); the landing's own count is 5 of 5.

## Unverified leads

- The landing's mutation claims (job logs `b7d5d26b…`, `39d27da8…`, `79d0f9ae…`, `bcfe2ea8…`) were not opened.
- The 1 s timing bound under CI load was not run.
- The native-image inclusion of the two new `*.properties` files is a question for the assembly, not shown here.

## Metrics and Anomalies

`complete: false` for two keys only (`total_tokens`, `plugin_version` `unknown`); `surface_delta` unmeasured (declared and realized paths not supplied). Sonar over plain HTTP: `pullRequest=382` 0 open, gate `OK`; `main` re-analysed at `4ef5c7d6`, 85 open (unchanged), gate `OK`. The lane's own `sonar-gate` answered `not_configured` while the project analyses its pull requests (the landing says so). Self-review ended `budget-exhausted` after four rounds (the operator chose a fourth); its two LOW items were closed in `eda31a09` without a fifth round.

## Routing and Merge Behavior

One PR (#382), squash through the merge queue. The message carries no `candidate-lesson`, so nothing is routed to `lessons-routing`. The landing records five shell-rule breaks (`grep`, `tail`, `rm`, `git add -A`) of the lane, corrected in the run.

## Reconciliation Actions

Row PLAN-V2-131 `running` -> `shipped`, `pr` `#382`, `landing` `landings/PLAN-V2-131.md`, `plan_marshall_plan_id` `rb-v2-131-build-log-bounds`; message `rb-v2-131-build-log-bounds-001` archived; Watch entry in `epic.md`; decision log.

## Follow-Ups

Placed 2026-11-12 under the operator's rule "every leftover goes into a staged plan":

1. M1 (the cut is not reported), M2 (the pre-tests do not bound a token-carrying line), m1, m5, m6, m7 -> PLAN-V2-114 item 11 (the rewrite to a linear form, hostile tests that carry the token, the cut made visible to the settlement as an ask-at-refine with a recommendation).
2. m3 (the npm dispatcher patterns and the six uncut parsers) and m4 (the excerpt's tail copy and its uncut long line) -> PLAN-V2-138 item 5.
3. m2 (`LimitProperties` accepts 0 and overflows) and the Layer-2 deviations (assertion messages, `@ParameterizedTest`, literal filler, duplicated tail-read code) -> PLAN-V2-125.
4. PLAN-V2-132 (coverage parsers) starts from this tree: ADR number `core-build/008`.
3. PLAN-V2-132 (coverage parsers) starts from this tree: ADR number `core-build/008`.
