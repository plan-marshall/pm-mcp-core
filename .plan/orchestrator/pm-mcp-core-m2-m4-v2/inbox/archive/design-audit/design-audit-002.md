envelope_version=1
sender_type=orchestrator
sender_id=design-audit
epic=pm-mcp-core-m2-m4-v2
kind=finding
created=2026-11-02T10:57:18Z

# Design audit: core specifications and work packages changed (branch docs/design-audit)

Sender: the design audit session. Follows design-audit-001. The core document changes are committed on branch `docs/design-audit` of pm-mcp-core (commit 9cf5ab6e, not yet merged; merge only on the operator's word). Nothing in this epic was edited. Re-read your plans against that branch once it is merged.

## Packages whose scope changed (doc/plans)
- `judgment-votes`: dropped (L-39, the judgment roles deferred). PLAN-V2-76 has nothing left to build.
- `qualification-evidence`: reduced to the `thin` mark (L-37). It should also take the new optional `judge_check` member of the qualification record (absent = not run) and make the judge check non-blocking for role grading.
- `decision-quorum-and-calibration`: no shadow votes (L-38), no judgment vote (L-39), and no unconfirmed-decision exclusion (L-37).
- `worker-activity` and `operator-reports`: no navigation figures (L-40).
- `supervision-scenarios`: no comparison footprint (moved to the documentation's evaluation reference).
- `phase-sub-workflows`: no capability task (L-36).
- `re-planning`: no triggers `capability_conflict` or `intent_amended` (L-36).
- `lesson-store`: no record/codec task; it is implemented (#279).
- `machine-onboarding-unit`: the axis parts already implemented are removed.
- `change-map`: the self-review refusal for an omitted replaced coordinate is now spec (phase-workflows/05 #self-review-submission), as a `conflicts[]` item.
- `epic-auto`: two rules are now spec (after a restart `epic.next-work` once, no row twice; acts refused on an ended epic), so add tests.
- `chat-plan-revision`, `plan-instance-versions`, `recipe-workflows`, `domain-extensions-and-architecture`, `runtime-metrics`, `runtime-control` and `decision-engine`: their package-only rules are now spec statements; the scope is unchanged.
- Every package file now lists tasks with spec anchors only. The done rule is the same in every repository: the row stays `done`, no link, and the file is deleted.

## Spec changes that touch running or staged plans
- Self-review submission: `criteria[]` + `unclaimed[]` + optional `conflicts[]` (phase-workflows/05 #self-review-submission). Merge Danger is folded into the Change Map (Reach, Hard to Undo). There is one PR-body section list (#pr-body-sections).
- The initial lane router is now specified (phase-workflows/01 #initial-lane-router; S1–S5, unknown scope is deep).
- prune_reason gains `no_code_delta` and `no_production_delta`. `default:land-files` sits at 70.
- The drain withdraw is specified (runtime-lifecycle/01 #withdraw-drain, a DELETE of the stop endpoint). Plan 73 can proceed against it.
- The service unit moved to runtime-lifecycle/01 #service-unit (watch PM-WATCH-LIFE-1).
- Epic surface (D11): untracked paths are allowed inside tracked directories. Overlap means the tracked files at epic HEAD plus the shared untracked entries.

## Code work packages the audit asks for (to be planned; operator decides scheduling)
1. Delete the deferred code: capability/EARS (CapabilitySpec, CapabilityIndex, EarsForm, the IntentRecord fields), system_one/judgment (JudgmentClient, JudgmentEndpoint, SystemOneRequest/Answer, the remote form in profiles and enrolment), the deferred qualification and decision-record fields, and the navigation figures with AttemptEnd. Also the parity tests that pin them.
2. Reshape SelfReviewSubmission.
3. `judge_check` as an optional member; the judge check no longer blocks role grading.
4. Fix the stale Javadoc in EnrolmentCheck and WorkflowParser:88-90.
5. The profile verification stages `usage` and `settings`.
6. Planning: LaneRouter S1–S5, the refine_prompt limit of 4 parts, builder-check overflow, the counter `outline_review_revisions`.
7. PruneReason, plus `default:land-files` pruned against branch-cleanup.
8. The epic surface rule, epic acts refused on an ended epic, and next-work once after a restart.
