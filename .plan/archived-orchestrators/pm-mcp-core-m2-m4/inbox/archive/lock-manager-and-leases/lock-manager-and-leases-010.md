envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:52:45Z

# Candidate lesson: outline deliverable used another deliverable's type without declaring the dependency

- Component: plan-marshall:phase-3-outline (deliverable depends authoring)
- Signal: Q-Gate finding 44f2ed (3-outline, triage, taken_into_account)
- Evidence: qgate-3-outline finding 44f2ed; decision log a49d06, 012762

## What happened

Deliverable 5 (FileChannel lock manager in pm-runtime) declared `depends: 2`, but its test and its fourth
success criterion need `AtomicStoreFile`, created by deliverable 3. Approach step 2 even called 3 and 5
independent. A task order taking 5 before 3 could not have compiled. The Q-Gate caught it; the outline was
re-dispatched once and deliverable 5 now declares `depends: 2, 3`.

## Suggested direction

When authoring `depends`, derive it from the types each deliverable's tests and success criteria name, not only
from the production code it writes; check every "independent" claim in the Approach against that list.
