envelope_version=1
sender_type=plan
sender_id=rb-v2-117-schema-files-machine-stores
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-08T08:49:27Z

```landing-facts
schema=landing-facts/1
plan_id=rb-v2-117-schema-files-machine-stores
epic=pm-mcp-core-m2-m4-v2
pr=#320
merge_state=merged
cleanup_owed=false
deliverables_total=3
deliverables_done=3
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2076
steps=setup:done,implement:done,build-gate:done,self-review:clear,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=n/a
step.sonar-roundtrip.open_issue_rules=n/a
```

# PLAN-V2-117 — schema-files-machine-stores (landing)

Standing rules: **r6**, rules R01, R02, R05, R06, R08, R10 (replaced), R14, R16, R17, R18, R19, R20.
Spec copy read: `spec_source=ledger_worktree`; no `spec_differ` marker. Executor skew: `match` (executor 0.1.2076, lane 0.1.2076).
Build server: `ready` at setup; every build resolved `daemon_longpoll`, `in_process_fallback` 0; no degraded build, no launch-fault retry, no killed job; `runtime_stamp_reason` absent.
Sonar (lane): `not_configured` — the facts are `n/a`. The R18 plain-HTTP read of the pull request (the project is public) was made at every head after CI: **0 open new-code issues**, quality gate **OK** at the merge head `7e5e2929`.

## 1. Deliverables

| Deliverable | Done-when | Verdict | Order | Test / evidence | Commit |
|---|---|---|---|---|---|
| D1 move the 22 blocks | every `[source,json]` block of 11/12/13/15 is an include of a schema file, each the former block's text | met | none | 22 includes (11:1, 12:18, 13:1, 15:2); deleted-line counts equal the files' line counts (87/765/24/54); each block was removed by an edit whose `oldString` was the block text, and the file was written from that text; all 22 targets resolve | `d37a32cf` |
| D2 the two tests read the files | the two parity tests pass against the files and name neither `SpecificationDocument` nor `pm.specification.root` | met | mutation-after | `AuditSchemaParityTest` (10), `CredentialStoreMoveRecordParityTest` (3); grep of both sources for the two names returns nothing; failing run at `8ed16f31` after mutating `audit-record.schema.json` / `credential-store-move.schema.json` — job `df402a6786b348dda42429c2c7879133` (pm-core) and `b847fd67dadb4a9da27987b03e82d56e` (pm-runtime) | `c1a07ecc` |
| D3 a test for each schema whose codec exists | a test exists for each schema whose codec exists; verify, verify -Ppre-commit, verify -Pcoverage green in every module touched | met | mutation-after | six new tests: `ConsentSchemaParityTest` (5), `ModelCatalogueSchemaParityTest` (3), `RoleAssignmentsSchemaParityTest` (4), `LeaseOwnerSchemaParityTest` (3), `MachineConfigurationSchemaParityTest` (4), `HarnessEnrolmentSchemaParityTest` (5); the ten codec-backed schemas are covered by these and the two D2 tests; failing run at `8ed16f31` after mutating the six schema files — jobs `df402a6786b348dda42429c2c7879133` (pm-core, 5 classes) and `b847fd67dadb4a9da27987b03e82d56e` (pm-runtime, 3 classes) | `8ed16f31` (+ `fda57477`, `ceaf776d`) |

