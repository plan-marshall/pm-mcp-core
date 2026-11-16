# Landing Analysis: PLAN-V2-48 — Verify the composed worker vector without resolving it

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #442 (merge `8b438c45`, head `d113d710`), `merged` through the merge queue; 11 files, +1194 / −20

> Landing record written by the `analyze` drain of 2026-11-16 from the message `rb-v2-48-worker-vector-verify-not-resolve-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, effort high; `total_tokens` unknown, `plugin_version` unknown: `landing-check` reports `complete: false`, missing `total_tokens` and `plugin_version`, the known defect E27). Every point marked re-read was read again by the orchestrator at its `file:line` at `8b438c45` (`git show`), not taken from the lane or the leaf.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 `WorkerVectorCheck` (rules a to e) and `ARGUMENT_REJECTED` | shipped-as-specified | `LaunchRefusal.java:47-49` carries `ARGUMENT_REJECTED("argument_rejected", …)` (re-read); `WorkerVectorCheck.java:70` is a `@UtilityClass`, `Input` has nine components and `@Builder`, `check` returns `Optional<Violation>`; no generic catch (leaf, conformance rows read). |
| D2 refusal reaches the job record | shipped-as-specified | `WorkerSupervisor.java:284-289` catches `CompositionException`, calls `refuse(...)` with `failure.refusal()` and releases the slot (re-read); no supervisor edit needed. |
| D3 the table, the conforming shapes, the mutation clause | shipped-modified | `WorkerVectorCheckTest` has the 4-shape conforming table and the refusing table; the clause "fails when the check is removed" rests on two out-of-tree runs of the lane (`d42584e6`, `746a7cad`), because no mutation tooling exists in the repository — not re-run, not re-verifiable by reading. `RecordingStarter` (`WorkerVectorCheckTest.java:285`, re-read) is a private class that try/catches `CompositionException` itself: the committed proof is "the composer throws", not "a supervisor records `refused`" (the spec made `WorkerSupervisorLaunchTest` read-only). |
| D4 ADR 010, guide, Traceability, specification sentences | shipped-modified | all four artifacts exist; `04-worker-launch.adoc` and `01-pipeline-and-subprocesses.adoc` state the parse-back and link to `#_the_composed_vector_check` (leaf, anchor at the guide's line 99). Five sentences are false or incomplete (see layer 2). |
| D5 | shipped-as-specified | commit `d42584e6` of the lane; spec D5 clauses read by the leaf as met. |
| Behaviour outside the spec text | added-unplanned | `LaunchComposer.java:307` withholds the ephemeral flag from the template before the fill (re-read), replacing the post-fill `removeIf`; this was CodeRabbit's inline-1 and is the better design. Side effect (leaf): a template that is only the flag now throws "rendered empty". |

## Three-Layer Analysis

