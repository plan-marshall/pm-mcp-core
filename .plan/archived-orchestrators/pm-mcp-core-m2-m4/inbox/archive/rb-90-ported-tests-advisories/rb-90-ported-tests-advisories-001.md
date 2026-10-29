envelope_version=1
sender_type=plan
sender_id=rb-90-ported-tests-advisories
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-20T21:49:36Z

```landing-facts
schema=landing-facts/1
plan_id=rb-90-ported-tests-advisories
epic=pm-mcp-core-m2-m4
pr=#120
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
```

guard: none on this harness (unenforced)

## Deliverables

| Deliverable | Done-when | Order | Failing run | Commit |
|---|---|---|---|---|
| D1 — stale "Rule 6" fixture comments | met: all three comments name the behaviour and cite the heading Job Runtime Specification, Working-Tree Hash; no other fixture carries an ordinal | none | no failing output captured; a wording fix has no failing test | 8fac6fc |
| D2 — nested-class sentence | met: `refusesScenario` is top-level, the nested classes are `Loader` and `Relations` | none | none captured | fc2c8c6 |
| D3 — missing `@throws` | met: `unescape`, `roster`, `load`, `read` documented; `run` shown to be no defect (both tags already present at `WorktreeShaScenario.java:126-127`) | test-first for the roster refusal probe; none for the Javadoc | `refusesRoster` failed before the fix (`maven-2026-10-20-210126.log`, 1 failure) and passed after (`maven-2026-10-20-210205.log`, 29 tests) | c6528a4, 0eacd4f, 635b10a |
| D4 — README package line | met: line 13 is now the first sentence of `ported/package-info.java` | none | none captured | ca38ea9 |

Done-when "recorded in the pull request body" — met: PR #120 carries the four dispositions, including the `run` refutation and the per-Done-when order table.

Done-when "`verify`, `verify -Ppre-commit` and `verify -Pcoverage` are green" — met at 8bbbf8a and 635b10a: `maven-2026-10-20-200156.log`, `maven-2026-10-20-200940.log`, `maven-2026-10-20-202214.log`, `maven-2026-10-20-211544.log`, `maven-2026-10-20-212047.log`; 6780 tests each.

Done-when "no test changes its assertions" — met: no existing assertion changed. The only test edit adds a probe to `refusesRoster`. The planted-difference trials were not re-run post-merge; the diff changes no production code and no fixture directive, so the trials' inputs are unchanged (order `none`, not test-first). The ported suite stays green in every run.

Done-when "ADRs, if any" — none added, so nothing to renumber against `origin/main`.

`test-compile` per pushed head: local `verify` (which runs test-compile) at ca38ea9, 8bbbf8a and 635b10a; at c6528a4 the `verify -Ppre-commit` gate did the same. Heads 8fac6fc and fc2c8c6 were comment/doc commits whose CI build compiled and tested them green.

## Personas and skills

- D1, D2, D4: `persona-documenter` (documentation).
- D3: `persona-implementer` for the test-helper code and `persona-module-tester` for the added refusal probe.
- Self-review rounds and PR-comment triage: `persona-code-reviewer`.
- Skills named by the spec: `skills: none named`. No surface drift, so nothing added.

## Hypotheses

- HYPOTHESIS "the sentence at `provider-git-ported.adoc:218` and the wording at `README.adoc:13` are inexact" — confirmed. `WorktreeShaPortedTest.java:329` puts `refusesScenario` outside the nested classes at `:161` and `:260`; `README.adoc:13` stopped at the colon of `package-info.java:11-12`.

## Findings

- Self-review round 1: two nits — `load`/`read` omitted `IllegalStateException` from the class-path lookup; the roster refusal wording overstated a line for the line-less missing-suite case. Both fixed in 0eacd4f. One process item (PR body) closed with PR #120. One spec-tension note: D4 edits the module README although the standing constraint reserves it for a new package; D4 is the specific deliverable and governs.
- Self-review round 2: no open findings.
- CodeRabbit inline (minor): a duplicate fixture in a roster mapping was rejected by `Set.of` before the roster context was added. Fixed fail-first in 635b10a; thread PRRT_kwDOVBd2NM6uhHAz answered and resolved; the bot confirmed the fix and a follow-up review found no actionable comments.
- Sourcery (optional): refused on review budget.
- SonarCloud: quality gate passed; 1 new issue reported, not triaged (see Residue).
- CI: green on 8bbbf8a and 635b10a.

## Reviewers

| Reviewer | State | Head | History |
|---|---|---|---|
| coderabbit (required) | reviewed | 635b10a (current head) | triggered at 8bbbf8a, reviewed with 1 inline finding; re-triggered after 635b10a, reviewed the increment, no actionable comments |
| sourcery (optional, non-gating) | refused | none | trigger posted at 8bbbf8a; refused on its 7-day review budget (2 days 10 hours); not re-triggered because it is optional and the notice rules out a review |

## Operator questions

1. "The resolve result supplies the build commands as `… --command-args \"quality-gate\"` … Maven rejects `quality-gate` as an unknown lifecycle phase … How should I run the build gate?" — options: (a) use the executor with real Maven args, (b) stop the run. Selected: (a) use the executor with real Maven args.

Free-text operator messages: none beyond the invocation.

## Rule check

guard: none on this harness (unenforced)

- Build commands: run through `plan-marshall:build-maven:maven` with the arguments the operator selected (`verify`, `verify -Ppre-commit`, `verify -Pcoverage`, `test -pl …`); the resolve-supplied canonical names are not Maven phases.
- `build-classify` is not a verb of the installed executor (`unknown_verb`); the installed copy also omits `base_branch`, `build_timeout_seconds` and `guard_log` from `resolve`. Fail-closed direction taken: the diff is buildable, so the full gate ran. The stale installed bundle should be upgraded.
- No other rule broken; no guard refusals exist because this harness has no guard.

## Residue

- SonarCloud reports 1 new issue on PR #120 with the quality gate passed. Not triaged: `sonar fetch_findings` writes plan-scoped state outside this lane's write boundary, and the gate is green.
- Sourcery's optional review is unavailable for 2 days 10 hours on its account budget.
- The installed runbook-lane executor is stale relative to the skill (`build-classify` missing; `resolve` omits `base_branch`, `build_timeout_seconds`, `guard_log`).
