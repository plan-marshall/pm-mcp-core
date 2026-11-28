# PLAN-V2-215: The liveness package texts say what the specification says: the sleep span, the build slot, the awaiting bound, the CI-wait record, the heartbeat and the watch lists

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
origin: the layer-3 analysis of PLAN-V2-213 (`landings/PLAN-V2-213.md`, #644 `7c307340`, the orchestrator's analysis of 2026-11-28) and the advisories of the lane's landing message, staged by the orchestrator on the forty-fourth run, 2026-11-28. Documents only: NO Java, no specification.
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-215-liveness-package-texts-corrections.md` and is queued as one row file, `queue/PLAN-V2-215.json`,
> in the epic ledger (lane `runbook`). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R05, R07, R08, R10, R11, R19, R20. (R02, R14 and R18 concern code and do not apply: this plan edits no Java; R19 names `doc/plans/README.adoc` as shared bookkeeping, governed by R08.)

Plan parameters: R06 none (the work package files are the product here); ADR none; layout WS-10. **Read the skill first (R20):** `ref-asciidoc` of `pm-documents`. After the last edit, scan every changed file for leftover tool-call markup (`</content>`, `</invoke>`, `<parameter`, `antml:`): the previous plan left such lines in three files and `asciidoc validate` does not report them.

## Objective

PLAN-V2-213 wrote the package texts of the liveness guarantee. Its analysis found six statements that are false or contradict the specification, one decision the packages call open although the specification answers it, one open decision that four packages consume and two name, and Done When lists that omit watch items anchored to the sections their tasks link. A code plan built from these texts would pass its Done When and fail the specification's guard. This plan corrects the texts. It changes no specification and no code.

## Deliverables

All references are read at `7c307340` (the head after #644) and re-read at the head of `main` before editing; drop what a merge since then settled and name the commit.

1. **The sleep span, the heartbeat and the first task of the stall clocks (`doc/plans/stall-clocks-and-overdue-waits.adoc`, `doc/plans/runtime-metrics.adoc`).** (a) Task 2 of the stall-clocks package and task 2 of `runtime-metrics` say "Open: under which wait reason a sleep span is attributed in the wait slices"; the specification STATES it: the span "is attributed as a pause in the wait slices" (`runtime-lifecycle/02-plan-pause-and-stalled-plans.adoc:72`, Clock Gap; `timeouts/02-runtime-leases-operator-web.adoc:89`, Clock Gap Threshold; the Clock-gap tests; PM-WATCH-LIFE-4). Say that. The real tension is elsewhere: Wait Attribution defines `paused` as the operator's pause alone (`job-runtime/01-pipeline-and-subprocesses.adoc:171`, repeated by `metrics.schema.json:123` and `deliberate` at `:164`). Name THAT as the open decision, in one sentence, with no owner and no guess: whether `paused` is widened to cover a sleep span or the span gets a reason of its own. Say that the stall-clocks Done When and Tests carry two parts of the LIFE-4 guard the package omits: the pause attribution, and "a deadline due within the gap is processed by its own rule" (`runtime-lifecycle/01` recovery step 7). (b) Task 1 lists the position (`state`, `call_path`, `instance_version`) as new: `StalledHold` already carries `state` (`StalledScopeDetector.java:145`) and `AuditEvent.planStalled` writes it; only `call_path` and `instance_version` are new. (c) Task 4 says `PeriodicSweep` is "the one stamp point built": no stamp exists (a grep of `pm-runtime/src/main` finds no heartbeat besides an unrelated comment in `SupervisorCalls`); `PeriodicSweep` is the one place a stamp can be taken. The package also carries the 60-second heartbeat threshold (`timeouts/02` `#heartbeat-threshold`) with a sweep that runs every 3600 seconds (`PeriodicSweep.java:50`, `timeouts/02` `#periodic-sweep-interval`): a heartbeat stamped once per pass and judged by its own stamps is late 59 minutes of every 60. Say so as SPECIFICATION DRIFT TO REPORT (an open question of the specification's owner, `runtime-lifecycle/01` `#runtime-self-watch`), name no owner, and keep the requirement as the specification states it.
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter
2. **The release of a slot whose job ended (`doc/plans/model-slot-release.adoc`).** (a) Left out says the build slots "are lease-store slots already released by `LeaseRecoveryStep`": FALSE. `LeaseRecoveryStep` releases only on caller-supplied `AbandonmentEvidence`, whose default `NONE` finds none, "so the step never releases" (`LeaseRecoveryStep.java:28`, `:51`), and its Javadoc leaves slot releases to `JOBS_AND_WORKERS` (`:33`), where `JobRecoveryStep:238` skips a settled job. The specification applies the end-fate rule to "a build or model slot" (`store-schemas/13-leases-integrity-testing.adoc:17`, `runtime-lifecycle/01` recovery step 4), and PM-WATCH-STORE-63 covers the model slot only: the build slot's twin has no owner. Say that, as owner-open, and drop the sentence. Task 1 names `LeaseRecoveryStep` among the paths to change: keep that consistent with the corrected sentence. (b) The package says a stop between the end fate and the release leaves the slot "until a later admission reaps it as a dead runtime's slot" (`ModelSlotScheduler.reap`, ADR `runtime-launch/005`); STORE-63's hazard says no evidence releases it: say both, with the line of each, and that the two are reconciled by the build of the task. (c) The Trace links Model Work § Model Slots, to which PM-WATCH-DISP-31 (`implementation-watch/model-work.adoc:592`) is anchored and which ADR `runtime-launch/005` cites: name it in the Done When, deleted where a named test covers its guard or rewritten to its remainder.
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter
3. **The awaiting bound, and the CI-wait record that four packages consume (`doc/plans/awaiting-bounds.adoc`, `doc/plans/stall-clocks-and-overdue-waits.adoc`, `doc/plans/process-and-overview.adoc`, `doc/plans/finalize-pipeline-and-merge.adoc`, `doc/plans/README.adoc`).** (a) `awaiting-bounds` task 2 and its Tests omit "no row is unbounded" (`timeouts/01-job-budgets-and-waits.adoc` `[#awaiting-bounds]`, the sentence after the closed list); "the bound is one of the closed list" does not carry it, because the list holds `operator` and `resident`, which have no bound by design. Say what the registry test pins: every registered awaiting primitive has a row, and no row names no source. (b) The persisted record of the CI wait (its expected duration, its last observed change and the `Retry-After` deferral) has no owner and no store member (`store-schemas/02` says only "composite wait facts"). Two packages say it is open (stall-clocks Left out, `finalize-pipeline-and-merge` task 3); two consume it without saying so (`process-and-overview` task 4, the elapsed time against the wait's expected duration; stall-clocks tasks 1 and 3, `awaited` and the overdue hold): add the one-sentence open note to both, and have the README row of the stall-clocks package say that its task 3 starts after that decision (it shows "depends on nothing"). (c) `plan-and-epic-control` Tests say `observe_now` "observes the wait at once": the specification says "within the provider's re-poll cadence" (`state-catalogue/01-conventions-generic-conditions.adoc:277`); say that. (d) `re-planning` task 4 says the reason `prerequisite_cycle` "joins the closed list" of rejection reasons: it is already in `mcp-tools/04-errors-and-testing.adoc:214` of the documentation checkout (re-fetch `origin/main` of `plan-marshall-documentation` first; the local checkout lags): say it is there.
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter
4. **The Done When watch lists (the class of omission the analyses of PLAN-V2-210 and 213 both found).** Re-derive each list from the anchors of the sections the tasks link, in this edit, and read each item to say whether it is deleted where a named test covers its guard, rewritten to its remainder, or stays open for another package: (a) `start-from-clients` task 5 links Store Schemas § Lesson Record, to which PM-WATCH-STORE-14, 15, 16, 19, 20, 34, 58 and PM-WATCH-CLI-4 are anchored: none is named or excluded (they probably belong to the lesson store: say which package). (b) `epic-auto` task 1 and `start-from-clients` task 8 link § Operator Local Configuration: both name PM-WATCH-STORE-62; PM-WATCH-EXT-29 (`implementation-watch/domain-extensions.adoc:499-503`) is anchored there too. (c) `project-onboarding-unit` task 4 links the whole of `domain-extensions/04-setup-state-machine.adoc`: PM-WATCH-EXT-27, 28, 29, 33 and 34 are anchored to other sections of that file: name the ones the task's sections carry, exclude the rest by their section. (d) The Done When lists of the three new packages (`awaiting-bounds`, `stall-clocks-and-overdue-waits`, `model-slot-release`) re-derived against the sections their tasks link.
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter

**Owner-open, reported and not created:** the sleep span's wait reason (deliverable 1), the build slot's end-fate release (deliverable 2), the owner of the CI-wait record (deliverable 3), the heartbeat's cadence against the sweep (deliverable 1). A package text states each as open in one sentence and names no owner.

**Done when** (against `origin/main` `7c307340`): `git grep -n "Open: under which wait reason" -- doc/plans` finds two lines at `7c307340` and none after; `git grep -n "already released by .LeaseRecoveryStep" -- doc/plans` finds one line at `7c307340` and none after; `awaiting-bounds` states that no row is unbounded in task 2 and in the Tests; the open note on the CI-wait record stands in all four consuming packages; every link and anchor the diff adds resolves (`git grep` of the target id); the Done When list of each package named in deliverable 4 names the watch items anchored to the sections its tasks link or excludes each by its section; no leftover tool-call markup in any changed file (`git grep` of `</content>`, `</invoke>`, `antml:`); no Java, no specification and no schema file is changed.

## Standing Constraints

- **Read every cited line at the head first** and drop what a merge settled. The lines are those the orchestrator's analysis read at `7c307340`.
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason.
- **A package text states the specification, not the design of the code.** Where the specification answers a question, the package says what it answers; where two specification statements disagree, the package names the disagreement as open and does not choose.
- **Documents only.** No dependency or plugin is added. No more than 100 files per pull request.

## Claim Labels

All lines read at `origin/main` `7c307340` by a read-only leaf and re-read by the orchestrator on 2026-11-28, except where marked.

- OBSERVED: the specification attributes a sleep span "as a pause in the wait slices" — read at `timeouts/02-runtime-leases-operator-web.adoc:89` and `runtime-lifecycle/02-plan-pause-and-stalled-plans.adoc:72`
- OBSERVED: `LeaseRecoveryStep` releases only on `AbandonmentEvidence`, default `NONE` finds none (`LeaseRecoveryStep.java:28,51`); `model-slot-release.adoc:17` says the build slots are "already released by `LeaseRecoveryStep`"
- OBSERVED: `PeriodicSweep.INTERVAL` is 3600 seconds (`PeriodicSweep.java:50`) and the heartbeat threshold row is 60 (`timeouts/02` `[[heartbeat-threshold]]`, `:76-78`)
- OBSERVED: the stray tool-call lines of PLAN-V2-213 were removed (commit `6e4a9cf7`) and none remains under `doc/` at `7c307340`
- HYPOTHESIS: `prerequisite_cycle` is in the rejection-reasons list of the documentation repository's `mcp-tools/04-errors-and-testing.adoc:214` — confirm/refute after `git fetch` of that checkout (verify-at-outline)
- HYPOTHESIS: the watch items named in deliverable 4 are anchored as listed — confirm/refute at `doc/implementation-watch/store-schemas.adoc`, `domain-extensions.adoc`, `cli.adoc` (verify-at-outline)
- Verify-first clause: if a statement of a deliverable was corrected by a merge since `7c307340`, the deliverable shrinks to what is left; say so in the landing message.

## Expected Surface

- OBSERVED: `doc/plans/stall-clocks-and-overdue-waits.adoc`, `doc/plans/runtime-metrics.adoc`, `doc/plans/model-slot-release.adoc`, `doc/plans/awaiting-bounds.adoc`, `doc/plans/process-and-overview.adoc`, `doc/plans/finalize-pipeline-and-merge.adoc`, `doc/plans/plan-and-epic-control.adoc`, `doc/plans/re-planning.adoc`, `doc/plans/README.adoc`
- OBSERVED: `doc/plans/start-from-clients.adoc`, `doc/plans/epic-auto.adoc`, `doc/plans/project-onboarding-unit.adoc`

Not surface (R19): `doc/adr/README.adoc`

## Dependencies and Sequencing

- Depends on: none (PLAN-V2-213 is merged, #644).
- Overlaps with: none that the gate finds (no other staged or running plan names these package files; PLAN-V2-193 names code and `runtime-job.adoc`).
- Blocks: the code plans of the liveness guarantee (the awaiting bounds, the hold record and stall clocks, the overdue waits, the self-watch, the release of a model slot), which the orchestrator stages after this plan has merged: they are built from these texts.
- Adjacent to: PLAN-V2-211 and the package `epic-auto`.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-215-liveness-package-texts-corrections.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository documents. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports its outcome through its PR and its inbox message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
