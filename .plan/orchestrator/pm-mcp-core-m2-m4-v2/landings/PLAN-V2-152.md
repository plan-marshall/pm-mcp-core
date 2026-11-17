# Landing Analysis: PLAN-V2-152 — Assertion messages of the pm-workflow tests

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #471 (merged `bd2a90b8`, 31 files) and #474 (merged `ae6c32a9`, 41 files)

> Landing record for one merged plan, **delivered as specified, no blocker and no major finding**. Written by `analyze` (inbox scan) from the lane's
> landing message `rb-v2-152-assertion-messages-workflow-001.md` (`deliverables_done` 2 of 2, `merge_state=merged`, `cleanup_owed=false`). One
> read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) read the two diffs through `git show` and ran two scratch ports of the shared
> scanner over the merged tree (nothing built, no repository file written). The orchestrator re-read the `ProjectUnitLoaderTest` message against the
> constructor, the five "covered once" messages, and the owner plan of the scanner residue.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 a message on every assertion of the `pm-workflow` tests | met | the leaf's independent scan of 175 files at `ae6c32a9`: 3 620 assertion calls, 0 without a message, 3 174 literal messages all within 20..60 characters, 443 non-literal (below) |
| D2 the thin guard | met | `pm-workflow/src/test/java/de/planmarshall/workflow/testing/AssertionMessageScanTest.java` is the provider guards' code with `BASE_PACKAGE = "de/planmarshall/workflow"`, `AssertionMessages.scan(root, BASE_PACKAGE, Map.of())`; no scan logic copied; not vacuous (the base resolves to the real tree, the exemption map is empty, an empty scan is itself a violation) |
| no assertion removed or weakened | met | no `@Test`/`@ParameterizedTest`/`@Nested`/method line removed in either diff; `#474` adds the guard's one `@Test`; assertion-call tokens equal on both sides; 71 files compared skeleton against skeleton, every hunk appends a message |
| per-package counts in each PR body, at most 100 files, CI and Sonar green | met by the lane's account | 31 and 41 files; CI, Sonar and the links check were not re-run |
| Expected Surface | clean | all 72 changed paths below `pm-workflow/src/test/java/de/planmarshall/workflow`; no production code, no pom |

## Three-Layer Analysis

### Layer 2 — Implemented

| Id | Point | Verdict | Severity |
|----|-------|---------|----------|
| L2-1 | the mutation-after claim is plausible: the removed message exists at `EnvelopePackerTest.java:289` on a measured shape, the failure line `AssertionMessageScanTest:50` is the `assertTrue(violations.isEmpty(), …)` line, the pm-core control proves the shape | confirmed by reading | ok |
| L2-2 | the 13 CodeRabbit threads: three fixed in `#471`, five declined with sound reasons (closure pins present at `1e9466ac`, the spec forbids more than a message), five wrong-parameter messages fixed in `#474`; all re-read in the merged code | confirmed | ok |
| L2-3 | the landing message counts 67 changed files; the PRs hold 31 + 41 = 72 | confirmed arithmetic slip | info |

### Layer 3 — Correct

| Id | Point | Verdict | Severity |
|----|-------|---------|----------|
| L3-1 | `ProjectUnitLoaderTest.java:621-623` asserts `new ProjectUnitLoader(null, defaults, roster, files)` with the message "a null class loader is refused"; the first parameter is `VerificationContext context` (`ProjectUnitLoader.java:144-146`, re-read): the same defect class as five of the 13 bot findings, which the bot missed | confirmed | minor |
| L3-2 | five messages claim coverage the hand lists do not prove: "every state behaviour / parameter type / call value / decision declaration / guard result is covered once" (`AstNodeModelTest.java:536,566,582,599,627`, re-read); they compare two handwritten lists, not the sealed hierarchy (the declined `#471` finding) | confirmed | minor |
| L3-3 | 443 assertions in `pm-workflow` carry a non-literal last argument (199 plain, 244 supplier or method reference: `rendered` x58, `refused.getMessage()` x22, `printed` x20, `name` x9, …); the guard accepts and does not measure them, so 20..60 and "says what is checked" are unproven; pre-existing, untouched by the PRs; PLAN-V2-114 items 28 and 32(b) cover `pm-core` and `pm-runtime` only | leaf count, not re-counted | info |
| L3-4 | `assertAll` is not in the scanner's list, so a heading without a message is not measured (item 32(a)); `pm-workflow` uses string headings | leaf reading | info |
| L3-5 | the decline reply of `#471` sends the derivation of the hand-listed catalogs from the enums to PLAN-V2-114, which has no such item: item 29 is scanner hardening. The staged plan that touches `AstNodeModelTest` is PLAN-V2-125 (its Order point, line 38) | confirmed (grep: no item in 114) | info, unowned |
| L3-6 | PLAN-V2-114 item 29's three latent scanner shapes do not occur in `pm-workflow`; item 33(d) (`ScenarioScriptTest.java:385-388`) is now covered by this plan except the dynamic `REFUSED.toString()` message at `:458` | confirmed by the leaf's greps | info |
| L3-7 | the guard's imports differ from the provider guards (a double blank line at `AssertionMessageScanTest.java:15-16`, `org.junit` before the `pm-core` import) | leaf reading | cosmetic |

## Review and Quality Numbers

| Number | Message | Re-read |
|--------|---------|---------|
| unresolved review threads | 0 | not re-queried; the 13 threads' fixes re-read in the merged code |
| review body findings total / handled | 5 / 5 (13 inline findings over two PRs by the lane's own count) | the lane's count; CodeRabbit reviewed `3a64ec39` and `48a352fd`, the later heads stand under the standing exemption |
| Sonar open new issues | 0 | gate `ok` by the lane; not re-queried |

`landing-check`: `complete=false`, missing `total_tokens` and `plugin_version` (E27). Surface delta unmeasured. The lane reports shell file reads (`grep -c`,
`awk`) and one `rm` of its own temporary test against the rules; no effect on the merged tree.

## Residue

1. **Carried into PLAN-V2-114, item 45:** L3-1, L3-2, L3-3 (the 443 non-literals, joined to items 28 and 32(b)), L3-4 as a pointer to item 32(a), L3-5
   (the catalogs derived from the enums, in the files PLAN-V2-125 also reads), L3-7.
2. **Not routed:** the landing note's file count (L2-3, a wrong number in a message; the record here states the right one).
