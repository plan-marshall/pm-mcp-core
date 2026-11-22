# Landing Analysis: PLAN-V2-20 — git-commit-primitives

epic: pm-mcp-core-m2-m4-v2
workstream: WS-01
pr: #545 (merge `c0d14b96`, 51 files, +5814/-115; `pm-provider-git`, its test generators, one `pm-workflow` verify class, ADRs, the guide, the specification)

> Landing record, plan-marshall form (`plan-v2-20-git-commit-primitives`, harness claude, model `claude-sonnet-5-5`, effort `unknown`, plugin and executor 0.1.2173, 5 of 5 deliverables, 65.85 M tokens, 112 000 s). Landing message `-005` (2026-11-22T17:23:55Z, `merge_state=merged`). `landing-check`: incomplete on `effort`, `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled` (`unknown`); re-read from the provider by the orchestrator: 16 review threads, **0 unresolved**; three CodeRabbit review bodies, each answered by a "Triage dispositions" comment; SonarCloud quality gate passed; Sonar `sonar_open_new_issues=0` stated by the lane. The PR was opened on 2026-11-21; six loop-back rounds were spent and `main` moved nine times. Four candidate lessons (`plan-marshall:phase-6-finalize`, `plan-marshall`, `phase-3-outline`, `platform-runtime`) were forwarded to plan-marshall's `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-057` to `-060`.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 rules and staging set (`CommitTrailer`, `CommitMessages`, `StagingSets`, `FootprintBoundary`) | shipped-modified | `resolve` is over `@Nullable Stored` (the spec's `Optional` parameters break the repo rule), `StagingSets.derive` is on `ScopeType` (no `ScopeKind` exists); additions: control-character and lone-surrogate rules, a Unicode line-terminator split, a name that refuses U+0085/2028/2029; four tests exist |
| D2 `git.resolve-message`, `git.template-message` | shipped-modified | `GitResolveMessagePrimitive:88-148`, `CallSite` enum (the nine call sites of `workflow-dsl/06:260-269`, the spec named one), `TYPE_PRIORITY` as specified; a refused message goes to `needs_interactive` |
| D3 `git.stage-and-commit` | shipped-modified | order of work as specified (footprint check first); `CAPABILITY_MISSING` to `failed`/`commit_failed` and `ATTEMPT_CAP` by operator decision 2026-11-21 (ADR `provider-git-primitive/005`); `trailer_source` fact added; `CommitSiteFixture` replaced by EasyMock mocks |
| D4 kind `commit-message` and `commit-message.evaluate` | shipped (kind, primitive); fixture check not shipped | `EntityKindRegistry:105`, `CommitMessageEvaluatePrimitive`; `GitCommitPrimitivesVerificationTest` cannot live in `pm-provider-git` (enforcer) and moved to the assembly (ADR `workflow-verify/043`); the control "refuses when one primitive is left out" does not exist in this repository |
| D5 documentation, ADRs, specification, watch, traceability | shipped-modified | ADRs `provider-git-primitive/001` to `007`, `workflow-verify/043`, `provider-git/004` amended; the guide `provider-git-primitive.adoc` (324 lines); `PM-WATCH-DSL-7` kept with the CLI half open, `PM-WATCH-DSL-46` new; `Specification.adoc` count 40 to 41 |