Read by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`), no build, no test run, no write; reason: every full ship is read on its own leaf. The orchestrator re-read the points marked above and in the tables.

- **Dispatch:** one read-only leaf per landing — reason: reading merged code is the depth the dispatch rule exists for.
- **Verdict:** layer 1 findings (5 minor); layer 2 findings (documentation and test points, 12 minor); layer 3 findings (latent code limitations, 7 minor and 2 nits; none a defect of what shipped).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---|---|---|---|---|
| L1-1 | D1 rule (d) covers every element outside the two JSON slots, element 0 and the path slots included; this contradicts the spec's own Standing Constraint "never refuse every launch of a shipped harness" for an executable or base path with `&`, `$`, `<` | checked | refutes | `WorkerVectorCheck.java:162-168` (re-read: the loop starts at position 0 and exempts only the JSON slot positions) |
| L1-2 | D1(c) asks `{{model}}` to be "one the effective catalogue allows" but gives the check only the composer's own resolved model | checked | refutes | `WorkerVectorCheck.java:297` (leaf) |
| L1-3 | D3 asks for a mutation test; no tooling exists, so the clause can rest only on a recorded run | checked | refutes | no pitest configuration (leaf, and the lane's own admission) |
| L1-4 | D3 wants the refusal to reach the job record through the supervisor while `WorkerSupervisorLaunchTest` is read-only | checked | refutes | the spec's substitute, a recording starter, cannot prove the supervisor path |
| L1-5 | the hypothesis "confirm against the shipped `pm-api` profiles if the SNAPSHOT carries them" was answerable | checked | refutes | the local `pm-api` SNAPSHOT lists `profiles/claude.json`, `antigravity.json` (re-read with `unzip -l`); `ShippedProfilesTest` reads them through `ShippedProfiles.read()` and asserts a non-empty set (re-read, `:50-52`) |

**Layer 1 verdict:** findings — 5 (minor). A spec shipped, so the findings go to the residue plan.

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|---|---|---|---|---|
| D1, D2 | `WorkerVectorCheck.java`, `LaunchComposer.java:325`, `WorkerSupervisor.java:284-289` | `WorkerVectorCheckTest` tables, `LaunchComposerTest` | none: every file of the diff maps to a deliverable | checked |
| D3 | as above | the tables fail when a rule is removed; the removal of the call: not shown by reading | the "mutation-after" runs of the lane | checked; the mutation runs not shown by reading |
| D4/D5 | ADR 010, guide, Traceability, two specification files | — | see the false statements below | checked |

Unborne claims and test points (all re-read unless stated):

- The lane says the shipped `pm-api` templates "were not readable from here". **Refuted**: the SNAPSHOT jar carries the profiles and `pm-core`'s `ShippedProfilesTest` already reads them; no test composes or checks any shipped profile against the grammars. The leaf's reading of the shipped `headless.argv` templates (single-slot elements only: `prompt_file`, `model`, `mcp_config`, `agent`) is not re-read by the orchestrator.
- Rule (d) is exercised only with a metacharacter in the prompt-file path slot (`WorkerVectorCheckTest` rows); no test puts one in element 0, a literal, or a JSON-slot element, so removing the JSON-slot exemption (`WorkerVectorCheck.java:167`, re-read) passes every test (leaf).
- `LaunchComposerTest.java:306-308` (re-read): the display name says a metacharacter is rendered "into a slot"; the fixture puts ` ;` in the literal tail of the `{{tools}}` element (`LaunchFixtures.java:125-127`, re-read).
- `conformingVectorDoesNotMove`: the digest constant is new, so it cannot show the vector did not move; not shown by reading.
- `RecordingStarter` (`WorkerVectorCheckTest.java:285`) matches the ADR 026 scan pattern (`(class|record|interface|enum) (Fake|Stub|Recording|Failing)`) outside the closed list; it implements no production interface (leaf; the scan itself not re-read).
- No `assert*` line was removed or weakened: `git diff --unified=0` shows no removed assert line (re-run).

False or incomplete statements the change made (all five of the lane's list hold; re-read):

| Statement | Evidence |
|---|---|
| the guide says the check runs "before the ephemeral flag is withheld" | `pm-runtime/doc/developer/runtime-launch.adoc:101-102`; the flag is withheld from the template at `LaunchComposer.java:307`, before the check at `:325` |
| ADR 010 says the check is the last step that can refuse "before the job record is admitted and the model slot claimed" | ADR `:54`; `WorkerSupervisor.java:270` claims the slot before `:284` composes; the same premise props the rejected alternative at ADR `:80-81` (leaf) |
| ADR 010 lists only the prompt file and the result schema as left behind by a refusal | ADR `:101-103`; also left: the MCP configuration file of a `delivery: file` profile (`LaunchComposer.java:292`, composed before the check) and the job's `cwd/` (leaf). The lane's "most consequential doc item" overstates it: the token minted at `:90` is never registered because the refusal carries a null job-token digest (leaf) |
| Model Work § Launch Refusals still ends "or a verify-not-resolve check fails" | `04-worker-launch.adoc:116-118` |
| the state catalogue's fate-`refused` list omits `argument_rejected` | `01-conventions-generic-conditions.adoc:232`; it already omitted `harness_update_unverified` and `harness_store_unreadable`, so it is an older incomplete list (leaf) |
| the act text of `ARGUMENT_REJECTED` names `pm-operator enrol-harness %s` | `LaunchRefusal.java:48-49` (re-read); `enrol-harness` records only path, version and SHA-256 (`job-runtime/01:55`, leaf), so it cannot correct a template; the wording follows the spec's D2 text |

**Layer 2 verdict:** findings — 12 (minor), none a missing deliverable.

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|---|---|---|---|---|
| L3-1 | `WorkerVectorCheck.java:286-288` (re-read) | an element `{{slot}}literal` whose slot value contains the literal; or adjacent slots (`:284-285`) | `carried` takes the earliest occurrence of the next literal, with no end anchor, so a conforming vector is refused `argument_rejected`; one slot plus a trailing literal is enough (broader than the lane said); no shipped template is shaped so (leaf) | minor, latent |
| L3-2 | `WorkerVectorCheck.java:163-167` (re-read) | a metacharacter in the literal part of an element that carries a JSON slot | the whole element is exempt, not only the slot value | minor |
| L3-3 | `WorkerVectorCheck.java:165` (re-read) | an enrolled executable or job-private base whose path contains `&`, `$`, `<` | every launch of that harness is refused and the named act cannot change a path | minor, latent |
| L3-4 | `WorkerSupervisor.java:284-289` (re-read) | any exception other than `CompositionException` out of `compose` (for instance the existing `UncheckedIOException` of the file write) | the model-slot lease taken at `:270` is not released; pre-existing, not new to this plan; the JSON-parse escape the lane feared has no realistic trigger (`JsonDocument` converts its errors to `ProfileRefusal`, leaf) | minor |
| L3-5 | `WorkerVectorCheck.java:333-336` (re-read) | `named.length == 0` | dead branch, `split(",", -1)` never returns an empty array | nit |
| L3-6 | `WorkerVectorCheck.java:332` | a dropped, duplicated or reordered `{{tools}}` subset; any `{{model}}` | membership-only; the model equals the composer's own value | minor, stated limitation |
| L3-7 | `WorkerVectorCheck.java:193-211, 288` (leaf, not re-read) | a composer defect that wrongly nulls a slot | the absent-slot set comes from the composer and `keptIndices` copies the fill algorithm, so the check agrees with the defect: independent for values, not for presence | minor, unverified lead |
| L3-8 | Javadoc and ADR 010 vs `check()` (leaf, not re-read) | — | the rules are said to be reported in the order (a) to (e) but (b) and (e) are reported together, then (c), then (d) | nit, unverified lead |
| L3-9 | `LaunchComposer.java:307` (re-read) | a template that is only the ephemeral flag | now "rendered empty"; before, a one-element vector | nit |

No generic catch, no new dependency, no new log call (leaf). **Layer 3 not shown by reading:** that the 10645-test builds, the coverage run, the integration profile and the links check are green; that the mutation runs failed as claimed.

**Layer 3 verdict:** findings — 9 (minor and nit; none a defect of what shipped, all latent).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|---|---|---|---|
| CLAUDE.md Code Standards | `@UtilityClass`, records with four or more components carry `@Builder`, absence as `Optional`, no generic catch | conforms | `WorkerVectorCheck.java:70`, `Input` at `:397` |
| CLAUDE.md Code Standards | test data from `cui-test-generator`, a literal only where it is the contract | deviates (minor) | `WorkerVectorCheckTest` writes `session-1`, `injected`, `a session`, `/tmp/work`, `not-json`; the class Javadoc names paths, model, efforts and tools as the literal contract but not these (leaf) |
| CLAUDE.md Code Standards | no new hand-written fake; ADR 026 closed list | deviates (minor, disputable) | `RecordingStarter`; the spec itself asked for "a recording starter"; ADR 026's definition may exempt a class with no production interface |
| CLAUDE.md Module Documentation | an ADR per decision, the developer documentation of the changed package | conforms with false sentences | ADR 010, the guide section; false statements listed in layer 2 |
| CLAUDE.md Dependencies | no new dependency | conforms | no pom in the 11 files |
| CLAUDE.md Code Standards | every assertion 20 to 60 characters | conforms, not counted line by line | leaf read all messages at 22 to 45 characters |

## Metrics and Anomalies

- Tokens: unknown (the harness measured none; recorded as unobserved, not zero).
- Duration: not carried.
- Anomalies: the self-review round ran **after** the merge (the lane records it as a rule deviation); it found the five false statements that the pre-PR round exists to catch. CodeRabbit refused on quota four times before it reviewed. The lane's shell-file-operation and pipe deviations are its own record (guard `none` on this harness).

## Routing and Merge Behavior

- Review: CodeRabbit (required) reviewed `3324a26e` after quota refusals (14:53 to 21:58); two body findings, both dispositioned (inline-1 fixed in `6eb9c410`, inline-2 not taken with a reason in ADR 010); Sourcery (optional) refused on its budget. Re-read against the provider on the merged head: `review-gate` 0 unresolved threads, 2 body findings, equal to the landing; `review_body_findings_handled: 2` re-read from the lane's progress file (six `finding:` lines: four Sonar, two CodeRabbit) — consistent; the gate command itself reported `body_findings_undisposed` because it is not given the progress file, which is not a difference.
- CI/merge: merged through the merge queue as `8b438c45`; `merge_state` read from the provider `merged`.
- Sonar: the landing's `sonar_open_new_issues=0`, re-read for `pullRequest=442`: total 0, gate `OK` — equal. Four smells on the first head were fixed in `3324a26e`.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#442`
- [x] row `landing` stamped `landings/PLAN-V2-48.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-48-worker-vector-verify-not-resolve` (the sender, not typed by hand)
- [x] epic.md narrative reconciled
- [x] leftovers placed (below); `landings_incomplete`: `total_tokens`, `plugin_version` unknown, recorded under the known Open Defect E27
- [x] resume anchor updated, `queue-view.md` regenerated

## Follow-Ups

- The five false statements, the `ARGUMENT_REJECTED` act text, the layer-3 limitations and the test points go to PLAN-V2-114 as a new item (the plan owns runtime-launch residue already through item 2(b)); the `ShippedProfiles` conformance test is a new deliverable-sized point inside it.
- L3-4 (the lease leak on a non-`CompositionException`) is pre-existing and goes with item 26's `WorkerSupervisor` restructure (L3-1 of PLAN-V2-94).
- Overlap with PLAN-V2-67 / PLAN-V2-94: none observed.
