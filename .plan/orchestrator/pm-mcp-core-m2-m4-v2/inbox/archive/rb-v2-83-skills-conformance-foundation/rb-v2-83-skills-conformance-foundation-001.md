envelope_version=1
sender_type=plan
sender_id=rb-v2-83-skills-conformance-foundation
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-10-30T12:10:44Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-83-skills-conformance-foundation
epic=pm-mcp-core-m2-m4-v2
pr=#248
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=2
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

guard: none on this harness (unenforced)

## Landing — PLAN-V2-83: add the coding-skill dependencies and record R20

Standing rules: **r4**, rules **R02, R06, R08, R11, R17, R18, R19, R20**. The standing-rules file was read in full before the work.

PR #248 merged as `d88d079ddcdf23b30946f29d6c718dc3f30585ac`. Branch `feature/v2-83-skills-conformance-foundation` deleted (local and remote); worktree removed; `main` fast-forwarded to the merge commit.

### 1. Deliverables

**D1 — the dependencies (test first: the build is the test).** Commit `4a397e0a`.
- Six artifacts, correct modules/scopes, no version literal: **met**. `order: none` — the declarations are additive and no class references the artifacts yet, so no failing run exists to capture; the spec's "the build is the test" is the green build on the managed versions. The parent `pm-mcp-parent` manages all six (`help:effective-pom`: `version.easymock`, `version.jspecify`, `version.lombok`, and `dependencyManagement` entries for generator, value-objects, mockwebserver, easymock, jspecify, lombok) — HYPOTHESIS confirmed, no stop-and-report needed.
- `pm-core`/`pm-workflow` enforcer rules and `BuildGuardsIT`: **met**, `order: none`. `verify -Pintegration-tests -pl pm-core -am`: 13/13, "Build guards of the repository".
- `dependency:tree` of `pm-core` and `pm-workflow`: **met**, `order: none` — both filtered trees empty for JGit, cui-http, Quarkus, Vert.x, `jakarta.enterprise`/`jakarta.inject`.

**D2 — CLAUDE.md and the ADR.** Commits `d7788a33`, `9f867e5b`, `9d69d872`, `c8827bae`, `3ab4e881`.
- CLAUDE.md § Code Standards gains R20 in the repository's own words (incl. generated data and its exemption, no `Random`/`UUID.randomUUID()`, generator artifacts, contract tests, `LogAsserts`, annotation HTTP tests, `@NullMarked`, `Optional` return-only, `@Builder` 4+, assertion messages, EasyMock/no new fake, `@UtilityClass`, no generic catch, no-precedent): **met**, `order: none`.
- Forbidden line now "Forbidden: Mockito, PowerMock, Hamcrest; test doubles are EasyMock": **met**.
- § Dependencies and Versions names the six as allowed: **met** (five of 2026-10-30 plus the R04 mockwebserver dependency).
- Log-message-holder rule unchanged word for word and repeated in the ADR: **met**.
- ADR `doc/adr/010-the-coding-skills-and-the-accepted-exception.adoc` (next free number; R11 checked), operator answers quoted, the one exception and the four not-exceptions with closing plans: **met**.
- `doc/adr/README.adoc` index row: **met**.

**Done-when block:** every clause **met** — modules compile/test (8412 tests), `BuildGuardsIT`/enforcer green, `dependency:tree` clean, CLAUDE.md/ADR agree with R20 (after three review-driven corrections), operator answers quoted, Sonar 0 open issues + gate OK, all build gates green, links check run. Test-run shas: local gates ran on the tree that became `4a397e0a`/`d7788a33`; CI's full Maven build and `sonar-build` are green on the final head `3ab4e881`.

### 2. Personas and skills

- Production/config work (the nine POMs): `persona-implementer`.
- Documentation (CLAUDE.md, ADR, README): `persona-documenter`.
- Every self-review round and the PR triage: `persona-code-reviewer`.
- The spec declares no `Skills:`/`Persona:` lines, so the resolve result reports `skills: none named`. The spec's First Instruction names nine skills, all read as standards before the work: `cui-logging`, `cui-testing`, `cui-http-testing`, `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `junit-integration`. No skill added for surface drift.

### 3. Hypotheses

