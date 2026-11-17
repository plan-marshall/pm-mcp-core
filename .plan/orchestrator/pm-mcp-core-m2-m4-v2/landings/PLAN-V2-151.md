# Landing Analysis: PLAN-V2-151 — Secret redactor hardening 2

epic: pm-mcp-core-m2-m4-v2
workstream: WS-07
pr: #475 (merged `f2700e2f`, parent `a8c2a266`; 16 files, +868/-135)

> Landing record for one merged plan, **delivered as specified, with one test-adequacy hole in the flood tests and three behaviours of the job-log
> read-out that the lane did not weigh**. Written by `analyze` (inbox scan) from the lane's landing message
> `rb-v2-151-secret-redactor-hardening-2-001.md` (`deliverables_done` 5 of 5, `merge_state=merged`, `cleanup_owed=false`). One read-only leaf
> (`execution-context-level-3`, `orchestrator.analyze`) read `git diff a8c2a266 f2700e2f` through `git show`; by reading, no build, no test run.
> The orchestrator re-read the flood shape at `f05e1544` and at the merge, `redactKeyBlockTail` (`SecretRedactor.java:580-601`), the `JobLog`
> read-out path (`JobLog.java:150-192`, `:222-256`) and the `@Nullable` return (`SecretRedactor.java:387`).

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 key block masked before the label passes | met | `redactPrivateKeyBlocks` runs first (`SecretRedactor.java:524`); `labelledPrivateKeyBlock`, `quotedLabelledPrivateKeyBlock`, `proseBeginMarkerWithoutEndStillMasks` assert exact output; the PEM scan stays one linear pass |
| D2 URL scan linear, stored seam, stack cut | met in code, **partly in test** | the regex is a find loop with per-token caches (`:361-480`), traced equivalent by the leaf; `Diagnostic.stackTrace` cut and surrogate-safe (`DiagnosticCollector.java:108-117`); the flood tests are weaker than the spec's shape (L2-1) |
| D3 (a) constructors redact, (b) escaped JSON, (c) `INVISIBLE` class | met; (c) has **no test** that U+200C, U+200D and the bidi controls are kept | `Diagnostic`, `RewriteFinding` constructors; `SecretRedactor.java:89,301,542`; the grep finds no pin |
| D4 job-log read-out redacted | met | `JobLog.java:153,188,190,225,238-255`; `JobLog` is the only reader of the log in this repository |
| D5 ADR 009 amended, ADR 010 new, three guides | met | ADR numbering correct; the guides agree with the code |
| Expected Surface | clean | all 16 files inside it |

## Three-Layer Analysis

### Layer 2 — Implemented

