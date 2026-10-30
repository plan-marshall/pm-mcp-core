# PLAN-V2-109: Turn the SonarCloud gate of main green: settle java:S9428 at StoreDigest.Builder

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
origin: the red `main` of 2026-10-30 (operator request: "currently main seems to fail")
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-109-store-digest-main-green.md` and is queued as one row file, `queue/PLAN-V2-109.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R07, R08, R11, R14, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R09, R10, R13: no specification, schema or format change; R12: no message is added; R15 and R16: no `pm-workflow` or `pm-runtime` change. R14 because `StoreDigest` is in `core.store`; item (7) of R14 is the one that bites: the Javadoc and the ADR are checked against what the builder does, not against what the code says about itself.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-store.adoc`, only if the decision changes what the guide says; ADR directory: `pm-core/doc/adr/core-store/` (the next free number of the directory at `origin/main`, R11); layout: workstream WS-10.
- R12 — none (no message).

**Read the skills first (R20).** `java-core`, `javadoc` and `cui-logging` (nothing is logged) of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read. The plan changes no test; where `StoreDigestTest` needs a touch, R20 applies to the touched lines.

## Objective

`main` has been red for more than 30 consecutive pushes (the first of them on 2026-10-28), and since 2026-10-30 the reason is one condition alone: the SonarCloud quality gate of `branch=main` fails on `new_reliability_rating` (rated 4, threshold 1) because of **one** bug, `java:S9428` ("Make this MessageDigest a local variable") at `StoreDigest.java:97`. The builds on Java 25 and 26 and the other jobs pass. This plan settles that one issue in the code, with the reason beside the declaration, so the gate of `main` turns green with the merge. It does nothing else: the 12 code smells open on `main` (S1192, S3776, S1135, S112) do not enter a failing condition and belong to PLAN-V2-46, PLAN-V2-81 and PLAN-V2-67.

## Deliverables

1. **`StoreDigest.Builder`: S9428.** `StoreDigest.Builder` holds `private final MessageDigest digest = StoreSnapshot.newSha256();` (line 97) because the builder *is* the incremental digest: `JournalRecovery` (lines 216, 222, 262) and `ScopeTransaction` (line 396) feed it chunks over several calls and read it once at `build()`, with a bounded buffer for files and no chunk kept (its Javadoc states it). A local variable would force the builder to keep its chunks, which changes the documented bounded-memory property and moves the file read from `update` to `build()`. Settle the issue by an inline `@SuppressWarnings("java:S9428")` on the field with the reason beside it ("the builder is the incremental digest of one thread; chunks are not kept"), in the style of `QueueTaskRecordCodec:303` and `ActivityEntryCodec:197`. The Javadoc of the builder says it is confined to one thread and is not shared. No behaviour changes; `StoreDigestTest` stays green unchanged.
2. **The ADR.** One ADR of `core-store` records the choice between the inline suppression and the redesign (a `StoreDigest.compute(consumer)` that keeps the digest local and moves the three call sites), and the fallback of the hypothesis below. Sections of `doc/adr/template.adoc`; the number is the next free one at `origin/main` (R11).

**Done when:** the pull request is green and the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) no longer lists `java:S9428`; the report states `sonar_open_issues` as the count and the rules left open (`unknown` for a failed read, never `0`) and the gate state of the pull request; the ADR is written; `verify -Ppre-commit` (its rewrites reviewed and committed), `verify` of `pm-core` (where a full run does not finish inside the host's bound, the targeted `-Dtest=StoreDigestTest,JournalRecoveryTest,ScopeTransactionTest` run and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main`. **After the merge** (the orchestrator's check at `analyze`, not the lane's): the gate `https://sonarcloud.io/api/qualitygates/project_status?projectKey=plan-marshall_pm-mcp-core&branch=main` reads `OK` after the first analysis of `main` and the `Maven Build` of that push is green.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Default and its alternatives.** The default is the inline suppression with a stated reason (as PLAN-V2-67 D1 states it). The redesign grows the Expected Surface by `JournalRecovery`, `ScopeTransaction` and their tests: ask the operator at refine (R07), do not decide silently.
- **If the analysis does not honour the suppression** (the hypothesis below is refuted at the run of the pull request): do not reshape the design silently and do not merge on a red `branch=main` gate. Where `runbook_lane resolve` reports `sonar: configured`, dismiss the issue with a stated reason, recorded as `{issue-key}: {reason}` in the run's progress record, through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition wontfix`; a dismissal the run cannot transmit is a blocker reported through the inbox message, never a silent pass.
- **Scope is the one issue.** Do not fix the other open Sonar issues of `main` here, and do not touch the analyzer error `Unable to run check class org.sonar.java.se.SymbolicExecutionVisitor ... StoreSnapshot.java` that the same log shows: it does not fail the gate. Report it in the landing as an observation.
- **No credential is assumed for the write API;** the suppression in the code is the settlement, the dismissal only the fallback above.

