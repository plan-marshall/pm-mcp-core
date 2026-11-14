envelope_version=1
sender_type=plan
sender_id=rb-v2-06-role-name-single-definition
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-13T23:45:30Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-06-role-name-single-definition
epic=pm-mcp-core-m2-m4-v2
pr=#410
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2118
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced).

# Landing — PLAN-V2-06: one definition of the role-name grammar with a source-scan guard

Standing rules read: r7, R01–R20 (R21 binds the orchestrator, not this lane). Spec read from the ledger worktree
(`spec_source: ledger_worktree`, no `spec_differ`). Merge commit `32a711f990d270012be5d04afbe9ef549d9b0987`, PR #410,
head `7bb4320f`.

## 1. Deliverables

| Deliverable | Verdict | Evidence (test, order, failing run) | Commit |
|---|---|---|---|
| D1 — the one definition and its test | met | `RoleNameTest` (23), `HarnessIdTest` (22), `IdentifierSpecificationParityTest` (2); order `none` (no failing run needed) | `0854c568`, `72447457` |
| D2 — the `pm-core` copies | met | the tests of every replaced class pass unchanged; `test -pl pm-core` green (4880 at `49b05150`, 4879 before the review fixes); order `none` | `49b05150`, `b08c3b7b` |
| D3 — the `pm-workflow` and `pm-runtime` copies | met | `test -pl pm-workflow -am` (7379) and `test -pl pm-runtime -am` (7196) green; order `none` | `88900aad`, `11e81c50` |
| D4 — the guard | met | `RoleNameSingleDefinitionTest` (15 tests); order `test-first` — written before the copies were replaced, failing at `39a75f568b762ba47358b0c57aa7fbc48530dd46` with every copy listed (job log `c163dfdcc91e495387a1bf00f4b642f8`); also `mutation-after` — the scratch copy in `a50e59d9` failed on `ShippedRoles.java:46` (job log `63f1072b66fe49d88b465d1f1e9b0caa`), reverted in `ae19fe23`; and the CI run at `72d1eaa0` failed on the copy a later plan added (`HarnessCandidate.java:44`, run 43524124607), which is the guard doing its job | `7d24c429`, `1862dcbd`, `6d2395f2`, `d5f72c41`, `c32ff5d0`, `fddf7946`, `289dcb4b`, `2bb842ab`, `72d1eaa0`, `7bb4320f` |
| D5 — documentation | met | guide, ADR, five package pointers, README lines, the `identifiers.adoc` § Modules link; order `none`; the links check returned `BROKEN LINKS 0`, `INLINE SCHEMA BLOCKS 0` | `3148033f`, `fbbb6c73`, `afa7e3f4` |
| D6 — the read-only sweep | met | reported in the PR body (14 remaining name-shaped grammars, none a role or harness name; no hand-written role-name check; the `harness_id` 1-to-32 vs 2-to-32 inconsistency); order `none` | n/a (no file) |

Per-head test runs against pushed heads: `289dcb4b` (pm-core, pm-workflow, pm-runtime, ITs), `2bb842ab` (module-tests,
`verify`), `72d1eaa0` (module-tests), `b08c3b7b`/`7bb4320f` (the full gate after the merge), plus CI's build and
integration-tests runs at `289dcb4b`, `2bb842ab` and `7bb4320f`.

The gates at `7bb4320f` (the merge head): `verify -Ppre-commit` 10472 tests, `verify` 10472, `verify -Pcoverage` 10472,
`verify -Pintegration-tests -pl pm-core -am` 21, `verify -Pintegration-tests -pl pm-runtime -am` 43 — all green.

## 2. Personas and skills

Personas: `persona-implementer` for D1–D3 production code; `persona-module-tester` for the tests of D1 and D4;
`persona-documenter` for D5; `persona-code-reviewer` for the three self-review rounds and for triaging every PR
comment.

