envelope_version=1
sender_type=plan
sender_id=rb-v2-17-project-onboarding-unit
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-18T11:23:54Z
revision=1
amended=2026-11-18T11:48:52Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-17-project-onboarding-unit
epic=pm-mcp-core-m2-m4-v2
pr=#496
merge_state=open
cleanup_owed=false
deliverables_total=8
deliverables_done=1
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2152
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:open
unresolved_review_threads=0
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

guard: none on this harness (unenforced)

# PLAN-V2-17 — the project onboarding unit: framework-free primitives (PR #496, open)

The spec is implemented only in the part this repository can own. The `project.*`/`setup.*` unit content, the
`/api/v1` routes and the runtime wiring live in `plan-marshall-mcp` (spec § Placement of endpoints and default units,
operator decision 2026-11-09; `CLAUDE.md` forbids model-facing content in a JAR of this repository), so the Done-when's
unit-level clauses cannot be met here. They are reported as not met, not claimed.

## 1. Deliverables

Every Done-when of the spec is one Tests block naming unit-level behaviour; no failing output was captured, so the
order of every clause is `none`.

| # | Deliverable | Verdict | Evidence |
|---|---|---|---|
| 1 | specification sections / trace | **not met** | no `doc/specification/`, `doc/Traceability.adoc` or watch change: nothing here implements a whole requirement, and a trace line would claim it (`order: none`) |
| 2 | the gate at the start | **met** | `ProjectOnboardingStartTest` (`ProjectOnboardingStart`; `SetupGate` untouched), commit `da54bf85`, `order: none` |
| 3 | the unit, detection, entry, abandonment | **not met** | only `ProjectIdentityProposal` (+ `ProjectIdentityProposalTest`, `a7cdab95`); detection, entry, stream and abandonment are `.pmwf` content (`order: none`) |
| 4 | the checkout's scripts | **partially met** | `CheckoutScriptsTest`, `PendingConsentTest`, `RunConsentsTest` (`b6a17eac`, hardened in `8509ddef`); the entry and the `project.enrol` write are the unit's (`order: none`) |
| 5 | the setup, first run | **not met** | `SetupRunTest`, `SetupAspectTest`, `SetupRunDerivationTest` (`4f52754e`); the state order, models/roles, landing variants and `no`-skip are the unit's (`order: none`) |
| 6 | consents of model use, in tokens | **partially met** | `EnrichmentOfferTest` (`b56b4134`); the qualification offer shipped earlier (`GradingOffer`); the consent states and the owed path are the unit's (`order: none`) |
| 7 | project values over the machine's | **partially met** | `ProjectValueProposalTest`, `TranscribeMethodsTest`, `SettingsConflictTest` (`89232611`, `2cfcc962`); the local-config write is not built (`order: none`) |
| 8 | the projects of the machine | **partially met** | `ExplorerStoreTest`, `ExplorerCodecTest` (`e3370abc`, `03e03df1`, `8509ddef`); the listing rows and the routes are assembly/runtime (`order: none`) |

Per-head test runs: `module-tests` at `03e03df1` (11,109) and through CI at `241da938`, `8509ddef`, `e3ac5fe7` and
`2cfcc962`; `quality-gate` (`verify`) at `8509ddef` and `2cfcc962` — 11,118 tests, analyses `compile, lint, test`.
Watch items `PM-WATCH-CLI-23/25/29`: **not evidenced** (nothing here exercises unenrolment, release or the lifecycle
gate).

## 2. Personas and skills

The spec declares no `Skills:` or `Persona:` lines, so `skills: none named`. Personas applied as standards:
`persona-implementer` (production code, D2–D8), `persona-module-tester` (the unit tests), `persona-code-reviewer` (every
self-review round and every PR comment). No skill added for surface drift. R20's coding skills: I read
`plan-marshall-ref-code-quality` (with its review spine) and `pm-dev-java-java-core` at SKILL.md level; the rest of the
list was followed through `CLAUDE.md`'s Code Standards, which are their repository-level condensation — recorded as such,
not as read.

## 3. Hypotheses

* `HYPOTHESIS: pm-runtime runtime/workspace hosts the projects of the machine` — **refuted**:
  `pm-runtime/src/main/java/de/planmarshall/runtime/workspace/package-info.java` and the directory hold
  `WorkspaceConfinement` only (the path-confinement entry). The explorer store went to `core.store`, as the spec's later
  re-grounding says.
