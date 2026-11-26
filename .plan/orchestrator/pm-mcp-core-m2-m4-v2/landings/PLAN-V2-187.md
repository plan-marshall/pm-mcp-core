# Landing Analysis: PLAN-V2-187 — the residue of the sweep's sixth closeout (statements only)

epic: pm-mcp-core-m2-m4-v2
workstream: WS-03
pr: #620 (https://github.com/plan-marshall/pm-mcp-core/pull/620), merge `03fdfe57` (squash through the merge queue), analysed on the thirty-ninth run, 2026-11-26

> Landing record. Source: the lane's inbox message `rb-v2-187-build-output-parser-statement-and-sweep-wording-residue-001` (landing-facts complete, `merge_state=merged`), corroborated against git (`03fdfe57`, `git show --stat`), the ci abstraction (`pr view`, `review-gate`) and the merged text, read by one read-only leaf and re-read by the orchestrator. Standing rules r7 (R21 binds this analysis).

## Deliverable Fidelity vs Spec

Five files, 30 insertions, 20 deletions, all under `pm-runtime` and exactly the declared Expected Surface (`git show --stat 03fdfe57`): ADR `runtime-start/016`, `runtime-start.adoc`, `BuildOutputParser.java`, `PendingEntryRecordsSweep.java`, `PendingEntryRecordsSweepTest.java`. No `pm-core` file, no parser, no `TestCounts.java` touched; no file outside the declaration.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1(a) the count-rule statement | shipped-as-specified | `BuildOutputParser.java:115-125`: one shared rule in `TestCounts#from`, each parser's own rule named against its method (Gradle, Jest, Maven, pyproject, TAP). The false "states it beside its count scan" is gone. Re-read against `TestCounts.java:138-149` |
| D1(b) `described` `@return` | shipped-as-specified | `PendingEntryRecordsSweep.java:349-350` says "returned as it is even when blank, or `<SimpleName> with no message`"; code `:354` agrees (null → `getSimpleName() + " with no message"`, blank unchanged) |
| D1(c) the decision at the catch | shipped-as-specified | `PendingEntryRecordsSweep.java:132` one comment line pointing to `described`'s Javadoc; no test added |
| D2(a) ADR stamp | shipped-as-specified | ADR 016 `:65-67` "(amended 2026-11-25 by `PLAN-V2-186`, which scoped the naming … and added these last two clauses)", same form as `:51`, `:55`, `:56` |
| D2(b) the orphan line | shipped-as-specified | `runtime-start.adoc:136` now reads "A fault of another type leaves `run` uncaught: …"; the paragraph is re-flowed, the words unchanged |
| D2(c) the literal named as the contract | shipped-modified (substance met, the word is missing) | the display names (`PendingEntryRecordsSweepTest.java:745`, `:787`) quote `'IllegalStateException with no message'` and say "the report names …", but neither they nor a comment say "contract"; the lane's "named as the report contract" overstates by that word (L2-1) |
| No behaviour change (main diff comment lines only) | shipped-as-specified | the main diff is Javadoc and one `//` line; `described`'s body `:353-354` unchanged |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: reading the five touched statements against the code of seven classes (`TestCounts`, the five parsers, the sweep and its store) is the depth the Dispatch Decision Rule reserves for a leaf; the orchestrator re-read every cited point at its line (BuildOutputParser `:108-128`, the sweep `:128-134, 338-356`, ADR 016 `:60-68`, the guide `:132-138`, the test display names, `TestCounts.java:136-150`) and records them below as re-read.
- **Verdict:** layer 1 clean; layer 2 findings — 1 (minor); layer 3 findings — 2 (minor/info) and 1 not shown by reading.

### Layer 1 — Specified

The spec's anchors still hold at the head: the line numbers of its re-ground record resolve, and "the main diff is comment lines only" does not contradict D2(c)'s display-name edit, because "main" is `src/main` and D2(c) is named as the one allowed test change. Done-when's two builds and the Sonar read are `not shown by reading` (a run). Persisted verdicts: none (no finding on a claim; the four claims keep `corroborated`).

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| none | the spec's four OBSERVED claims and its lines are consistent with `main` | checked | confirms | `BuildOutputParser.java:113-125`, `PendingEntryRecordsSweep.java:130-132, 342-354` (re-read) |
| L1-1 | Done-when "verify green, Sonar fixed" | not_shown_by_reading | none | a run |

**Layer 1 verdict:** clean (one point not shown by reading).

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1(a) | `BuildOutputParser.java:115-125` against `TestCounts.java:138-149` and the five callers | none (statement; the spec says none) | none | checked |
| D1(b), D1(c) | `PendingEntryRecordsSweep.java:132, 344-354` | none | none | checked |
| D2(a), D2(b) | ADR `:65-67`, guide `:133-157` | none | none | checked |
| D2(c) | `PendingEntryRecordsSweepTest.java:745, 787` | the two tests assert the literal at `:766`, `:801` | **L2-1 (minor):** the lane says the display names name "the report contract"; they do not contain the word | checked |

- **L2-1 (minor, checked, re-read):** the display names say what the report names, not that the literal is the contract (CLAUDE.md: "the test says so in its display name or a one-line comment"). The substance is there — the literal sits in the name of a test that asserts the report text — but the explicit contract wording the spec asked for is not.

**Layer 2 verdict:** findings — 1 (minor).

### Layer 3 — Correct

