# Landing Analysis: PLAN-V2-101 — every assertion of `core/store` carries a message, and a scanner guards the rule

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #438 (merge `2813564b`), `merged` through the merge queue, no label; 17 files, +463 / −45 (16 `core/store` test classes and the new scanner)

> Landing record written by the `analyze` drain of 2026-11-15 from the message `rb-v2-101-assertion-messages-core-store-001.md` of the runbook lane (harness opencode, model deepseek-v4.1-flash/high, standing rules r7). The message is a lead. The three layers were read by one read-only leaf from git at `2813564b` (no build, no test run; the leaf ported the scanner to a throw-away script and ran the port over the real tree, then deleted it); the orchestrator re-read the points marked "re-read".

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| D1 a message on every assertion in `core/store` | met | the port finds 45 offenders at `2813564b^1` (the spec's census, file for file) and 0 at `2813564b` in all 158 files; 3 878 literal messages are measurable and all are 20-60 characters (21 at exactly 20, 4 at exactly 60); 44 are dynamic and unmeasured. All `-U0` hunks of the 16 files read: every changed line is a message added, reworded, or an argument line wrapped; no expected/actual, lambda or type argument changed; no `@Test` line changed; no AAA comments; no added line over 120 characters. PR body: "pm-core 16 added + 29 corrected = 45 sites". |
| D2 the scanner in `guard` | met, thinly controlled | `pm-core/src/test/java/de/planmarshall/core/guard/AssertionMessageScanTest.java` (402 lines against the 400-line budget, 4 tests): `everyAssertionOfTheScannedPackagesCarriesAMessage` (`:80-96`); controls `:100-114`. A lexer state machine masks comments, strings, char literals and text blocks (`codeView` `:316-401`), then finds the assertion identifiers and splits arguments at depth-0 commas (`:276-298`); a message is measured only when it is a single string literal. The lane's "no message" mutation (`LockOrderTest:95`) is unverified. |
| Done-when: scanner passes / fails on a fixture | met | passes on the scope (main Maven Build green); fails on the planted fixtures. |
| Sonar, CI | met | `pullRequest=438&resolved=false`: total 0; quality gate `OK`; `Maven Build` (`43731998322`) and `Integration Tests` (`43731998170`) of `2813564b` on `main` success. |
| Expected Surface | met | exactly the 16 test files and the scanner. |

## Three-Layer Analysis

### Layer 1 — Specified
Coherent; the State line (45 offenders in 16 files) matched the tree. The lane's lead "a tokenizer is refuted, a regex is enough" was set aside by what shipped: a lexer plus paren-depth splitting, stronger than a regex. **Spec gap with consequences for the follow-ups:** the scanner is a `pm-core` test with `SCANNED_PACKAGES` = `List.of("de/planmarshall/core/store")` (re-read `AssertionMessageScanTest.java:50`) and `testSourceRoot()` resolves only `src/test/java` of the current module or `pm-core/src/test/java` (`:170-183`, re-read). PLAN-V2-102 to 105 still say the scanner "can be a plain regex over `src/test/java`" and that "the plan that lands first creates it; the others extend it"; a `de/planmarshall/workflow/...` or `runtime/...` entry would fail with "no test source was scanned". 103, 104 and 105 therefore need a module-qualified list, a shared helper or per-module scanners (ADR 026 `:126` sets a per-module precedent for `HandWrittenDoubleScanTest`); 102 can extend the list directly.

### Layer 2 — Implemented
As the table. Names known: `assertTrue`, `assertFalse`, `assertEquals`, `assertNotEquals`, `assertNull`, `assertNotNull`, `assertSame`, `assertThrows`, `assertInstanceOf`, `assertDoesNotThrow`, `assertArrayEquals` (the spec's list). The walk is recursive, so a new file or subpackage under `core/store` is picked up; a package outside the list is not covered. Dynamic messages (`String.format`, `.formatted`, a supplier, a concatenation, a constant, a text block) are skipped, stated in the class Javadoc (`:35-37`) and the PR body.

### Layer 3 — Correct
No bug in the 16 converted files; the messages are accurate in context. Scanner findings by weight (from the port, not the Java scanner):
1. False negative: type-argument commas. `argumentSpans` ignores `<>`, so `assertEquals(Map.of(), new HashMap<String, String>());` counts three spans and takes the last as the message, and `assertTrue(Map.<String, String>of().isEmpty());` passes with no message. The Javadoc (`:39-42`) admits it only for single-argument assertions. No current `core/store` file triggers it (grep).
2. False negative: a tolerance is recognised only as a numeric literal third argument; `assertEquals(1.0, v, DELTA);` reads `DELTA` as the message and passes (`:242-244`).
3. Dynamic messages are never measured: 44 exist, 34 of them `…, refusal::getMessage` or `x.getMessage()`, which report the exception text and do not say what is checked; `LeaseCodecTest:351` passes the identifier `description`.
4. Missing assertion names: `fail`, `assertNotSame`, `assertIterableEquals`, `assertLinesMatch`, `assertTimeout`, `assertTimeoutPreemptively`, `assertThrowsExactly`; the port found no offender among them in `core/store`.
5. The boundary controls are thin: the fixtures use 9, 61 and 26 characters (`:105,107,125`); an off-by-one on 20 or 60 would be caught only by the real scan (21 messages at 20, 4 at 60 exist today); no planted control for a text-block message, a type-argument comma, a multi-line call or a qualified `Assertions.assertX`.
6. The failure message prints only the path of `target/assertion-message-violations.txt` (`:94-95`); with thousands of offenders per package in 102-105 the CI log will not show them.
7. `core/guard` is not scanned and is itself non-compliant: `ProviderNeutralityArchTest:181` (re-read: `assertFalse` with no message) and `RoleNameSingleDefinitionTest:152` (62 characters).
8. The PR body's Sonar row still reads "pending" (cosmetic; the message and Sonar say met).
Interplay: #437 (PLAN-V2-94) touches `core/store` main classes (`ClarifiedRequestRenderer`, `IntentAcceptance`, `OutlineRenderer`) and no `core/store` test file: no conflict.

## Leftovers (placed, none asked)
- 102 to 105 specs do not match the shipped scanner (single module, regex hypothesis): adapted in place before any of them is emitted (PLAN-V2-102 State at the emit now; 103, 104, 105 carry a "Carried from PLAN-V2-101" section naming the module-qualified scanner design as their first decision).
- Scanner hardening (type-argument commas, tolerance identifier, missing names, offender list in the failure message, boundary and shape controls, `core/guard` offenders, the 402-line budget): PLAN-V2-102 new section "Carried residue of PLAN-V2-101" (it is the first plan to scan thousands of assertions).
- The 44 dynamic messages, including the `refusal::getMessage` family, are unmeasured by design; no owner, raised only if the operator wants descriptive messages there.

## Unverified leads
The lane's mutation reruns (`a36c360a…`, `1f9dadba…`), the pre-commit, coverage and integration job ids and `BROKEN LINKS 0`; the 400-line budget and the 20-60 rule text of `junit-core` (not read); the generic-comma result is from the port, a divergence in text-block or character-literal handling is possible.
