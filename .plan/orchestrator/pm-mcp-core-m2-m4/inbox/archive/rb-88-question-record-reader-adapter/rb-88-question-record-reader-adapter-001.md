envelope_version=1
sender_type=plan
sender_id=rb-88-question-record-reader-adapter
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-20T13:23:38Z

```landing-facts
schema=landing-facts/1
plan_id=rb-88-question-record-reader-adapter
epic=pm-mcp-core-m2-m4
pr=#116
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
```

guard: none on this harness (unenforced)

## Deliverables

**D1 — the adapter (met).** `QuestionLogRecordReader` implements the port over `QuestionLog`, resolves the scope of the
`ReadRequest`, and returns `Open` / `Resolved(outcome)` / `Unreadable`; the whole log is judged. Test:
`QuestionLogRecordReaderTest` (23 cases), order `test-first` — written first and run at the branch point, failing with
`Symbol: Klasse QuestionLogRecordReader` (`maven-2026-10-20-054401.log`), green in `maven-2026-10-20-114242.log`; commit
`dea886b`.

**D2 — the read is a read (met).** Lock-free, writes nothing, confined to the runtime base; a refused path is a value.
Test: `QuestionLogRecordReaderReadTest` (5 cases), order `mutation-after` — with the confinement block removed the
runtime test failed 5 of 9 cases (`maven-2026-10-20-082644.log`: `expected: not equal but was: <Open[]>`; the symlink
and missing-root cases), restored and green; commit `ab5a0e4`, hardened in `b3c04ca`.

**D3 — tests (met).** `QuestionFixtures` and `QuestionLogRecordReaderBoundaryTest` (9 cases); every line-oriented
fixture is written with LF and CRLF; the closing-outcome set is derived from `QuestionEvent` at run time. Order `none`
(D3 adds no behaviour; its cases pass with the behaviour of D1/D2); commit `cfe549c`.

**D4 — documents (met).** `package-info.java`, the developer guide `runtime-question.adoc`, ADRs 001–004, the module
README, the repository ADR index, the PM-ARCH-4 `Implementation:`/`Verified by:`/`Documentation:` lines, and the
PM-IMPL-7 gap reworded to the runtime/CDI wiring. No covered specification passage was found, so none was deleted; the
port Javadoc needed no correction. Order `none`; commits `1a63709`, `d0078e5`, `b3c04ca`, `25213c9`.

**T — the lane test proof (met).** T1: the table below. T2: accepted/refused/boundary per behaviour; three or more
variants in one `@ParameterizedTest`; CRLF twins; enum-derived completeness; named expectations; no Mockito/Hamcrest; a
fixed injected clock; no sleeps. T3: no pre-existing assertion removed or relaxed (all test files are new). T4: the four
gates on the merged head, listed below. T5: two self-review rounds, tests graded. T6: this section and the PR body
carry the counts, the coverage figures and the T1 table.

| Deliverable | Test | Order | Failing run | Passing run | Commit |
|---|---|---|---|---|---|
| D1 | `QuestionLogRecordReaderTest` | test-first | `maven-2026-10-20-054401.log` (testCompile at the branch point) | `maven-2026-10-20-114242.log` | `dea886b` |
| D2 | `QuestionLogRecordReaderReadTest` | mutation-after | `maven-2026-10-20-082644.log` (confinement removed) | `maven-2026-10-20-114242.log` | `ab5a0e4` |
| D3 | `QuestionLogRecordReaderBoundaryTest` | none | — | `maven-2026-10-20-114242.log` | `cfe549c` |
| D4 | documents | none | — | — | `1a63709`..`25213c9` |
| T | the four gates | none | `maven-2026-10-20-102320.log` (environmental) | see below | `c950f77` |

Test-compile ran on every pushed head: `b3c04ca` (pre-commit `maven-2026-10-20-100935.log` and CI), `25213c9`
(CI build; docs-only commit), `c950f77` (post-merge pre-commit `maven-2026-10-20-122529.log` and CI).

