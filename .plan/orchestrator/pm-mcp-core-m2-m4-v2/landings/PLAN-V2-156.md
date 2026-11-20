# Landing Analysis: PLAN-V2-156 — analysis-findings-remainder (part one)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #516 (squash merge `5490323a`, parent `1ba62907`, 18 files, +990/-203)

> Landing record, runbook lane (`rb-v2-156-analysis-findings-remainder`, harness opencode, model `deepseek-v4.1-flash/high`, the model the operator had switched to after the free-model episode). Drained from `inbox/rb-v2-156-analysis-findings-remainder-001.md` on 2026-11-20.
> `inbox landing-check`: `complete: false`, missing `total_tokens` and `plugin_version` (both `unknown`). The lane counts 3 of 6 deliverables and ended after this one pull request; it says the rest "carries a successor spec" it does not stage.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1(a) which cited-lesson states mark a revision untrusted | shipped-as-specified | the operator was asked (option "unreadable/integrity states mark, absent does not"); `LessonStore.java:783-796` marks `PresentUnreadable`, `StoreTampered`, `DeletedOutOfBand`, `Untracked` and undecodable staged bytes, `Absent` does not; `store-schemas/10` changed by prose only (two bullets, +2/-2; no schema file, so the parity tests are not affected by reading) and ADR `core-store/079` |
| D1(b) one test per arm, EmptyShell written to a file | shipped-modified | `LessonStoreUntrustedCitationTest` (332 lines): the absent arm, the same-transaction deletion and the malformed refusal fail on the old code; the unreadable/integrity arms are pure-function tests that held on the old code (mutation-after evidence, the lane's log not re-read); the four `get(transaction)` states are never driven end to end through `revise` |
| D1(c) dead `default -> false`, malformed ref | shipped-as-specified | `default` removed (a new variant fails to compile); `requireLessonReferences` runs at the top of `create` and `revise` |
| D1(d) same-transaction deletion | shipped-as-specified, chosen without asking | a deleted target reads untrusted; the arm is reachable only by a hand-built transaction (no production code deletes a lesson file) |
| D1(e) statements and one ADR | shipped-modified | statements present; **ADR 079 contradicts itself** (L1-4) |
| D2 DEL round trip in every front-matter string | shipped-as-specified | `LessonCodec.java:219-245` a `CharacterEscapes` subclass escapes U+007F at the one writer (root cause, central); the title-only refusal and its test are deleted; `LessonCodecControlCharacterTest` covers 13 members plus the title; not covered: `revisions[].sources[].ref`; C0 controls have no lesson-class test |
| D3(a) leading-zero case | shipped-as-specified | `unclaimedOrdersLeadingZeroSequences`, `allocationFollowsTheNormalizedSequence` (`0010` before `011`; fail if `normalized` is the identity) |
| D3(e) retirement rules in `Retired` | shipped-as-specified | `LessonFrontMatter.java:684-692`; a stored record violating them now decodes `Unreadable(RULE_VIOLATION)` (was `found`): documented in the guide, ADR 079 and the PR, no test pins it |
| D3(g) `ScopeTransactionTest` | shipped-as-specified | `OwnState`, three tests (the premise was true: no test referenced the accessors) |
| D3(h) assertion-message sweep, contract notes, loops | shipped-modified | no message needed a change (`AssertionMessageScanTest` already walks the tree); contract notes on the six old classes, the four crash loops are `@ParameterizedTest`; the two new test classes carry no contract note and use prose literals |
| D3(i) R05 reduction of `store-schemas/10` | **not delivered** | the lane added about 650 characters to the document instead |
| D4 null contracts and the 17-component conversion | **not delivered** | not attempted ("the largest piece of the plan") |
| D5(a), (b) the lock-winner failing-run report, the ADR 056 stale-digest gap | **not delivered** | |
| D5(c) ADR links, wraps, Javadoc | shipped-as-specified | |
| D5(d) the PR names the undone items, counts, Sonar by number | shipped-modified | the PR body names the undone items but cites "ADR core-store/077" (the file is 079; 077 is the slot-scope ADR), says the `merged_from[]` gap stays open (false after `6d44a1ae`), has no Sonar number and stale test counts |
| D6 the 57 carried items, each adopted, split off to a NAMED plan, or dropped with a reason | **not delivered in substance** | see below; the lane's "met" is wrong |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: a full-ship landing is read by its own leaf with no size qualifier; the leaf wrote nothing (it created and deleted one scratch file in `.plan/temp/`, disclosed).
- **Re-read by the orchestrator** (all held): ADR `core-store/079` lines 44 and 77-78 against 88-90; the PR #516 body (the ADR number, the `merged_from[]` note, no Sonar figure); the queue statuses of the plans the table names; `runbook_lane review-gate --pr-number 516 --required-reviewer coderabbit` (`unresolved_review_threads: 0`, `review_body_findings_total: 3`: both equal the landing; the gate's `handled: 0` is a form difference, **`review_body_findings_handled=3` re-verified** from the progress file: three `finding:` lines).
- **Not re-read, recorded as unverified leads:** the lane's mutation and failing-run logs (cited, not re-read); whether the assembly (`plan-marshall-mcp`) calls `create` or `revise` with a non-id `lesson` ref or builds `Retired` values the moved rules now refuse; that SnakeYAML accepts C0 escapes (no test); the R17 links check from the main checkout; whether watch `PM-WATCH-STORE-58`'s Guard text still reads right.
- **Verdict:** layer 1 findings (2 minor, rest info); layer 2 findings (minor); layer 3 findings (3 minor, rest info). No blocker, no major. D6 and the undone deliverables are the real gap.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | at `1ba62907` `isUntrusted` returned true for `Absent`, had a dead `default -> false`, `cited.isEmpty() -> false` hid a malformed ref, a delete target read as clean | checked | confirms | the `LessonStore.java` hunks |
| L1-2 | the implementation is faithful to the operator's option | checked | confirms | `LessonStore.java:783-796` |
| L1-3 | `store-schemas/10` changed by prose only | checked | confirms | stat of the document |
| L1-4 | ADR 079 contradicts itself: lines 44 and 77-78 say `revise` reads `merged_from[]` for the mark, lines 88-90 (the last Consequences bullet) still say the half "is not implemented"; commit `6d44a1ae` edited the ADR and left the bullet | checked (re-read) | refutes | ADR 079:88-90 |
| L1-5 | the PR body names the wrong ADR and a closed gap as open | checked (re-read) | refutes | PR #516 body |
| L1-6 | R05 runs the other way: +650 characters of prose in `store-schemas/10` while D3(i) is not done | checked | info | spec 10 |
| L1-7 | D1(d) changes behaviour (a deleted target marks) with no operator question; ADR records the reason | checked | info | `LessonStore.java:763-766` |

No finding names a labelled claim of a staged spec; nothing is persisted by `corpus set-verdict`.

**Layer 1 verdict:** findings — 7

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 | `isUntrusted(ScopeTransaction, LessonId)`: staged content, then a delete target, then `get(transaction, id)`; `create` ORs `citesUntrustedLesson` into the mark; `revise` adds `citesUntrustedMergedFrom` over the pre-revise front matter | see D1(b) above; the wiring of the four integrity states through `revise` is untested (minor) | none | checked |
| D2 | `LessonCodec` escape of 0x7F | 14 members round-trip; C0 and nested `sources[].ref` not covered | none | checked |
| D3 | as above | (a) fails on the identity; (e) untested decode consequence (minor); (g) premise true | none | checked |
| report | n/a | n/a | D5(d) as above; the lane's "D6 met" is wrong; the progress file shows the three findings | recorded |

**Layer 2 verdict:** findings — 4

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | ADR 079:88-90 | reading the ADR | cannot tell whether `merged_from[]` is read | minor |
| L3-2 | PR #516 body | reading it | wrong ADR number, false open-gap note, no Sonar figure | minor |
| L3-3 | `LessonStore.java:744-760` | a caller citing `lessons/<id>.md` | `create` and `revise` now refuse a `lesson` source whose `ref` is no lesson id (the schema says "identifier or store path"); intended and recorded; no caller in this repository, the assembly not read | info |
| L3-4 | `LessonStore.java:404, 775-780` | a future merge verb appending `merged_from[]` and revising in one transaction | `citesUntrustedMergedFrom` reads the pre-revise front matter from disk and misses the new origins (no such verb exists) | info |
| L3-5 | the two new test classes | reading them | no contract note for the literals, prose literals ("A component") that are not a contract (CLAUDE.md, R20) | minor |
| L3-6 | progress file | the R17 links check | run from the worktree against a lagging documentation checkout (3074 reports, filtered by hand), not a measured 0 | minor |
| L3-7 | process | the last production change `6d44a1ae` | came after the three self-review rounds and after the only CodeRabbit review (`4618710b`); it is the commit that left the stale ADR bullet | info |
| L3-8 | `LessonStore.java:189` | a `lesson` source whose cited file or sidecar is unreadable | `create` can now propagate `StoreRefusal` or `UncheckedIOException` | info |

**Layer 3 not shown by reading:** the build and test runs (the lane's counts: 11 352 and 11 568), the Sonar gate, the integration tests.

**Layer 3 verdict:** findings — 8 (no blocker)

### The 57 carried items (D6)

The pull request table has 57 rows, each with a one-sentence reason; all 57 are "split off", none adopted or dropped. The spec's Deliverable 6 asks for a **named** target; the lane says the successor spec "this run does not stage", so no row names an owner that exists. Re-read against the queue: items **1, 4, 8** the spec says to adopt with D4 ("D4 not attempted" is no reason); **2** routed to PLAN-V2-126 (staged, test conformance only: the production `requireNonNull(etag)` is code); **3** PLAN-V2-129, **11** PLAN-V2-131, **7, 28, 29, 31, 32, 38, 41, 45, 46** plans 128, 101-103, 105, 67, 49, 134, all shipped or superseded: origins, not owners; **10** routed to PLAN-V2-125 (the operator chose it for all of it: a valid target); **35** must be done or dropped by name before PLAN-V2-155 is emitted ("PLAN-V2-155's predecessor" is not a decision); **13, 47, 52** "needs a decision", unrouted; **54** defers a question the spec says not to stop for.

## Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| spec Deliverable 6 | adopt, split off to a named plan, or drop, with a recorded reason | **deviates** | the table above |
| spec R07 | ask at refine before a behaviour change | conforms for D1(a) and D3(e) (both asked); D1(d) chosen without asking, recorded in the ADR | |
| CLAUDE.md § Code Standards | literals only where the contract, said in a comment | **deviates** (minor) | L3-5 |
| CLAUDE.md § Work Packages / Module Documentation | an ADR that says what the code does | **deviates** (minor) | L1-4 |
| CLAUDE.md § Git Workflow | the review gate met before merge | conforms | the gate reads 0 unresolved threads and 3 findings, all handled |

## Metrics and Anomalies

- Tokens: `unknown`. Duration: not in the message. The lane waited on CodeRabbit's quota twice (90-minute cycles).
- Anomalies: one `quarkus-alignment` 404 cleared on rerun; one full-`verify` flake in an untouched class (`DiagnosticCollectorRedactionTest`) that passes in isolation; the lane's progress file lists the same shell-operation exceptions it reports.

## Routing and Merge Behavior

- Review: coderabbit required (quota-limited, reviewed `4618710b`, three findings fixed in `6d44a1ae`), sourcery refused on its own quota (optional). The gate reads **both numbers equal the landing's claim**.
- CI/merge: merge queue, `5490323a`; CI green on the head including integration tests and SonarCloud per the landing; `sonar_open_new_issues=0` and `ok` claimed, not re-read here.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#516`; `landing` stamped; `plan_marshall_plan_id` `rb-v2-156-analysis-findings-remainder`
- [x] epic.md: the decision, the Watch "landed"
- [x] the residue staged as successor plans in the plan-marshall form (operator, 2026-11-20): PLAN-V2-158 `null-contracts-and-optional-conversion`, 159 `lesson-store-closeout`, 160 `assertion-message-residue`, 161 `workflow-engine-and-dsl-residue`, 162 `runtime-worker-and-job-residue`, 163 `core-and-documentation-statement-residue`; item 35 folded into PLAN-V2-155 (the plan that reads its result), item 10 into PLAN-V2-125 (the operator's decision)

## Follow-Ups

- Every carried item is named in exactly one successor; the table is in the epic decision of 2026-11-20.
- ADR 079:88-90 and the PR-body errors are PLAN-V2-159's.
