envelope_version=1
sender_type=plan
sender_id=plan-70-plan-instance-alignment
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-19T18:16:08Z

component=pm-workflow
category=improvement

# Review-bot findings fixed in run: spec claims without a mechanism, a dangling reference to the deleted package file, a mirrored list with no drift guard

Source: automatic-review signal (6 CodeRabbit findings on PR #107 resolved as fixed in run: 2a3921, ff087d, 83c4a7, 775b09, 5ab134, 187d38), plan plan-70-plan-instance-alignment.

What slipped past the plan's own reviews and was caught by the bot:

1. Spec stated a behaviour that was not implemented (2a3921: `finalize.merge-barrier` reading the `merge_by_operator` prune; 83c4a7: `outline_review = false` pruning with `plan_default`, which no shipped rule reads). Fixed by marking each as not implemented yet.
2. An ADR (workflow-plan/016) still linked `doc/plans/plan-instance-alignment.adoc`, which the same PR deletes (ff087d).
3. `PlanFacts.Axis.METHOD`/`ASKING` mirror the keys of `DeclarationBounds.SCHEMAS` with no derivation or check (775b09); fixed by a guard test. The developer guide also copied the list instead of naming the derived sets (5ab134).
4. The predecessor exception dropped the parser's refusal `Diagnostic` (187d38).

## Rules

- A spec sentence written in this plan that describes runtime behaviour must name its mechanism or say "not implemented yet"; the functional review should check this, not leave it to the bot.
- Deleting a work package file includes a sweep for its path in the repository's own docs and ADRs (the D10 sweep covered only the documentation repository).
- A hardcoded list that must equal a set defined elsewhere needs a derivation or a test that compares them, in the same change.
