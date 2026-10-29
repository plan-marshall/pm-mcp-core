# PLAN-112: Hold a profile's bounded wait to the specified 30-second margin below the idle timeout

epic: pm-mcp-core-m2-m4
workstream: WS-05
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-112-profile-wait-margin.md` and is queued as one row file, `queue/PLAN-112.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R13, R17, R18, R19. (R10 because several tests read specification parts; this plan edits none; R12, R14, R15 and R16 are dropped, no message is added and no `core.store`, `pm-workflow` or `pm-runtime` class changes; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-core/doc/developer/core-profile.adoc`; ADR directory: `pm-core/doc/adr/core-profile/` (the next free number); layout: workstream WS-05.

## Objective

The specification forbids a client profile whose host does not reset its idle timeout on progress and whose bounded wait is longer than `connection.idle_timeout_seconds − 30` (Timeouts & Budgets § Bounded Wait Window: "profile validation also rejects a value above `connection.idle_timeout_seconds − 30`"; § Host Integration says the same). `ProfileValidator.connection` rejects only `bounded >= idle`, so a profile with a bounded wait inside the last 30 seconds below the idle timeout is accepted by the loader and refused later, when `ConnectionSettings` of `pm-workflow` (which enforces the specified margin) builds a wait. Close the gap in the validator, and correct the comment of the same class that still names the closed package `token-usage`. The plan changes `ProfileValidator`, which the staged plan `machine-harness-verification` also changes: it runs after that plan.

## Deliverables

1. **The margin in `ProfileValidator.connection`.** For `progress_resets_timeout: false`, refuse `bounded_wait_seconds > idle_timeout_seconds − 30` with the same refusal shape as today (`invalid(pointer + "/bounded_wait_seconds", ...)`, the message naming the bounded wait, the idle timeout and the margin 30). The margin is a named constant of the validator; `ConnectionSettings.IDLE_MARGIN` (`pm-workflow`, 30 seconds) cannot be shared (`pm-workflow` depends on `pm-core`, not the reverse), so the duplication is recorded in the ADR of Deliverable 3. A bounded wait equal to `idle − 30` stays valid (the specification says "above"). Test first, in `ProfileValidatorTest`: the existing case `bounded 240 / idle 240` is kept (it now fails by the margin, not by equality), and the boundary is pinned both ways, `bounded = idle − 30` accepted and `bounded = idle − 29` refused, for `progress_resets_timeout: false`; `progress_resets_timeout: true` accepts any bounded wait inside the schema's own bounds (15 to 3600), with a case at `bounded > idle − 30`.
2. **The shipped values and the fixtures.** The compiled values of the specification table (Client Profiles § Worker Block: idle 1800 / 60 / 180 / 270 with bounded 240 / 240 / 150 / 240 and progress true / true / false / false) satisfy the rule (150 ≤ 150, 240 ≤ 240). Re-read the table at the outline and run every profile fixture of `pm-core` (`ProfileFixtures`, `WorkerParametersTest` sets `bounded_wait_seconds` 120, the `ProfileLoader` tests) through the new rule; a fixture that now fails is corrected in the test, never by weakening the rule. The profile files of the product are in `plan-marshall-mcp` (the three shipped profiles and their overlays): list in the pull request body that the new rule holds for them or which does not, as a finding for that repository.
3. **The comment and the decision.** The Javadoc of `ProfileValidator.usage` (line 933) says the usage block is "`token-usage`'s", a package PLAN-107 closed (#215): reword it to name the rule's home, Client Profiles § Tokens, Not Money (`headless.usage`: "`input_tokens`, `output_tokens` and `tool_uses` are never `not_applicable`"). One ADR in `pm-core/doc/adr/core-profile/` (next free number; Status, Date, Context, Decision, Alternatives considered, Consequences, Source) records that the validator enforces the margin the specification names, the duplication with `ConnectionSettings`, and the alternative (leave the check to `ConnectionSettings`, so that a profile the loader accepts is refused at the wait: rejected because a profile is valid or not at load, Client Profiles § A Profile Is Untrusted Input). Extend `core-profile.adoc` where it lists the connection rules.

**Done when:** a profile with `progress_resets_timeout: false` and a bounded wait above `idle − 30` is refused at load with the pointer `/connection/bounded_wait_seconds`, a bounded wait of exactly `idle − 30` is accepted, and a profile with progress frames is unchanged; every shipped value of the specification table and every fixture passes; the comment names a section that exists; the ADR and the developer documentation are written; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of every module the plan changes (`pm-core`; where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** PM-CLIENT-5 (Client Profiles § Worker Block rules; the closed schema and the hardening rules); Timeouts & Budgets § Bounded Wait Window (`doc/specification/timeouts/01-job-budgets-and-waits.adoc` line 206) and § Host Integration (`timeouts/03-retention-host-config.adoc` line 97). The lead is the lane report of PLAN-25 (`landings/PLAN-25.md`).

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **The specification is the authority; it is not edited.** `client-profiles/01-profile-schema.adoc` and `timeouts/*.adoc` already state the rule; a finding that the specification and `ConnectionSettings` differ is raised in the inbox message.
- **The plan starts from the head of `main` after `machine-harness-verification` has merged**, and re-reads `ProfileValidator` and `ProfileValidatorTest` there (that plan adds `readable_paths` and the credential rule to both).

## Claim Labels

- OBSERVED: `ProfileValidator.connection` reads idle (45 to 7200), bounded (15 to 3600) and the progress flag, and refuses only `!progress && bounded >= idle` — `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` lines 1114 to 1131 (the condition at line 1127)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: read at a8da4104
- OBSERVED: the specification requires the margin — `doc/specification/timeouts/01-job-budgets-and-waits.adoc` line 206 ("for a host whose `connection.progress_resets_timeout` is false, profile validation also rejects a value above `connection.idle_timeout_seconds − 30`") and `timeouts/03-retention-host-config.adoc` line 97; `ConnectionSettings` enforces it (`pm-workflow/src/main/java/de/planmarshall/workflow/engine/ConnectionSettings.java` lines 39 and 56, `IDLE_MARGIN` 30 seconds, `boundedWait > idleTimeout − IDLE_MARGIN`)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: read at a8da4104; the lead had been "not checked against the validator" by the orchestrator
- OBSERVED: the test for the rule is `ProfileValidatorTest.java` lines 179 to 183 (idle 240, bounded 240, progress false); `ProfileFixtures.java` lines 185 and 186 build the valid profile with idle 1800 and bounded 240; `WorkerParametersTest.java` line 102 sets bounded 120
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: `grep bounded_wait_seconds` over `pm-*/src/test`
- OBSERVED: the compiled values of the specification table satisfy the margin — `client-profiles/01-profile-schema.adoc` lines 768 to 770: idle 1800 / 60 / 180 / 270, bounded 240 / 240 / 150 / 240, progress true / true / false / false
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: read; (150 ≤ 180 − 30, 240 ≤ 270 − 30)
- OBSERVED: the stale comment — `ProfileValidator.java` lines 933 to 936 ("The usage block is {@code token-usage}'s, including the rule that {@code input_tokens} and {@code output_tokens} are never {@code not_applicable}"); the rule is in `client-profiles/01-profile-schema.adoc` line 315 (`input_tokens, output_tokens and tool_uses are never not_applicable`)
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: read
- HYPOTHESIS: the three shipped profiles of `plan-marshall-mcp` and their machine overrides satisfy the margin — confirm/refute at the outline by reading the sibling checkout (which may be behind its `origin/main`); a violation is a finding for that repository
  - verdict: unverifiable | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: hypothesis, not read
- Verify-first clause: before editing, confirm that `machine-harness-verification` has merged (`doc/plans/README.adoc`, no row), list the open pull requests for one that touches `ProfileValidator.java`, and re-read lines 1114 to 1131 at the head; a refutation loops back to re-scope.
  - verdict: corroborated | checked_at: a8da4104 | by: pm-mcp-core-m2-m4/spec-residues-draft | evidence: PLAN-108 is staged and declares `ProfileValidator.java` and `ProfileValidatorTest.java`

## Expected Surface

- OBSERVED: `pm-core/src/main/java/de/planmarshall/core/profile/ProfileValidator.java` — `connection` (D1), the Javadoc of `usage` (D3); shared with PLAN-108
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/profile/ProfileValidatorTest.java` — the boundary cases (D1); shared with PLAN-108
- OBSERVED: `pm-core/src/test/java/de/planmarshall/core/profile/ProfileFixtures.java` and `WorkerParametersTest.java` — only if a fixture fails the new rule (D2)
- OBSERVED: `pm-core/doc/developer/core-profile.adoc` — developer documentation, extended
- HYPOTHESIS: `pm-core/doc/adr/core-profile/` — one ADR, the next free number of the directory

Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, the ADR directory and developer guide above as bookkeeping where they exist.

## Dependencies and Sequencing

- Depends on: PLAN-108 (`machine-harness-verification`): it declares `ProfileValidator.java` and `ProfileValidatorTest.java` (2 shared entries), and PLAN-108 itself waits for PLAN-46 (running); PLAN-46 and PLAN-109 name `ProfileValidator` in their text but not in their Expected Surface. PLAN-98, which lifted the refusals of the validator, is shipped.
- Sequenced 2026-10-29 by the cross-check: `ProfileValidator` is shared ground; the group PLAN-112, 145, 133 and 141 runs after PLAN-108, one at a time in that order (or each merges the base before its gate).
- Overlaps with the live plans (class level): the two entries above with PLAN-108 only.
- Disjoint from the other drafts of this cluster (no shared class): PLAN-114 (`runtime-endpoint-residues`), PLAN-113 (`qualification-token-split`), PLAN-110 (`corpus-schema-length-bounds`), PLAN-111 (`main-hygiene-leftovers`).
- Adjacent to: `ConnectionSettings` of `pm-workflow` (unchanged) and the profile files of `plan-marshall-mcp` (untouched).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-112-profile-wait-margin.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
