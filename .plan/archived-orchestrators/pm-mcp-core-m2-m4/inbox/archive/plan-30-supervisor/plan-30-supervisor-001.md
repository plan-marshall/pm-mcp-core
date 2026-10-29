envelope_version=1
sender_type=plan
sender_id=plan-30-supervisor
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-24T13:47:12Z

```landing-facts
schema=landing-facts/1
plan_id=plan-30-supervisor
epic=pm-mcp-core-m2-m4
pr=n/a
merge_state=n/a
cleanup_owed=false
deliverables_total=11
deliverables_done=0
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=0.1.1996
executor_version=0.1.1996
steps=
```

## Held by operator decision — no PR, nothing implemented

PLAN-30 (`supervisor`) was started in the plan-marshall lane on 2026-10-24 and stopped at the end of
`3-outline` by the operator's decision: **hold the plan until `worker-launch` (PLAN-29) lands**.

Why: the package's start path — the launch composition, the harness enrolment store and the model
slots — is not on `main`. The outline offered three shapes (declare the seams as ports inside
`runtime.worker`, hold until PLAN-29, or write a minimal inline launcher); the operator chose to hold.
Both unlanded dependencies are still `open` in `doc/plans/README.adoc`: `worker-launch` (PLAN-29) and
`slots-usage-and-budgets` (PLAN-31). `task-queue-and-protocol` (PLAN-28) merged as #160, so the queue
substrate is on `main`.

State kept for the resume (all under `.plan/local/plans/plan-30-supervisor/`, untracked):

- `request.md` — the ingested spec with the clarified request; confidence 99.0, track complex.
- `solution_outline.md` — 11 deliverables (supervisor core and generations; loss detection; ending,
  fencing and requeue; recycling; start-failure guard and start spacing; `agent_started`; the
  verification-record eligibility gate; the three profile-verification controls; the fixture-harness
  scenarios; developer documentation and ADRs; specification/watch/traceability replacement).
- `status.json` — `current_phase: 4-plan` (in_progress) with `metadata.hold_reason` naming this
  decision.
- Handshakes captured for `1-init`, `2-refine`, `3-outline`.

No branch, no worktree and no PR were created; nothing in the repository changed. `cleanup_owed=false`.

## Resuming

When PLAN-29 is on `main`, re-run the lane for this plan; it continues at `4-plan` (task derivation)
from the kept outline, or re-runs `outline` if the outline is to be re-derived against the landed
launch package (the ports decision in ADR runtime-worker/002 then becomes moot).

## Residue

- The epic row for PLAN-30 remains `running` until this landing is drained; the slot can be freed and
  the plan re-staged once the orchestrator has read this message.
- Spec claims re-verified at refine: 5 of 5 valid (greenfield `runtime.worker`, `VerificationRecord` /
  `LoadedProfile`, `RuntimeLogMessages`, the `supervisor.adoc` sections, `PM-WATCH-CLIENT-13`).
