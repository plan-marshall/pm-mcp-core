# PLAN-V2-193: A worker's write set is confined to its role, and the main-checkout check holds only where nothing confines

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: doc-review-2026-11-24-001.md section 1 ("Worker write set and the main-checkout guard"; job-runtime/02:17-19, phase-workflows/02:242); operator's cut, plan 6.
lane: plan-marshall

> Staged plan spec — ready for `/plan-marshall` hand-off. Lives at `plans/PLAN-V2-193-worker-write-set-and-main-checkout-guard.md`; one queue row, lane `plan-marshall`. The orchestrator EMITS the command; it never launches the plan inline.

## First Instruction: Read The Standing Rules

Read `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` completely first; say so with the revision in the first progress note and repeat revision and rule ids in the landing message. Drafted set: R02, R05, R06, R08, R10, R11, R13, R14, R18, R19, R20 (orchestrator confirms). Plan parameters: R06 developer documentation `pm-runtime/doc/developer/runtime-job.adoc` and `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc`; ADR directories `pm-runtime/doc/adr/runtime-job/` and `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/` (006 amended, new ADRs for the two decisions); layout WS-06.

## Objective

`JobConfinementComposer.forJob` gives every job the project root and every `write_paths` entry. The specification confines a worker by its role: a `writer` worker writes only the worktree of the plan its task serves and the project's `.marshall/local/temp/`; a `read_only` worker and the worker of a chat write no path of the project; `write_paths` entries inside the project root are never part of their set. With the worker confined, the main-checkout check of the commit holds only where the platform confines no job (`confinement: unavailable`).

## Deliverables

Three deliverables, about 25 files, no split needed.

