envelope_version=1
sender_type=orchestrator
sender_id=design-audit
epic=pm-mcp-core-m2-m4-v2
kind=finding
created=2026-11-02T09:19:46Z

# Design audit 2026-11-02: decisions that touch this epic's plans

Sender: the whole-corpus design audit session (plan-marshall-documentation, branch `docs/design-audit`). The operator has decided the points below. The documents are being changed now. Nothing in this epic was edited: this message is the only channel, and the epic decides how to act on it.

## Operator decisions that affect the plans
- **Staging window (D24/D24a).** A package builds only milestones in the staging window. The window is the milestones an operator-started epic declares; for this epic that is Milestones 2–4 plus the groundwork named in epic.md:99-104. The client-work track against the mock runtime is the one other exception. A prerequisite outside the window is pulled forward under roadmap.adoc:32, and the roadmap item names the milestone that pulls it. Anything else waits. The rule is in project-setup.adoc § Planning the Work. Milestone 1 is being checked and closed where its items are done.
- **Deferred to later-improvements (Z3).** These leave the normative text: capability specs; qualification statistics beyond baseline+margin / score 0 / count / thin; shadow votes; the remote `system_one` form and the judgment vote (the loopback endpoint stays); the 8 navigation figures; interfaces/key_links; EARS enforced by refusal (it becomes guidance). The judge check is non-blocking until a judge/ topic exists. One core package deletes the capability, EARS and remote-system_one code.
- **One source per layer (Z2).** A package file lists tasks with spec anchors only; package-only rules move to the spec. Implementation status lives only in Traceability and the roadmap check marks, so a spec carries no "Implemented by … Remaining:" notes. Implemented spec text is deleted, except schema blocks a parity test reads. The done-rule for every repository: the row stays `done`, keeps no link, the package file is deleted, and dependencies are named by package id. Standing rule R05 ("condense and point") needs a revision to match.
- **Renames.** D13: audit records → decision log (`pm://plan/{id}/decision-log`, `pm-operator decision-log export`); intent `decisions[]` → `choices[]`. D16: client profile → harness profile, `client_id` → `harness_id`, `pm-operator client` → `pm-operator harness`. "Client" now means only the operator's two clients. Each rename lands as its own package.
- **D4.** Merge Danger is folded into Change Map. **D2.** Self-review is slimmed to per-criterion results, `unclaimed[]` and one optional `conflicts[]`.
- **D25 (corrected).** Withdrawing a drain stays (02-operator-channel:109, client design `runtime stop --withdraw`). The core runtime-lifecycle spec gains the withdraw act.

## Recommended actions per plan (the epic decides)
- **Drop:** PLAN-V2-76 (judgment votes; becomes a later-improvements entry). PLAN-V2-45, or rescope it to its D2/D4 only: its D1(a) Merge Danger, D1(b)/D5 Capability Test Guard and capability write, and D3 `decisions export` all conflict.
- **Hold:** 78 (change-map; re-cut after D4/D2), 52 and 70 (client-profile renderer, Milestone 14), 39 (drop its remote system_one dependency), 23 (decision engine, Milestone 7), 60 (Milestone 8), 73 (until the withdraw act is in the core spec).
- **Rescope:** 06 (`ClientId` → `HarnessId`, or after the rename package). 22 (its Milestone 3 part, with the D13 names). 46 (loopback and harness classes, after the system_one deletion). 56 (after the Z3 cut of the store-schemas/16 qualification blocks). 50 and 34 (no "Implemented by … Remaining:" paragraphs; run after the documentation batches).
- **Outside the window, hold unless pulled forward:** 03, 07 (Milestone 12); 09, 14, 18, 28, 30, 35, 36, 37, 51, 55, 61, 62, 79, 80 (Milestone 6); 17, 24, 26, 41, 43, 54 (Milestone 7); 21, 27, 29, 32, 38, 40, 44, 64, 69, 72, 74, 75, 77 (Milestones 6–12). Running 08 and 16 should finish and be recorded as pull-forwards.
- **Epic Vision** (epic.md:9, "Milestones 2 to 4") does not match the roughly 50 plans for Milestones 5–14.
- **Ledger.** It shows 3 shipped, but main has 01, 02, 05, 107, 11 and 12 merged and 110, 08 and 16 running.

## Defects from merged commits (the audit fixes these in its documentation batch)
- store-schemas/10:360 and watch store-schemas:577 narrate "the decision of 2026-10-29" (target-only rule).
- store-schemas/10 "Implemented by `LessonCodec` … Remaining:" (Z2).