* The other Claim Labels are `OBSERVED` and were re-read where they bear on this work (the setup gate already exists and
  refuses with `machine_not_onboarded`/`onboarding_incomplete`, `ConsentStore`/`ConsentGate`/`ConsentScript` shipped with
  PLAN-46, `SettingsConflict` is the operator files' refusal).

## 4. Findings

Self-review, three rounds (each a read-only sub-agent with `persona-code-reviewer`):

* Round 1: 27 findings. 15 fixed in `e27d7a09` (explorer prune keeping the last act, `SettingsConflict` for the stale
  revision, identity proposal returning empty where no id can be formed, streamed script digest, `@Builder`, Javadoc and
  README accuracy, the `transcribe` schema key, the added-script case, pinned token figures). Open and deliberately not
  fixed: the spec-level Done-when vs placement gap, R05 (no requirement is fully implemented here, so no trace line), and
  the explorer store's audit seam.
* Round 2: the residuals fixed in `d31914dd` (enum including `browser`, `@throws` split, the spec-silent 8 MB bound
  removed, `SetupRun` Javadoc, ADR lists, a slim conflict guard/message test).
* Round 3: one code defect — `ExplorerCodec` bypassed `StrictJson`, accepting content after the document and
  misclassifying another format version — fixed in `03e03df1` with `StrictJson.read` and one refusal test per rule.

CI: head `241da938` failed on a Maven Central `429` resolving the surefire POM (not a code failure); the workflow was
re-run and went green. Sonar found 26 issues on the first head (all style in the new tests) and 7 after the review fixes;
all fixed in `da962f39` and `e3ac5fe7`; the head now carries **0** new issues, gate passed.

CodeRabbit (review at 10:41 on `241da938`, marker `kind=reviewed` covering that commit):

* inline 1 — `CheckoutScripts` accepted a symlinked *parent* directory (Major/Security): fixed in `8509ddef` by walking
  every component without following a link; thread answered with the commit and resolved.
* inline 2 — the final entry could be swapped between the check and the open (Major/Security): fixed in `8509ddef` by
  opening with `NOFOLLOW_LINKS`; thread answered and resolved.
* inline 3 — the explorer lock-key guard checked only level and id (Major): fixed in `8509ddef` by comparing with
  `LockKey.explorer(base)`; thread answered and resolved.
* outside-diff 1 — `recordActivity` pruned activity but not favourites: fixed in `8509ddef`, with a test.
* outside-diff 2 — `setup_aspect` missing from `WorkflowStatusCodec.MEMBERS`: **answered, not fixed** — a pre-existing gap
  in files this PR does not change (the reviewer states it predates the PR); named here as the follow-up that owns it.
* the 25 minor and 4 nitpick comments (read directly from the review body on the operator's instruction): **28 fixed** in
  `8509ddef` and `2cfcc962` — device-id validation at capture, value-rule caveats, portable script path, dot owner, the
  64-character grammar bound, duplicate transcription methods, `INTEGRATIONS` refused as a recorded run, revision and
  day-count exhaustion, `long` counts, four-digit day keys, favourite pruning on activity writes, the explorer factory in
  `LockKeyTest`, the duplicate-day case, locale-stable digit assertions, the list snapshot and estimate validation, the
  README preamble, the guides and the ADRs, and the four test nitpicks. **One answered**: the schema-derived
  required-member sets (the codebase's codecs state their contract explicitly; named follow-up). The specification's
  "transaction of `project.enrol`" wording is a specification correction, outside this repository's code.

## 5. Reviewers

* `coderabbit` (required, `quota_limited`, `review_complete_after_handling`): triggered at 09:55 — first answer was a
  review-limit refusal ("next included review available in 5 minutes"); the automatic run then reviewed `241da938` at
  10:41 (walkthrough plus marker `kind=reviewed` naming that commit). Every finding of it is fixed or answered, and every
  thread is resolved, so that review is the review at any later head of this PR. No trigger was posted for the later heads
  (the exemption). Heads and history: reviewed `241da938`; current head `2cfcc962`.
* `sourcery` (optional): triggered at 09:55, refused on quota ("5 days and 4 hours"); optional, so no obligation.
* Standing review authorization: not needed — the qualifying review is on record and its findings are handled.

## 6. Operator questions

1. "The runbook-lane executor is stale (0.1.2149 vs 0.1.2152). How should I proceed?" — options: regenerate then continue /
   stop and regenerate yourself / abort. Selected: free text "it is regenerated"; I re-ran `executor-skew`, got `match`
   and continued.
2. "The review gate is blocked: `review-gate` answers `undecidable_read` on CodeRabbit's picture-prefixed 'Minor comments:
   25' heading, while CI, verify and Sonar are green and every thread is resolved. How should I proceed?" — options: stop
   here with the PR open / post a fresh CodeRabbit review / stop and escalate the parser gap. Selected: "Directly read the
   minor comments and handle. No new review." All 29 minor/nitpick items were then read from the review body: 28 fixed
   (`8509ddef`, `2cfcc962`), 1 answered; no new review was requested.

## 7. Rule check

* `guard: none on this harness (unenforced)` — OpenCode has no pre-tool-use hook, so nothing was enforced and no
  guard-log line exists for the run. No "all kept" is claimed.
* `executor-skew`: `match` (0.1.2152) after the operator's regeneration; recorded here as the rule asks.
* Spec read: `spec_source=ledger_worktree`, no `spec_differ` marker.
* ADR numbering re-checked against `origin/main` (R11): `core-store` 073, `core-config` 004/005, `workflow-setup`
  002/003, `core-architecture` 001 — all free.
* Rule deviations, stated rather than hidden: the documentation link check ran as R17 prescribes
  (`python3 /home/oliver/git/plan-marshall-documentation/.claude/skills/doc-review/scripts/trace.py …`) — a direct
  interpreter call the lane's Executor rule would otherwise route through an executor, but no notation exists for a script
  of the sibling repository. `ci checks rerun` was used once, on the transient Maven Central `429`. The operator's second
  answer directed me to handle the review body's comments directly; that is an operator instruction on how to proceed with
  the blocker, not a merge authorization, and the gate's verdict is unchanged.
* R05 (condense and point, Traceability lines) is **not** done: no requirement is fully implemented here, and a trace
  line for `PM-EXT-8`/`PM-SEC-8` would state an implementation this repository does not contain. R01's package-file
  deletion is likewise **not** done: the package is not finished.
* Every shell file operation in the run was a `git` or executor/build call; no `cat`/`grep`/`find`/`sed`/`head` was used
  for repository files (the two `grep` calls target a saved tool-output file and a build-log path). No `.plan/` write
  outside `.plan/temp/` and the sanctioned CI body slots.

## 8. Build server

Preflight at setup: `ready` (registered). Every build ran with the default routing and was submitted:
`mechanism=daemon_longpoll` for all of them (test-compile ×5, module-tests ×9, quality-gate ×9). Fallbacks: none — no
`degraded`, no launch-fault retry, no `killed` job. Three routed builds returned a live `running` status; each was
re-attached with `build_server wait` on its `job_id` and finished green. The one transport-level failure was in CI
(Maven Central 429), not the local build server.

## 9. Residue

* **The merge gate is still blocked, and the run stops before the merge.** `review-gate` answers `undecidable_read` on
  CodeRabbit's `<picture>`-prefixed "Minor comments: 25" heading — the parser cannot map that counted title, and it will
  answer the same on every re-run because the review body does not change. Every review comment is handled (3 threads
  resolved, 2 outside-diff items fixed/answered, 28 minor/nitpick items fixed and 1 answered), and CI, `verify` and Sonar
  (0 new issues, gate passed) are green on `2cfcc962`. The rule is that an undecidable read is a stop, never a pass, so the
  PR is left **open** for the operator.
* Deliverables not met as written (D1, D3, D5, and the unwired halves of D4, D6, D7, D8) need the `plan-marshall-mcp`
  unit content and the assembly routes; the package file stays open.
* `ExplorerStore` writes no audit record: the operator-file audit seam (channel, device) is not wired for it yet.
* The specification's stale statements this run did not correct (outside its change set): Store Schemas 12 still says the
  remembered consent is written "in the transaction of `project.enrol`", and the spec's own § Dependencies still calls
  PLAN-V2-43 "staged" though it shipped as #462.
