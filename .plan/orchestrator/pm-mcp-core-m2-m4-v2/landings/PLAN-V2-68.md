# Landing Analysis: PLAN-V2-68 — a profile's bounded wait is held to the 30-second margin below the idle timeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #426 (merge `6bd6daac`), `merged` through the merge queue, no label

> Landing record for one shipped plan, written by the `analyze` inbox drain of 2026-11-14 from the message
> `rb-v2-68-profile-wait-margin-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high,
> executor 0.1.2125, standing rules r7). The message is a lead. The three layers were read by one read-only leaf from
> git at `origin/main` `6bd6daac` (no build, no test run); the orchestrator re-read the condition, the message, the
> tests and the five shipped `pm-api` profiles; the provider figures were re-read from the SonarCloud public API.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 the margin in `ProfileValidator.connection` | met | re-read: `ProfileValidator.java:45` `IDLE_MARGIN_SECONDS = 30` (private); `:1046` `!progress && bounded > idle - IDLE_MARGIN_SECONDS` throws `invalid(pointer + "/bounded_wait_seconds", …)` (`:1047-1049`); `>` so `idle − 30` loads; agrees with `ConnectionSettings.java:56`. Tests: the 240/240 case kept (only its `because` label changed), `idle 300 / bounded 271` refused (`ProfileValidatorTest.java:185-190`), `marginBoundaryAccepted` (`:407-435`) pins 270 accepted, progress true 240/240 and bounded 3,600 with progress. |
| D2 shipped values and fixtures | met | no fixture changed; `ProfileFixtures` and `WorkerParametersTest` cannot fail the rule (progress true). The lane's "finding for `plan-marshall-mcp`" is moot: the profiles are `pm-api` resources, and re-read at `pm-mcp-clients` `origin/main` (`6fa8c512`): claude 1800/240/true, opencode 60/240/true, antigravity 180/150/false (150 ≤ 150), codex 270/240/false (240 ≤ 240), neutral 60/30/true; all satisfy the margin. |
| D3 comment, ADR, guide | met | `usage` Javadoc (`ProfileValidator.java:851-855`) names Harness Profiles § Tokens, Not Money and the `headless.usage` pointers; ADR `core-profile/015` (next free; Status, Date, Context, Decision, Alternatives Considered, Consequences, Source; records the duplication with `ConnectionSettings.IDLE_MARGIN` and two rejected alternatives); `core-profile.adoc:120-126` connection-rules paragraph. |

Done-when: all clauses met. The diff is four files, all in the Expected Surface (124 insertions, 8 deletions); no existing assertion removed or relaxed. Sonar `pullRequest=426`: 0 open, gate `OK` (re-read); main gate still `ERROR` until PLAN-V2-144 merges.

## Three-Layer Analysis

### Layer 1 — Specified
Coherent and grounded (spec: `timeouts/01-job-budgets-and-waits.adoc:199`, `03-retention-harness-config.adoc:97`). Left open and decided by the lane: the refusal wording, the constant's name and visibility, placement inside `connection`, a second rejected alternative in the ADR ("move the margin to pm-core"). Stale spec text the lane had to read around: "shared with PLAN-V2-39" in the Expected Surface, "Client Profiles §" for the section name, "profiles of `plan-marshall-mcp`" for `pm-api`.

### Layer 2 — Implemented
As the table; no gaps against the Done-when.

### Layer 3 — Correct
No behavioural bug. The margin agrees with `ConnectionSettings`; nothing else in `pm-core`, `pm-workflow` or `pm-runtime` validates the pair (`WorkerParameters` only reads; `machine-config.schema.json` states an override passes the same validation, and the override is not implemented). Minor residue:

- the refusal message ("is not 30 seconds below the idle timeout") reads as "not exactly 30 below" and says nothing of "at least"; it is asserted nowhere (the lane's F7);
- the three acceptance assertions are all `assertEquals("acme-cli", clientId(...))`, which proves that no exception was thrown and little else;
- `ProfileValidatorTest.java:184` is about 126 characters;
- "host" in the Javadoc of `IDLE_MARGIN_SECONDS` and in ADR 015 (`harness-renames`);
- `pm-core/README.adoc` lists ADRs 005 and 010-012 only (013, 014 and now 015 missing; pre-existing gap).

## Conformance to the Project's Own Rules
Assertion messages 38-45 characters; `@DisplayName`, no AAA comments; literals are contract values and the class says so. The lane's rule check lists three `ls` calls (read-class violation of "use the file tools"), the executor skew answered by regeneration, and Sourcery refused on quota (optional). CodeRabbit: 0 actionable. `ProfileValidatorTest` is 569 lines (R20 reports, does not enforce).

## Leftovers
All five residue items above go into PLAN-V2-71 (same class and test), the "host" wording and the README gap into its documentation deliverable.

## Unverified leads
Test-first failing run and the green runs (job logs not read); the link check; `PM-CLIENT-5` traceability rows (R19: not in the surface).