- `HYPOTHESIS` parent `pm-mcp-parent` manages the six versions — **confirmed** by `help:effective-pom` of `pm-core` (log `maven-2026-10-30-102642.log`: properties `version.easymock/jspecify/lombok`; dependencyManagement entries for all six).
- `OBSERVED` dependency claims re-checked at `main` after the verify-first PR listing (only open PR: #71, a Dependabot `json-schema-validator` bump; it touches `pm-core/pom.xml` but is unmerged): all still hold.
- `OBSERVED` audit conflicts (§5 items 1–11) — the operator's answers resolve them; recorded in ADR 010.

### 4. Findings

- Self-review round 1: supersede instrument (A), `@NullMarked` scope (B), mockwebserver provenance (C), coverage wording (D) — all fixed in `9f867e5b`. Links evidence re-run; the "test-first" clause is non-discriminating by nature of an additive dependency change — reported, not fixable.
- Round 2: provenance re-merge (D-1), missing `Random`/`UUID` and no-precedent rules (D-2), ADR-002 instrument (D-3) — fixed in `9d69d872`.
- Round 3: stale OpenCode bridge (F1), ADR provenance/list (F2), a `package-info` rule relaxation (F3) — fixed in `c8827bae`; F1 re-fixed in `3ab4e881` after the bridge was re-emitted from the final CLAUDE.md.
- Round 4 (operator-authorized): F2/F3 verified; F1 confirmed fixed (`source-sha256 f9823227…` = `sha256(CLAUDE.md)`, body carries the restored rule). **self-review: clear.**
- CI: all checks green on `3ab4e881`. CodeRabbit: no actionable comments. Sourcery: quota refusal (optional). Sonar bot: 0 new issues, gate passed.

### 5. Reviewers

- `coderabbit` (required, quota-limited): reviewed head `3ab4e881`; summary marker `final_review_risk_coverage {"sourceCommitId":"3ab4e881…","coveredCommitId":"3ab4e881…","kind":"reviewed"}`, "No actionable comments were generated in the recent review". No threads. Reviewed / current head.
- `sourcery` (optional, quota-limited): **refused** on quota at 11:41 UTC ("request another review in 2 days and 11 hours"). Optional, so not gating; no re-trigger needed for merge.

### 6. Operator questions

1. "The executor `.plan/execute-script.py` is version 0.1.2034, older than the lane text (0.1.2041). How do you want to proceed?" — options: "Regenerate, then continue (Recommended)", "Stop here". Selected: **"Regenerate, then continue"**. Executor regenerated to 0.1.2041; skew re-checked `match`.
2. "Self-review round 3 found 3 defects … The lane caps self-review at 3 rounds … How should I proceed?" — options: "Run a 4th verification round (Recommended)", "Proceed to PR now". Selected: **"Run a 4th verification round"**.

No free-text operator messages.

### 7. Rule check

- **guard: none on this harness (unenforced).** `{guard_log}` was not consulted as evidence because nothing was enforced; the check below counts the run's own actions.
- `executor-skew`: `match` (0.1.2041 = 0.1.2041) after the operator-approved regeneration of a stale 0.1.2034 executor.
- Spec read: `spec_source: ledger_worktree`; no `spec_differ` marker was reported.
- Shell discipline: files read/searched with Read/Grep/Glob; git used for git state. Deviations: (a) R17's links check is a direct `python3 <sibling>/trace.py` invocation, as R17 and the spec's Done-when prescribe, not the executor; (b) a temporary detached worktree of the documentation repo was created under `/tmp/opencode` (environment-approved) to run R17 against `origin/main`, then removed — outside the lane's `.plan/temp/` rule; (c) `git -C /home/oliver/git/plan-marshall-documentation` was read for the sibling's HEAD and `origin/main`, as R17 names that checkout.
- The self-review sub-agents disclosed their own rule breaks (shell `find`/`grep`/`ls`, one pipe, one `python3 -c` with `;`) while gathering evidence; per the lane these count as the run's. They were read-only and changed nothing.
- Builds: all nine through the resolved build commands with default routing; no fallbacks, no retries, no killed jobs.
- No restarts, no killed sub-agents; the self-review was dispatched four times (rounds 1–4) with what each re-ran recorded above.
- Main checkout was moved only by `switch-and-pull` (step 3 of cleanup), never by hand.

### 8. Build server

- Setup preflight on the worktree: `ready`.
- Nine builds, all `mechanism=daemon_longpoll`: effective-pom, dependency:tree (`pm-core`, `pm-workflow`), `verify -Ppre-commit`, `verify`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am`, `verify -Pintegration-tests -pl pm-runtime -am`, final `verify -Ppre-commit`. Fallbacks: none. Killed jobs: none.
- `runtime_stamp_reason`: none reported; `plugin_version` is `unknown` (the runtime-info seam returned no version).

### 9. Residue

- **ADR 002's Status** still says "Accepted" while R20 replaces its dispatcher rule and PLAN-V2-93 supersedes it. `doc/adr/002` is outside this plan's Expected Surface; flagged to the orchestrator/PLAN-V2-93 rather than edited here.
- **Links:** against documentation `origin/main` `f5fb97b`, `BROKEN LINKS 8` — all pre-existing cross-repo drift (pm-mcp-core anchors `#fix-proof`, `#repaired-landings`, `PM-WATCH-PHASE-124` missing on `main`), none in this change. The sibling checkout was at `main fe91f425` (behind `origin/main`) when first run; the re-run used a detached worktree at `origin/main`.
- **Undeclared surface (R19 observation):** five Java files rewritten by `verify -Ppre-commit` (formatter/OpenRewrite only; no behaviour change) and the regenerated `.opencode/rules/plan-marshall-standing-rules.md` bridge.
- **Sonar state:** `resolve` reports `sonar: not_configured` (no project key in the project config), but R18's public search at the merge head measured 0 open new-code issues and a passing quality gate; the facts line records the measured `0`.
- **Sourcery** remains quota-limited for ~2 days 11 hours; optional, so not a merge condition.
