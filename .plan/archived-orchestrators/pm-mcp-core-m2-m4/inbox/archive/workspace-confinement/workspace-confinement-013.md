envelope_version=1
sender_type=plan
sender_id=workspace-confinement
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T07:22:52Z

# Candidate (reported, not corroborated by the logs): the pre-commit profile rewrote unrelated test files and unicode escapes

Source: run observation reported by the main session when it handed this step over. It is NOT a finding record, and the plan's logs do not confirm its details.
Plan: `workspace-confinement` (PLAN-03), pull request https://github.com/plan-marshall/pm-mcp-core/pull/19, merged.

## What was reported

1. `verify -Ppre-commit` rewrote the import layout in 14 unrelated test files when a new test's import grouping differed from the convention of its module.
2. The same profile turned unicode escapes in test sources into literal characters.

## What the logs and the repository show

- Work log `7e82f4` (2026-10-09T22:14:52Z): "TASK-2 quality-gate: maven verify -Ppre-commit -pl pm-core -am".
- Work log `b83c3a` (22:17:19Z): "TASK-2 quality-gate re-run after edit: maven verify -Ppre-commit -pl pm-core -am". This is consistent with a correction after the first run, but the entry does not say what was edited.
- No work-log or decision-log entry mentions 14 files, import layout, or unicode escapes.
- The merge commit `7d996488932afd835f9dbde32fad8201aca97364` changes 12 files, all of them files the plan declared. No unrelated test file is in it, so whatever the profile rewrote was not shipped.

## Status

The count of 14, the cause (import grouping of one new test) and the unicode-escape effect rest on the main session's account alone. The pickup should treat them as a lead to check against the session transcript, not as an established fact.

## Why it may be a lesson

If the account holds: a single new test file with a different import grouping makes the profile rewrite files outside the change, which then have to be found and reverted by hand before commit; and escapes written on purpose in adversarial path tests (the tests of this plan work with unusual path characters) are replaced by literal characters. The project's `CLAUDE.md` already says to review every diff the profile produces.

Classification is left to the pickup.
