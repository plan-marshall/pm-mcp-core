envelope_version=1
sender_type=plan
sender_id=rb-v2-16-lesson-store-operations
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-04T21:35:22Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-16-lesson-store-operations
epic=pm-mcp-core-m2-m4-v2
pr=#284
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=5
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2041
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

guard: none on this harness (unenforced)

# PLAN-V2-16 — the lesson store: allocation, resolver, revision, retirement and claim

Standing rules: **r5**, R01–R14, R17–R20 (R21 binds the orchestrator, not the lane). The spec names r4; the file is at
r5 and r5 governs. The spec was read from the shared ledger worktree (`spec_source: ledger_worktree`) with
`spec_differ: true` at the first resolve — the main checkout's copy said something else at that moment; every later read
used the ledger worktree's copy. `executor-skew`: `match` (executor 0.1.2041, lane 0.1.2041).

PR **#284** merged as **70bc999f** — by the operator's own session, not by this run's merge-queue call. The branch was
rebased onto a moved `main` and a docs audit commit by that session while this run still held it; the operator then chose
(question 1) to let this run reset its local branch to the published rebase and re-apply its one missing commit. Two
consequences are reported under Findings and Residue: the merged tree does not carry the second review's fixes, and the
follow-up PR **#289** does.

## 1. Deliverables

| Done-when | Order | Test | Failing run | Verdict |
|---|---|---|---|---|
| D1: the lesson on disk as the codec's bytes, its sidecar entry, no journal left | mutation-after | `LessonStoreTest$Creation.created` | — | met |
| D1: 200 concurrent creations of two threads on one hour | mutation-after | `LessonStoreTest$Allocation.twoHundredConcurrentCreations` | — | met |
| D1: an id drawn for the next hour after the clock moves | mutation-after | `LessonStoreTest$Allocation.nextHour` | — | met |
| D1: a create over an existing id refused | mutation-after | `LessonStoreTest$Creation.overExistingId`, `$Transitions.createOverDeletedOutOfBand` | — | met |
| D1 (Standing Constraints C1/I1): the characters YAML could refuse | mutation-after | `LessonStoreTest$RoundTrip.createRoundTrips` | 5758dec0, output `8281307a4d5c43efb78708a63b9acd64.log` (the codec's escaping reverted) | met |
| D2: every listed fixture resolves by its listed id (9 YAML + 6 headerless) | mutation-after | `LessonResolverTest$PortedFixtures.allListAndResolve`, `yamlFixturesAreFound`, `fixtureListsMatchTheDirectory` | 5758dec0, output `cb40123fc2634d62aacf091d1ed1f013.log` (an unreadable file mapped to `absent`; 4 tests failed) | met |
| D2: headerless `PresentUnreadable`, never `Absent` | mutation-after | `LessonResolverTest$PortedFixtures.headerlessArePresentUnreadable`, `$FourStates.presentUnreadable` | as above | met |
| D2: `EmptyShell`, `StoreTampered`, `DeletedOutOfBand`, `Untracked` | mutation-after | `LessonResolverTest$FourStates.emptyShell`, `tamperedEmptyShell`, `$IntegrityStates.changedByte`, `deletedOutOfBand`, `untracked` | as above | met |
| D2: a lock-free read of a tampered file is `DIGEST_UNVERIFIED`, not a decision | mutation-after | `LessonResolverTest$IntegrityStates.changedByte` | as above | met |
| D2: the property test over generated store states | mutation-after | `LessonResolverTest$Property.noListedStateIsAbsent` (100 states, each with a sidecar entry whose file is gone) | as above | met |
| D2: `unclaimed()`, `claimedBy`, `claims()` | mutation-after | `LessonResolverTest$ClaimsAndCandidates.*` | as above | met |
| D3: each rule with a refusing case and a passing control | none | `LessonRevisionTest`, `LessonRetirementTest` | — | met |
| D3: the flag never turns back | none | `LessonRevisionTest$DerivedFromUntrusted.*` | — | met |
| D3: a revision without sources refused | none | `LessonRevisionTest$ReplacedBody.withoutSources` | — | met |
| D3: each verdict's required members, a retired lesson not revisable, a claimed lesson not retirable | none | `LessonRetirementTest$Verdicts.*`, `$States.*`, `LessonRevisionTest$ReplacedBody.retired` | — | met |
| D4: the table's claim, release and end cells and the retired record's members | mutation-after | `LessonClaimTest$Claim/Release/End.otherStates`, `.otherStatesByB`, `End.applied` | 5758dec0, output `7814ff8394964efaa613c048960cae17.log` (the durable-claim guard removed; 4 cells failed) | met |
| D5: the crash windows (create, revise, claim, end stopped after each step) | mutation-after | `LessonStoreCrashTest$WriteStopped.*` | 5758dec0, output `932e52abdcca41c895c5a297bae6fa9c.log` (the digests step skipped; 10 tests failed) | met |
| D5: two concurrent claims, a claim racing a retirement, a repeated release and end | mutation-after | `LessonStoreCrashTest$ConcurrentWriters.twoClaims/claimRacingRetirement/twoReleases/twoEnds`, `$Repeated.*` | as above | met |
| D5: the staged namespace | mutation-after | `LessonStoreCrashTest$StagedNamespace.stagedFileIsNeverALesson` | as above | met |
| D5: the table with a test per cell | mutation-after | `LessonStoreTest$Transitions.*` (create/revise/retire × 7 states) and `LessonClaimTest` (claim/release/end × 7 states × plans A and B), table in the PR body | as above | met |
| D6: the guide, five ADRs, the reduced sections, the watch decisions, the new package file and row | none | the files themselves | — | met |
| D6: no package file or index cell names `lesson-store` | none | `doc/plans/lesson-store.adoc` deleted, its row replaced, the `Depends on` cells cleared, `start-source-intake.adoc` cleared by the audit commit | — | met |
| D6: the links check reports `BROKEN LINKS 0` | none | R17 links check | — | **not met as written** — see below |
| D6: PLAN-V2-24/22/21 no longer list the store as an open question | none | — | — | **not met as written** — PLAN-V2-24 still does (below) |

The links check reported `BROKEN LINKS 0` on every head up to and including `fad0ec04`/`476af0cf`. After the operator's
session rebased the branch onto the moved `main` and the documentation checkout advanced to `1daec1a`, the same check
reports `BROKEN LINKS 361`, all in the sibling's files (`roadmap.adoc` and the specification indexes) naming engine paths
that `main`'s harness-rename work replaced (`client-profiles` → `harness-profiles`, `03-retention-host-config` →
`03-retention-harness-config`). R17's carve-out applies: a count that names an item replaced in the sibling is the
sibling's own links, not a defect of this change — the sibling's `roadmap.adoc` had not been updated to the renames, and
the run reported 0 before those renames reached this branch.

