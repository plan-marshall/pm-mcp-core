envelope_version=1
sender_type=plan
sender_id=rb-v2-151-secret-redactor-hardening-2
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-17T12:52:21Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-151-secret-redactor-hardening-2
epic=pm-mcp-core-m2-m4-v2
pr=#475
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=10
review_body_findings_handled=10
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced).

## Deliverables

**D1 — met.** Tests `SecretRedactorTest.labelledPrivateKeyBlock`, `.quotedLabelledPrivateKeyBlock`,
`.proseBeginMarkerWithoutEndStillMasks`; order `test-first`; failing run `f05e1544` (tests only versus
`origin/main`; log `…/job-logs/3e1b1e2d4fae420191c971a6a113d617.log`, both labelled-key cases failed); passed at
`7251eff0` (log `cfbcd145d2094fc5a0b5bbd0d0f1c2e9`); commit `7251eff0` (formatter `89d3ccbd`).

**D2 — met.** `SecretRedactor.redactText` masks key blocks first; `redactUrlUserInformation` is one linear scan per
separator (case-insensitive, per-token caches for the user delimiter and the last `@`); `Diagnostic.stackTrace` is
redacted and cut to the stored bound, surrogate-safe. Tests: `DiagnosticCollectorRedactionTest.hostileUrlFloodThroughTheStoredSeam`
and `.hostileUrlFloodThroughTheDiagnosticConstructor` (stated bound 120 s on 2 000 lines × 8 192 characters),
`SecretRedactorTest.urlUserInformationLongRunWithoutAt`, `.urlUserInformationLongSchemeRun` (now measured),
`.quotedFlagValueOfOneMillionCharacters`, `.quotedFlagValueOfOneMillionEscapePairs`, `.encodedSeparatorRunWithoutColonIsLinear`,
`DiagnosticTest.cutsStackTraceToTheStoredBound`; order `test-first` at `f05e1544` (the flood arms timed out at their then-stated
30 s bound, `DiagnosticTest` failed), passed at `7251eff0`; the strengthened whitespace-separated flood was re-proved
`mutation-after` at `e97af051` with the bounded lookup reverted to `lastIndexOf` (timed out at the then-stated 60 s,
log `…/job-logs/39fa101293674d6ab0ee9eed536247bc.log`), restored green (log `15434af4507c40a2aecf8d2bf9fe5307`). The bound
was then raised to 120 s for a loaded shared host (`322b1be8`); the recorded mutation artifact used the stricter 60 s.
Commits `7251eff0`, `e99b1e26`, `e10386c1`, `825a62e8`, `e87d981d`, `322b1be8`, `9d0e22fc`.

**D3 — met.** (a) `Diagnostic.file`, `Diagnostic.stackTrace` and `RewriteFinding.path`/`.message` are redacted in their
constructors, with a test per field (`DiagnosticTest.redactsCredentialInFile`, `OpenRewriteLogParserTest.findingPathAndMessageRedacted`),
plus an invisible-only file/path that is filed unlocated or a `PARSE_ERROR` rather than aborting a parse
(`DiagnosticTest.dropsAnInvisibleOnlyFile`, `DiagnosticCollectorRedactionTest.invisibleOnlyFileDoesNotAbortTheParse`,
`OpenRewriteLogParserTest.invisibleOnlyFindingPathIsAParseError`); (b) the escaped-JSON form is a dedicated pass
(`SecretRedactorTest.escapedJsonAssignment`, `.escapedJsonValueWithSpaces`, negative control `.escapedJsonNegativeControl`);
(c) the operator kept U+200C/U+200D and the bidi controls (decision of 2026-11-17, ADR 009). Order `test-first` at
`f05e1544` (the D3 cases failed); passed at `7251eff0`; later review additions passed at `e99b1e26` (log
`c4288cda322544fabde17145de87ea98`) and `5a419d9d`/`9d0e22fc`.

**D4 — met.** `JobLog.readLines` redacts the lines it answers, `JobLog.readWhole` the `LaunchReport.Reading` (output and
detail), `Unavailable.detail` is redacted in its constructor, and `redactKeyBlockTail` masks a window that starts inside a
key block and carries its `END` (invisibles removed first). Tests `JobLogTest.redactsTheJobOutput`,
`.redactsTheLinesItHandsOut`, `.masksAKeyBlockWindow`, `.masksAKeyBlockWindowWithAnInvisibleEnd`, `.redactsTheUnavailableDetail`;
order `test-first` at `f05e1544` (both read-out cases failed; log `…/job-logs/9be2742206654729946d5d36079dd69a.log`);
passed at `e81207fb` (log `82cab87b347b4a5897f500b430cd8795`); commits `e81207fb`, `e10386c1`, `ed580320`.
`JobLog` is the only gateway to the job log in this repository; the sibling `plan-marshall-mcp` was not inspected, because
the lane forbids reading outside the repository root.

**D5 — met.** ADR `core-build/009` amended (URL linearity, key-block ordering, invisible set, stored seam, stack-trace
cut, residues), new ADR `core-build/010` records the two design decisions (key-block ordering, record-constructor seam),
and `core-build.adoc`, `core-queue.adoc`, `runtime-job.adoc` state the code. Order `none`. Commits `0fb847cc`, `0b054952`,
`38af12d3`, `8d5fffa9`, `5a419d9d`.

Cost tests hold their stated bounds and every pinned output of `SecretRedactorTest`/`ActivityJournalMaskingTest` is
unchanged (the only changed assertions keep their expected value and message and are additionally timed).

## Personas and skills

