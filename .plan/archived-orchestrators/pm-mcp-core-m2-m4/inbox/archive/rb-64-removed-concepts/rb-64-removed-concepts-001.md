envelope_version=1
sender_type=plan
sender_id=rb-64-removed-concepts
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-21T12:29:46Z

# PLAN-64 removed-concepts — landing

```landing-facts
schema=landing-facts/1
plan_id=rb-64-removed-concepts
epic=pm-mcp-core-m2-m4
pr=#122
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=7
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
```

guard: none on this harness (unenforced).

## 1. Deliverables

| # | Verdict | Evidence (test · order · failing run) | Commit |
|---|---|---|---|
| D1 scope session log | met | `PendingEntryCodecTest.membersWithoutSession`/`.withoutSession`/`.sessionMemberIsRefused` · `test-first` · 191229 | 65d386c |
| D2 session generation record | met | classpath guard at the branch point (9/9 failing, 191737; not committed — the Done-when forbids tests that name the concept) · `test-first` | e65e343 |
| D3 fenced-generation sweep step | met | `PeriodicSweepTest.shouldRegisterOnlyResidueSteps` · `test-first` · 191757 at origin/main | a554bba |
| D4 audit category identity | met (operator decision: `Scope.ENTRY` added here) | `AuditSchemaParityTest.enums` (192702) + `AuditCodecTest.removedIdentityCategory` (192738) · `test-first` | ad4b3ae |
| D5 operator command of a refusal | met | `JobRefusalTest.componentsAreReasonDetailAndChangedPaths`, `.everyReasonCarriesItsReasonAndDetails`, `VerifyNotResolvePipelineTest.tampered` · `test-first` · 193718 | a953076 |
| D6 directive `expire_question` | met | `ScenarioScriptTest.shouldRefuseUnknownDirective` (4 names) · `test-first` · 194929 | 7dd29f7 |
| D7 documents | **not fully met** | guides, ADRs (001/003/008/009 superseded; 002 kept Accepted), traceability, package row/file, spec sentence done; the roadmap `_Remaining_` update in `plan-marshall-documentation` is owed to the milestone exit and is not countable · `none` | 3f481b4 (+ later) |
| D8 stalled-scope detection | met at the seam | `StalledScopeDetectorTest` (21 → 36 cases: threshold 599/600/601, waits, pause, holds, external wait, episode, unobserved progress, release report, race, refused record, refused replacement, retry, immutability, missing values) · `test-first` against a skeleton · 200756 | c5c78d2, e477787, a09a31e, b91ffed, bee22a1, 7f7a221 |

`test-compile` ran on every pushed head: 191859, 192918, 193950, 195316, 201417, 204744, 221802, 223233, 052020, 053242,
054316, 061628, 072618, 080537, 093037, 104326, 115722.

Done-when clause "no class, test or document names … `expire_question`" is reported **not met as written**: the lane's
matrix requires the refusal test that names it, and the superseded ADRs keep their historical text.

## 2. Personas and skills

Production code: `persona-implementer`; tests: `persona-module-tester`; self-review: `persona-code-reviewer`;
documentation: `persona-documenter`. The spec names no `Skills:`/`Persona:` lines — `skills: none named`; no surface
drift was found (every changed file is inside the spec's Expected Surface or is a link repair the standing constraint
requires, named in the PR body).

## 3. Hypotheses

- Expected Surface inferred from the package's `Where` line — confirmed against the code; `StalledScopeDetector` and
  its test are new in `runtime.start`, `GenerationId` is removed (no reuse: a worker generation is its job id).
- The package adds no log record — confirmed (the one removed record belongs to D3).
- The package makes no HTTP call — confirmed.
- `WorkflowStatus` gives the detection all it needs — confirmed (`updatedAt`, `activeJob`, `activeJobs`, `activeTasks`,
  `pendingCondition`); pause/operator-wait/hold/external-wait come from the caller's `View`.

## 4. Findings

Self-review round 1 (16 findings): release report name, concurrent map + append ordering, unused `projectId`, sweep test
identity, vacuous minimality test, surplus fixture, READMEs, ADR 004, spec sentence, attribution, `OperatorPort.none()`,
stale core.queue references — fixed; D4 closed by the operator's answer. Round 2: the refusal detail named an operator
command (fixed), frozen idle span, release/insert race, runtime-job guide, runtime-start trace, external waits in the
view, ADR 002 tense, CRLF twin — fixed. Round 3: unobserved progress kept an episode open (fixed), the tampered detail
unasserted (fixed), docs wording (fixed); the round limit was reached, hence `self-review: budget-exhausted` — every
round-3 item was fixed afterwards, and CodeRabbit's later reviews were clean.