Per-head test runs (all pushed heads that carried the plan's code after the pull request existed): `6e90bf09` (gate `c7d4f0f1ae6b4da7b93ae8f076b1554f`, 6586 tests), `19c5a64b` (gate `fd5601b8b2b04d4ab1829a2758641d41`), `27b1f313` (CI Maven Build 25/26 and gate `145d96f81736425c9aabc2386c0a22a8`), `7e5e2929` (CI run `42459915353`, gate `e1356f927a1541fda42b97192058e2c6`). `verify` and `verify -Pcoverage` and `verify -Pintegration-tests` were green on `19c5a64b` (jobs `fd5601b8…`, `334aec0d…`, `0bd8f871…`) and green again after the merge (`145d96f8…`, `e1356f92…`). Local gate at the final head `7e5e2929`: success, tree clean.

The 12 schemas whose codec does not exist yet (D3's second half) are **named here for the packages that will build the codec**: `host-tools`, `install-records` → `pm-runtime runtime.launch`; `runtime`, `upgrade`, `login-environment` → `pm-runtime runtime.start`; `web-devices` → `pm-runtime runtime.security`; `credential-file` → `pm-runtime runtime.credentials`; `explorer`, `report-event`, `cleanup-event`, `screen-verdict`, `grant-record` → `pm-core core.store`. The plan's Write-Boundary forbids editing those specs, so this message is the carry.

## 2. Personas and skills

| Step | Persona applied |
|---|---|
| the move and the specification/developer-guide edits | `persona-documenter` |
| the parity tests | `persona-module-tester` |
| every self-review round and the PR-comment triage | `persona-code-reviewer` |

The spec declares no `Skills:`/`Persona:` lines → **skills: none named**. Surface drift reported: `pm-core/src/test/java/de/planmarshall/core/SchemaResource.java` (existing test helper, modified additively — `object`/`array`), the six new test classes (implied by D3, unlisted), the resource packages beyond the spec's HYPOTHESIS (`pm-core core.config`; `pm-runtime config`, `launch`, `start`, `security`), and one one-line pre-commit rewrite of `IntentSchemaParityTest` (a file that came from `#319` and was not rewrite-stable).

## 3. Hypotheses

- The spec's HYPOTHESIS "the schema files lie in `core/store/` of `pm-core` and in a `credentials` resource package of `pm-runtime`" — **refuted in part**. Placement follows each owning codec: `machine-config` → `pm-runtime/.../runtime/config/` (`MachineConfigurationCodec`), `consents`/`model-catalogue`/`role-assignments` → `pm-core/.../core/config/`, `harnesses`/`host-tools`/`install-records` → `pm-runtime/.../runtime/launch/`, `audit-*`/`lease-owner`/`explorer`/`report-event`/`cleanup-event`/`screen-verdict`/`grant-record` → `pm-core/.../core/store/`, `credential-*` → `pm-runtime/.../runtime/credentials/`, `runtime`/`upgrade`/`login-environment` → `runtime/start/`, `web-devices` → `runtime/security/`. The codec-less placement rule is now recorded on `main` as ADR 017 (from `#319`).

## 4. Findings

Self-review, three rounds (`persona-code-reviewer`):
- Round 1: F1 the credential-file holder claim was false (FileSecretStore writes only `{format_version, value}`) → fixed; F2 "DeviceRegistry reads the store" was false → fixed; F4 the model bound was asserted as a literal → now proven through `CatalogueModel`; the removed transaction-id-presence assertion → restored; `core-store.adoc` named `AuditSchemaParityTest` as spec-reading → fixed. Residue: F3 (no ADR on `main` for the codec-less rule — since resolved by `#319`'s ADR 017), F5 (the harness endpoint form is outside the block), F8 (surface delta).
- Round 2: NEW-0 an uncommitted pre-commit rewrite → committed; NEW-1 a closed set of spec-reading tests → "include" and the full list; NEW-2 present-tense holder claims for codec-less records → "its codec is not built yet"; NEW-3 the deleted package named as remaining work → `runtime.credentials`; nit on a `Set` javadoc → fixed. Residue NEW-4 (the 12 codec-less files) → carried in §1.
- Round 3: **nothing open**; self-review `clear`.

PR comments (22 inline threads + review bodies), every one triaged with `persona-code-reviewer`:
- Fixed: the project-scoped kind rule (derived from `ConsentKind`), written-member comparison, both store enums compared separately, the null/inherited/escalation effort forms, written owner JSON types, the populated usage report, the written provider entry, the created defaults and format version, the operation enum compared structurally, the digest generated, the expected members derived from the schema, the events map asserted, the host-tool prose reconciled with its schema, `core-store.adoc` lists made non-exhaustive.
- Answered as out of scope (the file is the former block's text byte for byte; the move adds or removes no member; production-codec findings belong to the codec's package): the proxy pattern, `from == to`, the audit-index UUIDv7 pattern, absolute consent paths, the CLI-device rule, negative revisions, code points, empty-log invariants, cleanup `project_id`/`remind_at`, lease scope identities, report/cleanup event variants, screen-verdict spans, provider variants, credential-key restrictions, the remote-endpoint form, the web-device duplicate-id rule, the host-tool record, the unverified login-environment and equal-port findings. The async-exported block (GitHub's include behaviour) is answered as ADR 016's recorded decision.

## 5. Reviewers

| Reviewer | Required | Result |
|---|---|---|
| coderabbit | yes (`quota_limited`) | Refused on quota at 2026-11-08T02:44:22Z; re-triggered after 90 min (04:16) — refused 04:17; re-triggered 05:58 — **reviewed**, submitted 06:16:47Z (18 inline threads + 20 further findings in the body); re-triggered 07:19 — reviewed the merge head `27b1f313` at 07:30:48Z with `final_review_risk_coverage` `kind=reviewed`, `coveredCommitId=27b1f313…`; re-triggered after the last push at 08:10:41Z — refused on quota at 08:11:18Z. **Standing authorization applied** (qualifying review, every finding handled, every other gate green on `7e5e2929`). |
| sourcery | no (`quota_limited`) | Refused on quota at 2026-11-08T02:44:50Z (250k diff chars / 7 days); optional, not gating; recorded. |

All 22 inline threads are answered and **resolved** (`reviewThreads 23`, 0 inline unresolved; the remaining 20 "unresolved" rows are review-body entries with no thread, answered by two PR comments).

## 6. Operator questions

None asked; no free-text operator messages beyond the two invocation lines of the runbook-lane command.

## 7. Rule check

- **Guard: none on this harness (unenforced).** `resolve` reported `guard: none`, so no guard-log line is claimed.
- **The run's own breaks, reported rather than hidden:** the round-1 self-review sub-agent ran one `git diff … > /tmp/opencode/…` redirect (outside `.plan/temp/`) once before switching to the file tools — reported by the sub-agent. The main run used `tail` to read the executor-named build logs, and `sed -n` / a pipe (`git show … | sed -n`, `git log … | head`, `wc -l`) on a handful of repository files while orienting (plan specs, `ProfileStages.java`, `core-store.adoc`, doc line counts) — the lane's file-reading rule asks for the file tools and no pipes; those reads were the deviation. No repository file was modified by them.
- Spec read recorded: `spec_source=ledger_worktree`, no `spec_differ`. Executor-skew verdict `match`, recorded.
- No worktree or branch was force-changed; the run merged `origin/main` once (`9dbd9b67`) when it moved, resolved one index-row conflict, re-ran the gate, and pushed.

## 8. Build server

Setup preflight `ready`; every build `daemon_longpoll` (0 `in_process_fallback`); no degraded build and no launch-fault retry in this run. Queue waits were long at times (`queued_seconds` up to ~1030 on a retried wait) but no build was skipped or backgrounded. No killed job.

## 9. Residue

- The 12 codec-less schema files and their packages (listed in §1); each package's later task adds the parity test.
- `harnesses.schema.json` states the program form only; `HarnessEnrolmentStore` also writes the remote-endpoint form (ADR `runtime-endpoint/005`). A move-only plan cannot change the block; `HarnessEnrolmentSchemaParityTest` states the program form and says why.
- The schema-content findings from the review that need a specification or codec change (the list in §4) are for their owners; the codec-side ones (`ModelCatalogueCodec.write` negative revision, `CatalogueModel` code points, `LeaseCodec`/`ScopeType` machine and entry scopes, `FileSecretStore`'s subset format) are production work in packages this plan must not touch.
- `pm-core/doc/developer/core-store.adoc` and ADR 016 still carry their pre-move wording in places; the `adoc-reading-retired` package (PLAN-V2-122) owns their retirement.
- Owed to the milestone exit (R05): the concept and user documentation of the slice.