Gates on the merged head `c950f77`: `verify -Ppre-commit` green, 6639 tests (`maven-2026-10-20-122529.log`); `verify`
green, 6639 (`maven-2026-10-20-123248.log`); `verify -Pcoverage` green, all checks met, pm-runtime 91.97 % instruction,
92.25 % line, 89.03 % branch against the 80 % threshold (`maven-2026-10-20-123736.log`); `verify -Pintegration-tests
-pl pm-runtime -am` green, 27 tests (`maven-2026-10-20-125658.log`); CI green on `c950f77` (30 checks).

Test counts: `pm-runtime` 1453 at the branch point → 1522 at the merged head (69 new invocations: 23 + 5 + 9 = 37
methods, the rest the parameterized expansions); `pm-core` untouched, 3105 both.

## Personas and skills

D1 production code: `persona-implementer`. D2/D3 tests: `persona-module-tester`. D4 documents: `persona-documenter`.
Every self-review round: `persona-code-reviewer`. The spec names no domain skills: `skills: none named`; no surface
drift.

## Hypotheses

- `HYPOTHESIS: QuestionLog` offers a read by scope and question id without a lock — **confirmed in substance, refuted as
  one call**: `QuestionLog.read()` is the lock-free read (`pm-core/.../QuestionLog.java:229`) and
  `Lines.current(questionId)` selects the current line (`QuestionLog.java:136`); the adapter composes them.
- `HYPOTHESIS:` PLAN-73 does not already plan this adapter — **confirmed**: `doc/plans/question-continuation.adoc:8`
  names `runtime.worker` and `runtime.launch` only, and the file holds no adapter bullet.
- `HYPOTHESIS:` the package `de.planmarshall.runtime.question` holds no question code — **confirmed**: no file of that
  package existed at the branch point; the operator selected the new package.

## Findings

Self-review round 1 (all fixed in `d0078e5`): F1 confinement root and wiring — the public constructor now builds the
confinement over the runtime base, a package-private seam serves the refusal tests; F2 CRLF twins — fixed; F3 D2 was
never seen failing as an assertion — proved by the mutation-after run; F4 engine-level path — out of scope, `pm-workflow`
may not depend on `pm-runtime`, owed outside; F5 ADR 002 wording — fixed; F6 untested branches — constructor/null,
missing base, multiple refused lines, torn at byte 0, refused-before-torn, invalid project id; F7 ADR index — fixed;
F8 package ADR — ADR 004 added; F9 closing outcomes — one parameterized test; F10 progress arithmetic — corrected;
F11 trace matrix — corrected; F12 preferences — agreement expectation named per question, redundant assertion removed,
reason strings kept; F13 landing report — written here. Observation: a `question` state of a pending entry cannot be
addressed through this port (no entry addressing) — outside this plan, listed as residue.

Self-review round 2 (all fixed in `b3c04ca`): F2r three remaining LF-only fixtures — parameterized; N1 implicit
`LockKey` validation — named `requireProjectId`; N2 developer-guide inventory — updated; N3 ADR 003 — the rejected
repository-root wiring is now an alternative. No new defect.

CodeRabbit (formal review at `b3c04ca`): one nitpick, the PM-IMPL-7 passage still naming the adapter as unimplemented —
fixed in `25213c9` and answered on the PR. Its re-review at `25213c9` had no actionable comments and carried forward to
`c950f77`.

SonarCloud: quality gate passed, 100 % coverage on new code, 0 duplication; the summary reports 4 new issues
(informational; not a required reviewer, not triaged here).

Sourcery (optional): refused on quota (2,500,000 diff characters); no gating.

## Reviewers

| Reviewer | State | Head reviewed | History |
|---|---|---|---|
| cuioss-review-bot | reviewed, no issues | `c950f77` | triggered 11:53Z, 12:14Z, 12:59Z; "Review updated until commit c950f77" at 13:01Z |
| coderabbit | reviewed, no actionable comments | `c950f77` (carried forward; review at `b3c04ca`, re-review at `25213c9`) | triggered 11:53Z, 12:13Z, 13:00Z; its one nitpick fixed before the final head |
| sourcery (optional) | refused (quota) | — | triggered 11:54Z; refusal notice; no gating |