Domain skills: the spec declares no `Skills:` lines, so `skills: none named` per deliverable; the R20 reading was
`java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing`. No skill was added for surface
drift: the only surface beyond the spec's, `HarnessCandidate` (added to `main` while the PR was open), is the same
module and the same decision, so no new domain skill applied.

## 3. Hypotheses

- "No behaviour change because every copy is used through a full match" — **confirmed**: every use was
  `matcher(..).matches()` or `String.matches`, and only `WorkflowStatus.requireMatch`
  (`pm-core/src/main/java/de/planmarshall/core/store/WorkflowStatus.java:500`) prints the name-grammar pattern, now the
  anchored `ShortName.TEXT`, so that message is byte-identical.
- "PLAN-V2-09 adds a `domain_id` pattern with the same text and PLAN-V2-23/V2-60 add records carrying a copy" —
  **refuted at the head**: none of them has landed, and no `domain_id` pattern exists in `src/main` (spec § 8). The
  guard is what stops them copying it.
- Verify-first re-grep: 24 literals in 22 classes on `main` at the start, matching the spec's binding correction 1;
  during the run a later plan added one more (`HarnessCandidate`), which the guard and CI found.

## 4. Findings

Self-review (three rounds, `persona-code-reviewer` sub-agent), all fixed:
- tokenizer did not skip character literals, so a `'"'` inverted quote parity (`72447457`/`d5f72c41`, control cases
  added);
- non-vacuity was tautological (`d5f72c41`, then `fddf7946`, then `72d1eaa0`: every reached root must hold a file and
  the three named roots must be among them);
- offender path dropped the module prefix (`d5f72c41`);
- the exemption matched a path suffix (`72d1eaa0`: full repo-relative path);
- the parity/identity assertions used string identity or a `"null"` fallback (`72447457`);
- documentation counts, the "only `WorkflowStatus` prints a pattern" wording and the unanchored
  `GradedConfiguration.HARNESS_NAME` (`fbbb6c73`, `afa7e3f4`);
- the guard Javadoc and the scan's remaining limits (`fddf7946`, `afa7e3f4`).

CI: the guard failed at `72d1eaa0` on `HarnessCandidate.java:44` (a copy PLAN-V2-39/#412 added to `main` after the
branch was cut). Disposition: merged `origin/main` and replaced it (`b08c3b7b`) — the spec's Standing Constraints say a
copy added since the spec was written is replaced like the others.

PR comments (coderabbit, review on `2bb842ab`):
- `finding: coderabbit inline inline-1 — 72d1eaa0` — derive the coverage check from the reactor, not a fixed module
  list. Disposition: the module list is gone; the scan counts every production root it reached and fails on an empty
  root, and the three roots the Done-when names must be among them.
- `finding: coderabbit inline inline-2 — 72d1eaa0` — exempt only the authoritative `ShortName.java`. Disposition: the
  exemption compares the full repository-relative path.
Both threads were answered with the commit and resolved.

Sourcery (optional): refused on its own quota, no review.

## 5. Reviewers

| Reviewer | Required | Outcome | Head reviewed | History |
|---|---|---|---|---|
| coderabbit | yes | reviewed | `2bb842ab` (marker `final_review_risk_coverage` coveredCommitId `2bb842ab`, kind `reviewed`) | quota-refused at 17:57Z, 19:51Z and 21:23Z; each re-trigger waited 90 minutes, the escalated `@coderabbitai full review` was posted once at 21:23Z; the review landed at 23:00Z; both findings fixed and resolved |
| sourcery | no | refused (quota) | — | refused at 17:57Z for "2 days and 16 hours"; optional, does not gate the merge |

Coderabbit is `review_complete_after_handling`, and its qualifying review plus the handling of both findings is the
review at any head of this PR — no re-trigger after the later pushes, per § 6 step 6.

## 6. Operator questions

