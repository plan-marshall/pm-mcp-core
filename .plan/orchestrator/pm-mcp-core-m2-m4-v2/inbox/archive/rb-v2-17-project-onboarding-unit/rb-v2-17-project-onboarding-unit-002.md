envelope_version=1
sender_type=plan
sender_id=rb-v2-17-project-onboarding-unit
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-18T16:24:45Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-17-project-onboarding-unit
epic=pm-mcp-core-m2-m4-v2
pr=#496
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2155
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-17 — the project onboarding unit: framework-free primitives (PR #496, MERGED as 9695e3f4)

Standing rules: **r7**, rule ids **R01–R16, R18–R20** (the spec's First Instruction names r4; the file read was r7 — drift
recorded). The spec is implemented only in the part this repository can own: the `project.*`/`setup.*` unit content, the
`/api/v1` routes and the runtime wiring are `plan-marshall-mcp` content (spec § Placement of endpoints and default units,
operator decision 2026-11-09; `CLAUDE.md` forbids model-facing content in a JAR of this repository).

## 1. Deliverables

Every Done-when of the spec is one Tests block naming unit-level behaviour; no failing output was captured, so the order
of every clause is `none`. **Deliverables done: 1 of 8.** D1, D3 and D5 are not met by construction (their content is
`plan-marshall-mcp`'s); D4, D6, D7 and D8 are partially met (the primitives are here, their wiring is not).

| # | Deliverable | Verdict | Evidence |
|---|---|---|---|
| 1 | specification sections / trace | **not met** | no `doc/specification/`, `doc/Traceability.adoc` or watch change; nothing here implements a whole requirement |
| 2 | the gate at the start | **met** | `ProjectOnboardingStartTest` (`da54bf85`, `da962f39`) |
| 3 | the unit, detection, entry, abandonment | **not met** | only `ProjectIdentityProposal` (`a7cdab95`); detection, entry, stream, abandonment are `.pmwf` content |
| 4 | the checkout's scripts | **partially met** | `CheckoutScriptsTest`, `PendingConsentTest`, `RunConsentsTest` (`b6a17eac`, `8509ddef`, `c95ee01c`, `9d46e0a5`, `aeafd2d1`); the entry and the `project.enrol` write are the unit's |
| 5 | the setup, first run | **not met** | `SetupRunTest`, `SetupAspectTest`, `SetupRunDerivationTest` (`4f52754e`); the state order and choices are the unit's |
| 6 | consents of model use, in tokens | **partially met** | `EnrichmentOfferTest` (`b56b4134`); qualification offer shipped earlier; the consent states are the unit's |
| 7 | project values over the machine's | **partially met** | `ProjectValueProposalTest`, `TranscribeMethodsTest`, `SettingsConflictTest` (`89232611`, `2cfcc962`); no local-config write |
| 8 | the projects of the machine | **partially met** | `ExplorerStoreTest`, `ExplorerCodecTest` (`e3370abc`, `03e03df1`, `8509ddef`); the listing rows and routes are assembly/runtime |

Watch items `PM-WATCH-CLI-23/25/29`: **not evidenced** (nothing here exercises unenrolment, release or the lifecycle gate).

## 2. Personas and skills

The spec declares no `Skills:`/`Persona:` lines (`skills: none named`). Personas as standards: `persona-implementer`
(production code), `persona-module-tester` (tests), `persona-code-reviewer` (every self-review round and every PR
comment). R20's coding skills: `plan-marshall-ref-code-quality` and `pm-dev-java-java-core` read at SKILL.md level; the
rest followed through `CLAUDE.md`'s Code Standards (recorded as such, not as read). No skill added for surface drift.

## 3. Hypotheses

`HYPOTHESIS: pm-runtime runtime/workspace hosts the projects of the machine` — **refuted**:
`runtime/workspace/package-info.java` holds `WorkspaceConfinement` only; the explorer store went to `core.store`. The
`OBSERVED` claims that bear on the work were re-read (the setup gate, `ConsentStore`/`ConsentGate`/`ConsentScript`,
`SettingsConflict`).

## 4. Findings

* Self-review round 1 (27 findings): 15 fixed in `e27d7a09`; open by design: the placement gap, R05, the explorer audit seam.
* Round 2: residuals fixed in `d31914dd`. Round 3: one code defect (`ExplorerCodec` bypassed `StrictJson`) fixed in
  `03e03df1`.
* CodeRabbit review round 1 (10:41 on `241da938`): 3 inline (Major) + 2 outside-diff + 25 minor + 4 nitpick — 30 fixed
  (`8509ddef`, `2cfcc962`), 1 answered (schema-derived required sets), 1 answered (the `WorkflowStatusCodec.MEMBERS`
  pre-existing gap). Its follow-ups at 11:02 and 13:20 re-opened the two security threads: the path-level fix was
  replaced by directory-handle traversal (`c95ee01c`), the handles closed (`9d46e0a5`, Sonar `S2095`), and the Javadoc
  identity claim corrected (`aeafd2d1`); both threads answered and resolved each time.
* CI: three transient Maven Central `429`s (surefire POM) — re-run each time, green after; Sonar 26 → 7 → 1 (BLOCKER
  `S2095`) → **0** new issues, gate passed on the merge head.

**Review numbers on the merge head**: `unresolved_review_threads` = 0 (the three threads resolved after each reply);
review-body findings = **31** read from the review body (2 outside-diff, 25 minor, 4 nitpick), **all handled** (30 fixed,
1 answered). The facts fence reports `unknown` for total/handled because `review-gate` is `undecidable_read` on the bot's
`<picture>`-prefixed "Minor comments: 25" heading and its own numbers do not exist for this head.

## 5. Reviewers

* `coderabbit` (required, `quota_limited`, `review_complete_after_handling`): triggered 09:55 — quota refusal first
  ("next included review in 5 minutes"); reviewed `241da938` at 10:41 with marker `kind=reviewed`; follow-ups at 11:02 and
  13:20 after the fixes. Every finding handled, every thread resolved. No trigger posted for the later heads (the
  exemption).
* `sourcery` (optional): quota-refused ("5 days and 4 hours"); optional, no obligation.

## 6. Operator questions

1. Executor skew (0.1.2149 vs 0.1.2152) — selected: free text "it is regenerated"; re-checked `match` and continued.
2. Review-gate blocked — options: stop / fresh review / escalate the parser gap. Selected: "Directly read the minor
   comments and handle. No new review." All 29 minor/nitpick items then read from the review body; 28 fixed, 1 answered.
3. CodeRabbit's follow-up round (directory-handle traversal) — fixed and answered.
4. Merge decision — the exact gap named (review-gate `undecidable_read` on the bot's summary heading; head `c5651228`;
   every other condition green). **Selected: "Merge despite the parser gap".** The merge was enqueued on that selection.

Free text: the executor sentence above.

## 7. Rule check

* `guard: none on this harness (unenforced)` — nothing was enforced, no guard-log line exists for the run; no "all kept".
* `executor-skew`: `match` (0.1.2155 at the end, after the second regeneration).
* Spec read: `spec_source=ledger_worktree`, no `spec_differ`.
* R11: ADRs renumbered against `origin/main` after the merge — `core-config/009`, `core-config/010` (004/005 were taken by
  #495); `core-store/073`, `workflow-setup/002–003`, `core-architecture/001` are free.
* R05 not done (nothing here implements a whole requirement); R01's package-file deletion not done (the package is open).
* Deviations stated: the documentation link check ran as R17 prescribes (a direct interpreter call, no executor notation
  exists for the sibling repository); `ci checks rerun` used three times, each on a transient Maven Central `429`.
* Every shell file operation was a `git` or executor/build call; no `cat`/`grep`/`find`/`sed`/`head` for repository files.

## 8. Build server

Preflight `ready`. All builds submitted, `mechanism=daemon_longpoll`: test-compile ×5, module-tests ×12, quality-gate ×15.
No `degraded`, no launch-fault retry, no `killed` job. Live results were re-attached with `build_server wait` on their
`job_id`. The transport failures were in CI (Maven Central 429), never in the local server.

## 9. Residue

* **The review-gate parser gap** — `review-gate` cannot map a `<picture>`-prefixed "Minor comments: N" heading; it stays a
  stop that the operator answered with the merge selection above. Worth fixing in the runbook-lane parser.
* Deliverables not met as written (D1, D3, D5; the unwired halves of D4, D6, D7, D8) need the `plan-marshall-mcp` unit
  content and the assembly routes; the package file stays open.
* `ExplorerStore` writes no audit record (the operator-file audit seam is not wired).
* Specification statements this run left uncorrected: Store Schemas 12's "in the transaction of `project.enrol`" wording,
  and the spec's Dependencies still calling PLAN-V2-43 "staged" though it shipped as #462.
* **Main merged twice during the run**: #495 (`eb949e33`, the effective-key table) and #500 (`04014815`, steward config).
  The `transcribe` member was added to `EffectiveConfigKeys` as **two** addressable nodes — `local-config.transcribe`
  (Object) and `local-config.transcribe.methods` (`ListOf(Enumeration(browser, local, provider))`) — so the table is
  **100 keys: 16 project, 20 workflow, 64 local-config** (not 99: the parity test requires the container node too), and
  the guide states those counts.
