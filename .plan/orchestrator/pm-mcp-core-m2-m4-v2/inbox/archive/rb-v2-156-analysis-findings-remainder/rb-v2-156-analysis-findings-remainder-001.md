envelope_version=1
sender_type=plan
sender_id=rb-v2-156-analysis-findings-remainder
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-19T22:22:05Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-156-analysis-findings-remainder
epic=pm-mcp-core-m2-m4-v2
pr=#516
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2155
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=3
review_body_findings_handled=3
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-156 — the lesson store's untrusted rule, DEL round trip and remaining defects (part 1)

Merged as `5490323a` (PR #516, head `ff536e3c`). Standing rules: r7, R02, R05, R06, R07, R08, R10, R11, R13, R14, R15, R16, R17, R18, R19, R20 (the spec's First Instruction names r6; the file on disk is r7 and r7 is what binds).

## 1. Deliverables

| # | Deliverable | Verdict |
|---|---|---|
| 1 | The untrusted-citation rule and every arm of it | **met** |
| 2 | DEL round-trips in every front-matter string | **met** |
| 3 | The rest of the lesson store's defects (a, e, g, h done; i not) | **not met as written** — 3(i) not attempted |
| 4 | The null contracts and the 17-component conversion | **not met** — not attempted (the largest piece of the plan) |
| 5 | The residue and the reporting of #512 | **met in part** — 5(c) and 5(d) done, 5(a) and 5(b) not attempted |
| 6 | One line per carried item | **met** — the table below and the PR body |

Done-when evidence, per clause:

- **D1(a)** answer stated in `store-schemas/10` and ADR `core-store/079`: met, order `none`.
- **D1(b)** one test per arm: met. Order `test-first` for the three arms that changed behaviour (absent, same-transaction deletion, malformed refusal) — failing run at the branch base `e2ae8627` with the fix uncommitted, log `70f599f121734ba8ab301dc6e32f9cb8` (3 failures); order `mutation-after` for the arms that already held — mutation of the unreadable/integrity arms and the undecodable-bytes arm, log `3411014062664329b6cea81cbce26662` (6 failures across the four integrity arms, the bytes arm and the staged-unreadable case); green at `6d44a1ae`.
- **D1(b) EmptyShell written to a file**: met by `citesPersistedEmptyShell` (a blank-body file re-baselined the way `accept-edit` does), after the self-review's round 1 found the arm tested only in memory.
- **D1(b) `citesAbsentLesson` display name**: met — the name says "stays false" and the assertion is `assertFalse`.
- **D1(c)** `default -> false` removed; a malformed lesson reference decided (refused): met, order `test-first`, log `70f599f121734ba8ab301dc6e32f9cb8`.
- **D1(d)** a lesson deleted in the same transaction is untrusted: met, order `test-first`, same log.
- **D1(e)** statements in the specification/guide and one ADR: met, order `none`.
- **D2** DEL round-trips in every front-matter string, title-only refusal deleted: met, order `test-first` — failing run log `50484b830c1c4ae9b3e3cfc23d6587ee` (14 of 14 failed); the premise (Jackson leaves DEL literal, SnakeYAML refuses it) is read from that run and pinned by `readerRefusesALiteralDel`; green at `4618710b`.
- **D3(a)** leading-zero arm: met, order `mutation-after` — with `normalized` replaced by the identity the two tests fail (log `14f6a3ec7887404da0e7242d878a7813`: order `0010` before `011`, allocation `0011` instead of `012`).
- **D3(e)** the two value rules moved into `LessonFrontMatter.Retired`, the who-retires rule kept in the store: met, order `none` (the assertions now refuse at construction).
- **D3(g)** `ScopeTransactionTest` for `lockTransaction`, `auditSink`, `targetPaths`, `targetContent`: met, order `none`.
- **D3(h)** assertion-message sweep, contract notes on the literals, four crash-window loops as `@ParameterizedTest`: met, order `none`; `AssertionMessageScanTest` green.
- **D3(i)** `store-schemas/10` reduced per R05 and the `doc/plans/README.adoc` row decided: **not met** for the reduction (an editorial change to the requirement text; the schema block, the kind tables and every anchor are untouched). The row was decided: this plan owns no row (R01/R08), and `lesson-store` stays `done` because the store's remainder is the open `lesson-lifecycle` row.
- **D4** not met, reason: not attempted. **D5(a)/(b)** not met, reason: the `pm-runtime` derived-fact store was not touched. **D5(c)/(d)** met: the ADR links, the line wraps, the `JsonModelResolver` Javadoc and this report.
- **D6** met: 57 lines below and in the PR body.

Per-head test runs: `verify` (the resolved gate canonical) at `f113b482`, `048ba9be`, `1c32bf6a`, `6d44a1ae` and after the merge at `ff536e3c`; the last two are 11 352 and 11 568 tests (logs `0c299f32327f42cc86bbb60de2b38882`, `a22a59bd00f04a179b88d3e7a6836f41`). Targeted module runs for every iteration are in the progress file. CI green on `ff536e3c` including integration tests and SonarCloud.

## 2. Personas and skills

- Production code: `persona-implementer`; tests: `persona-module-tester`; every self-review round and every PR-comment triage: `persona-code-reviewer`.
- Domain skills (R20, named by the spec): `java-core`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing` — read before the work; their standards files `java-core-patterns.md`, `null-safety-core.md`, `testing-junit-core.md`, `test-generator-framework.md` read as well. `skills added: none` — no surface drift beyond the lesson store.

## 3. Hypotheses

- The DEL hypothesis ("U+007F is left unescaped by the Jackson writer and refused by the SnakeYAML reader") — **confirmed** by the failing round trip: 12 of 13 front-matter members read back `Unreadable`, the title only because the construction refused it (log `50484b830c1c4ae9b3e3cfc23d6587ee`).
- The `RunningJob` S2637 hypothesis (deliverable 4) — **not tested**: deliverable 4 was not attempted.

## 4. Findings

Self-review, three read-only rounds with `persona-code-reviewer`:

- round 1: 11 findings. Fixed: F1 (the malformed-reference refusal was order- and state-dependent — the major one), F2 (EmptyShell only in memory), F3 (the decode consequence of the moved retirement rules undocumented), F4/F5/F11 (ADR 077), F7/F9/F10 (two 60+ character messages, `bundle` missing from the DEL round trip, new lines over 120). Reported, not changed: F6 (reporting).
- round 2: F1–F5, F7, F9–F11 closed; N1/N2 (tests that could not fail), N3 (a duplicated refusal message), N4 (a display name), N5 (no accepting control at `create`) fixed; F6/N6 reported; F8 kept as matched positive controls.
- round 3: **no open finding**; one display-name nit fixed.

PR comments: CodeRabbit reviewed head `4618710b` and raised three findings, all fixed in `6d44a1ae`:

- `finding: coderabbit inline inline-1 — 6d44a1ae` — `create` set `derived_from_untrusted` only from the caller; it now applies the citation rule to its sources.
- `finding: coderabbit inline inline-2 — 6d44a1ae` — the resolution arms are now compared with `LessonResolution.State`, so a new state cannot pass without an arm.
- `finding: coderabbit additional additional-1 — 6d44a1ae` — the `merged_from[]` half of the specification sentence is now implemented instead of only recorded as a gap.

Both inline threads were answered and resolved. Sourcery refused on its own quota (optional reviewer, recorded).

## 5. Reviewers

| Reviewer | Required | Result | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes | **reviewed** | `4618710b` (qualifying review, `final_review_risk_coverage` kind `reviewed`) | trigger at 18:18Z refused on quota; escalated `full review` at 19:50Z refused; 90-minute wait; re-trigger at 21:20Z performed; review at 21:32Z; 3 findings handled; not re-triggered after the merge because the resolve row marks it `review_complete_after_handling` and the review is on record |
| sourcery | no | **refused** | — | `@sourcery-ai review` at 18:18Z: "used your own review budget … in 3 days and 19 hours" — recorded, never waited on (optional) |

## 6. Operator questions

1. "Deliverable 1(a) asks you which resolution states of a CITED lesson mark a new revision `derived_from_untrusted: true`…" — options: (a) unreadable/integrity mark, absent does not; (b) revert to the specification's literal "is `true`"; (c) keep the whole widening including absent and document it. **Selected: (a).**
2. "Deliverable 3(e) says the retirement rules should be enforced in `LessonFrontMatter.Retired` 'in one place'…" — options: (a) move (i) and (ii) into `Retired`, keep (iii) in the store; (b) keep all three in `requireValid` and state why; (c) move all three with an operator-act component. **Selected: (a).**

Free-text operator messages: none. No question tool was needed for the reviewer quota, because the policy's steps 1 and the escalation carried the review.

## 7. Rule check

- Guard: **`guard: none` on this harness (unenforced)** — the resolve result reported no tool-call guard, so no guard log exists for this run and nothing is reported as "all kept".
- Shell file operations issued by this run: reads and searches were done with the file tools, with these exceptions, all recorded rather than hidden: two `sed -n` reads of source ranges in the first exploration (before switching to the file tools), one `awk` line-length scan (the repo has no file tool for line widths), one `date -Iseconds`, and one `wc -l`. No `cat`, `ls`, `find` or shell `grep` was used for repository content.
- One scripted pipeline was attempted and refused by the executor: the build with `-Dtest=A+B` reported `test_run_executed_nothing` (the `+` joins two classes into one pattern); it was re-run with a comma. One `ci` call with `--format json` was refused as an unrecognised argument and re-issued without it.
- Routed builds: every build went through the resolved `build` canonicals and the default routing; the client reported `mechanism=daemon_longpoll` on each. One build call returned `status: running` because the queue waited 300 s and was re-attached, never backgrounded. No launch-fault retry and no `killed` job.
- Full `verify` failed once on `DiagnosticCollectorRedactionTest.hostileUrlFloodThroughTheDiagnosticConstructor` (a 120 s bound in a class this diff does not touch) while the build server ran another job's build; the class passes in isolation (log `4ace9c37bf554f44b8066f4229574da6`) and later full runs were green.
- CI: `build / quarkus-alignment` failed once on `could not fetch …/quarkus-bom/3.40.1/quarkus-bom-3.40.1.pom: HTTP Error 404` — an unresolvable upstream artifact in a check no POM of this diff reaches; re-run of the workflow and green.
- Git: staged by name, never `git add -A`, no stash, no restore, no checkout of a path, no rebase, no forced push. One merge (`origin/main`) as §7 prescribes, with the commit trailer.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ` marker.
- `executor-skew`: **match** (executor `0.1.2155`, lane `0.1.2155`).
- Restarts, killed sub-agents and re-dispatches: none; the three self-review rounds are the same read-only sub-agent session, and the two quota waits were held by `ci pr wait-for-comments` in this session.

## 8. Build server

Setup preflight: **ready** (registered). Every build of this run was routed: `daemon_longpoll` 27 (the count of build calls this session, including the iterations and the mutation runs), `in_process_fallback` 0. Fallbacks: none — no `degraded` result, no `executor_absent_from_worktree` (the worktree reported `executor_in_worktree: present`), no launch-fault retry. `killed` jobs: none. `runtime_stamp_reason`: none; `plugin_version` is `unknown` as the runtime-info seam reports it.

## 9. Residue

- Deliverables 3(i), 4 and 5(a)/(b) are open and carry a successor spec; the 57 carried items are split off (below).
- The schema description of `sources[].ref` stays broader than the store's rule for `kind: lesson`, because the schema block of `store-schemas/10` must stay byte-stable (R10); recorded, not changed.
- The R17 links check ran from the worktree against the sibling documentation checkout after fetching `origin/main`; the checkout lags its `origin/main` (`72ebf20` vs `a4f3a79`) and the check reports 3074 missing files, all of them links into that checkout. No report names a file this plan changed.
- The `merged_from[]` gap CodeRabbit flagged is closed in `6d44a1ae`.

## Deliverable 6 — the 57 carried open items

None is adopted: the run delivered the lesson store's own defects and no carried item's files. Each is split off with the plan, plan family or package it belongs to; where no staged plan owns it, the target is the successor spec of PLAN-V2-156, which this run does not stage.

1 split off (deliverable 4(a)) · 2 split off (`pm-provider-http`, PLAN-V2-126) · 3 split off (`core.store`, PLAN-V2-129) · 4 split off (deliverable 4) · 5 split off (`core.role`/`core.store`) · 6 split off (`core-role.adoc`) · 7 split off (`pm-provider-github`) · 8 split off (deliverable 4(b)) · 9 split off (`runtime.credentials`/`config`) · 10 split off (PLAN-V2-125 + code) · 11 split off (`core-build`, PLAN-V2-131) · 12 split off (`workflow.verify`) · 13 split off (needs a cap decision) · 14 split off (`filesystem-layout`) · 15 split off (`core-build`) · 16 split off (`core-role-corpus`) · 17 split off (`runtime.job`) · 18 split off (three codecs) · 19 split off (`runtime.job`) · 20 split off (`core-id`, workflows) · 21 split off (`core-spi`) · 22 split off (`workflow.engine`) · 23 split off (`runtime-guard`) · 24 split off (`core-build`) · 25 split off (`core-profile`) · 26 split off (workflow dsl ADR) · 27 split off (`.plan/marshal.json`) · 28 split off (assertion-message family) · 29 split off (PLAN-V2-103 class) · 30 split off (`runtime.launch`) · 31 split off (PLAN-V2-67 residue) · 32 split off (assertion-message family) · 33 split off (`workflow.consult`) · 34 split off (workflow dsl/verify) · 35 split off (PLAN-V2-155's predecessor) · 36 split off (`core.store`, `runtime.config`) · 37 split off (`core.role.qualification`) · 38 split off (PLAN-V2-49 surface) · 39 split off (repository ADR directory) · 40 split off (`core-config`, `workflow-plan`) · 41 split off (`runtime.job`) · 42 split off (`runtime.worker`) · 43 split off (four unowned points) · 44 split off (`core-build`, `runtime.job`) · 45 split off (assertion-message family) · 46 split off (assertion-message family, ADR 028) · 47 split off (`workflow` engine, needs a decision) · 48 split off (`runtime.worker`) · 49 split off (`runtime.worker`) · 50 split off (`core.store`, `runtime.job`) · 51 split off (`workflow.plan`) · 52 split off (workflow dsl, needs a decision) · 53 split off (`core.store`/`core.config`) · 54 split off (operator may want a plan of its own) · 55 split off (`runtime.worker`) · 56 split off (`runtime`) · 57 split off (`model-work` spec and watch).

The same table with a reason per item is in the pull request body.
