envelope_version=1
sender_type=plan
sender_id=rb-v2-135-served-model-recorded
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-24T09:16:41Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-135-served-model-recorded
epic=pm-mcp-core-m2-m4-v2
pr=#594
merge_state=open
cleanup_owed=false
deliverables_total=6
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:open
unresolved_review_threads=0
review_body_findings_total=9
review_body_findings_handled=9
sonar_open_new_issues=unknown
step.sonar-roundtrip.open_issue_rules=unknown
```

**Standing rules: r7, R02, R05, R06, R07, R08, R10, R11, R13, R14, R16, R17, R18, R19, R20** (read in full at session start; R17's links check is void). Guard: none on this harness (unenforced).

## Report

### 1. Deliverables

| # | Done-when | Order | Verdict | Test / evidence |
|---|---|---|---|---|
| D1 | validator cases (missing `model`, `null`, pointer, non-string, no leading `/`, `~2` escape, >256, `not_applicable`, unknown member), figures stay seven | test-first | met | `ProfileValidatorTest` (`usageModelPointerAccepted`, six refusal rows, `usageFiguresStayNumeric`); failing run at 02022e51, log `b1efe53f76ab443f81a87df276c69653`; commit eee7b3a2 |
| D1 | every shipped profile declares the member | test-first | **not met** | `ShippedUsageFixturesTest.shippedProfilesDeclareTheModelPointer` — red because the pm-api SNAPSHOT profiles carry no `usage.model` (log `18f0bea34fc043f0b40a694da32ffdfe`, reproduced with `-U`, log `83cf4e0a3e824cd3ba8a5d797afe7745`). Everything else in D1 is green. |
| D2 | `readModel` + `ServedModels` | mutation-after | met | `UsagePointersTest$ModelPointer`, `ServedModelsTest`; mutation at 4c848282, log `15d4df821b874193bfeb796324e7d392` |
| D3 | row round trips, `unmeasured`, refused row, spec and file agree | mutation-after | met | `MetricsStoreTest.servedModelsRoundTrip`/`servedModelsUnmeasured`/`$RefusedTask`, `MetricsSchemaParityTest.servedModelsShape`, `PlanTokensTest`; mutation at 01a186ec, log `daef56a8455a49cd879202b2d1adc7a9` |
| D4 | kind, members, bounds, masking | mutation-after | met | `ActivityEntryValidationTest`, `ActivityEntryTest.modelDiffers`, `TaskQueueSchemaParityTest.modelDiffers`, `SecretRedactorTest` (three masking cases), `ActivityJournalMaskingTest` (three cases); mutation at 364795c1, log `f431585aac9e4635b7514bcef475fbd4` |
| D5 | watch, mapping, pump, no derivation; no production pump wiring | mutation-after | met | `ServedModelWatchTest` (13 cases), `ActivityEventMapperTest.servedModel`, `WorkerActivityPumpTest.servedModelChanges`/`noModelPointer`, `OffTrackDetectorTest.modelDiffersRaisesNoSignal`; mutation at 25699634, log `92a53a0147c8462f904493eb49912a43` |
| D6 | old statements gone, checks pass | none | met | grep of `doc/` finds none of the old statements; `verify -DskipTests` green across all modules at 8265f4e1 (log `b5de334dfd524bd8a29e1965606a4cb1`) |

Per-head test runs: 11 625 tests green over `pm-core` + `pm-runtime` + `pm-workflow` with the four blocked classes excluded (head 8265f4e1, log `4a017d679a3b4f0eba4c399cf58a90e2`); the touched-class sets at 79b6a5a8 (pm-core 276, log `490533c6a35b4f83b24a28ee88489eb4`; pm-runtime 40, log `9ff8e90393d8450e95df30f323c2b14b`). The quality-gate canonical at 1a179d30 is red on exactly the four shipped-profile classes (log `743ef79bd1d74cc595c200213549f881`).

### 2. Personas and skills

`persona-implementer` for D1–D5, `persona-code-reviewer` for the self-review and the PR-comment triage (deliverables declare Persona implementer; the spec declares no documentation persona, so D6 followed `persona-implementer` with `pm-documents:ref-asciidoc`). Domain skills per deliverable: `pm-dev-java:java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `pm-dev-java-cui:cui-testing`, `pm-documents:ref-asciidoc`; `cui-logging` read (no record added, R12). No skill added for surface drift.

### 3. Hypotheses

