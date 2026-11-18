# Landing Analysis: PLAN-V2-17 — The project onboarding unit: framework-free primitives

epic: pm-mcp-core-m2-m4-v2
workstream: WS-04
pr: #496 (`9695e3f4`, merged through the merge queue 2026-11-18, runbook lane, opencode, `rb-v2-17-project-onboarding-unit`)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message `rb-v2-17-project-onboarding-unit-002.md`, which supersedes `-001`, archived on 2026-11-18 as a Watch) after verifying claims against git, the PR and one read-only three-layer leaf; a pasted claim is a lead, never a fact.

## Deliverable Fidelity vs Spec

`deliverables_done=1` of 8: the spec asks this repository for what its own Placement section (operator decision 2026-11-09) puts in `plan-marshall-mcp`; the lane built the framework-free primitives and reported the rest not met. **The shortfall is the spec's (the orchestrator's staging defect), not the lane's.**

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 specification sections and trace | not met | no `doc/specification/`, `doc/Traceability.adoc` or watch change: nothing here implements a whole requirement (confirmed) |
| D2 the gate at the start | shipped-as-specified | `ProjectOnboardingStart` over `SetupGate`, `ProjectOnboardingStartTest`; **nothing in any module calls it** (the leaf): a gate type with a test that can fail, not wired |
| D3 the unit, detection, entry, abandonment | not met | only `ProjectIdentityProposal`; the rest is `.pmwf` unit content (confirmed) |
| D4 the checkout's scripts | shipped-modified (partial) | `CheckoutScripts`, `PendingConsent`, `RunConsents`; the entry and the `project.enrol` write are the unit's; **D4 contradicts itself on a joint transaction with `project.enrol`** (layer 1) |
| D5 the setup, first run | not met | `SetupRun`, `SetupAspect`, `SetupRunDerivation` primitives only (confirmed) |
| D6 consents of model use, in tokens | shipped-modified (partial) | `EnrichmentOfferTest`; the spec gives no source for the token figure (layer 1) |
| D7 project values over the machine's | shipped-modified (partial) | `ProjectValueProposal`, `TranscribeMethods`; **`SettingsConflict` was not built here** (only its test and the ADR wording changed); the spec omits `transcribe.methods` |
| D8 the projects of the machine | shipped-modified (partial) | `ExplorerStore`, `ExplorerCodec`, `LockKey.explorer(base)`; the listing rows and routes are the assembly's |