No factually false statement and no broken Javadoc/AsciiDoc syntax found (`{@link TestCounts#from}`, the five parser links and `TestCounts.Unmeasured` resolve; no stale line number is written into the changed prose).

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `BuildOutputParser.java:115-116` | "every parser passes its counts to `TestCounts#from`" read as universal | the sentence holds for the measured path only: Gradle `:146`, Jest `:213, :222`, pyproject `:694`, TAP `:463, :471` and Maven's cut arm return `unmeasured()` without the call; the cut arm is stated two sentences earlier in the same Javadoc, so no reader is misled | minor |
| L3-2 | `BuildOutputParser.java:118-123` | "a negative block count" for Gradle, pyproject, Jest | describes the branch, not its cause: a digit run beyond `Long.MAX_VALUE` reaches it through the `-1` sentinel; Jest's "exact addition" names a mechanism, its observable rule is a `long` overflow → `Unmeasured` | info |

**Layer 3 not shown by reading:** L3-3 — `PendingEntryRecordsSweep.java:344-346` ("no input reaches that catch with a message-less fault") rests on `entryIds()` alone; an undocumented `IllegalArgumentException`/`IllegalStateException` from `store(entryId)`, `StoreSnapshot.read` or `PendingEntryCodec.read` is not shown absent by reading. The text is unchanged by this landing and the lane recorded the same gap (its §9).

**Layer 3 verdict:** findings — 2 (minor, info); not shown by reading — 1. Nothing major, nothing to report as a defect needing a fix now.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Module Documentation | the package's ADR and developer doc change in the plan's own PR | conforms | ADR 016, `runtime-start.adoc` in the diff |
| CLAUDE.md Code Standards | no behaviour change; main diff is comment lines | conforms | `described` body unchanged |
| CLAUDE.md Code Standards | every assertion carries a message of 20 to 60 characters | conforms | no assertion line in the diff |
| CLAUDE.md Code Standards | a literal stays only where it is the contract and the test says so | deviates (partly) | L2-1: the word is missing, the substance is there |
| CLAUDE.md Code Standards | JUnit 5 only, no AAA comments, no test added | conforms | two `@DisplayName` strings only |
| CLAUDE.md Dependencies | no dependency or plugin added | conforms | no POM in the diff |
| `java-core` / `javadoc` review rules | a `@return` claim matches the implementation | conforms | `described` incl. null and blank |
| CLAUDE.md Git Workflow | commit trailers | not_shown_by_reading | the commit messages were not read |

## Metrics and Anomalies

- Tokens: `unknown` (the harness measured none; recorded as an unobserved fact, not zero). Model `deepseek-v4.1-flash/high`, effort high, harness opencode, `plugin_version unknown`, executor 0.1.2184.
- Duration: not recorded in the facts. The longest runs the lane reports: full `verify` 894 s and 878 s; the first full `verify` hit the explicit 600 s bound and was re-run at 1500 s.
- Anomalies: none beyond the quota wait below. The base moved three times during the run (`90505ad2`, `250b2f08`, `5ae4fd1c`), each merged in; the final head `82e7625e` was fully based on `5ae4fd1c`. `guard: none on this harness (unenforced)`.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) refused at 18:59Z, the lane held 90 minutes and re-triggered at 20:40Z; the review landed 20:43Z with "No actionable comments" and covered `a1b1fffd`. Sourcery (optional) approved at 21:10Z. Landing facts: 0 unresolved threads, 0 body findings, 0 handled. Re-read at 2026-11-26T06:33Z by `review-gate --pr-number 620`: 0 unresolved threads, 0 body findings, `merge_allowed: true`, `all_handled` — **no difference**. `review_body_findings_handled` = 0 of 0, nothing to re-read.
- CI/merge: green on every head; merged through the merge queue as `03fdfe57` (`pr view`: `state: merged`, `merge_commit_sha 03fdfe57…`). `cleanup_owed=false`.
- Sonar: the landing's `sonar_open_new_issues=0` (quality gate ok, `open_issue_rules=none`). The measured read after the merge is not decidable (the pull-request analysis is gone with the merge); recorded as the lane's measured claim, not re-measured. The lane's own read at head `82e7625e` was 0.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-V2-187 --status shipped`
- [x] row `pr` stamped `#620`
- [x] row `landing` stamped `landings/PLAN-V2-187.md`
- [x] row `plan_marshall_plan_id` stamped `rb-v2-187-build-output-parser-statement-and-sweep-wording-residue` (from the sender)
- [x] epic.md: Decisions entry of the thirty-ninth run; the residue below becomes an Open Defect (fold into the leftovers plan)
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

The 187 spec called itself the last of this stream; the three small points below are wording only and are routed under the leftovers rule (never "who owns it"): they join the one leftovers plan already recommended for PLAN-V2-126's carried open items (85%), which stays unstaged until the operator answers that question. Recorded as an Open Defect of `epic.md`, with this landing as its source:

- L2-1 — add the word "contract" to the two display names (`PendingEntryRecordsSweepTest.java:745, 787`) or a one-line comment.
- L3-1 — make the `BuildOutputParser` sentence true of every path: "every parser that reports a measured count passes it to `TestCounts#from`", and add Maven's `cut` arm to the colon list.
- L3-2 — word the sentinel branch by its cause ("a count the parser cannot represent: negative or past `Long.MAX_VALUE`").
- L3-3 — not shown by reading; the carried item of PLAN-V2-186/187 stands (the null-message corner of the codec path and `isResidue`'s uncaught paths).