1. "The pre-flight `executor-skew` check answers `skew`: the executor at `.plan/execute-script.py` carries version
   0.1.2114 while the lane text this run loaded is version 0.1.2118. … How should I proceed?" — options: "Regenerate
   the executor, then proceed (Recommended)", "Stop the run as blocked", "Proceed on the current executor anyway" —
   **selected: "Regenerate the executor, then proceed"**. The executor was regenerated to 0.1.2118 and `executor-skew`
   then answered `match`.

No other question was asked; no free-text operator message was received.

## 7. Rule check

- `guard: none on this harness (unenforced)` — the resolve result reported `guard: none`, so no tool-call guard line
  exists for this run and "all kept" is not claimed. The check below counts the run's own actions.
- Spec read: `spec_source: ledger_worktree`, no `spec_differ` marker. `executor-skew` at session start: `skew`, then
  `match` after the operator-approved regeneration; re-checked at landing time (`match`, executor 0.1.2118).
- Shell file operations by the run: none — every file was read with the read/grep/glob tools and written with the write
  and edit tools, except the sanctioned reads of the worktree (`git status`, `git diff`, `git log`).
- Every build went through the resolved `build` commands; every CI action through `plan-marshall:tools-integration-ci:ci`;
  the links check ran the R17 command as the standing rule names it (`BROKEN LINKS 0`).
- Restarts/killed sub-agents: none. The self-review sub-agent was re-dispatched twice (rounds 2 and 3) on the same
  session, each time re-reading the current diff; no work was re-run.
- The long reviewer-quota wait (four 90-minute windows) crossed a date change; the lane text loaded at session start
  remained the binding text and the executor version was re-checked unchanged.
- Nothing under `.plan/` was written except `.plan/temp/` (the progress file, the PR body draft, the landing file), the
  `ci` body slots the `prepare-*` verbs returned, and this inbox message.

## 8. Build server

Setup preflight: `ready` (project enrolled, daemon reachable). Every build resolved `routed` / `daemon_longpoll`
(`requested=auto`); the mechanism line `[BUILD-SERVER] resolved build (requested=auto, resolved=routed, reason=None,
notation=plan-marshall:build-maven:maven, plan=NO_PLAN, mechanism=daemon_longpoll, serialization=daemon-scheduled)`
appears on each. In-process fallbacks: none; launch-fault retries: none; `killed` jobs: none. Two rounds waited behind
another job in the same worktree (559 s and 156 s of `queued_seconds`) and were re-attached with one bounded wait each.
`runtime_stamp_reason`: not reported (`plugin_version` is `unknown`, the one fact the runtime-info seam did not supply).

## 9. Residue

- The 35 occurrences of the name grammar in the JSON schemas stay unguarded: the spec's binding correction 3 defers
  them ("the outline decides whether to add a schema-resource assertion … recommendation yes for the two role-artifact
  patterns and no for the other schemas"), and only two schema patterns are parity-tested (D1). A follow-up plan can
  extend the guard to the resource files.
- The `harness_id` grammar is 1 to 32 in the harness-profile and role-artifact schemas and 2 to 32 in the registry and
  the store schemas; the code keeps each where its schema states it. A lead for the documentation repository.
- The reserved-DSL-keyword refusal lives only in `WorkflowValidator`; `RoleArtifactValidator` and `ShippedRoles` accept a
  reserved keyword. Reported, not changed (D3).
- `.plan/marshal.json` has no `sonar` block, so `resolve` answers `not_configured` while the project's CI analyses
  SonarCloud on every PR. This run honoured R18 by reading the public API at the merge head: 0 open issues, quality
  gate OK (new coverage 81.9 %, 0 security hotspots). A configuration fix in the concerned project file would let the
  lane's own Sonar gate carry the check.
- Sourcery (optional reviewer) holds a hard quota refusal for about two more days; no optional review this run.
- No base worktree was used, so `cleanup_owed` is false; the plan worktree was removed, the branch pruned locally and
  remotely, and the main checkout moved to the merge commit (`switch-and-pull`, `59e9b815` -> `32a711f9`).
