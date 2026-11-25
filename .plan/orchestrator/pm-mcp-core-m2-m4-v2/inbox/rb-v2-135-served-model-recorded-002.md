envelope_version=1
sender_type=plan
sender_id=rb-v2-135-served-model-recorded
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-25T14:43:52Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-135-served-model-recorded
epic=pm-mcp-core-m2-m4-v2
pr=#594
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=6
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2184
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
unresolved_review_threads=0
review_body_findings_total=13
review_body_findings_handled=13
sonar_open_new_issues=0
step.sonar-roundtrip.open_issue_rules=none
step.sonar-roundtrip.quality_gate_status=ok
```

**Standing rules: r7 (2026-11-10), R02, R05, R06, R07, R08, R10, R11, R13, R14, R16, R17, R18, R19, R20** (read in full at the first session start and again on the resume; R17's links check is void per its 2026-11-23 amendment). Guard: none on this harness (unenforced).

## Report

### 1. Deliverables

| # | Done-when | Order | Verdict | Test / evidence |
|---|---|---|---|---|
| D1 | validator cases (missing `model`, `null`, pointer, non-string, no leading `/`, malformed escape, >256, `not_applicable`, unknown member), figures stay seven | test-first | met | `ProfileValidatorTest.usageModelPointerAccepted`, the refusal rows of `ProfileValidatorTest$Refusals`, `usageFiguresStayNumeric`; failing run at 02022e51, log `b1efe53f76ab443f81a87df276c69653`; commit eee7b3a2 |
| D1 | every shipped profile declares the member | test-first | met (the hard predecessor landed 2026-11-25) | `ShippedUsageFixturesTest.shippedProfilesDeclareTheModelPointer`; red at 02022e51 (log `b1efe53f76ab443f81a87df276c69653`), green at 79b6a5a8 (log `ab34c496c5394beda2cc6ba51b2d2d75`); commit eee7b3a2 |
| D2 | `readModel` and `ServedModels` | mutation-after | met | `UsagePointersTest$ModelPointer`, `ServedModelsTest`; mutation at 4c848282, log `15d4df821b874193bfeb796324e7d392`; commit 4c848282 |
| D3 | row round trips, `unmeasured`, refused row, schema and statement agree | mutation-after | met | `MetricsStoreTest.servedModelsRoundTrip`/`servedModelsUnmeasured`/`$RefusedTask`, `MetricsSchemaParityTest.servedModelsShape`, `PlanTokensTest`; mutation at 01a186ec, log `daef56a8455a49cd879202b2d1adc7a9`; commit 01a186ec |
| D4 | kind, members, bounds, masking | mutation-after | met | `ActivityEntryValidationTest$members`/`bounds`, `ActivityEntryTest.modelDiffers`, `TaskQueueSchemaParityTest.modelDiffers`, `SecretRedactorTest.entryModelDiffersIdentifiers`/`entryModelDiffersCollision`/`entryModelDiffersInvisibleCollision`/`entryModelDiffersIntermediateCollision`, `ActivityJournalMaskingTest`; mutation at 364795c1, log `f431585aac9e4635b7514bcef475fbd4`; commit 364795c1 |
| D5 | watch, mapping, pump, no derivation; no production pump wiring | mutation-after | met | `ServedModelWatchTest` (15 cases), `ActivityEventMapperTest.servedModel`, `WorkerActivityPumpTest.servedModelChanges`/`noModelPointer`, `OffTrackDetectorTest.modelDiffersRaisesNoSignal`; mutation at 25699634, log `92a53a0147c8462f904493eb49912a43`; commit 25699634 |
| D6 | old statements gone, checks pass | none | met | grep of `doc/` for the old statements finds none; `verify` green; commit 8265f4e1 |

Per-head test runs against pushed heads: 79b6a5a8 — `package` 12 876 tests (log `db17a3152361428cbefdfe75ebea2d85`) and `verify` 12 876 (log `c3901048f9864dfca1b4059135f530c4`); 40acc08f — `verify` 12 876 (log `fe1cb252ee164f57b1cb9ce71952ec6e`); c42cf012 — `verify` 12 876 (log `0fa7011c6aa140b4b6aa0392b480243c`); f6d0e66f (the `origin/main` merge) — `verify` 12 909 (log `954cc2035f64462a9d055587c45d12fd`); 4798b488 — `verify` 12 911 (log `2ce828264a0142aeacbb2aec1a4005ed`). Targeted runs of the touched classes: 280 (log `7ff58d82b49848b5b4e64a9b67a735b7`), 144 (log `d6543838acec484dae1220be7170998e`), 89 pm-runtime (log `84e0a15e31a544b1aeb681a8719539f2`), 91 pm-core (log `9e9a4d7f1c324cec99d6d746a185a7a3`), and 16 for the shipped-profile classes (log `ab34c496c5394beda2cc6ba51b2d2d75`).

### 2. Personas and skills

`persona-implementer` for D1–D6 (each deliverable declares `Persona: implementer`), `persona-code-reviewer` for the three self-review rounds and for the triage of every PR comment. Domain skills per deliverable, as the spec declares them: `pm-dev-java:java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `pm-dev-java-cui:cui-testing`, `pm-documents:ref-asciidoc`; `cui-logging` read as R20 asks (no record added, R12). No skill added for surface drift.

### 3. Hypotheses

