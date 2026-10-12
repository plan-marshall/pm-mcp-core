envelope_version=1
sender_type=plan
sender_id=plan-11-enrolment-store
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T13:19:15Z

# Write-path policy judged a missing leaf lexically, missing a symlinked ancestor (CWE-59)

Component (suggested): pm-runtime enrolment (EnrolmentPolicy), category bug
Signal: automatic-review, pr-comment finding e5704b fixed in-run on PR #30 (coderabbitai, Major, round 7)

## Observation

`writePath` normalised the path lexically and resolved the real path only when the complete path existed. A not-yet-existing leaf below a symlinked ancestor that points into the base directory passed the check, and lexical normalisation removed `..` before symlink resolution. `enrol` could therefore sign a write path that violates the policy. TASK-43: walk up to the longest existing ancestor (a dangling link counts as existing), resolve it through its real path, append the missing names, judge against the base and its real path; refuse `..` below the last existing ancestor and unresolvable ancestors (fail closed). Tests cover link-into-base (refused), link-outside (admitted), ordinary missing leaf (admitted).

## Candidate rule

A path-confinement check for a path that may not exist yet resolves the longest existing ancestor through its real path before judging; never normalise `..` lexically ahead of symlink resolution. Fixture set: missing leaf under a link into the base, under a link outside, and plain. Related: 2026-10-11-07-006 (symlink components in JournalScope).

Source: plan plan-11-enrolment-store (PLAN-11, PR #30); finding e5704b.