## Claim Labels

- OBSERVED: `main` is red on every push since at least 2026-10-28 (30 consecutive failed `Maven Build` runs, newest 41038751563 at `132a7a0c`); in that run the only failing job is `build / sonar-build` (step `Build and analyze`), while `build (25)`, `build (26)` and `quarkus-alignment` pass, and its log ends `QUALITY GATE STATUS: FAILED - View details on https://sonarcloud.io/dashboard?id=plan-marshall_pm-mcp-core&branch=main`
- OBSERVED: the gate `qualitygates/project_status?projectKey=plan-marshall_pm-mcp-core&branch=main` reads `ERROR` with exactly one failing condition, `new_reliability_rating` (actual 4, threshold 1); `new_security_rating`, `new_maintainability_rating`, `new_coverage` (90.2), `new_duplicated_lines_density` (0.3) and `new_security_hotspots_reviewed` (100) are `OK`; the new-code period is `previous_version` since 2026-10-09
- OBSERVED: `issues/search?componentKeys=plan-marshall_pm-mcp-core&branch=main&resolved=false` lists 13 issues, one of type `BUG` — `java:S9428` at `StoreDigest.java:97`, created 2026-10-11, inside the new-code period — and 12 `CODE_SMELL`s (S1192 x4, S3776, S1135 x6, S112)
- OBSERVED: `StoreDigest.Builder` is used incrementally at `JournalRecovery.java:216`, `:222`, `:262` and `ScopeTransaction.java:396`; the module already suppresses Sonar rules inline with a reason (`ActivityEntry.java:330`, `ActivityEntryCodec.java:197`, `QueueTaskRecordCodec.java:303`, `RoleSets.java:48`)
- HYPOTHESIS: the analysis honours `@SuppressWarnings("java:S9428")` as it does the `java:S3776` ones in the same module — confirm/refute at the run of the pull request (R18); the fallback is in Standing Constraints
- HYPOTHESIS: with `java:S9428` gone, the rating of the new code is A and the gate of `main` turns green with nothing else changed — confirm/refute at the first analysis of `main` after the merge
- Verify-first clause: before editing, list the open pull requests for one that touches `StoreDigest.java`, `JournalRecovery.java` or `ScopeTransaction.java`, and re-read the gate and the issue list of `main` (a plan that landed meanwhile may have cleared the bug or added another one — PLAN-V2-46, PLAN-V2-81 and PLAN-V2-67 also work Sonar issues of `main`); a refutation loops back to re-scope

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/store/StoreDigest.java` — the `Builder` field (line 97) and the Javadoc of `Builder`

Not surface (R19): the ADR directory `pm-core/doc/adr/core-store/` and the developer guide `pm-core/doc/developer/core-store.adoc` of the existing package as additive bookkeeping, `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`.

## Dependencies and Sequencing

- Depends on: none. This departs on purpose from the epic's wave-0 rule that every plan follows PLAN-V2-83: the plan adds one annotation and an ADR, uses none of the dependencies PLAN-V2-83 adds, and a red `main` is the reason to run it now. R20 still applies to the lines it touches.
- Overlaps with: PLAN-V2-83 (running) — none; it declares POMs, `CLAUDE.md` and the repository ADR index. PLAN-V2-67 D1 settles the same issue: PLAN-V2-67 drops D1 when this plan lands (its verify-first clause re-reads the Sonar list). PLAN-V2-100 declares `StoreDigest.java` for the builders-for-records rule — one shared file, and `StoreDigest` has two components, so it carries no `@Builder`.
- Adjacent to: `JournalRecovery` and `ScopeTransaction` (the callers), untouched unless the operator chooses the redesign at refine.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-109-store-digest-main-green.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
