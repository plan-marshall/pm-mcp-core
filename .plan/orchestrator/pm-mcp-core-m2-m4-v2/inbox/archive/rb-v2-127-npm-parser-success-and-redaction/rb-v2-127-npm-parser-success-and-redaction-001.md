envelope_version=1
sender_type=plan
sender_id=rb-v2-127-npm-parser-success-and-redaction
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-11T02:07:10Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-127-npm-parser-success-and-redaction
epic=pm-mcp-core-m2-m4-v2
pr=#353
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2088
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=14
review_body_findings_handled=14
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced).

Standing rules: r6, R02, R05, R06, R07, R08, R10, R11, R16, R17, R18, R19, R20 read in full at run start.

## Deliverables

**D1 — a passing Jest or TAP run is a success. met.**
- Done-when: outcome cases pass and failed before; negative controls stay indeterminate; `ParsedBuildPortedTest` passes with the three changed fixtures; Javadoc states the marker. All met. Order **test-first**: failing run at `85ff213b`, job log `f1e2240d794e41128b3b9c86eb080ec4.log` (24 tests, 2 failures: `jestPassingRun`, `tapPassingRun`); green at `3129425675764a738b5088cf575f664f` (24) and `4f5ae2d4071b4b9ea9bdf912090a7d0f` (20). Commit `dac01597`, extended in `af96ae51` (failed suite) and `af96ae51`/`b48965ec` (bailout + builder merge).
- Tests: `BuildOutputParserTest.jestPassingRun`, `.tapPassingRun`, `.jestFailedSuite`, `.tapBailout`, `.jestUnmeasuredSummary`, `.tapZeroCount`, `.jestAllSkipped`, `.zeroCountIsNoMarker`; fixtures `deliberate-jest-two-test-blocks`, `deliberate-tap-two-blocks`, `npm-tap-success` → `success-marker true`; three negative controls stayed false.

**D2 — the npm tools that print no passing summary. met (operator answer: spec default).**
- Done-when: operator's answer in the ADR; zero-exit install log ends `SUCCEEDED`; clean `tsc`/ESLint stay `INDETERMINATE`; guide sentences rewritten. All met. Order **test-first**: failing run at `dac01597`, logs `a31d45f310ed4b2b8b805aad4515d9e8.log` (NpmErrorParser 10/1) and `b360b4e46b1e4052b4c11b447374182a.log` (BuildOutputParserTest 26/1); green at `ddfc25a5c9cd4401bf88793dfde20003` (26) and `e7452d9b47104caa97a4a0cd0091360f` (30). Commit `9526c3ef`; ADR `runtime-job/016`; ADR 015 marked superseded in part.
- Tests: `NpmErrorParserTest.installCompletionLine`, `.installLineBesideAnError`, `.noCompletionLine`, `.installSummaryRejectsProse`; `BuildOutputParserTest.unrecognisedLog` (deliberately changed from NOTHING to success), `.opaqueLog`, `.cleanSyntaxToolLogs`.

**D3 — read the prefix current npm prints. met (verify-first clause: hypothesis confirmed).**
- Done-when: the recorded log and the two cases exist and pass. Met: npm 9.2.0 prints `npm ERR!`, npm 12.2.2 (latest) prints `npm error` for the same E404 probe; a deliberate fixture records each prefix. Order **test-first** (the Standing Constraint): failing run at `9526c3ef`, logs `293c46ebda784370842b8032b9f354bd.log` (12/1) and `9164b9137a664bc894c4030feaf9444c.log` (27/1); green at `22be4ee123ac449486c6db399298521f` (27) and `899f52670ec7412db54e2c713c6dc25c` (32). Commit `48182216`, Javadoc fix `f36bba04`.

**D4 — the unparsed excerpt is redacted on what a person reads. met.**
- Done-when: three cases pass and failed before; `redactsASecret` and bounds tests unchanged; ADR `core-build/004` and the guide name the stripped form. Met. Order **test-first**: failing run at `48182216`, log `0a17694dba3f4922a22c9f4da1c4c05c.log` (11 tests, 3 failures); green at `f9907b573edb486d94b2f22872bc46e9` (11). Commit `fc58eb10`.
- Tests: `UnparsedEvidenceTest.redactsACredentialSplitByAnEscape`, `.stripsEscapesBeforeTheByteBound`, `.reducesASpinner` (now also with a trailing lone `\r`).

