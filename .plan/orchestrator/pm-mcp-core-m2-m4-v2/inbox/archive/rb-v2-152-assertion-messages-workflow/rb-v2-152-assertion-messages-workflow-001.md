envelope_version=1
sender_type=plan
sender_id=rb-v2-152-assertion-messages-workflow
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-17T13:12:20Z

guard: none on this harness (unenforced)

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-152-assertion-messages-workflow
epic=pm-mcp-core-m2-m4-v2
pr=#474
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=2
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2136
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=5
review_body_findings_handled=5
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

## Deliverables

Standing rules: r7 (2026-11-10); rules that apply: R02, R06, R08, R11, R17, R18, R19, R20.

Spec read from the **ledger worktree** (`spec_source: ledger_worktree`); the resolve reported no `spec_differ` marker. `executor-skew`: **match** (executor 0.1.2135, lane 0.1.2135).

Two pull requests, because the spec caps a pull request at 100 files and the guard must land last: **#471** (31 files, merged as `bd2a90b8`) and **#474** (41 files, merged as `ae6c32a9`). Total 67 changed files, 2162 assertion messages.

**Deliverable 1 — a message on every assertion of the `pm-workflow` tests.** met.
- The scanner measured **2162** violations at `origin/main` (`1e9466ac`) in 71 files. The WIP branch `f53df72a` was taken over where it still merged (two content conflicts resolved by hand: `ast/AstNodeModelTest`, `dsl/WorkflowParserRefusalTest`), leaving 1887 in 65 files; the rest were converted file by file.
- order: `none` for the conversion itself (a mechanical, behaviour-preserving change verified by the scanner going 2162 → 1887 → 13 → 1 → 0).
- Messages added per package — #471: `dsl` 246, `model` 119, `consult` 117, `scenario` 88, `finalize` 77, `ast` 74, `unit` 65, `question` 44, `setup` 25, `hypermedia` 19, `diagram` 15, `log` 5, `classification` 3, `envelope` 2 (899). #474: `plan` 418, `engine` 344, `verify` 305, `task` 196 (1263).

**Deliverable 2 — the thin guard of `pm-workflow`.** met.
- `pm-workflow/src/test/java/de/planmarshall/workflow/testing/AssertionMessageScanTest.java`, the same shape as the six provider guards of #452, `BASE_PACKAGE = "de/planmarshall/workflow"`, `AssertionMessages.testSourceRoot(...)`, `AssertionMessages.scan(root, BASE_PACKAGE, Map.of())`; no scan logic copied. `pm-workflow/pom.xml:30-35` already declared the `pm-core` `test-jar` in test scope, so no pom edit.

## Done-when, clause by clause

| Clause | Verdict | Order | Test / evidence | Commit |
|---|---|---|---|---|
| The thin guard passes for the whole `pm-workflow` test tree | met | `none` | `AssertionMessageScanTest.everyAssertionOfTheModuleCarriesAMessage` green; scan reports 0 violations below `de/planmarshall/workflow`; log `372773ba89b242b690f2fe9cec32b172.log` | `48a352fd` |
| It fails on a fixture assertion without a message | met | `mutation-after` | Mutation: the committed message `"The refusal names the missing skills"` removed from `envelope/EnvelopePackerTest.java`; the guard failed with `1 assertion-message violation(s)` at `AssertionMessageScanTest.everyAssertionOfTheModuleCarriesAMessage:50`, log `1d9cb8f9430c40dfa1de76f4e02d5eda.log`; restored, green at log `372773ba89b242b690f2fe9cec32b172.log`. Control: `AssertionMessagesTest.missingAndOutOfRangeMessagesAreReported` (`missing` fixture `assertTrue(refusal.isPresent());` → `assertTrue: no message`), negative control `acceptableAssertionsAreNotReported` | `48a352fd` |
| No assertion is removed or weakened (same number of test methods per class) | met | `none` | No deleted `@Test`/`@ParameterizedTest`/`@Nested`/test-method signature in either diff (whole-diff scan); every hunk re-emits the same assertion with an appended message. The one removed `assertTrue` the self-review found (`plan/PlanInstanceHeaderTest`, exponent refusal) was restored before #474 | `9c90b0cb` |
| Each pull request body gives the number of messages added per package | met | `none` | tables in #471 and #474 | `3a64ec39`, `48a352fd` |
| Every pull request is green on its own and holds at most 100 files | met | `none` | 31 and 41 files; CI green on `3a64ec39`, `6b01ea24`, `48a352fd`, `50452482`, `c4d999a2` | `6b01ea24`, `50452482`, `c4d999a2` |
| Sonar: no open issue but a dismissed one, gate green | met | `none` | `read_open_issues` on #471 at `6b01ea24` and on #474 at `50452482` and `c4d999a2`: `0` confirmed, analysis revision = head; gate `OK` (5 conditions) | — |
| `verify -Ppre-commit` (rewrites reviewed and committed), `verify` of `pm-workflow`, `verify -Pcoverage` green | met | `none` | `verify -Ppre-commit` 10933 tests ×4 runs (compile/lint/test); `verify -pl pm-workflow -am` 7835; `verify -Pcoverage` 10933. No rewrite fell outside the plan's scope (the working tree held only the plan's test files) | `9c90b0cb`, `c4d999a2` |
| Links check (R17) | met | `none` | `trace.py /home/oliver/git/plan-marshall-documentation/doc .` → `BROKEN LINKS 0` | — |

