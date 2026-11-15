# Landing Analysis: PLAN-V2-144 — the dead null checks that kept the gate of main red

epic: pm-mcp-core-m2-m4-v2
workstream: WS-10
pr: #433 (merge `f82f3e73`), `merged` through the merge queue, no label (the first attempt, #425, was superseded)

> Landing record written by the `analyze` drain of 2026-11-15 from the message `rb-v2-144-main-green-dead-null-checks-001.md` of the runbook lane (harness opencode, executor 0.1.2125, standing rules r7). The message is a lead. The three layers were read by one read-only leaf from git at `f82f3e73` (no build, no test run); the orchestrator re-read the points marked "re-read".

## Deliverable Fidelity vs Spec

1 file, +3 / −6: `pm-runtime/src/main/java/de/planmarshall/runtime/endpoint/EndpointVerification.java`; inside the Expected Surface; no test, pom, dependency, suppression or doc change.

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 remove the two dead null checks | met | re-read: `ownsTheNamedProgram` (`EndpointVerification.java:125-130`) lost `|| profile.executable() == null`; `probe` (`:296-299`) lost the `identity == null` branch and the local, and reads `profile.identity()` twice. Both are dead because the record `EndpointProfile` (`pm-core/.../core/profile/EndpointProfile.java:50-51`, re-read) refuses a null `executable` and `identity` in its compact constructor, the package is `@NullMarked`, and the components are not `@Nullable`; the record has no other constructor. |
| D2 prove the gate | met | the lane called `deploy-snapshot` "not evidenced" because the `ci` surface cannot list a run by sha. Re-read by `gh run view 43667603742` (Maven Build, push, headSha `f82f3e73`): `build / sonar-build` success, `build / gate` success, `deploy-snapshot / deploy` success (the caller job `build / deploy-snapshot` shows `skipped`, a name artifact of the reusable-workflow call). The SonarCloud gate of `main` reads `OK` (re-read by plain HTTP, 2026-11-15). Before the fix (run `43657594701`, `a73716e9`) `sonar-build` failed and the deploy was skipped. |

Lane report corrected: `deliverables_done=1/2` and the residue "deploy-snapshot not evidenced" are too pessimistic; D2 is met.

## Three-Layer Analysis

### Layer 1 — Specified
Coherent and grounded (the two sites and the record that makes them dead are named). Left open and decided by the lane: how to write `probe` once the local goes (`profile.identity()` twice); how to read D2 when no sanctioned verb lists a run by sha. Spec gap: D2 names the job list of the Maven Build run but no verb that reads it; a read-only `gh run list` / `gh run view` reaches it.

### Layer 2 — Implemented
As the table.

### Layer 3 — Correct
Behaviour unchanged for every `EndpointProfile` that can exist; `EndpointVerificationTest` is untouched and has no case on the removed branches (none could exist). The `!lookup.isFound()` arm kept in `ownsTheNamedProgram` is exercised. Remaining null checks of the package at `f82f3e73`: `:142` (`weightsProbe`), `:184` (`decoyResult`), `:263` (`schema`) are on `@Nullable` values, legitimate. One new redundancy: `identity()` (`:135`) still tests `!lookup.isFound()` although `verify()` only calls it after `ownsTheNamedProgram` returned false for a not-found lookup; Sonar does not flag it (cross-method).

## Leftovers
- The redundant `!lookup.isFound()` in `identity()` (`EndpointVerification.java:135`): a candidate for the next plan that edits this class (PLAN-V2-142, harness-verification residue).
- The "found but foreign executable" arm of `ownsTheNamedProgram` has no test (pre-existing): same plan.
- The other null checks of the `endpoint` package (`WeightsReader`, `EndpointEnrolment`, `EndpointOutcome`, `EndpointRequest`, `AnswerSchema`, `EndpointClient`) were not each checked against their declaration; Sonar `main` is `OK`, so none is flagged.

## Unverified leads
The lane's test count and the Sonar `resolved=false` count of #433 (not read); the quota-policy handling that left #425 and opened #433.
