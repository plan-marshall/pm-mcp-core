# Landing Analysis: PLAN-V2-137 — records and builders residue

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #432 (merge `87e14af3`), `merged` through the merge queue, no label; 94 files, +564 / −198 (diff `f82f3e73..87e14af3`)

> Landing record written by the `analyze` drain of 2026-11-15 from the message `rb-v2-137-records-builders-residue-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, executor 0.1.2125, standing rules r7). The message is a lead. The three layers were read by one read-only leaf from git at `87e14af3` (no build, no test run); the orchestrator re-read the points marked "re-read".

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 positional calls to builder calls | met, one process breach | 37 sites in the 16 production files the spec names (the diff has exactly those 16); the leaf re-ran the scan over every `new X(` with 4+ arguments of an `@Builder` record in `src/main` of pm-core, pm-workflow, pm-runtime and the providers: what is left is codec/parser/reader code, the records' own factories and constants, and the sites the spec names as exempt. All 37 conversions were checked against the record component order: every chain names every component in order, no default is relied on, no argument order is changed. Breach: `ProviderVerification:232,256` and `ProviderVerificationRecordsTest` were converted although the spec (item 9) says to leave them until PLAN-V2-143 has merged; re-read: both files are in the diff (+14/−2, +6/−2) and PR #434 of 143 is still open; the PR body and the lane message do not say so. No textual conflict is reported (mergeable); 143's lane meets `Verification.builder()` in its target file. |
| D2 `WorkerGenerationRecordsTest` | met | the `toString()` comparison is a loop over `getRecordComponents()` (`:84-89`) with `assertEquals(18, components.length)` and values drawn away from the builder defaults; the Javadoc names the mechanism. Weak spot: two same-typed components could be swapped unnoticed when their random values collide (low probability). The lane's four mutation runs are unverified. |
| D3 a message on `assertThrows` | met | 81 `assertThrows` carry a message (80 in `*RecordsTest`, 1 in `ResponseBuilderTest`, 75 classes); all 81 measured, 32-43 characters; the diff changes only those lines. Five messages name the component loosely ("enrolment" for `file`, "start instant" for `processStartedAt`, "work dir" for `workingDirectory` ×2, "hash" for `configHash`). Messageless `assertThrows` left outside this plan: pm-core 383, pm-workflow 255, pm-runtime 185 (owners: PLAN-V2-101 to 105). |
| D4 `runtime-guard.adoc` | met on the evidence | new section "What the rule does not see" (`runtime-guard.adoc:98-109`, five gaps and the whole-class exemption); the gap claims rest on a reverted probe that the leaf could not re-prove; the provider blind spot is still in its old place (`:118-119`, not absorbed); "Client Profiles § The Request" still stands at `:60` and `OutboundCallsArchTest.java:~160` (the spec dropped the rename). |
| D5 six failure-report messages | met | re-read: `OutboundCallsArchTest.java:353,363,371,421,435,443` carry `withReport("…", result)` (`:272`), check texts 37-45 characters. `withReport` builds the report eagerly on every call; the old message was a lazy supplier (a `Supplier<String>` overload exists). |
| D6 one predicate | met for "one definition", partly for "same condition" | `connectionEvidence` (`:218-227`) is read by `connectionFree` (`:199-210`) and `opensConnection` (`:249-251`); logically equal to the old pair; controls kept; the symmetry test shares the predicate but does not evaluate the `ArchCondition` itself; the sentence test compares two literals of the same file (the lane said so itself). |
| D7 `CLAUDE.md` | partly met | edits `CLAUDE.md:127-129` (ArchUnit bullet), `:147`, `:148` ("within its set", a fourth edit explained in the PR body), `:149` (link). Not done: the sentence "The six above are the operator's answers" (`:124-126`) is unchanged although the spec item (8) said it must change; re-read: it now sits directly above a seventh entry. Grounding: the new bullet says the operator's word of 2026-10-29 covers `pm-runtime` and `pm-core`; ADR `023:66` (re-read) says the word covers `pm-runtime` only, and `pm-core/pom.xml` has carried ArchUnit since #31 (2026-10-11, before that word). The spec's own correction told the lane to name both; the PR body says the operator's record could not be confirmed. |

Done-when: D1 (with the breach), D2, D3, D5 met; D4, D6, D7 as in the table. Files outside the Expected Surface: none; one line over 120 characters (`OutboundCallsArchTest`, the `DestinationClass` Javadoc, 123).

## Three-Layer Analysis

### Layer 1 — Specified
Coherent and grounded; the State line holds (12 + 25 sites, D4 shrunk, six assertions). Decided by the lane where the spec left room: `KindMapping` (`ActivityEventMapping:86,87,89,91,93`, 8 arguments with 5 to 7 `null`s) left positional as codec-adjacent (the weakest call of the plan: it is the case the builder rule exists for); `ForgeUrl:290,291,344` left positional as a URL parser (the outer `ForgeUrl` call is not named in the PR body); `OwedAuditEvents:151` and `HarnessEnrolmentStore:259` (a reader of a `JsonNode`, 9 arguments) left positional as readers, not named in the PR body; the fourth CLAUDE.md edit; a different reason for not renaming "Client Profiles § The Request" than the spec's.

### Layer 2 — Implemented
As the table. Two records still lack `@Builder` (`PrimitiveResult`, `HarnessVerification.Request`); the spec says neither has an external site (the only one is `PrimitiveResult:167` inside its file).

### Layer 3 — Correct
No behaviour change found; no assertion removed or relaxed; the D6 test is stronger than before; no AAA comments; `throws Exception` in `WorkerGenerationRecordsTest:47` is the gate's rewrite in a test. Findings by weight: (1) the `ProviderVerification` breach (procedural); (2) the unsupported pm-core attribution and the unchanged "six above" sentence in `CLAUDE.md`; (3) eager `withReport`; (4) the symmetry and sentence tests of D6; (5) the unnamed positional exemptions.

## Leftovers (placed, none asked)
- PLAN-V2-114 new item 23: the lazy `withReport` (supplier), the D6 symmetry test evaluating the `ArchCondition` and the sentence test reading `EXEMPTIONS`, the 123-character line, the provider blind spot absorbed in `runtime-guard.adoc`, the stale "Client Profiles § The Request" at `runtime-guard.adoc:60` and `OutboundCallsArchTest:~160`, the five loose component names, the `KindMapping` decision (convert or record it in the code), the three unnamed reader/parser exemptions, `@Builder` on `PrimitiveResult` and `HarnessVerification.Request`, and the CLAUDE.md "six above" sentence.
- Messageless `assertThrows` elsewhere: PLAN-V2-101 to 105 (their own scopes).
- The CLAUDE.md claim that the 2026-10-29 word covers `pm-core` is the operator's to confirm or correct (open for the operator, not asked now).

## Unverified leads
The lane's mutation runs and the probe run, the 10 548-test count, Sonar, CI and the link check; whether #434 (143) needs a rebase onto the `Verification.builder()` calls (the PR reports mergeable).