| Id | Point | Verdict | Severity |
|----|-------|---------|----------|
| L2-1 | the flood that failed at `f05e1544` was `"x://y:"` repeated (no whitespace); the merged `hostileFlood()` is `"x://y: "` repeated (`DiagnosticCollectorRedactionTest.java:176-185`, re-read). The old pattern stops at a space, so the old code is linear on the merged flood: **both stored-seam and constructor tests would pass against the pre-fix pattern**. The only no-whitespace plain `://` guard left is one 8 192-character line under a 20 s bound (`urlUserInformationLongRunWithoutAt`), which a quadratic of about 11 M steps does not exceed | confirmed (shape re-read; the step estimate is the leaf's) | major (test adequacy) |
| L2-2 | both flood tests are timing-only: their content assertions check the test's own input (`:195-196`, `:208`), not the redacted output | confirmed | minor |
| L2-3 | `REDACTION_BOUND` loosened 5 s to 20 s, which also affects the PEM tests; whether the 400 000-character `BEGIN` case still discriminates is the leaf's estimate | leaf reading | minor |
| L2-4 | the claim "only changed assertions keep their expected value" holds; `quotedFlagValueOfOneMillionCharacters` swapped `assertDoesNotThrow` for a timeout | confirmed | info |
| L2-5 | no test for `Unreported.detail`/`.jobOutput` redaction, a capped read with a key block, `readLines` at offset > 0, or a lone END in an uncapped read | leaf reading | minor |

### Layer 3 — Correct

| Id | Point | Verdict | Severity |
|----|-------|---------|----------|
| L3-1 | **`redactKeyBlockTail` has no window context**: with no `BEGIN` before the first `END` it masks everything before that `END` (`SecretRedactor.java:590-601`, re-read). `JobLog.redacted` applies it to every text, including `readWhole` and `readLines(0)`, which start at the file start (`JobLog.java:188-190`, `:222-256`, re-read). A build output that merely prints `-----END PRIVATE KEY-----` (a quoted test source, a diff line) loses all output before it, and that output is what the `OutputParser` reads (`SubprocessJobRunner:1573`, leaf) | confirmed (chain re-read, the parser consequence is the leaf's) | major (evidence and parser-input loss, no leak) |
| L3-2 | an unclosed `BEGIN`, even in prose, masks to the end of the text, and `[redacted]` carries no newline: the rest of the output and its line structure are gone before the `OutputParser` and `WorkerActivityPump` see it | leaf reading | major |
| L3-3 | `readLines` is stateless per window: polled mid-block, a window entirely inside the body stays readable; the lane and the docs name the residue, but it is likelier on the incremental polled path than the docs say | leaf reading | major (accepted residue, understated) |
| L3-4 | the escaped-JSON pass masks the rest of the line from the escaped opening quote, which breaks harness stream-JSON lines (counted unmapped by `ActivityEventMapper`); doubly escaped JSON matches nothing and leaks | leaf reading | minor |
| L3-5 | the kept joiners and bidi controls let a split label or `PRIVATE\u200EKEY` marker bypass the redactor; accepted by the operator (2026-11-17, ADR 009), no test pins it | leaf reading | minor |
| L3-6 | `urlUserInformationMatch` returns `@Nullable UrlMatch` (`SecretRedactor.java:387`, re-read); the project rule is `Optional` returns only | confirmed (private method) | minor |
| L3-7 | `DiagnosticCollector.truncate` (`:83`, `:119`) cuts messages blind to surrogate pairs, 25 lines from the surrogate-safe `storedTrace`; the lane's residue, real | leaf reading | minor |
| L3-9, L3-10 | a cut inside `[redacted]` re-expands on a second constructor pass; `OpenRewriteLogParser` redacts the path twice and leaves `recipe` unredacted | leaf reading | info |
| — | pre-existing, out of scope: `AUTH_HEADER` (`:108`) cannot read a JSON-quoted `"Authorization":"Basic …"` | leaf reading | info |
| — | no generic catch, no new `LogRecord`, no new dependency, ADR numbering right | clean | — |

## Review and Quality Numbers

| Number | Message | Re-read |
|--------|---------|---------|
| unresolved review threads | 0 | not re-queried |
| review body findings total / handled | 10 / 10 | the lane's count; CodeRabbit reviewed three heads, the later heads stand under the standing exemption |
| Sonar open new issues | 0 | gate `ok` by the lane; not re-queried |

`landing-check`: `complete=false`, missing `total_tokens` and `plugin_version` (E27). Surface delta unmeasured. The two CI failures the lane reports were
external (`HTTP 429`) and re-run.

## Residue

1. **Carried into PLAN-V2-114, item 44** (the test points, the `Optional` return, the surrogate cut, the bound, the three read-out behaviours with
   the design choice left to the outline): L2-1, L2-2, L2-3, L2-5, L3-1 to L3-7, L3-9, L3-10.
2. **Report to the operator (spec D4):** the sibling `plan-marshall-mcp` job-log read-out was not inspected by the lane (outside its repository root);
   `JobRecordStore.logPath` is public, so an assembly can read the file without `JobLog` (info).
3. **Accepted by the lane and recorded in ADR 009:** the window inside a key body without markers (L3-3's documentation side).