1. **The composer takes the role class and the plan worktree.** `JobConfinementComposer.forJob` (:151-196, Javadoc :32-37) gains both inputs for a worker (kind `LLM`, :183-189) and builds the narrower set; `WorkerStart` carries the role class and the plan worktree, `WorkerSupervisor` (:317) fills them, `SubprocessJobRunner` passes them at :614 (the worker launch); the call at :1020 serves every other job kind and keeps the project root (pass neutral values). Tests: `JobConfinementComposerTest` (:202-220 pin the old worker set), `JobEnvironmentComposerTest`, `WorkerStartRecordsTest`, `WorkerJobTest`, `EgressJobTest`, `GitMetadataJobTest`, `OptionalAbsenceContractsTest:58`.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first, named by the finding: a `writer`'s set contains its plan worktree and `.marshall/local/temp/` and NOT the main checkout, NOT another plan's worktree (set membership at the composer; the refusal itself is `pm-exec`'s); a `read_only` worker's set holds no path of the project; a chat worker the same; an `write_paths` entry inside the project root is dropped for `read_only`/chat and kept for `build` jobs and `writer` workers; the credential-location rule and the git-metadata read-only paths still hold; `zero_tool` role: see HYPOTHESIS. Red at e451ed04; then the code.
2. **The main-checkout check is conditional.** `FootprintBoundary.java:21-23,41-51`, `CommitSite.java:118-124` (`mainCheckoutBreaches`): empty where the plan's jobs ran confined; the check holds where `confinement: unavailable`; `status.schema.json:169` `entry_baseline` description ("a hard error in every phase") says the same; tests `FootprintBoundaryTest` (:55-91), `GitStageAndCommitPrimitiveFixtures:118`; `provider-git-primitive.adoc` (:95, :166), ADR provider-git-primitive/006.
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc, pm-dev-java-cui:cui-testing
   - Persona: implementer
   - Test order: first the statement in the tests (a violation by a main-checkout path is judged as before when the site reports one; the port contract says the site reports none under enforced confinement), then Javadoc, schema description and documents. The condition itself is decided by the assembly's `CommitSite` (plan-marshall-mcp), which this repository cannot change; see HYPOTHESIS.
3. **Documentation and ADRs.** `runtime-job.adoc:343` (the composer's write set), `package-info.java` of `runtime.job` (:70), ADR runtime-job/014:28 (the write set is the project root: amended), new ADRs for the role-scoped write set and for the conditional check, `doc/Traceability.adoc` (the confinement row naming `JobConfinementComposer`).
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when** (against `origin/main` e451ed04): the write set of a `writer` worker is its plan worktree plus `.marshall/local/temp/` and excludes the main checkout and every other worktree — fails at e451ed04 (the set is the project root); a `read_only` and a chat worker's set holds no project path — fails at e451ed04; the write set of a `build`, `project_script`, `git`, `ci` and probe job is unchanged — holds at e451ed04 (`JobConfinementComposerTest` :101-260); the credential-location exclusion and the read-only git metadata of a worker are unchanged — holds at e451ed04. `verify -Ppre-commit`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am`, `verify -pl pm-runtime,pm-providers/pm-provider-git -am` green (targeted `-Dtest` where a full verify exceeds the host budget). Sonar: 0 open new-code findings at the head or each key selected by the operator.

## Standing Constraints

- A removed behaviour deletes the tests that pin it; no shim for the old wide set.
- Read cited lines at the head first (PLAN-V2-162 and #612 changed neighbouring files). No dependency or plugin; at most 100 files per pull request; no model-facing content.
- Sonar: fix or dismiss only on the operator's selection; an undecidable read blocks.

## Claim Labels

- OBSERVED: `JobConfinementComposer.forJob` adds `projectRoot` and every `enrolment.writePaths()` entry for every kind (:163-169), then per kind (:175-194); kind `LLM` adds harness paths, the job-private `data/`, read grants and the git metadata read-only paths.
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: Substance holds but lines moved: forJob is :155-200; projectRoot and every enrolment.writePaths() entry are added at :167-173 (not :163-169); per-kind switch is :179-198 (not :175-194); LLM case :187-193 adds harnessWritablePaths, jobPrivate data/, read grants, gitMetadataInside read-only.
- OBSERVED: the callers are `SubprocessJobRunner.java:614` (`WorkerStart`, `request.writablePaths()`) and `:1020` (every other kind, `List.of()`); `WorkerStart` (:40-42) has no role class and no worktree; `WorkerStartRequest` (:42-44) carries `role`, `workdirKind`, `planId`, `workdir`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: SubprocessJobRunner.java:614 forJob(request.enrolment(), jobId, request.writablePaths(), false) and :1020 forJob(..., List.of(), false); WorkerStart.java:40-42 components have no role class or worktree (reachable only via launch.roleClass()/workingDirectory()); WorkerStartRequest.java:42-43 has role, workdirKind, planId, workdir.
- OBSERVED: `RoleClass` is `writer`, `read_only`, `zero_tool` (`pm-core/.../role/RoleClass.java:22-29`).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: pm-core/src/main/java/de/planmarshall/core/role/RoleClass.java:22-29 enum RoleClass: WRITER(writer), READ_ONLY(read_only), ZERO_TOOL(zero_tool).
- OBSERVED: the specification states the target at `job-runtime/02-confinement-and-environment.adoc:18-19`; the conditional check at `state-catalogue/01:240` and `store-schemas/13:56`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: job-runtime/02-confinement-and-environment.adoc:18 (writer: plan worktree and .marshall/local/temp/; read_only and chat: no project path) and :19 (write_paths inside project root dropped for them); state-catalogue/01:240 and store-schemas/13:56 state the main-checkout check only where confinement is unavailable.
- OBSERVED: `FootprintBoundary.check(stagingSet, pinnedFilesChanged, mainCheckoutBreaches)` is a pure judge; `CommitSite.mainCheckoutBreaches` is implemented by the assembly (ADR provider-git-primitive/006).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: FootprintBoundary.java:44-56 check(StagingSet, Collection pinnedFilesChanged, Collection mainCheckoutBreaches) is a static pure judge; CommitSite.java:124 declares mainCheckoutBreaches as an interface method, class doc says the assembly implements it; ADR provider-git-primitive/006 Decision: 'which the assembly implements'.
- HYPOTHESIS: the plan worktree path is `WorkerStartRequest.workdir` of a `PLAN_WORKTREE` start and the chat worker is launched as a `read_only` role — confirm at `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerStartRequest.java:42-59`, `LaunchRequest.WorkdirKind` and `WorkerSupervisor.java:317` (verify-at-outline).
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: WorkerStartRequest.java:42-59: PLAN_WORKTREE iff planId != null, JOB_PRIVATE iff workdir == null, so workdir is the plan worktree path; LaunchRequest.java:83-89 WorkdirKind; model-work/01:137 gives chat-reply class read_only. Note WorkerStart is built at WorkerSupervisor.java:386-400 (:390), not :317.
- HYPOTHESIS: a `zero_tool` worker is treated like `read_only` (no project path) — confirm at the same files and `job-runtime/02:18`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: job-runtime/02:18 does not name zero_tool, but model-work/04-worker-launch.adoc:183 runs zero_tool workers in the empty private cwd/ 'which they can read and never write'; LaunchComposer.java:83 gives zero_tool WorkdirKind.JOB_PRIVATE; so no project path is writable, as for read_only.
- HYPOTHESIS: deliverable 2 is Javadoc, schema description and documents only, `FootprintBoundary` staying a pure judge; the code that makes the answer conditional is the assembly's — confirm at `CommitSite.java:103-124` and ADR provider-git-primitive/006:22-37; if the outline finds a seam in this repository, the deliverable grows by that class.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: CommitSite.java:103-124 is an interface (check 3 :118-124) whose class doc says this module implements none of it; GitStageAndCommitPrimitive.java:233 only calls site.mainCheckoutBreaches; no confinement logic in pm-provider-git main; ADR 006 puts the decision in the assembly's CommitSite.
- Verify-first clause: the enrolment's `write_paths` of a `writer` worker outside the project root (toolchain caches) stay as the specification says; confirm at job-runtime/02:19.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: job-runtime/02-confinement-and-environment.adoc:19 keeps the enrolment write_paths toolchain caches (~/.m2/repository, ~/.gradle, ~/.npm, ~/.cache/pip); only entries inside the project root are dropped for read_only and chat workers, and stay writable for build jobs and writer workers.

## Expected Surface

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobConfinementComposer.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/WorkerStart.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerSupervisor.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/package-info.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobConfinementComposerTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobEnvironmentComposerTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/WorkerStartRecordsTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/WorkerJobTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/EgressJobTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/GitMetadataJobTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/OptionalAbsenceContractsTest.java`
- OBSERVED: `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/FootprintBoundary.java`, `pm-providers/pm-provider-git/src/main/java/de/planmarshall/provider/git/primitive/CommitSite.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/FootprintBoundaryTest.java`, `pm-providers/pm-provider-git/src/test/java/de/planmarshall/provider/git/primitive/GitStageAndCommitPrimitiveFixtures.java`
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/store/status.schema.json`
- OBSERVED: `pm-runtime/doc/developer/runtime-job.adoc`, `pm-runtime/doc/adr/runtime-job/014-confinement-and-environment-composed-outside-the-launcher.adoc`, `pm-providers/pm-provider-git/doc/developer/provider-git-primitive.adoc`, `pm-providers/pm-provider-git/doc/adr/provider-git-primitive/006-the-primitive-checks-the-boundary-half.adoc`, `doc/Traceability.adoc`

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`

## Dependencies and Sequencing

- Depends on: the schema-and-store-removals plan (`status.schema.json`); PLAN-V2-162 (open: JobLog, SupervisionPass, WorkerSupervisor, runtime job and worker; it also owes `runtime-job.adoc`) — this plan STARTS AFTER 162 MERGES, because it edits `WorkerSupervisor` and `runtime-job.adoc`.
- Overlaps with: PLAN-V2-126 (test conformance of runtime and providers: `JobConfinementComposerTest`, `FootprintBoundaryTest`, the fixtures — runs after this plan or rebases over it; #612 already merged test changes there: re-read); the lane-entries plan P3 and the outline plan P2 on `status.schema.json` (different members).
- Adjacent to: `pm-exec` (pm-mcp-clients) enforces the set; the assembly's `CommitSite` (plan-marshall-mcp) decides the conditional check.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-193-worker-write-set-and-main-checkout-guard.md"
```

## Write-Boundary

The plan touches only its own repository source and tests; it creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message (`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary).

## Re-Ground Record of 2026-11-27 (cleanup, head 115d5987)

Binding, like every earlier record: where a claim above and this record disagree, the record is what is true at the head; the claim bullets are not rewritten. Read by read-only leaves (R21) from the head `origin/main` 115d5987; each contradicted claim is absorbed here (`rescoped: yes`).

- Claim 0: Substance holds but lines moved: forJob is :155-200; projectRoot and every enrolment.writePaths() entry are added at :167-173 (not :163-169); per-kind switch is :179-198 (not :175-194); LLM case :187-193 adds harnessWritablePaths, jobPrivate data/, read grants, gitMetadataInside read-only.