Side effect that matters: the diff added `transcribe` to `local-config.schema.json`, so `EffectiveConfigKeys` of PLAN-V2-10 had to follow: **the collision found on 2026-11-18 before the merge was resolved by the lane** (two addressable nodes, `local-config.transcribe` (Object) and `local-config.transcribe.methods` (`ListOf(Enumeration(browser, local, provider))`), so the table has **100 keys: 16 project, 20 workflow, 64 local-config**, re-read on `origin/main`: `EffectiveConfigKeys.java:130-131`; the orchestrator's note said 99, the lane's correction (the parity test requires the container node) is right).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`), a leaf of its own for this landing; the orchestrator re-read the points marked "re-read" at `9695e3f4`.
- **Verdict:** layer 1 findings (one major); layer 2 findings; layer 3 findings (one major, no blocker).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 (major) | the Done-when tests (onboard a fixture checkout to ready, a refused consent leaves the checkout unread, abandonment, the setup walk, `GET /api/v1/projects?all`) are reachable in this repository | checked | refutes | they need the `.pmwf` unit, the entry endpoint and the routes the spec's own Placement section (operator decision 2026-11-09, spec `:186-188`) keeps out; the spec never splits the Done-when per repository |
| L1-2 | D4: the remembered consent is written in the same transaction as `project.enrol` | checked | refutes | the spec and Store Schemas 12 line 145 both say so; the primitive exists without the unit |
| L1-3 | D7 lists every project value | checked | refutes | omits `transcribe.methods` |
| L1-4 | D6 names the source of the token figure | checked | refutes | none given |

**Layer 1 verdict:** findings — 4 (one major). The staging defect named in the Watch of 2026-11-18 is confirmed: the cleanup re-grounds re-checked the Claim Labels and never tested the Done-when against the Placement section.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D2 | `ProjectOnboardingStart.java` | `ProjectOnboardingStartTest` | not wired anywhere | checked |
| D4 | `CheckoutScripts.java`, `PendingConsent.java`, `RunConsents.java` | `CheckoutScriptsTest`, `PendingConsentTest`, `RunConsentsTest` | **the handle traversal, the explorer lock use, the window boundary and the schema parity are proven by no test that can fail** (the leaf) | checked |
| D6 to D8 | the primitives | their tests | `SettingsConflict` not built here | checked |

All 47 files are explained by a deliverable or by the lane's own report. Residues the lane named are all confirmed; **two more the lane did not name:** Store Schemas 12 still says the explorer codec is "not built yet", and the `pm-runtime` machine-config schema has no `transcribe` member to bound a project's narrowing. The 100-key table, the `local-config.schema.json` member, `LockKey` and `StrictJson` claims check at the merge commit.

**Layer 2 verdict:** findings — 5 (minor).

### Layer 3 — Correct

Re-read by the orchestrator at `9695e3f4`: L3-1 (`ExplorerStore.java:90` `actsInWindow()` sums every stored day, `:130` `mostUsed(limit)` takes no date, the prune runs only inside the write paths `:226` and `:249`), L3-3 (`CheckoutScripts.java:146`: the handle is opened with `READ` and `NOFOLLOW_LINKS` and the lines read show no file-type re-check), L3-8 (`setup_aspect` is a member of `status.schema.json:86` and of no `WorkflowStatusCodec` code line).

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `ExplorerStore.java:130`, `:90`, `:315` (re-read) | a read after more than 30 idle days, before any write | the 30-day window is applied only when a write prunes; the spec counts it back from the read (`timeouts/03` lines 221-225), but `mostUsed(limit)` takes no date: expired acts still rank a project most used; ADR `core-store/073` and the Javadoc claim the opposite; no test reads a stale document | **major** |
| L3-2 | `ExplorerStore.java:215` | a caller-supplied `enrolled` set, taken outside the lock | can wipe all favourites and activity in one write | minor |
| L3-3 | `CheckoutScripts.java:146` (re-read) | a FIFO or device swapped in after the regular-file check | the handle open does not re-check the file type: a hang or an unbounded read | minor |
| L3-4 | `CheckoutScripts.java:89` | — | the digest is of the lexically normalised path and nothing binds it to the later run | minor |
| L3-5 | `SetupRunDerivation.java:50` | any recorded run | overrides the entry, not only an interrupted first run | minor |
| L3-6 | `ProjectValueProposal.java:71` | an existing project override | cannot signal its removal | minor |
| L3-7 | `TranscribeMethods.java:34` | a project that wants no transcription | cannot narrow to none | minor |
| L3-8 | `WorkflowStatusCodec.java:146` (re-read) | `SetupAspect` persisted | `setup_aspect` is refused as a retained member (pre-existing, answered by the lane): the aspect cannot be persisted | minor |
| L3-9 | `ExplorerStore.java:273` | every write | no audit record, although Store Schemas 12 line 13 says every write is audited | minor |

**Layer 3 not shown by reading:** build and test results, the Sonar clause, standing rules r7 (the leaf was not given the file), and whether the consuming unit re-digests a script before launch.

**Layer 3 verdict:** no blocker; findings — 1 major, 8 minor. **Reported to the operator in the same turn; none is fixed inline.** The major (L3-1) is latent: nothing here reads the store yet.

### Conformance to the Project's Own Rules

Fifteen rows: one deviates (`CheckoutScriptsTest` and `PendingConsentTest` use literal project ids and script bodies without generators or a contract note); generic catches, `@NullMarked`, `@Builder` on records of four or more components, Optional/`@Nullable` rules, assertion messages (scanned), no new hand-written fake and no model-facing content all conform.

## Metrics and Anomalies

- Tokens: `unknown` (harness opencode); `plugin_version` `unknown`; `executor_version` 0.1.2155.
- Anomalies: **`review-gate` answered `undecidable_read` on CodeRabbit's `<picture>`-prefixed "Minor comments: 25" heading for the whole run** (reproduced by the orchestrator on 2026-11-18; forwarded to plan-marshall's `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-034`); the lane read the review body directly (31 findings: 30 fixed, 1 answered) and merged on the operator's selection "Merge despite the parser gap" (the lane's operator question 4; the operator's word "continue" and the recommendation of the Watch of 2026-11-18); three transient Maven Central 429s (surefire POM), re-run each time; the security threads reopened twice by CodeRabbit's follow-ups at 11:02 and 13:20 (the path-level fix was replaced by directory-handle traversal `c95ee01c`, the handles closed `9d46e0a5`, Sonar `S2095` a BLOCKER fixed, the Javadoc identity claim corrected `aeafd2d1`); main merged twice during the run (#495, #500).

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `241da938` and answered follow-ups at 11:02 and 13:20; Sourcery refused (5 days 4 hours). `review-gate --pr-number 496`: unresolved threads 0 (the landing says 0), **the total and handled counts are not obtainable from the gate** (`undecidable_read`), the landing says `unknown` and its text says 31, all handled: the orchestrator cannot reproduce 31 from the provider (**not re-verified**); the progress file holds 6 `finding:` lines.
- CI/merge: merged through the merge queue as `9695e3f4` (47 files, +3715 -14) after the lane merged `origin/main`.
- Sonar: `sonar_open_new_issues=0`, gate passed on the merge head (26 to 7 to 1 BLOCKER `S2095` to 0), the lane's read, not re-read here.
- Parallelization: ran beside PLAN-V2-10, 46, 13 and 47. **A collision no gate predicted:** the added schema member against the key table of the plan that merged first (see the Watch of 2026-11-18): resolved by the lane after the orchestrator's note, which the declared surfaces had not shown (an understated Expected Surface: `local-config.schema.json` was not listed).

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#496`
- [x] row `landing` stamped `landings/PLAN-V2-17.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-17-project-onboarding-unit`
- [x] Watch of 2026-11-18 (PR #496 open) retired; new Watch for the open residue; PLAN-V2-114 item 53
- [x] resume anchor, view regenerated

## Follow-Ups

- L3-1 to L3-9, L2 residues and the layer-1 points: PLAN-V2-114 item 53.
- The unit content, the entry endpoint and the routes (D1, D3, D5 and the unwired halves of D4, D6, D7, D8): owed to `plan-marshall-mcp`; the package file `project-onboarding-unit.adoc` stays open.
- PLAN-V2-22 (`start-source-intake`, depends on this plan) is unblocked for the gate.
- The `review-gate` parser gap: forwarded as `pm-mcp-core-m2-m4-v2-034` (uncommitted in plan-marshall's ledger worktree until its land).