- H21 (the model pointer is read on every line, independent of the events mapping): **confirmed** — `ActivityEventMapper.servedModel` reads before mapping, `ServedModelWatchTest`/`WorkerActivityPumpTest` exercise it.
- H22 (the `output.result_pointer` check is the model for the `model` shape): **refuted in one respect** — that check accepted a malformed `~` escape and resolved to nothing at run time; both members now use the RFC 6901 form.
- H23 (`@Builder` only from four components): **confirmed** — `ServedModels` (1) and `Difference` (2) keep the canonical constructor.
- H24 (the only `MetricsRow` builders are the named tests): **refuted** — `MetricsRowBuilderContractTest` and `DeletedMemberRefusalTest` also build one (both in the spec's understated surface) and were updated.

### 4. Findings

Self-review: round 1 (8 findings: masking collision, row bounds, watch/bound wording, five statement defects, test data) — all fixed in b27fe50f; round 2 (N0 invisible-character collision still threw, N1 substring admission, N2 Javadoc/schema, N4 over-long model, N5 coupling, E5 residual) — fixed in 80541f01; three residuals (ADR evidence cost, claim precision, an off-track test flake) fixed in 1a179d30. `self-review: clear` after 3 rounds.

CodeRabbit (review of 1a179d30, 7 inline + 2 nitpicks): `USAGE_MEMBERS` derived, RFC 6901 pointer check, one-construction masking, symmetric alias comparison + previous-identifier tracking, claude mapping statements, release-switch test base, no-pointer test launch — all fixed in 79b6a5a8; the "keep blocked" comment answered with the reason (the operator's "do not merge" holds, the member stays required, the predecessor is named). All nine dispositions are `coderabbit {section} {id} — 79b6a5a8`.

### 5. Reviewers

- **coderabbit** (required, quota-limited, `review_complete_after_handling`): triggered 08:55Z, reviewed head 1a179d30 (COMMENTED, marker `final_review_risk_coverage` names 1a179d30), 9 findings all handled; re-trigger answered "already reviewed the last commit" (`no_unreviewed_commit`) because of the exemption. `review-gate` on head 79b6a5a8: `merge_allowed: true`, `unresolved_review_threads: 0`, `review_body_findings_handled: 9/9`.
- **sourcery** (optional, quota-limited): refused on `hard_quota` (review budget of 250 000 diff characters, next 2026-11-30); no re-trigger.

### 6. Operator questions

1. "D1's test-first run exposes that the spec's hard cross-repo predecessor is open: pm-api 0.1.0-SNAPSHOT still ships profiles without `headless.usage.model`; how should the run proceed?" — options {stop and wait for the clients PR; proceed, do not merge; proceed, land the clients change first}; **selected "Proceed; do not merge"**.
2. "D4's text says nested `model_differs {launch, served}`; the binding re-ground record says pm-api ships flat `launchModel`/`servedModel` — which wire shape?" — options {flat `launch_model`/`served_model`; nested `model_differs`}; **selected "Flat launch_model/served_model"**.

### 7. Rule check

- Guard: none on this harness (unenforced); `guard: none` in the resolve result, no guard log exists.
- One rule slip of my own: the very first targeted build ran without `--project-dir`, so it tested the main checkout's unchanged code (log `cbaaf1f1f5824d94acb5623567d706be`); it was re-run against the worktree immediately and decided nothing.
- Read-class shell operations: two `ls` calls at the start (to locate the installed skill file and the surefire reports) and one `grep` of a build log; the rest used the file tools. Both are shell file operations the rules forbid; recorded here rather than excused.
- Builds: 30 maven runs, all `mechanism=daemon_longpoll` in the `[BUILD-SERVER] resolved build` line; none fell back, none was a launch fault, none was killed. One run answered `indeterminate` (a `-Dtest` selector matched nothing in a module); it decided nothing and was re-run with `-Dsurefire.failIfNoSpecifiedTests=false`.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ`.
- `executor-skew`: `match` (0.1.2183 = 0.1.2183).
- `CoverageItemTest.keepsAnAbsentFileAndBranch` failed once with "coverage item name must not be blank" and passes on re-run on the worktree and on main: a pre-existing flake of `Generators.nonEmptyStrings()` feeding a blank-only name into `CoverageItem`, not this plan's code.

### 8. Build server

Setup preflight: `ready`. Every build: `daemon_longpoll`, no fallback, no retry. Setup `executor_in_worktree: present`; `build-routing` answered `auto`. No `killed` job. `runtime_stamp_reason` absent.

### 9. Residue

- **D1's shipped-profile clause is not met** until the pm-mcp-clients profile change is merged and its SNAPSHOT deployed; the branch is red on that one cause and is not merged (the operator's decision).
- **Sonar is unread**: `sonar-build` was skipped because the build failed, so there is no pull-request analysis; the read answered `undecidable` (`analysis_revision_unavailable`). It will need a read once the build is green.
- **One residue referred to layer 1**: a harness can alternate invisible-character variants of one model and so write one `model_differs` entry per variant. The change condition is defined on the reported identifier and the settled comparison is exact/alias on that string, so this is the specified behaviour; only a product decision to compare the visible form would change it.
- **Owed outside this repository**: the pm-mcp-clients `usage.model` profiles and the `model_differs` display; the `pm-mcp-server` wiring of `ServedModelWatch` and `served_models`; the recorded per-version harness outputs (PM-WATCH-CLIENT-20).
