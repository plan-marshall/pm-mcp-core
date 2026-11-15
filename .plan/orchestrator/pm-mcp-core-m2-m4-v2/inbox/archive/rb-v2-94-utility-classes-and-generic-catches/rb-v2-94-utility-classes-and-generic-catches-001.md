envelope_version=1
sender_type=plan
sender_id=rb-v2-94-utility-classes-and-generic-catches
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-15T18:25:01Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-94-utility-classes-and-generic-catches
epic=pm-mcp-core-m2-m4-v2
pr=#437
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
review_body_findings_total=0
review_body_findings_handled=0
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-94 — utility classes and generic catches — landed

PR #437, merge commit `27d28bb7f83e6d0f129a7ff014a542aaafd56533`, merged through the merge queue. Branch `feature/v2-94-utility-classes-and-generic-catches`, base `main`.

Standing rules: r7, R02 R06 R08 R11 R14 R17 R18 R19 R20. The spec's First Instruction names r4; the file is at r7 (2026-11-10), so r7 is what was read and applied.

## Deliverables

### D1 — `@UtilityClass` for the static-only classes: met

61 classes converted (pm-core 20, pm-workflow 32, pm-runtime 5, pm-provider-git 3, pm-provider-sonar 1); `SecretRedactor` and `ProfileValidator` held out per the State section; the nested `INFO`/`WARN`/`ERROR` holders of `WorkflowLogMessages` annotated like `CoreLogMessages`, and the five static-import files switched to plain imports.

- Done-when "the `private [A-Z]…()` scan finds only the constructors listed with a reason": met. Scan re-run at the merged head `7c18690a`: 14 sites — `ProfileValidator:272` and `SecretRedactor:323` (held out), `CorpusValidator.Schemas:498`, `GradingOffer.ExpectedTokens:118`, `GitHubOperation.Permission:67`, `CloneDetector.Tokenizer:167`, `PrimitiveResult.Builder:122`, `StoreDigest.Builder:102`, `ToonValue.Builder:180`, `PlanFacts.Builder:365`, `AsciiDocTableRenderer:59`, `InstanceAsciiDocTableRenderer:41`, `MermaidRenderer:60`, `WorkflowPrinter:73`; every one is listed with its reason in the PR body, in the same `file:line`. Test name: none (no test asserts the scan); `order: none`; no failing run is claimed.
- Commit `f476dd6f` (66 files) and the style fix `54de43bf` (3 files).

### D2 — the generic catches of `pm-runtime` that no other plan owns: met

One code change: `@SuppressWarnings("java:S1181")` with its reason at `SystemProcessExecutables.java:157`, the only downcall that lacked it. The other eleven kept sites are listed in the PR body with the sentence each relies on; PLAN-V2-46's six `TODO: Catch specific` sites and PLAN-V2-67's `throws Throwable` are recorded as not this plan's.

