envelope_version=1
sender_type=orchestrator
sender_id=schema-transition
epic=pm-mcp-core-m2-m4-v2
kind=finding
created=2026-11-10T16:58:54Z

# schema-transition-003: PLAN-V2-122 names, one parser pitfall, and a record of an undone emit

From the verifying session of the schema transition (checked against `origin/main` of `pm-mcp-core` at `db02e443`, 2026-11-10). No ledger file was changed by this session after `schema-transition-002.md`; this message is the only thing it files.

## 1. PLAN-V2-122 can now name its files (spec change, small)

`generated-listings` (PLAN-V2-121, #339) is on `main`, so the spec of PLAN-V2-122 no longer needs the hypothetical wording:

- The files a test still reads below a `doc/` tree are `doc/log-messages/pm-core.adoc`, `doc/log-messages/pm-runtime.adoc` and `doc/log-messages/pm-workflow.adoc`. ADR 019 (`doc/adr/019-generated-log-message-listings.adoc`) names them and the one glob that covers them: `doc/log-messages/*.adoc`.
- Deliverable 2 of 122 can therefore state the narrowed entry of `maven-build.paths-code-extra` as that glob instead of "the generated log message files". `.github/project.yml` still carries `'**/doc/**'` and `'**/src/**'` on `main`.
- "Depends on" in the spec still reads "(117, 118 and 120 are shipped)" and "whose names this plan reads". Shipped now: 116, 117, 118, 120, 121. Open before 122: 119 (staged; the resume anchor orders it after 129, and 122 last).
- Deliverable 3 should cite ADR 019 next to the ADR of `schema-files-foundation`.
- Deliverable 4's search: the landing of 121 reports the only `doc/` paths in tests are these three listings, compared byte for byte. Verify-first still applies.

Disposition suggestion: Fold into `plans/PLAN-V2-122-adoc-reading-retired.md` (no new surface: the Expected Surface already lists `.github/project.yml` and the ADR files).

## 2. Parser pitfall of `corpus surfaces` (for the skill owner, not this epic)

A bare directory entry such as `doc/` in `## Expected Surface` claims the whole tree. In this epic it produced 89 false gate overlaps and a false overlap prompt (PLAN-V2-119 against 121, "10 files"). Reworded to 13 declarative file entries, the overlap went to 0. A spec author gets no warning when an entry is a directory. Suggest: either a warning in `corpus surfaces` for an entry that is a directory, or a line in the plan-spec template under Expected Surface ("name files, never a directory"). Stage or discard as you see fit; it belongs to `plan-marshall`, not to this epic's plans.

## 3. Record of an undone emit (no state changed)

On 2026-11-09 the verifying session logged, in a local git-ignored `decision.log`, an emit of PLAN-V2-121 and PLAN-V2-119 and then corrected it (at that time `epic.md` held 119 behind the deferred-code decision, and 121 was already merged as #339). Nothing was emitted, no queue row was transitioned, and no `launched` or `running` state was touched. The two lines never reached the ledger; this entry replaces them. The cause was working from a ledger snapshot that lacked the other session's `abc72506` (#345): the land stopped at `remote_branch_diverged`, nothing was forced, and the local edits were discarded in favour of the remote snapshot.

## 4. Operator-owned, to confirm on your side (not decided here)

- Whether the deferred-code decision that held PLAN-V2-119 (the deletion package for `harness-profiles/03-endpoint-harness`) is still open: the `epic.md` on `main` no longer carries that entry, and PLAN-V2-99 has shipped.
- The squash-commit message default of the merge queue (the review bot's "Summary" block lands in the commit message).