CodeRabbit (required): sweep-registration wording, refusal Javadoc, release/open independence, pending open retry,
serialization, release queue after append, ADR 014 trace — each fixed and answered, every thread resolved. Sourcery
(optional): quota refusal. SonarCloud: quality gate passed.

## 5. Reviewers

- coderabbit: reviewed (heads b91ffed, a562098, b402947, 7f7a221, bee22a1, fc2125c, fea86ae, da892e9); final review on
  da892e9 with no actionable comments; trigger `@coderabbitai review` after each push and `@coderabbitai full review`
  for the final heads; all threads resolved.
- sourcery: refused (review budget of 2,500,000 diff characters for 7 days, retry in 2 days 10 hours); optional, no gate.

## 6. Operator questions

1. "May I read ../plan-marshall-documentation read-only?" — options yes-read-only / no-stay-inside; selected "Yes,
   read-only (Recommended)".
2. "The D4 contradiction: re-enable `AuditSchemaParityTest.enums` (needs `Scope.ENTRY`) or leave it disabled?" —
   options re-enable / follow the package bullet; selected "Re-enable the parity test (Recommended)".
Free text: after the merge of #122 the worktree held one uncommitted build-rewrite of
`ServiceUnitInstallerTest`; the operator wrote "Land it with a small merge using skip-bot-review as pr label and
continue". That was taken as an instruction for a separate follow-up, not as permission to merge or skip a reviewer on
#122: PR #127 carried the rewrite with the `skip-bot-review` label, was verified and merged (c7780a0).

## 7. Rule check

- Shell rule: `sed -i` was used twice to apply repetitive edits (`PeriodicSweepTest.java`,
  `StalledScopeDetectorTest.java`), and one early exploration call chained commands with `;`. Both are section 1 Shell
  violations; no other shell violations.
- Guard: `guard: none` on this harness — nothing was enforced; `{guard_log}` was not read because no guard exists.
- Every other rule was kept: executor-only scripts and builds, one command per call otherwise, file tools for reading and
  searching, temp files only under `.plan/temp/`, edits only in the worktree, the package file/index from the standing
  constraint, the base branch merged (never rebased), and the operator's selections followed.

## 8. Residue

- Owed to the milestone exit: the roadmap `_Remaining_` parts in `plan-marshall-documentation` (D7) and the concept and
  user documentation of the slice.
- Follow-ups named in the PR (not this repository): `pm-mcp-server` registers `PendingEntryRecordsSweep` and wires
  `StalledScopeDetector` with an `OperatorPort`; `process-and-overview` serves the `needs_operator` summary; the removed
  public API of `JobRefusal`.
- `docker`/trace link check `trace.py` of the sibling checkout was not run (the operator's permission was read-only, no
  command in that checkout).
- PM-WATCH-RUN-2 and PM-WATCH-RUN-8 stay as gaps (they also require the clients, `pm-operator status`, the epic view and
  metrics).
- ADR `core-queue/002` stays Accepted (its API decision still holds); ADRs 001/003/008/009 are superseded.
- The regression proof that the removed types are absent exists only as the branch-point guard run (the Done-when
  forbids a committed test that names them).

## Numbers for the acceptance report

- Tests before (`origin/main`, environment-dependent class excluded) → after: pm-core 3158 → 2970, pm-workflow
  1967 → 1970, pm-runtime 1446 → 1558; whole reactor 6758 → 6711 (6738 after the base merges).
- Coverage (JaCoCo totals, whole-reactor run): pm-core 96 % instruction / 92 % branch; pm-runtime 89 % / 86 %;
  pm-workflow 94 % / 87 %.
- Gates on the final head da892e9: `verify -Ppre-commit` (6738 tests), `verify` (6738), `verify -Pcoverage` (6738),
  `verify -Pintegration-tests -pl pm-core` (10), `-pl pm-runtime` (27) — all success, with the one environment-dependent
  `SubprocessJobRunnerTest.escalated` (inotify watches) excluded; it fails identically on `origin/main` (log 222439).
- PR #122 merged at c6da0e2; follow-up PR #127 merged at c7780a0.