`PLAN-V2-24-start-from-clients.md` still lists "Owner of the lesson store" among its open questions; the spec lives under
`.plan/orchestrator/`, which the plan's Write-Boundary forbids this lane to edit. Reported for the orchestrator.

Per-head test runs on pushed heads: `test -pl pm-core` at `048150a4`…`476af0cf` (4137 tests green), the six lesson-store
classes at each later head, and `verify -Ppre-commit`/`verify`/`verify -Pcoverage`/`verify -Pintegration-tests -pl pm-core
-am` green at `476af0cf` (the head that landed through `c0b250ab` → `70bc999f`). The coverage gate also ran green on the
rebased tree (`c15fbb08`).

## 2. Personas and skills

Persona per kind of work: `persona-implementer` (the store, the resolver, the substrate accessors), `persona-module-tester`
(all six test classes), `persona-security-expert` (the untrusted mark and the integrity states), `persona-documenter`
(guide, ADRs, specification reduction, package files), `persona-code-reviewer` (the self-review round and the triage of
every review comment). The spec declares no `Skills:` and no `Persona:` line: `skills: none named` per deliverable.
R20's coding skills were read and applied as standards (java-core, java-lombok, java-null-safety, javadoc, junit-core,
cui-testing, cui-logging). No skill was added for surface drift.

## 3. Hypotheses

* "`EmptyShell` means a body with no non-whitespace character" — **confirmed**; `LessonStore.decode` uses
  `String.isBlank()` and `LessonResolverTest$FourStates.emptyShell` pins it (the `Lesson` Javadoc was corrected).
