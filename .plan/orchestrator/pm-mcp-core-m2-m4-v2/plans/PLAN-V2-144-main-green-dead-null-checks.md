# PLAN-V2-144: Turn the gate of `main` green again: remove the two dead null checks PLAN-V2-129 left in `EndpointVerification`

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
origin: the issues pass of 2026-11-14 (a read-only leaf, re-read by the orchestrator): the SonarCloud gate of `main` is `ERROR` (`new_reliability_rating` 3 against 1) because of one open BUG, `java:S2583` at `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/EndpointVerification.java:298`, and the smell `java:S2589` at `:126`; PLAN-V2-129 (#396, `58b921af`) made `EndpointProfile.executable` and `identity` non-null, which made both checks dead; the operator's rule of 2026-10-31 ("fix the dead null checks at the sites, do not ignore the rules", PLAN-V2-110 and 112) applies; precedent: PLAN-V2-112 `main-green-reliability-bugs`
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-144-main-green-dead-null-checks.md` and is queued as one row file, `queue/PLAN-V2-144.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R06, R08, R17, R18, R19, R20. (R01: no work package covers this plan; R03, R04: no HTTP; R05, R10: no specification part is edited; R07: no ask-at-refine item; R09: no confinement; R11: no ADR (no design decision); R12: no message; R13: no stored format changes; R14, R15, R16: no protocol or behaviour change.)

Plan parameters: R06 — developer documentation: none changes (no behaviour is described differently; say so in the pull request body); layout: workstream WS-10.

**Read the skills first (R20).** `java-core`, `java-null-safety`, `junit-core` and `cui-testing` of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read.

## Objective

Since 2026-11-12 every push to `main` fails the job `sonar-build` (the gate condition `new_reliability_rating`), and the job `deploy-snapshot`, which needs it, is skipped: the SNAPSHOT of this repository, which `plan-marshall-mcp` and the clients build against, has not been deployed since PLAN-V2-129 merged. The cause is two checks of a value that cannot be null: `EndpointProfile` requires `executable` and `identity` non-null in its compact constructor (`pm-core/src/main/java/de/planmarshall/core/profile/EndpointProfile.java:50-51`), and `EndpointVerification` still tests them. This plan removes the two dead checks and nothing else.

## Deliverables

1. **Remove the two dead checks (one commit).** `EndpointVerification.ownsTheNamedProgram` (`EndpointVerification.java:123-128`) keeps `!lookup.isFound()` and drops `|| profile.executable() == null` (S2589, `:126`); `EndpointVerification.probe` (`:296-302`) drops the `if (identity == null) { return Optional.empty(); }` branch and calls the version probe directly with `profile.identity()` (S2583, `:298`); a local that only served the dead branch goes with it. The behaviour is unchanged for every `EndpointProfile` that can exist. **Test order:** `none` (a refactor: the guard is the unchanged tests); `EndpointVerificationTest` is not edited, and the plan states in its progress note that no test constructs an `EndpointProfile` with a null `executable` or `identity` (the record refuses it, `EndpointProfile.java:50-51`; if a test does, it already fails today).
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **Prove the gate (no commit).** After the pull request is merged, read the gate of `main` through the public API (`https://sonarcloud.io/api/qualitygates/project_status?projectKey=plan-marshall_pm-mcp-core&branch=main`) and the job list of the `Maven Build` run of the merge commit (`deploy-snapshot` ran, `sonar-build` succeeded), and put both in the landing message. If the gate is still `ERROR` the landing says which condition and which issue remain (a new issue created since 2026-11-14) and the plan stops: it does not widen its scope.

**Done when:** `EndpointVerification.java` holds neither check; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed) and `verify -Pcoverage` are green; `git diff --stat` shows `EndpointVerification.java` alone (no test, no `pom.xml`); **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue and its quality gate is green; after the merge the gate of `main` is `OK` and `deploy-snapshot` ran.

## Standing Constraints

- **Do not ignore, suppress or dismiss a rule** (operator, 2026-10-31); no `@SuppressWarnings`, no `sonar.issue.ignore` property, no `pom.xml` change.
- **Nothing else changes.** The other open issues of `main` have owners (PLAN-V2-46: the six `java:S1135` and `java:S3776`; PLAN-V2-67: `java:S112` and the four singles; PLAN-V2-114 and 125: `java:S5778`); this plan does not touch them, not even the three `java:S1135` in this file.
- No dependency, no plugin. Where the project analyses its pull requests with Sonar, read it over plain HTTP (`pullRequest=N`, never `branch=<feature>`).

## Claim Labels

- OBSERVED: `EndpointVerification.java:126` (`profile.executable() == null`) and `:298` (`identity == null`) at `32a711f9`; `EndpointProfile.java:50-51` (`requireNonNull(executable)`, `requireNonNull(identity)`); both made so by #396.
- OBSERVED: the gate of `main` is `ERROR`, the only failing condition is `new_reliability_rating` (3 against 1), the open BUG is `java:S2583` at `:298` (created 2026-10-28), read by the orchestrator on 2026-11-14; `sonar-build` is `failure` and `deploy-snapshot` is `skipped` in the `Maven Build` runs of `32a711f9` (43529331749) and `6aa55a56` (43501954691).

## Expected Surface

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/EndpointVerification.java` — the two checks (D1)
- OBSERVED, read not edited: `pm-core/src/main/java/de/planmarshall/core/profile/EndpointProfile.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/endpoint/EndpointVerificationTest.java`

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`. No `pom.xml`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-129 (shipped). Emit it first among the plans that edit `pm-runtime`'s `endpoint` package: **PLAN-V2-46** (staged; D2 and D3 edit `EndpointVerification`, `AnswerSchema`, `EndpointHarness`, `WeightsReader`) runs after it and re-reads the file.
- Overlaps at class level: PLAN-V2-46 (above), PLAN-V2-114 (`EndpointVerification` is on its surface for item 10), PLAN-V2-125 and 126 (tests only; this plan edits none).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-144-main-green-dead-null-checks.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