Operator-widened scope, **re-read at `origin/main`** and as the message says: `JGitOperations` admits globs literally (`:459-475`), refuses a leading `:`, an absolute path, a backslash, a control character and an empty, `.` or `..` segment (`isRefusedPathText`, `:487-494`), commits a link as a link (`NOFOLLOW_LINKS`, `:506-509`), confines the parent by `toRealPath` of the parent or its nearest existing ancestor (`:529-545`) and checks the index without `HEAD` (`:560-577`). The weakened-test row is as stated: `RegistriesAndRecordsTest.entityKindRegistry` is split into five tests keeping every assertion and message (one assertion added); `EntityKindRegistry.kinds()` is a new package-private accessor no deliverable names.

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`); no file written, no build run; spec and message passed as data from the scratchpad.
- **Re-read by the orchestrator at `origin/main`:** `GitResolveMessagePrimitive.java:88-103` (the `NONE_ASSERT_CLEAN` arm returns `READY` with an empty fact map when the tree is clean), `GitStageAndCommitPrimitive.java:117-124` (`Params.message` is `requireNonNull`), the spec text `workflow-dsl/06:268-290,333` (it routes `none_assert_clean` to `git.compose-message` only for unexpected dirty files and does not say where a clean tree goes after `ready`); the ADR numbers of `workflow-verify` (`041` twice, `042`, `043`); provider counts of threads and bodies. **Not re-read, kept as the leaf's claims:** every L3 probe of the path rule and the trailer rules, the test-class sizes, the Traceability and ADR 043 wording.
- **Verdict:** layer 1 findings (a false premise: `GitOperations` was not read-only for this plan); layer 2 modified, not missing; layer 3 **no blocker, no confirmed major, one lead that is a major if confirmed (L3-1)**; the probes found no confinement bypass and no trailer injection.

### Layer 1 — Specified

| Finding | Claim | Verdict | Severity |
|---------|-------|---------|----------|
| L1-1 | D1 `resolve(Optional, Optional)` | contradicts "Optional is a return type only"; shipped as `@Nullable` | minor (spec text) |
| L1-2 | D1 `StagingSets.derive(ScopeKind, ...)` | no `ScopeKind`; shipped on `ScopeType` | minor |
| L1-3 | D3 `CAPABILITY_MISSING` to `hook_failed` | contradicted Stage 4 (`06:283`); settled by operator decision 2026-11-21 | minor |
| L1-4 | Constraint "`GitOperations` and `JGitOperations` are read, not edited; `CommitInput.paths` is PLAN-V2-15's" | false premise: the shipped paths contract followed links for the directory check, a dirty symlink failed with `PATH_REFUSED` and a path below an outward link was not confined; this forced the widened scope | major (premise, closed) |
| L1-5 | D4 `GitCommitPrimitivesVerificationTest` in `pm-provider-git` | impossible under the provider enforcer; its Done-when cannot be checked here | minor |
| L1-6 | D4 "no existing assertion of the defaults test changes" | unsatisfiable together with Sonar cleanliness once the validator reads test names | minor |
| L1-7, L1-8 | D2 "refused combination of the contract"; D2 names one call site, `06:260-269` lists nine | uncheckable as worded; the spec under-stated the primitive | minor |
| L1-9 | Hand-off 3 "no staged owner" | confirmed unowned (see Residue) | minor |

### Layer 2 — Implemented

Per deliverable as in the table above. Open advisories of the message, each verified at `origin/main` by the leaf: (1) `doc/Traceability.adoc` (`PM-EXEC-5` at `:1432` and the `846` entry) says every path is confined, the primitive confines only the directory of a name; (2) an embedded repository or modified submodule is listed as `dir/` by `git status`, staged by `StagingSets` and refused by `JGitOperations` (fails closed as `commit_failed`); (3) a `:`-led derived path: `StagingSets` has no `:` rule and no test; (4) `local-config.schema.json:155-162` (name `^[^<>\r\n]+$`) admits U+0085/2028/2029 that the code refuses, fail-safe (`invalid_ignored`); and the code's `\s` includes U+0085, the schema's does not; (5) ADR `workflow-verify/043` says no assertion of the defaults test changed (true for assertions, imprecise for tests); (6) no test makes the ancestor itself dangling; (7) four test classes are over 400 lines (`JGitOperationsTest` 988, `GitStageAndCommitPrimitiveTest` 597, `CommitTrailerTest` 411, `GitResolveMessagePrimitiveTest` 407). Files no deliverable explains: the operator-widened set, `state-catalogue/01`, `doc/adr/README.adoc`, the generator classes, `EntityKindRegistry.kinds()`.

### Layer 3 — Correct

| Defect | File:line | Trigger | Effect | Severity |
|--------|-----------|---------|--------|----------|
| L3-1 | `GitResolveMessagePrimitive:97-99`, `GitStageAndCommitPrimitive:117-124` (re-read) | `none_assert_clean` on a clean tree returns `ready` with no `message` fact; `ready` routes to `git.stage-and-commit`, whose `message` is required | **lead, unverified:** nothing in this repository shows how the engine binds the absent fact; if it refuses, every clean-tree `finalize.push-barrier` call fails; the spec does not name the route | lead (major if confirmed) |
| L3-2 | `StagingSets:105-126`, `JGitOperations:487-513` | a dirty entry `StagingSets` accepts and `JGitOperations` refuses (`:` lead, backslash, control character, `sub/`, a submodule path) | the whole plan commit fails closed as `failed`/`commit_failed` (`PATH_REFUSED`) before any staging; availability, not integrity | minor |
| L3-3 | `GitStageAndCommitPrimitive:180-182` | a bound `files` with a typo or a path outside the staging set | `retainAll` drops it silently; a partial commit goes through | minor |
| L3-4 | `JGitOperations:384-391` | `git.add` before `hasChanges` | on `NOTHING_TO_COMMIT` the listed paths stay staged; unrelated entries are never committed (`setOnly`, tested at `:193`) | minor |
| L3-5 | `CommitTrailer:45,83-88` | a stored trailer name with NUL, ESC or other C0/C1 controls | reaches the trailer line; no line injection; NUL may truncate in some consumers (unverified) | minor |
| L3-6 | `GitStageAndCommitPrimitive:210-213`, `store-schemas/08` | no `commit_identity` | spec says `identity_missing`, the primitive returns `commit_failed` with a `detail` | minor |
| L3-7, L3-8 | `GitStageAndCommitPrimitive:241-242`; `CommitSite:53-62` | an absolute root path in `detail`; a rename in porcelain output | leads: the path may reach model-facing facts; the contract does not say a rename lists both sides | lead |
| L3-9 to L3-11 | `CoAuthoredBySourceScanTest:46-56`; `hook_failed` and `attempts_exhausted` declared with no producer; a symlinked ancestor swapped between check and add | a literal-string scan only; documented (ADR 005); a TOCTOU inherent to a path check | minor / info / lead (low) |

**Layer 3 clean:** `..`, `.`, empty segments, absolute paths, backslash and control characters are refused; a path below an outward link is refused; the `Co-Authored-By` rule refuses the key in any case and position and splits at the Unicode terminators; the staging set per scope puts every dirty path in exactly one of `staged` and `outside`; `setOnly` keeps unrelated index entries out; the empty set is `nothing_to_commit` without a git call; the outcome mapping is total (`GitOutcome.values()` test).

## API (for the assembly in `plan-marshall-mcp`)

All in `de.planmarshall.provider.git.primitive` (`pm-provider-git`, depends on `pm-core` only): `CommitSite` (the interface the assembly implements: `worktreeRoot`, `scopeType`, `archiving`, `dirtyPaths`, `localTrailer`, `machineTrailer`, `commitIdentity`, `agentIdentity`, `diffSummary`, `pinnedFilesChanged`, `mainCheckoutBreaches`, and `DiffSummary`); `GitResolveMessagePrimitive(CommitSite)` (`git.resolve-message`, `Params` with a builder, `MessageMode`, `CallSite`); `GitTemplateMessagePrimitive` (`git.template-message`); `GitStageAndCommitPrimitive(GitOperations, CommitSite)` (`git.stage-and-commit`; outcomes `committed`, `nothing_to_commit`, `hook_failed`, `attempts_exhausted`, `failed`; reasons `footprint_violation`, `message_refused`, `commit_failed`; `cycleMeasure ATTEMPT_CAP`); `CommitMessageEvaluatePrimitive` (`commit-message.evaluate`); `CommitMessages`, `CommitTrailer`, `StagingSets.derive(ScopeType, Path, Collection, boolean)`, `FootprintBoundary.check`. `pm-workflow`: `EntityKindRegistry.defaults()` holds `commit-message`. `GitOperations.commit` documents `PATH_REFUSED` and literal paths.

## Hand-offs open for others (the message, confirmed by the leaf)

1. `plan-marshall-mcp` `engine-wiring`: the `CommitSite` per scope, the `PrimitiveRegistry` of the four primitives, the `InputBindingContext` calling `StagingSets.derive`. 2. `default-units-and-drift-tests`: the production `git-commit-and-push.pmwf` (routing `failed`), the `StateCatalogueDriftTest` rows, `GitCommitPrimitivesVerificationTest`. 3. The engine's mapping of `footprint_violation`, the attempt counters, the `change-ledger.jsonl` row, `head_at_completion`, `freshness_reconcile` — no staged owner (L1-9). 4. The git CLI variant (PM-WATCH-DSL-7; keep unrelated index entries out, set the author variables). 5. The `agent_identity` derivation (PLAN-V2-17, PLAN-V2-12).

## Residue and its placement

No existing STAGED plan owns `pm-provider-git` code (PLAN-V2-126 has only test conformance of the providers); a new regular cleanup plan, **PLAN-V2-169** `git-commit-primitives-closeout`, carries item 87: L3-1 (the clean-tree route, ask-at-refine), L3-2 with the missing `StagingSets` colon-led test, the `dirtyPaths` contract for nested repositories and submodules, L3-3, L3-6 (the code of a missing identity, ask-at-refine), the dangling-ancestor test, the `commit_trailer` schema against the code, the Traceability and ADR 043 wording, and the over-400-line test classes. The Hand-off 3 items stay with the assembly's plans (outside this repository) and are recorded as a Watch.

## Lane process observations (not defects of the code)

A `ci-verify` record at the 10:05 UTC head was green while the PR was DIRTY and no workflow had run (review bots counted as CI): corrected at the next head; forwarded as a candidate lesson (`-057`). Review closed at six heads by operator override over one `weakened_test` validator row. The landed squash message carries the PR body (with the CodeRabbit summary) and no `Co-Authored-By: plan-marshall` trailer: the repository default is `PR_TITLE`/`PR_BODY` and the CI surface cannot change it (a PR comment records it). `main` carries two ADRs numbered `041` in `workflow-verify`, which PLAN-V2-161 item 80 renumbers; `043` is now taken by this plan, so the target moved to `044` (spec 161 updated).