* "The listing needs no new `Outcome` in `StoreRefusal`" — **confirmed**; the store throws `StoreRefusal` only for an
  unreadable sidecar or journal, and the states are values of `LessonResolution` (`StoreRefusal.java` untouched).
* "The front matter's writer and the YAML reader may disagree on U+0085, a C1 control, U+FFFE/U+FFFF and U+2028/U+2029"
  (Standing Constraints, from the PLAN-V2-11 landing analysis, claims C1 and I1) — **confirmed**: the round trip failed
  with `PresentUnreadable` before the fix; `LessonCodec` now escapes every non-ASCII character
  (`LessonStoreTest$RoundTrip.createRoundTrips`).
* "The retirement `applied` clears `claim` and keeps `retired.by`" — **confirmed** and recorded in ADR core-store/064;
  `LessonClaimTest$End.applied` asserts both.

## 4. Findings

Self-review round 1 (persona-code-reviewer, adversarial lenses): 12 findings. Fixed: allocation reusing an id after an
out-of-band delete; the lock-free read deciding `deleted_out_of_band` while four documents said locked-only (documents
corrected, the behaviour kept); the verify-then-read window in the locked read (the bytes read are now the bytes hashed);
the D5 table collapsed to one cell per row (completed); two draws in one transaction (documented and pinned); a tampered
lesson listed as a candidate (now filtered to `VERIFIED`); two reduced bullets without the guide link (added); R20
builders on the four-component records and two short assertion messages (fixed); weak test evidence (the no-op tests now
prove the commit is refused, the property test carries a sidecar-only entry, the crash tests assert the roll-forward).
Recorded: the undeclared `ScopeTransaction`/`package-info` edits and the ten reformatted provider files. Reported not met:
D6's consumer-plan clause. One structural defect found by the same review — `isHigher`/`unclaimed()` comparing sequences
by length, so a leading-zero id outranks `999` — was fixed only after the merge (follow-up #289).

CodeRabbit review 1 (`647c0d4a`): seven findings. Fixed in `9d658c06`: allocation over the transaction's targets
(`ScopeTransaction.targetPaths()`), the "never runs out" bound corrected in ADR 063 and `LessonId`, `create` checking the
id's hour, `unclaimed()` ordering by hour/sequence, a same-transaction citation for the untrusted mark, `EmptyShell`
carrying its integrity, and the test gaps. Answered and resolved.

CodeRabbit review 2 (`9d658c06`, the head that merged through the rebase): four findings. Fixed after the merge in
follow-up #289: leading-zero sequences, the listed id in a successful outcome, a persisted blank lesson counting for the
untrusted mark, and the three SonarCloud complexity findings. The fourth — "keep the identity of a deleted lesson
reserved" — is answered, not fixed: the store has no delete verb and the specification says a lesson file is never
deleted, so no transactional delete can drop an entry; a future `remove` verb must leave a tombstone and is recorded for
`lesson-lifecycle`.

SonarCloud (#284): 31 open issues at `476af0cf` (all code smells in the new files, plus one S2637 bug); fixed in
`647c0d4a` → 4 issues → fixed in `9d658c06` (0 new issues, quality gate green). #289's first run failed the gate on
78.4% new-code coverage; three tests were added and the gate went green (0 new issues).

CI: green on `647c0d4a` and `9d658c06`; #289 green on `ae2647c2`.

## 5. Reviewers

* **coderabbit** (required, quota-limited): triggered 10:28 (`rate limited`), re-triggered 11:10 (`rate limited`),
  waited the 90-minute window, re-triggered 12:46 → reviewed `647c0d4a` (7 findings); re-triggered 12:59 (`rate
  limited`), waited again, re-triggered 14:37 → reviewed `9d658c06` (4 findings). The merged head `c0b250ab` is that
  review's `coveredCommitId`'s tree plus three doc lines. Standing review authorization: a qualifying review exists on
  `9d658c06`, every finding is fixed in a pushed commit (`9d658c06`, and the follow-up `289` for the ones found there) or
  answered (the delete-identity one). Eight of the ten threads were resolved through `ci pr resolve-thread`; two were
  never resolvable by this run — the allocation finding of review 1 and the delete-identity finding of review 2 — because
  their thread ids never appeared in `ci pr comments`' truncated output and the tool stopped listing inline comments
  afterwards. Both findings are handled in the code or answered in the PR-level disposition comment, but their threads
  stay open (Residue).
* **sourcery** (optional, quota-limited): refused #284 on its 150,000-diff-character limit; reviewed the small follow-up
  #289 (`SUCCESS`).

## 6. Operator questions

1. "The branch was rewritten on the remote by another writer … which do you choose?" — options: reset-to-remote and
   re-apply the fix commit (recommended), merge both histories, stop and hand over. **Selected:** reset to the remote and
   re-apply the fix commit.
2. "PR #284 was merged by another session at 70bc999f … what should be done with the two commits?" — options: open a
   follow-up PR (recommended), delete the branch and report a follow-up, leave the branch and stop. **Selected:** open a
   follow-up PR.
3. The free-text message "continue" after the clock advanced two days; it is not a selection and nothing was acted on
   beyond continuing the run.

## 7. Rule check

`guard: none on this harness (unenforced)` — the resolve result reported `guard: none`, so no guard log exists and no
line of it was counted. Violations this run made, own actions only:

* **File edits through the shell, five times**: `sed -i` once (test id strings), `perl -pi -e` four times (a test rename,
  the `readFound` rename, the outcome constructor sites, and the `(lessonId, lesson)` substitutions). Each is a `git
  diff`-visible edit that should have gone through the `edit` tool.
* Everything else was kept: one command per shell call (no `&&`, pipes, loops or redirects in build/CI calls), files read
  with the file tools, temp files only under `.plan/temp/`, the repository files edited only in the worktree, the spec and
  `.plan/orchestrator/` untouched, no `git grep`, no CI CLI, no direct build wrapper, every build through the resolved
  `maven run` command and every CI action through `ci`. The R17 links check ran its own script with the absolute sibling
  paths, as R17 prescribes.
* **Restarts/kills**: none. **Re-dispatch**: the self-review sub-agent ran once (round 1) and once for its report; no
  sub-agent was killed. The build server became unreachable after the two-day gap and the last three builds fell back
  in-process (see §8).
* **Spec read**: `spec_source: ledger_worktree`, `spec_differ: true` at the first resolve (the main checkout's copy
  differed); every later read used the ledger worktree's copy. **`executor-skew`: `match`** (0.1.2041 both sides).

## 8. Build server

Setup preflight: **ready** (registered, version 1). At landing: **down** (`build_server_reason: unreachable`) — the
daemon stopped answering during the two-day gap, and the last three builds resolved `in_process_fallback`
(`reason: unreachable`): `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-core -am`, and the follow-up's
`verify -Ppre-commit`. Every build before that was `daemon_longpoll`, with no launch fault and no retry. One routed
build hit the daemon's own bound on 2026-11-02 (`timeout`, `timeout_used_seconds: 521`, `timeout_source: explicit`); it
was re-run with `--timeout 1800` and finished green — no build was killed. `runtime_stamp_reason` was not carried.

## 9. Residue

* **Follow-up PR #289** (`fix/v2-16-lesson-store-review-followup`, head `ae2647c2`): the second review's fixes, all
  checks green including SonarCloud (0 new issues) and a Sourcery review. Open, not merged: the operator opened the
  follow-up and owns its merge.
* **`PLAN-V2-24-start-from-clients.md`** still lists "Owner of the lesson store" as an open question (orchestrator's
  edit).
* **The links check** on the rebased base names only the sibling's stale links (R17 carve-out).
* **Two review threads stay open on the merged #284** (the allocation finding of the first review and the delete-identity
  finding of the second): their thread ids were never observable through `ci pr comments`, so `ci pr resolve-thread` could
  not name them. The findings themselves are handled — the first is fixed in `9d658c06`, the second is answered in the
  PR-level disposition comment and owned by `lesson-lifecycle`.
* **A `remove` verb for lessons** must keep a deleted id's identity reserved (the CodeRabbit finding answered above);
  owned by `lesson-lifecycle`.
* The operator's session's docs audit commit `c0b250ab` carried the watch counts in `doc/ImplementationWatch.adoc` and
  `doc/Specification.adoc` and the `start-source-intake.adoc` cell that this lane had left; the landed tree includes it.
