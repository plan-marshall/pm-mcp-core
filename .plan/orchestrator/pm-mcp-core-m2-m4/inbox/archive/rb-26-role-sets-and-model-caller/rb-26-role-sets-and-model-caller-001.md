envelope_version=1
sender_type=plan
sender_id=rb-26-role-sets-and-model-caller
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-23T10:51:16Z

```landing-facts
schema=landing-facts/1
plan_id=rb-26-role-sets-and-model-caller
epic=pm-mcp-core-m2-m4
pr=#146
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.1989
executor_version=0.1.1989
sonar_open_issues=0
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
```
guard: none on this harness (unenforced)

Standing rules: r1, R01–R13, R15, R18 (read in full at r1; R17 not in the plan's list and not run).

## 1. Deliverables (per Done-when)

All five deliverables are fully met. The Done-when's clauses:

| Done-when clause | Verdict | Test | order | Failing run | Commit |
|---|---|---|---|---|---|
| Validation table row by row, each reason with a refusing fixture | met | `RoleValidationTableTest` (all ten reasons; `RoleArtifactValidatorTest` for `schema_invalid`) | test-first | base `9c9d99c`, log `70279308ddf24d3bb46621d129fe0962` (compile: the types did not exist) | `0c07ea2` |
| A class its harness cannot run is `harness_class_not_runnable` | met | `RoleValidationTableTest` (read_only, writer, zero_tool) | test-first | same run | `0c07ea2` |
| A valid artifact whose effective model the catalogue forbids is `model_not_allowed`; a `steward` never | met | `RoleValidationTableTest`, `RoleEffectiveConfigurationTest` | mutation-after | `2493ca9`, log `maven-2026-10-22-223123` (check removed: 3 tests fail) | `2493ca9` |
| Each level overrides only what it names; a changed assignment or plan override changes both digests | met | `RoleEffectiveConfigurationTest` (six levels, project artifact over the machine assignment, both digests), `RoleDigestTest` | mutation-after | `2493ca9`, log `maven-2026-10-22-222548` (assignment cached: `assignmentIsReadAtEveryResolution` fails) | `c6234c2` |
| A task of the `judge` and of the `steward` is queued without a qualification record | met | `ModelCallerTest` | test-first | `2493ca9`, log `maven-2026-10-22-224149` (compile: `ModelCaller` and its ports did not exist) | `16b1730` |
| The shipped set is read from `classpath:/roles/`; tests use fixture roles | met | `ShippedRolesTest` | test-first | `16b1730`, log `maven-2026-10-22-224914` (compile: `ShippedRoles` did not exist) | `c999d63` |
| Sonar: no open issue but a dismissed one, gate green, before ready | met | SonarCloud, PR 146 | none (no code run produces it) | gate `OK`, `0` open issues on `2724d34` (10:20Z); the PR comment records 0 new issues | `2724d34` |

Per-head test runs (the project publishes no separate `module-tests`/`test-compile` canonical; the gate canonical `verify` is the per-head run): `401720a` 7031, `ec888f9` 7040, `7752afd` 7050, `1bf7270` 7052, `7cb1dbd` 7052, `acda501` 7057, `0e0d44b` 7058, `4ae6a05` 7076, `2724d34` 7093 tests — every one green; log `maven-2026-10-23-102006` is the last.

## 2. Personas and skills

- Production code: `persona-implementer`.
- Unit and module tests: `persona-module-tester`.
- Documentation: `persona-documenter`.
- Every self-review round and every PR-comment triage: `persona-code-reviewer`.
- The spec's deliverables name no `Skills:` and no `Persona:` line; **skills: none named**. No skill added for surface drift. The five `doc/plans/*` files were edited only to re-point links at the specification after the package file was deleted (R01, R08).

## 3. Hypotheses

The spec's Claim Labels, re-read at 4617865 and checked here:

- HYPOTHESIS — Expected Surface inferred from the Where line: **confirmed** at the trace matrix. All nine OBSERVED paths exist (`pm-core/src/main/java/de/planmarshall/core/role/` with `package-info.java` and `corpus/`; `pm-workflow/src/main/java/de/planmarshall/workflow/model/` with `package-info.java`; `doc/plans/README.adoc`; the package file; the watch and specification parts). The touched files are those paths plus the two module ADR directories the plan creates and the five plan files whose links pointed at the deleted package file.
- HYPOTHESIS — the package adds INFO/WARN/ERROR messages, log holder deliberately not in the surface: **refuted**. No log record was added; the package logs nothing (R12 not triggered).
- HYPOTHESIS — the package makes no HTTP call, so R03/R04 bind only if the trace matrix shows one: **confirmed**. No HTTP client and no HTTP test; the trace matrix shows no call.
- The OBSERVED claims (the package file's What/Trace/Done When; the index row `open`, depends on nothing; the two package directories; the log holders) were re-read and **corroborated**: the package file is deleted with its row, and the role sets are new beside `corpus/` with no collision.

## 4. Findings

- Self-review round 1 (14 findings, `ec888f9`): fixed the six-level ordering (a project artifact replaces the machine assignment), the class table for the shipped `envelope`/`zero_tool` kinds, the steward routing, the consultation flag, the stale refusal, the default-assignment duplication and the vacuous tests. Left open at that round: the R05 specification deletion (reported, not code) and the pm-runtime recipe rewrite (reverted in `7fc49ef`).
- Self-review round 2 (11 findings, `7752afd`): made an unnamed kind require `read_only`, re-pointed five dangling plan links, corrected the residue statement, added the consultation/resumability/fallback/project-assignment/refused-skill tests, and matched the effort cap to the schema.
- Self-review round 3 (3 findings, `1bf7270`): the registry's `decision` vote kind, the lower resumability bound, and the residue count.
- CodeRabbit review on `7cb1dbd` (10 findings + 1 nitpick, `acda501`): fixed the skills-list guard, the off-track threshold guard, `SIGNALS.keySet()`, the absent catalogue (now `model_not_allowed`), the strict `major.mminor` parse, the Javadoc tags, the deterministic refusal detail, the `ModelJob` class set, and added `HarnessProfileTest`. Two answered with reasons and resolved: the configuration digest's scope (the specification fixes the effective artifact with only harness/model/effort replaced) and the ordering of an unlisted effort (the specification does not place it).
- CodeRabbit review on `acda501` (2 findings, `0e0d44b`): the non-finite threshold and the derived consultation classes; both fixed, the threads auto-resolved.
- SonarCloud: the first PR run was red (`new_reliability_rating` 3) with 3 BUGs and 49 code smells; all fixed or suppressed with a stated reason (`10e2df2`, `7cb1dbd`); the gate is green with 0 open issues.

## 5. Reviewers

- **coderabbit** (required, quota-limited): reviewed `7cb1dbd` (10 findings + nitpick) and `acda501` (2 findings); the current head `2724d34` was **not** reviewed. Standing review authorization applies: a qualifying review exists (`acda501`), every finding is fixed or answered and every thread resolved, the re-trigger after the last push was posted at 10:34Z and refused with `no_unreviewed_commit` ("Already reviewed the last commit"), and every other gate condition holds on `2724d34` (CI green, `verify` green, Sonar green, comments handled). Earlier quota refusals: 05:41Z ("next in 38 minutes"), 08:37Z, 10:13Z; the 07:14Z trigger produced the `7cb1dbd` review.
- **sourcery** (optional, quota-limited): refused on size (the PR exceeds its 150,000-character diff limit) at 05:41Z; no findings.

## 6. Operator questions

1. Asked after the self-review's 3-round limit: whether to proceed to the PR with the three open items reported or run a fourth review round. Options: "Proceed to the PR (Recommended)" and "Run a fourth review round". The operator selected **"Proceed to the PR"**.

Free text from the operator: "retry" (after a truncated tool output; no rule-bending action was taken).

## 7. Rule check

- guard: none on this harness (unenforced).
- **R05 (and the Expected Surface): the deletion of the implemented specification sections and the covered watch items is not carried.** The reason is in the report body: >100 link sites from other specification parts, and `model-work/01` still holds the escalation assignment, the shipped content and the qualification text this package does not implement. Recorded as a residue.
- **Git trailer:** the revert commit `7fc49ef` carries no `Co-Authored-By: plan-marshall <noreply@cuioss.de>` line. It cannot be amended without a force-push, which the lane bans.
- **Worktree rule, during execution:** the implementation files were first written in the main checkout instead of the worktree; they were moved into the worktree before any commit and the main checkout was verified clean (`git status --porcelain` empty) before setup continued. The builds during that window ran against the main checkout once; every subsequent build and every commit used the worktree. Reported for the record.
- No other section-1 rule was broken: no broad staging, no `git stash`/`restore`/`rebase`/force-push, no `.plan/` write outside `.plan/temp/` and the `ci` body slots, no file outside the worktree at commit time, no `gh`/`glab`/build-wrapper/Bash-file-tool use.

## 8. Build server

- Setup preflight: `ready` (the resolve result at the project root said `down`/`unreachable`; the worktree call was authoritative).
- Every build ran **in process**: the worktree's `.plan/execute-script.py` was missing (`executor_absent_from_worktree`), so every routed build failed at the worktree executor (exit 2). All builds ran with `--execution-mode in_process` against the absolute worktree path; `mechanism=in_process_fallback` in each result. No `killed` job.
- Build bound: one `verify` run hit the explicit 617 s bound (tests 5387 passed, none failed) and was re-submitted at 1200 s, where it passed in 396 s; recorded per the client's bound rule.
- Runtime stamp: resolve re-run at landing reported plugin/executor 0.1.1989 (the setup resolve reported 0.1.1986 — the environment was updated during the run); `sonar: not_configured` in the resolve result, so the Sonar check was made through the public API, as R18 directs.

## 9. Residue

- R05's specification-and-watch deletion (section 7).
- The specification does not place an unlisted effort above the range of `headless.model_efforts`; `effortSupported` uses the first listed value as the lower bound. A specification decision is owed before that rule can be tightened (CodeRabbit thread answered, resolved).
- The configuration digest covers the effective artifact as the specification words it (only harness/model/effort replaced); a profile change that keeps the harness major.minor can change the derived context budget or question bounds without changing the digest. Reported as a specification question, withdrawn by the reviewer as out of contract.
- `PM-WATCH-DISP-49` stays in the watch (its matched control, every shipped kind with its shipped role, needs the assembly's shipped role set); `PM-WATCH-DISP-57` stays with PLAN-34.
- Owed to the milestone exit: the concept and user documentation of the slice and the Milestone 4 roadmap check-off.