**D5 — the redactor knows the shapes an npm log carries. met (operator answer: all four).**
- Done-when: each of (a)–(d) fails before and passes; each negative control and idempotence pass; the class Javadoc lists the shapes. Met. Order **test-first**: failing run at `fc58eb10`, log `b4b019b3787845d7b9b062624e492b59.log` (15 tests, 4 failures); green at `fc5dff8b14154cb8ad76cfd7be0c79bf` (19 incl. journal masking). Commits `0c2935a3`, `ace124e0`, `dc4487da`, `c6045806`, `bb5b185c`, `f7d9c973`, `9d2f074e`.
- Tests: `SecretRedactorTest.npmAccessToken`, `.urlUserInformation`, `.spacedCredentialFlags`, `.pemPrivateKeyBlock`, `.unclosedPemPrivateKeyBlock`, `.shapeNegativeControls`, `.idempotent`; `ActivityJournalMaskingTest` stays green. The review cycle hardened quoted values (spaces, escaped quotes, later arguments), unclosed quotes with the opposite mark, short values and commas; the one Sonar S5998 on the disjoint alternation is suppressed inline with its reason.

**D6 — the npm error diagnostic is redacted at its source. met, with a layer-1 observation.**
- Done-when: the case passes and failed before; `ERESOLVE`/`E404` messages unchanged; the guide says where the diagnostics are redacted. Met. Order **test-first**: failing run at `0c2935a3`, log `36439e6ef7db475faabc88a0f11e4e55.log` (13/1); green at `fe57137315b9446aa6e2701d348ae596` (43). Commit `50a6cdca`.
- Observation: the spec's motivating line (`request to https://… failed`) cannot reach `Diagnostic.message`, because `NpmErrorParser.firstLine` returns the block's code line; the reachable vector is a secret in the code token. The redaction is applied to the composed message, which covers either part; ADR `core-build/006` states the correction.

Per-head test runs: every pushed head was tested; the final gate at `9d2f074e` ran `verify -Ppre-commit` = 10003 tests green (`85e1f21021c44bcb9e4e48348b6e0aa1.log`), and CI ran build + integration tests green (`42936796569`, `42936796653`).

## Personas and skills