## Operator questions

1. (Interrupted by a server restart, no answer; re-asked as 2 and 3) "May `../plan-marshall-documentation` be read
   read-only?" / "Which package for the adapter?".
2. Sibling checkout: options "No — stay inside this repository (Recommended)", "Yes — read-only access allowed" →
   **"Yes — read-only access allowed"**.
3. Adapter package: options "New `de.planmarshall.runtime.question` (Recommended)", "Existing
   `de.planmarshall.runtime.workspace`" → **"New de.planmarshall.runtime.question (Recommended)"**.
4. Build command names: the resolve names (`quality-gate`, `module-tests`, `verify`, `test-compile`) are rejected by the
   executor; options "Use CLAUDE.md's literal commands (Recommended)", "Stop and report the blocker" → **"Use CLAUDE.md's
   literal commands (Recommended)"**.
5. The full `verify -Ppre-commit` was SIGKILLed: options "Retry the full pre-commit once (Recommended)", "Scope
   pre-commit to pm-runtime", "Stop and report blocked" → **"Retry the full pre-commit once (Recommended)"**.
6. Environment-dependent failures in untouched packages: options "Free/settle the machine, then retry (Recommended)",
   "Retry now", "Stop and report blocked" → the operator answered in free text ("Where are you in the process? What
   about letting it run on the CI?") — **no option selected**; the question was asked again as 7.
7. Build gate: options "Push, open PR, let CI run, retry local verify before merge (Recommended)", "Retry local verify
   now", "Use CI in place of the local verify" (rule bend, not recommended), "Stop and report blocked" → **"Use CI in
   place of the local verify"**.
8. Docs-only classifier: the installed `runbook_lane` accepts only `resolve`, so `build-classify` is unknown; options
   "Run the full gates for the doc fix (Recommended)", "Treat the doc fix as docs-only", "Stop and report blocked" →
   **"Treat the doc fix as docs-only"**.

Free-text operator message, separate from the question options: "WHere are you in the process? WHat about letting it
run on the CI?" (answered with the status and a re-asked question; no action was taken on the free text alone).

## Rule check

guard: none on this harness (unenforced).

Rules kept with an operator decision, each recorded above: the build command names of the resolve result were unusable
(the executor passes them literally), and the operator selected the literal `CLAUDE.md` commands; the local `verify`
substitution for CI was selected at question 7, but the final local `verify` on the merged head `c950f77` is green, so
no merge relied on the substitution; the one-line Traceability fix was gated as docs-only under the operator's answer to
question 8, and the full gates were then run on the merged head anyway. The interrupted question 1 was re-asked and no
option was assumed. No guard refusal exists to report. One build was SIGKILLed by the environment and retried once
under the operator's answer to question 5; the environment-dependent `LspClientTest` and
`HuggingFaceSourceRepositoryTest` failures occurred in packages this plan does not touch and the final runs are green.

## Residue

- Owed outside this repository: the CDI producer of the adapter in `pm-mcp-server` (`plan-marshall-mcp`), and the
  engine-level `question` state path (a `pm-workflow` test cannot depend on `pm-runtime`).
- Owed to the milestone exit: the concept and user documentation of the slice and the roadmap check-off in
  `plan-marshall-documentation`.
- Observation for the epic: the port carries no pending-entry addressing, so a `question` state of a pending entry (an
  entity of the workspace scope) cannot be resolved through this adapter; out of this plan's scope.
- Sourcery (optional) refused its review on quota; SonarCloud reports 4 new issues with the quality gate passed
  (informational, not triaged).
- No covered specification passage was found for the adapter, so none was deleted.
- The installed `runbook_lane` script has no `build-classify` verb although the skill documents it (stale executor);
  reported to the operator, not changed (outside this repository).
