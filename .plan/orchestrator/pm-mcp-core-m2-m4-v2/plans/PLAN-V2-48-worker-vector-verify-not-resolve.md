# PLAN-V2-48: Verify the composed worker vector without resolving it

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-119 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-V2-48-worker-vector-verify-not-resolve.md` and is queued as one row file, `queue/PLAN-V2-48.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19, R20. (R12 applies only if the trace matrix shows a message to add, see Standing Constraints; R14 and R16 apply because the plan changes `pm-runtime`; R15 does not apply, no `pm-workflow` code changes; R17 because the plan edits a specification part and runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-launch.adoc` (extended); ADR directory: `pm-runtime/doc/adr/runtime-launch/`; layout: workstream WS-04.
- R12 — log message holder: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Make the argument vector of a worker launch pass the argument-schema validation the job runtime gives every job. Model Work § Launch Contract (`doc/specification/model-work/04-worker-launch.adoc:48-49`) says: "Remaining: the verify-not-resolve argument-schema validation of the composed vector, which the worker start path does not run yet", and Job Runtime § Worker Jobs ends with "The argument vector is the one the server composed from the role artifact and the profile's `headless` template … validated like every other". PLAN-29 (#172) delivered the composition and left this; PLAN-30 (#190) shipped the start path without it; the Open Defect of 2026-10-25 records that no plan owns it. At head, `HarnessVerifier` refuses a harness before the spawn and nothing checks what the composer made of the template. The plan adds that check as the last step of the composition: the rendered vector is parsed back against the profile's template and the closed grammar of each typed slot, and a vector that does not conform refuses the launch as `argument_rejected` with the job record's fate `refused`, exactly as every other composition refusal is recorded.

## Deliverables

1. **The check `WorkerVectorCheck`** (new class in `runtime.launch`). It takes the enrolled executable path, the template that was rendered (the resume template for a resumed generation, the class template otherwise, as `composeArgv` chooses) and the rendered vector, and answers the first violated rule and the position, never the value. The rules: (a) element 0 is the enrolled executable path; (b) every element of the vector corresponds to one element of the template, the elements the template skips (a slot with no value removes its flag with it) being the only gaps, and the literal parts of a template element are reproduced exactly; (c) every typed slot value matches the closed grammar of its slot: `{{model}}` is the model the composer resolved and one the effective catalogue allows, `{{effort}}` one of the efforts the profile accepts for that model, `{{prompt_file}}`, `{{result_schema}}` and `{{cwd}}` absolute normalized paths below the job-private directory or, for `{{cwd}}`, the working directory the composer fixed, `{{session_id}}` the grammar of a harness session id (`[A-Za-z0-9._-]{1,128}`), `{{tools}}` a comma list of tool names of the closed grammar of the role's tool set, `{{mcp_config}}` and `{{agent}}` a well-formed JSON object of the composed shape (the relay entry only); (d) no element outside the two JSON slots contains a shell metacharacter (`|`, `;`, `&`, `$`, `` ` ``, `>`, `<`), the rule `ArgumentCheck` applies to every caller-supplied argument of every other kind (the runtime spawns without a shell, so this is the defence in depth the specification names, not a new restriction); (e) the vector carries no element that is not a template element or a slot value (an element a profile edit or a composer defect added).
2. **The wiring in `LaunchComposer.compose`.** The check runs on the composed vector before the `WorkerLaunch` is built; a violation throws `LaunchComposer.CompositionException` whose `refusal()` is the new `LaunchRefusal.ARGUMENT_REJECTED` (code `argument_rejected`, the reason of `JobRefusal.Reason`, with the act that resolves it: correct the profile's template or re-enrol the harness profile). `WorkerSupervisor` already records a composition refusal through `refuseWorker` (the fate `refused`, the reason in the job record); the supervisor is not edited. The detail names the rule and the position of the element, never its value.
3. **The tests** (JUnit 5, `@DisplayName`, AAA, `@ParameterizedTest` for three or more variants): in `WorkerVectorCheckTest`, one conforming vector per shipped template shape of the test fixtures (fresh, warm, resumed, zero-tool with no tools) and one refusing vector per rule (a, b, c per slot, d per metacharacter, e) as a table; a mutation test that removes the check and shows the table failing; in `LaunchComposerTest`, a profile whose template renders a metacharacter into a slot value is refused `argument_rejected` and no job-private file other than the ones the composition wrote before the check is left half-written (the files are written before the vector is composed, so the test pins which exist); a launch that conforms is unchanged byte for byte (the digest of the vector of the existing fixtures does not move). The refusal reaches the job record through the supervisor: one test in `WorkerSupervisorLaunchTest` is allowed to be read, not edited; the end-to-end proof is a test of the new class that composes through a real `LaunchComposer` and a recording starter.
4. **Document the decision.** One ADR in `pm-runtime/doc/adr/runtime-launch/` (next free number) records: the check parses the rendered vector back against the template and the slot grammars instead of re-rendering it (re-rendering would repeat the composer's own code and prove nothing); it runs at the end of the composition, inside the start path, and not in `SubprocessJobRunner` (alternatives: a stage of `VerifyNotResolvePipeline` over a new `JobRequest` kind for workers, which would put a launch-template concept into `runtime.job`; validation in `startWorker`, which would refuse after the slot was claimed and the record admitted); the two JSON slots are exempt from the metacharacter rule and checked as JSON. Extend `pm-runtime/doc/developer/runtime-launch.adoc` (the check, the grammar table, the refusal) and the `doc/Traceability.adoc` lines of `PM-EXEC-1` and `PM-CLIENT-2`.
5. **Delete the implemented specification text** (R05): the sentence "Remaining: the verify-not-resolve argument-schema validation of the composed vector, which the worker start path does not run yet." from `doc/specification/model-work/04-worker-launch.adoc` § Launch Contract (replace it by the pointer to the developer guide the implemented text uses), and the matching "validated like every other" qualifier in `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` § Worker Jobs only if the trace matrix shows the sentence now false or redundant. Before editing, search the test tree for readers of both files (R10) and list each; no block a test reads is touched.

**Done when:** a composed worker vector that does not conform to its template and the slot grammars is refused `argument_rejected` before the spawn and is recorded `refused`; a conforming vector is unchanged; the table of Deliverable 3 passes and fails when the check is removed; the ADR, the guide, the Traceability lines and the specification sentence are done. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-runtime` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-runtime -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** PM-EXEC-1, PM-EXEC-5 (argument validation of every job), PM-CLIENT-2 (the launch contract); `doc/specification/model-work/04-worker-launch.adoc` § Launch Contract; `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` § Verify-Not-Resolve Execution Pipeline (Argument Schema Validation, Worker Jobs); `doc/specification/client-profiles/01-profile-schema.adoc` `headless.argv` (the typed slots, line 235 of the schema; the composer fills `{{session_id}}` in addition for a resumed template, which the closed set of the specification does not list — a gap the trace matrix reports); the Open Defect "the verify-not-resolve validation of a composed worker vector has no owner" and the landing of PLAN-29.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Log messages: none expected.** A composition refusal is recorded through the job record and the existing `WorkerSupervisor` path; the check logs nothing itself. If the trace matrix shows one, it goes into `RuntimeLogMessages` at the next free identifier of its level, and the plan says so in its inbox message.
- **The slot grammars are this plan's design and the ADR is where they are stated.** Where the trace matrix finds a slot of a shipped profile (`pm-api`) that no grammar above covers, or a template literal the check would refuse in a shipped profile, the plan stops that slot and asks the operator at refine (R07) instead of widening a grammar; a refusal of every launch of a shipped harness is the failure this constraint exists to avoid.

## Claim Labels

- OBSERVED: the specification names the gap — `doc/specification/model-work/04-worker-launch.adoc:48-49` ("Remaining: the verify-not-resolve argument-schema validation of the composed vector, which the worker start path does not run yet."); `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc:53` (last sentence of the Worker Jobs bullet: "validated like every other")
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: files read at a8da4104
- OBSERVED: the worker start path runs no argument check — `SubprocessJobRunner.startWorker` (`pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java:541-575`) admits the record and runs the launch with `launch.argv()` without a pipeline call, while `start` (line 520) calls `pipeline.verify`; `grep -rn "VerifyNotResolve\|ArgumentCheck" pm-runtime/src/main/java/de/planmarshall/runtime/worker pm-runtime/src/main/java/de/planmarshall/runtime/launch` is empty
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: files read and grep at a8da4104
- OBSERVED: the composer fills the template by string replacement and re-checks nothing — `LaunchComposer.composeArgv` (`pm-runtime/src/main/java/de/planmarshall/runtime/launch/LaunchComposer.java:278-310`) and `fill` (line 372); the slots are `model`, `effort`, `prompt_file`, `result_schema`, `tools`, `cwd`, `mcp_config`, `agent`, `session_id` (lines 286-294); the composition's refusal type is `CompositionException` with `refusal()` fixed to `PROMPT_COMPOSITION_FAILED` (lines 449-466)
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: file read at a8da4104
- OBSERVED: `LaunchRefusal` has six values and none is `argument_rejected` (`LaunchRefusal.java`); `JobRefusal.Reason.ARGUMENT_REJECTED("argument_rejected")` exists (`JobRefusal.java:56`) and the job record's `refusal_reason` pattern `^[a-z][a-z0-9_]*$` admits it (`doc/specification/store-schemas/10-job-and-lesson-records.adoc:117`)
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: files read at a8da4104
- OBSERVED: `WorkerSupervisor` records a composition failure through `refuseWorker` with `failure.refusal()` (`pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerSupervisor.java:285`), so a new `LaunchRefusal` value needs no supervisor change
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: file read at a8da4104
- HYPOTHESIS: the slot grammars of Deliverable 1 are broad enough for every shipped profile and narrow enough to refuse an injected element — the shipped templates live in `pm-api` (`pm-mcp-clients`), not in this repository; confirm/refute against the profiles the tests use (`LaunchFixtures`) and the specification's profile schema, and against the shipped profile bundle through the SNAPSHOT of `pm-api` if it carries the profiles (verify-at-outline)
- HYPOTHESIS: a test that reads `model-work/04-worker-launch.adoc` or `job-runtime/01-pipeline-and-subprocesses.adoc` may exist (R10) — search the test tree before editing; the two sentences are prose, no schema block
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-runtime/src/main/java/de/planmarshall/runtime/launch/`, re-read `LaunchComposer.composeArgv` and `LaunchRefusal` at the head of `main`; a refutation of any claim above loops back to re-scope.

## Expected Surface

- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/WorkerVectorCheck.java` — new class
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/LaunchComposer.java` — the call after `composeArgv`
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/launch/LaunchRefusal.java` — `ARGUMENT_REJECTED`
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/launch/WorkerVectorCheckTest.java` — new test class
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/launch/LaunchComposerTest.java` — the refusal and the unchanged vector
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/launch/LaunchFixtures.java` — a fixture template with a metacharacter slot, if the table needs one
- OBSERVED: `doc/specification/model-work/04-worker-launch.adoc` — specification part; the "Remaining" sentence of § Launch Contract replaced by the pointer
- OBSERVED: `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` — specification part; the Worker Jobs sentence only if the trace matrix shows it redundant
- OBSERVED: `pm-runtime/doc/developer/runtime-launch.adoc` — extended (shared bookkeeping, R19)
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-launch/` — one ADR, the next free number (shared bookkeeping)

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: nothing. PLAN-29 (`worker-launch`, #172) and PLAN-30 (`supervisor`, #190) are shipped.
- Sequenced 2026-10-29 (decision 14 of 18): PLAN-V2-59 (`egress-record`) is the only plan of this group that edits the pinned job-record block of `store-schemas/10` (and `JobRecord`, `JobRecordCodec`); run it first and merge the base before your gate.
- Overlaps with, by file: `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc` is declared by PLAN-V2-42 (`runtime-metrics`, staged: "the implemented sections deleted"), 1 shared entry, only if this plan edits that part; `doc/specification/model-work/04-worker-launch.adoc` by no live plan. No live plan declares `LaunchComposer`, `LaunchRefusal`, `LaunchFixtures` or their tests (PLAN-V2-42 names `ModelSlotScheduler` of the same package; PLAN-97 and PLAN-98 are shipped). PLAN-46, PLAN-105, PLAN-V2-39 and PLAN-V2-43: no shared file (PLAN-V2-39 declares `HarnessEnrolmentStore` and the harness store schema, not the launch package). The other plans of this cluster: disjoint, no shared file.
- Adjacent to: `pm-api` of `pm-mcp-clients` (the shipped profiles and their templates): a change of a template there is a pull request in that repository; the Quarkus assembly stays untouched.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-48-worker-vector-verify-not-resolve.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

Open items of the previous epic that this plan owns (register: `owner-register.md` of the epic; the full text is in the archived ledger `pm-mcp-core-m2-m4/epic.md`). They are not deliverables and extend no Done-when: read each while outlining, re-check it against `origin/main` (Claim Labels) and either adopt it into a deliverable of the outline or say in the landing why not.

- **The verify-not-resolve validation of a composed worker vector has no owner (found at the PLAN-2** (Op33) — the verify-not-resolve argument-schema validation of a composed worker vector is not run by the worker start path; Model Work § Launch Contract says "Remaining: ..."