- production code: `persona-implementer`; unit/module tests: `persona-module-tester`; trust boundaries and secrets (D4–D6): `persona-security-expert`; documentation: `persona-documenter`; every self-review round and PR triage: `persona-code-reviewer`.
- Per deliverable the spec names no domain skill: `skills: none named` (R20's java-core, java-lombok, java-null-safety, javadoc, junit-core and cui-testing were read as standards at run start).

## Hypotheses

- D3 "newer npm prints `npm error `" — **confirmed**: npm 12.2.2 (latest) prints `npm error code E404` for the recorded probe, npm 9.2.0 prints `npm ERR!`.
- npm access token = `npm_` + 36 alphanumeric characters — **confirmed** against the npm Registry API documentation and recorded in the test/fixture comment.
- install completion line forms — **confirmed** for `added N packages in …`, `removed N packages …`, `changed N packages …` and `up to date, audited N packages` from the recorded npm 9/12 logs; the pattern now requires the printed count (a prose line is no marker).

## Findings

- Self-review round 1 (sub-agent, `persona-code-reviewer`): 16 findings; fixed the R20 assertion messages, ADR wording/reachability, realistic prefix fixtures, the shared install-summary constant, the `unrecognisedLog` change, idempotence coverage, and the "positive banner" wording; refuted the no-artifact, coverage-timeout and operator-answer claims (artifacts and question-tool answers recorded).
- Self-review round 2: no blocker/major; all six Done-whens met; nits fixed (E404 Javadoc, npm 12 adapter log, trailing space, PEM idempotence).
- PR review (CodeRabbit, 14 body findings + inline threads): `inline-1` — 8fcf6cc5; `inline-2` — 8fcf6cc5; `inline-3` — a332e39e; `inline-4` — f36bba04; `inline-5` — ace124e0; `inline-6` — dc4487da; `inline-7` — dc4487da; `inline-8` — c6045806; `inline-9` — c6045806; `inline-10` — af96ae51; `inline-11` — af96ae51; `inline-12` — af96ae51; `inline-13` — af96ae51; `inline-14` — 9d2f074e. All threads resolved with a reply naming the commit.
- SonarCloud: one java:S6126 in `SecretRedactorTest` fixed in 8fcf6cc5; one S5998 on the quoted-value repetition fixed by the last-quote form in bb5b185c; a second S5998 on the reintroduced disjoint alternation suppressed inline in f7d9c973 with its reason. Final read at `9d2f074e`: 0 open, 0 hotspots, gate OK.

## Reviewers

- `coderabbit` (required, quota_limited): reviewed every head it could (`d01f9604`, `8fcf6cc5`, `a332e39e`, `1d41b5cd`, `e0e63fad`, `af96ae51`, `9d2f074e`); several later pushes were skipped by the bot and were re-triggered with its `trigger_comment`. Standing review authorization: a qualifying review exists on a PR head, all 14 findings handled, one re-trigger after the last push recorded, and every other gate on the current head green.
- `sourcery` (optional): refused on its review budget (250k diff characters / 7 days) at 09:04Z and 09:05Z; optional, not gating.

## Operator questions

1. "Deliverable 2 (R07): which policy for npm tools that print no passing summary?" — options: spec default / verb-carried decision. Selected: **Spec default**.
2. "Deliverable 5 (R07): which secret shapes should `SecretRedactor` learn?" — options: all four (a)–(d) / only (a)–(c) / none. Selected: **All four (a)–(d)**.
3. "Deliverable 6 (R07): where should diagnostic messages be redacted?" — options: npm parser only / every parser at `stored()`. Selected: **npm parser only**.

Free-text operator messages: none.

## Rule check

- guard: none on this harness (unenforced). The guard log was therefore not read as evidence; the run's own actions are the count below.
- The lane text was re-read in full at session start and after the server restart (2026-11-10T14:52Z); the run resumed at the first step not recorded as done.
- One server restart cancelled an in-flight integration-tests build; the job was re-attached and returned green (39 ITs). No killed sub-agent and no re-dispatch occurred.
- R17's links check was invoked exactly as the standing rule prescribes (absolute `trace.py` path); its first run reported two broken ADR links, both fixed, then `BROKEN LINKS 0`.
- One spec premise is wrong and was reported, not followed: D6's example line cannot reach `Diagnostic.message` (see D6). The redaction was applied to the composed message.
- No shell file reads/writes outside the permitted `.plan/temp/` and CI body slots; no `gh`/`glab`, no direct build or test binary, no inline Python.
- The spec read came from the ledger worktree (`spec_source: ledger_worktree`), no `spec_differ`.
- `executor-skew`: `match` (executor 0.1.2088, lane 0.1.2088).

## Build server

- Setup preflight: `ready` (registered).
- Mechanism: every build was `daemon_longpoll`; no `in_process_fallback`, no degraded result, no launch fault.
- One bound expiry: `verify -Pcoverage` at the first head hit `timeout_used_seconds 936` (`timeout_source: explicit`) after 839 s queued; the split per-module form was used (pm-core 4560, pm-runtime 6761) and recorded.
- One non-mine failure: `pm-runtime` `LspClientTest.stubborn` failed on one gate run at the merge head (initialize TIMEOUT under load, log `2e2f3c591f034970942894cb8ba7a528.log`) and passed on re-run; unrelated to this plan's code.
- No `killed` job.

## Residue

- The spec's D6 motivating example is unreachable (recorded above); a spec correction belongs to the next orchestrator reconcile.
- URL shape: a single-user URL (`scheme://<token>@host`, no colon) stays readable — the spec's shape is `user:secret@host`; a password containing a literal `@` is masked (the password group backtracks to the last `@` of the authority).
- npm 12's "requested resource" E404 wording does not name the package (the diagnostic still files).
- A PEM block whose `BEGIN` line lies outside the text remains carried (the Carried Open Items), while a block that starts inside the text masks to its end.
- Other parsers' diagnostic messages are not redacted at `DiagnosticCollector.stored` (carried by the spec; operator chose npm only).
- The review bot's ast-grep ReDoS warnings on the composed regex constants are false positives: only static final literal strings reach `Pattern.compile`.
- `pm-core/doc/developer/core-queue.adoc` (R06, changed package) and ADR `core-build/001` wording are touched beyond the declared deliverables; `doc/Traceability.adoc` carries the updated PM-SEC-1 verification lines (R08). Recorded as surface delta, as R19 expects.