- H21 (the model pointer is read on every line, independent of the events mapping): **confirmed** — `ActivityEventMapper.servedModel` reads before mapping; `ServedModelWatchTest` and `WorkerActivityPumpTest` exercise it.
- H22 (the `output.result_pointer` check is the model for the `model` shape): **refuted in one respect** — that check accepted a malformed `~` escape; both members now use the RFC 6901 form, and the check is a scan (`ProfileValidator.isJsonPointer`).
- H23 (`@Builder` only from four components): **confirmed** — `ServedModels` (1) and `ServedModelWatch.Difference` (2) keep the canonical constructor.
- H24 (the only `MetricsRow` builders are the named tests): **refuted** — `MetricsRowBuilderContractTest` and `DeletedMemberRefusalTest` also build one (both in the spec's understated surface) and were updated.

### 4. Findings

Self-review: round 1 (8 findings: masking collision, row bounds, watch/bound wording, five statement defects, test data) — fixed in b27fe50f; round 2 (invisible-character collision, substring admission, Javadoc/schema, over-long model, coupling, test data) — fixed in 80541f01; round 3 residuals (ADR evidence cost, claim precision, an off-track test flake) — fixed in 1a179d30. `self-review: clear` after 3 rounds.

CodeRabbit review 1 (head 1a179d30, 7 inline + 2 nitpicks): `USAGE_MEMBERS` derived, RFC 6901 pointer check, one-construction masking, symmetric alias comparison, `claude` mapping statements, release-switch test base, no-pointer test launch — fixed in 79b6a5a8; the "keep blocked" comment answered with the operator's decision.

Sonar at 79b6a5a8 (8 open): S5998 pointer scan, S3776 redactor method split, S1192 twice, S1130, S5778 twice, S3415 — fixed in 40acc08f and c42cf012.

CodeRabbit review 2 (head f6d0e66f, 3 actionable + 1 additional): the intermediate-collision test now builds the pair it names (inline-8), the watch commits only after the write (inline-9), an alias report keeps the concrete release (inline-10) — all fixed in 4798b488; additional-1 (derive the figure names from the schema) answered with the reason that the seven names are the contract D1 names and `HarnessProfileSchemaParityTest` already pins the schema. All 13 dispositions are `coderabbit {section} {id} — {sha|reason}` in the progress file.

### 5. Reviewers

- **coderabbit** (required, quota-limited, `review_complete_after_handling`): reviewed 1a179d30 (COMMENTED, marker names it) and again f6d0e66f (3 actionable + 1 additional, all handled in 4798b488). `review-gate` on the merge head 4798b488: `merge_allowed: true`, `unresolved_review_threads: 0`, `review_body_findings_handled: 13/13`, `required_reviewer` satisfied. Two threads were replied to and resolved on 2026-11-25; the third was already resolved.
- **sourcery** (optional, quota-limited): refused on `hard_quota` (250 000 diff characters, next 2026-11-30); no re-trigger.

### 6. Operator questions

1. (2026-11-24) "D1's test-first run exposes that the spec's hard cross-repo predecessor is open: pm-api 0.1.0-SNAPSHOT still ships profiles without `headless.usage.model`; how should the run proceed?" — options {stop and wait for the clients PR; proceed, do not merge; proceed, land the clients change first}; **selected "Proceed; do not merge"**.
2. (2026-11-24) "D4's text says nested `model_differs {launch, served}`; the binding re-ground record says pm-api ships flat `launchModel`/`servedModel` — which wire shape?" — options {flat `launch_model`/`served_model`; nested `model_differs`}; **selected "Flat launch_model/served_model"**.

No question was asked in the resume session: the blocker that the first selection guarded against had cleared, so the run proceeded on the operator's standing decision to start the plan and the lane's own merge rule.

### 7. Rule check

- Guard: none on this harness (unenforced); no guard log exists.
- Slips of the run's own actions, recorded rather than excused: the first session ran one targeted build without `--project-dir` (re-run against the worktree immediately, it decided nothing), issued two `ls` and one `grep` through the shell, and this session issued one inline-Python heredoc while editing `UsagePointers` (it printed a placeholder and decided nothing), one `git commit -am` (one file was modified, the commit shows exactly one file changed), and read the harness's own truncated tool-output file under `~/.local/share/opencode/tool-output/` to obtain the rest of this skill's text — outside the repository root, which the rules forbid.
- Builds: 42 in total over both sessions (30 + 12), every one routed (`mechanism=daemon_longpoll` in the `[BUILD-SERVER] resolved build` line); none fell back, none was a launch fault, none was killed. Two runs answered `indeterminate` (a `-Dtest` selector that executed nothing); each was re-run with a working selector and decided nothing.
- Spec read: `spec_source=ledger_worktree`, no `spec_differ`.
- `executor-skew`: `match` (0.1.2183 = 0.1.2183) at the first session; `match` again on the resume (0.1.2183 = 0.1.2183), and `resolve` reported `executor_version: 0.1.2184` at landing time.
- `CoverageItemTest.keepsAnAbsentFileAndBranch` failed once in the first session with "coverage item name must not be blank" and passes on re-run on the worktree and on main: a pre-existing flake of `Generators.nonEmptyStrings()`, not this plan's code.

### 8. Build server

Setup preflight: `ready`. Every build: `daemon_longpoll`, no fallback, no retry. Setup `executor_in_worktree: present`; `build-routing` answered `auto`. No `killed` job. `runtime_stamp_reason` absent. The landing-time `resolve` reports `executor_in_worktree: absent` with `worktree_not_created`, which is the post-cleanup state, not a build-time degradation.

### 9. Residue

- **One residue referred to layer 1**: a harness can alternate invisible-character variants of one model and so write one `model_differs` entry per variant. The change condition is defined on the reported identifier and the settled comparison is exact/alias on that string, so this is the specified behaviour; only a product decision to compare the visible form would change it.
- **Owed outside this repository**: the `model_differs` display in the pm-mcp-clients clients; the `pm-mcp-server` wiring of `ServedModelWatch` and `served_models`; the recorded per-version harness outputs (PM-WATCH-CLIENT-20).
- The PR body was updated after the merge queue's head was read; that is a body edit and changed no tree.
