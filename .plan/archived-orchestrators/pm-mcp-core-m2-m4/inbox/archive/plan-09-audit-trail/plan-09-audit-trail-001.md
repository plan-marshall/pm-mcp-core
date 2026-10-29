envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:56:42Z

# Sourcery "used its review budget" quota notice not recognised as a refusal

- Source: review-bot records 6122fe and 318838 (plan-09-audit-trail, PR #69), both `bot_kind: sourcery`, `kind: issue_comment`, resolution `taken_into_account`.
- Suggested component: `plan-marshall:automatic-review` (standards/sourcery.md `refusal_patterns`), possibly `plan-marshall:workflow-integration-github` (fetch_findings).

## Observation

Sourcery posted, twice (head edbed7a and head 8cf3c94):

> Sorry @cuioss-oliver, this account has used its review budget of 2,500,000 diff characters for the last 7 days. You can request another review in 2 days and 6 hours by commenting `@sourcery-ai review`.

`fetch_findings` did not classify it as a refusal and filed it as a pending `pr-comment` finding each time, which then had to be dispositioned by hand. The registry's nearest pattern is `"used your own review budget of"` (#1391 wording); the live wording is `"this account has used its review budget of"` — "its", not "your own", so the substring never matches. The structural recogniser (`_is_rate_limit_notice`) keys on exceeded/reached/hit and misses it too.

The second firing also logged a WARNING that the re-review comment did not reference the head SHA and was "recorded as declined", yet the comment was still ingested as a finding.

## Candidate rule

Add a handle- and pronoun-free pattern such as `"used its review budget of"` (or `"review budget of"`) to Sourcery's `refusal_patterns`, and add the live wording as a regression fixture so a pronoun change in the notice does not reopen the gap. Consider whether a comment already classified "declined" by the re-review path should ever reach the findings store.
