# Landing Analysis: PLAN-V2-159 — lesson-store-closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-11
pr: #527 (squash merge `16728afc`, 8 files, no `src/main` change)

> Landing record, runbook lane (`rb-v2-159-lesson-store-closeout`, harness opencode, model `deepseek-v4.1-flash/high`, effort high, 4 deliverables by the lane's count: 4 met). One landing message (`-001`, revision 1, created 2026-11-20T21:01Z, amended 2026-11-21T09:00Z: `merge_state=merged`, the amendment supersedes the earlier `open` report) and no candidate-lesson message.
> Landing facts: `unresolved_review_threads=0`, `review_body_findings_total=3`, `handled=3`, `sonar_open_new_issues=0`, `total_tokens` unknown, no blocker. CodeRabbit, the one required reviewer, refused nine times over six waits on 2026-11-20 (#523, #527) and reviewed at 2026-11-21T08:38Z on the operator's word "retry coderabbit now"; Sourcery refused (250 000 diff characters per 7 days) and does not gate.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 ADR 079 and its tests: four locked-read states through `revise`, the decode consequence of the moved retirement rules, DEL and C0 round trips, the contract notes, `PM-WATCH-STORE-58` | shipped-as-specified, with one false ADR statement left (L3-1) | ADR 079 `:88-93` matches `LessonStore.java:199` (create empty) and `:401-404` (revise preserves); `LessonStoreUntrustedCitationTest.CitationStatesThroughRevise` has four tests; `LessonCodecTest:269`; `LessonCodecControlCharacterTest` members()[8] and `c0ControlRoundTrips`; the Guard of the watch checked, not edited. |
| D2 the R05 reduction of `store-schemas/10`, anchors and schema block byte-stable, the `README.adoc:342` row decided | shipped-modified, a real shortfall (L1-7) | the file lost 127 bytes of 25 837 (0.5 %); anchors, the `include::` block and the kind tables are byte-stable. |
| D3(a) the lock-winner pin's failing run | shipped-modified | `DerivedFactStoreLockTest` is unchanged; the reported mutation inverts the newer-wins guard (`DerivedFactStore.java:222-223`), not the lock order the spec's parenthetical names. |
| D3(b) the different-digest write rule decided and covered | shipped-as-specified | `DerivedFactRefreshTest.TwoDigests` `:267-316`; ADR 056 `:44-51` matches `DerivedFactStore.entryOf` `:234-258`; the developer guide was not updated (L3-2). |
| D4 the merge knob; the lane and the assembly read | shipped-as-specified | `.plan/marshal.json:127` `final_merge_without_asking` false to true, the only line changed; the key is read by `phase-6-finalize` branch-cleanup only; no Java of the assembly references the lesson store. Carried item 27 has its recorded outcome. |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (general-purpose, three layers in one brief; it wrote no file, ran no build or test and did not move HEAD). Its measurements are the leaf's; the orchestrator re-read none of them. Mutation and failing-run log ids are the plan's, not re-opened.
- **Verdict:** no blocker. Two majors: the D2 reduction is nominal, and ADR 079 still contradicts the code on a `default` arm.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | the four locked-read states are driven through `revise` | checked | confirms | four tests build each state from store files and assert the mark on the written revision; the mutation run hits the pure arm, not the `isUntrusted(transaction, cited)` wiring (`LessonStore.java:782-792`), which the PR body states by reasoning. |
| L1-2 | the decode consequence of the moved retirement rules | checked | confirms, narrow | one of three moved rules covered (`covering_clause`); `covering_input`, `into` required and `into` forbidden are not. |
| L1-7 | D2: reduce implemented prose to pointers, the two bullets #516 enlarged included | checked | refutes (partly) | File Format 1088 to 1018 bytes (761 before #512), Derived from Untrusted Text 1363 to 1333 (1062): about 19 % of #516's 530 bytes reversed; Lock, Claim, Retirement and Lesson Maintenance still restate mechanics (R05 r7). The landing states the 127 bytes and the eleven requirement bullets, so the shortfall is reported, not hidden. |
| L1-8 | anchors, schema block, kind tables byte-stable | checked | confirms | diff hunks only at lines 32-34 and 48-62. |
| L1-9 | the `README.adoc:342` row decided | checked | info | the row is now `diagram-generator`; the decision "left, R08" is in the PR body only. |
| L1-10 | D3(a) fails without the lock order | checked | info | the `violations.isEmpty()` assertion (`:209`) has no reported mutation; the winner pin does fail under the inverted guard. |
| L1-13 | surface | checked | info | in the diff, not in the spec's surface: ADR 056, ADR 079, `DerivedFactRefreshTest`, `LessonCodecTest` (all declared in the PR); in the spec, untouched: `README.adoc`, `core-store.adoc`, `DerivedFactStore.java`, `LessonCodec.java`, `LessonFrontMatter.java`, `LessonStore.java`, `LessonRevisionTest`, `DerivedFactStoreLockTest`; the PR body lists `LessonStore.java` as "declared and changed" with a formatter rewrite, the merged diff has no such file. |
| L1-14 | Done-when `BROKEN LINKS 0` | checked | info | the lane reports 3 pre-existing; the leaf's one run (documentation checkout 6 commits behind origin) reports 50, mostly github URLs; the new pointers are not in the list. The literal 0 is not met. |

**Layer 1 verdict:** findings — 1 major (L1-7), 3 minor, rest info.

### Layer 2 — Implemented

- The new tests pin what their names say: each state is produced from real files, the DEL title test has a matched control, `TwoDigests` discriminates; all new assertion messages are 36-60 characters (two `assertAll` headings of 71 and 72 characters are exempt, `AssertionMessages.java:38-39`); no AAA comments, no new hand-written fake. ADR 079 matches the code for create and revise (`LessonStore.java:199`, `:401-404`).
- **L2-3 (minor):** ADR 079's "a revision preserves `merged_from`" is pinned by no test; replacing `toBuilder()` with a cleared list passes.
- **L2-5 (minor):** CodeRabbit finding 2 was declined for today's records correctly, but two of the stated reasons do not hold: `CLAUDE.md` has no ban on reflection in tests, and `LessonFrontMatterSchemaParityTest` (`:72-114`) pins member names, not string-ness, so a new free-text member passes without joining the DEL matrix.
- **L2-7 (minor):** `DerivedFactRefreshTest` uses the prose fixture `"the provider answered 503"` with no contract note, and `value` and `oldPayload` are both `letterStrings(1,20)`, so the value check is vacuous in about 1 run in 20 000.
- **L2-6 (info):** the decode test covers `covering_clause` only; the C0 round trip covers `\u0001` only.

### Layer 3 — Correct

- **L3-1 (major):** ADR 079 `:79-81` says the switch "has no `default` arm, so a new variant of the sealed type fails to compile until its arm is stated"; `LessonStore.java:815` has `default -> false` (the type is sealed, `:42`), introduced by #522 `d65dbc66` (its PR text calls it a reindent) and absent at `5490323a`. A new resolution variant silently counts as trusted: fail-open on the path the ADR calls fail-closed. `resolutionArmsCoverEveryState` (`:154-162`) compares the `State` enum only and does not catch it. The plan edited the neighbouring bullet in the same list and left this one.
- **L3-2 (minor):** the PR claims `core-store.adoc:1474-1476` and `:1506-1515` already state the D3 rules; the cross-digest success rule is in ADR 056 `:38-42` and the `DerivedFactStore` Javadoc only.
- **L3-3 (minor):** ADR 056 was amended in place for a decision: no Alternatives entry, Date unchanged; ADR 056 `:31-36` records a failure-versus-success write-order gap nobody owns.
- **L3-5, L3-7 (info):** two statements of the PR body that the merged diff does not bear out (the `LessonStore.java` "formatter rewrite"; the `:197` reindent of #522, which added a line); a line number off by one (`core-store.adoc:897`, is `:896`).
- The merge knob affects only `phase-6-finalize` branch-cleanup; `CLAUDE.md` "Git Workflow" lets the lane merge without asking by its own text, not through the knob.

### Review State

3 CodeRabbit findings at `4a594a28`, all minor: two real and fixed in `5b522ef0` (the empty-list claim limited to `create`; the DEL inserted into the title value only), one declined with a reason (see L2-5); 0 unresolved threads; Sonar 0 open issues, gate ok.

## Metrics and Anomalies

`total_tokens` unknown (opencode lane). 16 builds, all routed through the build server (`daemon_longpoll`), none degraded. The lane's own rule breaches are self-reported (`ls`, `find`, `grep`, `tail`, `wc`, one loop, one `&&` with a pipe; the guard is `none` on this harness). #523 was closed and #527 opened on the same branch; the recreation was to get a CodeRabbit review (nine refusals over six 90-minute waits).

## Routing and Merge Behavior

Merged through the platform merge queue (merge-group run `44887007326`, success). `cleanup_owed=false`. No candidate lesson was sent.

## Reconciliation Actions

- Row PLAN-V2-159 transitioned to `shipped`; `pr` `#527`, `landing`, `plan_marshall_plan_id` `rb-v2-159-lesson-store-closeout`, `lane` stays `runbook`.
- Residue placed, none new staged: L3-1, L1-7, L3-2, L3-3, the watch Guard and the README decision into PLAN-V2-163 as carried item 64; the four test gaps (L2-3, L1-2, L2-5 and L2-6, L2-7) and the nine `pm-workflow` pre-commit rewrites into PLAN-V2-125 as item 65; the missing failing run of the lock-order half of D3(a) into PLAN-V2-126 as item 66.

## Follow-Ups

- Operator: the `merged_from[]` write gap stays with the open `lesson-lifecycle` work package; the three broken links of the documentation repository are pre-existing and nobody owns them; why #523 was closed for #527 is answered by the landing (CodeRabbit refusals).