Per-head test runs (module-tests): `3a64ec39` and `6b01ea24` (CI build), `48a352fd` (CI + local 7835), `50452482` and `c4d999a2` (CI + local 10933). No `test-compile` run was needed beside them; the project publishes one and the gate canonical covers it.

## Personas and skills

- production code: none touched (test-only plan).
- unit and module tests: `persona-module-tester` applied as the standard for the message wording and for the mutation-after control; `junit-core` (`SKILL.md` + `standards/testing-junit-core.md`) for the 20–60 character rule, message position and "says what is checked".
- every self-review round and every PR-comment triage: `persona-code-reviewer` (its adversarial-refute lenses were given to the review sub-agent and applied to each of the 13 PR findings).
- `skills: none named` — the spec declares no `Skills:` lines, and `resolve` reported `skills_note: none named`. R20 skills read and applied: `junit-core`, `cui-testing`, `cui-logging`, `java-core`, `javadoc`, `java-lombok`, `java-null-safety`.
- skills added for surface drift: none (the change stayed inside the declared `pm-workflow` test packages).

## Hypotheses

- "the `pm-workflow` test tree held 2108 messageless assertions at `ab5c15fb`" — **confirmed in scale, refuted in size at the head**: the shipped scanner measures 2162 violations at `origin/main` `1e9466ac` (`pm-workflow/target/assertion-message-violations.txt`, 71 files).
- "the thin guard of every provider module exists and scans its whole tree; the shared scanner is published in `pm-core:tests`" — **confirmed**: `pm-providers/*/src/test/java/.../testing/AssertionMessageScanTest.java` (six files), `AssertionMessages.java` in `pm-core` tests.
- "`pm-workflow/pom.xml:30-34` already declares the `pm-core` `test-jar` in test scope" — **confirmed**, `pm-workflow/pom.xml:30-35`; no pom edit.
- "the branch `feature/v2-103-assertion-messages-workflow-wip` still merges" — **confirmed with two content conflicts** (`ast/AstNodeModelTest.java:175`, `dsl/WorkflowParserRefusalTest.java:148`), resolved by hand; it converted `ast`, `classification`, `consult`, `diagram` and part of `dsl`.
- verify-first clause (open pull requests touching the `pm-workflow` test tree) — **refuted**: the only two open pull requests were `feature/harness-icon-store` and a Dependabot bump; neither touches the tree. Scanner re-run at the head of `main`.

## Findings

Self-review (round 1, `persona-code-reviewer`, on the whole diff): 1 assertion removed (`plan/PlanInstanceHeaderTest`, exponent refusal) — restored; 6 message-quality findings (`consult` successor wording, `engine` active-jobs wording, `verify` duplicate registry messages, `diagram` duplicate renderer messages, `engine` duplicate bounded-wait messages, `verify` noun-fragment message) — all fixed.