- Done-when "the `catch (RuntimeException|Throwable|Exception)` scan of `pm-runtime` main finds only the sites listed in the pull request body with their reason": met. Scan at `7c18690a`: 18 sites, all listed (12 kept + 6 PLAN-V2-46). Test name: none — no catch's behaviour changed, and the State section records that no test has to change; `order: none`.
- Done-when "the six `TODO: Catch specific` markers are not this plan's": met — six remain, all in PLAN-V2-46's four endpoint files.
- Done-when "a catch this plan changes has a test of the failure it handles": met vacuously — the only catch edit is an annotation on an unchanged `catch (Throwable t)`; no failing run is claimed, `order: none`.
- Done-when "Sonar: no open issue other than one dismissed with its reason, and the gate is green, before the PR is reported ready": met — `read_open_issues --pr 437 --ce-wait-timeout 60` at `7c18690a`: `count_status: confirmed`, `open_issue_count: 0`; `sonar_rest gate-status`: `OK`; `sonar-gate`: `merge_allowed: true`.
- Done-when "`verify -Ppre-commit`, `verify`, `verify -Pcoverage`, and `verify -Pintegration-tests -pl <module> -am` for pm-core and pm-runtime are green": met — `verify -Ppre-commit` 10555 tests at `d28ff034` and 10613 at `7c18690a`; `verify` 10555 at `d28ff034`; `verify -Pcoverage` 10136 at `d28ff034` and 10555 at `54de43bf`; integration pm-core 21, pm-runtime 43 at `d28ff034`; the head CI run at `7c18690a` re-runs build, sonar-build and integration-tests green. `order: none`.
- Done-when "the links check (R17)": met — `BROKEN LINKS 0`, run from the main checkout against `plan-marshall-documentation` at `origin/main` `4dd67ea6` (the links check's own `trace.py`, the one command R17 names).
- Commit `d28ff034`.

Per-head test runs: `d28ff034` (10555), `54de43bf` (10555), `7c18690a` (10613).

## Personas and skills

- Implementer (`persona-implementer`) for both deliverables, as each spec deliverable declares `Persona: implementer`.
- D1 skills: `pm-dev-java:java-lombok`, `pm-dev-java:java-core`, `pm-dev-java:java-null-safety`. D2 skills: `pm-dev-java:java-core`, `pm-dev-java:javadoc`, `pm-dev-java:junit-core`. All loaded as standards before the work. `persona-code-reviewer` for every self-review round.
- `skills: none named` does not apply: both deliverables declare their skills. No skill was added for surface drift; the surface is exactly the declared one.
- The R20 reading (First Instruction) covered the plan's own declared sets; the cui-logging/cui-testing/cui-http-testing/junit-integration skills are not used by this plan (no logging, HTTP or integration-test change) and were not read.

## Hypotheses

- "63 static-only classes with a hand-written private constructor" — confirmed by the scan of the worktree at setup (22 core incl. the 2 held out, 32 workflow, 5 runtime, 3 git, 1 sonar); the four extra workflow classes are the State's instance-member non-candidates (`MermaidRenderer.java:60` holds `private final StringBuilder out`, `AsciiDocTableRenderer.java:59`, `InstanceAsciiDocTableRenderer.java:41`, `WorkflowPrinter.java:73`).
- "The line numbers moved when PLAN-V2-84 landed — find each site again" — confirmed: the catches sit at `SubprocessJobRunner.java:1145,1209,1458,1467,1493,1788` and `WorkerSupervisor.java:325`, not the Claim Labels' numbers; `ProfileValidator` and `SecretRedactor` moved again with the base merge to `:272` and `:323`.
- "The tests of the changed catches (none expected)" — confirmed: none had to change; no assertion was removed or relaxed anywhere in the diff.

## Findings

Self-review, three rounds, each on the head of its time, all with `persona-code-reviewer` and adversarial-refute lenses:

- Round 1 (8 findings): fixed the `@UtilityClass`/`@SuppressWarnings` adjacency in `RoleArtifactValidator` and the annotation form in `HeaderSchemaCheck`/`DeclarationBounds` (commit `54de43bf`). Report-only: ADR drift, the false "no mocking library" premise, the D2 body-vs-Done-when wording on the six markers, and the 65 out-of-surface test-tree constructors.
- Round 2 (1 open finding): the shared `NO_PLAN` body slot had been overwritten by a concurrent lane; `ci pr view --pr-number 437` proved #437 carried this plan's own body. Also required a coverage re-run at the head (done, green).
- Round 3: **open findings 0** — self-review clear.
- PR comments: no reviewer thread and no review-body finding (CodeRabbit's review carried none); `review-gate` reports 0 unresolved threads, 0 body findings.

## Reviewers

- **coderabbit** (required, `quota_limited: true`, `review_complete_after_handling: true`): refused on quota four times (10:45Z, 12:16Z, 13:47Z, 15:31Z) with three triggers including the escalated `@coderabbitai full review`, each after the mandated 90-minute wait; the review landed at 17:40Z on `54de43bf` (check SUCCESS, 0 findings). It is the qualifying review that `review-gate` credits at any head of this PR, so no re-trigger was posted after the merge-base push. The operator was asked at the fourth refusal and chose "Keep waiting and retrying".
- **sourcery** (optional, `quota_limited: true`): refused on its 7-day budget (≈23 h); no review exists and none is required. Recorded, not re-triggered.

## Operator questions

1. "The executor-skew witness reports `skew` … How should I proceed?" — options: "Regenerate the executor, then continue" / "Stop the run as blocked"; selected **"Regenerate the executor, then continue"**. `generate_executor preflight` regenerated `.plan/execute-script.py` 0.1.2123 → 0.1.2133; the verdict then read `match`.
2. "Required reviewer quota — codrabbit has not reviewed any head … How should I proceed?" — options: "Stop here, keep #437 open" / "Keep waiting and retrying"; selected **"Keep waiting and retrying"**.

No free-text operator messages were sent.

## Rule check

- guard: none on this harness (unenforced).
- Spec read: `spec_source: ledger_worktree`; no `spec_differ` marker.
- Executor skew: `skew` at the start (executor 0.1.2123 vs lane 0.1.2133), operator-answered, executor regenerated, then `match`; the landing-time resolve reports `executor_version: 0.1.2136`.
- One rule was not kept, by a sub-agent, not by the lane: the explore sub-agent that recovered the spec's 6805-character "State at the emit" line (the read tool truncates a line at 2000 characters) used one non-mutating `awk` substring print on that file instead of a file tool. The lane's own reads and searches used the file tools; the recovery was read-only and is how the binding class list and the D2 decisions reached this run. Recorded here as the single deviation.
- No shell file-listing or file-reading command was issued by the lane itself; `git -C … diff/status/fetch/merge` and `date` were used, which the Git rule and the build rules allow.
- R17 required reading the sibling documentation checkout (outside this repository) — done with the exact command R17 names, plus read-only `git -C` on that checkout to confirm it sits at its `origin/main`.
- No `sleep`, no `gh`/`glab`, no direct Maven invocation, no `python3 -c`.

## Build server

- Setup preflight: `ready` (registered). Every build used the default routing; each result's client line reports `resolved=routed`, `mechanism=daemon_longpoll`.
- Builds: 7 routed in total — quality-gate (one failed compile, the missing `HEADING` constant, fixed at once), quality-gate green at `d28ff034`, `verify`, `verify -Pcoverage`, integration pm-core, integration pm-runtime, quality-gate at `54de43bf`, coverage at `54de43bf`, quality-gate at `7c18690a`. The quality-gate at `54de43bf` came back `status: running` after the client's 300-second wait and was re-attached with a bounded wait (`build_server wait --job-id 04e67b07…` → success, 299 s); that is a bounded re-attach, not a fallback. No degraded result, no launch-fault retry, no killed job.
- `runtime_stamp_reason`: none carried.

## Residue

- **ADR drift (reported, outside the plan's R06 surface):** `pm-workflow/doc/adr/workflow-dsl/005-frontmatter-read-by-its-own-line-parser.adoc:26` still calls `UnitFrontmatterReader` "a plain final class with a private constructor, because `pm-workflow` does not use Lombok" — false twice now (the class is `@UtilityClass`; the module uses Lombok since PLAN-V2-83). A later plan should correct it.
- **JobSettlement I1 attribution:** the PR body records the carry and the binding State's decision to keep the site; the Javadoc's phrase "the last-resort handler the specification names" is what PLAN-V2-127's I1 calls unsupported. No edit made (the file is outside this plan's Expected Surface).
- **`WorkerSupervisor.java:321-325`:** the generic catch releases the lease and rethrows; a `finally` guarded by a "kept" flag would remove it with no behaviour change. The State keeps it as-is; a later plan may take the rewrite.
- **The spec's "the repository has no mocking library" premise is false** (EasyMock is declared in ten poms); the conclusion holds because all 63 classes were already `final`, and EasyMock cannot mock a final class.
- **`SecretRedactor`'s conversion has no owner after PLAN-V2-143 landed**: the State carries it to PLAN-V2-59; ProfileValidator's own plan (PLAN-V2-63) converts it. Worth a queue annotation at the epic level.
- **65 hand-written private constructors in 64 test-tree classes** remain (out of the Done-when's `src/main` pathspec); PLAN-V2-126 owns them.
- `plugin_version` is `unknown` — the platform-runtime seam returned no bundle version.
