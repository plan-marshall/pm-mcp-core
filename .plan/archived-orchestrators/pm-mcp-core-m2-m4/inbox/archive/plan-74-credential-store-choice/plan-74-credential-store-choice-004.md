envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:21:36Z

component=pm-runtime
category=anti-pattern
source=qgate 6-finalize ce18f1 (functional-review false_self_report, error)

# A behaviour fix left the specification describing the old resume rule

## Observation

After f4a55d1 changed the resume of a `copying` record (a recorded target now resumes as switched), `doc/specification/store-schemas/12-machine-stores.adoc:695` and `:711` still said a copying record resumes by deleting the target copies. An implementation written against the spec would delete from the recorded store. The functional review flagged it as a false self-report; fixed in TASK-023.

## Rule

A fix task that changes a state-machine transition must list the specification section, the ADR and the Javadoc that state that transition among its affected files, and update them in the same commit. The code-review fix loop created code-only fix tasks (TASK-018..020), and the doc drift needed a second loop to surface.