#471 (coderabbit, head `3a64ec39`): 8 inline findings — 3 fixed in `6b01ea24` (the `ask` form name, the remaining-ask count, the refused source line) and 5 declined with a stated reason (enum/relation/priority catalogues that are pre-existing test design from the merged WIP branch; changing them is not a message fix and the spec forbids changing an assertion beyond its message).

#474 (coderabbit, head `48a352fd`): 5 inline findings, all "the message names the wrong parameter" — all fixed in `50452482` (`CompositionRuleEvaluatorTest` unknown fact, `TaskProtocolRefusalTest` infinite threshold, `CompiledInvariantsTest` null pipeline, `DeclarationBoundsTest` null bound, `WorkflowValidatorTest` null unit).

All 13 threads were answered and resolved; the progress file carries one `finding:` line per finding.

## Reviewers

- **coderabbit** (required): reviewed `3a64ec39` on #471 (qualifying review, marker `final_review_risk_coverage` names that commit) and `48a352fd` on #474 (marker names that commit). Quota-limited: three refusals on #474 (08:42:21Z, 10:19:55Z, 11:51:10Z) while no review existed, each followed by the 90-minute wait and a re-trigger; the fourth trigger at 12:22 was answered by a review at 12:31:32Z. Exemption applied: `review_complete_after_handling` with the review on record, so the later heads `6b01ea24`, `50452482` and `c4d999a2` needed no re-trigger.
- **sourcery** (optional): refused both pull requests — "larger than the review limit of 150,000 diff characters" — recorded, not gating.

## Operator questions

None asked. No free-text operator messages were exchanged. The quota policy was spent before any question was considered, and the review arrived at its fourth trigger.

## Rule check

`guard: none on this harness (unenforced)` — the resolve reported no tool-call guard for opencode and `.plan/temp/guard.log` does not exist, so no guard line was read and none is claimed.

Rule breaks by the run itself, all reported rather than hidden:
- Two shell `grep -c` calls (counting conflict markers) and several shell `awk` passes over the scanner's violation list at the start — shell file reads where the file tools are required.
- `rm` used once to delete the lane's own temporary measurement test (`pm-workflow/.../testing/TempMeasureTest.java`, never committed).
- The links check ran the standing rule R17's absolute command (`python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …`) instead of the executor form; R17 names that exact command and the spec requires it.

Everything else was kept: one command per shell call without separators, no `cat`/`ls`/`head`/`sed`/`find` for reads, no `python3 -c`, no temp writes outside `.plan/temp/`, commits staged by name, no `git stash`/`restore`/`reset`/`rebase`/forced push, and the main checkout moved only through `switch-and-pull`.

Restarts and re-dispatches: none. No sub-agent was killed. 18 conversion sub-agents and 1 review sub-agent were dispatched; their edits were re-verified by the scanner (0 violations), by compilation, and by the full test suite.

Spec read facts: `spec_source: ledger_worktree`, no `spec_differ` marker. `executor-skew`: `match`, answered and recorded.

## Build server

- setup preflight: `ready` (registered).
- 21 routed build calls, **all `daemon_longpoll`**; no `in_process_fallback`. Two of them returned a live job and were re-attached in the same call (same `job_id`), never backgrounded.
- fallbacks: none — no degradation and no launch-fault retry.
- `killed` jobs: none.
- `runtime_stamp_reason`: none reported.

## Residue

- The detached base worktree `v2-152-assertion-messages-workflow-base` at `1e9466ac` answered `executor_in_worktree: absent` and was removed with `git worktree remove`; no base-sha evidence is claimed, which is why the guard's failing shape is proved by `mutation-after`.
- CodeRabbit's review of #474 covers `48a352fd`; the five later message fixes in `50452482` were not re-reviewed (the spec's `review_complete_after_handling` exemption), and every one of them was a message literal only.
- The latent scanner shapes the spec names (PLAN-V2-114 item 29) remain out of scope; no such assertion was written.