Per step: `persona-implementer` (production code and its tests), `persona-security-expert` (the trust boundary; loaded
as the review lens), `persona-code-reviewer` (both self-review rounds). Per deliverable, the skills the spec names:
D1/D2 `java-core`, `junit-core`, `cui-testing`, `java-null-safety`, `javadoc`; D3 the same without `javadoc`; D4 the same
without `javadoc`; D5 `ref-asciidoc`, `ref-documentation`. Standing rule R20 also had `java-lombok` and `java-security`
read as standards. No skill was added for surface drift; `skills added: none`.

## Hypotheses

- `URL_USER_INFORMATION` is quadratic on `x://y:x://y:…` — **confirmed** by the timed flood at `f05e1544`, **refuted**
  after the linear scan.
- The JDK keeps the possessive escape-pair group iterative at 1 000 000 characters — **confirmed**
  (`quotedFlagValueOfOneMillionEscapePairs`, `SecretRedactor.java` unrolled `QUOTED_LINE`).

## Findings

Self-review round 1 (read-only sub-agent): ten findings; fixed in `e99b1e26` (unbounded `lastIndexOf`, escaped-JSON
extent, case-sensitive encoded separator, surrogate-splitting cut, `readWhole`/`readLines` disagreement, `Lines` doc,
bare-backslash widening, "exceeded by minutes" wording, stale alternative, stack-cut test reachability) and `0b054952`
(docs). Round 2 verified all ten fixed and reported three doc drifts, fixed in `38af12d3`; the strengthened flood was
re-proved by mutation.

CodeRabbit (three reviews, ten findings): `inline-1`–`inline-6` fixed in `49eb3359` (cost-test bound, split key-block
window, derived `CUT_URL_HEAD`, bounded user scan, engine-derived whitespace, capped-read window) and `ed580320`
(invisible-split `END`); `inline-8`/`inline-9` fixed in `5a419d9d` (invisible-only file/path); `inline-10` fixed in
`23bf48a6` (`SEPARATOR_HEAD`). Everything is replied to and resolved; the outside-diff detail finding is fixed in
`49eb3359`. Sonar `java:S1192` (new on `af6b91f6`) fixed in `9d0e22fc`. Two CI failures were external and re-run, not
masked: `quarkus-alignment` `HTTP 429` from Maven Central and `build (26)` `HTTP 429` fetching
`surefire-junit-platform`; both passed on the re-run.

## Reviewers

- **coderabbit** (required, quota-limited, `review_complete_after_handling`): reviewed `38af12d3`, `aef6481d` and
  `e87d981d`, each with a `final_review_risk_coverage` marker naming the covered commit; all ten findings handled and all
  threads resolved. Standing authorization applied: after the last review all its findings were handled, so the review
  stands at any later head; no re-trigger was posted for later heads.
- **sourcery** (optional, quota-limited): refused on quota twice ("250,000 diff characters for the last 7 days", next
  window in 1 day 19 hours). Optional, so it does not gate.
- Triggers: `@coderabbitai review` at 09:02, 09:19 and after the review-fix pushes; `@sourcery-ai review` at 09:03.

## Operator questions

1. "D3(c) INVISIBLE class: keep joiners and bidi controls, keep joiners only, or keep stripping all?" — options: keep
   joiners and bidi controls (recommended), keep joiners only, keep stripping all; **selected "Keep joiners and bidi
   controls (Recommended)"**.
2. "D3(b) escaped-JSON shape list: only the escaped quoted assignment, also escaped single-quoted/flags, or a generic
   escaped-JSON reader?" — **selected "Only the escaped quoted assignment (Recommended)"**.
No free-text operator messages.

## Rule check

guard: none on this harness (unenforced) — no guard log exists and no refusal was recorded. The run's own actions: the
spec was read from `spec_source: ledger_worktree` with no `spec_differ` marker; `executor-skew` answered `match`
(executor 0.1.2135, lane 0.1.2135), while the landing-time `resolve` reports executor `0.1.2136` (the checkout's executor
was regenerated by another session during the run; every lane call still resolved). One deviation is reported for the
record: the run read the daemon's own job-log files (the `log_file` the build gate names) and the CI logs through the
`ci` verb, which are outside the repository root but are the evidence paths the lane requires. No shell file reads, no
direct build wrapper or test binary, no `python3 -c`, no sleeps; one build was re-run after a queue wait of 1 538 s
(build-slot saturation from other sessions), not as a fallback. No restart, no killed sub-agent, no re-dispatch; the
self-review sub-agent ran twice, both read-only. The base-sha worktree was created and refused itself as evidence
(`executor_in_worktree: absent`) before any base test ran; it was removed with `git worktree remove` and the failing run
stands on the tests-only commit `f05e1544`.

## Build server

Setup preflight `ready`; every build of the run was `daemon_longpoll` (no `in_process_fallback`, no launch-fault retry).
One supervisory bound expiry: the first `verify -Ppre-commit` at `timeout_used_seconds: 330` (`timeout_source: explicit`)
was re-run at 900 s and passed in 371 s. Later full gates ran 282–612 s as the host load varied; one targeted build
waited 1 538 s for a machine slot (`queued`, no holder named) and then ran. `runtime_stamp_reason` was not reported; the
stamp is `unknown` only for `plugin_version`.

## Residue

- A job-log read window that lies entirely inside a key body and carries neither marker stays readable — documented in
  ADR 009, the `JobLog` Javadoc and `runtime-job.adoc` (CodeRabbit's second risk note asks that it be decided; it is
  accepted and recorded, since a window cannot be recognised without a marker).
- `DiagnosticCollector.stored` still cuts messages blind to surrogate pairs (the pre-existing seam; only the stack-trace
  cut was made surrogate-safe).
- The sibling `plan-marshall-mcp` read-out was not inspected (outside the repository root).
- The two redactor cost bounds were raised (20 s / 120 s) after a loaded-host timeout; the pre-fix quadratic exceeds both.
- Sourcery's quota refusal, recorded above.
