envelope_version=1
sender_type=plan
sender_id=plan-v2-210-package-texts-third-doc-review
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T19:21:42Z

# Candidate lesson: functional review graded a dependency criterion unevidenced

- Source: Q-Gate finding b4040f (phase 6-finalize, rule functional-review, severity error)
- Component: plan-marshall:phase-6-finalize
- Resolution in run: taken_into_account (functional review closed at a4562cb2 with no blocking finding)

## Observation

Criterion D5.C2 (forgejo-provider row lists the dependency on ci-contract-finalize-operations, "fails at 2b4bdee8") was graded unevidenced because no failing run against the pre-fix tree 2b4bdee8 was cited.

## Candidate rule

For a docs-only plan whose acceptance criteria are phrased as "fails at {sha}", cite the failing check against the pre-fix tree (for example `git show {sha}:doc/plans/README.adoc` showing the old row) together with the SHA and a failure token in the verification evidence, so the functional review does not have to raise it.

Classification deferred to the orchestrator.
